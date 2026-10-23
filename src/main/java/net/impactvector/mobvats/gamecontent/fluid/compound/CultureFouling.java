package net.impactvector.mobvats.gamecontent.fluid.compound;

import com.google.gson.JsonParser;
import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Waste is part of the canonical formula, never a contamination scalar or nested fluid. */
public final class CultureFouling {
    public static final String WASTE =
            CompoundComposition.canonicalSelector(CompoundComposition.Kind.FLUID, "mobvats:waste");
    public static final String XP =
            CompoundComposition.canonicalSelector(CompoundComposition.Kind.FLUID, "mobvats:experience");

    public static boolean experience(CompoundComposition.Entry e) {
        return e.kind() == CompoundComposition.Kind.FLUID && e.selector().equals(XP);
    }

    public static boolean waste(CompoundComposition.Entry e) {
        return e.kind() == CompoundComposition.Kind.FLUID && e.selector().equals(WASTE);
    }

    public static FluidStack dirty(FluidStack clean, int wasteAmount) {
        if (clean.isEmpty() || wasteAmount <= 0) return FluidStack.EMPTY;
        try {
            var entries = new ArrayList<CompoundComposition.Entry>();
            FluidStack result;
            if (CompoundFluids.isCompound(clean)) {
                var formula = clean.get(CompoundFluids.COMPOSITION.get());
                if (formula == null) return FluidStack.EMPTY; // World spills have no recoverable formula.
                entries.addAll(formula.entries());
                result = clean.copyWithAmount(1);
            } else {
                entries.add(new CompoundComposition.Entry(
                        CompoundComposition.Kind.FLUID,
                        BuiltInRegistries.FLUID.getKey(clean.getFluid()).toString(),
                        1000));
                result = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1);
                result.applyComponents(clean.getComponentsPatch());
            }
            int xp = entries.stream()
                    .filter(CultureFouling::experience)
                    .mapToInt(CompoundComposition.Entry::amount)
                    .sum();
            entries.removeIf(CultureFouling::experience);
            // Existing contamination remains; only XP is replaced. Non-XP media retain legacy fouling.
            entries.add(
                    new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, WASTE, xp > 0 ? xp : wasteAmount));
            result.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
            return result;
        } catch (IllegalArgumentException | ArithmeticException e) {
            return FluidStack.EMPTY;
        }
    }
    /** Only the ordinary wrapper family and an exact resource selector can restore a raw fluid. */
    public static FluidStack unwrap(FluidStack stack) {
        if (!stack.is(ProductFluids.CULTURE_SLURRY.get())) return stack;
        var formula = stack.get(CompoundFluids.COMPOSITION.get());
        if (formula == null || formula.entries().size() != 1) return stack;
        var entry = formula.entries().getFirst();
        if (entry.kind() != CompoundComposition.Kind.FLUID || entry.amount() != 1000) return stack;
        var json = JsonParser.parseString(entry.selector());
        if (!json.isJsonObject()
                || json.getAsJsonObject().size() != 1
                || !json.getAsJsonObject().has("fluid")
                || !json.getAsJsonObject().get("fluid").isJsonPrimitive()) return stack;
        var id = ResourceLocation.tryParse(json.getAsJsonObject().get("fluid").getAsString());
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return stack;
        var raw = new FluidStack(BuiltInRegistries.FLUID.get(id), stack.getAmount());
        if (raw.is(Fluids.EMPTY) || CompoundFluids.isCompound(raw)) return stack;
        raw.applyComponents(stack.getComponentsPatch());
        raw.remove(CompoundFluids.COMPOSITION.get());
        return raw;
    }
    /** Reverse only the configured increment, then verify the exact round trip before accepting a pair. */
    public static FluidStack clean(FluidStack dirty, int wasteAmount) {
        var formula = dirty.get(CompoundFluids.COMPOSITION.get());
        if (formula == null || wasteAmount <= 0) return FluidStack.EMPTY;
        var entries = new ArrayList<CompoundComposition.Entry>();
        boolean found = false;
        for (var entry : formula.entries()) {
            if (waste(entry)) {
                if (entry.amount() < wasteAmount) return FluidStack.EMPTY;
                found = true;
                if (entry.amount() > wasteAmount)
                    entries.add(new CompoundComposition.Entry(
                            entry.kind(), entry.selector(), entry.amount() - wasteAmount));
            } else entries.add(entry);
        }
        if (!found || entries.isEmpty()) return FluidStack.EMPTY;
        var compound = dirty.copyWithAmount(1);
        compound.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        var result = unwrap(compound);
        if (FluidStack.isSameFluidSameComponents(dirty, dirty(result, wasteAmount))) return result;
        // An exact single-fluid compound formula need not use the ordinary wrapper's 1000 mB basis.
        return FluidStack.isSameFluidSameComponents(dirty, dirty(compound, wasteAmount)) ? compound : FluidStack.EMPTY;
    }

    public static boolean hasWaste(FluidStack stack) {
        var formula = stack.get(CompoundFluids.COMPOSITION.get());
        return formula != null && formula.entries().stream().anyMatch(CultureFouling::waste);
    }

    /** Recognize the old saved pair (clean + a Waste increment, still carrying XP).
     * The clean prototype identifies pre-existing contamination, so it is preserved without guessing.
     * Unrecognized/custom pairs retain their exact stored identities.
     */
    public static FluidStack migrateDirtyPrototype(FluidStack clean, FluidStack storedDirty) {
        var source = clean.get(CompoundFluids.COMPOSITION.get());
        var stored = storedDirty.get(CompoundFluids.COMPOSITION.get());
        if (source == null
                || stored == null
                || source.entries().stream().noneMatch(CultureFouling::experience)
                || stored.entries().stream().noneMatch(CultureFouling::experience)) return storedDirty;
        int increment = stored.entries().stream()
                        .filter(CultureFouling::waste)
                        .mapToInt(CompoundComposition.Entry::amount)
                        .sum()
                - source.entries().stream()
                        .filter(CultureFouling::waste)
                        .mapToInt(CompoundComposition.Entry::amount)
                        .sum();
        if (increment <= 0) return storedDirty;
        var entries = new ArrayList<>(source.entries());
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, WASTE, increment));
        var legacy = clean.copyWithAmount(1);
        legacy.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        return FluidStack.isSameFluidSameComponents(legacy, storedDirty) ? dirty(clean, increment) : storedDirty;
    }

    private CultureFouling() {}
}
