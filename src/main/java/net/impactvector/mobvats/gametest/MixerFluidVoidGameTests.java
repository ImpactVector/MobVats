package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.google.gson.JsonParser;
import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.MachineInspection;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerFluidVoidGameTests {
    private static final BlockPos O = new BlockPos(1, 2, 1), V = O.offset(4, 1, 3);

    private static FluidStack feed(int n) {
        return new FluidStack(ProductFluids.FEEDING_SLURRY.get(), n);
    }

    private static void rig(GameTestHelper h, java.util.function.Consumer<MultiblockMixer> test) {
        MachineFixtures.mixerRig(h::setBlock, O);
        Slice11Fixtures.mixerStorage(h, O);
        h.setBlock(V, MixerContent.state(MixerPartType.FluidVoid));
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(c.isAssembled(), "Void shell did not form");
            test.accept(c);
        });
    }

    private static MixerFluidVoidEntity sink(GameTestHelper h) {
        return (MixerFluidVoidEntity) h.getBlockEntity(V);
    }

    @GameTest(template = "empty")
    public static void disabledMixerStillEvacuatesCommittedFluidToVoid(GameTestHelper h) {
        rig(h, c -> {
            c.setMachineActive(false);
            full(c);
            var owner = (MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2));
            for (var tank : owner.service().tanks()) tank.drain(Integer.MAX_VALUE, EXECUTE);
            owner.service().tank(feed(1)).fill(feed(500), EXECUTE);
            h.runAtTickTime(40, () -> {
                h.assertTrue(
                        owner.service().tank(feed(1)).isEmpty() && sink(h).disposedMb() == 500,
                        "Disabled Mixer blocked committed service evacuation/void");
                h.succeed();
            });
        });
    }

    private static MixerRecipeDefinition recipe() {
        return MixerRecipeRegistry.parse(JsonParser.parseString("""
        {"recipes":{"test:void":{"batch_ticks":200,"strategies":[{"id":"default","fluids":[{"selector":"minecraft:water","amount":100}]}],
        "outputs":{"fluids":[{"fluid":"mobvats:feeding_slurry","amount":1500},{"fluid":"mobvats:culture_slurry","amount":1500}]}}}}
        """).getAsJsonObject())
                .recipes()
                .get("test:void");
    }

    private static List<FluidStack> outputs() {
        var d = recipe();
        return d.fluidOutputs().stream()
                .map(o -> d.result(d.strategies().getFirst(), o, List.of(List.of(new FluidStack(Fluids.WATER, 100)))))
                .toList();
    }

    private static void full(MultiblockMixer c) {
        for (var cluster : c.storage().clusters()) {
            var t = cluster.tank();
            for (var s : outputs()) if (t.isFluidValid(s)) t.setFluid(s.copyWithAmount(t.getCapacity()));
        }
        var owner = c.getWorld().getBlockEntity(c.getBoundingBox().getMin().offset(0, 1, 2));
        var service = ((MixerControllerEntity) owner).service();
        for (var s : outputs()) service.tank(s).setFluid(s.copyWithAmount(1000));
        c.state().fluids(true).fill(new FluidStack(Fluids.WATER, 100), EXECUTE);
    }

    @GameTest(template = "empty")
    public static void shellTapConnectivityAndInspection(GameTestHelper h) {
        rig(h, c -> {
            h.assertTrue(
                    c.conduitTopology()
                            .reachable(h.absolutePos(O.offset(0, 1, 2)), ConduitResource.FLUID)
                            .contains(h.absolutePos(V)),
                    "Void absent from TAP topology");
            h.assertTrue(
                    c.conduitTopology().components().stream()
                            .noneMatch(k -> k.with(ConduitTopology.Role.STORE).contains(h.absolutePos(V))),
                    "Void classified as Store");
            sink(h).filter("mobvats:feeding_slurry");
            h.assertTrue(
                    MachineInspection.lines(h.getLevel(), h.absolutePos(V))
                            .toString()
                            .contains("feeding"),
                    "Inspection omitted filter");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void filteredDisposalAndUnfilteredDisposal(GameTestHelper h) {
        rig(h, c -> {
            var e = sink(h);
            e.filter("mobvats:feeding_slurry");
            h.assertTrue(
                    e.accepts(feed(1))
                            && !e.accepts(new FluidStack(Fluids.WATER, 1))
                            && !e.accepts(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1)),
                    "Filter crossed fluid identity");
            e.filter("");
            h.assertTrue(e.accepts(new FluidStack(Fluids.WATER, 1)), "Unfiltered sink rejected fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void realStorageBeforeVoidAndPartialOverflow(GameTestHelper h) {
        rig(h, c -> {
            var t = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
            var handler = PhysicalFluidStorage.withVoids(t, List.of(sink(h)), s -> true);
            h.assertTrue(
                    handler.fill(feed(500), EXECUTE) == 500 && !sink(h).flaring() && t.getFluidAmount() == 500,
                    "Void preferred over storage");
            h.assertTrue(
                    handler.fill(feed(1000), EXECUTE) == 1000 && t.getFluidAmount() == 1000 && sink(h).flaring(),
                    "Overflow did not retain real capacity first");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void simulationHasNoEffects(GameTestHelper h) {
        rig(h, c -> {
            var t = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(100);
            var handler = PhysicalFluidStorage.withVoids(t, List.of(sink(h)), s -> true);
            h.assertTrue(
                    handler.fill(feed(1000), SIMULATE) == 1000 && t.isEmpty() && !sink(h).flaring(),
                    "Simulation mutated storage or pulse");
            full(c);
            var water = c.state().amount(0);
            h.assertTrue(
                    c.state().canCommit(recipe()) && c.state().amount(0) == water && !sink(h).flaring(),
                    "Recipe simulation disposed output");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void multiOutputAtomicDisposal(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            h.assertTrue(
                    c.state().commit(recipe())
                            && c.state().amount(0) == 0
                            && sink(h).flaring()
                            && sink(h).disposedMb() == 3000,
                    "Multi-output disposal failed atomic commit");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void unmatchedOutputBackpressuresWholeBundle(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            sink(h).filter("mobvats:feeding_slurry");
            h.assertTrue(
                    !c.state().commit(recipe()) && c.state().amount(0) == 100 && !sink(h).flaring(),
                    "Unmatched second output partially committed");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void staleFilterReservationRejected(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            var r = c.state().prepare(recipe()).orElseThrow();
            sink(h).filter("mobvats:feeding_slurry");
            h.assertTrue(!r.commit() && c.state().amount(0) == 100 && !sink(h).flaring(), "Stale sink filter accepted");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void staleInputRejectsDisposalWithoutEffects(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            var r = c.state().prepare(recipe()).orElseThrow();
            c.state().fluids(true).fill(new FluidStack(Fluids.WATER, 100), EXECUTE);
            h.assertTrue(
                    !r.commit() && c.state().amount(0) == 200 && !sink(h).flaring(), "Stale input committed disposal");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void atomicPartialRealStorageThenOverflow(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            var tank = c.storage().clusters().stream()
                    .map(PhysicalFluidStorage.Cluster::tank)
                    .filter(t -> t.getFluid().is(ProductFluids.FEEDING_SLURRY.get()))
                    .findFirst()
                    .orElseThrow();
            tank.drain(500, EXECUTE);
            var before = tank.getFluidAmount();
            var committed = c.state().commit(recipe());
            h.assertTrue(
                    committed
                            && tank.getFluidAmount() == before + 500
                            && sink(h).flaring()
                            && sink(h).disposedMb() == 2500
                            && c.state().amount(0) == 0,
                    "Atomic overflow: committed=" + committed + " tank=" + tank.getFluidAmount() + " before=" + before
                            + " flare=" + sink(h).flaring() + " water="
                            + c.state().amount(0) + " blocked=" + c.state().blocked());
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void detachedTapRejectsReservation(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            var r = c.state().prepare(recipe()).orElseThrow();
            for (var q : List.of(V.below(), V.above()))
                h.setBlock(
                        q,
                        ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING, ConduitShell.MIXER)
                                .setValue(ConduitBlock.MODE, ConduitMode.TRUNK));
            c.rebuildTopology();
            h.assertTrue(
                    !c.state().canCommit(recipe()) && !r.commit() && c.state().amount(0) == 100 && !sink(h).flaring(),
                    "Detached sink TAP accepted stale reservation or fresh disposal");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void removedVoidDoesNotLoseFluids(GameTestHelper h) {
        rig(h, c -> {
            full(c);
            var r = c.state().prepare(recipe()).orElseThrow();
            var removed = sink(h);
            h.setBlock(V, Blocks.AIR);
            h.assertTrue(!r.commit() && c.state().amount(0) == 100 && !removed.flaring(), "Removed sink lost fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void componentFilterAndPortablePersistence(GameTestHelper h) {
        rig(h, c -> {
            var sample = feed(1);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Exact slurry"));
            sink(h).filter(EndpointState.fluidId(sample));
            h.assertTrue(sink(h).accepts(sample) && !sink(h).accepts(feed(1)), "Component filter lost exact identity");
            var item = EndpointState.capture(new ItemStack(MixerContent.block(MixerPartType.FluidVoid)), sink(h));
            h.setBlock(V, Blocks.AIR);
            h.setBlock(V, MixerContent.state(MixerPartType.FluidVoid));
            var e = sink(h);
            EndpointState.restore(item, e);
            h.assertTrue(e.filter().equals(EndpointState.fluidId(sample)), "Portable sink lost filter");
            h.runAtTickTime(70, () -> {
                h.assertTrue(e.isMachineAssembled() && e.accepts(sample), "Sink failed reform");
                h.succeed();
            });
        });
    }
}
