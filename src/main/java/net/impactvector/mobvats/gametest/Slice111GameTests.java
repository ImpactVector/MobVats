package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.ItemResultBuffer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class Slice111GameTests {
    private static final BlockPos P = new BlockPos(3, 2, 3);

    private static FluidBufferEntity buffer(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, FluidBufferContent.state(ConduitCladding.BARE));
        return (FluidBufferEntity) h.getBlockEntity(pos);
    }

    private static Player player(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void click(GameTestHelper h, BlockPos pos, Player player, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var absolute = h.absolutePos(pos);
        h.getBlockState(pos)
                .useItemOn(
                        held,
                        h.getLevel(),
                        player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static ItemStack drop(GameTestHelper h, BlockPos pos) {
        var entity = h.getBlockEntity(pos);
        var drops = Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), entity);
        h.assertTrue(
                drops.size()
                        == (net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.installed(
                                                h.getBlockState(pos))
                                        == net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.NONE
                                ? 1
                                : 2),
                "Expected endpoint and installed material drops");
        return drops.getFirst();
    }

    private static void place(GameTestHelper h, BlockPos pos, ItemStack stack) {
        h.setBlock(pos, Blocks.AIR);
        h.setBlock(pos.below(), Blocks.STONE);
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var absolute = h.absolutePos(pos.below());
        stack.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        h.assertTrue(h.getBlockEntity(pos) != null, "Endpoint item placement failed");
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void bufferBucketsFillDrainPreserveAndClear(GameTestHelper h) {
        var b = buffer(h, P);
        var player = player(h);
        click(h, P, player, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(
                b.filter().equals("minecraft:water")
                        && b.tank.getFluidAmount() == 1000
                        && player.getMainHandItem().is(Items.BUCKET),
                "Initial bucket did not configure/fill/transform");
        click(h, P, player, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(b.tank.getFluidAmount() == 2000, "Matching fill failed");
        click(h, P, player, new ItemStack(Items.LAVA_BUCKET));
        h.assertTrue(
                b.tank.getFluidAmount() == 2000 && player.getMainHandItem().is(Items.LAVA_BUCKET),
                "Different fluid mutated tank/container");
        click(h, P, player, new ItemStack(Items.BUCKET));
        click(h, P, player, new ItemStack(Items.BUCKET));
        h.assertTrue(
                b.tank.isEmpty()
                        && b.filter().equals("minecraft:water")
                        && player.getMainHandItem().is(Items.WATER_BUCKET),
                "Drain did not preserve filter");
        click(h, P, player, new ItemStack(Items.BUCKET, 3));
        h.assertTrue(
                b.filter().isEmpty()
                        && b.tank.isEmpty()
                        && player.getMainHandItem().getCount() == 3,
                "Clear consumed container or invented fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void emptyFilteredBufferRetargetsAndExperienceClears(GameTestHelper h) {
        var b = buffer(h, P);
        b.filter("mobvats:experience");
        var player = player(h);
        click(h, P, player, new ItemStack(Items.BUCKET));
        h.assertTrue(b.filter().isEmpty(), "Experience filter needs deferred XP bucket to clear");
        b.filter("mobvats:experience");
        click(h, P, player, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(
                b.filter().equals("minecraft:water") && b.tank.getFluidAmount() == 1000,
                "Empty buffer refused new exact fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void insufficientFluidAndCapacityLeaveContainersIntact(GameTestHelper h) {
        var b = buffer(h, P);
        b.tank.fill(new FluidStack(Fluids.WATER, 375), FluidAction.EXECUTE);
        var player = player(h);
        click(h, P, player, new ItemStack(Items.BUCKET));
        h.assertTrue(
                b.tank.getFluidAmount() == 375 && player.getMainHandItem().is(Items.BUCKET),
                "Partial bucket drain lost fluid");
        b.tank.fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE);
        click(h, P, player, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(
                b.tank.getFluidAmount() == 3375 && player.getMainHandItem().is(Items.WATER_BUCKET),
                "Partial bucket insert lost fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void bufferDropPlacePreservesExactPhysicalState(GameTestHelper h) {
        var b = buffer(h, P);
        b.filter("mobvats:experience");
        b.tank.fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 2375), FluidAction.EXECUTE);
        var item = drop(h, P);
        var presentation = item.copy();
        h.assertTrue(EndpointState.read(item).getInt("version") == 1, "Missing versioned portable state");
        var clone = b.getBlockState().getBlock().getCloneItemStack(h.getLevel(), b.getBlockPos(), b.getBlockState());
        h.assertTrue(EndpointState.read(item).equals(EndpointState.read(clone)), "Pick/drop state differ");
        place(h, P, item);
        var restored = (FluidBufferEntity) h.getBlockEntity(P);
        h.assertTrue(
                restored.filter().equals("mobvats:experience")
                        && restored.tank.getFluidAmount() == 2375
                        && restored.tank.getFluid().is(ProductFluids.EXPERIENCE.get()),
                "Drop/place lost exact state");
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        presentation
                .getItem()
                .appendHoverText(presentation, Item.TooltipContext.of(h.getLevel()), tooltip, TooltipFlag.NORMAL);
        h.assertTrue(tooltip.stream().anyMatch(t -> t.getString().contains("2375")), "Tooltip lost exact amount");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void adjacentClusterRetargetsAndSplitConservesOwnership(GameTestHelper h) {
        var a = buffer(h, P);
        var b = buffer(h, P.east());
        var player = player(h);
        h.assertTrue(a.interactionCluster().members().size() == 2, "Unfiltered adjacency failed");
        for (int i = 0; i < 5; i++) click(h, P.east(), player, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(
                a.filter().equals("minecraft:water")
                        && b.filter().equals(a.filter())
                        && a.tank.getFluidAmount() == 4000
                        && b.tank.getFluidAmount() == 1000,
                "Cluster lost deterministic fill ownership");
        var item = drop(h, P);
        h.setBlock(P, Blocks.AIR);
        h.assertTrue(
                b.interactionCluster().members().size() == 1 && b.tank.getFluidAmount() == 1000,
                "Split moved/duplicated physical fluid");
        place(h, P, item);
        a = (FluidBufferEntity) h.getBlockEntity(P);
        h.assertTrue(
                a.interactionCluster().tank().getFluidAmount() == 5000
                        && a.tank.getFluidAmount() == 4000
                        && b.tank.getFluidAmount() == 1000,
                "Merge lost/duplicated physical fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void clusterCompatibilityAndOrderAreDeterministic(GameTestHelper h) {
        var a = buffer(h, P);
        var b = buffer(h, P.east());
        var s = new PhysicalFluidStorage();
        a.filter("minecraft:water");
        b.filter("minecraft:lava");
        s.compile(List.of(b, a), ConduitTopology.EMPTY);
        h.assertTrue(s.clusters().size() == 2, "Incompatible filters merged");
        b.filter("minecraft:water");
        s.compile(List.of(b, a), ConduitTopology.EMPTY);
        h.assertTrue(s.clusters().size() == 1, "Same exact filters did not merge");
        a.filter("");
        b.filter("");
        a.tank.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
        b.tank.fill(new FluidStack(Fluids.LAVA, 350), FluidAction.EXECUTE);
        s.compile(List.of(b, a), ConduitTopology.EMPTY);
        h.assertTrue(s.clusters().size() == 2, "Different stored fluids merged");
        b.tank.setFluid(new FluidStack(Fluids.WATER, 350));
        s.compile(List.of(b, a), ConduitTopology.EMPTY);
        h.assertTrue(
                s.clusters().size() == 1
                        && s.clusters().getFirst().tank().getFluidAmount() == 600
                        && s.clusters().getFirst().members().getFirst() == a,
                "Same fluid/order compatibility failed");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void itemPortSamplesClearsAndPreservesStaging(GameTestHelper h) {
        h.setBlock(P, Content.state(VatPartType.ItemPort));
        var port = (VatItemPortEntity) h.getBlockEntity(P);
        var player = player(h);
        player.setShiftKeyDown(true);
        var held = new ItemStack(Items.WHEAT, 17);
        click(h, P, player, held);
        h.assertTrue(
                port.filter().equals("minecraft:wheat") && ItemStack.matches(held, player.getMainHandItem()),
                "Item sample consumed stack");
        port.setIoDirection(IoDirection.Output);
        port.staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        var item = drop(h, P);
        place(h, P, item);
        port = (VatItemPortEntity) h.getBlockEntity(P);
        h.assertTrue(
                port.filter().equals("minecraft:wheat")
                        && port.getIoDirection().isOutput()
                        && port.staging().getStackInSlot(0).is(Items.WHEAT)
                        && port.staging().getStackInSlot(0).getCount() == 4,
                "Port drop/place lost config or staged resources");
        click(h, P, player, ItemStack.EMPTY);
        h.assertTrue(port.filter().isEmpty(), "Empty sneak-hand failed to clear item filter");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void itemFilterRestrictsCapabilitiesAndToolRouting(GameTestHelper h) {
        h.setBlock(P, Content.state(VatPartType.ItemPort));
        var port = (VatItemPortEntity) h.getBlockEntity(P);
        port.filter("minecraft:wheat");
        h.assertTrue(
                !port.externalItems()
                                .insertItem(0, new ItemStack(Items.CARROT), false)
                                .isEmpty()
                        && port.externalItems()
                                .insertItem(0, new ItemStack(Items.WHEAT), false)
                                .isEmpty(),
                "Filtered input acceptance failed");
        port.setIoDirection(IoDirection.Output);
        port.staging().setStackInSlot(0, new ItemStack(Items.CARROT));
        h.assertTrue(
                port.externalItems().getStackInSlot(0).isEmpty()
                        && port.externalItems().extractItem(0, 1, false).isEmpty(),
                "Output exposed excluded staging");
        port.staging().setStackInSlot(0, ItemStack.EMPTY);
        var results = new ItemResultBuffer();
        results.store(List.of(new ItemStack(Items.CARROT), new ItemStack(Items.WHEAT, 3)));
        h.assertTrue(
                results.drain(List.of(port)) == 3
                        && results.itemCount() == 1
                        && port.externalItems().extractItem(0, 3, false).is(Items.WHEAT),
                "Tool output routing ignored filter");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void fluidPortSamplesClearsAndPersistsWithoutTransfer(GameTestHelper h) {
        h.setBlock(P, Content.state(VatPartType.FluidPort));
        var port = (VatFluidPortEntity) h.getBlockEntity(P);
        var player = player(h);
        player.setShiftKeyDown(true);
        var held = new ItemStack(Items.WATER_BUCKET);
        click(h, P, player, held);
        h.assertTrue(
                port.filter().equals("minecraft:water") && ItemStack.matches(held, player.getMainHandItem()),
                "Fluid sample mutated container");
        port.setIoDirection(IoDirection.Output);
        place(h, P, drop(h, P));
        port = (VatFluidPortEntity) h.getBlockEntity(P);
        h.assertTrue(
                port.filter().equals("minecraft:water") && port.getIoDirection().isOutput(),
                "Fluid filter/direction did not restore");
        click(h, P, player, ItemStack.EMPTY);
        h.assertTrue(port.filter().isEmpty(), "Fluid clear failed");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void fluidFilterRestrictsInputOutputAndTankVisibility(GameTestHelper h) {
        var water = buffer(h, P);
        water.filter("minecraft:water");
        water.tank.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
        var lava = buffer(h, P.east());
        lava.filter("minecraft:lava");
        lava.tank.fill(new FluidStack(Fluids.LAVA, 750), FluidAction.EXECUTE);
        h.setBlock(P.north(), Content.state(VatPartType.FluidPort));
        var port = (VatFluidPortEntity) h.getBlockEntity(P.north());
        port.filter("minecraft:water");
        var tanks = List.of(water.tank, lava.tank);
        var input = PhysicalFluidStorage.handler(tanks, true, port::accepts);
        h.assertTrue(
                input.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0
                        && input.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 100,
                "Filtered input failed");
        var output = PhysicalFluidStorage.handler(tanks, false, port::accepts);
        h.assertTrue(
                output.getFluidInTank(1).isEmpty()
                        && output.drain(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE)
                                .isEmpty()
                        && output.drain(100, FluidAction.EXECUTE).is(Fluids.WATER)
                        && lava.tank.getFluidAmount() == 750,
                "Filtered output leaked different fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void legacyEndpointSavesLoadUnchanged(GameTestHelper h) {
        var b = buffer(h, P);
        b.filter("mobvats:experience");
        b.tank.fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 2375), FluidAction.EXECUTE);
        var saved = b.saveWithoutMetadata(h.getLevel().registryAccess());
        b.tank.setFluid(FluidStack.EMPTY);
        b.filter("");
        b.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(
                b.filter().equals("mobvats:experience") && b.tank.getFluidAmount() == 2375,
                "Existing buffer save format changed");
        h.setBlock(P.north(), Content.state(VatPartType.FluidPort));
        var port = (VatFluidPortEntity) h.getBlockEntity(P.north());
        var tag = new CompoundTag();
        tag.putString("fluidFilter", "#minecraft:water");
        port.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(port.accepts(new FluidStack(Fluids.WATER, 1)), "Legacy command selector failed to load");
        h.setBlock(P.south(), Content.state(VatPartType.ItemPort));
        h.assertTrue(
                ((VatItemPortEntity) h.getBlockEntity(P.south())).filter().isEmpty(),
                "Old plain item port gained filter");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 80)
    public static void formedPortsLockBothEditsAndClearsAndUnlockAfterRemoval(GameTestHelper h) {
        VatGameTests.build(h, 5);
        var itemPos = new BlockPos(2, 2, 5);
        var fluidPos = new BlockPos(1, 2, 3);
        ((VatHeadEntity) h.getBlockEntity(new BlockPos(3, 3, 3)))
                .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
        h.setBlock(itemPos, Content.state(VatPartType.ItemPort));
        h.setBlock(fluidPos, Content.state(VatPartType.FluidPort));
        Slice11Fixtures.vatStorage(h, new BlockPos(1, 1, 1), 5, 5);
        var item = (VatItemPortEntity) h.getBlockEntity(itemPos);
        var fluid = (VatFluidPortEntity) h.getBlockEntity(fluidPos);
        item.filter("minecraft:wheat");
        fluid.filter("minecraft:water");
        h.runAtTickTime(30, () -> {
            h.assertTrue(item.isMachineAssembled() && fluid.isMachineAssembled(), "Lock fixture did not assemble");
            var player = player(h);
            player.setShiftKeyDown(true);
            h.assertTrue(
                    fluid.externalFluids().fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0
                            && fluid.externalFluids().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE)
                                    == 1000,
                    "Formed Fluid Input ignored filter");
            fluid.setIoDirection(IoDirection.Output);
            h.assertTrue(
                    fluid.externalFluids()
                                    .drain(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE)
                                    .isEmpty()
                            && fluid.externalFluids()
                                    .drain(1000, FluidAction.EXECUTE)
                                    .is(Fluids.WATER),
                    "Formed Fluid Output ignored filter");
            click(h, itemPos, player, new ItemStack(Items.CARROT));
            click(h, fluidPos, player, new ItemStack(Items.LAVA_BUCKET));
            click(h, itemPos, player, ItemStack.EMPTY);
            click(h, fluidPos, player, ItemStack.EMPTY);
            h.assertTrue(
                    item.filter().equals("minecraft:wheat") && fluid.filter().equals("minecraft:water"),
                    "Formed filter edited/cleared");
            h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(!item.isMachineAssembled() && !fluid.isMachineAssembled(), "Broken fixture stayed formed");
            var player = player(h);
            player.setShiftKeyDown(true);
            click(h, itemPos, player, new ItemStack(Items.CARROT));
            click(h, fluidPos, player, new ItemStack(Items.LAVA_BUCKET));
            h.assertTrue(
                    item.filter().equals("minecraft:carrot")
                            && fluid.filter().equals("minecraft:lava")
                            && player.getMainHandItem().is(Items.LAVA_BUCKET),
                    "Unformed filters stayed locked or sampling consumed container");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void capabilityContainerTransfersAndSamplesWithoutBucketSpecialCase(GameTestHelper h) {
        var b = buffer(h, P);
        var container = new ItemStack(Items.PRISMARINE_SHARD);
        FluidUtil.getFluidHandler(container)
                .orElseThrow()
                .fill(new FluidStack(Fluids.WATER, 1375), FluidAction.EXECUTE);
        var player = player(h);
        click(h, P, player, container);
        h.assertTrue(
                b.tank.getFluidAmount() == 1375
                        && b.filter().equals("minecraft:water")
                        && FluidUtil.getFluidContained(player.getMainHandItem()).isEmpty(),
                "Capability container insertion failed");
        click(h, P, player, player.getMainHandItem());
        h.assertTrue(
                b.tank.isEmpty()
                        && b.filter().equals("minecraft:water")
                        && FluidUtil.getFluidContained(player.getMainHandItem())
                                        .orElseThrow()
                                        .getAmount()
                                == 1375,
                "Capability container extraction failed");
        h.setBlock(P.north(), Content.state(VatPartType.FluidPort));
        var port = (VatFluidPortEntity) h.getBlockEntity(P.north());
        player.setShiftKeyDown(true);
        var held = player.getMainHandItem().copy();
        click(h, P.north(), player, held.copy());
        h.assertTrue(
                port.filter().equals("minecraft:water") && ItemStack.matches(held, player.getMainHandItem()),
                "Capability container sampling consumed contents");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void itemSelectorUsesIdentityAndIgnoresComponents(GameTestHelper h) {
        h.setBlock(P, Content.state(VatPartType.ItemPort));
        var port = (VatItemPortEntity) h.getBlockEntity(P);
        var player = player(h);
        player.setShiftKeyDown(true);
        var wheat = new ItemStack(Items.WHEAT, 6);
        wheat.set(
                net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Named wheat"));
        click(h, P, player, wheat.copy());
        h.assertTrue(
                port.accepts(new ItemStack(Items.WHEAT)) && ItemStack.matches(wheat, player.getMainHandItem()),
                "Item selector changed existing component policy or mutated sample");
        var saved = port.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        port.filter("");
        port.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(port.filter().equals("minecraft:wheat"), "Item filter save/load failed");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void stackedContainersStowAndCreativeContainersRemainUnchanged(GameTestHelper h) {
        var b = buffer(h, P);
        var player = player(h);
        click(h, P, player, new ItemStack(Items.WATER_BUCKET));
        click(h, P, player, new ItemStack(Items.BUCKET, 3));
        h.assertTrue(
                b.tank.isEmpty()
                        && player.getMainHandItem().is(Items.BUCKET)
                        && player.getMainHandItem().getCount() == 2
                        && player.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "Stacked drain did not stow transformed container once");
        var creative = h.makeMockServerPlayerInLevel();
        creative.setGameMode(GameType.CREATIVE);
        click(h, P, creative, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(
                b.tank.getFluidAmount() == 1000 && creative.getMainHandItem().is(Items.WATER_BUCKET),
                "Standard creative container semantics changed");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice111", timeoutTicks = 10)
    public static void actualBreakDropsPhysicalContentsExactlyOnce(GameTestHelper h) {
        var b = buffer(h, P);
        b.filter("minecraft:water");
        b.tank.fill(new FluidStack(Fluids.WATER, 2375), FluidAction.EXECUTE);
        h.getLevel().destroyBlock(h.absolutePos(P), true);
        var drops = h.getLevel()
                .getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class, new AABB(h.absolutePos(P)).inflate(1));
        h.assertTrue(
                drops.size() == 1
                        && drops.getFirst().getItem().getCount() == 1
                        && EndpointState.read(drops.getFirst().getItem())
                                        .getCompound("tank")
                                        .getCompound("Fluid")
                                        .getInt("amount")
                                == 2375,
                "Actual break lost or duplicated fluid drop");
        var item = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        place(h, P, item);
        h.assertTrue(
                ((FluidBufferEntity) h.getBlockEntity(P)).tank.getFluidAmount() == 2375,
                "Actual break/place lost fluid");
        h.succeed();
    }
}
