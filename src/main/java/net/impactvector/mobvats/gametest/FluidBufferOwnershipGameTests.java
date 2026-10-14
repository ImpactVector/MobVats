package net.impactvector.mobvats.gametest;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class FluidBufferOwnershipGameTests {
    @GameTest(template = "empty", timeoutTicks = 10)
    public static void preparedFillRechecksChangedFilter(GameTestHelper h) {
        var a = buffer(h, new BlockPos(2, 2, 2), ConduitShell.SHARED);
        a.filter("minecraft:water");
        var s = new PhysicalFluidStorage();
        s.compile(List.of(a), ConduitTopology.EMPTY);
        var tank = s.clusters().getFirst().tank();
        var plan = new ResourceReservation();
        plan.fluid(tank, new FluidStack(Fluids.WATER, 1000));
        a.filter("minecraft:lava");
        h.assertTrue(!plan.commit() && a.tank.isEmpty(), "Prepared output bypassed the changed filter");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void chunkBoundaryUnloadAndUnloadedQueriesConservePhysicalStorage(GameTestHelper h) {
        var start = h.absolutePos(new BlockPos(1, 2, 2));
        var edge = new BlockPos((start.getX() & ~15) + 15, start.getY(), start.getZ());
        h.getLevel().setBlock(edge, FluidBufferContent.state(ConduitCladding.BARE), 3);
        h.getLevel().setBlock(edge.east(), FluidBufferContent.state(ConduitCladding.BARE), 3);
        var a = (FluidBufferEntity) h.getLevel().getBlockEntity(edge);
        var b = (FluidBufferEntity) h.getLevel().getBlockEntity(edge.east());
        h.assertTrue(
                (a.getBlockPos().getX() >> 4) != (b.getBlockPos().getX() >> 4),
                "Fixture does not cross a chunk boundary");
        a.tank.setFluid(new FluidStack(Fluids.WATER, 4000));
        b.tank.setFluid(new FluidStack(Fluids.WATER, 1500));
        h.assertTrue(a.interactionCluster().tank().getCapacity() == 8000, "Loaded standalone boundary did not join");
        var retained = a.interactionCluster().tank();
        var delayedPlan = new ResourceReservation();
        delayedPlan.fluid(retained, new FluidStack(Fluids.WATER, 4500));
        var saved = b.saveWithoutMetadata(h.getLevel().registryAccess());
        b.onChunkUnloaded();
        h.assertTrue(
                retained.drain(1000, FluidAction.EXECUTE).isEmpty(), "Retained boundary tank accessed unloading side");
        h.assertTrue(a.interactionCluster().tank().getFluidAmount() == 4000, "Loaded side included unloading storage");
        b.loadWithComponents(saved, h.getLevel().registryAccess());
        b.onLoad();
        h.assertTrue(!delayedPlan.commit(), "Unload/reload revived a prepared transaction");
        h.assertTrue(
                a.interactionCluster().tank().getFluidAmount() == 5500, "Boundary reload duplicated or lost fluid");
        var unloaded = edge.offset(1000000, 0, 1000000);
        h.assertTrue(!h.getLevel().hasChunkAt(unloaded), "Query fixture unexpectedly loaded");
        int chunks = h.getLevel().getChunkSource().getLoadedChunksCount();
        h.assertTrue(
                PhysicalFluidStorage.members(h.getLevel(), unloaded, unloaded.east())
                        .isEmpty(),
                "Unloaded query found storage");
        h.assertTrue(
                !h.getLevel().hasChunkAt(unloaded)
                        && chunks == h.getLevel().getChunkSource().getLoadedChunksCount(),
                "Storage query acquired a chunk");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void machineRepairAndControllerReplacementRetirePreparedStorage(GameTestHelper h) {
        var o = new BlockPos(1, 1, 1);
        MachineFixtures.mixer(h::setBlock, o, 5, 4, 5, 2, 2, true);
        var p = o.offset(0, 2, 0);
        h.setBlock(p, FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
        var a = (FluidBufferEntity) h.getBlockEntity(p);
        a.filter("minecraft:water");
        a.tank.setFluid(new FluidStack(Fluids.WATER, 2500));
        var control = o.offset(0, 1, 2);
        var oldTank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank[1];
        var plan = new ResourceReservation[1];
        h.runAtTickTime(30, () -> {
            var owner = (MultiblockMixer) ConduitStructure.containing(h.getLevel(), a.getBlockPos());
            h.assertTrue(owner != null, "Mixer fixture did not form");
            oldTank[0] = owner.storage().bulkConnected(a.getBlockPos()).getFirst();
            plan[0] = new ResourceReservation();
            plan[0].fluid(oldTank[0], new FluidStack(Fluids.WATER, 1000));
            h.setBlock(o.offset(2, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(
                    ConduitStructure.containing(h.getLevel(), a.getBlockPos()) == null, "Machine did not disassemble");
            h.assertTrue(
                    !plan[0].commit() && oldTank[0].getCapacity() == 0 && a.tank.getFluidAmount() == 2500,
                    "Disassembly allowed stale transaction");
            h.setBlock(o.offset(2, 3, 0), MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(70, () -> {
            var owner = (MultiblockMixer) ConduitStructure.containing(h.getLevel(), a.getBlockPos());
            h.assertTrue(owner != null, "Machine did not reassemble");
            var current = owner.storage().bulkConnected(a.getBlockPos()).getFirst();
            h.assertTrue(
                    current != oldTank[0] && current.getFluidAmount() == 2500,
                    "Repair reused old identity or changed totals");
            oldTank[0] = current;
            plan[0] = new ResourceReservation();
            plan[0].fluid(current, new FluidStack(Fluids.WATER, 1000));
            h.setBlock(control, Blocks.AIR);
            h.setBlock(control, MixerContent.state(MixerPartType.Controller));
            h.assertTrue(!plan[0].commit(), "Controller replacement retained transaction authority");
        });
        h.runAtTickTime(95, () -> {
            var owner = (MultiblockMixer) ConduitStructure.containing(h.getLevel(), a.getBlockPos());
            h.assertTrue(
                    owner != null
                            && owner.storage().bulkConnected(a.getBlockPos()).getFirst() != oldTank[0]
                            && a.tank.getFluidAmount() == 2500,
                    "Replacement lost contents or retained endpoint identity");
            h.succeed();
        });
    }

    private static FluidBufferEntity buffer(GameTestHelper h, BlockPos p, ConduitShell shell) {
        h.setBlock(
                p,
                FluidBufferContent.state(
                        shell == ConduitShell.SHARED ? ConduitCladding.BARE : ConduitCladding.GLASS, shell));
        return (FluidBufferEntity) h.getBlockEntity(p);
    }

    private static void bucket(GameTestHelper h, FluidBufferEntity buffer, Item item) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        h.assertTrue(buffer.interact(player, InteractionHand.MAIN_HAND), "Container action was not handled");
        h.assertTrue(
                player.getMainHandItem().is(item == Items.BUCKET ? Items.WATER_BUCKET : Items.BUCKET),
                "Container did not transfer");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void touchingIndependentOwnersDoNotShareContainerStorage(GameTestHelper h) {
        var o = new BlockPos(1, 1, 1);
        MachineFixtures.vat(h::setBlock, o, 5, 5, 4);
        ((VatHeadEntity) h.getBlockEntity(o.offset(2, 2, 2)))
                .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
        MachineFixtures.mixer(h::setBlock, o.east(5), 5, 4, 5, 2, 2, true);
        var a = buffer(h, o.offset(4, 2, 0), ConduitShell.VAT);
        var b = buffer(h, o.offset(5, 2, 0), ConduitShell.MIXER);
        a.filter("minecraft:water");
        b.filter("minecraft:water");
        a.tank.setFluid(new FluidStack(Fluids.WATER, 4000));
        b.tank.setFluid(new FluidStack(Fluids.WATER, 2000));
        var unformedTank = a.interactionCluster().tank();
        var unformedPlan = new ResourceReservation();
        unformedPlan.fluid(unformedTank, new FluidStack(Fluids.WATER, 5000));
        h.runAtTickTime(35, () -> {
            var first = ConduitStructure.containing(h.getLevel(), a.getBlockPos());
            var second = ConduitStructure.containing(h.getLevel(), b.getBlockPos());
            h.assertTrue(
                    first != null && second != null && first != second,
                    "Independent fixtures did not form: Vat "
                            + ((VatHeadEntity) h.getBlockEntity(o.offset(2, 2, 2)))
                                    .getMultiblockController()
                                    .orElseThrow()
                                    .getLastError()
                            + "; Mixer "
                            + ((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity)
                                            h.getBlockEntity(o.offset(5, 1, 2)))
                                    .getMultiblockController()
                                    .orElseThrow()
                                    .getLastError());
            h.assertTrue(a.interactionCluster().members().equals(List.of(a)), "Container discovery escaped its owner");
            h.assertTrue(
                    !unformedPlan.commit()
                            && unformedTank.drain(1000, FluidAction.EXECUTE).isEmpty(),
                    "Unformed plan survived assignment to independent owners");
            bucket(h, b, Items.BUCKET);
            h.assertTrue(
                    a.tank.getFluidAmount() == 4000 && b.tank.getFluidAmount() == 1000,
                    "Container drained another machine");
            bucket(h, b, Items.WATER_BUCKET);
            h.assertTrue(
                    a.tank.getFluidAmount() == 4000 && b.tank.getFluidAmount() == 2000,
                    "Container redistributed another machine");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void retiredClusterRejectsPreparedTransactionAndDirectHandler(GameTestHelper h) {
        var a = buffer(h, new BlockPos(2, 2, 2), ConduitShell.SHARED);
        var s = new PhysicalFluidStorage();
        s.compile(List.of(a), ConduitTopology.EMPTY);
        var tank = s.clusters().getFirst().tank();
        tank.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        var reservation = new ResourceReservation();
        reservation.fluid(tank, new FluidStack(Fluids.WATER, 1000));
        var output = s.prepareOutputs(a.getBlockPos());
        var handler = s.handler(a.getBlockPos(), false, f -> true);
        s.clear();
        h.assertTrue(!reservation.commit(), "Prepared transaction wrote through retired cluster");
        h.assertTrue(
                !output.valid() && handler.drain(1000, FluidAction.EXECUTE).isEmpty(),
                "Retired endpoint remained available");
        h.assertTrue(tank.drain(1000, FluidAction.EXECUTE).isEmpty(), "Retained tank drained after retirement");
        s.compile(List.of(a), ConduitTopology.EMPTY);
        h.assertTrue(
                s.clusters().getFirst().tank() != tank && a.tank.getFluidAmount() == 2000,
                "Restore reused retired identity or lost contents");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void unloadCallbackRetiresReachabilityBeforeRemoval(GameTestHelper h) {
        var a = buffer(h, new BlockPos(2, 2, 2), ConduitShell.SHARED);
        var b = buffer(h, new BlockPos(3, 2, 2), ConduitShell.SHARED);
        var s = new PhysicalFluidStorage();
        s.compile(List.of(a, b), ConduitTopology.EMPTY);
        var tank = s.clusters().getFirst().tank();
        tank.fill(new FluidStack(Fluids.WATER, 6000), FluidAction.EXECUTE);
        var reservation = new ResourceReservation();
        reservation.fluid(tank, new FluidStack(Fluids.WATER, 5000));
        b.onChunkUnloaded();
        h.assertTrue(s.bulkConnected(a.getBlockPos()).isEmpty(), "Cached cluster retained unloading member");
        h.assertTrue(!reservation.commit(), "Transaction survived unload callback");
        h.assertTrue(a.interactionCluster().members().equals(List.of(a)), "Interaction included unloading member");
        h.assertTrue(
                a.tank.getFluidAmount() == 4000 && b.tank.getFluidAmount() == 2000, "Unload changed physical totals");
        b.onLoad();
        s.compile(List.of(a, b), ConduitTopology.EMPTY);
        h.assertTrue(s.clusters().getFirst().tank().getFluidAmount() == 6000, "Reload lost contents");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void compatibilityChangeRejectsRetainedClusterWithoutRedistribution(GameTestHelper h) {
        var a = buffer(h, new BlockPos(2, 2, 2), ConduitShell.SHARED);
        var b = buffer(h, new BlockPos(3, 2, 2), ConduitShell.SHARED);
        var s = new PhysicalFluidStorage();
        s.compile(List.of(a, b), ConduitTopology.EMPTY);
        var tank = s.clusters().getFirst().tank();
        tank.fill(new FluidStack(Fluids.WATER, 6000), FluidAction.EXECUTE);
        b.tank.setFluid(new FluidStack(Fluids.LAVA, 2000));
        h.assertTrue(tank.drain(1000, FluidAction.EXECUTE).isEmpty(), "Stale cluster merged incompatible contents");
        h.assertTrue(
                a.tank.getFluid().is(Fluids.WATER)
                        && a.tank.getFluidAmount() == 4000
                        && b.tank.getFluid().is(Fluids.LAVA)
                        && b.tank.getFluidAmount() == 2000,
                "Query altered amount or identity");
        s.compile(List.of(a, b), ConduitTopology.EMPTY);
        h.assertTrue(s.clusters().size() == 2, "Compiler did not split incompatible group");
        h.succeed();
    }
}
