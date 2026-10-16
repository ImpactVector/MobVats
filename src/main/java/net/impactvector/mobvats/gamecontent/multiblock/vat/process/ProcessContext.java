package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

/** Cached controller-owned runtime view of already compiled topology. Never serialized. */
public final class ProcessContext {
    private final VatProcessEngine engine;
    private final MobAssembly assembly;
    private final SpecimenProcessState state;
    private final MobProcessRegistry.Profile profile;
    private final List<VatTopology.Assignment> actors, loading;
    private final Map<String, List<VatTopology.Assignment>> byUse = new HashMap<>();
    private final Map<BlockPos, VatItemPortEntity> items = new HashMap<>();
    private final Map<BlockPos, VatFluidPortEntity> fluidPorts = new HashMap<>();
    private final Map<BlockPos, VatToolPortEntity> tools = new HashMap<>();
    private final Map<BlockPos, AbstractVatEntity> actorEntities = new HashMap<>();
    private final Map<String, Object> strategies = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T strategy(String id, java.util.function.Supplier<T> factory) {
        return (T) strategies.computeIfAbsent(id, k -> factory.get());
    }

    private final Set<String> committedUses = new HashSet<>();

    Set<String> beginUses() {
        committedUses.clear();
        return committedUses;
    }

    private double multiplier = 0.5;

    public ProcessContext(
            VatProcessEngine engine,
            MobAssembly assembly,
            SpecimenProcessState state,
            MobProcessRegistry.Profile profile) {
        this.engine = engine;
        this.assembly = assembly;
        this.state = state;
        this.profile = profile;
        var topology = controller().getTopology();
        var head = head();
        actors = topology.actors().stream().filter(r -> r.head().equals(head)).toList();
        loading = topology.toolLoading().stream()
                .filter(r -> r.head().equals(head))
                .toList();
        for (var route : actors) {
            var list = byUse.computeIfAbsent(route.use(), k -> new ArrayList<>());
            list.add(route);
        }
        var positions = new TreeSet<BlockPos>(VatTopologyCompiler.POSITION);
        for (var route : actors) {
            positions.addAll(route.itemInputs());
            positions.addAll(route.itemOutputs());
            cacheActor(route);
        }
        for (var route : loading) {
            positions.addAll(route.itemInputs());
            positions.addAll(route.itemOutputs());
            cacheActor(route);
        }
        var fluidPositions = new TreeSet<BlockPos>(VatTopologyCompiler.POSITION);
        for (var route : actors) {
            fluidPositions.addAll(route.fluidInputs());
            fluidPositions.addAll(route.fluidOutputs());
        }
        for (var pos : fluidPositions)
            if (controller().getWorld().getBlockEntity(pos) instanceof VatFluidPortEntity port
                    && port.getMultiblockController()
                            .filter(c -> c == controller())
                            .isPresent()) fluidPorts.put(pos, port);
        for (var contact : topology.itemContacts()) if (contact.head().equals(head)) positions.add(contact.input());
        for (var pos : positions)
            if (controller().getWorld().getBlockEntity(pos) instanceof VatItemPortEntity port
                    && port.getMultiblockController()
                            .filter(c -> c == controller())
                            .isPresent()) items.put(pos, port);
    }

    private void cacheActor(VatTopology.Assignment route) {
        if (controller().getWorld().getBlockEntity(route.actor()) instanceof AbstractVatEntity part) {
            actorEntities.put(route.actor(), part);
            if (part instanceof VatToolPortEntity tool) tools.put(route.actor(), tool);
        }
    }

    public VatProcessEngine engine() {
        return engine;
    }

    public MultiblockVat controller() {
        return engine.controller();
    }

    public ServerLevel level() {
        return (ServerLevel) controller().getWorld();
    }

    public MobAssembly assembly() {
        return assembly;
    }

    public BlockPos head() {
        return assembly.head().getBlockPos();
    }

