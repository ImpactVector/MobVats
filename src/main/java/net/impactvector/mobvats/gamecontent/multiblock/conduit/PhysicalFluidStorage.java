package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Endpoint-owned clusters and routing preference. Connectivity remains resource-only. */
public final class PhysicalFluidStorage {
    public record Cluster(List<FluidBufferEntity> members, FluidTank tank) {}

    private List<Cluster> clusters = List.of();
    private ConduitTopology topology = ConduitTopology.EMPTY;
    private final Map<BlockPos, List<Cluster>> bulkMembership = new HashMap<>();
    private boolean available = true;
    private long generation;
    private long bulkSnapshotBuilds;

    public long bulkSnapshotBuilds() {
        return bulkSnapshotBuilds;
    }
    /** Retire endpoint identities on pause, structural invalidation, and chunk unload. */
    public void clear() {
        ++generation;
        available = false;
        clusters = List.of();
        topology = ConduitTopology.EMPTY;
        services.clear();
        sinks = List.of();
        pumpInward = Map.of();
        bulkMembership.clear();
    }

    private Map<BlockPos, Direction> pumpInward = Map.of();

    public void pumpInward(Map<BlockPos, Direction> value) {
        pumpInward = Map.copyOf(value);
        bulkMembership.clear();
    }

    private boolean adjacent(BlockPos endpoint, BlockPos member) {
        return endpoint.distManhattan(member) == 1
                && (!pumpInward.containsKey(endpoint)
                        || endpoint.relative(pumpInward.get(endpoint)).equals(member));
    }

    private List<FluidSink> sinks = List.of();

    public void sinks(List<? extends FluidSink> values) {
        sinks = List.copyOf(values);
    }

    public IFluidHandler destination(
            BlockPos endpoint, boolean bulkOnly, java.util.function.Predicate<FluidStack> filter) {
        return new IFluidHandler() {
            private IFluidHandler current() {
                return withVoids(
                        handler(bulkOnly ? bulkConnected(endpoint) : connected(endpoint), true, filter),
                        sinks.stream()
                                .filter(e -> connectedEndpoints(endpoint, e.getBlockPos(), ConduitResource.FLUID))
                                .toList(),
                        filter);
            }

            public int getTanks() {
                return current().getTanks();
            }

            public FluidStack getFluidInTank(int i) {
                return current().getFluidInTank(i);
            }

            public int getTankCapacity(int i) {
                return current().getTankCapacity(i);
            }

            public boolean isFluidValid(int i, FluidStack s) {
                return current().isFluidValid(i, s);
            }

            public int fill(FluidStack s, FluidAction a) {
                return current().fill(s, a);
            }

            public FluidStack drain(FluidStack s, FluidAction a) {
                return current().drain(s, a);
            }

            public FluidStack drain(int n, FluidAction a) {
                return current().drain(n, a);
            }
        };
    }
    /** Simulation has no pulse or retained contents. Real tanks are always offered the entire request first. */
    public static IFluidHandler withVoids(
            IFluidHandler real, List<? extends FluidSink> sinks, java.util.function.Predicate<FluidStack> filter) {
        return new IFluidHandler() {
            public int getTanks() {
                return real.getTanks();
            }

            public FluidStack getFluidInTank(int i) {
                return real.getFluidInTank(i);
            }

            public int getTankCapacity(int i) {
                return real.getTankCapacity(i);
            }

            public boolean isFluidValid(int i, FluidStack s) {
                return real.isFluidValid(i, s);
            }

            public int fill(FluidStack s, FluidAction action) {
                if (!filter.test(s)) return 0;
                int moved = real.fill(s, action);
                if (moved == s.getAmount()) return moved;
                for (var sink : sinks)
                    if (!sink.isRemoved() && sink.accepts(s)) {
                        if (action.execute()) sink.consumed(s.getAmount() - moved);
                        return s.getAmount();
                    }
                return moved;
            }

            public FluidStack drain(FluidStack s, FluidAction a) {
                return real.drain(s, a);
            }

            public FluidStack drain(int n, FluidAction a) {
                return real.drain(n, a);
            }
        };
    }

