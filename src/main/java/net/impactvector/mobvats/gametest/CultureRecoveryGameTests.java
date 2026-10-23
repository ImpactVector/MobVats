package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.google.gson.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatMediumStorage;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class CultureRecoveryGameTests {
    static JsonObject defaults() {
        try (var in = CultureRecoveryGameTests.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")) {
            return JsonParser.parseString(new String(
                            Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static FluidStack culture(int amount, int xp, int waste) {
        var entries = new ArrayList<CompoundComposition.Entry>();
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 1000));
        if (xp > 0)
            entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:experience", xp));
        if (waste > 0)
            entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:waste", waste));
        var result = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), amount);
        result.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        return result;
    }

    private static void same(GameTestHelper h, FluidStack actual, FluidStack expected) {
        h.assertTrue(
                FluidStack.matches(actual, expected),
                "Expected " + expected + " " + expected.getComponents() + ", got " + actual + " "
                        + actual.getComponents());
    }

    private static net.minecraft.nbt.CompoundTag resources(GameTestHelper h, MixerState state) {
        var tag = state.save(h.getLevel().registryAccess());
        tag.remove("blocked"); // Failed planning may update diagnostics, never resources or metrics.
        return tag;
    }

    private static void recipes(JsonObject root, java.util.function.Consumer<MixerRecipeRegistry.Loaded> work) {
        var before = MixerRecipeRegistry.snapshot();
        try {
            var loaded = MixerRecipeRegistry.parse(root);
            MixerRecipeRegistry.publish(loaded);
            work.accept(loaded);
        } finally {
            MixerRecipeRegistry.publish(before);
        }
    }

    @GameTest(template = "empty")
    public static void xpReplacementConservesFractionsPersistenceAndPartialDrain(GameTestHelper h) {
        var m = new VatMediumStorage(() -> {});
        m.setCapacity(4000);
        var fresh = culture(2000, 250, 17);
        m.setFluid(fresh);
        h.assertTrue(
                m.foul(777) == 777 && m.cleanAmount() == 1223 && m.dirtyAmount() == 777 && m.getFluidAmount() == 2000,
                "Physical culture not conserved");
        same(h, m.cleanPrototype(), fresh.copyWithAmount(1));
        same(h, m.dirtyPrototype(), culture(1, 0, 267));
        var restored = new VatMediumStorage(() -> {});
        restored.setCapacity(4000);
        restored.load(m.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.matches(m.snapshot()), "Persisted fractions changed");
        same(h, restored.drain(123, EXECUTE), culture(123, 0, 267));
        h.assertTrue(restored.drain(fresh.copyWithAmount(1), EXECUTE).isEmpty(), "Dirty-first rule bypassed");
        same(h, restored.drain(1000, EXECUTE), culture(654, 0, 267));
        same(h, restored.drain(2000, EXECUTE), fresh.copyWithAmount(1223));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullReplacementUsesActualXpNotConfiguredLegacyWaste(GameTestHelper h) {
        for (int xp : new int[] {1, 100, 250, 137}) {
            var dirty = CultureFouling.dirty(culture(1000, xp, 13), 99);
            same(h, dirty, culture(1, 0, xp + 13));
        }
        same(h, CultureFouling.dirty(culture(1000, 0, 13), 99), culture(1, 0, 112));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacySavedPairMigratesWithoutLosingPreexistingWasteOrAmounts(GameTestHelper h) {
        var old = new VatMediumStorage(() -> {});
        old.restore(new VatMediumStorage.State(culture(1, 250, 17), culture(1, 250, 117), 1223, 777));
        var restored = new VatMediumStorage(() -> {});
        restored.load(old.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.cleanAmount() == 1223 && restored.dirtyAmount() == 777, "Legacy fractions changed");
        same(h, restored.cleanPrototype(), culture(1, 250, 17));
        same(h, restored.dirtyPrototype(), culture(1, 0, 267));
        restored.foul(23);
        same(h, restored.drain(123, EXECUTE), culture(123, 0, 267));
        h.assertTrue(restored.getFluidAmount() == 1877, "Migration/partial transfer lost physical Culture");
        var custom = culture(1, 250, 117);
        custom.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct custom dirty metadata"));
        same(h, CultureFouling.migrateDirtyPrototype(culture(1, 250, 17), custom), custom);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oneAndTwoMixerLoopsConserveAndPayConfiguredXp(GameTestHelper h) {
        recipes(defaults(), loaded -> {
            for (boolean separate : new boolean[] {false, true}) {
                var filter = loaded.recipes().get("mobvats:filter_general");
                var enrich = loaded.recipes().get("mobvats:enrich_culture");
                var fresh = culture(1000, 250, 0);
                fresh.set(DataComponents.CUSTOM_NAME, Component.literal("Culture metadata"));
                var current = fresh;
                var a = new MixerState(() -> {});
                var b = separate ? new MixerState(() -> {}) : a;
                for (int cycle = 0; cycle < 8; cycle++) {
                    a.fluids(true).fill(CultureFouling.dirty(current, 100).copyWithAmount(1000), EXECUTE);
                    a.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
                    h.assertTrue(a.select().orElseThrow().recipe().equals(filter.id()), "Wrong filter selection");
                    h.assertTrue(a.commit(filter), "Filter did not commit");
                    var intermediate = a.fluids(false).drain(1000, EXECUTE);
                    var expected = culture(1000, 0, 0);
                    expected.set(DataComponents.CUSTOM_NAME, Component.literal("Culture metadata"));
                    same(h, intermediate, expected);
                    h.assertTrue(
                            CultureProfiles.multiplier(ResourceLocation.withDefaultNamespace("cow"), intermediate)
                                    == 0.5,
                            "Filtered culture qualified as enriched");
                    // The intermediate is an ordinary transferable/persistable FluidStack.
                    var saved = (net.minecraft.nbt.CompoundTag)
                            intermediate.save(h.getLevel().registryAccess());
                    b.fluids(true).fill(FluidStack.parseOptional(h.getLevel().registryAccess(), saved), EXECUTE);
                    b.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 249), EXECUTE);
                    var before = resources(h, b);
                    h.assertTrue(
                            !b.commit(enrich) && before.equals(resources(h, b)), "Insufficient XP consumed inputs");
                    b.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 1), EXECUTE);
                    h.assertTrue(
                            b.select().orElseThrow().recipe().equals(enrich.id()) && b.commit(enrich),
                            "Enrichment selection/commit failed");
                    current = b.fluids(false).drain(1000, EXECUTE);
                    same(h, current, fresh);
                    h.assertTrue(
                            !enrich.matchesInput(0, current) && !b.commit(enrich),
                            "Repeated batch duplicated ingredients");
                }
                h.assertTrue(
                        b.metrics().total("experienceConsumedMb") == 2000
                                && a.metrics().total("cultureFilteredMb") == 8000,
                        "Round-trip ingredient accounting changed");
            }
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void enrichmentRetainsUnrelatedIngredientsAndWaste(GameTestHelper h) {
        recipes(defaults(), loaded -> {
            var source = culture(1000, 0, 19);
            var entries =
                    new ArrayList<>(source.get(CompoundFluids.COMPOSITION.get()).entries());
            entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:diamond", 3));
            source.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
            var enrich = loaded.recipes().get("mobvats:enrich_culture");
            var actual = enrich.result(
                    List.of(List.of(source), List.of(new FluidStack(ProductFluids.EXPERIENCE.get(), 250))));
            entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:experience", 250));
            source.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
            same(h, actual, source);
            var filter = loaded.recipes().get("mobvats:filter_general");
            h.assertTrue(
                    !filter.matchesInput(0, CultureFouling.dirty(new FluidStack(ProductFluids.MEAT.get(), 1), 100)),
                    "Bundled charcoal recipe became universal");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullOutputAndStalePlansAreAtomic(GameTestHelper h) {
        recipes(defaults(), loaded -> {
            var s = new MixerState(() -> {});
            var recipe = loaded.recipes().get("mobvats:enrich_culture");
            s.fluids(true).fill(culture(1000, 0, 0), EXECUTE);
            s.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 250), EXECUTE);
            var plan = s.select().orElseThrow();
            s.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 1), EXECUTE);
            var before = resources(h, s);
            h.assertTrue(!plan.commit() && before.equals(resources(h, s)), "Stale XP plan committed");
            s.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 1), EXECUTE);
            var output = recipe.result(List.of(
                    List.of(culture(1000, 0, 0)), List.of(new FluidStack(ProductFluids.EXPERIENCE.get(), 250))));
            var tag = s.save(h.getLevel().registryAccess());
            var full = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(SlurryBalance.CAPACITY);
            full.setFluid(output.copyWithAmount(SlurryBalance.CAPACITY));
            tag.getList("escrow", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    .add(full.writeToNBT(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag()));
            s.load(tag, h.getLevel().registryAccess());
            before = resources(h, s);
            h.assertTrue(!s.commit(recipe) && before.equals(resources(h, s)), "Full output consumed inputs");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredEnrichmentRatioAndExactMeters(GameTestHelper h) {
        var root = defaults();
        root.getAsJsonObject("recipes")
                .getAsJsonObject("mobvats:enrich_culture")
                .getAsJsonArray("strategies")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("fluids")
                .get(1)
                .getAsJsonObject()
                .addProperty("amount", 100);
        recipes(root, loaded -> {
            var recipe = loaded.recipes().get("mobvats:enrich_culture");
            var consumed =
                    List.of(List.of(culture(1000, 0, 0)), List.of(new FluidStack(ProductFluids.EXPERIENCE.get(), 100)));
            same(h, recipe.result(consumed), culture(1000, 100, 0));
            var meter = new MixerMeterRequirements(
                    List.of(), List.of(new FluidMeterRequirement("mobvats:experience", 100)));
            h.assertTrue(
                    meter.matches(recipe.strategies().getFirst(), List.of(), consumed), "Configured XP meter failed");
            var wrong = new MixerMeterRequirements(
                    List.of(), List.of(new FluidMeterRequirement("mobvats:experience", 250)));
            h.assertTrue(!wrong.matches(recipe.strategies().getFirst(), List.of(), consumed), "Inexact meter accepted");
            var state = new MixerState(() -> {});
            state.fluids(true).fill(culture(1000, 0, 0), EXECUTE);
            state.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 100), EXECUTE);
            state.meterRequirements(wrong);
            h.assertTrue(state.select().isEmpty(), "Inexact XP meter selected enrichment");
            state.meterRequirements(meter);
            h.assertTrue(
                    state.select().orElseThrow().commit() && state.metrics().total("experienceConsumedMb") == 100,
                    "Configured XP consumption changed");
            same(h, state.fluids(false).drain(1000, EXECUTE), culture(1000, 100, 0));
        });
        h.succeed();
    }
}
