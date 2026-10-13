package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Resource-bound cadence progress, followed by a detached plan and a reversible commit. */
public abstract class EnvironmentalToolExecutor implements ToolInteractionExecutor {
    protected abstract EntityItemInteractionExecutor.Plan plan(
            ProcessContext context, LivingEntity specimen, InteractionLevel level, ItemStack held);

    protected boolean ready(LivingEntity specimen) {
        return true;
    }

    @Override
    public void validateDefinition(PassiveMobDefinition owner, ToolInteractionDefinition definition) {
        if (definition.duration_ticks() < 1)
            throw new IllegalArgumentException(
                    "Environmental interaction requires positive duration_ticks: " + definition.id());
    }

    @Override
    public ProcessResult execute(ProcessContext c, ProcessState state, ToolInteractionDefinition d) {
        for (var route : c.routes(d.actor_use())) {
            var tool = c.tool(route);
            if (tool == null
                    || !tool.available()
                    || !d.accepts(tool.heldItem())
                    || !supports(c.level(), c.identity(), tool.heldItem())) continue;
            if (!tool.results().isEmpty()) return ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
            var before = c.assembly().head().specimenState();
            var level = new InteractionLevel(c.level());
            var specimen = (LivingEntity)
                    BuiltInRegistries.ENTITY_TYPE.get(c.identity()).create(level);
            if (specimen == null) return ProcessResult.of(ProcessResult.Reason.SPECIMEN_INVALID);
            specimen.setUUID(c.assembly().head().specimenId());
            specimen.setYRot(c.assembly().head().getSpecimenYaw());
            specimen.setPos(c.assembly().renderPosition());
            CapturedSpecimen.apply(specimen, before);
            if (!ready(specimen)) return ProcessResult.of(ProcessResult.Reason.CONDITION_FAILED);
            var held = tool.heldItem();
            var binding = new CompoundTag();
            binding.putUUID("specimen", c.assembly().head().specimenId());
            binding.putLong("actor", route.actor().asLong());
            binding.put("specimenState", before.copy());
            binding.put("input", held.save(c.level().registryAccess()));
            int previous = state.phase == ProcessState.Phase.RUNNING
                            && state.data.getCompound("environmentBinding").equals(binding)
                    ? state.elapsed
                    : 0;
            int elapsed = (int) Math.min(d.duration_ticks(), (long) previous + VatProcessEngine.CADENCE);
            var next = state.data.copy();
            next.put("environmentBinding", binding);
            if (elapsed < d.duration_ticks()) {
                boolean committed = new ProcessTransaction()
                        .add(reservation(ProcessTransaction.tool(tool, held)))
                        .add(reservation(EntityItemInteractionExecutor.specimenStep(
                                c.assembly().head(), before, before)))
                        .add(stateStep(state, ProcessState.Phase.RUNNING, elapsed, next))
                        .commit();
                return new ProcessResult(ProcessResult.Reason.RUNNING, committed);
            }
            var plan = plan(c, specimen, level, held.copy());
            if (plan == null || level.rejected) return ProcessResult.of(ProcessResult.Reason.CONDITION_FAILED);
            var yield = c.yieldPlan();
            var drops = yield.items(d.id(), plan.drops());
            next.remove("environmentBinding");
            var transaction = new ProcessTransaction()
                    .add(ProcessOutput.buffered(tool.results(), drops))
                    .add(ProcessTransaction.tool(tool, plan.held()))
                    .add(EntityItemInteractionExecutor.specimenStep(c.assembly().head(), before, plan.specimen()))
                    .add(yield.step())
                    .add(stateStep(state, ProcessState.Phase.IDLE, 0, next));
            if (!transaction.commit()) return ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
            c.controller().toolInteracted(tool, c.head());
            return ProcessResult.committed(true);
        }
        return ProcessResult.of(ProcessResult.Reason.MISSING_TOOL);
    }

    private static ProcessTransaction.Step stateStep(
            ProcessState state, ProcessState.Phase phase, int elapsed, CompoundTag data) {
        var before = state.data.copy();
        var oldPhase = state.phase;
        int oldElapsed = state.elapsed;
        var after = data.copy();
        return new ProcessTransaction.Step(
                state,
                () -> state.phase == oldPhase && state.elapsed == oldElapsed && state.data.equals(before),
                () -> {
                    state.phase = phase;
                    state.elapsed = elapsed;
                    state.data = after.copy();
                    return true;
                },
                () -> {
                    state.phase = oldPhase;
                    state.elapsed = oldElapsed;
                    state.data = before.copy();
                });
    }

    private static ProcessTransaction.Step reservation(ProcessTransaction.Step step) {
        return new ProcessTransaction.Step(step.resource(), step.valid(), () -> true, () -> {});
    }
}
