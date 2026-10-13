package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1),
            CONTROLLER = ORIGIN.offset(0, 1, 2),
            INPUT = ORIGIN.offset(2, 1, 0),
            OUTPUT = ORIGIN.offset(4, 1, 2),
            ITEM = ORIGIN.offset(2, 1, 4),
            INTAKE = ORIGIN.offset(0, 2, 2);
    private static final BlockPos IMPELLER = ORIGIN.offset(2, 3, 2);

    static void rig(GameTestHelper h) {
        MachineFixtures.mixerRig(h::setBlock, ORIGIN);
        Slice11Fixtures.mixerStorage(h, ORIGIN);
    }

    private static MultiblockMixer mixer(GameTestHelper h) {
        return ((MixerControllerEntity) h.getBlockEntity(CONTROLLER))
                .getMultiblockController()
                .orElseThrow();
    }

    private static MixerState state() {
        return new MixerState(() -> {});
    }

    private static void rejects(GameTestHelper h, String message) {
        h.runAtTickTime(35, () -> {
            h.assertTrue(!mixer(h).isAssembled(), message);
            h.succeed();
        });
    }

    private static void assertsAssembly(GameTestHelper h, int length) {
        var c = mixer(h);
        h.assertTrue(
                c.isAssembled(),
                "Physical Mixer failed formation: "
                        + c.getLastError().map(e -> e.getChatMessage().getString()));
        var assembly = c.assembly().orElseThrow();
        h.assertTrue(
                assembly.impeller().equals(h.absolutePos(IMPELLER))
                        && assembly.shaftLength() == length
                        && assembly.bottom().equals(h.absolutePos(IMPELLER.below(length))),
                "Assembly geometry differs from built Shaft");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void missingImpellerRejectsFormation(GameTestHelper h) {
        rig(h);
        MachineFixtures.mixerFault(h::setBlock, ORIGIN, MachineFixtures.MixerFault.MISSING_IMPELLER);
        rejects(h, "Mixer formed without Impeller");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void multipleImpellersRejectFormation(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER.east(), MixerContent.state(MixerPartType.Impeller));
        rejects(h, "Mixer formed with two Impellers");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void offCenterImpellerRejectsFormation(GameTestHelper h) {
        rig(h);
        MachineFixtures.mixerFault(h::setBlock, ORIGIN, MachineFixtures.MixerFault.OFF_CENTER_IMPELLER);
        rejects(h, "Off-center assembly formed");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sideImpellerRejectsFormation(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER, MixerContent.state(MixerPartType.Casing));
        h.setBlock(INTAKE, MixerContent.state(MixerPartType.Impeller));
        rejects(h, "Side Impeller formed");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bottomImpellerRejectsFormation(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER, MixerContent.state(MixerPartType.Casing));
        h.setBlock(ORIGIN.offset(2, 0, 2), MixerContent.state(MixerPartType.Impeller));
        rejects(h, "Bottom Impeller formed");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void emptyCenterCannotReplaceShaft(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER.below(), Blocks.AIR);
        h.setBlock(IMPELLER.below(2), Blocks.AIR);
        rejects(h, "Empty center became imaginary Shaft");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void contiguousShaftToLowestInteriorForms(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            assertsAssembly(h, 2);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shortShaftForms(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER.below(2), Blocks.AIR);
        h.runAtTickTime(35, () -> {
            assertsAssembly(h, 1);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void gapImmediatelyBelowImpellerRejectsFormation(GameTestHelper h) {
        rig(h);
        MachineFixtures.mixerFault(h::setBlock, ORIGIN, MachineFixtures.MixerFault.SHAFT_GAP);
        rejects(h, "Gap below Impeller formed");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void offAxisShaftRejectsFormation(GameTestHelper h) {
        rig(h);
        MachineFixtures.mixerFault(h::setBlock, ORIGIN, MachineFixtures.MixerFault.OFF_AXIS_SHAFT);
        rejects(h, "Attached off-axis Shaft formed");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void disconnectedShaftRejectsFormation(GameTestHelper h) {
        rig(h);
        MachineFixtures.mixerFault(h::setBlock, ORIGIN, MachineFixtures.MixerFault.DISCONNECTED_SHAFT);
        rejects(h, "Disconnected Shaft formed");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shaftCannotReplaceBottomShell(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER.below(3).east(), MixerContent.state(MixerPartType.Shaft));
        rejects(h, "Off-axis Shaft replaced bottom shell");
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void impellerAndShaftBreakRepairRebuildGeometryAndStorage(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            assertsAssembly(h, 2);
            fill(mixer(h).state(), 1000, 0);
            h.setBlock(IMPELLER, Blocks.AIR);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(!mixer(h).isAssembled() && mixer(h).assembly().isEmpty(), "Broken Impeller retained assembly");
            h.setBlock(IMPELLER, MixerContent.state(MixerPartType.Impeller));
        });
        h.runAtTickTime(90, () -> {
            assertsAssembly(h, 2);
            h.assertTrue(mixer(h).state().amount(0) == 1000, "Impeller repair lost storage");
            h.setBlock(IMPELLER.below(), Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    !mixer(h).isAssembled() && mixer(h).assembly().isEmpty(), "Broken upper Shaft retained assembly");
            h.setBlock(IMPELLER.below(), MixerContent.state(MixerPartType.Shaft));
        });
        h.runAtTickTime(150, () -> {
            assertsAssembly(h, 2);
            h.setBlock(IMPELLER.below(2), Blocks.AIR);
        });
        h.runAtTickTime(180, () -> {
            assertsAssembly(h, 1);
            h.setBlock(IMPELLER.below(2), MixerContent.state(MixerPartType.Shaft));
        });
        h.runAtTickTime(210, () -> {
            assertsAssembly(h, 2);
            h.assertTrue(mixer(h).state().amount(0) == 1000, "Shaft repairs lost storage");
            h.succeed();
        });
    }

    private static void structuralRig(GameTestHelper h, int width, int height, int depth) {
        MachineFixtures.mixer(h::setBlock, ORIGIN, width, height, depth, height - 2, 2, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void internalGapRejectsFormation(GameTestHelper h) {
        structuralRig(h, 5, 6, 5);
        h.setBlock(ORIGIN.offset(2, 3, 2), Blocks.AIR);
        rejects(h, "Internal gap accepted disconnected lower Shaft");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void maximumHeightShaftReachesLowestInterior(GameTestHelper h) {
        structuralRig(h, 5, 9, 5);
        h.runAtTickTime(35, () -> {
            var c = mixer(h);
            h.assertTrue(
                    c.isAssembled()
                            && c.assembly().orElseThrow().shaftLength() == 7
                            && c.assembly().orElseThrow().bottom().equals(h.absolutePos(ORIGIN.offset(2, 1, 2))),
                    "Full maximum-height Shaft failed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void evenWidthCannotCenterImpeller(GameTestHelper h) {
        structuralRig(h, 4, 4, 5);
        rejects(h, "Even-width Mixer accepted uncentered Impeller");
    }

    private static void fill(MixerState state, int water, int waste) {
        if (water > 0) state.fluids(true).fill(new FluidStack(Fluids.WATER, water), FluidAction.EXECUTE);
        if (waste > 0) state.fluids(true).fill(new FluidStack(ProductFluids.WASTE.get(), waste), FluidAction.EXECUTE);
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void mixerFormsAndWaterBucketXpInteractions(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var c = mixer(h);
            h.assertTrue(c.isAssembled(), "Mixer did not form");
            var cap =
                    h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(INPUT), Direction.NORTH);
            h.assertTrue(
                    cap != null
                            && cap.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000
                            && c.state().amount(0) == 0,
                    "Water simulation mutated Mixer");
            h.assertTrue(
                    cap.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000
                            && cap.fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000), FluidAction.EXECUTE)
                                    == 0,
                    "Mixer water acceptance/output rejection wrong");
            var player = h.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            var pos = h.absolutePos(INPUT);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
            h.getLevel()
                    .getBlockState(pos)
                    .useItemOn(
                            player.getItemInHand(InteractionHand.MAIN_HAND),
                            h.getLevel(),
                            player,
                            InteractionHand.MAIN_HAND,
                            hit);
            h.assertTrue(
                    c.state().amount(0) == 2000
                            && player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET),
                    "Water Bucket did not fill/return Bucket through block interaction");
            cap.fill(new FluidStack(Fluids.WATER, 6000), FluidAction.EXECUTE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            h.getLevel()
                    .getBlockState(pos)
                    .useItemOn(
                            player.getItemInHand(InteractionHand.MAIN_HAND),
                            h.getLevel(),
                            player,
                            InteractionHand.MAIN_HAND,
                            hit);
            h.assertTrue(
                    c.state().amount(0) == 8000
                            && player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET),
                    "Full tank consumed bucket");
            player.giveExperiencePoints(15);
            h.assertTrue(
                    c.depositPlayerXp(player) && c.state().experienceMb() == 260 && player.totalExperience == 2,
                    "Player XP deposit lost/duplicated points");
            var input =
                    h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(ITEM), Direction.SOUTH);
            h.assertTrue(
                    input != null
                            && input.insertItem(0, new ItemStack(Items.CARROT), false)
                                    .isEmpty()
                            && input.extractItem(0, 1, false).isEmpty(),
                    "Mixer Item Input policy wrong");
            h.assertTrue(
                    c.state().status(true).contains("Water:")
                            && c.state().status(true).contains("Recipe:")
                            && c.state().status(true).contains("Plants:"),
                    "Mixer status missing state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mixerRejectsInvalidInteriorAndRecovers(GameTestHelper h) {
        rig(h);
        var interior = ORIGIN.offset(3, 2, 3);
        h.setBlock(interior, Blocks.STONE);
        h.runAtTickTime(30, () -> {
            h.assertTrue(!mixer(h).isAssembled(), "Mixer accepted obstructed interior");
            h.setBlock(interior, Blocks.AIR);
            h.setBlock(ORIGIN, Blocks.AIR);
        });
        h.runAtTickTime(31, () -> h.setBlock(ORIGIN, MixerContent.state(MixerPartType.Casing)));
        h.runAtTickTime(60, () -> {
            h.assertTrue(mixer(h).isAssembled(), "Mixer did not recover after correction");
            h.setBlock(ORIGIN, Blocks.AIR);
        });
        h.runAtTickTime(85, () -> {
            h.assertTrue(!mixer(h).isAssembled(), "Missing casing frame accepted");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void waterIntakeRequiresSourceAndPreservesSource(GameTestHelper h) {
        rig(h);
        h.setBlock(INTAKE, MixerContent.state(MixerPartType.Intake));
        h.setBlock(
                INTAKE.above(),
                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.state(
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.FLUID,
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.CASING,
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitShell.MIXER)
                        .setValue(
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock.MODE,
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitMode.TAP));
        var water = INTAKE.west();
        h.setBlock(
                water, Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 7));
        h.runAtTickTime(30, () -> {
            h.assertTrue(mixer(h).state().amount(0) == 0, "Intake accepted flowing water");
            h.setBlock(water, Blocks.WATER);
        });
        final int[] amount = {0};
        h.runAtTickTime(65, () -> {
            amount[0] = mixer(h).state().amount(0);
            h.assertTrue(
                    amount[0] >= 500
                            && h.getLevel().getFluidState(h.absolutePos(water)).isSource(),
                    "Intake failed source automation or destroyed source");
            h.setBlock(water, Blocks.AIR);
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(mixer(h).state().amount(0) == amount[0], "Intake continued without adjacent source");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void experienceUsesExactMillibuckets(GameTestHelper h) {
        var c = state();
        fill(c, 1000, 0);
        var recipe = MixerRecipeRegistry.recipe("culture_slurry");
        h.assertTrue(
                c.depositExperienceMb(249) && !c.commit(recipe) && c.experienceMb() == 249 && c.amount(0) == 1000,
                "Insufficient mB partially consumed inputs");
        h.assertTrue(c.depositExperienceMb(12), "Non-point-aligned XP rejected");
        var restored = state();
        restored.load(c.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(
                restored.experienceMb() == 261 && restored.status(true).contains("XP: 261 mB"),
                "XP quantity rounded during persistence/presentation");
        h.assertTrue(
                restored.commit(recipe)
                        && restored.experienceMb() == 11
                        && restored.amount(2) == 1000
                        && restored.metrics().total("experienceConsumedMb") == 250,
                "Exact recipe consumption lost the XP remainder");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void cultureAtomicInputsBlockedOutputAndPersistence(GameTestHelper h) {
        var c = state();
        fill(c, 1000, 0);
        h.assertTrue(
                !c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "culture_slurry"))
                        && c.amount(0) == 1000
                        && c.amount(2) == 0,
                "Missing XP consumed Water");
        c.depositExperienceMb(250);
        h.assertTrue(
                c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "culture_slurry"))
                        && c.amount(0) == 0
                        && c.experienceMb() == 0
                        && c.amount(2) == 1000,
                "Culture transaction did not conserve inputs/output");
        c.depositExperienceMb(250);
        h.assertTrue(
                !c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "culture_slurry"))
                        && c.experienceMb() == 250,
                "Missing Water consumed XP");
        fill(c, 7000, 0);
        c.depositExperienceMb(1500);
        for (int i = 0; i < 7; i++)
            h.assertTrue(
                    c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                            "culture_slurry")),
                    "Culture fill preparation failed");
        fill(c, 1000, 0);
        c.depositExperienceMb(250);
        h.assertTrue(
                !c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "culture_slurry"))
                        && c.amount(0) == 1000
                        && c.experienceMb() == 250
                        && c.amount(2) == 8000
                        && c.metrics().total("waterConsumed") == 8000,
                "Blocked Culture partially consumed inputs or inflated metrics");
        var data = c.save(h.getLevel().registryAccess());
        var reloaded = state();
        reloaded.load(data, h.getLevel().registryAccess());
        h.assertTrue(reloaded.amount(2) == 8000 && reloaded.experienceMb() == 250, "Culture failed save/reload");
        h.assertTrue(
                reloaded.fluids(false)
                                        .drain(
                                                reloaded.fluids(false).drain(1000, FluidAction.SIMULATE),
                                                FluidAction.EXECUTE)
                                        .getAmount()
                                == 1000
                        && reloaded.commit(
                                net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                        .recipe("culture_slurry")),
                "Culture did not resume after export");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void feedingAtomicPlantsAndBlockedOutput(GameTestHelper h) {
        for (var plant : List.of(
                Items.WHEAT,
                Items.WHEAT_SEEDS,
                Items.POTATO,
                Items.CARROT,
                Items.BEETROOT,
                Items.KELP,
                Items.BEETROOT_SEEDS,
                Items.PUMPKIN_SEEDS,
                Items.MELON_SEEDS)) {
            var c = state();
            fill(c, 1000, 0);
            h.assertTrue(
                    c.itemInput().insertItem(0, new ItemStack(plant), false).isEmpty(), "Plant tag rejected " + plant);
            h.assertTrue(
                    !c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                    "feeding_slurry"))
                            && c.amount(0) == 1000
                            && c.plantCount() == 1,
                    "Missing WASTE consumed other inputs");
            fill(c, 0, 250);
            h.assertTrue(
                    c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                    "feeding_slurry"))
                            && c.amount(0) == 0
                            && c.amount(1) == 0
                            && c.plantCount() == 0
                            && c.amount(3) == 1000,
                    "Feeding transaction failed " + plant);
        }
        var c = state();
        fill(c, 8000, 2000);
        h.assertTrue(
                !c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "feeding_slurry"))
                        && c.amount(0) == 8000
                        && c.amount(1) == 2000,
                "Missing plant consumed fluids");
        h.assertTrue(
                !c.itemInput().insertItem(0, new ItemStack(Items.DIRT), false).isEmpty(), "Nonplant input accepted");
        c.itemInput().insertItem(0, new ItemStack(Items.CARROT, 9), false);
        for (int i = 0; i < 8; i++)
            c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                    "feeding_slurry"));
        fill(c, 1000, 250);
        h.assertTrue(
                !c.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "feeding_slurry"))
                        && c.amount(0) == 1000
                        && c.amount(1) == 250
                        && c.plantCount() == 1
                        && c.amount(3) == 8000
                        && c.metrics().total("plantsConsumed") == 8,
                "Blocked Feeding partially consumed inputs");
        var saved = c.save(h.getLevel().registryAccess());
        var loaded = state();
        loaded.load(saved, h.getLevel().registryAccess());
        h.assertTrue(
                loaded.amount(3) == 8000 && loaded.amount(1) == 250 && loaded.plantCount() == 1,
                "Feeding/input persistence failed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void mixerAssimilationConservesItemsFluidsAndXp(GameTestHelper h) {
        var a = state();
        var b = state();
        fill(a, 8000, 1000);
        fill(b, 8000, 1000);
        a.depositExperienceMb(250);
        b.depositExperienceMb(500);
        var plants = List.of(Items.CARROT, Items.POTATO, Items.WHEAT, Items.KELP, Items.BEETROOT, Items.WHEAT_SEEDS);
        for (int i = 0; i < 4; i++) a.itemInput().insertItem(i, new ItemStack(plants.get(i), 64), false);
        b.itemInput().insertItem(0, new ItemStack(plants.get(4), 64), false);
        b.itemInput().insertItem(1, new ItemStack(plants.get(5), 64), false);
        a.assimilate(b);
        b.clear();
        var saved = a.save(h.getLevel().registryAccess());
        var loaded = state();
        loaded.load(saved, h.getLevel().registryAccess());
        h.assertTrue(
                loaded.amount(0) == 16000
                        && loaded.amount(1) == 2000
                        && loaded.experienceMb() == 750
                        && loaded.plantCount() == 384
                        && loaded.itemInput().getSlots() == 6
                        && b.plantCount() == 0
                        && b.amount(0) == 0
                        && b.experienceMb() == 0,
                "Mixer reconnection lost/duplicated storage");
        h.assertTrue(
                loaded.fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0
                        && loaded.commit(
                                net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                        .recipe("feeding_slurry"))
                        && loaded.amount(0) == 15000
                        && loaded.plantCount() == 383,
                "Overflow fill/recipe policy failed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mixerCadenceAndProgressPersistence(GameTestHelper h) {
        rig(h);
        h.setBlock(IMPELLER.east(), MixerContent.state(MixerPartType.Drive));
        h.runAtTickTime(30, () -> {
            fill(mixer(h).state(), 2000, 250);
            mixer(h).state().depositExperienceMb(250);
            mixer(h).state().itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
        });
        h.runAtTickTime(65, () -> {
            var c = mixer(h);
            c.syncDataFrom(
                    c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync),
                    h.getLevel().registryAccess(),
                    SyncReason.FullSync);
            MixerCombustionGameTests.ticks(c, c.spinUpTicks());
            MixerCombustionGameTests.ticks(c, 30);
            h.assertTrue(
                    c.state().progress() > 0
                            && c.state().amount(0) == 2000
                            && c.state().plantCount() == 1,
                    "Progress consumed recipe inputs early: " + c.state().status(true));
            var data = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            int progress = c.state().progress();
            c.syncDataFrom(data, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(c.state().progress() == progress, "Mixer progress lost on reload");
        });
        h.runAtTickTime(95, () -> {
            var c = mixer(h);
            MixerCombustionGameTests.ticks(c, c.spinUpTicks() + 400);
            h.assertTrue(
                    c.state().amount(0) == 0
                            && c.state().amount(1) == 0
                            && c.state().amount(2) == 1000
                            && c.state().amount(3) == 1000
                            && c.state().experienceMb() == 0
                            && c.state().plantCount() == 0,
                    "Central cadence failed both recipes");
            var out =
                    h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(OUTPUT), Direction.EAST);
            h.assertTrue(
                    out != null
                            && out.drain(
                                                    java.util.stream.IntStream.range(0, out.getTanks())
                                                            .mapToObj(out::getFluidInTank)
                                                            .filter(s -> s.is(ProductFluids.FEEDING_SLURRY.get()))
                                                            .findFirst()
                                                            .orElseThrow()
                                                            .copyWithAmount(250),
                                                    FluidAction.EXECUTE)
                                            .getAmount()
                                    == 250,
                    "Mixer output capability failed explicit slurry export");
            h.assertTrue(
                    c.state().metrics().total("waterConsumed") == 2000
                            && c.state().metrics().total("experienceConsumedMb") == 250
                            && c.state().metrics().total("plantsConsumed") == 1,
                    "Recipe metrics incorrect");
            h.succeed();
        });
    }
}
