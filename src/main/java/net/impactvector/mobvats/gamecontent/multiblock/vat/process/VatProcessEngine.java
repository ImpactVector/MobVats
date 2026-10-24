package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Controller-owned generic executor. Topology selects claims; definitions select operations. */
public final class VatProcessEngine {
    public static final int CADENCE = 10;
    private final MultiblockVat controller;
    private final MobProcessRegistry registry;
    private final Map<BlockPos, SpecimenProcessState> states = new TreeMap<>(VatTopologyCompiler.POSITION);
    private final Map<BlockPos, UUID> stateOwners = new HashMap<>();
    private final Map<BlockPos, Map<String, ProcessResult>> status = new TreeMap<>(VatTopologyCompiler.POSITION);
    private List<ProcessContext> contexts = List.of();
    private List<BlockPos> scheduledOrder = List.of();
    private final Map<BlockPos, ProcessContext> contextsByHead = new HashMap<>();
    private long compiledRevision = -1, registryRevision = -1, legacyXp;
    private int cadence, compilations;
    private BlockPos nextHead;
    private long suspensionAt = Long.MIN_VALUE;

    /** World-time cooldowns freeze alongside tick-count progress. No signal is persisted.
     * A powered reload treats the interval since the saved maintenance epoch as suspended. */
    public void maintainSuspension() {
        if (!controller.isAssembled() || !(controller.getWorld() instanceof ServerLevel)) return;
        long now = controller.getWorld().getGameTime();
        long elapsed = suspensionAt == Long.MIN_VALUE ? 0 : Math.max(0, now - suspensionAt);
        suspensionAt = now;
        if (elapsed == 0) return;
        boolean inhibited = controller.controllerInhibited();
        for (var tool : controller.toolPorts()) if (inhibited || tool.redstoneInhibited()) tool.pauseCooldown(elapsed);
        compile();
        boolean changed = false;
        for (var context : contexts) {
            var shifted = new HashSet<String>();
            for (var definition : context.profile().definitions()) {
                var state = context.state().existingProcess(definition.stateId());
                if (state == null || state.data.getAllKeys().stream().noneMatch(VatProcessEngine::cooldownEpoch))
                    continue;
                if (!inhibited
                        && (definition.exclusiveUse() == null || !context.actorsInhibited(definition.exclusiveUse())))
                    continue;
                if (!shifted.add(definition.stateId())) continue;
                for (var key : state.data.getAllKeys())
                    if (cooldownEpoch(key)) {
                        state.data.putLong(key, state.data.getLong(key) + elapsed);
                        changed = true;
                    }
            }
        }
        if (changed) controller.processStateChanged(false);
    }

    private static boolean cooldownEpoch(String key) {
        return key.equals("interactionAt") || key.startsWith("interactionCooldown:");
    }

    public BlockPos nextHead() {
        return nextHead;
    }

    public double multiplier(BlockPos head) {
        var context = contextsByHead.get(head);
        return context == null ? 0.5 : context.multiplier();
    }

    public VatProcessEngine(MultiblockVat controller) {
        this(controller, MobProcessRegistry.DEFAULT);
    }

    public VatProcessEngine(MultiblockVat controller, MobProcessRegistry registry) {
        this.controller = controller;
        this.registry = registry;
    }

    public MultiblockVat controller() {
        return controller;
    }

    public long experienceMb() {
        return controller.storage().clusters().stream()
                .mapToLong(c -> c.tank()
                                .getFluid()
                                .is(net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get())
                        ? c.tank().getFluidAmount()
                        : 0)
                .sum();
    }

