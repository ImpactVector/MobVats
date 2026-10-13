package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import java.util.*;
import java.util.function.BiPredicate;
import net.impactvector.mobvats.gamecontent.fluid.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.MachineMetrics;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.*;

/** Generic recipe executor over physical buffers and endpoint service tanks. Controller NBT holds only legacy escrow. */
public final class MixerState {
    private final Runnable changed;
    private boolean committing;

    private void contentsChanged() {
        if (!committing && changed != null) changed.run();
    }

    private final ItemStackHandler items = new ItemStackHandler(4) {
        public boolean isItemValid(int slot, ItemStack stack) {
            return MixerRecipeRegistry.acceptsItem(stack);
        }

        protected void onContentsChanged(int slot) {
            contentsChanged();
        }
    };
    private final ItemStackHandler producedItems = new ItemStackHandler(4) {
        protected void onContentsChanged(int slot) {
            contentsChanged();
        }
    };
    // Unbound instances are isolated process fixtures/client views. Live server controllers bind immediately.
    private final List<FluidTank> escrow = new ArrayList<>();
    private final Set<net.minecraft.resources.ResourceLocation> restoredFluids = new HashSet<>();

    public boolean outputFluid(FluidStack stack) {
        return MixerRecipeRegistry.outputFluid(stack)
                || restoredFluids.contains(
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid()));
    }

    private PhysicalFluidStorage storage;
    private BlockPos endpoint = BlockPos.ZERO;
    private long legacyXpMigration;
    private String recipe = "", blocked = "Waiting for recipe inputs";
    private int progress;
    private MixerRecipeDefinition activeDefinition;
    private String savedSignature = "";
    private final MachineMetrics metrics = new MachineMetrics();

    public MixerState(Runnable changed) {
        this.changed = changed;
    }

    public void bind(PhysicalFluidStorage storage, BlockPos endpoint) {
        this.storage = storage;
        this.endpoint = endpoint.immutable();
        migrate();
    }

    private FluidTank isolated(FluidStack stack) {
        for (var t : escrow) if (!t.isEmpty() && FluidStack.isSameFluidSameComponents(t.getFluid(), stack)) return t;
        var tank = new FluidTank(SlurryBalance.CAPACITY) {
            protected void onContentsChanged() {
                contentsChanged();
            }
        };
        escrow.add(tank);
        return tank;
    }

    private List<FluidTank> tanks() {
        return storage == null ? List.copyOf(escrow) : storage.connected(endpoint);
    }
    /** Read-only physical identities for presentation; never blends separate buffers. */
    public List<FluidStack> presentationFluids() {
        return tanks().stream()
                .filter(t -> !t.isEmpty())
                .map(t -> t.getFluid().copy())
                .toList();
    }
    /** Read-only presentation of the active or next executable recipe. Planning must not change status. */
    public FluidStack workingFluid() {
        String previousBlocked = blocked;
        try {
            var selected = MixerRecipeRegistry.recipe(recipe);
            if (selected != null) {
                var p = plan(selected);
                return p == null
                        ? net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation.select(
                                selected, presentationFluids())
                        : net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation.select(
                                selected, List.of(p.strategy()), presentationFluids());
            }
            for (var definition : MixerRecipeRegistry.recipes()) {
                var p = plan(definition);
                if (p != null)
                    return net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation.select(
                            definition, List.of(p.strategy()), presentationFluids());
            }
            return net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation.select(
                    null, presentationFluids());
        } finally {
            blocked = previousBlocked;
        }
    }

    private void migrate() {
        if (storage == null) return;
        boolean movedAny = false;
        for (var t : escrow)
            if (!t.isEmpty()) {
                var stack = t.getFluid().copy();
                int moved = PhysicalFluidStorage.handler(storage.bulkConnected(endpoint), true, s -> true)
                        .fill(stack, IFluidHandler.FluidAction.EXECUTE);
                t.drain(moved, IFluidHandler.FluidAction.EXECUTE);
                movedAny |= moved > 0;
                if (!t.isEmpty() && outputFluid(t.getFluid())) {
                    moved = PhysicalFluidStorage.handler(storage.outputTanks(endpoint, t.getFluid()), true, s -> true)
                            .fill(t.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                    t.drain(moved, IFluidHandler.FluidAction.EXECUTE);
                    movedAny |= moved > 0;
                }
            }
        if (legacyXpMigration > 0) {
            int ratio = ExperienceFluid.ratio();
            var request = ExperienceFluid.stack(Math.min(legacyXpMigration, Integer.MAX_VALUE / ratio));
            var target = PhysicalFluidStorage.handler(storage.bulkConnected(endpoint), true, s -> true);
            int accepted = target.fill(request, IFluidHandler.FluidAction.SIMULATE) / ratio * ratio;
            if (accepted > 0) {
                target.fill(request.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                legacyXpMigration -= accepted / ratio;
                movedAny = true;
            }
        }
        if (movedAny) changed.run();
    }
    /** Compatibility totals for old callers/UI only; execution never addresses resources by index. */
    private Fluid legacyFluid(int i) {
        return switch (i) {
            case 0 -> Fluids.WATER;
            case 1 -> ProductFluids.WASTE.get();
            case 2 -> ProductFluids.CULTURE_SLURRY.get();
            case 3 -> ProductFluids.FEEDING_SLURRY.get();
            case 4 -> ProductFluids.EXPERIENCE.get();
            default -> throw new IndexOutOfBoundsException(i);
        };
    }

    public int amount(int i) {
        return tanks().stream()
                .filter(t -> t.getFluid().is(legacyFluid(i)))
                .mapToInt(FluidTank::getFluidAmount)
                .sum();
    }

    public int experienceMb() {
        return amount(4);
    }

    public int plantCount() {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) n += items.getStackInSlot(i).getCount();
        return n;
    }

    public int progress() {
        return progress;
    }

    public String recipe() {
        return recipe;
    }

    public String blocked() {
        return blocked;
    }

    public MachineMetrics metrics() {
        return metrics;
    }

    public boolean depositExperienceMb(long amountMb) {
        if (amountMb <= 0 || amountMb > Integer.MAX_VALUE) return false;
        var request = new FluidStack(ProductFluids.EXPERIENCE.get(), (int) amountMb);
        var handler = fluids(true);
        if (handler.fill(request, IFluidHandler.FluidAction.SIMULATE) != request.getAmount()) return false;
        return handler.fill(request, IFluidHandler.FluidAction.EXECUTE) == request.getAmount();
    }

    public IItemHandler itemInput() {
        return itemPort(true, s -> true);
    }

    public IItemHandler itemPort(boolean input, java.util.function.Predicate<ItemStack> filter) {
        var inventory = input ? items : producedItems;
        return new IItemHandler() {
            public int getSlots() {
                return inventory.getSlots();
            }

            public ItemStack getStackInSlot(int slot) {
                var s = inventory.getStackInSlot(slot);
                return filter.test(s) ? s.copy() : ItemStack.EMPTY;
            }

            public ItemStack insertItem(int slot, ItemStack s, boolean simulate) {
                return input && filter.test(s) ? items.insertItem(slot, s, simulate) : s;
            }

            public ItemStack extractItem(int slot, int n, boolean simulate) {
                return !input && filter.test(inventory.getStackInSlot(slot))
                        ? inventory.extractItem(slot, n, simulate)
                        : ItemStack.EMPTY;
            }

            public int getSlotLimit(int slot) {
                return inventory.getSlotLimit(slot);
            }

            public boolean isItemValid(int slot, ItemStack s) {
                return input && filter.test(s) && items.isItemValid(slot, s);
            }
        };
    }

    public IFluidHandler fluids(boolean input) {
        return new IFluidHandler() {
            private boolean accepted(FluidStack s) {
                return input ? MixerRecipeRegistry.acceptsFluid(s) : outputFluid(s);
            }

            private IFluidHandler current() {
                return PhysicalFluidStorage.handler(tanks(), input, this::accepted);
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
                return input && current().isFluidValid(i, s);
            }

            public int fill(FluidStack s, FluidAction a) {
                if (!input || !accepted(s)) return 0;
                if (storage == null) {
                    var existing = escrow.stream()
                            .filter(t -> !t.isEmpty() && FluidStack.isSameFluidSameComponents(t.getFluid(), s))
                            .findFirst()
                            .orElse(null);
                    if (existing != null && existing.getFluidAmount() >= existing.getCapacity()) return 0;
                    if (existing == null && a.simulate()) return Math.min(s.getAmount(), SlurryBalance.CAPACITY);
                    return (existing == null ? isolated(s) : existing).fill(s, a);
                }
                return PhysicalFluidStorage.handler(storage.bulkConnected(endpoint), true, this::accepted)
                        .fill(s, a);
            }

            public FluidStack drain(FluidStack s, FluidAction a) {
                return current().drain(s, a);
            }

            public FluidStack drain(int n, FluidAction a) {
                return current().drain(n, a);
            }
        };
    }
    /** Bipartite integral allocation also handles overlapping broad/tag and exact ingredients. */
    private static int[][] allocate(int[] available, int[] required, BiPredicate<Integer, Integer> matches) {
        int resources = available.length,
                inputs = required.length,
                source = resources + inputs,
                sink = source + 1,
                n = sink + 1;
        var residual = new long[n][n];
        for (int r = 0; r < resources; r++) {
            residual[source][r] = available[r];
            for (int i = 0; i < inputs; i++) if (matches.test(r, i)) residual[r][resources + i] = Integer.MAX_VALUE;
        }
        long target = 0;
        for (int i = 0; i < inputs; i++) {
            residual[resources + i][sink] = required[i];
            target += required[i];
        }
        long flow = 0;
        while (flow < target) {
            var parent = new int[n];
            Arrays.fill(parent, -1);
            parent[source] = source;
            var q = new ArrayDeque<Integer>();
            q.add(source);
            while (!q.isEmpty() && parent[sink] < 0) {
                int v = q.removeFirst();
                for (int j = 0; j < n; j++)
                    if (parent[j] < 0 && residual[v][j] > 0) {
                        parent[j] = v;
                        q.add(j);
                    }
            }
            if (parent[sink] < 0) return null;
            long used = target - flow;
            for (int v = sink; v != source; v = parent[v]) used = Math.min(used, residual[parent[v]][v]);
            for (int v = sink; v != source; v = parent[v]) {
                residual[parent[v]][v] -= used;
                residual[v][parent[v]] += used;
            }
            flow += used;
        }
        var result = new int[resources][inputs];
        for (int r = 0; r < resources; r++)
            for (int i = 0; i < inputs; i++) result[r][i] = (int) residual[resources + i][r];
        return result;
    }

    private record Plan(
            MixerRecipeDefinition definition,
            MixerRecipeDefinition.Strategy strategy,
            ResourceReservation resources,
            List<FluidTank> newTanks,
            List<FluidStack> outputs,
            int itemCount,
            PhysicalFluidStorage.OutputReservation routes) {}

    private static int[][] allocateFluids(
            MixerRecipeDefinition definition, MixerRecipeDefinition.Strategy strategy, List<FluidStack> stacks) {
        var bundle = strategy.bundle(definition);
        var available = stacks.stream().mapToInt(FluidStack::getAmount).toArray();
        var required = bundle.fluids().stream()
                .mapToInt(ResourceBundle.FluidDemand::amount)
                .toArray();
        var sources = definition.fluidOutputs().stream()
                .filter(o -> o.transform() != null)
                .map(o -> o.transform().sourceInput())
                .distinct()
                .sorted()
                .toList();
        return allocateSources(stacks, bundle, available, required, sources, 0, new HashMap<>());
    }

    private static int[][] allocateSources(
            List<FluidStack> stacks,
            ResourceBundle bundle,
            int[] available,
            int[] required,
            List<Integer> sources,
            int index,
            Map<Integer, FluidStack> identities) {
        if (index == sources.size())
            return allocate(
                    available,
                    required,
                    (r, i) -> bundle.fluids().get(i).selector().test(stacks.get(r))
                            && (!identities.containsKey(i)
                                    || FluidStack.isSameFluidSameComponents(identities.get(i), stacks.get(r))));
        int source = sources.get(index);
        var tried = new ArrayList<FluidStack>();
        for (var candidate : stacks) {
            if (!bundle.fluids().get(source).selector().test(candidate)
                    || tried.stream().anyMatch(s -> FluidStack.isSameFluidSameComponents(s, candidate))) continue;
            tried.add(candidate);
            identities.put(source, candidate);
            var result = allocateSources(stacks, bundle, available, required, sources, index + 1, identities);
            if (result != null) return result;
        }
        identities.remove(source);
        return null;
    }

    private Plan plan(MixerRecipeDefinition d) {
        for (var strategy : d.strategies())
            if (strategy.enabled()) {
                var plan = plan(d, strategy);
                if (plan != null) return plan;
            }
        return null;
    }

    private Plan plan(MixerRecipeDefinition d, MixerRecipeDefinition.Strategy strategy) {
        var physical = new ArrayList<>(tanks());
        var snapshot = physical.stream().map(t -> t.getFluid().copy()).toList();
        var fluidUse = allocateFluids(d, strategy, snapshot);
        if (fluidUse == null) {
            blocked = "Insufficient recipe fluid";
            return null;
        }
        var reservation = new ResourceReservation();
        var bundle = strategy.bundle(d);
        var itemStacks = new ArrayList<ItemStack>();
        for (int i = 0; i < items.getSlots(); i++)
            itemStacks.add(reservation.item(items, i).copy());
        var itemUse = allocate(
                itemStacks.stream().mapToInt(ItemStack::getCount).toArray(),
                bundle.items().stream()
                        .mapToInt(ResourceBundle.ItemDemand::count)
                        .toArray(),
                (r, i) -> bundle.items().get(i).selector().test(itemStacks.get(r)));
        if (itemUse == null) {
            blocked = "Insufficient recipe item";
            return null;
        }
        var consumed = new ArrayList<List<FluidStack>>();
        for (int i = 0; i < strategy.fluids().size(); i++) {
            var stacks = new ArrayList<FluidStack>();
            for (int r = 0; r < physical.size(); r++)
                if (fluidUse[r][i] > 0) stacks.add(snapshot.get(r).copyWithAmount(fluidUse[r][i]));
            consumed.add(stacks);
        }
        var outputs = new ArrayList<FluidStack>();
        try {
            for (var output : d.fluidOutputs()) outputs.add(d.result(strategy, output, consumed));
        } catch (IllegalArgumentException | ArithmeticException e) {
            blocked = e.getMessage();
            return null;
        }
        for (int r = 0; r < physical.size(); r++) {
            var s = reservation.fluid(physical.get(r)).copy();
            s.shrink(Arrays.stream(fluidUse[r]).sum());
            reservation.fluid(physical.get(r), s);
        }
        int itemCount = 0;
        for (int i = 0; i < itemStacks.size(); i++) {
            int count = Arrays.stream(itemUse[i]).sum();
            itemCount = Math.addExact(itemCount, count);
            var s = itemStacks.get(i).copy();
            s.shrink(count);
            reservation.item(items, i, s);
        }
        // Item production has bounded private escrow, separate from input inventory.
        for (var output : d.itemOutputs()) {
            var stack = output.stack();
            int remaining = stack.getCount();
            for (int i = 0; i < producedItems.getSlots() && remaining > 0; i++) {
                var existing = reservation.item(producedItems, i);
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                int moved = Math.min(
                        remaining,
                        Math.max(
                                0,
                                Math.min(producedItems.getSlotLimit(i), stack.getMaxStackSize())
                                        - existing.getCount()));
                if (moved > 0) {
                    reservation.item(producedItems, i, stack.copyWithCount(existing.getCount() + moved));
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                blocked = "Recipe item output blocked";
                return null;
            }
        }
        var newTanks = new ArrayList<FluidTank>();
        var routes = storage == null ? null : storage.prepareOutputs(endpoint);
        for (var output : outputs) {
            List<FluidTank> destinations;
            if (storage == null) {
                var target = escrow.stream()
                        .filter(t -> !t.isEmpty() && FluidStack.isSameFluidSameComponents(t.getFluid(), output))
                        .findFirst()
                        .orElse(null);
                if (target == null)
                    target = newTanks.stream()
                            .filter(t -> FluidStack.isSameFluidSameComponents(reservation.fluid(t), output))
                            .findFirst()
                            .orElse(null);
                if (target == null) {
                    target = new FluidTank(SlurryBalance.CAPACITY) {
                        protected void onContentsChanged() {
                            contentsChanged();
                        }
                    };
                    newTanks.add(target);
                }
                destinations = List.of(target);
            } else destinations = routes.tanks(output);
            int remaining = output.getAmount();
            for (var tank : destinations) {
                var existing = reservation.fluid(tank);
                if (!tank.isFluidValid(output)
                        || !existing.isEmpty() && !FluidStack.isSameFluidSameComponents(existing, output)) continue;
                int used = Math.min(remaining, Math.max(0, tank.getCapacity() - existing.getAmount()));
                if (used > 0) {
                    reservation.fluid(tank, output.copyWithAmount(existing.getAmount() + used));
                    remaining -= used;
                }
                if (remaining == 0) break;
            }
            if (remaining > 0 && (routes == null || !routes.dispose(output.copyWithAmount(remaining)))) {
                blocked = "Recipe fluid output blocked";
                return null;
            }
        }
        return new Plan(d, strategy, reservation, List.copyOf(newTanks), List.copyOf(outputs), itemCount, routes);
    }
    /** Single-use local reservation, invalidated by changed inputs, outputs, topology or registry snapshot. */
    public final class Reservation {
        private final Plan plan;
        private final MixerRecipeRegistry.Loaded registry;
        private final PhysicalFluidStorage boundStorage;
        private final BlockPos boundEndpoint;
        private final List<FluidTank> connected;
        private final List<List<FluidTank>> destinations;
        private boolean completed;

        private Reservation(Plan plan) {
            this.plan = plan;
            registry = MixerRecipeRegistry.snapshot();
            boundStorage = storage;
            boundEndpoint = endpoint;
            connected = tanks();
            destinations = storage == null
                    ? List.of()
                    : plan.outputs().stream()
                            .map(o -> plan.routes().tanks(o).stream()
                                    .filter(t -> t.isFluidValid(o))
                                    .toList())
                            .toList();
        }

        public String strategy() {
            return plan.strategy().id();
        }

        public boolean commit() {
            if (completed) return false;
            completed = true;
            if (committing
                    || registry != MixerRecipeRegistry.snapshot()
                    || boundStorage != storage
                    || !boundEndpoint.equals(endpoint)
                    || !connected.equals(tanks())) {
                blocked = "Reservation changed";
                return false;
            }
            if (storage != null) {
                if (!plan.routes().valid()) {
                    blocked = "Output routing changed";
                    return false;
                }
                for (int i = 0; i < plan.outputs().size(); i++) {
                    var output = plan.outputs().get(i);
                    if (!destinations
                            .get(i)
                            .equals(plan.routes().tanks(output).stream()
                                    .filter(t -> t.isFluidValid(output))
                                    .toList())) {
                        blocked = "Output filter changed";
                        return false;
                    }
                }
            }
            committing = true;
            try {
                if (!plan.resources().commit()) {
                    blocked = "Storage changed";
                    return false;
                }
                escrow.addAll(plan.newTanks());
                if (plan.routes() != null) plan.routes().publish();
            } finally {
                committing = false;
            }
            recordCommit(plan);
            blocked = "";
            changed.run();
            return true;
        }
    }

    public Optional<Reservation> prepare(MixerRecipeDefinition definition) {
        var p = plan(definition);
        return p == null ? Optional.empty() : Optional.of(new Reservation(p));
    }

    public boolean canCommit(MixerRecipeDefinition d) {
        return plan(d) != null;
    }

    public boolean commit(MixerRecipeDefinition d) {
        return prepare(d).map(Reservation::commit).orElse(false);
    }

    private void recordCommit(Plan p) {
        var d = p.definition();
        var strategy = p.strategy();
        for (var input : strategy.fluids()) {
            metrics.record("fluidConsumed", input.amount());
            if (input.identity()
                    .equals(net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.canonicalSelector(
                            net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Kind.FLUID,
                            "minecraft:water"))) metrics.record("waterConsumed", input.amount());
            if (input.identity()
                    .equals(net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.canonicalSelector(
                            net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Kind.FLUID,
                            "mobvats:waste"))) metrics.record("wasteConsumed", input.amount());
            if (input.identity()
                    .equals(net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.canonicalSelector(
                            net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Kind.FLUID,
                            "mobvats:experience"))) metrics.record("experienceConsumedMb", input.amount());
        }
        for (int i = 0; i < d.fluidOutputs().size(); i++)
            if (d.fluidOutputs().get(i).transform() != null) {
                metrics.record(
                        "cultureFilteredMb",
                        strategy.fluids()
                                .get(d.fluidOutputs().get(i).transform().sourceInput())
                                .amount());
                if (!net.impactvector.mobvats.gamecontent.fluid.compound.CompoundFluids.isCompound(
                        p.outputs().get(i)))
                    restoredFluids.add(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(
                            p.outputs().get(i).getFluid()));
            }
        metrics.record("plantsConsumed", p.itemCount());
        metrics.record("itemsConsumed", p.itemCount());
        metrics.record(
                d.id() + "Produced",
                p.outputs().stream().mapToLong(FluidStack::getAmount).sum()
                        + d.itemOutputs().stream()
                                .mapToLong(MixerRecipeDefinition.ItemOutput::amount)
                                .sum());
    }

    public void activeTick() {
        metrics.tick();
    }

    public void cadence() {
        var previousRecipe = recipe;
        var previousBlocked = blocked;
        int previousProgress = progress;
        boolean committed = false;
        try {
            committed = advanceCadence();
        } finally {
            if (committed
                    || !recipe.equals(previousRecipe)
                    || !blocked.equals(previousBlocked)
                    || progress != previousProgress) changed.run();
        }
    }

    private boolean advanceCadence() {
        migrate();
        var selected = MixerRecipeRegistry.recipe(recipe);
        if (activeDefinition != null && activeDefinition != selected) {
            recipe = "";
            progress = 0;
            selected = null;
            activeDefinition = null;
        }
        if (selected == null) {
            recipe = "";
            progress = 0;
            selected = MixerRecipeRegistry.recipes().stream()
                    .filter(this::canCommit)
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                if (MixerRecipeRegistry.recipes().isEmpty()) blocked = "No loaded Mixer recipes";
                return false;
            }
            recipe = selected.id();
        }
        if (activeDefinition == null && !savedSignature.isEmpty() && !savedSignature.equals(selected.signature()))
            progress = 0;
        activeDefinition = selected;
        savedSignature = "";
        if (!canCommit(selected)) return false;
        blocked = "";
        progress = (int) Math.min(selected.batchTicks(), (long) progress + SlurryBalance.CADENCE);
        if (progress == selected.batchTicks() && commit(selected)) {
            recipe = "";
            progress = 0;
            activeDefinition = null;
            return true;
        }
        return false;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (var t : escrow) if (!t.isEmpty()) list.add(t.writeToNBT(registries, new CompoundTag()));
        tag.put("escrow", list);
        var restored = new ListTag();
        restoredFluids.stream()
                .sorted(java.util.Comparator.comparing(Object::toString))
                .forEach(id -> restored.add(StringTag.valueOf(id.toString())));
        tag.put("restoredFluids", restored);
        // Old keys remain readable to migration tooling; new readers use the generic escrow list only.
        for (int i = 0; i < 5; i++) {
            final int index = i;
            var t = escrow.stream()
                    .filter(v -> v.getFluid().is(legacyFluid(index)))
                    .findFirst()
                    .orElse(null);
            tag.put("tank" + i, t == null ? new CompoundTag() : t.writeToNBT(registries, new CompoundTag()));
        }
        tag.putLong("legacyXpMigration", legacyXpMigration);
        tag.put("items", items.serializeNBT(registries));
        tag.put("producedItems", producedItems.serializeNBT(registries));
        tag.putString("recipeSignature", activeDefinition == null ? savedSignature : activeDefinition.signature());
        tag.putString("recipe", recipe);
        tag.putInt("progress", progress);
        tag.putString("blocked", blocked);
        tag.put("metrics", metrics.save());
        return tag;
    }

    public CompoundTag presentation(HolderLookup.Provider registries) {
        var tag = save(registries);
        var list = new ListTag();
        for (var t : tanks()) if (!t.isEmpty()) list.add(t.writeToNBT(registries, new CompoundTag()));
        tag.put("escrow", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        escrow.clear();
        if (tag.contains("escrow")) {
            var list = tag.getList("escrow", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var tank = new FluidTank(SlurryBalance.CAPACITY);
                tank.readFromNBT(registries, list.getCompound(i));
                if (!tank.isEmpty()) escrow.add(tank);
            }
        } else
            for (int i = 0; i < 5; i++) {
                var tank = new FluidTank(SlurryBalance.CAPACITY);
                tank.readFromNBT(registries, tag.getCompound("tank" + i));
                if (!tank.isEmpty()) escrow.add(tank);
            }
        restoredFluids.clear();
        var restored = tag.getList("restoredFluids", Tag.TAG_STRING);
        for (int i = 0; i < restored.size(); i++) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(restored.getString(i));
            if (id != null) restoredFluids.add(id);
        }
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        else items.setSize(4);
        if (tag.contains("producedItems")) producedItems.deserializeNBT(registries, tag.getCompound("producedItems"));
        else producedItems.setSize(4);
        legacyXpMigration = Math.max(0, tag.getLong(tag.contains("legacyXpMigration") ? "legacyXpMigration" : "xp"));
        recipe = tag.getString("recipe");
        progress = Math.max(0, tag.getInt("progress"));
        blocked = tag.getString("blocked");
        metrics.load(tag.getCompound("metrics"));
        savedSignature = tag.getString("recipeSignature");
        activeDefinition = MixerRecipeRegistry.recipe(recipe);
        if (activeDefinition != null
                && !savedSignature.isEmpty()
                && !savedSignature.equals(activeDefinition.signature())) {
            recipe = "";
            progress = 0;
            activeDefinition = null;
        }
    }

    public void assimilate(MixerState source) {
        legacyXpMigration = Math.addExact(legacyXpMigration, source.legacyXpMigration);
        restoredFluids.addAll(source.restoredFluids);
        for (var t : source.escrow)
            if (!t.isEmpty()) {
                var target = isolated(t.getFluid());
                var s = t.getFluid().copy();
                s.setAmount(Math.addExact(s.getAmount(), target.getFluidAmount()));
                target.setFluid(s);
            }
        for (int i = 0; i < source.items.getSlots(); i++) {
            var remaining = source.items.getStackInSlot(i).copy();
            for (int slot = 0; slot < items.getSlots() && !remaining.isEmpty(); slot++)
                remaining = items.insertItem(slot, remaining, false);
            if (!remaining.isEmpty()) {
                var previous = new ArrayList<ItemStack>();
                for (int j = 0; j < items.getSlots(); j++)
                    previous.add(items.getStackInSlot(j).copy());
                items.setSize(previous.size() + 1);
                for (int j = 0; j < previous.size(); j++) items.setStackInSlot(j, previous.get(j));
                items.setStackInSlot(previous.size(), remaining);
            }
        }
        for (int i = 0; i < source.producedItems.getSlots(); i++) {
            var remaining = source.producedItems.getStackInSlot(i).copy();
            for (int slot = 0; slot < producedItems.getSlots() && !remaining.isEmpty(); slot++)
                remaining = producedItems.insertItem(slot, remaining, false);
            if (!remaining.isEmpty()) {
                int size = producedItems.getSlots();
                var previous = new ArrayList<ItemStack>();
                for (int j = 0; j < size; j++)
                    previous.add(producedItems.getStackInSlot(j).copy());
                producedItems.setSize(size + 1);
                for (int j = 0; j < size; j++) producedItems.setStackInSlot(j, previous.get(j));
                producedItems.setStackInSlot(size, remaining);
            }
        }
        if (recipe.isEmpty()) {
            recipe = source.recipe;
            progress = source.progress;
            activeDefinition = source.activeDefinition;
        }
        metrics.assimilate(source.metrics);
        changed.run();
    }

    public void clear() {
        escrow.clear();
        restoredFluids.clear();
        legacyXpMigration = 0;
        items.setSize(4);
        producedItems.setSize(4);
        recipe = "";
        progress = 0;
        activeDefinition = null;
        savedSignature = "";
        metrics.load(new CompoundTag());
    }

    public String status(boolean formed) {
        return status(formed, true);
    }

    public String status(boolean formed, boolean active) {
        return (formed ? (active ? "Mixer formed / active" : "Mixer formed / disabled") : "Mixer unformed / inactive")
                + "\nWater: " + amount(0) + " mB | WASTE: " + amount(1) + " mB | XP: " + experienceMb()
                + " mB | Plants: " + plantCount() + "\nRecipe: " + (recipe.isEmpty() ? "none" : recipe)
                + " | Progress: " + progress + " | " + blocked + "\nResources: "
                + tanks().stream()
                        .filter(t -> !t.isEmpty())
                        .map(t -> t.getFluid().getHoverName().getString() + " " + t.getFluidAmount() + " mB")
                        .toList()
                + (legacyXpMigration > 0 ? "\nLegacy XP migration pending: " + legacyXpMigration : "")
                + (storage == null ? "" : "\n" + String.join("\n", storage.diagnostics()));
    }
}
