package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import java.util.stream.Stream;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Detached menu projection. Identity, biology and assignments retain their existing owners. */
public record VatSpecimenSnapshot(
        UUID id,
        long head,
        String species,
        String name,
        boolean adult,
        String status,
        double happiness,
        double yield,
        double frequency,
        double culture,
        String feedSource,
        String feeding,
        List<Process> processes,
        List<Actor> actors,
        String appearance) {
    public VatSpecimenSnapshot {
        processes = List.copyOf(processes);
        actors = List.copyOf(actors);
    }

    public record Process(
            String id, String phase, String status, int elapsed, int duration, double rate, long remainingTicks) {
        public double fraction() {
            return duration <= 0 ? 0 : Math.clamp((double) elapsed / duration, 0, 1);
        }
    }

    public record Actor(
            String type,
            long position,
            String operation,
            String item,
            String itemName,
            String status,
            long cooldownTicks) {}

    /** Tool Port first, then Blender; routing coordinates break ties but never place the miniature. */
    public Optional<Actor> primaryProductionActor() {
        return actors.stream()
                .filter(a -> a.type().equals("tool port") || a.type().equals("blender"))
                .filter(a -> !a.operation().equals("tool loading"))
                .min(Comparator.comparingInt((Actor a) -> a.type().equals("tool port") ? 0 : 1)
                        .thenComparingLong(Actor::position)
                        .thenComparing(Actor::operation)
                        .thenComparing(Actor::item));
    }

    public static List<VatSpecimenSnapshot> capture(MultiblockVat vat) {
        return vat.specimenSnapshots();
    }

    static VatSpecimenSnapshot capture(MultiblockVat vat, VatHeadEntity h) {
        var pos = h.getBlockPos();
        var identity = h.getIdentity();
        var definition = identity == null ? null : PassiveMobDefinitionRegistry.resolve(identity);
        boolean valid = vat.isAssembled()
                && identity != null
                && h.geometry() != null
                && vat.getTopology().assemblies().contains(pos);
        var runtime = valid ? vat.processEngine().presentationState(pos, h.specimenId()) : null;
        String name = identity == null
                ? "Unknown specimen"
                : BuiltInRegistries.ENTITY_TYPE
                        .getOptional(identity)
                        .map(type -> type.getDescription().getString())
                        .orElse(identity.toString());
        var custom = h.specimenState().getString("CustomName");
        if (!custom.isEmpty()) {
            try {
                var component =
                        Component.Serializer.fromJson(custom, vat.getWorld().registryAccess());
                if (component != null) name = component.getString();
            } catch (RuntimeException ignored) {
                /* Keep the species label for malformed captured names. */
            }
        }
        String status = !vat.isAssembled()
                ? "Unformed"
                : identity == null
                        ? "Identity missing"
                        : !valid
                                ? "Invalid geometry"
                                : !h.activationReady()
                                        ? "Awaiting Lightning"
                                        : !vat.getTopology().operational(pos)
                                                ? "Harness obstructed"
                                                : !vat.isMachineActive()
                                                        ? "Paused by operator"
                                                        : vat.controllerInhibited()
                                                                ? "Redstone inhibited"
                                                                : definition == null
                                                                        ? "Definition unavailable"
                                                                        : runtime == null
                                                                                ? "Waiting for process data"
                                                                                : "Operational";
        double yield = valid ? vat.yieldMultiplier(pos) : 0.5;
        var processes = valid ? vat.processEngine().presentation(pos) : List.<Process>of();
        if (status.equals("Operational")) {
            if (processes.stream()
                    .anyMatch(p ->
                            p.status().endsWith("output blocked") || p.status().endsWith("backpressure")))
                status = "Output blocked";
            else if (runtime.stage() == LifeStage.JUVENILE) status = "Growing";
        }
        var actors = new ArrayList<Actor>();
        var routes = Stream.concat(vat.getTopology().actors().stream(), vat.getTopology().toolLoading().stream())
                .filter(a -> a.head().equals(pos))
                .distinct()
                .sorted(Comparator.comparing(VatTopology.Assignment::actor, VatTopologyCompiler.POSITION)
                        .thenComparing(VatTopology.Assignment::use))
                .toList();
        for (var route : routes) addActor(vat, actors, route.actor(), route.use(), pos);
        vat.getAssemblies().stream().filter(a -> a.head() == h).findFirst().ifPresent(a -> {
            for (var harness : a.harnessPositions()) addActor(vat, actors, harness, "Suspension", pos);
            if (actors.stream()
                    .noneMatch(
                            actor -> actor.position() == a.drain().getBlockPos().asLong()))
                addActor(vat, actors, a.drain().getBlockPos(), "Drain", pos);
        });
        return new VatSpecimenSnapshot(
                h.specimenId(),
                pos.asLong(),
                identity == null ? "Unknown" : identity.toString(),
                name,
                (runtime == null ? h.lifeStage() : runtime.stage()) == LifeStage.ADULT,
                status,
                Math.clamp((yield - .5) / 1.5, 0, 1),
                yield,
                valid ? vat.frequencyMultiplier(pos) : .5,
                identity == null ? .5 : vat.medium().frequency(identity),
                runtime == null ? "None" : runtime.feedSource(),
                valid ? vat.processEngine().feedingStatus(pos) : "Feeding data unavailable",
                processes,
                actors,
                net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.appearance(h.specimenState())
                        .toString());
    }

    private static void addActor(MultiblockVat vat, List<Actor> result, BlockPos pos, String use, BlockPos head) {
        if (!(vat.getWorld().getBlockEntity(pos) instanceof AbstractVatEntity part)) return;
        var stack = part instanceof VatToolPortEntity tool ? tool.heldItem() : ItemStack.EMPTY;
        long cooldown = part instanceof VatToolPortEntity tool && tool.interactionStart() != Long.MIN_VALUE
                ? Math.max(
                        0,
                        VatToolPortEntity.INTERACTION_TICKS - (vat.getWorld().getGameTime() - tool.interactionStart()))
                : 0;
        String status = part.redstoneInhibited()
                ? "Redstone inhibited"
                : !vat.processingEnabled()
                        ? "Paused"
                        : vat.processEngine().actorStatus(head, use).values().stream()
                                .map(r -> reason(r.reason()))
                                .distinct()
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Assigned");
        result.add(new Actor(
                label(part.getPartType().name()),
                pos.asLong(),
                label(use),
                stack.isEmpty()
                        ? ""
                        : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.isEmpty() ? "" : stack.getHoverName().getString(),
                status,
                cooldown));
    }

    public static String label(String value) {
        return value.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    public static String reason(ProcessResult.Reason reason) {
        return switch (reason) {
            case IDLE -> "waiting";
            case RUNNING -> "running";
            case READY -> "ready";
            case WAITING_INPUT -> "waiting for input";
            case WAITING_FLUID -> "waiting for fluid";
            case WAITING_XP -> "waiting for experience";
            case MISSING_ACTOR -> "no compatible actor";
            case MISSING_TOOL -> "waiting for a tool";
            case OUTPUT_BLOCKED -> "output blocked";
            case BACKPRESSURE -> "backpressure";
            case SPECIMEN_INVALID -> "specimen unavailable";
            case CONDITION_FAILED -> "waiting for biological conditions";
            case EXCLUSIVE_USE -> "waiting for shared actor";
            case COMMITTED -> "action complete";
        };
    }
}
