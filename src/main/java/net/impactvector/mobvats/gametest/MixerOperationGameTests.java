package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.google.gson.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerOperationGameTests {
    private static final String MIXED = """
        {"priority":10,"batch_ticks":200,"strategies":[{"id":"mixed","items":[
          {"selector":[{"item":"minecraft:carrot"},{"item":"minecraft:potato"}],"amount":1},
          {"selector":"minecraft:carrot","amount":1}],"fluids":[
          {"selector":[{"fluid":"minecraft:water"},{"fluid":"mobvats:waste"}],"amount":100},
          {"selector":"minecraft:water","amount":100}]}],"outputs":{"items":[
          {"item":"minecraft:iron_ingot","amount":2},{"item":"minecraft:gold_ingot","amount":3}],"fluids":[
          {"fluid":"mobvats:culture_slurry","amount":100},{"fluid":"mobvats:feeding_slurry","amount":200}]}}
        """;

    private static JsonObject recipe(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static MixerRecipeRegistry.Loaded definitions(Map<String, JsonObject> recipes) {
        var root = new JsonObject();
        var values = new JsonObject();
        recipes.forEach(values::add);
        root.add("recipes", values);
        return MixerRecipeRegistry.parse(root);
    }

    private static MixerRecipeRegistry.Loaded one(JsonObject recipe) {
        return definitions(Map.of("test:operation", recipe));
    }

    private static void scoped(MixerRecipeRegistry.Loaded definitions, Runnable action) {
        var saved = MixerRecipeRegistry.snapshot();
        try {
            MixerRecipeRegistry.publish(definitions);
            action.run();
        } finally {
            MixerRecipeRegistry.publish(saved);
        }
    }

    private static MixerState state() {
        return new MixerState(() -> {});
    }

    private static JsonObject simple() {
        return recipe("""
        {"batch_ticks":200,"strategies":[{"id":"default","items":[{"selector":"minecraft:carrot","amount":1}]}],
         "outputs":{"items":[{"item":"minecraft:iron_ingot","amount":1}]}}
        """);
    }

    private static void fillMixed(MixerState s) {
        s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
        s.itemInput().insertItem(1, new ItemStack(Items.POTATO), false);
        s.fluids(true).fill(new FluidStack(Fluids.WATER, 100), EXECUTE);
        s.fluids(true).fill(new FluidStack(ProductFluids.WASTE.get(), 100), EXECUTE);
    }

    private static int outputItems(MixerState s) {
        var out = s.itemPort(false, v -> true);
        int n = 0;
        for (int i = 0; i < out.getSlots(); i++) n += out.getStackInSlot(i).getCount();
        return n;
    }

    private static net.minecraft.nbt.CompoundTag resources(GameTestHelper h, MixerState s) {
        var tag = s.save(h.getLevel().registryAccess());
        tag.remove("blocked");
        return tag;
    }

    private static void invalid(GameTestHelper h, JsonObject recipe) {
        boolean rejected = false;
        try {
            one(recipe);
        } catch (RuntimeException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "Invalid operation accepted");
    }

    @GameTest(template = "empty")
    public static void authoredRecipePriorityAndIdOrder(GameTestHelper h) {
        var a = simple();
        a.addProperty("priority", 50);
        var z = simple();
        z.addProperty("priority", 50);
        var low = simple();
        low.addProperty("priority", -1000);
        scoped(definitions(Map.of("test:z", z, "test:a", a, "test:low", low)), () -> {
            h.assertTrue(
                    MixerRecipeRegistry.recipes().stream()
                            .map(MixerRecipeDefinition::id)
                            .toList()
                            .equals(List.of("test:a", "test:z", "test:low")),
                    "Recipe precedence changed with map order");
            var s = state();
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            s.cadence();
            h.assertTrue(s.recipe().equals("test:a") && s.progress() > 0, "Selection ignored authored priority/id");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void strategyPriorityIdEnabledAndFallback(GameTestHelper h) {
        var r = simple();
        var original = r.getAsJsonArray("strategies").get(0).getAsJsonObject();
        var strategies = new JsonArray();
        for (var id : List.of("z", "a", "low", "disabled", "missing")) {
            var s = original.deepCopy();
            s.addProperty("id", id);
            s.addProperty("priority", id.equals("low") ? -1 : id.equals("disabled") || id.equals("missing") ? 100 : 10);
            if (id.equals("disabled")) s.addProperty("enabled", false);
            if (id.equals("missing"))
                s.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("selector", "minecraft:diamond");
            strategies.add(s);
        }
        r.add("strategies", strategies);
        scoped(one(r), () -> {
            var d = MixerRecipeRegistry.recipe("test:operation");
            h.assertTrue(
                    d.strategies().stream()
                            .map(MixerRecipeDefinition.Strategy::id)
                            .toList()
                            .equals(List.of("disabled", "missing", "a", "z", "low")),
                    "Strategy precedence incorrect");
            var s = state();
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            var reservation = s.prepare(d).orElseThrow();
            h.assertTrue(
                    reservation.strategy().equals("a") && reservation.commit(),
                    "Disabled/missing strategy prevented deterministic fallback");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacySchemaAndNegativeDefaults(GameTestHelper h) {
        try (var in = MixerOperationGameTests.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")) {
            var defaults = MixerRecipeRegistry.parse(JsonParser.parseString(
                    new String(Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            h.assertTrue(
                    defaults.recipes().values().stream().allMatch(r -> r.priority() < 0),
                    "Bundled default outranks pack priority zero");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        var old = recipe("""
            {"items":[{"selector":"minecraft:carrot","amount":1}],"fluids":[{"selector":"minecraft:water","amount":100}],
             "output":"mobvats:feeding_slurry","output_amount":100,"batch_ticks":200}
            """);
        scoped(one(old), () -> {
            var d = MixerRecipeRegistry.recipe("test:operation");
            h.assertTrue(
                    d.priority() == 0
                            && d.strategies().size() == 1
                            && d.fluidOutputs().size() == 1,
                    "Legacy normalization failed");
            var s = state();
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 100), EXECUTE);
            h.assertTrue(s.commit(d) && s.amount(3) == 100, "Legacy execution changed");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedBundlesOverlappingSelectorsAndDiscovery(GameTestHelper h) {
        scoped(one(recipe(MIXED)), () -> {
            var s = state();
            fillMixed(s);
            var d = MixerRecipeRegistry.recipe("test:operation");
            var before = resources(h, s);
            h.assertTrue(s.canCommit(d) && resources(h, s).equals(before), "Planning mutated resources or metrics");
            h.assertTrue(
                    s.commit(d)
                            && s.plantCount() == 0
                            && s.amount(0) == 0
                            && s.amount(1) == 0
                            && s.amount(2) == 100
                            && s.amount(3) == 200
                            && outputItems(s) == 5,
                    "Mixed/overlapping allocation failed");
            h.assertTrue(
                    MixerRecipeRegistry.outputItem(new ItemStack(Items.GOLD_INGOT))
                            && !MixerRecipeRegistry.outputItem(new ItemStack(Items.CARROT))
                            && MixerRecipeRegistry.acceptsItem(new ItemStack(Items.POTATO))
                            && MixerRecipeRegistry.outputFluid(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 1))
                            && !MixerRecipeRegistry.outputFluid(new FluidStack(Fluids.WATER, 1)),
                    "Output identity discovery exported inputs or missed outputs");

            // Compound outputs carry derived components; inspect using the exact identity exposed by the port.
            var fluids = s.fluids(false);
            var compound = java.util.stream.IntStream.range(0, fluids.getTanks())
                    .mapToObj(fluids::getFluidInTank)
                    .filter(f -> f.is(ProductFluids.CULTURE_SLURRY.get()))
                    .findFirst()
                    .orElseThrow();
            h.assertTrue(compound.get(CompoundFluids.COMPOSITION.get()) != null, "Derived compound composition lost");
            var loaded = state();
            loaded.load(s.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(
                    outputItems(loaded) == 5 && loaded.amount(2) == 100 && loaded.amount(3) == 200,
                    "Mixed outputs lost on persistence");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lateFluidOutputBlockDoesNotProduceItemsOrConsumeInputs(GameTestHelper h) {
        var r = recipe(MIXED);
        r.getAsJsonObject("outputs")
                .getAsJsonArray("fluids")
                .get(1)
                .getAsJsonObject()
                .addProperty("amount", 8001);
        scoped(one(r), () -> {
            var s = state();
            fillMixed(s);
            var before = resources(h, s);
            h.assertTrue(
                    !s.commit(MixerRecipeRegistry.recipe("test:operation"))
                            && before.equals(resources(h, s))
                            && outputItems(s) == 0,
                    "Blocked final fluid output partially mutated operation");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lateItemOutputBlockIsAtomic(GameTestHelper h) {
        var r = recipe(MIXED);
        r.getAsJsonObject("outputs")
                .getAsJsonArray("items")
                .get(1)
                .getAsJsonObject()
                .addProperty("amount", 257);
        scoped(one(r), () -> {
            var s = state();
            fillMixed(s);
            var before = resources(h, s);
            h.assertTrue(
                    !s.commit(MixerRecipeRegistry.recipe("test:operation")) && before.equals(resources(h, s)),
                    "Blocked item output partially consumed or produced resources");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedOutputCapacityAccumulatesAcrossDeclarations(GameTestHelper h) {
        var r = recipe(MIXED);
        var outputs = r.getAsJsonObject("outputs").getAsJsonArray("fluids");
        for (var f : outputs) {
            f.getAsJsonObject().addProperty("fluid", "mobvats:meat");
            f.getAsJsonObject().addProperty("amount", 4500);
        }
        scoped(one(r), () -> {
            var s = state();
            fillMixed(s);
            var before = resources(h, s);
            h.assertTrue(
                    !s.commit(MixerRecipeRegistry.recipe("test:operation")) && before.equals(resources(h, s)),
                    "Output declarations independently overbooked one destination");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void staleItemFluidAndRegistryReservationsReject(GameTestHelper h) {
        var loaded = one(recipe(MIXED));
        scoped(loaded, () -> {
            var s = state();
            fillMixed(s);
            var d = MixerRecipeRegistry.recipe("test:operation");
            var p = s.prepare(d).orElseThrow();
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            var before = resources(h, s);
            h.assertTrue(
                    !p.commit() && before.equals(resources(h, s)) && !p.commit(),
                    "Stale item reservation committed/reused");
            p = s.prepare(d).orElseThrow();
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 1), EXECUTE);
            before = resources(h, s);
            h.assertTrue(!p.commit() && before.equals(resources(h, s)), "Stale fluid reservation mutated resources");
            p = s.prepare(d).orElseThrow();
            MixerRecipeRegistry.publish(one(recipe(MIXED)));
            before = resources(h, s);
            h.assertTrue(!p.commit() && before.equals(resources(h, s)), "Reload left an old reservation executable");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void staleOutputAndSingleUseReservationsReject(GameTestHelper h) {
        scoped(one(simple()), () -> {
            var s = state();
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT, 3), false);
            var d = MixerRecipeRegistry.recipe("test:operation");
            h.assertTrue(s.commit(d), "Initial item production failed");
            var p = s.prepare(d).orElseThrow();
            s.itemPort(false, v -> true).extractItem(0, 1, false);
            var before = resources(h, s);
            h.assertTrue(
                    !p.commit() && before.equals(resources(h, s)),
                    "Changed output inventory did not invalidate reservation");
            p = s.prepare(d).orElseThrow();
            h.assertTrue(
                    p.commit() && !p.commit() && outputItems(s) == 1 && s.plantCount() == 1,
                    "Reservation duplicated operation");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadChangedRemovedActiveRecipeResetsProgress(GameTestHelper h) {
        scoped(one(simple()), () -> {
            var s = state();
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            s.cadence();
            s.cadence();
            s.cadence();
            h.assertTrue(s.progress() > 20, "Fixture did not accumulate progress");
            var saved = s.save(h.getLevel().registryAccess());
            var changed = simple();
            changed.addProperty("batch_ticks", 400);
            MixerRecipeRegistry.publish(one(changed));
            s.cadence();
            h.assertTrue(
                    s.progress() == 10 && s.plantCount() == 1 && outputItems(s) == 0,
                    "Changed recipe inherited old progress/committed early");
            var restored = state();
            restored.load(saved, h.getLevel().registryAccess());
            h.assertTrue(restored.progress() == 0, "Saved progress rebound to changed operation");
            MixerRecipeRegistry.publish(definitions(Map.of()));
            s.cadence();
            h.assertTrue(
                    s.recipe().isEmpty() && s.progress() == 0 && s.plantCount() == 1 && outputItems(s) == 0,
                    "Removed active recipe consumed or retained progress");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void v2TransformOnOneOutputPreservesSourceIdentity(GameTestHelper h) {
        var r = recipe("""
            {"batch_ticks":200,"strategies":[{"id":"filter","items":[{"selector":"minecraft:charcoal","amount":1,"record_in_composition":false}],
              "fluids":[{"selector":[{"fluid":"mobvats:culture_slurry"},{"fluid":"mobvats:component_fluid"}],"amount":1000}]}],
             "outputs":{"items":[{"item":"minecraft:iron_ingot","amount":1}],"fluids":[
              {"fluid":"mobvats:culture_slurry","amount":1000,"transform":{"source_input":0,"remove":[{"kind":"FLUID","selector":"mobvats:waste"}],"restore_ordinary":true}},
              {"fluid":"mobvats:meat","amount":10}]}}
            """);
        scoped(one(r), () -> {
            var clean = new FluidStack(Fluids.WATER, 1000);
            clean.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved water"));
            var dirty = CultureFouling.dirty(clean, 100).copyWithAmount(1000);
            var s = state();
            s.fluids(true).fill(dirty, EXECUTE);
            s.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            h.assertTrue(!s.outputFluid(clean), "Unproduced ordinary input exposed as output");
            h.assertTrue(
                    s.commit(MixerRecipeRegistry.recipe("test:operation"))
                            && outputItems(s) == 1
                            && s.outputFluid(clean),
                    "Mixed transform operation failed");
            var actual = s.fluids(false).drain(clean, SIMULATE);
            h.assertTrue(
                    actual.getAmount() == 1000 && FluidStack.isSameFluidSameComponents(actual, clean),
                    "Transform source components or ordinary restoration changed");
            h.assertTrue(
                    s.fluids(false)
                                    .drain(new FluidStack(ProductFluids.MEAT.get(), 10), SIMULATE)
                                    .getAmount()
                            == 10,
                    "Transform incorrectly applied to second output");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidV2DefinitionsRejectAtomically(GameTestHelper h) {
        var r = simple();
        r.addProperty("priority", 1.5);
        invalid(h, r);
        r = simple();
        r.getAsJsonArray("strategies").add(r.getAsJsonArray("strategies").get(0).deepCopy());
        invalid(h, r);
        r = simple();
        r.addProperty("output", "minecraft:water");
        invalid(h, r);
        r = simple();
        r.getAsJsonObject("outputs").add("items", new JsonArray());
        invalid(h, r);
        r = simple();
        r.getAsJsonObject("outputs")
                .getAsJsonArray("items")
                .get(0)
                .getAsJsonObject()
                .addProperty("item", "test:missing");
        invalid(h, r);
        r = simple();
        r.getAsJsonObject("outputs")
                .getAsJsonArray("items")
                .get(0)
                .getAsJsonObject()
                .addProperty("amount", 0);
        invalid(h, r);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void physicalServiceOutputPlanningIsAtomicAndNonMutating(GameTestHelper h) {
        var r = simple();
        r.getAsJsonObject("outputs")
                .add(
                        "fluids",
                        JsonParser.parseString(
                                        "[{\"fluid\":\"mobvats:meat\",\"amount\":100},{\"fluid\":\"mobvats:waste\",\"amount\":1001}]")
                                .getAsJsonArray());
        scoped(one(r), () -> {
            var service = new net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging(() -> {});
            var storage = new net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage();
            storage.services(Map.of(net.minecraft.core.BlockPos.ZERO, service));
            var s = state();
            s.bind(storage, net.minecraft.core.BlockPos.ZERO);
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            var before = resources(h, s);
            h.assertTrue(
                    !s.commit(MixerRecipeRegistry.recipe("test:operation"))
                            && service.tanks().isEmpty()
                            && before.equals(resources(h, s)),
                    "Blocked operation created service tanks or mutated resources");
            r.getAsJsonObject("outputs")
                    .getAsJsonArray("fluids")
                    .get(1)
                    .getAsJsonObject()
                    .addProperty("amount", 100);
            MixerRecipeRegistry.publish(one(r));
            var d = MixerRecipeRegistry.recipe("test:operation");
            h.assertTrue(s.canCommit(d) && service.tanks().isEmpty(), "Simulation published service tanks");
            var p = s.prepare(d).orElseThrow();
            h.assertTrue(
                    service.tanks().isEmpty()
                            && p.commit()
                            && service.tanks().size() == 2
                            && outputItems(s) == 1
                            && s.amount(1) == 100,
                    "Physical service output bundle failed");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedOwnedReservationRollsBackSetterFailure(GameTestHelper h) {
        var failing = new net.neoforged.neoforge.items.ItemStackHandler(2) {
            boolean fail = true;

            public void setStackInSlot(int slot, ItemStack stack) {
                if (slot == 1 && stack.is(Items.IRON_INGOT) && fail) {
                    fail = false;
                    throw new IllegalStateException("Injected setter failure");
                }
                super.setStackInSlot(slot, stack);
            }
        };
        failing.setStackInSlot(0, new ItemStack(Items.CARROT));
        var resources = new net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceReservation();
        resources.item(failing, 0, ItemStack.EMPTY);
        resources.item(failing, 1, new ItemStack(Items.IRON_INGOT));
        boolean rejected = false;
        try {
            resources.commit();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        h.assertTrue(
                rejected
                        && failing.getStackInSlot(0).is(Items.CARROT)
                        && failing.getStackInSlot(1).isEmpty()
                        && !resources.commit(),
                "Setter failure left a partially mutated transaction");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void knownLegacyDefaultsAndAuthoredDefaultsRemain(GameTestHelper h) throws java.io.IOException {
        JsonObject legacy;
        try (var in = MixerOperationGameTests.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")) {
            legacy = JsonParser.parseString(new String(
                            Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        for (var entry : legacy.getAsJsonObject("recipes").entrySet()) {
            var r = entry.getValue().getAsJsonObject();
            r.remove("priority");
            var s = r.remove("strategies").getAsJsonArray().get(0).getAsJsonObject();
            for (var key : List.of("items", "fluids")) if (s.has(key)) r.add(key, s.get(key));
            var out = r.remove("outputs")
                    .getAsJsonObject()
                    .getAsJsonArray("fluids")
                    .get(0)
                    .getAsJsonObject();
            r.add("output", out.get("fluid"));
            r.add("output_amount", out.get("amount"));
            for (var key : List.of("composition", "transform")) if (out.has(key)) r.add(key, out.get(key));
        }
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "mixer-v2-defaults-");
        var file = directory.resolve("_defaults.json");
        try {
            java.nio.file.Files.writeString(file, legacy.toString());
            var loaded = MixerRecipeRegistry.load(directory, s -> {});
            h.assertTrue(
                    loaded.errors().isEmpty()
                            && loaded.recipes().values().stream().allMatch(r -> r.priority() == 0)
                            && java.nio.file.Files.readString(file).equals(legacy.toString()),
                    "Known v1 defaults were rewritten");
            legacy.getAsJsonObject("recipes").getAsJsonObject("culture_slurry").addProperty("batch_ticks", 777);
            var authored = legacy.toString();
            java.nio.file.Files.writeString(file, authored);
            loaded = MixerRecipeRegistry.load(directory, s -> {});
            h.assertTrue(
                    loaded.errors().isEmpty()
                            && java.nio.file.Files.readString(file).equals(authored),
                    "Authored legacy defaults were overwritten");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void assembledItemOutputPortExportsOnlyProduction(GameTestHelper h) {
        MixerGameTests.rig(h);
        var pos = new net.minecraft.core.BlockPos(3, 2, 5);
        h.setBlock(
                pos,
                net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.state(
                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.ItemPort));
        h.runAtTickTime(
                35,
                () -> scoped(one(simple()), () -> {
                    var port = (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerItemPortEntity)
                            h.getBlockEntity(pos);
                    var controller = port.getMultiblockController().orElseThrow();
                    h.assertTrue(controller.isAssembled(), "Item output fixture did not assemble");
                    var s = controller.state();
                    s.itemInput().insertItem(0, new ItemStack(Items.CARROT, 2), false);
                    h.assertTrue(
                            s.commit(MixerRecipeRegistry.recipe("test:operation")), "Assembled item production failed");
                    port.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
                    port.filter("minecraft:iron_ingot");
                    var cap = h.getLevel()
                            .getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    h.absolutePos(pos),
                                    net.minecraft.core.Direction.SOUTH);
                    h.assertTrue(
                            cap != null
                                    && cap.extractItem(0, 1, true).is(Items.IRON_INGOT)
                                    && outputItems(s) == 1
                                    && cap.extractItem(0, 1, false).is(Items.IRON_INGOT)
                                    && s.plantCount() == 1
                                    && cap.extractItem(0, 1, false).isEmpty(),
                            "Configured output port exported inputs or lost production");
                    h.succeed();
                }));
    }
}
