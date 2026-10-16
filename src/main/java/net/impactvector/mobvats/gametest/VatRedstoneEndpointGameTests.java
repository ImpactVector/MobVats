package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.*;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatRedstoneEndpointGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            II = O.offset(1, 1, 4),
            IO = O.offset(3, 1, 4),
            FI = O.offset(0, 1, 2),
            FO = O.offset(4, 1, 2),
            SUMP = O.offset(2, 0, 2);

    private static void rig(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        h.setBlock(II, Content.state(VatPartType.ItemPort));
        h.setBlock(IO, Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(IO)).setIoDirection(IoDirection.Output);
        h.setBlock(FI, Content.state(VatPartType.FluidPort));
        h.setBlock(FO, Content.state(VatPartType.FluidPort));
        ((VatFluidPortEntity) h.getBlockEntity(FO)).setIoDirection(IoDirection.Output);
        Slice11Fixtures.vatStorage(h, O, 5, 5);
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
    }

    private static FluidStack water(int n) {
        return new FluidStack(Fluids.WATER, n);
    }

    private static void items(GameTestHelper h, boolean indirect) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var in = (VatItemPortEntity) h.getBlockEntity(II);
            var out = (VatItemPortEntity) h.getBlockEntity(IO);
            in.endpoint().filter("minecraft:wheat");
            out.endpoint().filter("minecraft:beef");
            var input = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(II), Direction.SOUTH);
            var output = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(IO), Direction.SOUTH);
            h.assertTrue(input != null && output != null, "Missing fixture item capabilities");
            input.insertItem(0, new ItemStack(Items.WHEAT, 4), false);
            out.staging().setStackInSlot(0, new ItemStack(Items.BEEF, 4));
            MixerRedstoneGameTests.power(h, II.south(), indirect);
            MixerRedstoneGameTests.power(h, IO.south(), indirect);
            var committed = new net.impactvector.mobvats.gamecontent.multiblock.vat.process.ItemResultBuffer();
            committed.store(java.util.List.of(new ItemStack(Items.BEEF, 3)));
            h.assertTrue(
                    committed.drain(java.util.List.of(out)) == 0 && committed.itemCount() == 3,
                    "Internal output transport bypassed inhibited port");
            for (boolean simulate : new boolean[] {true, false}) {
                h.assertTrue(
                        input.insertItem(0, new ItemStack(Items.WHEAT), simulate)
                                                .getCount()
                                        == 1
                                && output.extractItem(0, 1, simulate).isEmpty(),
                        "Powered retained handler transferred items");
            }
            var saved = out.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            out.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    output.extractItem(0, 1, false).isEmpty()
                            && out.staging().getStackInSlot(0).getCount() == 4
                            && in.staging().getStackInSlot(0).getCount() == 4
                            && out.filter().equals("minecraft:beef"),
                    "Reload/signal lost inventory, filter, or inhibition");
            MixerRedstoneGameTests.unpower(h, II.south());
            MixerRedstoneGameTests.unpower(h, IO.south());
            h.assertTrue(
                    input.insertItem(0, new ItemStack(Items.WHEAT), false).isEmpty()
                            && output.extractItem(0, 1, false).getCount() == 1,
                    "Item endpoints failed to resume");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void itemPortsDirectRetainedCapabilitiesAndReload(GameTestHelper h) {
        items(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void itemPortsIndirectRetainedCapabilitiesAndReload(GameTestHelper h) {
        items(h, true);
    }

    private static void fluids(GameTestHelper h, boolean indirect) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var input = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FI), Direction.WEST);
            var output = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FO), Direction.EAST);
            h.assertTrue(
                    input != null && output != null && input.fill(water(2000), FluidAction.EXECUTE) == 2000,
                    "Unpowered fluid fixture failed");
            MixerRedstoneGameTests.power(h, FI.west(), indirect);
            h.assertTrue(
                    input.fill(water(1000), FluidAction.SIMULATE) == 0
                            && input.fill(water(1000), FluidAction.EXECUTE) == 0,
                    "Powered input filled shared tanks");
            h.assertTrue(
                    output.drain(water(250), FluidAction.EXECUTE).getAmount() == 250,
                    "Disabled input made shared resources inaccessible through enabled output");
            MixerRedstoneGameTests.power(h, FO.east(), indirect);
            for (var action : FluidAction.values())
                h.assertTrue(
                        output.drain(water(1000), action).isEmpty()
                                && output.drain(1000, action).isEmpty(),
                        "Powered output drained via retained handler");
            var out = (VatFluidPortEntity) h.getBlockEntity(FO);
            var tag = out.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            out.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(output.drain(1000, FluidAction.EXECUTE).isEmpty(), "Reload bypassed fluid inhibition");
            MixerRedstoneGameTests.unpower(h, FI.west());
            MixerRedstoneGameTests.unpower(h, FO.east());
            h.assertTrue(
                    input.fill(water(250), FluidAction.EXECUTE) == 250
                            && output.drain(water(2000), FluidAction.EXECUTE).getAmount() == 2000,
                    "Fluid port pause lost resources or failed resume");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fluidPortsDirectPreserveSharedAccess(GameTestHelper h) {
        fluids(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fluidPortsIndirectPreserveSharedAccess(GameTestHelper h) {
        fluids(h, true);
    }

    private static void sump(GameTestHelper h, boolean indirect) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var sump = (VatSumpEntity) h.getBlockEntity(SUMP);
            var tank = sump.service().tank(water(1));
            h.assertTrue(
                    tank.fill(water(1000), FluidAction.EXECUTE) == 1000, "Sump fixture failed to fill service tank");
            var milk = c.processEngine()
                    .state(h.absolutePos(HEAD))
                    .process(net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveProcessCompiler.MILK);
            milk.elapsed = 1200;
            milk.phase = net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessState.Phase.READY;
            var power = SUMP.below();
            h.setBlock(power, indirect ? Blocks.STONE : Blocks.REDSTONE_BLOCK);
            if (indirect)
                h.setBlock(
                        power.north(),
                        Blocks.LEVER
                                .defaultBlockState()
                                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                                .setValue(LeverBlock.POWERED, true));
            h.assertTrue(sump.redstoneInhibited(), "Missing Sump signal");
            for (int i = 0; i < 20; i++) c.updateMultiblockEntity();
            h.assertTrue(
                    tank.getFluidAmount() == 1000
                            && c.milkAmount() == 0
                            && milk.phase
                                    == net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessState.Phase
                                            .READY,
                    "Powered Sump collected production or evacuated service resources");
            h.setBlock(power.north(), Blocks.AIR);
            h.setBlock(power, Blocks.AIR);
            for (int i = 0; i < 10; i++) c.updateMultiblockEntity();
            h.assertTrue(tank.isEmpty() && c.milkAmount() > 0, "Sump did not resume collection/evacuation");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sumpDirectPausesServiceEjection(GameTestHelper h) {
        sump(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sumpIndirectPausesServiceEjection(GameTestHelper h) {
        sump(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void activeEndpointsInhibitIndependentlyOfController(GameTestHelper h) {
        rig(h);
        h.setBlock(IO.south(), Blocks.BARREL);
        h.setBlock(FO.east(), net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.block());
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var item = (VatItemPortEntity) h.getBlockEntity(IO);
            var fluid = (VatFluidPortEntity) h.getBlockEntity(FO);
            item.setIoMode(IoMode.Active);
            fluid.setIoMode(IoMode.Active);
            item.staging().setStackInSlot(0, new ItemStack(Items.BEEF, 4));
            ((VatFluidPortEntity) h.getBlockEntity(FI)).externalFluids().fill(water(1000), FluidAction.EXECUTE);
            var lever = Blocks.LEVER
                    .defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.POWERED, true);
            h.setBlock(IO.south().above(), lever);
            h.setBlock(FO.east().above(), lever);
            MixerRedstoneGameTests.power(h, O.offset(1, 1, -1), false);
            h.assertTrue(
                    item.redstoneInhibited() && fluid.redstoneInhibited() && !c.processingEnabled(),
                    "Missing active signals");
            h.assertTrue(
                    item.transferBoundary(1000) == 0 && fluid.transferBoundary(1000) == 0,
                    "Powered active endpoint transferred resources");
            h.setBlock(IO.south().above(), Blocks.AIR);
            h.setBlock(FO.east().above(), Blocks.AIR);
            h.assertTrue(
                    item.transferBoundary(1000) == 4 && fluid.transferBoundary(1000) == 1000,
                    "Controller inhibition prevented enabled active transport");
            var barrel = (BarrelBlockEntity) h.getBlockEntity(IO.south());
            h.assertTrue(barrel.getItem(0).getCount() == 4, "Active endpoint lost items");
            h.succeed();
        });
    }
}