    public long legacyXpPending() {
        return legacyXp;
    }
    /** Per-actuator round robin avoids cadence/cooldown resonance starving alternate targets. */
    public boolean toolTurn(
            net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatToolPortEntity tool,
            String use,
            ProcessContext current) {
        var eligible = contexts.stream()
                .filter(c -> c.operational()
                        && c.state().stage() == LifeStage.ADULT
                        && c.routes(use).stream().anyMatch(r -> r.actor().equals(tool.getBlockPos())))
                .filter(c -> {
                    if (use.equals(PassiveUses.SHEAR))
                        return !c.assembly().head().specimenState().getBoolean("Sheared");
                    var d = PassiveMobDefinitionRegistry.resolve(c.identity());
                    if (d == null || !d.lifecycle()) return false;
                    if (c.itemInputs().stream()
                            .anyMatch(p -> d.feed().accepts(p.staging().getStackInSlot(0))
                                    && p.staging().getStackInSlot(0).getCount()
                                            >= d.feed().count())) return true;
                    return d.feeding_slurry().supported()
                            && c.routes(PassiveUses.FEED).stream()
                                    .anyMatch(r -> controller.storage().connected(r.actor()).stream()
                                            .anyMatch(t -> t.getFluid()
                                                            .is(net.impactvector.mobvats.gamecontent.fluid.ProductFluids
                                                                    .FEEDING_SLURRY
                                                                    .get())
                                                    && t.getFluidAmount()
                                                            >= d.feeding_slurry()
                                                                    .cost()));
                })
                .sorted(Comparator.comparing(ProcessContext::head, VatTopologyCompiler.POSITION))
                .toList();
        if (eligible.isEmpty()) return false;
        var last = tool.interactionHead();
        var next = last == null
                ? eligible.getFirst()
                : eligible.stream()
                        .filter(c -> VatTopologyCompiler.POSITION.compare(c.head(), last) > 0)
                        .findFirst()
                        .orElse(eligible.getFirst());
        return next.head().equals(current.head());
    }