    public ResourceLocation identity() {
        return assembly.head().getIdentity();
    }

    public SpecimenProcessState state() {
        return state;
    }

    public MobProcessRegistry.Profile profile() {
        return profile;
    }

    public MachineMetrics metrics() {
        return controller().metrics();
    }

    public double multiplier() {
        return multiplier;
    }

    void multiplier(double value) {
        multiplier = value;
    }

    public YieldRemainders.Batch yieldPlan() {
        return state.yield().plan(controller().yieldMultiplier(head()), level().registryAccess());
    }

    public boolean operational() {
        return controller().getTopology().operational(head());
    }

    public List<VatTopology.Assignment> routes(String use) {
        return byUse.getOrDefault(PassiveUses.canonical(use), List.of()).stream()
                .filter(this::actorEnabled)
                .toList();
    }

    private boolean actorEnabled(VatTopology.Assignment route) {
        var actor = actorEntities.get(route.actor());
        return actor != null && !actor.isRemoved() && !actor.redstoneInhibited();
    }

    public boolean actorsInhibited(String use) {
        var assigned = byUse.getOrDefault(PassiveUses.canonical(use), List.of());
        return !assigned.isEmpty() && assigned.stream().noneMatch(this::actorEnabled);
    }

    public List<VatTopology.Assignment> loading() {
        return loading.stream().filter(this::actorEnabled).toList();
    }

    public List<VatItemPortEntity> inputsFor(String use) {
        if (use.isEmpty())
            return routes(PassiveUses.DRAIN).stream()
                    .flatMap(r -> r.itemInputs().stream())
                    .distinct()
                    .sorted(VatTopologyCompiler.POSITION)
                    .map(p -> itemPort(p, true))
                    .filter(Objects::nonNull)
                    .toList();
        var positions = new TreeSet<BlockPos>(VatTopologyCompiler.POSITION);
        for (var route : routes(use)) positions.addAll(route.itemInputs());
        return positions.stream()
                .map(p -> itemPort(p, true))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<VatItemPortEntity> itemInputs() {
        return inputsFor(PassiveUses.FEED).stream()
                .filter(p -> p.accepts(p.staging().getStackInSlot(0)))
                .toList();
    }

    public VatItemPortEntity itemPort(BlockPos pos, boolean input) {
        var port = items.get(pos);
        return port != null
                        && !port.isRemoved()
                        && port.getIoDirection().isInput() == input
                        && !port.redstoneInhibited()
                        && (!input
                                || port.staging().getStackInSlot(0).isEmpty()
                                || port.accepts(port.staging().getStackInSlot(0)))
                ? port
                : null;
    }

    public VatToolPortEntity tool(VatTopology.Assignment route) {
        var port = tools.get(route.actor());
        return port != null
                        && !port.isRemoved()
                        && !port.redstoneInhibited()
                        && port.getMultiblockController()
                                .filter(c -> c == controller())
                                .isPresent()
                ? port
                : null;
    }

    public AbstractVatEntity actor(VatTopology.Assignment route) {
        return actorEntities.get(route.actor());
    }

    public List<VatItemPortEntity> outputs(VatTopology.Assignment route) {
        return route.itemOutputs().stream()
                .map(p -> itemPort(p, false))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<VatFluidPortEntity> fluidEndpoints(VatTopology.Assignment route, boolean input) {
        return (input ? route.fluidInputs() : route.fluidOutputs())
                .stream()
                        .map(fluidPorts::get)
                        .filter(p -> p != null
                                && !p.isRemoved()
                                && !p.redstoneInhibited()
                                && p.getIoDirection().isInput() == input)
                        .toList();
    }

    public int fluidAmount(Fluid fluid) {
        var buffers = controller().processFluids();
        for (int i = 0; i < buffers.getTanks(); i++)
            if (buffers.getFluidInTank(i).is(fluid))
                return buffers.getFluidInTank(i).getAmount();
        return 0;
    }
}