    private final NavigableMap<BlockPos, ServiceStaging> services = new TreeMap<>(ConduitTopology.POSITION);

    public void services(Map<BlockPos, ServiceStaging> endpoints) {
        services.clear();
        services.putAll(endpoints);
    }

    public List<String> diagnostics() {
        var result = new ArrayList<String>();
        services.forEach((p, s) -> s.diagnostics().forEach(d -> result.add(d + " @ " + p.toShortString())));
        clusters.forEach(c -> result.add(
                "Bulk storage: " + c.tank().getFluidAmount() + "/" + c.tank().getCapacity() + " mB"));
        return result;
    }

    public boolean evacuate() {
        boolean movedAny = false;
        for (var e : services.entrySet())
            for (var tank : e.getValue().tanks())
                if (!tank.isEmpty()) {
                    int moved = destination(e.getKey(), true, s -> true)
                            .fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                    tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
                    movedAny |= moved > 0;
                }
        return movedAny;
    }
    /** Only the producing endpoint's stage may accept newly completed production. */
    public List<FluidTank> outputTanks(BlockPos endpoint, FluidStack request) {
        var result = new ArrayList<>(bulkConnected(endpoint));
        var service = services.get(endpoint);
        if (service != null) result.add(service.tank(request));
        return result;
    }

    public final class OutputReservation {
        private final BlockPos endpoint;
        private final List<FluidTank> bulk;
        private final ServiceStaging service;
        private final ServiceStaging.Pending pending;
        private final List<FluidSink> connectedSinks;
        private final Map<FluidSink, List<FluidStack>> disposal = new LinkedHashMap<>();

        private OutputReservation(BlockPos endpoint) {
            this.endpoint = endpoint.immutable();
            bulk = bulkConnected(endpoint);
            service = services.get(endpoint);
            pending = service == null ? null : service.prepare();
            connectedSinks = sinks.stream()
                    .filter(s -> connectedEndpoints(endpoint, s.getBlockPos(), ConduitResource.FLUID))
                    .toList();
        }
        /** Reserve terminal overflow only after the caller has exhausted real output capacity. */
        public boolean dispose(FluidStack remainder) {
            for (var sink : connectedSinks)
                if (!sink.isRemoved() && sink.accepts(remainder)) {
                    disposal.computeIfAbsent(sink, s -> new ArrayList<>()).add(remainder.copy());
                    return true;
                }
            return false;
        }

        public List<FluidTank> tanks(FluidStack output) {
            var result = new ArrayList<>(bulk);
            if (pending != null) result.add(pending.tank(output));
            return result;
        }

        public boolean valid() {
            return bulk.equals(bulkConnected(endpoint))
                    && service == services.get(endpoint)
                    && (pending == null || pending.valid())
                    && disposal.entrySet().stream()
                            .allMatch(e -> sinks.contains(e.getKey())
                                    && !e.getKey().isRemoved()
                                    && connectedEndpoints(endpoint, e.getKey().getBlockPos(), ConduitResource.FLUID)
                                    && e.getValue().stream().allMatch(e.getKey()::accepts));
        }

        public void publish() {
            if (pending != null) pending.publish();
            disposal.forEach((sink, stacks) -> stacks.forEach(s -> sink.consumed(s.getAmount())));
        }
    }

    public OutputReservation prepareOutputs(BlockPos endpoint) {
        return new OutputReservation(endpoint);
    }

    public List<Cluster> clusters() {
        return clusters;
    }

    public static List<FluidBufferEntity> members(Level level, BlockPos min, BlockPos max) {
        var out = new ArrayList<FluidBufferEntity>();
        for (var pos : BlockPos.betweenClosed(min, max))
            if (level.hasChunkAt(pos)
                    && level.getBlockEntity(pos) instanceof FluidBufferEntity e
                    && e.routingAvailable()) out.add(e);
        out.sort(Comparator.comparing(FluidBufferEntity::getBlockPos, ConduitTopology.POSITION));
        return out;
    }

