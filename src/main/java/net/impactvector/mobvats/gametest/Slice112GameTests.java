package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class Slice112GameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            DRAIN = O.offset(2, 0, 2),
            PORT = DRAIN.west(),
            BLENDER = O.offset(2, 1, 4);

    private static FluidStack fluid(Fluid f, int n) {
        return new FluidStack(f, n);
    }

    private static void rig(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ResourceLocation.withDefaultNamespace("cow"));
        h.setBlock(PORT, Content.state(VatPartType.FluidPort));
        ((VatFluidPortEntity) h.getBlockEntity(PORT)).setIoDirection(IoDirection.Output);
        h.setBlock(DRAIN.east(), Content.state(VatPartType.Casing));
    }

    private static MultiblockVat vat(GameTestHelper h) {
        var vat = ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
        h.assertTrue(vat.isAssembled(), "Vat fixture unformed");
        return vat;
    }

    private static ServiceStaging stage(GameTestHelper h) {
        return ((VatSumpEntity) h.getBlockEntity(DRAIN)).service();
    }

    private static void cycles(MultiblockVat v) {
        for (int i = 0; i < VatProcessEngine.CADENCE; i++) v.processEngine().tick();
    }

    private static void ready(MultiblockVat v, GameTestHelper h, String id) {
        var s = v.processEngine().state(h.absolutePos(HEAD)).process(id);
        s.phase = ProcessState.Phase.READY;
        s.elapsed = 1200;
    }

    private static Player player(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void click(GameTestHelper h, BlockPos pos, Player p, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        var absolute = h.absolutePos(pos);
        h.getBlockState(pos)
                .useItemOn(
                        held,
                        h.getLevel(),
                        p,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static FluidBufferEntity buffer(GameTestHelper h, BlockPos p, String filter) {
        h.setBlock(p, FluidBufferContent.state(ConduitCladding.CASING));
        var b = (FluidBufferEntity) h.getBlockEntity(p);
        b.filter(filter);
        return b;
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 70)
    public static void wasteMilkAndExperienceHaveIndependentBoundedService(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var v = vat(h);
            var waste = stage(h).tank(fluid(ProductFluids.WASTE.get(), 1));
            var xp = stage(h).tank(fluid(ProductFluids.EXPERIENCE.get(), 1));
            int milkYield = (int) Math.floor(1000 * v.yieldMultiplier(h.absolutePos(HEAD)));
            ready(v, h, PassiveProcessCompiler.WASTE);
            cycles(v);
            h.assertTrue(
                    waste.getFluidAmount() == (int) Math.floor(250 * v.yieldMultiplier(h.absolutePos(HEAD)))
                            && v.storage().clusters().isEmpty(),
                    "Scaled Waste missing without bulk storage");
            xp.fill(fluid(ProductFluids.EXPERIENCE.get(), 250), FluidAction.EXECUTE);
            ready(v, h, PassiveProcessCompiler.MILK);
            cycles(v);
            var milk = stage(h).tank(fluid(NeoForgeMod.MILK.get(), 1));
            h.assertTrue(
                    milk.getFluidAmount() == milkYield
                            && xp.fill(fluid(ProductFluids.EXPERIENCE.get(), 1000), FluidAction.EXECUTE) == 750,
                    "One byproduct reduced another allowance");
            waste.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
            milk.fill(fluid(NeoForgeMod.MILK.get(), 2000), FluidAction.EXECUTE);
            ready(v, h, PassiveProcessCompiler.MILK);
            ready(v, h, PassiveProcessCompiler.WASTE);
            cycles(v);
            h.assertTrue(
                    v.processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.MILK).phase
                                    == ProcessState.Phase.READY
                            && waste.getFluidAmount() == 1000,
                    "Full service output was voided/reset");
            ((VatFluidPortEntity) h.getBlockEntity(PORT)).filter("minecraft:milk");
            var p = player(h);
            click(h, PORT, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    p.getMainHandItem().is(Items.MILK_BUCKET) && milk.getFluidAmount() == 1000,
                    "Milk retrieval changed container count");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 70)
    public static void formedOutputBucketsSelectFilteredAndStableMultiFluid(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            vat(h);
            var port = (VatFluidPortEntity) h.getBlockEntity(PORT);
            var waste = stage(h).tank(fluid(ProductFluids.WASTE.get(), 1));
            var xp = stage(h).tank(fluid(ProductFluids.EXPERIENCE.get(), 1));
            waste.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
            xp.fill(fluid(ProductFluids.EXPERIENCE.get(), 1000), FluidAction.EXECUTE);
            port.filter("mobvats:waste");
            var p = player(h);
            click(h, PORT, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    waste.isEmpty()
                            && xp.getFluidAmount() == 1000
                            && FluidUtil.getFluidContained(p.getMainHandItem())
                                    .orElseThrow()
                                    .is(ProductFluids.WASTE.get()),
                    "Filtered port extracted wrong stage/container");
            waste.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
            port.filter("");
            h.assertTrue(
                    port.externalFluids().drain(1000, FluidAction.SIMULATE).is(ProductFluids.EXPERIENCE.get())
                            && xp.getFluidAmount() == 1000,
                    "Resource ordering/simulation changed");
            click(h, PORT, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    xp.isEmpty()
                            && waste.getFluidAmount() == 1000
                            && FluidUtil.getFluidContained(p.getMainHandItem())
                                    .orElseThrow()
                                    .is(ProductFluids.EXPERIENCE.get()),
                    "Unfiltered extraction was not deterministic");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 70)
    public static void formedInputContainersAreFilteredAndAtomic(GameTestHelper h) {
        rig(h);
        var store = buffer(h, PORT.north(), "minecraft:milk");
        h.runAtTickTime(30, () -> {
            vat(h);
            var port = (VatFluidPortEntity) h.getBlockEntity(PORT);
            port.setIoDirection(IoDirection.Input);
            port.filter("minecraft:milk");
            var p = player(h);
            click(h, PORT, p, new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET), "Rejected input transformed container");
            click(h, PORT, p, new ItemStack(Items.MILK_BUCKET));
            var milk = store.tank;
            h.assertTrue(
                    stage(h).tank(fluid(NeoForgeMod.MILK.get(), 1)).isEmpty(),
                    "Input filled Producer escrow instead of Store");
            h.assertTrue(
                    milk.getFluidAmount() == 1000 && p.getMainHandItem().is(Items.BUCKET),
                    "Matching input failed to transform");
            milk.fill(fluid(NeoForgeMod.MILK.get(), 3000), FluidAction.EXECUTE);
            milk.drain(250, FluidAction.EXECUTE);
            click(h, PORT, p, new ItemStack(Items.MILK_BUCKET));
            h.assertTrue(
                    milk.getFluidAmount() == 3750 && p.getMainHandItem().is(Items.MILK_BUCKET),
                    "Partial input lost fluid/container");
            milk.drain(3000, FluidAction.EXECUTE);
            port.setIoDirection(IoDirection.Output);
            click(h, PORT, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    milk.getFluidAmount() == 750 && p.getMainHandItem().is(Items.BUCKET), "Partial output lost fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 70)
    public static void servicePersistsAndEvacuatesAfterCompatibleStorageAppears(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var v = vat(h);
            var owner = (VatSumpEntity) h.getBlockEntity(DRAIN);
            stage(h).tank(fluid(ProductFluids.WASTE.get(), 1))
                    .fill(fluid(ProductFluids.WASTE.get(), 750), FluidAction.EXECUTE);
            stage(h).tank(fluid(ProductFluids.EXPERIENCE.get(), 1))
                    .fill(fluid(ProductFluids.EXPERIENCE.get(), 1000), FluidAction.EXECUTE);
            stage(h).tank(fluid(NeoForgeMod.MILK.get(), 1))
                    .fill(fluid(NeoForgeMod.MILK.get(), 375), FluidAction.EXECUTE);
            var saved = owner.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            owner.service().load(new CompoundTag(), h.getLevel().registryAccess());
            owner.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    stage(h).tank(fluid(ProductFluids.WASTE.get(), 1)).getFluidAmount() == 750
                            && stage(h).tank(fluid(ProductFluids.EXPERIENCE.get(), 1))
                                            .getFluidAmount()
                                    == 1000
                            && stage(h).tank(fluid(NeoForgeMod.MILK.get(), 1)).getFluidAmount() == 375,
                    "Endpoint save/load lost independent stages");
            var b = buffer(h, DRAIN.north(), "minecraft:water");
            v.rebuildTopology();
            v.storage().evacuate();
            h.assertTrue(
                    b.tank.isEmpty()
                            && stage(h).tank(fluid(ProductFluids.WASTE.get(), 1))
                                            .getFluidAmount()
                                    == 750,
                    "Incompatible buffer evacuated waste");
            b.filter("mobvats:waste");
            v.rebuildTopology();
            v.storage().evacuate();
            h.assertTrue(
                    b.tank.getFluidAmount() == 750
                            && stage(h).tank(fluid(ProductFluids.WASTE.get(), 1))
                                    .isEmpty()
                            && stage(h).tank(fluid(ProductFluids.EXPERIENCE.get(), 1))
                                            .getFluidAmount()
                                    == 1000,
                    "Compatible storage did not resume exact evacuation after reload");
            h.setBlock(
                    PORT.north(),
                    ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
            v.rebuildTopology();
            var port = (VatFluidPortEntity) h.getBlockEntity(PORT);
            port.filter("mobvats:waste");
            b.tank.fill(fluid(ProductFluids.WASTE.get(), 250), FluidAction.EXECUTE);
            var p = player(h);
            click(h, PORT, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    b.tank.isEmpty()
                            && FluidUtil.getFluidContained(p.getMainHandItem())
                                    .orElseThrow()
                                    .is(ProductFluids.WASTE.get()),
                    "Port could not bucket physical bulk storage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 100)
    public static void stageSurvivesBreakReformationAndControllerRebind(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var v = vat(h);
            stage(h).tank(fluid(ProductFluids.WASTE.get(), 1))
                    .fill(fluid(ProductFluids.WASTE.get(), 750), FluidAction.EXECUTE);
            var engine = new VatProcessEngine(v);
            engine.assembled();
            v.processEngine().assimilate(engine);
            v.rebuildTopology();
            v.rebuildTopology();
            h.assertTrue(
                    stage(h).tank(fluid(ProductFluids.WASTE.get(), 1)).getFluidAmount() == 750,
                    "Assimilation/rebind copied or discarded endpoint contents");
            h.setBlock(O, Blocks.AIR);
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(
                    !((VatSumpEntity) h.getBlockEntity(DRAIN)).isMachineAssembled(), "Break fixture remained formed");
            h.setBlock(O, Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(80, () -> {
            vat(h);
            h.assertTrue(
                    stage(h).tank(fluid(ProductFluids.WASTE.get(), 1)).getFluidAmount() == 750,
                    "Machine reformation lost service ownership");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 70)
    public static void blenderHarvestCommitsOneBucketWithoutBulkOrSword(GameTestHelper h) {
        rig(h);
        h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        h.setBlock(BLENDER.east(), Content.state(VatPartType.FluidPort));
        ((VatFluidPortEntity) h.getBlockEntity(BLENDER.east())).setIoDirection(IoDirection.Output);
        var feed = O.offset(2, 1, 0);
        h.setBlock(feed, Content.state(VatPartType.FeedPort));
        h.setBlock(feed.east(), Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(feed.east())).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        h.runAtTickTime(35, () -> {
            var v = vat(h);
            var blender = (VatBlenderEntity) h.getBlockEntity(BLENDER);
            h.assertTrue(
                    blender.service().tank(fluid(ProductFluids.MEAT.get(), 1)).getFluidAmount()
                                    == (int) Math.floor(1000 * v.yieldMultiplier(h.absolutePos(HEAD)))
                            && v.metrics().total(MachineMetrics.BLENDER_HARVESTS) == 1
                            && v.killOutputs().isEmpty(),
                    "Blender did not retain completed harvest independently of Sword");
            var saved = blender.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            blender.service().load(new CompoundTag(), h.getLevel().registryAccess());
            blender.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    blender.service().tank(fluid(ProductFluids.MEAT.get(), 1)).getFluidAmount()
                            == (int) Math.floor(1000 * v.yieldMultiplier(h.absolutePos(HEAD))),
                    "Meat staging lost on reload");
            var p = player(h);
            click(h, BLENDER.east(), p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    p.getMainHandItem().is(ProductFluids.MEAT_BUCKET.get())
                            && blender.service()
                                            .tank(fluid(ProductFluids.MEAT.get(), 1))
                                            .getFluidAmount()
                                    == (int) Math.floor(1000 * v.yieldMultiplier(h.absolutePos(HEAD))) - 1000,
                    "Meat stage was not bucketable");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void mixerRecipesRetainIndependentServiceAndPersistAtOwner(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        h.setBlock(
                p,
                net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.state(
                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Controller));
        var owner =
                (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity) h.getBlockEntity(p);
        var culture = owner.service().tank(fluid(ProductFluids.CULTURE_SLURRY.get(), 1));
        var feeding = owner.service().tank(fluid(ProductFluids.FEEDING_SLURRY.get(), 1));
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(), ConduitTopology.EMPTY);
        storage.services(Map.of(owner.getBlockPos(), owner.service()));
        var state = new MixerState(() -> {});
        state.fluids(true).fill(fluid(Fluids.WATER, 4000), FluidAction.EXECUTE);
        state.fluids(true).fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        state.depositExperienceMb(500);
        state.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        // Input capacity is physical; the fixture supplies it separately from output staging.
        var water = buffer(h, p.west(), "minecraft:water");
        var waste = buffer(h, p.east(), "mobvats:waste");
        var xp = buffer(h, p.north(), "mobvats:experience");
        storage.compile(List.of(water, waste, xp), ConduitTopology.EMPTY);
        state.bind(storage, owner.getBlockPos());
        h.assertTrue(
                state.commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                                "culture_slurry"))
                        && state.commit(
                                net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                        .recipe("feeding_slurry"))
                        && culture.getFluidAmount() == 1000
                        && feeding.getFluidAmount() == 1000
                        && !state.commit(
                                net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                        .recipe("culture_slurry")),
                "Mixer recipes failed independent bounded output stages");
        var saved = owner.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        owner.service().load(new CompoundTag(), h.getLevel().registryAccess());
        owner.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
        storage.services(Map.of(owner.getBlockPos(), owner.service()));
        state.bind(storage, owner.getBlockPos());
        h.assertTrue(state.amount(2) == 1000 && state.amount(3) == 1000, "Mixer stage reload lost/duplicated slurry");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void genericContainerFullPreflightAndStackedBucketStowing(GameTestHelper h) {
        var stage = new ServiceStaging(() -> {});
        var tank = stage.tank(fluid(ProductFluids.WASTE.get(), 1));
        tank.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        var p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PRISMARINE_SHARD));
        FluidContainerInteraction.interact(
                p, InteractionHand.MAIN_HAND, PhysicalFluidStorage.handler(stage.tanks(), false, s -> true), false);
        h.assertTrue(
                tank.getFluidAmount() == 1000
                        && FluidUtil.getFluidContained(p.getMainHandItem()).isEmpty(),
                "Insufficient full generic transfer was partly committed");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
        FluidContainerInteraction.interact(
                p, InteractionHand.MAIN_HAND, PhysicalFluidStorage.handler(stage.tanks(), false, s -> true), false);
        h.assertTrue(
                tank.isEmpty()
                        && p.getMainHandItem().getCount() == 2
                        && p.getInventory().countItem(ProductFluids.WASTE_BUCKET.get()) == 1,
                "Stacked output lost/duplicated/stowed incorrectly");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void standardCapabilityContainerTransfersWholeContents(GameTestHelper h) {
        var b = buffer(h, new BlockPos(3, 2, 3), "minecraft:water");
        var network = PhysicalFluidStorage.handler(List.of(b.tank), true, s -> s.is(Fluids.WATER));
        var p = player(h);
        var held = new ItemStack(Items.PRISMARINE_SHARD);
        FluidUtil.getFluidHandler(held).orElseThrow().fill(fluid(Fluids.WATER, 1500), FluidAction.EXECUTE);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        FluidContainerInteraction.interact(p, InteractionHand.MAIN_HAND, network, true);
        h.assertTrue(
                b.tank.getFluidAmount() == 1500
                        && FluidUtil.getFluidContained(p.getMainHandItem()).isEmpty(),
                "Generic input did not transfer/transform whole contents");
        FluidContainerInteraction.interact(
                p, InteractionHand.MAIN_HAND, PhysicalFluidStorage.handler(List.of(b.tank), false, s -> true), false);
        h.assertTrue(
                b.tank.isEmpty()
                        && FluidUtil.getFluidContained(p.getMainHandItem())
                                        .orElseThrow()
                                        .getAmount()
                                == 1500,
                "Generic output lost/duplicated contents");
        b.tank.fill(fluid(Fluids.WATER, 3000), FluidAction.EXECUTE);
        var before = p.getMainHandItem().copy();
        FluidContainerInteraction.interact(p, InteractionHand.MAIN_HAND, network, true);
        h.assertTrue(
                b.tank.getFluidAmount() == 3000 && ItemStack.matches(before, p.getMainHandItem()),
                "Insufficient generic capacity partly emptied container");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void actualControllerAssimilationRetainsSeparateEndpointStages(GameTestHelper h) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, Content.state(VatPartType.Sump));
        h.setBlock(p.east(4), Content.state(VatPartType.Sump));
        var a = (VatSumpEntity) h.getBlockEntity(p);
        var b = (VatSumpEntity) h.getBlockEntity(p.east(4));
        a.service()
                .tank(fluid(ProductFluids.WASTE.get(), 1))
                .fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        b.service()
                .tank(fluid(ProductFluids.WASTE.get(), 1))
                .fill(fluid(ProductFluids.WASTE.get(), 750), FluidAction.EXECUTE);
        var target = new MultiblockVat(h.getLevel());
        var source = new MultiblockVat(h.getLevel());
        target.attachPart(a);
        source.attachPart(b);
        target.assimilateController(source);
        h.assertTrue(
                target.containsPart(a)
                        && target.containsPart(b)
                        && a.service().tank(fluid(ProductFluids.WASTE.get(), 1)).getFluidAmount() == 1000
                        && b.service().tank(fluid(ProductFluids.WASTE.get(), 1)).getFluidAmount() == 750,
                "Controller assimilation lost/copied/combined bounded endpoint state");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 70)
    public static void formedMixerBucketsBothSlurriesWithoutOutputBuffers(GameTestHelper h) {
        MixerGameTests.rig(h);
        for (var pos : BlockPos.betweenClosed(O, O.offset(4, 3, 4)))
            if (h.getLevel().getBlockEntity(h.absolutePos(pos)) instanceof FluidBufferEntity b
                    && (b.filter().equals("mobvats:culture_slurry")
                            || b.filter().equals("mobvats:feeding_slurry")))
                h.setBlock(
                        pos,
                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.state(
                                net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Casing));
        h.runAtTickTime(30, () -> {
            var owner = (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity)
                    h.getBlockEntity(O.offset(0, 1, 2));
            var mixer = owner.getMultiblockController().orElseThrow();
            h.assertTrue(mixer.isAssembled(), "Mixer fixture unformed");
            var state = mixer.state();
            state.fluids(true).fill(fluid(Fluids.WATER, 4000), FluidAction.EXECUTE);
            state.fluids(true).fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
            state.depositExperienceMb(500);
            state.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 64), false);
            h.assertTrue(
                    state.commit(
                                    net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                            .recipe("culture_slurry"))
                            && state.commit(
                                    net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                            .recipe("feeding_slurry"))
                            && state.amount(2) == 1000
                            && state.amount(3) == 1000,
                    "Mixer outputs required bulk output storage");
            var output = O.offset(4, 1, 2);
            var port = (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPortEntity)
                    h.getBlockEntity(output);
            var p = player(h);
            port.filter("mobvats:culture_slurry");
            click(h, output, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    p.getMainHandItem().is(ProductFluids.CULTURE_SLURRY_BUCKET.get())
                            && state.amount(2) == 0
                            && state.amount(3) == 1000,
                    "Culture stage not bucketable through Mixer output");
            port.filter("mobvats:feeding_slurry");
            click(h, output, p, new ItemStack(Items.BUCKET));
            h.assertTrue(
                    p.getMainHandItem().is(ProductFluids.FEEDING_SLURRY_BUCKET.get()) && state.amount(3) == 0,
                    "Feeding stage not bucketable through Mixer output");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void fullBulkStorageRetainsStageAndEvacuationResumes(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        h.setBlock(p, Content.state(VatPartType.Sump));
        var owner = (VatSumpEntity) h.getBlockEntity(p);
        var staged = owner.service().tank(fluid(ProductFluids.WASTE.get(), 1));
        staged.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        var bulk = buffer(h, p.east(), "mobvats:waste");
        bulk.tank.fill(fluid(ProductFluids.WASTE.get(), 4000), FluidAction.EXECUTE);
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(bulk), ConduitTopology.EMPTY);
        storage.services(Map.of(owner.getBlockPos(), owner.service()));
        h.assertTrue(
                !storage.evacuate() && staged.getFluidAmount() == 1000 && bulk.tank.getFluidAmount() == 4000,
                "Full bulk storage voided stage");
        bulk.tank.drain(500, FluidAction.EXECUTE);
        h.assertTrue(
                storage.evacuate() && staged.getFluidAmount() == 500 && bulk.tank.getFluidAmount() == 4000,
                "Evacuation did not resume within available capacity");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void producingEndpointDropsAndPlacementPreserveService(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        var registry = h.getLevel().registryAccess();
        for (var block : List.of(
                Content.block(VatPartType.Sump),
                Content.block(VatPartType.Blender),
                net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.block(
                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Controller))) {
            h.setBlock(p, block);
            var owner = h.getBlockEntity(p);
            var service = owner instanceof AbstractVatEntity vat
                    ? vat.service()
                    : ((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity) owner)
                            .service();
            service.tank(fluid(ProductFluids.WASTE.get(), 1))
                    .fill(fluid(ProductFluids.WASTE.get(), 750), FluidAction.EXECUTE);
            service.tank(fluid(ProductFluids.EXPERIENCE.get(), 1))
                    .fill(fluid(ProductFluids.EXPERIENCE.get(), 1000), FluidAction.EXECUTE);
            var saved = service.save(registry);
            var drops = net.minecraft.world.level.block.Block.getDrops(
                    h.getBlockState(p), h.getLevel(), h.absolutePos(p), owner);
            h.assertTrue(
                    drops.size() == 1
                            && net.impactvector.mobvats.gamecontent.item.EndpointState.read(drops.getFirst())
                                    .getCompound("serviceStaging")
                                    .equals(saved),
                    "Endpoint drop omitted exact service state");
            var stack = drops.getFirst();
            h.setBlock(p, Blocks.AIR);
            h.setBlock(p.below(), Blocks.STONE);
            var player = player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var below = h.absolutePos(p.below());
            stack.useOn(new net.minecraft.world.item.context.UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
            var placed = h.getBlockEntity(p);
            var restored = placed instanceof AbstractVatEntity vat
                    ? vat.service()
                    : ((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity) placed)
                            .service();
            h.assertTrue(restored.save(registry).equals(saved), "Endpoint item placement lost/duplicated service");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void disconnectedStageIsHiddenAndConnectedStagesAggregateExactly(GameTestHelper h) {
        var p = h.absolutePos(new BlockPos(3, 2, 3));
        var a = new ServiceStaging(() -> {});
        var b = new ServiceStaging(() -> {});
        a.tank(fluid(ProductFluids.WASTE.get(), 1)).fill(fluid(ProductFluids.WASTE.get(), 500), FluidAction.EXECUTE);
        b.tank(fluid(ProductFluids.WASTE.get(), 1)).fill(fluid(ProductFluids.WASTE.get(), 500), FluidAction.EXECUTE);
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(), ConduitTopology.EMPTY);
        storage.services(Map.of(p.west(), a, p.east(), b));
        h.assertTrue(
                storage.handler(p.north(3), false, s -> true)
                        .drain(1000, FluidAction.SIMULATE)
                        .isEmpty(),
                "Disconnected service escaped compiled connectivity");
        var network = storage.handler(p, false, s -> true);
        h.assertTrue(
                network.drain(1000, FluidAction.SIMULATE).getAmount() == 1000,
                "Connected stages did not aggregate a complete bucket");
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        FluidContainerInteraction.interact(player, InteractionHand.MAIN_HAND, network, false);
        h.assertTrue(
                player.getMainHandItem().is(ProductFluids.WASTE_BUCKET.get())
                        && a.tanks().getFirst().isEmpty()
                        && b.tanks().getFirst().isEmpty(),
                "Aggregated output lost or duplicated staged fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 100)
    public static void lethalSwordHarvestStagesExperienceAlongsideFullWaste(GameTestHelper h) {
        rig(h);
        stage(h).tank(fluid(ProductFluids.WASTE.get(), 1))
                .fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        h.setBlock(BLENDER, Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity) h.getBlockEntity(BLENDER)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        var feed = O.offset(2, 1, 0);
        h.setBlock(feed, Content.state(VatPartType.FeedPort));
        h.setBlock(feed.east(), Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(feed.east())).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        h.runAtTickTime(65, () -> {
            var v = vat(h);
            var xp = stage(h).tank(fluid(ProductFluids.EXPERIENCE.get(), 1));
            h.assertTrue(
                    v.storage().clusters().isEmpty()
                            && v.metrics().total(MachineMetrics.HARVESTS) == 1
                            && xp.getFluidAmount() > 0
                            && xp.getFluidAmount() == v.metrics().total(MachineMetrics.XP) * ExperienceFluid.ratio()
                            && stage(h).tank(fluid(ProductFluids.WASTE.get(), 1))
                                            .getFluidAmount()
                                    == 1000,
                    "Full Waste prevented independent Drain XP from an actual harvest");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void mixerLegacyOutputPrefersBulkAndRetainsOverflowEscrow(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        var stage = new ServiceStaging(() -> {});
        stage.tank(fluid(ProductFluids.CULTURE_SLURRY.get(), 1))
                .fill(fluid(ProductFluids.CULTURE_SLURRY.get(), 250), FluidAction.EXECUTE);
        var bulk = buffer(h, p.east(), "mobvats:culture_slurry");
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(bulk), ConduitTopology.EMPTY);
        storage.services(Map.of(h.absolutePos(p), stage));
        var legacy = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(8000);
        legacy.fill(fluid(ProductFluids.CULTURE_SLURRY.get(), 5000), FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("tank2", legacy.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
        var state = new MixerState(() -> {});
        state.load(tag, h.getLevel().registryAccess());
        state.bind(storage, h.absolutePos(p));
        var saved = state.save(h.getLevel().registryAccess());
        h.assertTrue(
                bulk.tank.getFluidAmount() == 4000
                        && stage.tanks().getFirst().getFluidAmount() == 1000
                        && saved.getCompound("tank2").getCompound("Fluid").getInt("amount") == 250,
                "Legacy slurry overflow was lost or bulk preference changed");
        var restored = new MixerState(() -> {});
        restored.bind(storage, h.absolutePos(p));
        restored.load(saved, h.getLevel().registryAccess());
        restored.bind(storage, h.absolutePos(p));
        bulk.tank.drain(1000, FluidAction.EXECUTE);
        restored.cadence();
        h.assertTrue(
                bulk.tank.getFluidAmount() == 3250
                        && stage.tanks().getFirst().getFluidAmount() == 1000
                        && restored.save(h.getLevel().registryAccess())
                                .getCompound("tank2")
                                .isEmpty(),
                "Reload/retry lost or duplicated legacy output escrow");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice112", timeoutTicks = 10)
    public static void unfilteredOutputSelectsOnlyContainerCompatibleCompleteTransfers(GameTestHelper h) {
        var stage = new ServiceStaging(() -> {});
        var xp = stage.tank(fluid(ProductFluids.EXPERIENCE.get(), 1));
        xp.fill(fluid(ProductFluids.EXPERIENCE.get(), 250), FluidAction.EXECUTE);
        var waste = stage.tank(fluid(ProductFluids.WASTE.get(), 1));
        waste.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        var network = PhysicalFluidStorage.handler(stage.tanks(), false, s -> true);
        var p = player(h);
        var held = new ItemStack(Items.PRISMARINE_SHARD);
        FluidUtil.getFluidHandler(held).orElseThrow().fill(fluid(ProductFluids.WASTE.get(), 500), FluidAction.EXECUTE);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        FluidContainerInteraction.interact(p, InteractionHand.MAIN_HAND, network, false);
        var contents = FluidUtil.getFluidContained(p.getMainHandItem()).orElseThrow();
        h.assertTrue(
                contents.is(ProductFluids.WASTE.get())
                        && contents.getAmount() == 1500
                        && waste.isEmpty()
                        && xp.getFluidAmount() == 250,
                "An earlier incompatible/partial fluid hid a complete compatible transfer");
        waste.fill(fluid(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        FluidContainerInteraction.interact(p, InteractionHand.MAIN_HAND, network, false);
        h.assertTrue(
                p.getMainHandItem().is(ProductFluids.WASTE_BUCKET.get())
                        && xp.getFluidAmount() == 250
                        && waste.isEmpty(),
                "Partial earlier byproduct prevented a later eligible bucket");
        h.succeed();
    }
}
