package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Immutable physical generation. Lazy connectivity snapshots contain positions, never resource state. */
public final class ConduitTopology {
    private final List<Component> components;
    private final Map<BlockPos, BlockPos> componentOrigins;

    private record Key(BlockPos endpoint, ConduitResource resource) {}

    public record Connectivity(List<BlockPos> positions, Set<BlockPos> membership) {}

    private final Map<Key, List<Component>> memberships = new HashMap<>();
    private final Map<Component, List<BlockPos>> positions = new IdentityHashMap<>();
    private final Map<Key, Connectivity> snapshots = new HashMap<>();
    private static final Connectivity DISCONNECTED = new Connectivity(List.of(), Set.of());
    private long snapshotBuilds;

    public long snapshotBuilds() {
        return snapshotBuilds;
    }

    public List<Component> components() {
        return components;
    }

    public Map<BlockPos, BlockPos> componentOrigins() {
        return componentOrigins;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConduitTopology t
                && components.equals(t.components)
                && componentOrigins.equals(t.componentOrigins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(components, componentOrigins);
    }

    public static final Comparator<BlockPos> POSITION = Comparator.<BlockPos>comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);
    public static final ConduitTopology EMPTY = new ConduitTopology(List.of());

    public enum Role {
        PRODUCER,
        CONSUMER,
        STORE
    }

    public record Endpoint(BlockPos position, ConduitResource resource, Set<Role> roles, Direction attachment) {
        public Endpoint {
            position = position.immutable();
            roles = Set.copyOf(roles);
        }

        public Endpoint(BlockPos position, ConduitResource resource, Role... roles) {
            this(position, resource, Set.of(roles), null);
        }

        public Endpoint attached(Direction direction) {
            return new Endpoint(position, resource, roles, direction);
        }
    }

    public record Component(ConduitResource resource, BlockPos origin, int trunks, int taps, List<Endpoint> endpoints) {
        public Component {
            endpoints = List.copyOf(endpoints);
        }

        public List<BlockPos> positions() {
            return endpoints.stream()
                    .map(Endpoint::position)
                    .distinct()
                    .sorted(POSITION)
                    .toList();
        }

        public List<BlockPos> with(Role role) {
            return endpoints.stream()
                    .filter(e -> e.roles().contains(role))
                    .map(Endpoint::position)
                    .distinct()
                    .sorted(POSITION)
                    .toList();
        }
        // Compatibility views for operations outside this migration slice.
        public List<BlockPos> inputs() {
            return with(Role.STORE);
        }

        public List<BlockPos> outputs() {
            return endpoints.stream()
                    .filter(e -> e.roles().contains(Role.CONSUMER) && !e.roles().contains(Role.PRODUCER))
                    .map(Endpoint::position)
                    .distinct()
                    .sorted(POSITION)
                    .toList();
        }
    }

    public record Endpoints(List<BlockPos> inputs, List<BlockPos> outputs) {}

    public ConduitTopology(List<Component> components, Map<BlockPos, BlockPos> componentOrigins) {
        this.components = List.copyOf(components);
        this.componentOrigins = Map.copyOf(componentOrigins);
        for (var component : components) {
            var members = component.positions();
            positions.put(component, members);
            for (var position : members)
                memberships
                        .computeIfAbsent(new Key(position, component.resource()), k -> new ArrayList<>())
                        .add(component);
        }
    }

    public ConduitTopology(List<Component> components) {
        this(components, Map.of());
    }
    /** Read-only inspection lookup retained by the existing compiler; never walks routes. */
    public Optional<Component> componentAt(BlockPos position) {
        var origin = componentOrigins.get(position);
        return origin == null
                ? Optional.empty()
                : components.stream().filter(c -> c.origin().equals(origin)).findFirst();
    }

    public static boolean contains(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX()
                && pos.getX() <= max.getX()
                && pos.getY() >= min.getY()
                && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ()
                && pos.getZ() <= max.getZ();
    }

