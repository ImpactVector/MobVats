package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class Slice122GameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1),
            MAX = new BlockPos(6, 4, 6),
            HEAD = new BlockPos(3, 3, 3),
            TOOL = new BlockPos(3, 2, 5),
            FEED = new BlockPos(3, 2, 1);
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");

    private static VatMediumStorage medium(GameTestHelper h) {
        var m = new VatMediumStorage(() -> {});
        m.compile(h.getLevel(), h.absolutePos(MIN), h.absolutePos(MAX));
        return m;
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static FluidStack culture(int amount) {
        var s = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), amount);
        s.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "#c:water", 1000),
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:wheat", 8))));
        return s;
    }

    private static FluidStack enriched(int amount) {
        var s = culture(amount);
        var entries = new ArrayList<>(s.get(CompoundFluids.COMPOSITION.get()).entries());
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:experience", 250));
        s.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        return s;
    }

    private static void same(GameTestHelper h, FluidStack a, FluidStack b) {
        h.assertTrue(FluidStack.isSameFluidSameComponents(a, b), "Fluid identity/components changed: " + a + " / " + b);
    }

    private static void close(GameTestHelper h, double actual, double expected) {
        h.assertTrue(Math.abs(actual - expected) < 1e-8, "Expected " + expected + ", got " + actual);
    }

    /** Legacy authored recipe compatibility fixture, distinct from the current bundled recovery recipes. */
    private static MixerRecipeRegistry.Loaded legacyDefinitions() {
        try (var in = Slice122GameTests.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")) {
            var root = JsonParser.parseString(new String(
                            Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            root.getAsJsonObject("recipes").remove("mobvats:enrich_culture");
            var general = root.getAsJsonObject("recipes").getAsJsonObject("mobvats:filter_general");
            general.getAsJsonArray("strategies")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("fluids")
                    .get(0)
                    .getAsJsonObject()
                    .add(
                            "selector",
                            JsonParser.parseString(
                                    "[{\"fluid\":\"mobvats:culture_slurry\"},{\"fluid\":\"mobvats:component_fluid\"}]"));
            var transform = general.getAsJsonObject("outputs")
                    .getAsJsonArray("fluids")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("transform");
            transform.remove("required");
            transform.addProperty("restore_ordinary", true);
            return MixerRecipeRegistry.parse(root);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void scoped(MixerRecipeRegistry.Loaded loaded, Runnable work) {
        var before = MixerRecipeRegistry.snapshot();
        try {
            MixerRecipeRegistry.publish(loaded);
            work.run();
        } finally {
            MixerRecipeRegistry.publish(before);
        }
    }

    private static FluidStack filter(GameTestHelper h, FluidStack clean, String filterId) {
        var loaded = legacyDefinitions();
        var recipe = loaded.recipes().get(filterId);
        var dirty = CultureFouling.dirty(clean, 100).copyWithAmount(1000);
        return recipe.result(List.of(List.of(dirty)));
    }

    private static VatBoundaryEntity valve(GameTestHelper h, VatPartType type) {
        var p = new BlockPos(7, 2, 2);
        h.setBlock(p, Content.state(type));
        return (VatBoundaryEntity) h.getBlockEntity(p);
    }

    @GameTest(template = "empty")
    public static void freshMediumIsCleanOnly(GameTestHelper h) {
        var m = medium(h);
        h.assertTrue(
                m.fill(culture(1000), EXECUTE) == 1000 && m.cleanAmount() == 1000 && m.dirtyAmount() == 0,
                "Fresh fill was dirty");
        same(h, m.cleanPrototype(), culture(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fractionsShareCapacity(GameTestHelper h) {
        var m = medium(h);
        m.setCapacity(1000);
        m.fill(water(800), EXECUTE);
        m.foul(600);
        h.assertTrue(
                m.fill(m.dirtyPrototype().copyWithAmount(500), SIMULATE) == 200
                        && m.fill(water(500), EXECUTE) == 200
                        && m.getFluidAmount() == 1000
                        && m.fill(water(1), EXECUTE) == 0,
                "Fractions received separate capacities");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foulingPreservesLevelAndVolume(GameTestHelper h) {
        var m = medium(h);
        m.fill(water(4000), EXECUTE);
        var p = h.absolutePos(new BlockPos(2, 2, 2));
        double before = m.exposure(p);
        m.foul(2000);
        close(h, m.exposure(p), before);
        h.assertTrue(m.getFluidAmount() == 4000, "Fouling minted/lost volume");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullyDirtyRetainsPrototype(GameTestHelper h) {
        var m = medium(h);
        m.fill(enriched(1000), EXECUTE);
        m.foul(1000);
        same(h, m.cleanPrototype(), enriched(1));
        h.assertTrue(m.cleanAmount() == 0 && m.dirtyAmount() == 1000, "Full dirty state lost");
        close(h, m.frequency(COW), 2.75);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void finalDrainResetsSelectedCulture(GameTestHelper h) {
        var m = medium(h);
        m.fill(water(1000), EXECUTE);
        m.foul(1000);
        m.drain(1000, EXECUTE);
        h.assertTrue(
                m.cleanPrototype().isEmpty() && m.dirtyPrototype().isEmpty() && m.fill(culture(100), EXECUTE) == 100,
                "Empty medium retained old identity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void compoundDirtyAddsWasteAndKeepsCanonicalFormula(GameTestHelper h) {
        var clean = culture(1);
        var dirty = CultureFouling.dirty(clean, 137);
        var entries =
                new ArrayList<>(clean.get(CompoundFluids.COMPOSITION.get()).entries());
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:waste", 137));
        h.assertTrue(
                dirty.get(CompoundFluids.COMPOSITION.get()).equals(new CompoundComposition(entries)),
                "Formula changed beyond configured waste");
        same(h, CultureFouling.clean(dirty, 137), clean);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void componentFamilyAndMetadataSurviveFouling(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.COMPONENT_FLUID.get(), 1000);
        clean.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:chorus_fruit", 1))));
        clean.set(DataComponents.CUSTOM_NAME, Component.literal("Exact metadata"));
        var dirty = CultureFouling.dirty(clean, 100);
        h.assertTrue(
                dirty.is(clean.getFluid())
                        && dirty.get(CompoundFluids.COMPOSITION.get()).entries().size() == 2,
                "Compound family nested/changed");
        same(h, CultureFouling.clean(dirty, 100), clean);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rawWaterWrapsExactFluid(GameTestHelper h) {
        var dirty = CultureFouling.dirty(water(1), 100);
        h.assertTrue(
                dirty.is(ProductFluids.CULTURE_SLURRY.get())
                        && dirty.get(CompoundFluids.COMPOSITION.get()).entries().stream()
                                .anyMatch(e -> e.selector()
                                        .equals(CompoundComposition.canonicalSelector(
                                                CompoundComposition.Kind.FLUID, "minecraft:water"))),
                "Raw water broadened to tag");
        same(h, CultureFouling.clean(dirty, 100), water(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rawLavaWrapsExactFluid(GameTestHelper h) {
        var clean = new FluidStack(Fluids.LAVA, 1);
        same(h, CultureFouling.clean(CultureFouling.dirty(clean, 100), 100), clean);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moddedRawFluidWrapsAndRestoresExactly(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.MEAT.get(), 1);
        same(h, CultureFouling.clean(CultureFouling.dirty(clean, 100), 100), clean);
        same(h, filter(h, clean, "mobvats:filter_general"), clean);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownWorldFormulaCannotBeInventedByFouling(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000);
        h.assertTrue(CultureFouling.dirty(clean, 100).isEmpty(), "World spill gained a formula");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void matchingCleanAndDirtyCanRefill(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(1000), EXECUTE);
        m.foul(1000);
        h.assertTrue(
                m.fill(culture(200), EXECUTE) == 200
                        && m.fill(m.dirtyPrototype().copyWithAmount(300), EXECUTE) == 300
                        && m.cleanAmount() == 200
                        && m.dirtyAmount() == 1300,
                "Matching pair rejected");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unrelatedCleanAndDirtyAreRejected(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(1000), EXECUTE);
        h.assertTrue(
                m.fill(water(1000), EXECUTE) == 0
                        && m.fill(CultureFouling.dirty(water(1000), 100).copyWithAmount(1000), EXECUTE) == 0,
                "Unrelated culture blended");
        h.assertTrue(m.getFluidAmount() == 1000 && m.dirtyAmount() == 0, "Rejected fill changed medium");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyVatAcceptsRecoverableDirtyPair(GameTestHelper h) {
        for (var clean : List.of(water(1), culture(1), new FluidStack(Fluids.LAVA, 1))) {
            var m = medium(h);
            h.assertTrue(
                    m.fill(CultureFouling.dirty(clean, 100).copyWithAmount(1000), EXECUTE) == 1000,
                    "Valid dirty fill rejected");
            same(h, m.cleanPrototype(), clean);
            h.assertTrue(m.dirtyAmount() == 1000 && m.cleanAmount() == 0, "Dirty fill silently cleaned");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedDirtyFillIsRejected(GameTestHelper h) {
        var dirty = culture(1000);
        var entries =
                new ArrayList<>(dirty.get(CompoundFluids.COMPOSITION.get()).entries());
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:waste", 99));
        dirty.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        h.assertTrue(medium(h).fill(dirty, EXECUTE) == 0, "Unsafe dirty pair inferred");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialLastConversionAndFullDirtyNoOp(GameTestHelper h) {
        var m = medium(h);
        m.fill(water(73), EXECUTE);
        h.assertTrue(
                m.foul(50) == 50 && m.foul(50) == 23 && m.foul(50) == 0 && m.getFluidAmount() == 73,
                "Partial conversion lost/minted fluid");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foulingRollbackRestoresBothFractions(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(1000), EXECUTE);
        var before = m.snapshot();
        var transaction = new ProcessTransaction()
                .add(Fouling.step(m, 50))
                .add(new ProcessTransaction.Step(new Object(), () -> true, () -> false, () -> {}));
        h.assertTrue(!transaction.commit() && m.matches(before), "Failed transaction fouled medium");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredActionVolumesParseAndValidate(GameTestHelper h) {
        var data = CultureProfiles.parse(JsonParser.parseString(
                        "{\"default\":\"x\",\"profiles\":{\"x\":[{\"medium\":\"minecraft:water\",\"frequency\":1,\"dirty_frequency\":0.75}]},\"fouling\":{\"default_mb\":50,\"waste_formula_mb\":137,\"actions\":{\"test:action\":{\"fouling_mb\":25}}}}")
                .getAsJsonObject());
        var before = CultureProfiles.snapshot();
        try {
            CultureProfiles.publish(data);
            h.assertTrue(
                    CultureProfiles.foulingMb("test:action") == 25
                            && CultureProfiles.foulingMb("test:other") == 50
                            && CultureProfiles.wasteFormulaMb() == 137,
                    "Action config ignored");
        } finally {
            CultureProfiles.publish(before);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCleanAndDirtyEndpoints(GameTestHelper h) {
        var m = medium(h);
        m.fill(water(1000), EXECUTE);
        close(h, m.frequency(COW), 1);
        m.foul(1000);
        close(h, m.frequency(COW), .75);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void enrichedVolumeWeightedFrequency(GameTestHelper h) {
        var m = medium(h);
        m.fill(enriched(1000), EXECUTE);
        close(h, m.frequency(COW), 5);
        m.foul(500);
        close(h, m.frequency(COW), 3.875);
        m.foul(250);
        close(h, m.frequency(COW), 3.3125);
        m.foul(250);
        close(h, m.frequency(COW), 2.75);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void amountDrainIsDirtyFirstThenClean(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(2000), EXECUTE);
        m.foul(650);
        var dirty = m.dirtyPrototype();
        same(h, m.drain(1000, SIMULATE), dirty);
        h.assertTrue(m.drain(1000, EXECUTE).getAmount() == 650, "Amount drain mixed clean into dirty");
        same(h, m.drain(1000, EXECUTE), culture(1));
        h.assertTrue(m.cleanAmount() == 350 && m.dirtyAmount() == 0, "Clean tail wrong");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitDrainCannotSkipDirtyFraction(GameTestHelper h) {
        var m = medium(h);
        m.fill(water(2000), EXECUTE);
        m.foul(50);
        h.assertTrue(m.drain(water(1000), EXECUTE).isEmpty(), "Exact drain bypassed dirty ordering");
        same(h, m.drain(m.dirtyPrototype().copyWithAmount(50), EXECUTE), CultureFouling.dirty(water(1), 100));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drainValveExportsDirtyBeforeClean(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(2000), EXECUTE);
        m.foul(650);
        var out = new FluidTank(2000);
        var e = valve(h, VatPartType.DrainValve);
        h.assertTrue(BoundaryDevices.drain(m, out, e) && out.getFluidAmount() == 650, "Valve failed dirty evacuation");
        same(h, out.getFluid(), CultureFouling.dirty(culture(1), 100));
        h.assertTrue(
                !BoundaryDevices.drain(m, out, e) && m.cleanAmount() == 1350,
                "Valve mixed distinct identities into output");
        out.setFluid(FluidStack.EMPTY);
        h.assertTrue(BoundaryDevices.drain(m, out, e), "Valve failed clean tail");
        same(h, out.getFluid(), culture(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void waterAndEnrichedCultureUseCharcoal(GameTestHelper h) {
        same(h, filter(h, water(1), "mobvats:filter_general"), water(1));
        same(h, filter(h, culture(1), "mobvats:filter_general"), culture(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaUsesSandAndRejectsCharcoalRecipe(GameTestHelper h) {
        var clean = new FluidStack(Fluids.LAVA, 1);
        same(h, filter(h, clean, "mobvats:filter_lava"), clean);
        var dirty = CultureFouling.dirty(clean, 100);
        h.assertTrue(
                !legacyDefinitions().recipes().get("mobvats:filter_general").matchesInput(0, dirty),
                "Charcoal recipe matched lava culture");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void arbitraryAdditivesAndFamilySurviveFiltration(GameTestHelper h) {
        var clean = culture(1);
        var entries =
                new ArrayList<>(clean.get(CompoundFluids.COMPOSITION.get()).entries());
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:diamond", 3));
        clean.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        same(h, filter(h, clean, "mobvats:filter_general"), clean);
        var end = new FluidStack(ProductFluids.COMPONENT_FLUID.get(), 1);
        end.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:chorus_fruit", 1))));
        same(h, filter(h, end, "mobvats:filter_general"), end);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void filterItemsConsumedWithoutEnteringFormula(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            for (var id : List.of("mobvats:filter_general", "mobvats:filter_lava")) {
                var recipe = loaded.recipes().get(id);
                var clean = id.endsWith("lava") ? new FluidStack(Fluids.LAVA, 1000) : culture(1000);
                var state = new MixerState(() -> {});
                state.fluids(true).fill(CultureFouling.dirty(clean, 100).copyWithAmount(1000), EXECUTE);
                state.itemInput()
                        .insertItem(0, new ItemStack(id.endsWith("lava") ? Items.SAND : Items.CHARCOAL), false);
                h.assertTrue(
                        state.commit(recipe)
                                && state.plantCount() == 0
                                && state.metrics().total("cultureFilteredMb") == 1000,
                        "Filter batch failed or did not consume material");
                var output = state.fluids(false).drain(1000, EXECUTE);
                same(h, output, clean);
                h.assertTrue(output.getAmount() == 1000, "Filtration changed culture volume");
            }
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidReagentCanBeConsumedWithoutRecording(GameTestHelper h) {
        var loaded = MixerRecipeRegistry.parse(
                        JsonParser.parseString(
                                "{\"recipes\":{\"test:fluid_filter\":{\"fluids\":[{\"selector\":\"mobvats:culture_slurry\",\"amount\":1000},{\"selector\":\"mobvats:meat\",\"amount\":25,\"record_in_composition\":false}],\"output\":\"mobvats:culture_slurry\",\"output_amount\":1000,\"batch_ticks\":1,\"transform\":{\"remove\":[{\"kind\":\"FLUID\",\"selector\":\"mobvats:waste\"}]}}}}"))
                .recipes()
                .get("test:fluid_filter");
        var dirty = CultureFouling.dirty(culture(1), 100).copyWithAmount(1000);
        same(
                h,
                loaded.result(List.of(List.of(dirty), List.of(new FluidStack(ProductFluids.MEAT.get(), 25)))),
                culture(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tagOnlyFormulaIsNeverUnwrapped(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1);
        clean.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(
                        List.of(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "#c:water", 1000))));
        same(h, filter(h, clean, "mobvats:filter_general"), clean);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ambiguousAlternativeFormulaIsNeverUnwrapped(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1);
        clean.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(new CompoundComposition.Entry(
                        CompoundComposition.Kind.FLUID,
                        "[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:lava\"}]",
                        1000))));
        same(h, CultureFouling.unwrap(clean), clean);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cleanOrSpilledCultureDoesNotConsumeFilters(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            for (var input : List.of(culture(1000), new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000))) {
                var state = new MixerState(() -> {});
                h.assertTrue(state.fluids(true).fill(input, EXECUTE) == 0, "Filter accepted non-dirty input");
                state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
                h.assertTrue(
                        !state.commit(loaded.recipes().get("mobvats:filter_general"))
                                && state.plantCount() == 1
                                && state.metrics().total("cultureFilteredMb") == 0,
                        "Failed filter consumed material/counted volume");
            }
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void saveLoadPreservesBothFractionsAndPrototype(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(1234), EXECUTE);
        m.foul(345);
        var restored = medium(h);
        restored.load(m.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.matches(m.snapshot()), "Clean/dirty NBT changed");
        m.foul(1234);
        restored.load(m.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.cleanAmount() == 0 && restored.dirtyAmount() == 1234, "Fully dirty save lost prototype");
        same(h, restored.cleanPrototype(), culture(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtyFluidAndMediumNetworkRoundTrip(GameTestHelper h) {
        var m = medium(h);
        m.fill(culture(1234), EXECUTE);
        m.foul(432);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            FluidStack.STREAM_CODEC.encode(buf, m.dirtyPrototype().copyWithAmount(432));
            same(h, FluidStack.STREAM_CODEC.decode(buf), m.dirtyPrototype());
            buf.writeNbt(m.save(h.getLevel().registryAccess()));
            var restored = medium(h);
            restored.load(Objects.requireNonNull(buf.readNbt()), h.getLevel().registryAccess());
            h.assertTrue(restored.matches(m.snapshot()), "Network medium lost fractions");
        } finally {
            buf.release();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldMediumMigratesEntirelyCleanWithoutLoss(GameTestHelper h) {
        var old = new FluidTank(10);
        var stack = enriched(4567);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy culture"));
        old.setFluid(stack);
        var tag = old.writeToNBT(h.getLevel().registryAccess(), new CompoundTag());
        tag.putBoolean("legacyMigrated", true);
        var m = medium(h);
        m.setCapacity(100);
        m.load(tag, h.getLevel().registryAccess());
        h.assertTrue(
                m.cleanAmount() == 4567 && m.dirtyAmount() == 0 && m.overCapacity(),
                "12.1 migration lost/converted overfull fluid");
        same(h, m.cleanPrototype(), stack);
        close(h, m.frequency(COW), 5);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldConflictAndMigrationDiagnosticsSurvive(GameTestHelper h) {
        var old = new FluidTank(10);
        old.setFluid(culture(2000));
        var tag = old.writeToNBT(h.getLevel().registryAccess(), new CompoundTag());
        tag.putString("migrationDiagnostic", "Legacy vat medium conflict");
        var list = new ListTag();
        list.add(water(1000).save(h.getLevel().registryAccess()));
        tag.put("reconnectionConflicts", list);
        var m = medium(h);
        m.load(tag, h.getLevel().registryAccess());
        h.assertTrue(m.fill(culture(1), EXECUTE) == 0 && m.diagnostics().size() == 2, "Legacy safeguards lost");
        m.drain(2000, EXECUTE);
        same(h, m.drain(1000, EXECUTE), water(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void compatibleReconnectionMergesEachFraction(GameTestHelper h) {
        var a = medium(h);
        var b = medium(h);
        a.fill(culture(1000), EXECUTE);
        b.fill(culture(2000), EXECUTE);
        a.foul(250);
        b.foul(500);
        a.assimilate(b);
        h.assertTrue(a.cleanAmount() == 2250 && a.dirtyAmount() == 750, "Reconnection lost a fraction");
        same(h, a.cleanPrototype(), culture(1));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void conflictingReconnectionRetainsDirtyPairs(GameTestHelper h) {
        var a = medium(h);
        var b = medium(h);
        a.fill(culture(1000), EXECUTE);
        b.fill(water(2000), EXECUTE);
        a.foul(300);
        b.foul(500);
        a.assimilate(b);
        var restored = medium(h);
        restored.load(a.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.fill(culture(1), EXECUTE) == 0, "Conflict safeguard bypassed");
        same(h, restored.drain(1000, EXECUTE), CultureFouling.dirty(culture(1), 100));
        same(h, restored.drain(1000, EXECUTE), culture(1));
        same(h, restored.drain(1000, EXECUTE), CultureFouling.dirty(water(1), 100));
        same(h, restored.drain(2000, EXECUTE), water(1));
        h.assertTrue(restored.isEmpty(), "Conflict fluid lost/duplicated");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtyBucketRetainsExactComposition(GameTestHelper h) {
        var original = CultureFouling.dirty(culture(1), 100).copyWithAmount(1000);
        var bucket = FluidUtil.getFilledBucket(original);
        same(h, original, FluidUtil.getFluidContained(bucket).orElseThrow());
        var tank = new FluidTank(1000);
        h.assertTrue(
                FluidUtil.tryEmptyContainer(bucket, tank, 1000, null, true).isSuccess(),
                "Dirty bucket transfer failed");
        same(h, original, tank.getFluid());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtyBufferPersistenceAndConduitViews(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        var buffers = new ArrayList<FluidBufferEntity>();
        for (var pos : List.of(p.west(), p.east())) {
            h.setBlock(pos, FluidBufferContent.state(ConduitCladding.BARE));
            buffers.add((FluidBufferEntity) h.getBlockEntity(pos));
        }
        var original = CultureFouling.dirty(culture(1), 100).copyWithAmount(750);
        var storage = new PhysicalFluidStorage();
        storage.compile(buffers, ConduitTopology.EMPTY);
        var handler = PhysicalFluidStorage.handler(storage.connected(h.absolutePos(p)), true, s -> true);
        h.assertTrue(handler.fill(original, EXECUTE) == 750, "Dirty fluid buffer fill failed");
        var nbt = buffers.getFirst().tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag());
        var restored = new FluidTank(4000);
        restored.readFromNBT(h.getLevel().registryAccess(), nbt);
        same(h, original, restored.getFluid());
        same(
                h,
                original,
                PhysicalFluidStorage.handler(storage.connected(h.absolutePos(p)), false, s -> true)
                        .drain(750, EXECUTE));
        h.succeed();
    }

    private static void rig(GameTestHelper h, ResourceLocation mob, boolean tool) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(mob);
        Slice11Fixtures.vatStorage(h, MIN, 5, 5);
        Slice11Fixtures.resourceIo(h, MIN);
        h.setBlock(FEED, Content.state(VatPartType.ItemPort));
        Slice11Fixtures.feedFrom(h, FEED, FEED.south());
        if (tool) h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
    }

    private static ProcessContext context(MultiblockVat vat, PassiveMobDefinition d) {
        var profile = PassiveProcessCompiler.compile(d);
        var registry = new MobProcessRegistry();
        registry.register(d.identity(), profile);
        var engine = new VatProcessEngine(vat, registry.freeze());
        engine.assembled();
        var assembly = vat.getAssemblies().getFirst();
        return new ProcessContext(engine, assembly, engine.state(assembly.head().getBlockPos()), profile);
    }

    private static PassiveMobDefinition definition(ResourceLocation mob) {
        return PassiveMobDefinitionRegistry.resolve(mob);
    }

    private static ProcessDefinition operation(ProcessContext c, String prefix) {
        return c.profile().definitions().stream()
                .filter(d -> d.id().startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    private static MultiblockVat vat(GameTestHelper h) {
        var vat = ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
        h.assertTrue(vat.isAssembled(), "Lifecycle fixture unformed");
        return vat;
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void partialExposureInterpolatesMixedFrequency(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            double happiness = vat.yieldMultiplier(h.absolutePos(HEAD));
            vat.medium().fill(enriched(vat.medium().getCapacity() / 2), EXECUTE);
            vat.medium().foul(vat.medium().getFluidAmount() / 2);
            double exposure = vat.specimenClearance(h.absolutePos(HEAD)).shell().stream()
                    .mapToDouble(vat.medium()::exposure)
                    .average()
                    .orElseThrow();
            close(h, vat.frequencyMultiplier(h.absolutePos(HEAD)), .5 + exposure * (3.875 - .5));
            close(h, vat.yieldMultiplier(h.absolutePos(HEAD)), happiness);
            h.assertTrue(exposure > 0 && exposure < 1, "Fixture lacked partial exposure");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void completedGrowthFoulsExactlyOnce(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var c = context(vat, definition(COW));
            c.state().stage(LifeStage.JUVENILE);
            var growth = c.state().growth();
            growth.start();
            growth.elapsed = definition(COW).juvenile_growth_ticks() - 1;
            var op = operation(c, "00_growth");
            op.execute(c, growth);
            op.execute(c, growth);
            h.assertTrue(
                    c.state().stage() == LifeStage.ADULT
                            && vat.medium().dirtyAmount() == 50
                            && vat.metrics().total("foulingEvents") == 1,
                    "Growth did not foul once");
            for (int i = 0; i < 5; i++) op.execute(c, growth);
            h.assertTrue(vat.medium().dirtyAmount() == 50, "Ready growth fouled repeatedly");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void successfulPassiveOutputFoulsOrdinaryAtOneTimes(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(vat.medium().getCapacity()), EXECUTE);
            var c = context(vat, definition(COW));
            var op = operation(c, "10_output_0");
            var state = c.state().process(op.stateId());
            state.phase = ProcessState.Phase.READY;
            h.assertTrue(
                    op.execute(c, state).reason() == ProcessResult.Reason.COMMITTED
                            && vat.medium().dirtyAmount() == 50
                            && vat.metrics().total("cultureFouledMb") == 50,
                    "Ordinary passive output did not foul");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void blockedPassiveOutputDoesNotFoul(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var c = context(vat, definition(COW));
            for (var cluster : vat.storage().clusters()) {
                var tank = cluster.tank();
                if (tank.isFluidValid(new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), 1)))
                    tank.setFluid(
                            new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), tank.getCapacity()));
            }
            for (var tank : vat.storage()
                    .outputTanks(
                            c.assembly().drain().getBlockPos(),
                            new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), 1)))
                tank.setFluid(new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), tank.getCapacity()));
            var op = operation(c, "10_output_0");
            var state = c.state().process(op.stateId());
            state.phase = ProcessState.Phase.READY;
            h.assertTrue(
                    op.execute(c, state).reason() == ProcessResult.Reason.OUTPUT_BLOCKED
                            && vat.medium().dirtyAmount() == 0
                            && vat.metrics().total("foulingEvents") == 0,
                    "Blocked passive output fouled");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void dryUnrecognizedAndFullyDirtyActionsRemainValid(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            var c = context(vat, definition(COW));
            h.assertTrue(
                    Fouling.commit(new ProcessTransaction(), c, "mobvats:milk")
                            && vat.medium().isEmpty(),
                    "Dry fouling created fluid/blocked");
            vat.medium().setFluid(new FluidStack(ProductFluids.MEAT.get(), 1000));
            h.assertTrue(
                    Fouling.commit(new ProcessTransaction(), c, "mobvats:milk")
                            && vat.medium().dirtyAmount() == 0,
                    "Unrecognized fluid fouled");
            vat.medium().setFluid(water(25));
            Fouling.commit(new ProcessTransaction(), c, "mobvats:milk");
            h.assertTrue(
                    vat.medium().dirtyAmount() == 25 && vat.metrics().total("cultureFouledMb") == 25,
                    "Final partial action failed");
            Fouling.commit(new ProcessTransaction(), c, "mobvats:milk");
            h.assertTrue(vat.metrics().total("foulingEvents") == 1, "No-op counted as fouling event");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void configuredBiologicalProcessFoulsOnFinishOnly(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(enriched(1000), EXECUTE);
            var p = new PassiveMobDefinition.ResourceProcess(
                    "test:bio",
                    1,
                    StageRequirement.ANY,
                    "",
                    List.of(),
                    List.of(new PassiveMobDefinition.ResourceOutput("item", null, null, "minecraft:egg", 1, 0)),
                    true);
            var d = new PassiveMobDefinition(
                    COW.toString(), null, 0, List.of(), null, null, null, null, Map.of(), List.of(p));
            var c = context(vat, d);
            var runtime = new ResourceProcessRuntime(p);
            var state = c.state().process(p.id());
            runtime.start(c, state);
            h.assertTrue(vat.medium().dirtyAmount() == 0, "Starting process fouled");
            state.phase = ProcessState.Phase.READY;
            h.assertTrue(
                    runtime.finish(c, state).reason() == ProcessResult.Reason.COMMITTED
                            && vat.medium().dirtyAmount() == 50,
                    "Generic biological finish did not foul");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void mechanicalProcessDoesNotFoul(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var p = new PassiveMobDefinition.ResourceProcess(
                    "test:mechanical", 1, StageRequirement.ANY, "", List.of(), List.of(), false);
            var c = context(
                    vat,
                    new PassiveMobDefinition(
                            COW.toString(), null, 0, List.of(), null, null, null, null, Map.of(), List.of(p)));
            h.assertTrue(
                    new ResourceProcessRuntime(p)
                                            .finish(c, c.state().process(p.id()))
                                            .reason()
                                    == ProcessResult.Reason.COMMITTED
                            && vat.medium().dirtyAmount() == 0,
                    "Mechanical finish fouled");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void configuredPerActionOverrideCommitsExactVolume(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var c = context(vat, definition(COW));
            var before = CultureProfiles.snapshot();
            try {
                CultureProfiles.publish(new CultureProfiles.Data(
                        before.profiles(),
                        before.mobs(),
                        before.groups(),
                        before.defaultProfile(),
                        100,
                        50,
                        Map.of("test:override", 25)));
                h.assertTrue(
                        Fouling.commit(new ProcessTransaction(), c, "test:override")
                                && vat.medium().dirtyAmount() == 25,
                        "Per-action override ignored");
            } finally {
                CultureProfiles.publish(before);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void failedBiologicalTransactionDoesNotCountFouling(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var c = context(vat, definition(COW));
            h.assertTrue(
                    !Fouling.commit(
                                    new ProcessTransaction()
                                            .add(new ProcessTransaction.Step(
                                                    new Object(), () -> true, () -> false, () -> {})),
                                    c,
                                    "mobvats:milk")
                            && vat.medium().dirtyAmount() == 0
                            && vat.metrics().total("foulingEvents") == 0,
                    "Failed work counted as fouling");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void sheepRegrowthFoulsOnceAfterFeedCommit(GameTestHelper h) {
        var sheep = ResourceLocation.withDefaultNamespace("sheep");
        rig(h, sheep, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var c = context(vat, definition(sheep));
            var head = c.assembly().head();
            var nbt = head.specimenState();
            nbt.putBoolean("Sheared", true);
            head.updateSpecimenState(nbt);
            var state = c.state().process(PassiveUses.SHEAR);
            state.elapsed = 1200;
            h.assertTrue(
                    SecondaryToolOperations.shear(c, state).reason() == ProcessResult.Reason.WAITING_INPUT
                            && vat.medium().dirtyAmount() == 0,
                    "Unfed regrowth fouled");
            ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
            h.assertTrue(
                    SecondaryToolOperations.shear(c, state).reason() == ProcessResult.Reason.COMMITTED
                            && !head.specimenState().getBoolean("Sheared")
                            && vat.medium().dirtyAmount() == 50,
                    "Regrowth failed fouling commit");
            SecondaryToolOperations.shear(c, state);
            h.assertTrue(vat.medium().dirtyAmount() == 50, "Regrowth fouled repeatedly");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void toolStabsAndReplacementStartDoNotFoul(GameTestHelper h) {
        rig(h, COW, true);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var tool = (VatToolPortEntity) h.getBlockEntity(TOOL);
            tool.setHeldItem(new ItemStack(Items.WOODEN_SWORD));
            var c = context(vat, definition(COW));
            ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
            var op = c.profile().definitions().stream()
                    .filter(d -> d.id().endsWith("mobvats:weapon_harvest"))
                    .findFirst()
                    .orElseThrow();
            var state = c.state().process(op.stateId());
            var result = op.execute(c, state);
            h.assertTrue(
                    result.reason() == ProcessResult.Reason.COMMITTED
                            && tool.heldItem().getDamageValue() > 0
                            && vat.medium().dirtyAmount() == 0,
                    "Tool strike fouled or did not happen: " + result + " / routes="
                            + c.routes(PassiveUses.HARVEST).size() + " / feeds="
                            + c.itemInputs().size() + " / available=" + tool.available() + " / turn="
                            + c.engine().toolTurn(tool, PassiveUses.HARVEST, c));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void connectedOutputBucketsObserveValveOrdering(GameTestHelper h) {
        rig(h, COW, false);
        var portPos = MIN.offset(0, 1, 2);
        h.setBlock(portPos, Content.state(VatPartType.FluidPort));
        var port = (VatFluidPortEntity) h.getBlockEntity(portPos);
        port.setIoDirection(IoDirection.Output);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            var clean = culture(2000);
            vat.medium().fill(clean, EXECUTE);
            vat.medium().foul(1000);
            var dirty = vat.medium().dirtyPrototype();
            var destination = vat.storage().connected(h.absolutePos(portPos)).stream()
                    .filter(t -> t.isFluidValid(dirty))
                    .findFirst()
                    .orElseThrow();
            var e = valve(h, VatPartType.DrainValve);
            h.assertTrue(BoundaryDevices.drain(vat.medium(), destination, e), "Dirty valve export failed");
            var player = h.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            h.assertTrue(port.interact(player, InteractionHand.MAIN_HAND), "Dirty output bucket failed");
            same(h, FluidUtil.getFluidContained(player.getMainHandItem()).orElseThrow(), dirty);
            h.assertTrue(BoundaryDevices.drain(vat.medium(), destination, e), "Clean valve tail failed");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            h.assertTrue(port.interact(player, InteractionHand.MAIN_HAND), "Clean output bucket failed");
            same(h, FluidUtil.getFluidContained(player.getMainHandItem()).orElseThrow(), clean);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void dirtyWorldPlacementRejectedWithoutLoss(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        var e = valve(h, VatPartType.PushPump);
        var root = h.absolutePos(p);
        for (var n : List.of(p.below(), p.west(), p.east(), p.north(), p.south())) h.setBlock(n, Blocks.STONE);
        var tank = new FluidTank(1000);
        var original = CultureFouling.dirty(culture(1), 100).copyWithAmount(1000);
        tank.setFluid(original);
        h.assertTrue(
                !BoundaryDevices.push(h.getLevel(), root, tank, e)
                        && tank.getFluidAmount() == 1000
                        && h.getBlockState(p).isAir(),
                "Dirty placement lost contents");
        same(h, tank.getFluid(), original);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void filterBatchNeverBlendsDifferentSourceFormulas(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            var first = CultureFouling.dirty(water(1), 100).copyWithAmount(500);
            var second = CultureFouling.dirty(culture(1), 100).copyWithAmount(1000);
            var state = new MixerState(() -> {});
            state.fluids(true).fill(first, EXECUTE);
            state.fluids(true).fill(second, EXECUTE);
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            h.assertTrue(
                    state.commit(loaded.recipes().get("mobvats:filter_general")),
                    "An earlier partial unrelated formula starved a complete batch");
            var result = state.fluids(false).drain(culture(1000), EXECUTE);
            same(h, result, culture(1));
            h.assertTrue(result.getAmount() == 1000, "Filter blended formulas");
            h.assertTrue(
                    state.fluids(false).drain(first, EXECUTE).getAmount() == 500,
                    "Partial unrelated dirty culture was consumed");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialDifferentFormulasCannotCombineForFilterBatch(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            state.fluids(true).fill(CultureFouling.dirty(water(1), 100).copyWithAmount(500), EXECUTE);
            state.fluids(true).fill(CultureFouling.dirty(culture(1), 100).copyWithAmount(500), EXECUTE);
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            h.assertTrue(
                    !state.commit(loaded.recipes().get("mobvats:filter_general"))
                            && state.plantCount() == 1
                            && state.amount(2) == 1000,
                    "Filter combined unrelated dirty formulas");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void filteredOrdinaryOutputEligibilitySurvivesReload(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            h.assertTrue(!state.outputFluid(water(1)), "Raw input was initially treated as a filter output");
            state.fluids(true).fill(CultureFouling.dirty(water(1), 100).copyWithAmount(1000), EXECUTE);
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            h.assertTrue(state.commit(loaded.recipes().get("mobvats:filter_general")), "Raw filtration failed");
            var restored = new MixerState(() -> {});
            restored.load(
                    state.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            var result = restored.fluids(false).drain(1000, EXECUTE);
            same(h, result, water(1));
            h.assertTrue(result.getAmount() == 1000, "Restored raw output could not drain after reload");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidFilterReagentIsActuallyConsumed(GameTestHelper h) {
        var loaded = MixerRecipeRegistry.parse(
                JsonParser.parseString(
                        "{\"recipes\":{\"test:fluid_filter\":{\"fluids\":[{\"selector\":\"mobvats:culture_slurry\",\"amount\":1000},{\"selector\":\"mobvats:meat\",\"amount\":25,\"record_in_composition\":false}],\"output\":\"mobvats:culture_slurry\",\"output_amount\":1000,\"batch_ticks\":1,\"transform\":{\"remove\":[{\"kind\":\"FLUID\",\"selector\":\"mobvats:waste\"}]}}}}"));
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            state.fluids(true).fill(CultureFouling.dirty(culture(1), 100).copyWithAmount(1000), EXECUTE);
            state.fluids(true).fill(new FluidStack(ProductFluids.MEAT.get(), 25), EXECUTE);
            h.assertTrue(state.commit(loaded.recipes().get("test:fluid_filter")), "Fluid reagent batch failed");
            same(h, state.fluids(false).drain(1000, EXECUTE), culture(1));
            h.assertTrue(
                    java.util.stream.IntStream.range(0, state.fluids(true).getTanks())
                            .mapToObj(state.fluids(true)::getFluidInTank)
                            .noneMatch(s -> s.is(ProductFluids.MEAT.get()) && s.getAmount() > 0),
                    "Fluid filter reagent not consumed");
        });
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void resourceDefinitionFoulingMbOverridesActionFallback(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var p = new PassiveMobDefinition.ResourceProcess(
                    "test:override", 1, StageRequirement.ANY, "", List.of(), List.of(), true, 100);
            var c = context(
                    vat,
                    new PassiveMobDefinition(
                            COW.toString(), null, 0, List.of(), null, null, null, null, Map.of(), List.of(p)));
            h.assertTrue(
                    new ResourceProcessRuntime(p)
                                            .finish(c, c.state().process(p.id()))
                                            .reason()
                                    == ProcessResult.Reason.COMMITTED
                            && vat.medium().dirtyAmount() == 100,
                    "Definition fouling_mb ignored");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void definitionJsonAcceptsOptionalFoulingAndRejectsNegative(GameTestHelper h) throws Exception {
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"), "slice122-process-");
        java.nio.file.Files.writeString(
                directory.resolve("process.json"),
                "{\"minecraft:cow\":{\"entity\":\"minecraft:cow\",\"processes\":[{\"id\":\"test:bio\",\"duration_ticks\":1,\"accelerated\":true,\"fouling_mb\":25,\"outputs\":[{\"type\":\"item\",\"item\":\"minecraft:egg\",\"count\":1}]}]}}");
        var loaded = PassiveMobDefinitionRegistry.load(directory, s -> {});
        h.assertTrue(
                loaded.errors().isEmpty()
                        && loaded.definitions().get(COW).processes().getFirst().fouling_mb() == 25,
                "Definition JSON override rejected");
        java.nio.file.Files.writeString(
                directory.resolve("process.json"),
                "{\"minecraft:cow\":{\"entity\":\"minecraft:cow\",\"processes\":[{\"id\":\"test:bio\",\"duration_ticks\":1,\"accelerated\":true,\"fouling_mb\":-1,\"outputs\":[{\"type\":\"item\",\"item\":\"minecraft:egg\",\"count\":1}]}]}}");
        h.assertTrue(
                PassiveMobDefinitionRegistry.load(directory, s -> {}).errors().size() == 1,
                "Negative definition fouling accepted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void existingMixerDefaultsAndAuthoredDefaultsSurvive(GameTestHelper h) throws Exception {
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"), "slice122-mixer-");
        var old = new JsonObject();
        var recipes = new JsonObject();
        old.add("recipes", recipes);
        old.add("names", new JsonArray());
        try (var in = Slice122GameTests.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")) {
            old = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        old.getAsJsonObject("recipes")
                .entrySet()
                .removeIf(e -> e.getValue().getAsJsonObject().has("transform"));
        java.nio.file.Files.writeString(directory.resolve("_defaults.json"), old.toString());
        var loaded = MixerRecipeRegistry.load(directory, s -> {});
        h.assertTrue(
                java.nio.file.Files.readString(directory.resolve("_defaults.json"))
                                .equals(old.toString())
                        && loaded.errors().isEmpty(),
                "Existing defaults were rewritten");
        old.getAsJsonObject("recipes").getAsJsonObject("culture_slurry").addProperty("batch_ticks", 777);
        String authored = old.toString();
        java.nio.file.Files.writeString(directory.resolve("_defaults.json"), authored);
        MixerRecipeRegistry.load(directory, s -> {});
        h.assertTrue(
                java.nio.file.Files.readString(directory.resolve("_defaults.json"))
                        .equals(authored),
                "User-authored defaults overwritten");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vatDrainFilterFillCycleConservesCulture(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            var vat = medium(h);
            var clean = culture(4000);
            vat.fill(clean, EXECUTE);
            vat.foul(1000);
            var mixer = new MixerState(() -> {});
            h.assertTrue(
                    BoundaryDevices.drain(vat, mixer.fluids(true), valve(h, VatPartType.DrainValve))
                            && vat.getFluidAmount() == 3000,
                    "Cycle failed dirty drain");
            mixer.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            h.assertTrue(mixer.commit(loaded.recipes().get("mobvats:filter_general")), "Cycle filter failed");
            h.assertTrue(
                    BoundaryDevices.fill(vat, mixer.fluids(false), valve(h, VatPartType.FillValve))
                            && vat.cleanAmount() == 4000
                            && vat.dirtyAmount() == 0,
                    "Cycle did not restore clean volume");
            same(h, vat.cleanPrototype(), clean);
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void filterTimingDoesNotConsumeBeforeConfiguredCompletion(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            state.fluids(true).fill(CultureFouling.dirty(culture(1), 100).copyWithAmount(1000), EXECUTE);
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            int duration = loaded.recipes().get("mobvats:filter_general").batchTicks();
            for (int i = 10; i < duration; i += 10) state.cadence();
            h.assertTrue(
                    state.progress() == duration - 10
                            && state.plantCount() == 1
                            && state.metrics().total("cultureFilteredMb") == 0,
                    "Filter consumed/counted before completion");
            state.cadence();
            h.assertTrue(
                    state.plantCount() == 0 && state.metrics().total("cultureFilteredMb") == 1000,
                    "Configured filter timing ignored");
            same(h, state.fluids(false).drain(1000, EXECUTE), culture(1));
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedFilterPreservesDirtyVolumeAndFilter(GameTestHelper h) {
        var loaded = legacyDefinitions();
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            var dirty = CultureFouling.dirty(culture(1), 100).copyWithAmount(1000);
            state.fluids(true).fill(dirty, EXECUTE);
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            var tag = state.save(h.getLevel().registryAccess());
            var full = new FluidTank(8000);
            full.setFluid(culture(8000));
            tag.getList("escrow", Tag.TAG_COMPOUND)
                    .add(full.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
            state.load(tag, h.getLevel().registryAccess());
            h.assertTrue(
                    !state.commit(loaded.recipes().get("mobvats:filter_general"))
                            && state.plantCount() == 1
                            && state.metrics().total("cultureFilteredMb") == 0,
                    "Blocked filter consumed material/counts");
            h.assertTrue(
                    state.fluids(false).drain(dirty, SIMULATE).getAmount() == 1000, "Blocked filter lost dirty medium");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void splitPhysicalBuffersFilterWithoutDuplicatingFormula(GameTestHelper h) {
        var loaded = legacyDefinitions();
        var p = new BlockPos(3, 2, 3);
        var members = new ArrayList<FluidBufferEntity>();
        for (var pos : List.of(p.west(), p.east())) {
            h.setBlock(pos, FluidBufferContent.state(ConduitCladding.BARE));
            members.add((FluidBufferEntity) h.getBlockEntity(pos));
        }
        var dirty = CultureFouling.dirty(culture(1), 100);
        members.getFirst().tank.setFluid(dirty.copyWithAmount(300));
        members.getLast().tank.setFluid(dirty.copyWithAmount(700));
        var storage = new PhysicalFluidStorage();
        storage.compile(members, ConduitTopology.EMPTY);
        storage.services(Map.of(h.absolutePos(p), new ServiceStaging(() -> {})));
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            state.bind(storage, h.absolutePos(p));
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            h.assertTrue(state.commit(loaded.recipes().get("mobvats:filter_general")), "Split-buffer filter failed");
            var output = state.fluids(false).drain(culture(1000), EXECUTE);
            same(h, output, culture(1));
            h.assertTrue(
                    output.getAmount() == 1000 && state.metrics().total("cultureFilteredMb") == 1000,
                    "Physical formula/volume duplicated");
        });
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void blockedConfiguredProcessDoesNotFoul(GameTestHelper h) {
        rig(h, COW, false);
        h.runAtTickTime(35, () -> {
            var vat = vat(h);
            vat.medium().fill(water(1000), EXECUTE);
            var p = new PassiveMobDefinition.ResourceProcess(
                    "test:blocked",
                    1,
                    StageRequirement.ANY,
                    "",
                    List.of(),
                    List.of(new PassiveMobDefinition.ResourceOutput("item", null, null, "minecraft:egg", 9999, 0)),
                    true);
            var c = context(
                    vat,
                    new PassiveMobDefinition(
                            COW.toString(), null, 0, List.of(), null, null, null, null, Map.of(), List.of(p)));
            var state = c.state().process(p.id());
            state.phase = ProcessState.Phase.READY;
            h.assertTrue(
                    new ResourceProcessRuntime(p).finish(c, state).reason() == ProcessResult.Reason.OUTPUT_BLOCKED
                            && vat.medium().dirtyAmount() == 0
                            && vat.metrics().total("foulingEvents") == 0,
                    "Blocked configured work fouled");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sameFamilyDifferentAdditivesRemainIncompatible(GameTestHelper h) {
        var vat = medium(h);
        var clean = culture(1000);
        vat.fill(clean, EXECUTE);
        var other = clean.copy();
        var entries =
                new ArrayList<>(other.get(CompoundFluids.COMPOSITION.get()).entries());
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:diamond", 1));
        other.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        h.assertTrue(
                vat.fill(other, EXECUTE) == 0
                        && vat.fill(CultureFouling.dirty(other, 100).copyWithAmount(1000), EXECUTE) == 0
                        && vat.cleanAmount() == 1000
                        && vat.dirtyAmount() == 0,
                "Same family formulas blended");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void singleFluidComponentFamilySurvivesFilterAndRefill(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.COMPONENT_FLUID.get(), 1000);
        clean.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 1000))));
        var vat = medium(h);
        vat.fill(clean, EXECUTE);
        vat.foul(500);
        var filtered = filter(h, clean, "mobvats:filter_general");
        same(h, filtered, clean);
        h.assertTrue(
                vat.fill(filtered.copyWithAmount(100), EXECUTE) == 100,
                "Filtered compound could not refill its selected culture");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtySingleFluidCompoundWithDifferentFormulaBasisCanEstablishPair(GameTestHelper h) {
        var clean = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1);
        clean.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 500))));
        var dirty = CultureFouling.dirty(clean, 100);
        var vat = medium(h);
        h.assertTrue(
                vat.fill(dirty.copyWithAmount(1000), EXECUTE) == 1000,
                "Valid non-wrapper compound dirty fill rejected");
        same(h, vat.cleanPrototype(), clean);
        same(h, filter(h, clean, "mobvats:filter_general"), clean);
        h.succeed();
    }
}
