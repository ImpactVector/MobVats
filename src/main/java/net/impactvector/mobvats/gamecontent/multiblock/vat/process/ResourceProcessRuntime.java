package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import static net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessResult.Reason.*;

import java.util.*;
import java.util.function.Predicate;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Compiled fixed transforms; aggregate shadow changes prevent double-spending shared inputs/outputs. */
public final class ResourceProcessRuntime {
    private record Input(String type, Predicate<ItemStack> items, Predicate<FluidStack> fluids, int quantity) {}

    private record Output(
            String type,
            ResourceLocation buffer,
            ItemStack item,
            FluidStack fluid,
            int quantity,
            boolean yieldScaled) {}

    private final PassiveMobDefinition.ResourceProcess definition;
    private final List<Input> inputs;
    private final List<Output> outputs;

    public ResourceProcessRuntime(PassiveMobDefinition.ResourceProcess d) {
        definition = d;
        inputs = d.inputs().stream()
                .map(i -> new Input(
                        i.type(),
                        i.type().equals("item") ? ResourceSelectors.items(i.items()) : null,
                        i.type().equals("fluid") ? ResourceSelectors.fluids(i.fluids()) : null,
                        i.type().equals("item") ? i.count() : i.amount()))
                .toList();
        outputs = d.outputs().stream()
                .map(o -> new Output(
                        o.type(),
                        o.buffer() == null ? null : ResourceLocation.parse(o.buffer()),
                        o.type().equals("item")
                                ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.item())), o.count())
                                : ItemStack.EMPTY,
                        o.type().equals("fluid")
                                ? new FluidStack(
                                        BuiltInRegistries.FLUID.get(ResourceLocation.parse(o.fluid())), o.amount())
                                : FluidStack.EMPTY,
                        o.amount(),
                        o.yield_scaled()))
                .toList();
    }

    public record Plan(ProcessTransaction transaction, ProcessResult.Reason problem, Map<String, Long> metrics) {
        public Plan(ProcessTransaction transaction, ProcessResult.Reason problem) {
            this(transaction, problem, Map.of());
        }
    }

    public Plan plan(ProcessContext c) {
        var yield = c.yieldPlan();
        var itemChanges = new LinkedHashMap<ItemStackHandler, ItemStack>();
        var fluidChanges = new LinkedHashMap<FluidTank, FluidStack>();
        var disposal = new LinkedHashSet<net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryEntity>();
        for (var in : inputs) {
            int remaining = in.quantity();
            switch (in.type()) {
                case "item" -> {
                    for (var port : c.inputsFor(definition.actor_use())) {
                        var handler = port.staging();
                        var stack = itemChanges.computeIfAbsent(
                                handler, h -> h.getStackInSlot(0).copy());
                        if (port.accepts(stack) && in.items().test(stack)) {
                            int used = Math.min(remaining, stack.getCount());
                            stack.shrink(used);
                            remaining -= used;
                        }
                        if (remaining == 0) break;
                    }
                }
                case "fluid" -> {
                    for (var tank : inputTanks(c)) {
                        var stack = fluidChanges.computeIfAbsent(
                                tank, t -> t.getFluid().copy());
                        if (in.fluids().test(stack)) {
                            int used = Math.min(remaining, stack.getAmount());
                            stack.shrink(used);
                            remaining -= used;
                        }
                        if (remaining == 0) break;
                    }
                }
                case "xp" -> {
                    for (var tank : inputTanks(c)) {
                        var stack = fluidChanges.computeIfAbsent(
                                tank, t -> t.getFluid().copy());
                        if (stack.is(net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get())) {
                            int used = Math.min(remaining, stack.getAmount());
                            stack.shrink(used);
                            remaining -= used;
                        }
                        if (remaining == 0) break;
                    }
                }
                default -> throw new IllegalStateException("Unvalidated input");
            }
            if (remaining != 0) return new Plan(null, WAITING_INPUT);
        }
        var metrics = new TreeMap<String, Long>();
        var drops = new ArrayList<ItemStack>();
        for (var out : outputs) {
            String metric = out.type().equals("xp")
                    ? "experienceProducedMb"
                    : definition.id() + "/" + out.type() + (out.buffer() == null ? "" : "/" + out.buffer());
            switch (out.type()) {
                case "item" -> {
                    int remaining = out.yieldScaled()
                            ? yield.items(definition.id(), List.of(out.item())).stream()
                                    .mapToInt(ItemStack::getCount)
                                    .sum()
                            : out.item().getCount();
                    metrics.merge(metric, (long) remaining, Long::sum);
                    while (remaining > 0) {
                        int n = Math.min(remaining, out.item().getMaxStackSize());
                        var stack = out.item().copy();
                        stack.setCount(n);
                        drops.add(stack);
                        remaining -= n;
                    }
                }
                case "fluid" -> {
                    var output = out.yieldScaled() ? yield.fluid(definition.id(), out.fluid()) : out.fluid();
                    metrics.merge(metric, (long) output.getAmount(), Long::sum);
                    if (!output.isEmpty() && !allocateOutput(c, output, fluidChanges, disposal))
                        return new Plan(null, OUTPUT_BLOCKED);
                }
                case "xp" -> {
                    int amountMb =
                            out.yieldScaled() ? yield.amount(definition.id() + "/xp", out.quantity()) : out.quantity();
                    metrics.merge(metric, (long) amountMb, Long::sum);
                    if (amountMb > 0
                            && !allocateOutput(
                                    c,
                                    new FluidStack(
                                            net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get(),
                                            amountMb),
                                    fluidChanges,
                                    disposal)) return new Plan(null, OUTPUT_BLOCKED);
                }
                default -> throw new IllegalStateException("Unvalidated output");
            }
        }
        var transaction = new ProcessTransaction().add(yield.step());
        if (!drops.isEmpty()) {
            var tool = c.routes(definition.actor_use()).stream()
                    .map(c::tool)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (tool != null) {
                if (!tool.results().isEmpty()) return new Plan(null, OUTPUT_BLOCKED);
                transaction.add(ProcessOutput.buffered(tool.results(), drops));
            } else {
                if (c.assembly().drain().redstoneInhibited()) return new Plan(null, OUTPUT_BLOCKED);
                var ports = c.routes(PassiveUses.DRAIN).stream()
                        .flatMap(r -> c.outputs(r).stream())
                        .distinct()
                        .toList();
                for (var drop : drops) {
                    var remaining = drop.copy();
                    for (var port : ports) {
                        if (!port.accepts(remaining)) continue;
                        var handler = port.staging();
                        var before = itemChanges.computeIfAbsent(
                                handler, h -> h.getStackInSlot(0).copy());
                        if (!before.isEmpty() && !ItemStack.isSameItemSameComponents(before, remaining)) continue;
                        int moved = Math.min(
                                remaining.getCount(),
                                Math.min(handler.getSlotLimit(0), remaining.getMaxStackSize()) - before.getCount());
                        if (moved > 0) {
                            var after = remaining.copyWithCount(before.getCount() + moved);
                            itemChanges.put(handler, after);
                            remaining.shrink(moved);
                        }
                        if (remaining.isEmpty()) break;
                    }
                    if (!remaining.isEmpty()) {
                        var sink = c
                                .controller()
                                .voids(
                                        c.assembly().drain().getBlockPos(),
                                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM)
                                .stream()
                                .filter(e -> e.accepts(remaining))
                                .findFirst()
                                .orElse(null);
                        if (sink == null) return new Plan(null, OUTPUT_BLOCKED);
                        disposal.add(sink);
                    }
                }
            }
        }
        itemChanges.forEach((handler, after) -> {
            if (!ItemStack.matches(handler.getStackInSlot(0), after))
                transaction.add(ProcessTransaction.item(handler, after));
        });
        for (var entry : fluidChanges.entrySet()) {
            var tank = entry.getKey();
            var after = entry.getValue();
            if (FluidStack.matches(tank.getFluid(), after)) continue;
            if (after.getAmount() > tank.getCapacity() || !after.isEmpty() && !tank.isFluidValid(after))
                return new Plan(null, OUTPUT_BLOCKED);
            transaction.add(ProcessTransaction.fluid(tank, after));
        }

        disposal.forEach(e -> transaction.add(e.disposal()));
        return new Plan(transaction, null, Map.copyOf(metrics));
    }

    private java.util.List<FluidTank> inputTanks(ProcessContext c) {
        var tanks = new LinkedHashSet<FluidTank>();
        String use = definition.actor_use().isEmpty() ? PassiveUses.DRAIN : definition.actor_use();
        for (var route : c.routes(use)) tanks.addAll(c.controller().storage().connected(route.actor()));
        return List.copyOf(tanks);
    }

    private boolean allocateOutput(
            ProcessContext c,
            FluidStack request,
            Map<FluidTank, FluidStack> shadows,
            Set<net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryEntity> disposal) {
        if (c.assembly().drain().redstoneInhibited()) return false;
        var tanks = c.controller().storage().outputTanks(c.assembly().drain().getBlockPos(), request);
        int remaining = request.getAmount();
        for (boolean empty : new boolean[] {false, true})
            for (var tank : tanks) {
                var before = shadows.getOrDefault(tank, tank.getFluid());
                if (before.isEmpty() != empty
                        || !before.isEmpty() && !FluidStack.isSameFluidSameComponents(before, request)
                        || !tank.isFluidValid(request)) continue;
                int moved = Math.min(remaining, Math.max(0, tank.getCapacity() - before.getAmount()));
                if (moved > 0) {
                    var after = request.copyWithAmount(before.getAmount() + moved);
                    shadows.put(tank, after);
                    remaining -= moved;
                }
                if (remaining == 0) return true;
            }
        var sink = c
                .controller()
                .voids(
                        c.assembly().drain().getBlockPos(),
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.FLUID)
                .stream()
                .filter(e -> e.accepts(request))
                .findFirst()
                .orElse(null);
        if (sink == null) return false;
        disposal.add(sink);
        return true;
    }

    public ProcessResult start(ProcessContext c, ProcessState state) {
        if (inputs.isEmpty()) {
            state.start();
            return ProcessResult.committed(true);
        }
        var plan = plan(c);
        if (plan.problem() != null) return ProcessResult.of(plan.problem());
        state.start();
        return ProcessResult.committed(true);
    }

    public ProcessResult finish(ProcessContext c, ProcessState state) {
        var plan = plan(c);
        if (plan.problem() != null) return ProcessResult.of(plan.problem());
        var transaction = plan.transaction().add(ProcessTransaction.state(state, ProcessState.Phase.IDLE, 0));
        if (!(definition.accelerated()
                ? Fouling.commit(transaction, c, definition.id(), definition.fouling_mb())
                : transaction.commit())) return ProcessResult.of(OUTPUT_BLOCKED);
        plan.metrics().forEach((key, amount) -> c.metrics().record(key, amount));
        return ProcessResult.committed(true);
    }
}
