package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import static net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessDefinition.*;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

/** Compiles loaded data into reusable engine requirements, timers and strategy operations. */
public final class PassiveProcessCompiler {
    public static final String GROWTH = "mobvats:growth",
            HARVEST = "mobvats:replacement",
            MILK = "mobvats:milk",
            WASTE = "mobvats:waste";

    public static MobProcessRegistry.Profile compile(PassiveMobDefinition d) {
        var definitions = new ArrayList<ProcessDefinition>();
        if (d.lifecycle())
            definitions.add(new ProcessDefinition(
                    "00_growth",
                    GROWTH,
                    Stage.TICK,
                    d.identity()::equals,
                    List.of(ProcessRequirement.stage(StageRequirement.JUVENILE)),
                    new Progress(d.juvenile_growth_ticks(), true, c -> c.multiplier()),
                    Backpressure.NONE,
                    null,
                    List.of(),
                    List.of(
                            (c, s) -> Fouling.complete(c, GROWTH),
                            ProcessEffect.setStage(LifeStage.ADULT),
                            ProcessEffect.resetGrowth())));
        int index = 0;
        for (var output : d.secondary_outputs()) {
            var bufferId = ProductBuffers.key(output.buffer());
            var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(output.fluid()));
            var requirements = new ArrayList<ProcessRequirement>();
            requirements.add(ProcessRequirement.stage(output.requires_stage()));
            requirements.add(
                    ProcessRequirement.actor(output.actor_use().isEmpty() ? PassiveUses.DRAIN : output.actor_use()));
            definitions.add(new ProcessDefinition(
                    "01_timer_" + index,
                    output.process(),
                    Stage.TICK,
                    d.identity()::equals,
                    requirements,
                    new Progress(output.interval_ticks(), true, c -> output.accelerated() ? c.multiplier() : 1),
                    Backpressure.NONE,
                    null,
                    List.of(),
                    List.of()));
            var ready = new ArrayList<>(requirements);
            ready.add(ProcessRequirement.phase(ProcessState.Phase.READY));
            definitions.add(new ProcessDefinition(
                    "10_output_" + index++,
                    output.process(),
                    Stage.CADENCE,
                    d.identity()::equals,
                    ready,
                    null,
                    Backpressure.OUTPUT_CAPACITY,
                    (c, s) -> {
                        var yield = c.yieldPlan();
                        var request = yield.fluid(output.process(), new FluidStack(fluid, output.amount()));
                        if (request.isEmpty())
                            return ProcessResult.committed(
                                    output.accelerated()
                                            ? Fouling.commit(
                                                    new ProcessTransaction().add(yield.step()),
                                                    c,
                                                    output.process(),
                                                    output.fouling_mb())
                                            : new ProcessTransaction()
                                                    .add(yield.step())
                                                    .commit());
                        var origin = output.actor_use().isEmpty()
                                        || output.actor_use().equals(PassiveUses.DRAIN)
                                ? c.assembly().drain().getBlockPos()
                                : c.routes(output.actor_use()).getFirst().actor();
                        if (output.container() != null) {
                            var input = BuiltInRegistries.ITEM.get(
                                    ResourceLocation.parse(output.container().input()));
                            var tool = c.routes(output.container().actor_use()).stream()
                                    .map(c::tool)
                                    .filter(t -> t != null
                                            && t.heldItem().is(input)
                                            && t.results().isEmpty())
                                    .findFirst()
                                    .orElse(null);
                            if (tool != null) origin = tool.getBlockPos();
                        }
                        var tank = c.controller().storage().selectOutput(origin, request);
                        if (tank == null) return ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
                        var step = ProcessOutput.fluid(tank, request);
                        if (step == null) return ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
                        var transaction = new ProcessTransaction().add(step).add(yield.step());
                        boolean committed = output.accelerated()
                                ? Fouling.commit(transaction, c, output.process(), output.fouling_mb())
                                : transaction.commit();
                        if (committed) c.metrics().record(output.metric(), request.getAmount());
                        return committed
                                ? ProcessResult.committed(true)
                                : ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
                    },
                    List.of(),
                    List.of(ProcessEffect.reset())));
        }
        if (!d.toolInteractions().isEmpty())
            definitions.add(action(
                    "40_acquire", d, List.of(), (c, s) -> operations(c, d).acquire()));
        if (d.lifecycle() && !d.harvest().blender().isEmpty()) {
            definitions.add(action(
                            "50_replace",
                            d,
                            List.of(ProcessRequirement.stage(StageRequirement.ADULT)),
                            (c, s) -> operations(c, d).replaceBlender(s))
                    .withExclusiveUse(d.harvest().actor_use()));
        }
        int interactionIndex = 0;
        for (var interaction : ToolInteractions.ordered(d)) {
            interaction.validate();
            var executor = ToolInteractionExecutorRegistry.require(ResourceLocation.parse(interaction.executor()));
            executor.validateDefinition(d, interaction);
            var stateId = executor.stateId(interaction);
            definitions.add(new ProcessDefinition(
                            String.format(java.util.Locale.ROOT, "55_interaction_%04d_", interactionIndex++)
                                    + interaction.id(),
                            stateId,
                            Stage.CADENCE,
                            d.identity()::equals,
                            List.of(ProcessRequirement.stage(interaction.requires_stage())),
                            null,
                            Backpressure.RETAIN_TOOL_RESULT,
                            (c, s) -> ToolInteractions.execute(c, s, interaction, executor),
                            List.of(),
                            List.of())
                    .withExclusiveUse(interaction.actor_use()));
        }
        for (var process : d.processes()) compileResource(definitions, d, process);
        return new MobProcessRegistry.Profile(definitions, d.bufferDefinitions());
    }

    private static void compileResource(
            List<ProcessDefinition> definitions, PassiveMobDefinition d, PassiveMobDefinition.ResourceProcess p) {
        var runtime = new ResourceProcessRuntime(p);
        var requirements = new ArrayList<ProcessRequirement>();
        requirements.add(ProcessRequirement.stage(p.requires_stage()));
        if (!p.actor_use().isEmpty()) requirements.add(ProcessRequirement.actor(p.actor_use()));
        var idle = new ArrayList<>(requirements);
        idle.add(ProcessRequirement.phase(ProcessState.Phase.IDLE));
        definitions.add(new ProcessDefinition(
                "20_start_" + p.id(),
                p.id(),
                Stage.CADENCE,
                d.identity()::equals,
                idle,
                null,
                Backpressure.OUTPUT_CAPACITY,
                runtime::start,
                List.of(),
                List.of()));
        definitions.add(new ProcessDefinition(
                "02_timer_" + p.id(),
                p.id(),
                Stage.TICK,
                d.identity()::equals,
                requirements,
                new Progress(p.duration_ticks(), false, c -> p.accelerated() ? c.multiplier() : 1),
                Backpressure.NONE,
                null,
                List.of(),
                List.of()));
        var ready = new ArrayList<>(requirements);
        ready.add(ProcessRequirement.phase(ProcessState.Phase.READY));
        definitions.add(new ProcessDefinition(
                "30_finish_" + p.id(),
                p.id(),
                Stage.CADENCE,
                d.identity()::equals,
                ready,
                null,
                Backpressure.OUTPUT_CAPACITY,
                runtime::finish,
                List.of(),
                List.of()));
    }

    private static PassiveOperations operations(ProcessContext c, PassiveMobDefinition d) {
        return c.strategy("passive", () -> new PassiveOperations(c, d));
    }

    private static ProcessDefinition action(
            String id, PassiveMobDefinition d, List<ProcessRequirement> req, Operation op) {
        return new ProcessDefinition(
                id,
                HARVEST,
                Stage.CADENCE,
                d.identity()::equals,
                req,
                null,
                Backpressure.NONE,
                op,
                List.of(),
                List.of());
    }

    private PassiveProcessCompiler() {}
}
