package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import java.util.*;
import java.util.function.Consumer;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryEntity;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerPumpGameTests {
    private static final BlockPos O = new BlockPos(1, 2, 1), P = O.offset(4, 1, 3), R = P.east(), B = O.offset(1, 1, 4);

    private static MixerPumpEntity pump(GameTestHelper h) {
        return (MixerPumpEntity) h.getBlockEntity(P);
    }

    @GameTest(template = "empty")
    public static void disabledMixerStillPullsFiniteSource(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    c.setMachineActive(false);
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && h.getBlockState(R).isAir(),
                            "Disabled Mixer stopped Pull Pump");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void disabledMixerStillPushesStoredFluid(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PushPump,
                c -> {
                    c.setMachineActive(false);
                    tank(h).fill(water(1000), EXECUTE);
                    h.setBlock(R, Blocks.AIR);
                },
                () -> {
                    h.assertTrue(
                            tank(h).isEmpty()
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Disabled Mixer stopped Push Pump");
                    h.succeed();
                });
    }

    private static FluidTank tank(GameTestHelper h) {
        return ((FluidBufferEntity) h.getBlockEntity(B)).tank;
    }

    private static FluidStack water(int n) {
        return new FluidStack(Fluids.WATER, n);
    }

    private static void tap(GameTestHelper h, BlockPos p, ConduitMode mode) {
        boolean inside = MixerPlacement.interior(p, O, O.offset(4, 3, 4));
        h.setBlock(
                p,
                ConduitContent.state(
                                ConduitResource.FLUID,
                                inside ? ConduitCladding.BARE : ConduitCladding.CASING,
                                ConduitShell.MIXER)
                        .setValue(ConduitBlock.MODE, mode));
    }

    private static void rig(GameTestHelper h, MixerPartType type, Consumer<MultiblockMixer> start, Runnable verify) {
        MachineFixtures.mixerRig(h::setBlock, O);
        h.setBlock(P, MixerContent.state(type));
        h.setBlock(B, FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
        tap(h, B.east(), ConduitMode.TAP);
        tap(h, B.east(2), ConduitMode.TAP);
        tap(h, P.west(), ConduitMode.TAP);
        // One-cell sealed external cavity makes exact world/storage accounting deterministic.
        for (var d : Direction.values()) if (d != Direction.WEST) h.setBlock(R.relative(d), Blocks.STONE);
        h.setBlock(R, Blocks.STONE);
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(c.isAssembled(), "Pump Mixer did not form");
            h.assertTrue(
                    c.conduitTopology()
                            .reachable(h.absolutePos(P), ConduitResource.FLUID)
                            .contains(h.absolutePos(B)),
                    "Fixture storage disconnected");
            start.accept(c);
        });
        h.runAtTickTime(55, verify);
    }

    @GameTest(template = "empty")
    public static void pullFormsTapAndExtractsFiniteSource(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    h.assertTrue(
                            c.conduitTopology()
                                    .reachable(h.absolutePos(P), ConduitResource.FLUID)
                                    .contains(h.absolutePos(B)),
                            "Inward TAP disconnected");
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && h.getBlockState(R).isAir()
                                    && pump(h).service().tanks().isEmpty(),
                            "Finite pull accounting failed");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void pushFormsTapAndPlacesSource(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PushPump,
                c -> {
                    h.assertTrue(
                            c.conduitTopology()
                                    .reachable(h.absolutePos(P), ConduitResource.FLUID)
                                    .contains(h.absolutePos(B)),
                            "Push TAP disconnected");
                    tank(h).fill(water(2000), EXECUTE);
                    h.setBlock(R, Blocks.AIR);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Push did not place exactly one bucket");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void fullPhysicalStorageBlocksPull(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    tank(h).fill(water(tank(h).getCapacity()), EXECUTE);
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == tank(h).getCapacity()
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Full storage consumed source");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void partialCapacityDoesNotExtract(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    tank(h).fill(water(tank(h).getCapacity() - 999), EXECUTE);
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == tank(h).getCapacity() - 999
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Partial bucket extracted");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void pullFilterMismatchPreservesSource(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    pump(h).filter("minecraft:lava");
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).isEmpty()
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Pull filter bypassed");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void pushFilterMismatchPreservesStorage(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PushPump,
                c -> {
                    pump(h).filter("minecraft:lava");
                    tank(h).fill(water(1000), EXECUTE);
                    h.setBlock(R, Blocks.AIR);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && h.getBlockState(R).isAir(),
                            "Push filter bypassed");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void blockedPlacementPreservesStorage(GameTestHelper h) {
        rig(h, MixerPartType.PushPump, c -> tank(h).fill(water(1000), EXECUTE), () -> {
            h.assertTrue(
                    tank(h).getFluidAmount() == 1000
                            && h.getBlockState(R).is(Blocks.STONE)
                            && pump(h).status().contains("blocked"),
                    "Blocked push spent storage");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void infiniteWaterPreservedAndBackpressured(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    for (int dz = -2; dz <= 2; dz++) {
                        var q = R.offset(0, 0, dz);
                        h.setBlock(q.below(), Blocks.STONE);
                        h.setBlock(q.east(), Blocks.STONE);
                        h.setBlock(q.above(), Blocks.STONE);
                        h.setBlock(q, Math.abs(dz) == 2 ? Blocks.STONE : Blocks.WATER);
                    }
                    tank(h).fill(water(tank(h).getCapacity() - 1000), EXECUTE);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == tank(h).getCapacity()
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Infinite source removed or storage overflowed: formed=" + pump(h).isMachineAssembled()
                                    + " amount=" + tank(h).getFluidAmount() + " status=" + pump(h).status());
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void compoundAndComponentFluidsPreserved(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PushPump,
                c -> {
                    var s = water(1000);
                    s.set(DataComponents.CUSTOM_NAME, Component.literal("Identity"));
                    tank(h).fill(s, EXECUTE);
                    h.setBlock(R, Blocks.AIR);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && tank(h).getFluid().has(DataComponents.CUSTOM_NAME)
                                    && h.getBlockState(R).isAir(),
                            "Lossy component world placement");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void unsupportedWorldFluidPreserved(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PushPump,
                c -> {
                    tank(h).fill(new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), 1000), EXECUTE);
                    h.setBlock(R, Blocks.AIR);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && h.getBlockState(R).isAir(),
                            "Unsupported fluid spent");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void trunkDoesNotAttachPump(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    tap(h, P.west(), ConduitMode.TRUNK);
                    c.rebuildTopology();
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).isEmpty()
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "TRUNK attached pump");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void sideTapCannotAttachPump(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    h.setBlock(P.west(), Blocks.AIR);
                    tap(h, P.below(), ConduitMode.TAP);
                    tap(h, P.below().west(), ConduitMode.TAP);
                    tap(h, B.below(), ConduitMode.TAP);
                    tap(h, B.below().east(), ConduitMode.TAP);
                    c.rebuildTopology();
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(
                            tank(h).isEmpty()
                                    && h.getBlockState(R).getFluidState().isSource(),
                            "Wrong-facing TAP attached pump");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void unformedPumpCannotRun(GameTestHelper h) {
        h.setBlock(P, MixerContent.state(MixerPartType.PullPump));
        h.setBlock(B, FluidBufferContent.state(ConduitCladding.BARE));
        h.setBlock(R, Blocks.WATER);
        h.runAtTickTime(35, () -> {
            var c = pump(h).getMultiblockController().orElseThrow();
            h.assertTrue(
                    !c.isAssembled()
                            && !MixerPumps.tick(c, List.of(pump(h)))
                            && tank(h).isEmpty()
                            && h.getBlockState(R).getFluidState().isSource(),
                    "Unformed pump operated");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void breakReformRestoresOperation(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    h.setBlock(O.offset(0, 0, 0), Blocks.AIR);
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    h.assertTrue(!pump(h).isMachineAssembled() && tank(h).isEmpty(), "Broken Mixer operated");
                    h.setBlock(O.offset(0, 0, 0), MixerContent.state(MixerPartType.Casing));
                });
        h.runAtTickTime(90, () -> {
            h.assertTrue(
                    pump(h).isMachineAssembled()
                            && tank(h).getFluidAmount() == 1000
                            && h.getBlockState(R).isAir(),
                    "Reformed pump failed");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void filterSaveLoadPortableAndInteraction(GameTestHelper h) {
        h.setBlock(P, MixerContent.state(MixerPartType.PullPump));
        var e = pump(h);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        e.configure(player, InteractionHand.MAIN_HAND);
        h.assertTrue(
                e.filter().equals("minecraft:water") && player.getMainHandItem().is(Items.WATER_BUCKET),
                "Sampling consumed container");
        var exact = water(1);
        exact.set(DataComponents.CUSTOM_NAME, Component.literal("Exact"));
        e.filter(EndpointState.fluidId(exact));
        var data = e.saveWithFullMetadata(h.getLevel().registryAccess());
        var item = EndpointState.capture(new ItemStack(MixerContent.block(MixerPartType.PullPump)), e);
        e.filter("");
        e.loadWithComponents(data, h.getLevel().registryAccess());
        h.assertTrue(e.filter().equals(EndpointState.fluidId(exact)), "Save lost filter");
        h.setBlock(P, MixerContent.state(MixerPartType.PushPump));
        EndpointState.restore(item, pump(h));
        h.assertTrue(pump(h).filter().equals(EndpointState.fluidId(exact)), "Drop lost filter");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        pump(h).configure(player, InteractionHand.MAIN_HAND);
        h.assertTrue(pump(h).filter().isEmpty(), "Filter clear failed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void formedFilterLockedAndInspectionVisible(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    pump(h).filter("minecraft:water");
                    var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    player.setShiftKeyDown(true);
                    pump(h).configure(player, InteractionHand.MAIN_HAND);
                    h.assertTrue(
                            pump(h).filter().equals("minecraft:water")
                                    && MachineInspection.lines(h.getLevel(), h.absolutePos(P))
                                            .toString()
                                            .contains("water"),
                            "Filter lock/inspection failed");
                },
                h::succeed);
    }

    @GameTest(template = "empty")
    public static void compoundFormulaCannotBePlaced(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PushPump,
                c -> {
                    var original = new FluidStack(
                            net.impactvector.mobvats.gamecontent.fluid.ProductFluids.CULTURE_SLURRY.get(), 1000);
                    var composition = new net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition(
                            List.of(new net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Entry(
                                    net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Kind.FLUID,
                                    "minecraft:water",
                                    1000)));
                    original.set(
                            net.impactvector.mobvats.gamecontent.fluid.compound.CompoundFluids.COMPOSITION.get(),
                            composition);
                    tank(h).fill(original, EXECUTE);
                    h.setBlock(R, Blocks.AIR);
                },
                () -> {
                    h.assertTrue(
                            tank(h).getFluidAmount() == 1000
                                    && tank(h).getFluid()
                                            .has(net.impactvector.mobvats.gamecontent.fluid.compound.CompoundFluids
                                                    .COMPOSITION
                                                    .get())
                                    && h.getBlockState(R).isAir(),
                            "Compound placement lost formula");
                    h.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void reachableVoidCannotReplacePhysicalPullCapacity(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    var v = O.offset(4, 1, 1);
                    h.assertTrue(
                            c.conduitTopology()
                                    .reachable(h.absolutePos(P), ConduitResource.FLUID)
                                    .contains(h.absolutePos(v)),
                            "Void fixture disconnected");
                    tank(h).fill(water(tank(h).getCapacity()), EXECUTE);
                    h.setBlock(R, Blocks.WATER);
                },
                () -> {
                    var sink = (MixerFluidVoidEntity) h.getBlockEntity(O.offset(4, 1, 1));
                    h.assertTrue(
                            tank(h).getFluidAmount() == tank(h).getCapacity()
                                    && h.getBlockState(R).getFluidState().isSource()
                                    && sink.disposedMb() == 0,
                            "Pull disposed world source through Void");
                    h.succeed();
                });
        var v = O.offset(4, 1, 1);
        h.setBlock(v, MixerContent.state(MixerPartType.FluidVoid));
        tap(h, v.west(), ConduitMode.TAP);
        tap(h, v.west().above(), ConduitMode.TRUNK);
        for (int z = 1; z <= 3; z++) tap(h, O.offset(3, 3, z), ConduitMode.TRUNK);
        tap(h, P.west().above(), ConduitMode.TRUNK);
    }

    @GameTest(template = "empty")
    public static void standardSimulationAndPlacementSearchArePure(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    var handler = PhysicalFluidStorage.handler(
                            c.storage().bulkConnected(h.absolutePos(P)), true, pump(h)::accepts);
                    h.setBlock(R, Blocks.AIR);
                    h.assertTrue(
                            handler.fill(water(1000), SIMULATE) == 1000 && tank(h).isEmpty(),
                            "Fill simulation mutated storage");
                    h.assertTrue(
                            WorldFluidPumps.placement(h.getLevel(), h.absolutePos(R), Fluids.WATER)
                                            .equals(h.absolutePos(R))
                                    && h.getBlockState(R).isAir(),
                            "Placement validation changed world");
                    tank(h).fill(water(1000), EXECUTE);
                    var output = PhysicalFluidStorage.handler(
                            c.storage().bulkConnected(h.absolutePos(P)), false, pump(h)::accepts);
                    h.assertTrue(
                            output.drain(water(1000), SIMULATE).getAmount() == 1000
                                    && tank(h).getFluidAmount() == 1000
                                    && h.getBlockState(R).isAir(),
                            "Drain simulation changed storage/world");
                },
                h::succeed);
    }

    @GameTest(template = "empty")
    public static void mixerModeCraftPreservesComponentFilter(GameTestHelper h) {
        var sample = water(1);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("Exact"));
        var input = new ItemStack(MixerContent.block(MixerPartType.PullPump));
        var state = new net.minecraft.nbt.CompoundTag();
        state.putString("filter", EndpointState.fluidId(sample));
        EndpointState.write(input, state);
        var ingredients = NonNullList.of(
                net.minecraft.world.item.crafting.Ingredient.EMPTY,
                net.minecraft.world.item.crafting.Ingredient.of(input));
        var recipe = new net.impactvector.mobvats.gamecontent.item.BoundarySwitchRecipe(
                new net.minecraft.world.item.crafting.ShapelessRecipe(
                        "",
                        net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                        new ItemStack(MixerContent.block(MixerPartType.PushPump)),
                        ingredients));
        var grid = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(input));
        h.assertTrue(
                recipe.matches(grid, h.getLevel())
                        && EndpointState.read(recipe.assemble(grid, h.getLevel().registryAccess()))
                                .getString("filter")
                                .equals(EndpointState.fluidId(sample)),
                "Mixer pump conversion lost filter");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedVatAndMixerRepeatedRoundTripNoLoss(GameTestHelper h) {
        rig(
                h,
                MixerPartType.PullPump,
                c -> {
                    var v = new BlockPos(10, 3, 10);
                    h.setBlock(v, Content.state(VatPartType.PushPump));
                    var vat = (VatBoundaryEntity) h.getBlockEntity(v);
                    var t = tank(h);
                    var root = h.absolutePos(R);
                    for (int i = 0; i < 20; i++) {
                        h.setBlock(R, Blocks.WATER);
                        h.assertTrue(
                                WorldFluidPumps.pull(h.getLevel(), root, t, pump(h))
                                        && t.getFluidAmount() == 1000
                                        && h.getBlockState(R).isAir(),
                                "Repeated Mixer pull lost/duplicated fluid");
                        h.assertTrue(
                                BoundaryDevices.push(
                                                h.getLevel(),
                                                root,
                                                PhysicalFluidStorage.handler(List.of(t), false, s -> true),
                                                vat)
                                        && t.isEmpty()
                                        && h.getBlockState(R).getFluidState().isSource(),
                                "Vat shared push lost/duplicated fluid");
                    }
                    h.setBlock(R, Blocks.STONE);
                },
                () -> {
                    h.assertTrue(tank(h).isEmpty(), "Repeated roundtrip retained fluid");
                    h.succeed();
                });
    }
}
