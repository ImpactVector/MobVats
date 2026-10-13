package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import com.google.gson.JsonParser;
import java.util.*;
import java.util.function.*;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Authored selectors survive matching, including tags and native custom ingredient forms. */
public record MixerRecipeDefinition(
        String id,
        int priority,
        List<Strategy> strategies,
        List<ItemOutput> itemOutputs,
        List<FluidOutput> fluidOutputs,
        int batchTicks) {
    public record Strategy(String id, int priority, boolean enabled, List<FluidInput> fluids, List<ItemInput> items) {
        public Strategy {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Strategy requires an id");
            fluids = List.copyOf(fluids);
            items = List.copyOf(items);
            if (fluids.isEmpty() && items.isEmpty()) throw new IllegalArgumentException("Strategy requires inputs");
        }

        public ResourceBundle bundle(MixerRecipeDefinition recipe) {
            var demands = new ArrayList<ResourceBundle.FluidDemand>();
            for (int i = 0; i < fluids.size(); i++) {
                final int index = i;
                demands.add(new ResourceBundle.FluidDemand(
                        s -> recipe.matchesInput(this, index, s), fluids.get(i).amount()));
            }
            return new ResourceBundle(
                    items.stream()
                            .map(i -> new ResourceBundle.ItemDemand(i.selector(), i.amount()))
                            .toList(),
                    demands);
        }
    }

    public record ItemOutput(net.minecraft.world.item.Item item, int amount) {
        public ItemOutput {
            if (item == net.minecraft.world.item.Items.AIR || amount <= 0)
                throw new IllegalArgumentException("Invalid item output");
        }

        public ItemStack stack() {
            return new ItemStack(item, amount);
        }
    }

    public record FluidOutput(
            Supplier<? extends Fluid> fluid, int amount, CompoundComposition composition, Transform transform) {
        public FluidOutput {
            if (amount <= 0) throw new IllegalArgumentException("Fluid output amount must be positive");
            if (composition != null && transform != null)
                throw new IllegalArgumentException("Transform cannot replace composition");
            if ((composition != null || transform != null)
                    && !CompoundFluids.isCompound(new FluidStack(fluid.get(), 1)))
                throw new IllegalArgumentException("Composition/transform requires compound output");
        }
    }

    public MixerRecipeDefinition(
            String id,
            List<FluidInput> fluids,
            List<ItemInput> items,
            Supplier<? extends Fluid> output,
            int amount,
            int ticks,
            CompoundComposition composition,
            Transform transform) {
        this(
                id,
                0,
                List.of(new Strategy("default", 0, true, fluids, items)),
                List.of(),
                List.of(new FluidOutput(output, amount, composition, transform)),
                ticks);
    }

    public MixerRecipeDefinition(
            String id,
            List<FluidInput> fluids,
            List<ItemInput> items,
            Supplier<? extends Fluid> output,
            int amount,
            int ticks,
            CompoundComposition composition) {
        this(id, fluids, items, output, amount, ticks, composition, null);
    }

    public record FluidInput(String identity, int amount, Predicate<FluidStack> selector, boolean recordInComposition) {
        public FluidInput(String identity, int amount, Predicate<FluidStack> selector) {
            this(identity, amount, selector, true);
        }

        public FluidInput(String selector, int amount) {
            this(selector, amount, true);
        }

        public FluidInput(String selector, int amount, boolean recordInComposition) {
            this(
                    CompoundComposition.canonicalSelector(CompoundComposition.Kind.FLUID, selector),
                    amount,
                    FluidIngredient.CODEC_NON_EMPTY
                            .parse(
                                    net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors.ops(),
                                    JsonParser.parseString(CompoundComposition.canonicalSelector(
                                            CompoundComposition.Kind.FLUID, selector)))
                            .getOrThrow(),
                    recordInComposition);
        }

        public FluidInput(List<String> values, int amount) {
            this(alternatives(values, CompoundComposition.Kind.FLUID), amount);
        }

        public FluidInput {
            if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive");
        }
    }

    public record ItemInput(String identity, int amount, Predicate<ItemStack> selector, boolean recordInComposition) {
        public ItemInput(String identity, int amount, Predicate<ItemStack> selector) {
            this(identity, amount, selector, true);
        }

        public ItemInput(String selector, int amount) {
            this(selector, amount, true);
        }

        public ItemInput(String selector, int amount, boolean recordInComposition) {
            this(
                    CompoundComposition.canonicalSelector(CompoundComposition.Kind.ITEM, selector),
                    amount,
                    Ingredient.CODEC_NONEMPTY
                            .parse(
                                    net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors.ops(),
                                    JsonParser.parseString(CompoundComposition.canonicalSelector(
                                            CompoundComposition.Kind.ITEM, selector)))
                            .getOrThrow(),
                    recordInComposition);
        }

        public ItemInput(List<String> values, int amount) {
            this(alternatives(values, CompoundComposition.Kind.ITEM), amount);
        }

        public ItemInput {
            if (amount <= 0) throw new IllegalArgumentException("Item amount must be positive");
        }
    }

    public record Removal(CompoundComposition.Kind kind, String selector) {
        public Removal {
            selector = CompoundComposition.canonicalSelector(kind, selector);
        }

        public boolean matches(CompoundComposition.Entry entry) {
            return kind == entry.kind()
                    && net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles.contains(
                            new CompoundComposition.Entry(kind, selector, 1), entry);
        }
    }

    public record Transform(
            int sourceInput,
            List<Removal> remove,
            CompoundComposition required,
            CompoundComposition excluded,
            boolean restoreOrdinary) {
        public Transform {
            remove = List.copyOf(remove);
            if (sourceInput < 0 || remove.isEmpty())
                throw new IllegalArgumentException("Transform requires a source and removal selectors");
        }

        private boolean contains(CompoundComposition formula, CompoundComposition needs) {
            if (needs == null) return true;
            for (var need : needs.entries())
                if (formula.entries().stream()
                                .filter(e -> e.kind() == need.kind()
                                        && net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles
                                                .contains(need, e))
                                .mapToLong(CompoundComposition.Entry::amount)
                                .sum()
                        < need.amount()) return false;
            return true;
        }

        public boolean matches(FluidStack stack) {
            var formula = stack.get(CompoundFluids.COMPOSITION.get());
            return CompoundFluids.isCompound(stack)
                    && formula != null
                    && contains(formula, required)
                    && (excluded == null || !contains(formula, excluded))
                    && remove.stream().allMatch(r -> formula.entries().stream().anyMatch(r::matches));
        }
    }

    private static String alternatives(List<String> values, CompoundComposition.Kind kind) {
        if (values.isEmpty()) throw new IllegalArgumentException("Empty selector");
        if (values.size() == 1) return values.getFirst();
        return "["
                + String.join(
                        ",",
                        values.stream()
                                .map(s -> CompoundComposition.canonicalSelector(kind, s))
                                .toList()) + "]";
    }

    public MixerRecipeDefinition {
        net.minecraft.resources.ResourceLocation.parse(id);
        strategies = strategies.stream()
                .sorted(Comparator.comparingInt(Strategy::priority).reversed().thenComparing(Strategy::id))
                .toList();
        itemOutputs = List.copyOf(itemOutputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        if (batchTicks <= 0 || strategies.isEmpty() || itemOutputs.isEmpty() && fluidOutputs.isEmpty())
            throw new IllegalArgumentException("Recipe requires strategies, outputs and positive batch time");
        var ids = new HashSet<String>();
        for (var strategy : strategies) {
            if (!ids.add(strategy.id())) throw new IllegalArgumentException("Duplicate strategy id: " + strategy.id());
            for (var out : fluidOutputs)
                if (out.transform() != null
                        && (out.transform().sourceInput() >= strategy.fluids().size()
                                || !strategy.fluids()
                                        .get(out.transform().sourceInput())
                                        .recordInComposition()))
                    throw new IllegalArgumentException("Transform requires a recorded source input in every strategy");
        }
    }
    // Legacy single-strategy/single-fluid accessors for integrations and saved test fixtures.
    public List<FluidInput> fluids() {
        return strategies.getFirst().fluids();
    }

    public List<ItemInput> items() {
        return strategies.getFirst().items();
    }

    public Supplier<? extends Fluid> output() {
        return fluidOutputs.getFirst().fluid();
    }

    public int outputAmount() {
        return fluidOutputs.getFirst().amount();
    }

    public Transform transform() {
        return fluidOutputs.getFirst().transform();
    }
    /** Stable authored identity for progress restored from NBT; predicate object identity is excluded. */
    public String signature() {
        var key = new StringBuilder(id).append('|').append(priority).append('|').append(batchTicks);
        for (var s : strategies) {
            key.append('|')
                    .append(s.id())
                    .append(':')
                    .append(s.priority())
                    .append(':')
                    .append(s.enabled());
            for (var i : s.items())
                key.append("|I:")
                        .append(i.identity())
                        .append(':')
                        .append(i.amount())
                        .append(':')
                        .append(i.recordInComposition());
            for (var f : s.fluids())
                key.append("|F:")
                        .append(f.identity())
                        .append(':')
                        .append(f.amount())
                        .append(':')
                        .append(f.recordInComposition());
        }
        for (var i : itemOutputs)
            key.append("|O:")
                    .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i.item()))
                    .append(':')
                    .append(i.amount());
        for (var f : fluidOutputs)
            key.append("|O:")
                    .append(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(
                            f.fluid().get()))
                    .append(':')
                    .append(f.amount())
                    .append(':')
                    .append(f.composition())
                    .append(':')
                    .append(f.transform());
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean matchesInput(int index, FluidStack stack) {
        return matchesInput(strategies.getFirst(), index, stack);
    }

    public boolean matchesInput(Strategy strategy, int index, FluidStack stack) {
        return strategy.fluids().get(index).selector().test(stack)
                && fluidOutputs.stream()
                        .allMatch(o -> o.transform() == null
                                || index != o.transform().sourceInput()
                                || o.transform().matches(stack));
    }

    public boolean outputMatches(FluidStack stack) {
        return fluidOutputs.stream()
                .anyMatch(o -> stack.is(o.fluid().get())
                        || o.transform() != null
                                && CompoundFluids.isCompound(stack)
                                && strategies.stream()
                                        .anyMatch(s -> s.fluids()
                                                .get(o.transform().sourceInput())
                                                .selector()
                                                .test(stack)));
    }

    public FluidStack result(List<List<FluidStack>> consumed) {
        return result(strategies.getFirst(), fluidOutputs.getFirst(), consumed);
    }

    public FluidStack result(Strategy strategy, FluidOutput out, List<List<FluidStack>> consumed) {
        var fluids = strategy.fluids();
        var items = strategy.items();
        var transform = out.transform();
        var explicitComposition = out.composition();
        var result = new FluidStack(out.fluid().get(), out.amount());
        if (transform != null) {
            var source = consumed.get(transform.sourceInput());
            if (source.isEmpty()
                    || source.stream()
                            .anyMatch(s -> !transform.matches(s)
                                    || !FluidStack.isSameFluidSameComponents(source.getFirst(), s)))
                throw new IllegalArgumentException("Transform requires one exact compound identity");
            result = source.getFirst().copyWithAmount(out.amount());
        }
        if (CompoundFluids.isCompound(result)) {
            var formula = explicitComposition;
            if (formula == null) {
                var entries = new ArrayList<CompoundComposition.Entry>();
                for (int i = 0; i < fluids.size(); i++)
                    if (fluids.get(i).recordInComposition())
                        CompoundFluids.appendInput(
                                entries, fluids.get(i).identity(), fluids.get(i).amount(), consumed.get(i));
                for (var item : items)
                    if (item.recordInComposition())
                        entries.add(new CompoundComposition.Entry(
                                CompoundComposition.Kind.ITEM, item.identity(), item.amount()));
                if (transform != null)
                    entries.removeIf(e -> transform.remove().stream().anyMatch(r -> r.matches(e)));
                formula = new CompoundComposition(entries);
            }
            result.set(CompoundFluids.COMPOSITION.get(), formula);
        } else if (explicitComposition != null)
            throw new IllegalArgumentException("Ordinary outputs cannot have compound composition");
        return transform != null && transform.restoreOrdinary() ? CultureFouling.unwrap(result) : result;
    }
}