    public void compile(List<FluidBufferEntity> members, ConduitTopology topology) {
        ++generation;
        members = members.stream()
                .sorted(Comparator.comparing(FluidBufferEntity::getBlockPos, ConduitTopology.POSITION))
                .toList();
        this.topology = topology;
        available = true;
        bulkMembership.clear();
        var remaining = new LinkedHashMap<BlockPos, FluidBufferEntity>();
        members.forEach(e -> remaining.put(e.getBlockPos(), e));
        var result = new ArrayList<Cluster>();
        while (!remaining.isEmpty()) {
            var first = remaining.values().iterator().next();
            var owner = first.getLevel() == null
                    ? null
                    : ConduitStructure.containing(first.getLevel(), first.getBlockPos());
            remaining.remove(first.getBlockPos());
            var group = new ArrayList<FluidBufferEntity>();
            group.add(first);
            var queue = new ArrayDeque<FluidBufferEntity>();
            queue.add(first);
            FluidStack content = first.tank.getFluid().copy();
            while (!queue.isEmpty()) {
                var e = queue.removeFirst();
                for (var face : Direction.values()) {
                    var next = remaining.get(e.getBlockPos().relative(face));
                    if (next == null
                            || (next.getLevel() == null
                                            ? null
                                            : ConduitStructure.containing(next.getLevel(), next.getBlockPos()))
                                    != owner
                            || !first.filter().equals(next.filter())
                            || !content.isEmpty()
                                    && !next.tank.isEmpty()
                                    && !FluidStack.isSameFluidSameComponents(content, next.tank.getFluid())) continue;
                    if (content.isEmpty() && !next.tank.isEmpty())
                        content = next.tank.getFluid().copy();
                    remaining.remove(next.getBlockPos());
                    group.add(next);
                    queue.add(next);
                }
            }
            group.sort(Comparator.comparing(FluidBufferEntity::getBlockPos, ConduitTopology.POSITION));
            var immutable = List.copyOf(group);
            result.add(new Cluster(immutable, new ClusterTank(immutable, owner)));
        }
        clusters = List.copyOf(result);
    }

    private final class ClusterTank extends FluidTank {
        private final List<FluidBufferEntity> members;
        private final long compiledGeneration;
        private final long[] memberEpochs;
        private final ConduitCuboidController<?> owner;

        ClusterTank(List<FluidBufferEntity> members, ConduitCuboidController<?> owner) {
            super(Math.multiplyExact(FluidBufferEntity.CAPACITY, members.size()));
            this.members = members;
            memberEpochs =
                    members.stream().mapToLong(FluidBufferEntity::storageEpoch).toArray();
            this.owner = owner;
            compiledGeneration = generation;
            setValidator(s -> members.stream().allMatch(e -> e.accepts(s)));
            refresh();
        }

        private boolean valid() {
            if (!available || compiledGeneration != generation) return false;
            var filter = members.getFirst().filter();
            FluidStack identity = FluidStack.EMPTY;
            int index = 0;
            for (var member : members) {
                if (member.storageEpoch() != memberEpochs[index++]
                        || !member.routingAvailable()
                        || !filter.equals(member.filter())) return false;
                var level = member.getLevel();
                var pos = member.getBlockPos();
                // The loaded guard precedes every world lookup, including the unload-callback interval.
                if (level != null
                        && (!level.hasChunkAt(pos)
                                || level.getBlockEntity(pos) != member
                                || ConduitStructure.containing(level, pos) != owner)) return false;
                var stored = member.tank.getFluid();
                if (!stored.isEmpty()) {
                    if (!identity.isEmpty() && !FluidStack.isSameFluidSameComponents(identity, stored)) return false;
                    identity = stored;
                }
            }
            return true;
        }

        public int getCapacity() {
            return valid() ? super.getCapacity() : 0;
        }

        public boolean isFluidValid(FluidStack stack) {
            return valid() && super.isFluidValid(stack);
        }

