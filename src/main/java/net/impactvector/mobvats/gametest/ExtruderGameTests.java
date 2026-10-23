package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.extruder.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.product.ProductContent;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "mobvats")
public final class ExtruderGameTests {
    private static final BlockPos P = new BlockPos(3, 3, 3);
    private static final Map<BlockPos, Source> SOURCES = new HashMap<>();
    private static final Map<BlockPos, IItemHandler> SINKS = new HashMap<>();

    @SubscribeEvent
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    var source = SOURCES.get(pos);
                    if (source == null) return null;
                    source.queriedSide = side;
                    return side == source.face ? source : null;
                },
                Blocks.DIAMOND_BLOCK);
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> side == Direction.UP ? SINKS.get(pos) : null,
                Blocks.GOLD_BLOCK);
    }

    private static final class Source implements IFluidHandler {
        final List<FluidTank> tanks = new ArrayList<>();
        Direction face = Direction.NORTH;
        Direction queriedSide;
        boolean blocked, partialSimulation, badExecute, wrongExecute;
        int simulations, executions;

        Source(FluidStack... fluids) {
            for (var fluid : fluids) {
                var tank = new FluidTank(64000);
                tank.setFluid(fluid.copy());
                tanks.add(tank);
            }
        }

        public int getTanks() {
            return tanks.size();
        }

        public FluidStack getFluidInTank(int tank) {
            return tanks.get(tank).getFluid();
        }

        public int getTankCapacity(int tank) {
            return 64000;
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        public int fill(FluidStack stack, FluidAction action) {
            return 0;
        }

        public FluidStack drain(int amount, FluidAction action) {
            throw new AssertionError("Amount-only drain bypasses identity");
        }

        public FluidStack drain(FluidStack stack, FluidAction action) {
            if (action.simulate()) simulations++;
            else executions++;
            if (blocked) return FluidStack.EMPTY;
            for (var tank : tanks) {
                if (!FluidStack.isSameFluidSameComponents(stack, tank.getFluid())) continue;
                var request = stack.copy();
                if ((action.simulate() && partialSimulation) || (!action.simulate() && badExecute))
                    request.setAmount(request.getAmount() - 1);
                var result = tank.drain(request, action);
                return !action.simulate() && wrongExecute ? new FluidStack(Fluids.WATER, result.getAmount()) : result;
            }
            return FluidStack.EMPTY;
        }
    }

    private record Fixture(ExtruderEntity entity, Source source) {}

    private static Fixture fixture(GameTestHelper h, FluidStack... fluids) {
        h.setBlock(P.below(), Blocks.AIR);
        h.setBlock(P, Blocks.AIR);
        h.setBlock(P, ExtruderContent.BLOCK.get());
        var source = new Source(fluids);
        attach(h, P.south(), source);
        return new Fixture((ExtruderEntity) h.getBlockEntity(P), source);
    }

    private static void attach(GameTestHelper h, BlockPos pos, Source source) {
        h.setBlock(pos, Blocks.DIAMOND_BLOCK);
        SOURCES.put(h.absolutePos(pos), source);
        h.getLevel().invalidateCapabilities(h.absolutePos(pos));
    }

    private static FluidStack meat(int amount) {
        return new FluidStack(ProductFluids.MEAT.get(), amount);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void strangeMeatNeverExtrudesEvenWithMeatComposition(GameTestHelper h) {
        for (var selected : ExtruderBlock.Form.values()) {
            for (boolean metadata : new boolean[] {false, true}) {
                var strange = new FluidStack(ProductFluids.STRANGE_MEAT.get(), 2000);
                if (metadata)
                    strange.set(
                            CompoundFluids.COMPOSITION.get(),
                            new CompoundComposition(List.of(new CompoundComposition.Entry(
                                    CompoundComposition.Kind.FLUID, "mobvats:meat", 1000))));
                var f = fixture(h, strange);
                form(h, selected);
                f.entity.processCycle();
                output(h, f, Items.AIR, 0);
                h.assertTrue(
                        f.source.executions == 0 && f.source.getFluidInTank(0).getAmount() == 2000,
                        "Strange Meat entered edible extrusion " + selected);
                // A rejected first tank must not prevent normal Meat in a subsequent tank.
                f = fixture(h, strange, meat(1000));
                form(h, selected);
                f.entity.processCycle();
                output(
                        h,
                        f,
                        selected == ExtruderBlock.Form.BLOCK
                                ? ProductContent.MEAT_BLOCK.get().asItem()
                                : ProductContent.RAW_MEAT.get(),
                        1);
                h.assertTrue(f.source.getFluidInTank(0).getAmount() == 2000, "Consumed hostile biomass");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void redstoneDirectPauseResume(GameTestHelper h) {
        var f = fixture(h, meat(1000));
        f.entity.processCycle();
        h.setBlock(P.below(), Blocks.BARREL);
        h.setBlock(P.above(), Blocks.REDSTONE_BLOCK);
        for (int i = 0; i < 5; i++) f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.assertTrue(
                f.source.executions == 1 && f.source.getFluidInTank(0).getAmount() == 750,
                "Powered Extruder consumed fluid");
        var barrel = (BarrelBlockEntity) h.getBlockEntity(P.below());
        h.assertTrue(barrel.isEmpty(), "Powered Extruder actively ejected output");
        var cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(P), Direction.DOWN);
        h.assertTrue(
                cap != null && cap.extractItem(0, 1, false).getCount() == 1,
                "Powered Extruder blocked passive extraction");
        h.setBlock(P.above(), Blocks.AIR);
        f.entity.processCycle();
        f.entity.processCycle();
        h.assertTrue(!barrel.isEmpty() && f.source.executions == 3, "Extruder did not resume normally");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void redstoneIndirectAndReload(GameTestHelper h) {
        var f = fixture(h, meat(2000));
        form(h, ExtruderBlock.Form.BLOCK);
        f.entity.processCycle();
        // A floor lever strongly powers the solid block beside the machine.
        h.setBlock(P.east(), Blocks.STONE);
        h.setBlock(
                P.east().above(),
                Blocks.LEVER
                        .defaultBlockState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                        .setValue(LeverBlock.POWERED, true));
        h.assertTrue(h.getLevel().hasNeighborSignal(h.absolutePos(P)), "Fixture lacks indirect power");
        var registries = h.getLevel().registryAccess();
        var tag = f.entity.saveWithFullMetadata(registries);
        h.getLevel().removeBlockEntity(h.absolutePos(P));
        var restored = (ExtruderEntity) BlockEntity.loadStatic(h.absolutePos(P), h.getBlockState(P), tag, registries);
        h.getLevel().setBlockEntity(restored);
        for (var facing : Direction.Plane.HORIZONTAL) {
            h.setBlock(P, h.getBlockState(P).setValue(ExtruderBlock.FACING, facing));
            restored.processCycle();
        }
        h.assertTrue(
                restored.output().getStackInSlot(0).getCount() == 1
                        && f.source.getFluidInTank(0).getAmount() == 1000
                        && h.getBlockState(P).getValue(ExtruderBlock.FORM) == ExtruderBlock.Form.BLOCK,
                "Indirect power/reload lost inhibition or state");
        h.setBlock(P.east().above(), Blocks.AIR);
        h.setBlock(P, h.getBlockState(P).setValue(ExtruderBlock.FACING, Direction.NORTH));
        restored.processCycle();
        h.assertTrue(restored.output().getStackInSlot(0).getCount() == 2, "Reloaded Extruder did not resume");
        h.succeed();
    }

    private static FluidStack waste(int amount) {
        return new FluidStack(ProductFluids.WASTE.get(), amount);
    }

    private static void form(GameTestHelper h, ExtruderBlock.Form form) {
        h.setBlock(P, h.getBlockState(P).setValue(ExtruderBlock.FORM, form));
    }

    private static void output(GameTestHelper h, Fixture f, Item item, int count) {
        var stack = f.entity.output().getStackInSlot(0);
        h.assertTrue(
                count == 0 ? stack.isEmpty() : stack.is(item) && stack.getCount() == count,
                "Unexpected output: " + stack);
    }

    private static void convert(GameTestHelper h, boolean meat, boolean block, int amount, Item item) {
        var f = fixture(h, meat ? meat(amount) : waste(amount));
        if (block) form(h, ExtruderBlock.Form.BLOCK);
        f.entity.processCycle();
        output(h, f, item, 1);
        h.assertTrue(f.source.getFluidInTank(0).isEmpty(), "Incorrect fluid cost");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void portionMeat(GameTestHelper h) {
        convert(h, true, false, 250, ProductContent.RAW_MEAT.get());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void portionWaste(GameTestHelper h) {
        convert(h, false, false, 100, ProductContent.FERTILIZER.get());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void blockMeat(GameTestHelper h) {
        convert(h, true, true, 1000, ProductContent.MEAT_BLOCK.get().asItem());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void blockWaste(GameTestHelper h) {
        convert(h, false, true, 1000, ProductContent.WASTE_BLOCK.get().asItem());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void registrationPlacementAndRecipe(GameTestHelper h) {
        var id = ResourceLocation.fromNamespaceAndPath("mobvats", "extruder");
        h.assertTrue(BuiltInRegistries.BLOCK.get(id) == ExtruderContent.BLOCK.get(), "Block registration");
        h.assertTrue(
                BuiltInRegistries.ITEM.get(id) == ExtruderContent.BLOCK.get().asItem(), "Item registration");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isPresent(), "Missing crafting recipe");
        h.setBlock(P.below(), Blocks.STONE);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(90);
        var stack = new ItemStack(ExtruderContent.BLOCK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hitPos = h.absolutePos(P.below());
        h.assertTrue(
                stack.useOn(new UseOnContext(
                                player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(hitPos).add(0, 0.5, 0), Direction.UP, hitPos, false)))
                        .consumesAction(),
                "Placement failed");
        h.assertTrue(
                h.getBlockEntity(P) instanceof ExtruderEntity
                        && h.getBlockState(P).getValue(ExtruderBlock.FACING)
                                == player.getDirection().getOpposite(),
                "Facing/entity placement");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void horizontalRearAndNeighborFace(GameTestHelper h) {
        var f = fixture(h, meat(1000));
        for (var direction : Direction.Plane.HORIZONTAL) {
            var source = new Source(meat(250));
            source.face = direction;
            attach(h, P.relative(direction.getOpposite()), source);
            h.setBlock(P, h.getBlockState(P).setValue(ExtruderBlock.FACING, direction));
            f.entity.processCycle();
            h.assertTrue(
                    source.queriedSide == direction && source.getFluidInTank(0).isEmpty(),
                    "Wrong rear/neighbor-facing query");
        }
        output(h, f, ProductContent.RAW_MEAT.get(), 4);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void insufficientAllConversions(GameTestHelper h) {
        for (boolean block : new boolean[] {false, true})
            for (boolean meat : new boolean[] {false, true}) {
                int amount = (block ? 1000 : meat ? 250 : 100) - 1;
                var f = fixture(h, meat ? meat(amount) : waste(amount));
                form(h, block ? ExtruderBlock.Form.BLOCK : ExtruderBlock.Form.PORTION);
                f.entity.processCycle();
                output(h, f, Items.AIR, 0);
                h.assertTrue(
                        f.source.getFluidInTank(0).getAmount() == amount && f.source.executions == 0,
                        "Partial batch consumed");
            }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void simulationPreservesResources(GameTestHelper h) {
        var source = new Source(meat(1000));
        var result = source.drain(meat(250), IFluidHandler.FluidAction.SIMULATE);
        h.assertTrue(
                result.getAmount() == 250 && source.getFluidInTank(0).getAmount() == 1000, "Simulation mutated tank");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void partialSimulationRejected(GameTestHelper h) {
        var f = fixture(h, meat(1000));
        f.source.partialSimulation = true;
        f.entity.processCycle();
        output(h, f, Items.AIR, 0);
        h.assertTrue(
                f.source.executions == 0 && f.source.getFluidInTank(0).getAmount() == 1000,
                "Partial simulation executed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void handlerRestrictionsRespected(GameTestHelper h) {
        var f = fixture(h, meat(1000));
        f.source.blocked = true;
        f.entity.processCycle();
        output(h, f, Items.AIR, 0);
        h.assertTrue(f.source.executions == 0, "Bypassed handler restriction");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void brokenExecutionDoesNotDuplicate(GameTestHelper h) {
        for (boolean wrongFluid : new boolean[] {false, true}) {
            var f = fixture(h, meat(1000));
            f.source.badExecute = !wrongFluid;
            f.source.wrongExecute = wrongFluid;
            f.entity.processCycle();
            output(h, f, Items.AIR, 0);
            h.assertTrue(f.source.executions == 1, "Execution not exercised");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void unsupportedFluidsIgnored(GameTestHelper h) {
        var f = fixture(
                h, new FluidStack(Fluids.WATER, 1000), new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000));
        f.entity.processCycle();
        output(h, f, Items.AIR, 0);
        h.assertTrue(f.source.simulations == 0 && f.source.executions == 0, "Unsupported fluid considered");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void supportedCompositionIdentity(GameTestHelper h) {
        for (boolean meat : new boolean[] {true, false}) {
            var fluid = meat ? meat(1000) : waste(1000);
            fluid.set(
                    CompoundFluids.COMPOSITION.get(),
                    new CompoundComposition(List.of(
                            new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:beef", 1))));
            var f = fixture(h, fluid);
            f.entity.processCycle();
            output(h, f, meat ? ProductContent.RAW_MEAT.get() : ProductContent.FERTILIZER.get(), 1);
            h.assertTrue(
                    FluidStack.isSameFluidSameComponents(fluid, f.source.getFluidInTank(0)),
                    "Remaining composition stripped");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void singleSlotStackLimitAndBackpressure(GameTestHelper h) {
        var f = fixture(h, meat(20000));
        for (int i = 0; i < 65; i++) f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 64);
        h.assertTrue(
                f.entity.output().getSlots() == 1
                        && f.source.getFluidInTank(0).getAmount() == 4000
                        && f.source.executions == 64,
                "Overflow consumed fluid");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void incompatibleProductsPreserved(GameTestHelper h) {
        var f = fixture(h, meat(250));
        f.entity.processCycle();
        f.source.tanks.getFirst().setFluid(waste(1000));
        f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.assertTrue(f.source.getFluidInTank(0).getAmount() == 1000, "Incompatible input consumed");
        f.entity.output().extractItem(0, 64, false);
        f.entity.processCycle();
        output(h, f, ProductContent.FERTILIZER.get(), 1);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void modeChangePreservesOutputAndResumes(GameTestHelper h) {
        var f = fixture(h, meat(1250));
        f.entity.processCycle();
        form(h, ExtruderBlock.Form.BLOCK);
        f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.assertTrue(f.source.getFluidInTank(0).getAmount() == 1000, "Mode change consumed incompatible batch");
        f.entity.output().extractItem(0, 64, false);
        f.entity.processCycle();
        output(h, f, ProductContent.MEAT_BLOCK.get().asItem(), 1);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void barrelInsertion(GameTestHelper h) {
        var f = fixture(h, meat(250));
        f.entity.processCycle();
        h.setBlock(P.below(), Blocks.BARREL);
        f.entity.processCycle();
        output(h, f, Items.AIR, 0);
        h.assertTrue(
                ((BarrelBlockEntity) h.getBlockEntity(P.below())).getItem(0).is(ProductContent.RAW_MEAT.get()),
                "Barrel did not receive output");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void partialDestinationAcceptance(GameTestHelper h) {
        var f = fixture(h, meat(1000));
        for (int i = 0; i < 4; i++) f.entity.processCycle();
        var sink = new ItemStackHandler(1);
        sink.setStackInSlot(0, new ItemStack(ProductContent.RAW_MEAT.get(), 62));
        h.setBlock(P.below(), Blocks.GOLD_BLOCK);
        SINKS.put(h.absolutePos(P.below()), sink);
        h.getLevel().invalidateCapabilities(h.absolutePos(P.below()));
        f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 2);
        h.assertTrue(sink.getStackInSlot(0).getCount() == 64, "Partial insertion lost items");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fullDestinationRetainsOutput(GameTestHelper h) {
        var f = fixture(h, meat(250));
        f.entity.processCycle();
        h.setBlock(P.below(), Blocks.BARREL);
        var barrel = (BarrelBlockEntity) h.getBlockEntity(P.below());
        for (int i = 0; i < barrel.getContainerSize(); i++) barrel.setItem(i, new ItemStack(Items.STONE, 64));
        f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void bottomExtractionAndSimulation(GameTestHelper h) {
        var f = fixture(h, meat(500));
        f.entity.processCycle();
        f.entity.processCycle();
        var cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(P), Direction.DOWN);
        h.assertTrue(cap != null && cap.extractItem(0, 1, true).getCount() == 1, "Missing bottom extraction");
        output(h, f, ProductContent.RAW_MEAT.get(), 2);
        h.assertTrue(cap.extractItem(0, 1, false).getCount() == 1, "Extraction failed");
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void externalInsertionRejected(GameTestHelper h) {
        var f = fixture(h, meat(250));
        var cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(P), Direction.DOWN);
        for (var item : List.of(Items.STONE, ProductContent.RAW_MEAT.get()))
            for (boolean simulate : new boolean[] {true, false}) {
                var stack = new ItemStack(item, 64);
                h.assertTrue(
                        cap.insertItem(0, stack, simulate).getCount() == 64 && !cap.isItemValid(0, stack),
                        "External insertion accepted");
            }
        output(h, f, Items.AIR, 0);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void otherFacesAndFluidCapabilityAbsent(GameTestHelper h) {
        fixture(h, meat(250));
        for (var side : Direction.values()) {
            if (side != Direction.DOWN)
                h.assertTrue(
                        h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(P), side) == null,
                        "Unexpected output face");
            h.assertTrue(
                    h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(P), side) == null,
                    "Extruder exposes fluid tank");
        }
        h.assertTrue(
                h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(P), null) == null,
                "Unsided item access");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void inventoryAndFormSaveReload(GameTestHelper h) {
        var f = fixture(h, waste(2000));
        form(h, ExtruderBlock.Form.BLOCK);
        f.entity.processCycle();
        var registries = h.getLevel().registryAccess();
        var tag = f.entity.saveWithFullMetadata(registries);
        var restored = BlockEntity.loadStatic(h.absolutePos(P), h.getBlockState(P), tag, registries);
        h.assertTrue(
                restored instanceof ExtruderEntity
                        && restored.getBlockState().getValue(ExtruderBlock.FORM) == ExtruderBlock.Form.BLOCK,
                "Form/entity reload failed");
        h.assertTrue(
                ((ExtruderEntity) restored)
                        .output()
                        .getStackInSlot(0)
                        .is(ProductContent.WASTE_BLOCK.get().asItem()),
                "Inventory reload failed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void configurationInteraction(GameTestHelper h) {
        var f = fixture(h, meat(250));
        f.entity.processCycle();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        var abs = h.absolutePos(P);
        var result = h.getBlockState(P)
                .useWithoutItem(
                        h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
        h.assertTrue(
                result.consumesAction() && h.getBlockState(P).getValue(ExtruderBlock.FORM) == ExtruderBlock.Form.BLOCK,
                "Sneak configuration failed");
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void handlerOrderAndNoMixedBatch(GameTestHelper h) {
        var f = fixture(h, waste(100), meat(250));
        f.entity.processCycle();
        output(h, f, ProductContent.FERTILIZER.get(), 1);
        h.assertTrue(f.source.getFluidInTank(1).getAmount() == 250, "Did not select handler order");
        f = fixture(h, meat(150), waste(50));
        f.entity.processCycle();
        output(h, f, Items.AIR, 0);
        h.assertTrue(f.source.executions == 0, "Combined different fluids");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void differentCompositionsDoNotCombine(GameTestHelper h) {
        var a = meat(150);
        var b = meat(150);
        a.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(
                        List.of(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:beef", 1))));
        b.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:porkchop", 1))));
        var f = fixture(h, a, b);
        f.entity.processCycle();
        output(h, f, Items.AIR, 0);
        h.assertTrue(f.source.executions == 0, "Combined component identities");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void sourceCapabilityInvalidation(GameTestHelper h) {
        var f = fixture(h, meat(250));
        f.entity.processCycle();
        f.entity.output().extractItem(0, 64, false);
        var replacement = new Source(waste(100));
        attach(h, P.south(), replacement);
        f.entity.processCycle();
        output(h, f, ProductContent.FERTILIZER.get(), 1);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void automaticCadence(GameTestHelper h) {
        var f = fixture(h, meat(10000));
        h.runAfterDelay(21, () -> {
            int count = f.entity.output().getStackInSlot(0).getCount();
            h.assertTrue(
                    count >= 2 && count <= 3 && f.source.executions == count,
                    "Automatic cadence is not one batch per ten ticks");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void bufferCapabilityIntegration(GameTestHelper h) {
        var f = fixture(h, meat(0));
        h.setBlock(P.south(), net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.block());
        var buffer =
                (net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity) h.getBlockEntity(P.south());
        buffer.tank.fill(meat(250), IFluidHandler.FluidAction.EXECUTE);
        f.entity.processCycle();
        output(h, f, ProductContent.RAW_MEAT.get(), 1);
        h.assertTrue(buffer.tank.isEmpty(), "Buffer capability did not drain");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void formLootReplacementAndBlockstateSerialization(GameTestHelper h) {
        var f = fixture(h, waste(1000));
        form(h, ExtruderBlock.Form.BLOCK);
        var state = h.getBlockState(P);
        var restored = net.minecraft.nbt.NbtUtils.readBlockState(
                h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                net.minecraft.nbt.NbtUtils.writeBlockState(state));
        h.assertTrue(restored.equals(state), "Form/facing serialization failed");
        var drops = Block.getDrops(state, h.getLevel(), h.absolutePos(P), f.entity);
        h.assertTrue(
                drops.size() == 1
                        && drops.getFirst().is(ExtruderContent.BLOCK.get().asItem()),
                "Machine loot missing");
        h.setBlock(P, Blocks.AIR);
        h.setBlock(P.below(), Blocks.STONE);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst());
        var pos = h.absolutePos(P.below());
        h.assertTrue(
                drops.getFirst()
                        .useOn(new UseOnContext(
                                player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false)))
                        .consumesAction(),
                "Replacement failed");
        h.assertTrue(
                h.getBlockState(P).getValue(ExtruderBlock.FORM) == ExtruderBlock.Form.BLOCK, "Dropped item lost form");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void bufferAdapterHonorsFiltersAndLifetime(GameTestHelper h) {
        fixture(h, meat(0));
        h.setBlock(P.south(), net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.block());
        var buffer =
                (net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity) h.getBlockEntity(P.south());
        buffer.filter("mobvats:meat");
        var cap =
                h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(P.south()), Direction.NORTH);
        h.assertTrue(cap != null && cap.fill(waste(1000), IFluidHandler.FluidAction.EXECUTE) == 0, "Filter bypassed");
        h.assertTrue(
                cap.fill(meat(1000), IFluidHandler.FluidAction.SIMULATE) == 1000 && buffer.tank.isEmpty(),
                "Fill simulation mutated buffer");
        cap.fill(meat(1000), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(
                cap.drain(meat(250), IFluidHandler.FluidAction.SIMULATE).getAmount() == 250
                        && buffer.tank.getFluidAmount() == 1000,
                "Drain simulation mutated buffer");
        buffer.onChunkUnloaded();
        h.assertTrue(
                cap.drain(meat(250), IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Retained capability drained unloaded buffer");
        buffer.onLoad();
        h.setBlock(P.south(), Blocks.AIR);
        h.assertTrue(
                cap.drain(meat(250), IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Retained capability drained removed buffer");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void creativeTabAndModelVariants(GameTestHelper h) throws Exception {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("mobvats", "mobvats"));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        h.assertTrue(
                tab.getDisplayItems().stream()
                                .filter(s -> s.is(ExtruderContent.BLOCK.get().asItem()))
                                .count()
                        == 1,
                "Extruder missing/duplicated in creative tab");
        var loader = ExtruderGameTests.class.getClassLoader();
        var variants = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                        loader.getResourceAsStream("assets/mobvats/blockstates/extruder.json"),
                        java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("variants");
        h.assertTrue(variants.size() == 8, "Missing directional/form states");
        int rotation = 0;
        for (var facing : List.of("north", "east", "south", "west")) {
            for (var form : List.of("portion", "block")) {
                var variant = variants.getAsJsonObject("facing=" + facing + ",form=" + form);
                h.assertTrue(
                        variant.get("y").getAsInt() == rotation
                                && variant.get("model").getAsString().equals("mobvats:block/extruder_" + form),
                        "Wrong model rotation/form");
            }
            rotation += 90;
        }
        for (var resource : List.of(
                "assets/mobvats/models/block/extruder_portion.json",
                "assets/mobvats/models/block/extruder_block.json",
                "assets/mobvats/models/item/extruder.json",
                "data/mobvats/loot_table/blocks/extruder.json")) {
            try (var stream = loader.getResourceAsStream(resource)) {
                h.assertTrue(stream != null && stream.readAllBytes().length > 0, "Missing resource " + resource);
            }
        }
        h.succeed();
    }
}
