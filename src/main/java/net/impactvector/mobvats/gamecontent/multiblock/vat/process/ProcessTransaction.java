package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Small composable reversible steps for private controller-owned resources, server thread only.
 * External handlers/capabilities are intentionally unsupported: simulated external acceptance
 * cannot provide this rollback guarantee. No observer/gameplay ticks interleave a commit. */
public final class ProcessTransaction {
    public record Step(Object resource, BooleanSupplier valid, BooleanSupplier apply, Runnable undo) {}

    private final List<Step> steps = new ArrayList<>();
    private final Set<Object> resources = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean completed;

    public ProcessTransaction add(Step step) {
        if (completed) throw new IllegalStateException("Completed transaction");
        if (!resources.add(step.resource()))
            throw new IllegalArgumentException("Combine changes to the same resource in one step");
        steps.add(step);
        return this;
    }

    public boolean commit() {
        if (completed) return false;
        completed = true;
        for (var step : steps) if (!step.valid().getAsBoolean()) return false;
        int applied = 0;
        try {
            for (var step : steps) {
                ++applied;
                if (!step.apply().getAsBoolean()) {
                    rollback(applied);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            rollback(applied);
            throw exception;
        }
    }

    private void rollback(int count) {
        for (int i = count - 1; i >= 0; i--) steps.get(i).undo().run();
    }

    public static Step item(ItemStackHandler handler, ItemStack after) {
        var before = handler.getStackInSlot(0).copy();
        var result = after.copy();
        return new Step(
                handler,
                () -> ItemStack.matches(before, handler.getStackInSlot(0))
                        && result.getCount()
                                <= Math.min(handler.getSlotLimit(0), result.isEmpty() ? 64 : result.getMaxStackSize()),
                () -> {
                    handler.setStackInSlot(0, result.copy());
                    return true;
                },
                () -> handler.setStackInSlot(0, before.copy()));
    }

    public static Step fluid(FluidTank tank, FluidStack after) {
        var before = tank.getFluid().copy();
        var result = after.copy();
        return new Step(
                tank,
                () -> FluidStack.matches(before, tank.getFluid())
                        && result.getAmount() <= tank.getCapacity()
                        && (result.isEmpty() || tank.isFluidValid(0, result)),
                () -> {
                    tank.setFluid(result.copy());
                    return true;
                },
                () -> tank.setFluid(before.copy()));
    }
    /** Compatibility adapter for old in-code operations; the reversible resource is now a physical tank. */
    public static Step experienceMb(VatProcessEngine engine, long after) {
        long before = engine.experienceMb();
        if (after < 0) throw new IllegalArgumentException("XP");
        var request = new FluidStack(
                net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get(),
                Math.toIntExact(Math.abs(after - before)));
        var tanks = engine.controller().storage().clusters().stream()
                .map(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.Cluster::tank)
                .toList();
        var tank = net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.select(tanks, request);
        if (tank == null) return new Step(engine, () -> false, () -> false, () -> {});
        if (after >= before) {
            var step = ProcessOutput.fluid(tank, request);
            return step == null ? new Step(engine, () -> false, () -> false, () -> {}) : step;
        }
        var step = new ProcessInput.Liquid(request.getFluid(), request.getAmount()).plan(tank);
        return step == null ? new Step(engine, () -> false, () -> false, () -> {}) : step;
    }

    public static Step tool(
            net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatToolPortEntity tool, ItemStack after) {
        var before = tool.heldItem().copy();
        var result = after.copy();
        return new Step(
                tool,
                () -> !tool.isRemoved() && ItemStack.matches(before, tool.heldItem()),
                () -> {
                    tool.setHeldItem(result.copy());
                    return true;
                },
                () -> tool.setHeldItem(before.copy()));
    }

    public static Step lifecycle(SpecimenProcessState specimen, LifeStage stage, int progress) {
        var before = specimen.stage();
        var growth = specimen.growth();
        var phase = growth.phase;
        int elapsed = growth.elapsed;
        return new Step(
                specimen,
                () -> specimen.stage() == before && growth.phase == phase && growth.elapsed == elapsed,
                () -> {
                    specimen.stage(stage);
                    growth.phase = stage == LifeStage.JUVENILE ? ProcessState.Phase.RUNNING : ProcessState.Phase.IDLE;
                    growth.elapsed = progress;
                    return true;
                },
                () -> {
                    specimen.stage(before);
                    growth.phase = phase;
                    growth.elapsed = elapsed;
                });
    }

    public static Step state(ProcessState state, ProcessState.Phase phase, int elapsed) {
        var before = state.phase;
        int ticks = state.elapsed;
        return new Step(
                state,
                () -> state.phase == before && state.elapsed == ticks,
                () -> {
                    state.phase = phase;
                    state.elapsed = elapsed;
                    return true;
                },
                () -> {
                    state.phase = before;
                    state.elapsed = ticks;
                });
    }
}