        private void refresh() {
            if (!valid()) {
                fluid = FluidStack.EMPTY;
                return;
            }
            FluidStack value = FluidStack.EMPTY;
            for (var e : members)
                if (!e.tank.isEmpty()) {
                    if (value.isEmpty()) value = e.tank.getFluid().copy();
                    else value.grow(e.tank.getFluidAmount());
                }
            fluid = value;
        }

        public FluidStack getFluid() {
            refresh();
            return super.getFluid();
        }

        public int getFluidAmount() {
            refresh();
            return super.getFluidAmount();
        }

        public boolean isEmpty() {
            refresh();
            return super.isEmpty();
        }

        public void setFluid(FluidStack value) {
            if (!valid()) throw new IllegalStateException("Retired fluid cluster");
            super.setFluid(value.copy());
            onContentsChanged();
        }

        protected void onContentsChanged() {
            int remaining = fluid.getAmount();
            for (var e : members) {
                var value = fluid.copy();
                value.setAmount(Math.min(remaining, FluidBufferEntity.CAPACITY));
                e.tank.setFluid(value.isEmpty() ? FluidStack.EMPTY : value);
                e.setChanged();
                remaining -= value.getAmount();
            }
        }

        public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
            if (!valid()) return 0;
            refresh();
            return super.fill(stack, action);
        }

        public FluidStack drain(FluidStack stack, IFluidHandler.FluidAction action) {
            if (!valid()) return FluidStack.EMPTY;
            refresh();
            return super.drain(stack, action);
        }