    public static ConduitTopology compile(Level level, BlockPos min, BlockPos max, Collection<Endpoint> endpoints) {
        if (ConduitPerformance.ENABLED) ++ConduitPerformance.rebuilds;
        var byPosition = new HashMap<BlockPos, List<Endpoint>>();
        for (var e : endpoints)
            if (contains(e.position(), min, max))
                byPosition.computeIfAbsent(e.position(), p -> new ArrayList<>()).add(e);
        var nodes = new TreeMap<BlockPos, ConduitResource>(POSITION);
        for (var pos : BlockPos.betweenClosed(min, max))
            if (level.getBlockState(pos).getBlock() instanceof ConduitBlock block)
                nodes.put(pos.immutable(), block.resource());
        var visited = new HashSet<BlockPos>();
        var components = new ArrayList<Component>();
        var origins = new HashMap<BlockPos, BlockPos>();
        for (var entry : nodes.entrySet()) {
            var origin = entry.getKey();
            if (!visited.add(origin)) continue;
            var resource = entry.getValue();
            var queue = new ArrayDeque<BlockPos>();
            queue.add(origin);
            var connected = new LinkedHashSet<Endpoint>();
            int trunks = 0, taps = 0;
            while (!queue.isEmpty()) {
                if (ConduitPerformance.ENABLED) {
                    ++ConduitPerformance.nodes;
                    ConduitPerformance.edges += 6;
                }
                var pos = queue.removeFirst();
                origins.put(pos, origin);
                boolean tap = level.getBlockState(pos).getValue(ConduitBlock.MODE) == ConduitMode.TAP;
                if (tap) ++taps;
                else ++trunks;
                for (var direction : Direction.values()) {
                    var next = pos.relative(direction);
                    if (nodes.get(next) == resource) {
                        if (visited.add(next)) queue.add(next);
                    } else if (tap)
                        for (var endpoint : byPosition.getOrDefault(next, List.of()))
                            if (endpoint.resource() == resource && inwardAttachment(level, endpoint, pos, min, max)) {
                                connected.add(endpoint);
                            }
                }
            }
            components.add(new Component(
                    resource,
                    origin,
                    trunks,
                    taps,
                    connected.stream()
                            .sorted(Comparator.comparing(Endpoint::position, POSITION)
                                    .thenComparing(e ->
                                            e.roles().stream().sorted().toList().toString()))
                            .toList()));
        }
        return new ConduitTopology(components, origins);
    }

    public Endpoints expand(
            BlockPos actor,
            ConduitResource resource,
            Collection<BlockPos> directInputs,
            Collection<BlockPos> directOutputs) {
        var inputs = new TreeSet<BlockPos>(POSITION);
        inputs.addAll(directInputs);
        var outputs = new TreeSet<BlockPos>(POSITION);
        outputs.addAll(directOutputs);
        for (var component : memberships.getOrDefault(new Key(actor, resource), List.of())) {
            inputs.addAll(component.inputs());
            outputs.addAll(component.outputs());
        }
        return new Endpoints(List.copyOf(inputs), List.copyOf(outputs));
    }

    public List<BlockPos> reachable(BlockPos endpoint, ConduitResource resource) {
        return connectivity(endpoint, resource).positions();
    }
    /** Server-thread lookup; a position attached to several components retains the old one-hop union semantics. */
    public Connectivity connectivity(BlockPos endpoint, ConduitResource resource) {
        if (ConduitPerformance.ENABLED) ++ConduitPerformance.lookups;
        var key = new Key(endpoint, resource);
        var attached = memberships.get(key);
        if (attached == null) return DISCONNECTED;
        var cached = snapshots.get(key);
        if (cached != null) return cached;
        long start = ConduitPerformance.ENABLED ? System.nanoTime() : 0;
        ++snapshotBuilds;
        var result = new TreeSet<BlockPos>(POSITION);
        for (var component : attached) result.addAll(positions.get(component));
        result.remove(endpoint);
        var snapshot = new Connectivity(List.copyOf(result), Set.copyOf(result));
        snapshots.put(new Key(endpoint.immutable(), resource), snapshot);
        if (ConduitPerformance.ENABLED) {
            ++ConduitPerformance.misses;
            ConduitPerformance.components += attached.size();
            ConduitPerformance.endpoints +=
                    attached.stream().mapToInt(c -> c.endpoints().size()).sum();
            ConduitPerformance.nanos += System.nanoTime() - start;
        }
        return snapshot;
    }

    private static boolean inwardAttachment(
            Level level, Endpoint endpoint, BlockPos conduit, BlockPos min, BlockPos max) {
        return endpoint.attachment() == null
                || conduit.equals(endpoint.position().relative(endpoint.attachment()));
    }

    public List<String> diagnostics() {
        var result = new ArrayList<String>();
        result.add("Conduits: Item components "
                + components.stream()
                        .filter(c -> c.resource() == ConduitResource.ITEM)
                        .count() + " / Fluid components "
                + components.stream()
                        .filter(c -> c.resource() == ConduitResource.FLUID)
                        .count() + " / TAP "
                + components.stream().mapToInt(Component::taps).sum() + " / TRUNK "
                + components.stream().mapToInt(Component::trunks).sum());
        for (var c : components) {
            if (result.size() >= 33) {
                result.add("Further conduit components omitted");
                break;
            }
            result.add(c.resource() + " @ " + c.origin().toShortString() + ": TRUNK " + c.trunks() + ", TAP " + c.taps()
                    + ", endpoints " + c.positions().size() + " connected");
        }
        return List.copyOf(result);
    }
}
