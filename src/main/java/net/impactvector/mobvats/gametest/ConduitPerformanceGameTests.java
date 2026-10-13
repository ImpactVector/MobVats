package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class ConduitPerformanceGameTests {
    private static class Endpoint implements ResourceEndpoint {
        final BlockPos pos;
        final Set<ConduitTopology.Role> roles;
        final FluidTank tank = new FluidTank(4000);
        PhysicalFluidStorage storage;
        boolean available = true, accept = true;

        Endpoint(int x, ConduitTopology.Role... roles) {
            pos = new BlockPos(x, 1, 1);
            this.roles = Set.of(roles);
        }

        public BlockPos getBlockPos() {
            return pos;
        }

        public Set<ConduitTopology.Role> roles(ConduitResource resource) {
            return resource == ConduitResource.FLUID ? roles : Set.of();
        }

        public List<FluidTank> fluids(ConduitTopology.Role role) {
            return !roles.contains(role) ? List.of() : storage == null ? List.of(tank) : storage.bulkConnected(pos);
        }

        public boolean routingAvailable() {
            return available;
        }

        public boolean accepts(FluidStack stack) {
            return accept;
        }
    }

    private static ConduitTopology topology(Collection<? extends ResourceEndpoint> endpoints) {
        return new ConduitTopology(List.of(new ConduitTopology.Component(
                ConduitResource.FLUID,
                BlockPos.ZERO,
                32,
                32,
                endpoints.stream().flatMap(e -> e.connections().stream()).toList())));
    }

    private static List<FluidBufferEntity> buffers(int count) {
        var result = new ArrayList<FluidBufferEntity>();
        for (int i = 0; i < count; i++)
            result.add(new FluidBufferEntity(
                    new BlockPos(10 + i * 2, 1, 1), FluidBufferContent.state(ConduitCladding.BARE)));
        return result;
    }

    @GameTest(template = "empty")
    public static void sharedBuffersAndPortsReuseConnectivityWithLiveTransfers(GameTestHelper h) {
        var buffers = buffers(32);
        var endpoints = new ArrayList<ResourceEndpoint>(buffers);
        var a = new Endpoint(1, CONSUMER);
        var b = new Endpoint(3, CONSUMER);
        var producer = new Endpoint(5, PRODUCER);
        endpoints.addAll(List.of(a, b, producer));
        var storage = new PhysicalFluidStorage();
        for (int x : List.of(-10, -12, -14)) {
            var port = new Endpoint(x, STORE);
            port.storage = storage;
            endpoints.add(port);
        }
        var topology = topology(endpoints);
        storage.compile(buffers, topology);
        var routed = new ArrayList<ResourceEndpoint>();
        for (var endpoint : endpoints) if (!(endpoint instanceof FluidBufferEntity)) routed.add(endpoint);
        // Controllers expose each buffer's logical cluster identity, rather than its private physical tank.
        for (var cluster : storage.clusters())
            for (var member : cluster.members())
                routed.add(new ResourceEndpoint() {
                    public BlockPos getBlockPos() {
                        return member.getBlockPos();
                    }

                    public Set<ConduitTopology.Role> roles(ConduitResource resource) {
                        return resource == ConduitResource.FLUID ? Set.of(STORE) : Set.of();
                    }

                    public List<FluidTank> fluids(ConduitTopology.Role role) {
                        return List.of(cluster.tank());
                    }
                });
        var routing = new ResourceRouting(topology, routed);
        var input = storage.destination(new BlockPos(-10, 1, 1), true, s -> true);
        var outputs = List.of(routing.fluidConsumer(a, s -> true), routing.fluidConsumer(b, s -> true));
        for (int i = 0; i < 100; i++) {
            h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 100), EXECUTE) == 100, "Live fill rejected");
            for (var output : outputs) {
                h.assertTrue(output.getTanks() == 33, "Shared tank aliases not deduplicated");
                h.assertTrue(output.getFluidInTank(0).getAmount() == 100, "Amount read cached stale contents");
                h.assertTrue(output.getTankCapacity(0) == 4000, "Capacity changed");
                h.assertTrue(
                        output.drain(new FluidStack(Fluids.WATER, 100), SIMULATE)
                                        .getAmount()
                                == 100,
                        "Simulation lost live supply");
            }
            h.assertTrue(outputs.getFirst().drain(100, EXECUTE).getAmount() == 100, "Live drain failed");
        }
        h.assertTrue(
                topology.snapshotBuilds() == 5,
                "Repeated handler operations enumerated stable components: " + topology.snapshotBuilds());
        h.assertTrue(
                storage.bulkSnapshotBuilds() == 3,
                "Repeated port queries rescanned buffers: " + storage.bulkSnapshotBuilds());
        producer.tank.setFluid(new FluidStack(Fluids.WATER, 200));
        h.assertTrue(
                outputs.getFirst().drain(100, SIMULATE).getAmount() == 100, "Producer unavailable behind empty stores");
        producer.accept = false;
        h.assertTrue(outputs.getFirst().drain(100, SIMULATE).isEmpty(), "Live filter ignored");
        producer.accept = true;
        producer.available = false;
        h.assertTrue(outputs.getFirst().drain(100, SIMULATE).isEmpty(), "Removed endpoint still eligible");
        h.assertTrue(topology.snapshotBuilds() == 5, "Resource changes invalidated physical connectivity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void storageGenerationRetiresIdentitiesAndRevalidatesReservations(GameTestHelper h) {
        var buffer = buffers(1).getFirst();
        var port = new Endpoint(1, STORE);
        var endpoints = List.of(buffer, port);
        var storage = new PhysicalFluidStorage();
        var topology = topology(endpoints);
        storage.compile(List.of(buffer), topology);
        var handler = storage.destination(port.pos, true, s -> true);
        var reservation = storage.prepareOutputs(port.pos);
        h.assertTrue(handler.getTanks() == 1, "Small network disconnected");
        buffer.filter("minecraft:lava");
        h.assertTrue(
                handler.fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 0,
                "Cached connectivity bypassed changed buffer filter");
        h.assertTrue(
                handler.fill(new FluidStack(Fluids.LAVA, 100), EXECUTE) == 100,
                "Changed filter rejected matching fluid");
        h.assertTrue(
                storage.bulkSnapshotBuilds() == 1 && topology.snapshotBuilds() == 1,
                "Fluid/filter reads rebuilt connectivity");
        buffer.setRemoved();
        h.assertTrue(
                handler.getTanks() == 0 && !reservation.valid(), "Unload retained endpoint availability/reservation");
        buffer.clearRemoved();
        storage.clear();
        h.assertTrue(handler.getTanks() == 0, "Disassembly retained storage identities");
        var replacement = new FluidBufferEntity(buffer.getBlockPos(), buffer.getBlockState());
        storage.compile(List.of(replacement), topology(List.of(replacement, port)));
        h.assertTrue(
                handler.getTanks() == 1 && handler.getFluidInTank(0).isEmpty() && !reservation.valid(),
                "Reassembly retained old inventory identity");
        h.assertTrue(buffer.tank.getFluidAmount() == 100, "Lifecycle clearing erased persisted contents");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void physicalEditsCreateFreshSnapshots(GameTestHelper h) {
        var tap = new BlockPos(3, 2, 3);
        var left = tap.west();
        var right = tap.east();
        var state = ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE);
        h.setBlock(tap, state.setValue(ConduitBlock.MODE, ConduitMode.TAP));
        var endpoints = List.of(
                new ConduitTopology.Endpoint(h.absolutePos(left), ConduitResource.FLUID, STORE),
                new ConduitTopology.Endpoint(h.absolutePos(right), ConduitResource.FLUID, CONSUMER));
        var topology = ConduitTopology.compile(h.getLevel(), h.absolutePos(left), h.absolutePos(right), endpoints);
        for (int i = 0; i < 100; i++)
            h.assertTrue(
                    topology.reachable(h.absolutePos(left), ConduitResource.FLUID)
                            .equals(List.of(h.absolutePos(right))),
                    "Small network order/membership changed");
        h.assertTrue(topology.snapshotBuilds() == 1, "Repeated lookup re-enumerated endpoints");
        h.setBlock(tap, state);
        var trunk = ConduitTopology.compile(h.getLevel(), h.absolutePos(left), h.absolutePos(right), endpoints);
        h.assertTrue(
                trunk.reachable(h.absolutePos(left), ConduitResource.FLUID).isEmpty(),
                "TAP -> TRUNK retained endpoint");
        h.setBlock(tap, Blocks.AIR);
        var removed = ConduitTopology.compile(h.getLevel(), h.absolutePos(left), h.absolutePos(right), endpoints);
        h.assertTrue(
                removed.reachable(h.absolutePos(left), ConduitResource.FLUID).isEmpty(), "Removal retained connection");
        h.setBlock(tap, state.setValue(ConduitBlock.MODE, ConduitMode.TAP));
        var restored = ConduitTopology.compile(h.getLevel(), h.absolutePos(left), h.absolutePos(right), endpoints);
        h.assertTrue(
                restored.reachable(h.absolutePos(left), ConduitResource.FLUID).equals(List.of(h.absolutePos(right))),
                "Replaced TAP failed reconnection");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void overlappingComponentsKeepOneHopAndResourceBoundaries(GameTestHelper h) {
        var a = new BlockPos(1, 1, 1);
        var bridge = new BlockPos(3, 1, 1);
        var b = new BlockPos(5, 1, 1);
        var other = new BlockPos(7, 1, 1);
        var topology = new ConduitTopology(List.of(
                new ConduitTopology.Component(
                        ConduitResource.FLUID,
                        a,
                        1,
                        1,
                        List.of(
                                new ConduitTopology.Endpoint(a, ConduitResource.FLUID, STORE),
                                new ConduitTopology.Endpoint(bridge, ConduitResource.FLUID, STORE))),
                new ConduitTopology.Component(
                        ConduitResource.FLUID,
                        b,
                        1,
                        1,
                        List.of(
                                new ConduitTopology.Endpoint(bridge, ConduitResource.FLUID, STORE),
                                new ConduitTopology.Endpoint(b, ConduitResource.FLUID, STORE))),
                new ConduitTopology.Component(
                        ConduitResource.ITEM,
                        other,
                        1,
                        1,
                        List.of(
                                new ConduitTopology.Endpoint(a, ConduitResource.ITEM, STORE),
                                new ConduitTopology.Endpoint(other, ConduitResource.ITEM, STORE)))));
        h.assertTrue(
                topology.reachable(a, ConduitResource.FLUID).equals(List.of(bridge)),
                "Snapshot introduced transitive endpoint bridges");
        h.assertTrue(
                topology.reachable(bridge, ConduitResource.FLUID).equals(List.of(a, b)),
                "Multiple attached components lost union/order");
        h.assertTrue(topology.reachable(a, ConduitResource.ITEM).equals(List.of(other)), "Resource networks mixed");
        h.succeed();
    }
}