    public void addExperienceMb(long value) {
        if (value < 0) throw new IllegalArgumentException("XP");
        var request = new net.neoforged.neoforge.fluids.FluidStack(
                net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get(), Math.toIntExact(value));
        if (controller
                        .processFluids()
                        .inputHandler()
                        .fill(request, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)
                != request.getAmount()) throw new IllegalStateException("Physical XP fluid capacity required");
        controller
                .processFluids()
                .inputHandler()
                .fill(request, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    }

    public int compilationCount() {
        return compilations;
    }

    public CowProcesses.Snapshot snapshot(BlockPos head) {
        return CowProcesses.snapshot(states.get(head));
    }

    public Map<String, ProcessResult> status(BlockPos head) {
        return Collections.unmodifiableMap(status.getOrDefault(head, Map.of()));
    }
    /** Presentation of the existing evaluated interaction; never executes or refreshes a process. */
    public Optional<ProcessResult> interactionStatus(BlockPos head, String interaction) {
        return status(head).entrySet().stream()
                .filter(e ->
                        e.getKey().startsWith("55_interaction_") && e.getKey().endsWith("_" + interaction))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Map<String, ProcessResult> actorStatus(BlockPos head, String use) {
        var context = contextsByHead.get(head);
        if (context == null) return Map.of();
        var result = new TreeMap<String, ProcessResult>();
        for (var definition : context.profile().definitions())
            if (use.equals(definition.exclusiveUse())) {
                var evaluated = status(head).get(definition.id());
                if (evaluated != null) result.put(definition.id(), evaluated);
            }
        return Collections.unmodifiableMap(result);
    }

    public SpecimenProcessState state(BlockPos head) {
        return states.get(head);
    }

    public SpecimenProcessState presentationState(BlockPos head, UUID owner) {
        return Objects.equals(stateOwners.get(head), owner) ? states.get(head) : null;
    }

    /** Availability through the same enabled endpoints used by passive feeding, without reserving inputs. */
    public String feedingStatus(BlockPos head) {
        var context = contextsByHead.get(head);
        if (context == null
                || !Objects.equals(
                        stateOwners.get(head), context.assembly().head().specimenId()))
            return "Feeding data unavailable";
        var definition = PassiveMobDefinitionRegistry.resolve(context.identity());
        if (definition == null || !definition.lifecycle()) return "Feeding not required";
        if (definition.feeding_slurry().supported()
                && context.routes(PassiveUses.FEED).stream()
                        .anyMatch(r -> controller.storage().connected(r.actor()).stream()
                                .anyMatch(t -> t.getFluid()
                                                .is(net.impactvector.mobvats.gamecontent.fluid.ProductFluids
                                                        .FEEDING_SLURRY
                                                        .get())
                                        && t.getFluidAmount()
                                                >= definition.feeding_slurry().cost())))
            return "Feeding slurry available";
        if (context.itemInputs().stream()
                .anyMatch(p -> definition.feed().accepts(p.staging().getStackInSlot(0))
                        && p.staging().getStackInSlot(0).getCount()
                                >= definition.feed().count())) return "Item feed available";
        return "Waiting for feed";
    }

    /** Read compiled timers and their last evaluations, without executing requirements or creating state. */
    public List<VatSpecimenSnapshot.Process> presentation(BlockPos head) {
        var context = contextsByHead.get(head);
        if (context == null
                || !Objects.equals(
                        stateOwners.get(head), context.assembly().head().specimenId())) return List.of();
        var result = new ArrayList<VatSpecimenSnapshot.Process>();
        var groups = new LinkedHashMap<String, List<ProcessDefinition>>();
        for (var definition : context.profile().definitions())
            groups.computeIfAbsent(definition.stateId(), k -> new ArrayList<>()).add(definition);
        for (var entry : groups.entrySet()) {
            var definitions = entry.getValue();
            var state = context.state().existingProcess(entry.getKey());
            var timer = definitions.stream()
                    .filter(d -> d.progress() != null)
                    .findFirst()
                    .orElse(null);
            var evaluated = timer == null ? null : status(head).get(timer.id());
            if (evaluated == null
                    || (evaluated.reason() != ProcessResult.Reason.RUNNING
                            && evaluated.reason() != ProcessResult.Reason.OUTPUT_BLOCKED
                            && evaluated.reason() != ProcessResult.Reason.BACKPRESSURE)) {
                var action = definitions.stream()
                        .filter(d -> d.progress() == null)
                        // Optional tool interactions can share a timer's state id. Their availability
                        // belongs to the actor view, rather than masking the timed process's result.
                        .filter(d -> timer == null || d.exclusiveUse() == null)
                        .map(d -> status(head).get(d.id()))
                        .filter(Objects::nonNull)
                        .filter(r -> r.reason() != ProcessResult.Reason.CONDITION_FAILED
                                && r.reason() != ProcessResult.Reason.IDLE)
                        .reduce((a, b) -> b)
                        .orElse(null);
                if (action != null) evaluated = action;
            }
            int elapsed = state == null ? 0 : state.elapsed;
            int duration = timer == null ? 0 : timer.progress().duration();
            double rate = timer == null ? 0 : timer.progress().rate().applyAsDouble(context);
            boolean suspended = !controller.processingEnabled() || !context.operational();
            boolean running = !suspended
                    && state != null
                    && state.phase == ProcessState.Phase.RUNNING
                    && evaluated != null
                    && evaluated.reason() == ProcessResult.Reason.RUNNING;
            String text = !controller.processingEnabled()
                    ? (controller.controllerInhibited() ? "Redstone inhibited" : "Paused")
                    : !context.assembly().head().activationReady()
                            ? "Awaiting Lightning"
                            : !controller.getTopology().operational(head)
                                    ? "Harness obstructed"
                                    : evaluated == null ? "Waiting" : VatSpecimenSnapshot.reason(evaluated.reason());
            String name =
                    entry.getKey().substring(entry.getKey().indexOf(':') + 1).replace('/', ' ');
            result.add(new VatSpecimenSnapshot.Process(
                    entry.getKey(),
                    state == null ? "IDLE" : state.phase.name(),
                    VatSpecimenSnapshot.label(name) + ": " + text,
                    elapsed,
                    duration,
                    rate,
                    running && rate > 0 && duration > 0
                            ? (long) Math.ceil(Math.max(0, duration - elapsed) / rate)
                            : -1));
        }
        return List.copyOf(result);
    }

    public void assembled() {
        states.keySet().retainAll(controller.getTopology().assemblies());
        stateOwners.keySet().retainAll(states.keySet());
        compiledRevision = -1;
        compile();
    }
    /** Drop derived endpoint/assembly references without touching committed specimen state. */
    public void retireContexts() {
        contexts = List.of();
        scheduledOrder = List.of();
        contextsByHead.clear();
        status.clear();
        compiledRevision = -1;
    }

    private void compile() {
        if (compiledRevision == controller.getTopologyRevision() && registryRevision == registry.revision()) return;
        if (registryRevision >= 0 && registryRevision != registry.revision()) controller.rebuildTopology();
        boolean stageChanged = false;
        for (var assembly : controller.getAssemblies()) {
            var head = assembly.head().getBlockPos();
            var owner = stateOwners.get(head);
            if (owner != null && !owner.equals(assembly.head().specimenId())) {
                states.remove(head);
                status.remove(head);
            }
            stateOwners.put(head.immutable(), assembly.head().specimenId());
            var state = states.get(head);
            if (state != null && assembly.head().lifeStage() != state.stage()) {
                assembly.head().setLifeStage(state.stage());
                stageChanged = true;
            }
        }
        if (stageChanged) controller.rebuildTopology();
        var compiled = new ArrayList<ProcessContext>();
        var buffers = new LinkedHashMap<net.minecraft.resources.ResourceLocation, ProductBufferDefinition>();
        boolean configured = false;
        for (var assembly : controller.getAssemblies().stream()
                .sorted(Comparator.comparing(a -> a.head().getBlockPos(), VatTopologyCompiler.POSITION))
                .toList()) {
            var profile = registry.resolve(assembly.head().getIdentity());
            if (profile == null) continue;
            if (profile.buffers() != null) {
                configured = true;
                profile.buffers().forEach((id, d) -> {
                    var previous = buffers.putIfAbsent(id, d);
                    if (previous != null && !previous.equals(d))
                        controller.processFluids().definitionConflict(id);
                });
            }
            var head = assembly.head().getBlockPos().immutable();
            var state = states.computeIfAbsent(head, k -> {
                var initial = new SpecimenProcessState();
                initial.stage(assembly.head().lifeStage());
                var definition =
                        PassiveMobDefinitionRegistry.resolve(assembly.head().getIdentity());
                if (initial.stage() == LifeStage.JUVENILE && definition != null && definition.lifecycle())
                    initial.growth().start();
                return initial;
            });
            compiled.add(new ProcessContext(this, assembly, state, profile));
            var results = status.computeIfAbsent(head, k -> new TreeMap<>());
            results.keySet()
                    .retainAll(profile.definitions().stream()
                            .map(ProcessDefinition::id)
                            .toList());
        }
        if (configured) controller.processFluids().configure(buffers);
        for (var context : compiled)
            context.assembly().head().setLifeStage(context.state().stage());
        contexts = List.copyOf(compiled);
        scheduledOrder = List.of();
        contextsByHead.clear();
        contexts.forEach(c -> contextsByHead.put(c.head(), c));
        status.keySet().retainAll(contextsByHead.keySet());
        compiledRevision = controller.getTopologyRevision();
        registryRevision = registry.revision();
        ++compilations;
    }

    public boolean tick() {
        maintainSuspension();
        if (!(controller.getWorld() instanceof ServerLevel)
                || !controller.isAssembled()
                || !controller.processingEnabled()) return false;
        compile();
        boolean changed = controller.processFluids().migrate();
        if (legacyXp > 0) {
            int ratio = net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.ratio();
            var request = net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.stack(
                    Math.min(legacyXp, Integer.MAX_VALUE / ratio));
            var handler = controller.processFluids().inputHandler();
            int accepted =
                    handler.fill(request, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)
                            / ratio
                            * ratio;
            if (accepted > 0) {
                request.setAmount(accepted);
                handler.fill(request, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                legacyXp -= accepted / ratio;
                changed = true;
            }
        }
        var order = scheduledHeads();
        for (var head : order) {
            var context = context(head);
            if (context == null) continue;
            context.multiplier(controller.frequencyMultiplier(head));
            changed |= execute(context, ProcessDefinition.Stage.TICK);
        }
        boolean scheduleChanged = false;
        if (++cadence >= CADENCE) {
            cadence = 0;
            changed |= transferCommittedOutputs();
            for (var head : order) {
                var context = context(head);
                if (context != null) changed |= execute(context, ProcessDefinition.Stage.CADENCE);
            }
            if (!order.isEmpty()) {
                var next = order.get(order.size() == 1 ? 0 : 1);
                scheduleChanged = !java.util.Objects.equals(nextHead, next);
                nextHead = next;
                scheduledOrder = List.of();
            }
        }
        if (changed || scheduleChanged) controller.processStateChanged(false);
        return changed;
    }
    /** Connectivity work only; committed outputs can leave an operator-disabled machine. */
    public boolean transferCommittedOutputs() {
        if (!controller.isAssembled() || !(controller.getWorld() instanceof ServerLevel)) return false;
        boolean changed = false;
        var toolPorts = new TreeSet<BlockPos>(VatTopologyCompiler.POSITION);
        controller.getTopology().actors().forEach(r -> toolPorts.add(r.actor()));
        controller.getTopology().toolLoading().forEach(r -> toolPorts.add(r.actor()));
        controller.toolPorts().forEach(p -> toolPorts.add(p.getBlockPos()));
        for (var pos : toolPorts)
            if (net.impactvector.mobvats.gamecontent.multiblock.common.LoadedWorld.blockEntity(
                                    controller.getWorld(), pos)
                            instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatToolPortEntity tool
                    && !tool.redstoneInhibited()) {
                var produced = tool.results().snapshot();
                var connected = controller
                        .getTopology()
                        .conduits()
                        .reachable(pos, net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM);
                for (var port : controller.itemPorts())
                    if (port.getIoDirection().isOutput()
                            && (connected.contains(port.getBlockPos())
                                    || port.getBlockPos().distManhattan(pos) == 1)) port.demandOutputs(produced);
                long moved = controller
                        .resourceRouting()
                        .publish(tool, net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM);
                for (var port : controller.itemPorts())
                    if (port.getIoDirection().isOutput())
                        moved += controller.resourceRouting().pull(port, port::demands, s -> false);
                long voided = tool.results()
                        .discard(controller.voids(
                                pos, net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM));
                if (voided > 0) {
                    controller.metrics().record("itemsVoided", voided);
                    changed = true;
                }
                if (moved > 0) {
                    tool.setChanged();
                    controller.metrics().record(MachineMetrics.ITEMS, moved);
                    changed = true;
                }
            }
        for (var port : controller.itemPorts())
            if (port.getIoDirection().isOutput()) {
                long moved = controller.resourceRouting().pull(port, port::demands, s -> false);
                if (moved > 0) {
                    controller.metrics().record(MachineMetrics.ITEMS, moved);
                    changed = true;
                }
            }
        if (!controller.killOutputs().isEmpty())
            for (var tool : controller.toolPorts().stream()
                    .sorted(Comparator.comparing(
                            net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatToolPortEntity::getBlockPos,
                            VatTopologyCompiler.POSITION))
                    .toList())
                if (!tool.redstoneInhibited()
                        && tool.results().isEmpty()
                        && tool.results().store(controller.killOutputs().snapshot())) {
                    controller.killOutputs().clear();
                    tool.setChanged();
                    changed = true;
                    break;
                }
        return changed;
    }

    private ProcessContext context(BlockPos head) {
        return contextsByHead.get(head);
    }

    private List<BlockPos> scheduledHeads() {
        if (!scheduledOrder.isEmpty()) return scheduledOrder;
        var heads = contexts.stream().map(ProcessContext::head).toList();
        if (heads.isEmpty()) return heads;
        int start = 0;
        if (nextHead != null) {
            while (start < heads.size() && VatTopologyCompiler.POSITION.compare(heads.get(start), nextHead) < 0)
                start++;
            if (start == heads.size()) start = 0;
        }
        var ordered = new ArrayList<BlockPos>(heads.size());
        for (int i = 0; i < heads.size(); i++) ordered.add(heads.get((start + i) % heads.size()));
        scheduledOrder = List.copyOf(ordered);
        return scheduledOrder;
    }

    private boolean execute(ProcessContext context, ProcessDefinition.Stage stage) {
        boolean changed = false;
        var uses = context.beginUses();
        for (var definition : context.profile().definitions(stage)) {
            var result = !context.operational()
                    ? ProcessResult.of(ProcessResult.Reason.SPECIMEN_INVALID)
                    : definition.exclusiveUse() != null && uses.contains(definition.exclusiveUse())
                            ? ProcessResult.of(ProcessResult.Reason.EXCLUSIVE_USE)
                            : definition.execute(context, context.state().process(definition.stateId()));
            if (result.reason() == ProcessResult.Reason.COMMITTED && definition.exclusiveUse() != null)
                uses.add(definition.exclusiveUse());
            status.get(context.head()).put(definition.id(), result);
            changed |= result.changed();
            if (context.assembly().head().lifeStage() != context.state().stage()) {
                context.assembly().head().setLifeStage(context.state().stage());
                controller.rebuildTopology();
            }
            // Tool acquisition/transformation can legitimately rebuild active claims during this cadence.
            if (compiledRevision != controller.getTopologyRevision()) {
                var head = context.head();
                double multiplier = context.multiplier();
                compile();
                for (var updated : contexts)
                    if (updated.head().equals(head)) {
                        context = updated;
                        context.multiplier(multiplier);
                        break;
                    }
            }
        }
        return changed;
    }

    public void saveSuspension(CompoundTag data) {
        data.putLong("redstoneMaintenanceAt", suspensionAt);
    }

    public void loadSuspension(CompoundTag data) {
        suspensionAt = data.contains("redstoneMaintenanceAt") ? data.getLong("redstoneMaintenanceAt") : Long.MIN_VALUE;
    }

    public void save(CompoundTag data, HolderLookup.Provider registries) {
        data.putInt("passiveStateVersion", 1);
        data.putLong("legacyXpMigration", legacyXp);
        data.putInt("processCadence", cadence);
        if (nextHead != null) data.putLong("processNextHead", nextHead.asLong());
        var generic = new ListTag();
        for (var entry : states.entrySet()) {
            var tag = new CompoundTag();
            tag.putLong("head", entry.getKey().asLong());
            var owner = stateOwners.get(entry.getKey());
            if (owner != null) tag.putUUID("specimenId", owner);
            tag.put("processes", entry.getValue().save(registries));
            generic.add(tag);
        }
        data.put("specimenProcesses", generic);
    }

    public void load(CompoundTag data, HolderLookup.Provider registries) {
        legacyXp = Math.max(
                0,
                data.getLong(
                        data.contains("legacyXpMigration")
                                ? "legacyXpMigration"
                                : data.contains("processXp") ? "processXp" : "cowXp"));
        states.clear();
        stateOwners.clear();
        cadence = Math.floorMod(data.getInt("processCadence"), CADENCE);
        nextHead = data.contains("processNextHead") ? BlockPos.of(data.getLong("processNextHead")) : null;
        compiledRevision = -1;
        contexts = List.of();
        scheduledOrder = List.of();
        contextsByHead.clear();
        status.clear();
        var generic = data.getList("specimenProcesses", Tag.TAG_COMPOUND);
        for (int i = 0; i < generic.size(); i++) {
            var tag = generic.getCompound(i);
            var head = BlockPos.of(tag.getLong("head"));
            states.put(head, SpecimenProcessState.load(tag.getCompound("processes"), registries));
            if (tag.hasUUID("specimenId")) stateOwners.put(head, tag.getUUID("specimenId"));
        }
        // Versioned generic state is authoritative; the old lifecycle is imported only once.
        if (data.getInt("passiveStateVersion") >= 1) return;
        for (var state : states.values()) CowProcesses.migrateOldGeneric(state);
        var legacy = data.getList("cowProcesses", Tag.TAG_COMPOUND);
        if (data.contains("cowProcesses")) {
            var heads = new HashSet<BlockPos>();
            for (int i = 0; i < legacy.size(); i++)
                heads.add(BlockPos.of(legacy.getCompound(i).getLong("head")));
            states.keySet().retainAll(heads);
        }
        for (int i = 0; i < legacy.size(); i++) {
            var tag = legacy.getCompound(i);
            CowProcesses.readLegacy(
                    states.computeIfAbsent(BlockPos.of(tag.getLong("head")), k -> new SpecimenProcessState()),
                    tag,
                    registries);
        }
    }

    public void assimilate(VatProcessEngine source) {
        if (states.isEmpty()) {
            suspensionAt = source.suspensionAt;
            nextHead = source.nextHead;
            cadence = source.cadence;
        }
        source.states.forEach((head, state) -> {
            if (states.putIfAbsent(head, state) == null && source.stateOwners.containsKey(head))
                stateOwners.put(head, source.stateOwners.get(head));
        });
        legacyXp = Math.addExact(legacyXp, source.legacyXp);
        compiledRevision = -1;
    }

    public void clear() {
        suspensionAt = Long.MIN_VALUE;
        states.clear();
        stateOwners.clear();
        status.clear();
        contexts = List.of();
        scheduledOrder = List.of();
        contextsByHead.clear();
        legacyXp = 0;
        cadence = 0;
        nextHead = null;
        compiledRevision = -1;
    }
}