        public FluidStack drain(int amount, IFluidHandler.FluidAction action) {
            if (!valid()) return FluidStack.EMPTY;
            refresh();
            return super.drain(amount, action);
        }
    }

    public List<FluidTank> bulkConnected(BlockPos endpoint) {
        if (ConduitPerformance.ENABLED) ++ConduitPerformance.bulkQueries;
        if (!available) return List.of();
        var members = bulkMembership.get(endpoint);
        if (members == null) {
            ++bulkSnapshotBuilds;
            var connected =
                    topology.connectivity(endpoint, ConduitResource.FLUID).membership();
            members = clusters.stream()
                    .filter(c -> c.members().stream()
                            .anyMatch(m -> endpoint.equals(m.getBlockPos())
                                    || adjacent(endpoint, m.getBlockPos())
                                    || connected.contains(m.getBlockPos())))
                    .toList();
            bulkMembership.put(endpoint.immutable(), members);
            if (ConduitPerformance.ENABLED) {
                ++ConduitPerformance.storageMisses;
                ConduitPerformance.buffers +=
                        clusters.stream().mapToInt(c -> c.members().size()).sum();
            }
        } else if (ConduitPerformance.ENABLED) ++ConduitPerformance.storageHits;
        // Availability is live, including the interval before deferred structural recompilation.
        return members.stream()
                .filter(c -> c.tank().getCapacity() > 0)
                .map(Cluster::tank)
                .toList();
    }

    public List<FluidTank> connected(BlockPos endpoint) {
        var result = new ArrayList<>(bulkConnected(endpoint));
        services.forEach((pos, stage) -> {
            if (connected(endpoint, pos)) result.addAll(stage.tanks());
        });
        return List.copyOf(result);
    }

    public boolean connectedEndpoints(BlockPos endpoint, BlockPos member, ConduitResource resource) {
        return adjacent(endpoint, member)
                || topology.connectivity(endpoint, resource).membership().contains(member);
    }

    private boolean connected(BlockPos endpoint, BlockPos member) {
        return endpoint.equals(member) || connectedEndpoints(endpoint, member, ConduitResource.FLUID);
    }

    public FluidTank select(BlockPos endpoint, FluidStack request) {
        return select(connected(endpoint), request);
    }

    public FluidTank selectOutput(BlockPos endpoint, FluidStack request) {
        return selectOutput(endpoint, request, null);
    }

    public FluidTank selectOutput(BlockPos endpoint, FluidStack request, Object excluded) {
        var bulk = select(
                bulkConnected(endpoint).stream()
                        .filter(t -> t != excluded
                                && t.fill(request, IFluidHandler.FluidAction.SIMULATE) == request.getAmount())
                        .toList(),
                request);
        if (bulk != null) return bulk;
        var service = services.get(endpoint);
        if (service == null) return null;
        var tank = service.tank(request);
        return tank != excluded && tank.fill(request, IFluidHandler.FluidAction.SIMULATE) == request.getAmount()
                ? tank
                : null;
    }

    public static FluidTank select(List<FluidTank> tanks, FluidStack request) {
        for (var t : tanks)
            if (!t.isEmpty() && FluidStack.isSameFluidSameComponents(t.getFluid(), request) && t.isFluidValid(request))
                return t;
        for (var t : tanks) if (t.isEmpty() && t.isFluidValid(request)) return t;
        return null;
    }

    public IFluidHandler handler(BlockPos endpoint, boolean input, java.util.function.Predicate<FluidStack> filter) {
        return new IFluidHandler() {
            private IFluidHandler current() {
                return input ? destination(endpoint, false, filter) : handler(connected(endpoint), false, filter);
            }

            public int getTanks() {
                return current().getTanks();
            }

            public FluidStack getFluidInTank(int i) {
                return current().getFluidInTank(i);
            }

            public int getTankCapacity(int i) {
                return current().getTankCapacity(i);
            }

            public boolean isFluidValid(int i, FluidStack s) {
                return current().isFluidValid(i, s);
            }

            public int fill(FluidStack s, FluidAction a) {
                return current().fill(s, a);
            }

            public FluidStack drain(FluidStack s, FluidAction a) {
                return current().drain(s, a);
            }

            public FluidStack drain(int n, FluidAction a) {
                return current().drain(n, a);
            }
        };
    }

    public static IFluidHandler handler(
            List<FluidTank> tanks, boolean input, java.util.function.Predicate<FluidStack> filter) {
        return new IFluidHandler() {
            public int getTanks() {
                return tanks.size();
            }

            public FluidStack getFluidInTank(int i) {
                var s = tanks.get(i).getFluid();
                return filter.test(s) ? s.copy() : FluidStack.EMPTY;
            }

            public int getTankCapacity(int i) {
                return tanks.get(i).getCapacity();
            }

            public boolean isFluidValid(int i, FluidStack s) {
                return filter.test(s) && tanks.get(i).isFluidValid(s);
            }

            public int fill(FluidStack s, FluidAction a) {
                if (!input || !filter.test(s)) return 0;
                var ordered = new ArrayList<>(tanks);
                ordered.sort(Comparator.comparing(FluidTank::isEmpty));
                int remaining = s.getAmount();
                for (var t : ordered) {
                    var portion = s.copy();
                    portion.setAmount(remaining);
                    remaining -= t.fill(portion, a);
                    if (remaining == 0) break;
                }
                return s.getAmount() - remaining;
            }

            public FluidStack drain(FluidStack s, FluidAction a) {
                if (input || !filter.test(s)) return FluidStack.EMPTY;
                var result = FluidStack.EMPTY;
                int remaining = s.getAmount();
                for (var t : tanks) {
                    var portion = s.copy();
                    portion.setAmount(remaining);
                    var taken = t.drain(portion, a);
                    if (!taken.isEmpty()) {
                        if (result.isEmpty()) result = taken;
                        else result.grow(taken.getAmount());
                        remaining -= taken.getAmount();
                    }
                    if (remaining == 0) break;
                }
                return result;
            }

            public FluidStack drain(int n, FluidAction a) {
                if (input || n <= 0) return FluidStack.EMPTY;
                var ordered = tanks.stream()
                        .filter(t -> !t.isEmpty() && filter.test(t.getFluid()))
                        .sorted(Comparator.comparing(t -> net.minecraft.core.registries.BuiltInRegistries.FLUID
                                .getKey(t.getFluid().getFluid())
                                .toString()))
                        .toList();
                for (var t : ordered) {
                    var wanted = t.getFluid().copy();
                    wanted.setAmount(n);
                    return drain(wanted, a);
                }
                return FluidStack.EMPTY;
            }
        };
    }
}
