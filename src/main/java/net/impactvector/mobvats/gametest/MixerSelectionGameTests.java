package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerSelectionGameTests {
    private static MixerMeterRequirements meters(int items, int water) {
        return new MixerMeterRequirements(
                items == 0 ? List.of() : List.of(new ItemMeterRequirement("minecraft:wheat", items)),
                water == 0 ? List.of() : List.of(new FluidMeterRequirement("minecraft:water", water)));
    }

    @GameTest(template = "empty")
    public static void sampledMeterFiltersRetainExistingIdentitySemantics(GameTestHelper h) {
        var item = new ItemStack(Items.WHEAT);
        item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("sample"));
        h.assertTrue(
                ItemMeterRequirement.sampled(item, 4).matches(new ItemStack(Items.WHEAT)),
                "Sampled item filter changed existing item-ID semantics");
        var ordinary = new FluidStack(Fluids.WATER, 1);
        var named = ordinary.copy();
        named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("sample"));
        var exact = FluidMeterRequirement.sampled(named, 1000);
        h.assertTrue(
                exact.matches(named.copyWithAmount(4000)) && !exact.matches(ordinary),
                "Sampled fluid filter lost components or included volume");
        var broad = FluidMeterRequirement.sampled(ordinary, 1000);
        var strategy = new MixerRecipeDefinition.Strategy(
                "test",
                0,
                true,
                List.of(
                        new MixerRecipeDefinition.FluidInput("first", 1000, s -> true),
                        new MixerRecipeDefinition.FluidInput("second", 1000, s -> true)),
                List.of());
        var allocations = List.of(
                List.of(named.copyWithAmount(500), named.copyWithAmount(500)), List.of(ordinary.copyWithAmount(1000)));
        h.assertTrue(
                new MixerMeterRequirements(List.of(), List.of(broad, exact)).matches(strategy, List.of(), allocations),
                "Overlapping filters failed to exchange ingredient slots");
        h.assertTrue(
                new MixerMeterRequirements(List.of(), List.of(exact, broad)).matches(strategy, List.of(), allocations),
                "Meter order changed compatibility");
        h.assertTrue(
                !new MixerMeterRequirements(List.of(), List.of(exact, exact)).matches(strategy, List.of(), allocations),
                "Component-exact fluid meters double-counted consumption");
        var mixed = List.of(
                List.of(named.copyWithAmount(500), ordinary.copyWithAmount(500)),
                List.of(ordinary.copyWithAmount(1000)));
        h.assertTrue(
                !new MixerMeterRequirements(List.of(), List.of(exact)).matches(strategy, List.of(), mixed),
                "Exact fluid meter accepted partially matching allocation");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidManualMeterRequirementsAreRejected(GameTestHelper h) {
        for (var invalid : List.<Runnable>of(
                () -> new ItemMeterRequirement("minecraft:wheat", 0),
                () -> new ItemMeterRequirement("minecraft:wheat", -1),
                () -> new FluidMeterRequirement("minecraft:water", 0),
                () -> new FluidMeterRequirement("minecraft:water", -1),
                () -> new ItemMeterRequirement("#minecraft:flowers", 1),
                () -> new FluidMeterRequirement("#minecraft:water", 1),
                () -> new ItemMeterRequirement("minecraft:unknown_meter_item", 1),
                () -> new FluidMeterRequirement("minecraft:unknown_meter_fluid", 1),
                () -> ItemMeterRequirement.sampled(ItemStack.EMPTY, 1),
                () -> FluidMeterRequirement.sampled(FluidStack.EMPTY, 1))) {
            boolean rejected = false;
            try {
                invalid.run();
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            h.assertTrue(rejected, "Invalid manual meter accepted");
        }
        var mutable = new ArrayList<ItemMeterRequirement>();
        mutable.add(new ItemMeterRequirement("minecraft:wheat", 4));
        var requirements = new MixerMeterRequirements(mutable, List.of());
        mutable.clear();
        h.assertTrue(requirements.itemRequirements().size() == 1, "Requirements retained mutable configuration list");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void meterItemQuantityIsExactAndSurplusDoesNotMatter(GameTestHelper h) {
        scoped(Map.of("test:meter", itemRecipe("wheat", 4)), () -> {
            var s = state(new ItemStack(Items.WHEAT, 64));
            for (int count : new int[] {3, 5}) {
                s.meterRequirements(meters(count, 0));
                h.assertTrue(s.select().isEmpty(), "Nonexact item meter selected recipe");
            }
            s.meterRequirements(meters(4, 0));
            h.assertTrue(s.select().orElseThrow().commit(), "Exact item meter failed");
            h.assertTrue(s.plantCount() == 60, "Meter changed recipe consumption");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void meterFluidQuantityIsExactAndSurplusDoesNotMatter(GameTestHelper h) {
        scoped(Map.of("test:meter", waterRecipe(1000)), () -> {
            var s = state();
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
            for (int amount : new int[] {500, 1500}) {
                s.meterRequirements(meters(0, amount));
                h.assertTrue(s.select().isEmpty(), "Nonexact fluid meter selected recipe");
            }
            s.meterRequirements(meters(0, 1000));
            h.assertTrue(s.select().orElseThrow().commit(), "Exact fluid meter failed");
            h.assertTrue(s.amount(0) == 3000, "Meter changed fluid consumption");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void metersCombineAndAllowAdditionalUnmeteredInputs(GameTestHelper h) {
        var r = recipe(
                "[" + item("wheat", 4) + "," + item("carrot", 2) + "]",
                "[{\"selector\":\"minecraft:water\",\"amount\":1000}]");
        scoped(Map.of("test:meter", r), () -> {
            var s = state(new ItemStack(Items.WHEAT, 8), new ItemStack(Items.CARROT, 4));
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
            s.meterRequirements(meters(4, 500));
            h.assertTrue(s.select().isEmpty(), "Item match bypassed fluid mismatch");
            s.meterRequirements(meters(3, 1000));
            h.assertTrue(s.select().isEmpty(), "Fluid match bypassed item mismatch");
            s.meterRequirements(meters(4, 1000));
            h.assertTrue(s.select().orElseThrow().commit(), "AND match with extra ingredient failed");
            h.assertTrue(s.plantCount() == 6 && s.amount(0) == 1000, "Wrong mixed batch consumption");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateItemMetersNeedDistinctWholeIngredientSlots(GameTestHelper h) {
        var requirements = new MixerMeterRequirements(
                List.of(new ItemMeterRequirement("minecraft:wheat", 4), new ItemMeterRequirement("minecraft:wheat", 2)),
                List.of());
        for (var r : List.of(itemRecipe("wheat", 4), itemRecipe("wheat", 6)))
            scoped(Map.of("test:meter", r), () -> {
                var s = state(new ItemStack(Items.WHEAT, 64));
                s.meterRequirements(requirements);
                h.assertTrue(s.select().isEmpty(), "Meters reused or partitioned one ingredient slot");
            });
        scoped(Map.of("test:meter", recipe("[" + item("wheat", 4) + "," + item("wheat", 2) + "]", "[]")), () -> {
            var s = state(new ItemStack(Items.WHEAT, 64));
            s.meterRequirements(requirements);
            h.assertTrue(s.select().orElseThrow().commit() && s.plantCount() == 58, "Distinct item slots failed");
            s.meterRequirements(new MixerMeterRequirements(
                    List.of(
                            new ItemMeterRequirement("minecraft:wheat", 4),
                            new ItemMeterRequirement("minecraft:wheat", 4)),
                    List.of()));
            h.assertTrue(s.select().isEmpty(), "Identical meters double-counted one slot");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateFluidMetersNeedDistinctWholeIngredientSlots(GameTestHelper h) {
        var requirements = new MixerMeterRequirements(
                List.of(),
                List.of(
                        new FluidMeterRequirement("minecraft:water", 1000),
                        new FluidMeterRequirement("minecraft:water", 500)));
        for (var r : List.of(waterRecipe(1000), waterRecipe(1500)))
            scoped(Map.of("test:meter", r), () -> {
                var s = state();
                s.fluids(true).fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
                s.meterRequirements(requirements);
                h.assertTrue(s.select().isEmpty(), "Fluid meters reused or partitioned one slot");
            });
        var r = recipe("[]", """
                [{"selector":"minecraft:water","amount":1000},{"selector":"minecraft:water","amount":500}]
                """);
        scoped(Map.of("test:meter", r), () -> {
            var s = state();
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
            s.meterRequirements(requirements);
            h.assertTrue(s.select().orElseThrow().commit() && s.amount(0) == 2500, "Distinct fluid slots failed");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void metersFilterCandidatesWithoutChangingRanking(GameTestHelper h) {
        var base = "[" + item("wheat", 4);
        scoped(
                Map.of(
                        "test:highest", recipe(base + "," + item("carrot", 1) + "," + item("carrot", 1) + "]", "[]"),
                        "test:z", recipe(base + "," + item("carrot", 2) + "]", "[]"),
                        "test:a", recipe(base + "," + item("carrot", 2) + "]", "[]"),
                        "test:low_consumption", recipe(base + "," + item("carrot", 1) + "]", "[]"),
                        "test:simple", itemRecipe("wheat", 4)),
                () -> {
                    var s = state(new ItemStack(Items.WHEAT, 64), new ItemStack(Items.CARROT, 64));
                    s.meterRequirements(meters(4, 0));
                    selected(h, s, "test:highest");
                    s.meterRequirements(new MixerMeterRequirements(
                            List.of(
                                    new ItemMeterRequirement("minecraft:wheat", 4),
                                    new ItemMeterRequirement("minecraft:carrot", 2)),
                            List.of()));
                    selected(h, s, "test:a");
                    s.meterRequirements(MixerMeterRequirements.NONE);
                    selected(h, s, "test:highest");
                });
        scoped(
                Map.of(
                        "test:a_small", recipe(base + "," + item("carrot", 1) + "]", "[]"),
                        "test:z_large", recipe(base + "," + item("carrot", 2) + "]", "[]"),
                        "test:b_large", recipe(base + "," + item("carrot", 2) + "]", "[]")),
                () -> {
                    var s = state(new ItemStack(Items.WHEAT, 64), new ItemStack(Items.CARROT, 64));
                    s.meterRequirements(meters(4, 0));
                    selected(h, s, "test:b_large");
                });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void incompatibleMetersLeaveAllResourcesUntouchedAndIdle(GameTestHelper h) {
        scoped(
                Map.of(
                        "test:meter",
                        recipe("[" + item("wheat", 4) + "]", "[{\"selector\":\"minecraft:water\",\"amount\":1000}]")),
                () -> {
                    var s = state(new ItemStack(Items.WHEAT, 64));
                    s.fluids(true).fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
                    s.meterRequirements(meters(5, 1000));
                    var before = s.save(h.getLevel().registryAccess());
                    h.assertTrue(s.select().isEmpty(), "Incompatible selection succeeded");
                    h.assertTrue(before.equals(s.save(h.getLevel().registryAccess())), "Selection mutated state");
                    s.cadence();
                    h.assertTrue(
                            s.recipe().isEmpty() && s.progress() == 0 && s.plantCount() == 64 && s.amount(0) == 4000,
                            "Incompatible cadence consumed or started a batch");
                    h.assertTrue(
                            !s.commit(MixerRecipeRegistry.recipe("test:meter")), "Explicit commit bypassed meters");
                });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void metersUseActualAllocationIncludingSplitStacks(GameTestHelper h) {
        var r = recipe("""
                [{"selector":[{"item":"minecraft:wheat"},{"item":"minecraft:carrot"}],"amount":4}]
                """, "[]");
        scoped(Map.of("test:meter", r), () -> {
            var split = state(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.WHEAT, 2));
            split.meterRequirements(meters(4, 0));
            h.assertTrue(split.select().orElseThrow().commit() && split.plantCount() == 0, "Split allocation rejected");
            var mixed = state(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.CARROT, 2));
            mixed.meterRequirements(meters(4, 0));
            h.assertTrue(mixed.select().isEmpty(), "Meter matched selector instead of actual consumed identities");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void meterChangesAndResourceChangesInvalidateReservations(GameTestHelper h) {
        scoped(Map.of("test:meter", itemRecipe("wheat", 4)), () -> {
            var s = state(new ItemStack(Items.WHEAT, 8));
            s.meterRequirements(meters(4, 0));
            var old = s.select().orElseThrow();
            s.meterRequirements(meters(5, 0));
            h.assertTrue(!old.commit() && s.plantCount() == 8, "Changed meters retained stale reservation");
            s.meterRequirements(meters(4, 0));
            var changed = s.select().orElseThrow();
            s.itemInput().insertItem(0, new ItemStack(Items.WHEAT), false);
            h.assertTrue(!changed.commit() && s.plantCount() == 9, "Changed input retained stale reservation");
            var valid = s.select().orElseThrow();
            h.assertTrue(
                    valid.commit() && !valid.commit() && s.plantCount() == 5, "Reservation lost single-use behavior");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void matchingMetersStillRespectOutputBackpressure(GameTestHelper h) {
        scoped(Map.of("test:meter", itemRecipe("wheat", 4)), () -> {
            var s = state(new ItemStack(Items.WHEAT, 8));
            s.meterRequirements(meters(4, 0));
            var registries = h.getLevel().registryAccess();
            var saved = s.save(registries);
            var output = new net.neoforged.neoforge.items.ItemStackHandler(4);
            for (int i = 0; i < 4; i++) output.setStackInSlot(i, new ItemStack(Items.IRON_INGOT, 64));
            saved.put("producedItems", output.serializeNBT(registries));
            s.load(saved, registries);
            var before = s.save(registries);
            h.assertTrue(s.select().isEmpty() && before.equals(s.save(registries)), "Meters bypassed output capacity");
        });
        h.succeed();
    }

    private static JsonObject recipe(String items, String fluids) {
        return JsonParser.parseString("""
                {"priority":999,"batch_ticks":20,"strategies":[{"id":"default","items":%s,"fluids":%s}],
                "outputs":{"items":[{"item":"minecraft:iron_ingot","amount":1}]}}
                """.formatted(items, fluids)).getAsJsonObject();
    }

    private static String item(String id, int amount) {
        return "{\"selector\":\"minecraft:" + id + "\",\"amount\":" + amount + "}";
    }

    private static JsonObject itemRecipe(String id, int amount) {
        return recipe("[" + item(id, amount) + "]", "[]");
    }

    private static JsonObject waterRecipe(int amount) {
        return recipe("[]", "[{\"selector\":\"minecraft:water\",\"amount\":" + amount + "}]");
    }

    private static void scoped(Map<String, JsonObject> recipes, Runnable test) {
        var saved = MixerRecipeRegistry.snapshot();
        var root = new JsonObject();
        var values = new JsonObject();
        recipes.forEach(values::add);
        root.add("recipes", values);
        try {
            MixerRecipeRegistry.publish(MixerRecipeRegistry.parse(root));
            test.run();
        } finally {
            MixerRecipeRegistry.publish(saved);
        }
    }

    private static MixerState state(ItemStack... stacks) {
        var state = new MixerState(() -> {});
        for (int i = 0; i < stacks.length; i++) state.itemInput().insertItem(i, stacks[i], false);
        return state;
    }

    private static void selected(GameTestHelper h, MixerState state, String id) {
        h.assertTrue(state.select().orElseThrow().recipe().equals(id), "Expected selected recipe " + id);
    }

    /** Input ports intentionally forbid extraction; alter fixture supplies through persisted state. */
    private static void removeItems(GameTestHelper h, MixerState state, int slot, int amount) {
        var registries = h.getLevel().registryAccess();
        var saved = state.save(registries);
        var inventory = new net.neoforged.neoforge.items.ItemStackHandler();
        inventory.deserializeNBT(registries, saved.getCompound("items"));
        h.assertTrue(inventory.extractItem(slot, amount, false).getCount() == amount, "Fixture removal failed");
        saved.put("items", inventory.serializeNBT(registries));
        state.load(saved, registries);
    }

    @GameTest(template = "empty")
    public static void complexityCountsSlotsBeforeConsumptionAndLegacyPriority(GameTestHelper h) {
        var complex = recipe("[" + item("carrot", 1) + "," + item("carrot", 1) + "]", "[]");
        complex.addProperty("priority", -999);
        scoped(Map.of("test:z_complex", complex, "test:a_simple", itemRecipe("carrot", 64)), () -> {
            var s = state(new ItemStack(Items.CARROT, 64));
            selected(h, s, "test:z_complex");
            s.cadence();
            h.assertTrue(s.recipe().equals("test:z_complex"), "Cadence bypassed selection ranking");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void consumptionOutranksRecipeIdForEqualComplexity(GameTestHelper h) {
        scoped(Map.of("test:a", itemRecipe("carrot", 1), "test:z", itemRecipe("carrot", 2)), () -> {
            selected(h, state(new ItemStack(Items.CARROT, 2)), "test:z");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualStackSizesOneSixteenAndSixtyFour(GameTestHelper h) {
        scoped(
                Map.of(
                        "test:a",
                        itemRecipe("carrot", 32),
                        "test:b",
                        itemRecipe("snowball", 9),
                        "test:c",
                        itemRecipe("iron_sword", 1)),
                () -> {
                    var s = state(
                            new ItemStack(Items.CARROT, 32),
                            new ItemStack(Items.SNOWBALL, 9),
                            new ItemStack(Items.IRON_SWORD));
                    selected(h, s, "test:c");
                    removeItems(h, s, 2, 1);
                    selected(h, s, "test:b");
                });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullStacksAndOneThousandMillibucketsTieById(GameTestHelper h) {
        scoped(
                Map.of(
                        "test:d",
                        itemRecipe("carrot", 64),
                        "test:c",
                        itemRecipe("snowball", 16),
                        "test:b",
                        itemRecipe("iron_sword", 1),
                        "test:a",
                        waterRecipe(1000)),
                () -> {
                    var s = state(
                            new ItemStack(Items.CARROT, 64),
                            new ItemStack(Items.SNOWBALL, 16),
                            new ItemStack(Items.IRON_SWORD));
                    s.fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
                    selected(h, s, "test:a");
                    s = state(
                            new ItemStack(Items.CARROT, 64),
                            new ItemStack(Items.SNOWBALL, 16),
                            new ItemStack(Items.IRON_SWORD));
                    selected(h, s, "test:b");
                    removeItems(h, s, 2, 1);
                    selected(h, s, "test:c");
                    removeItems(h, s, 1, 1);
                    selected(h, s, "test:d");
                });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidFractionsCompareExactlyAgainstItems(GameTestHelper h) {
        scoped(Map.of("test:a", itemRecipe("carrot", 1), "test:z", waterRecipe(16)), () -> {
            var s = state(new ItemStack(Items.CARROT));
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 16), FluidAction.EXECUTE);
            selected(h, s, "test:z"); // 16/1000 > 1/64
        });
        scoped(Map.of("test:a", itemRecipe("carrot", 1), "test:z", waterRecipe(15)), () -> {
            var s = state(new ItemStack(Items.CARROT));
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 15), FluidAction.EXECUTE);
            selected(h, s, "test:a"); // 15/1000 < 1/64
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void alternativesUseActualAllocationAndComponentStackLimits(GameTestHelper h) {
        var alternate = recipe("""
                [{"selector":[{"item":"minecraft:carrot"},{"item":"minecraft:iron_sword"}],"amount":1}]
                """, "[]");
        scoped(Map.of("test:z", alternate, "test:a", itemRecipe("snowball", 8)), () -> {
            var s = state(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SNOWBALL, 8));
            selected(h, s, "test:z");
            removeItems(h, s, 0, 1);
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            selected(h, s, "test:a");
            removeItems(h, s, 0, 1);
            var smallStack = new ItemStack(Items.CARROT);
            smallStack.set(DataComponents.MAX_STACK_SIZE, 1);
            s.itemInput().insertItem(0, smallStack, false);
            selected(h, s, "test:z");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unrecordedInputsAreStillConsumedAndRanked(GameTestHelper h) {
        var unrecorded = itemRecipe("iron_sword", 1);
        unrecorded
                .getAsJsonArray("strategies")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("items")
                .get(0)
                .getAsJsonObject()
                .addProperty("record_in_composition", false);
        scoped(Map.of("test:z", unrecorded, "test:a", itemRecipe("carrot", 1)), () -> {
            var s = state(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.CARROT));
            var p = s.select().orElseThrow();
            h.assertTrue(
                    p.recipe().equals("test:z")
                            && p.commit()
                            && s.itemInput().getStackInSlot(0).isEmpty(),
                    "record_in_composition was incorrectly treated as a non-consumed tool flag");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void splitAllocationsAndMixedInputsSumConsumption(GameTestHelper h) {
        scoped(Map.of("test:z", itemRecipe("carrot", 2), "test:a", itemRecipe("snowball", 16)), () -> {
            var small = new ItemStack(Items.CARROT);
            small.set(DataComponents.MAX_STACK_SIZE, 1);
            selected(h, state(small, new ItemStack(Items.CARROT), new ItemStack(Items.SNOWBALL, 16)), "test:z");
        });
        var mixed = recipe("[" + item("carrot", 32) + "]", "[{\"selector\":\"minecraft:water\",\"amount\":501}]");
        var items = recipe("[" + item("snowball", 8) + "," + item("snowball", 8) + "]", "[]");
        scoped(Map.of("test:z", mixed, "test:a", items), () -> {
            var s = state(new ItemStack(Items.CARROT, 32), new ItemStack(Items.SNOWBALL, 16));
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 501), FluidAction.EXECUTE);
            selected(h, s, "test:z"); // 32/64 + 501/1000 > 8/16 + 8/16
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void outputBackpressurePreservesFallbackAndSelectionPurity(GameTestHelper h) {
        var blocked = recipe("[" + item("carrot", 1) + "," + item("carrot", 1) + "]", "[]");
        blocked.getAsJsonObject("outputs")
                .getAsJsonArray("items")
                .get(0)
                .getAsJsonObject()
                .addProperty("amount", 257);
        scoped(Map.of("test:z", blocked, "test:a", itemRecipe("carrot", 1)), () -> {
            var s = state(new ItemStack(Items.CARROT, 2));
            var before = s.save(h.getLevel().registryAccess());
            selected(h, s, "test:a");
            h.assertTrue(
                    before.equals(s.save(h.getLevel().registryAccess())), "Blocked candidate mutated selection state");
            removeItems(h, s, 0, 2);
            before = s.save(h.getLevel().registryAccess());
            h.assertTrue(
                    s.select().isEmpty() && before.equals(s.save(h.getLevel().registryAccess())),
                    "Failed selection mutated state");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missingPreferredInputFallsBackAndNextBatchReevaluates(GameTestHelper h) {
        scoped(Map.of("test:z", itemRecipe("carrot", 2), "test:a", itemRecipe("carrot", 1)), () -> {
            var s = state(new ItemStack(Items.CARROT, 2));
            s.cadence();
            h.assertTrue(s.recipe().equals("test:z"), "Preferred recipe did not start");
            removeItems(h, s, 0, 1);
            selected(h, s, "test:a");
            s.cadence();
            h.assertTrue(
                    s.recipe().equals("test:a") && s.progress() == 10,
                    "Unavailable recipe retained selection/progress");
            s.cadence();
            h.assertTrue(s.select().isEmpty(), "Empty Mixer selected an ineligible recipe");
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT, 2), false);
            s.cadence();
            h.assertTrue(s.recipe().equals("test:z"), "Next batch retained successful fallback affinity");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void selectionDoesNotMutateBuffersStatusMetricsOrPublishServiceTanks(GameTestHelper h) {
        var r = waterRecipe(500);
        r.getAsJsonObject("outputs")
                .add(
                        "fluids",
                        JsonParser.parseString("[{\"fluid\":\"mobvats:meat\",\"amount\":100}]")
                                .getAsJsonArray());
        scoped(Map.of("test:selection", r), () -> {
            var service = new net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging(() -> {});
            var tank = service.tank(new FluidStack(Fluids.WATER, 1));
            tank.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
            var storage = new net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage();
            storage.services(Map.of(net.minecraft.core.BlockPos.ZERO, service));
            int[] changes = {0};
            var s = new MixerState(() -> changes[0]++);
            s.bind(storage, net.minecraft.core.BlockPos.ZERO);
            var before = s.save(h.getLevel().registryAccess());
            int count = service.tanks().size();
            int notifications = changes[0];
            for (int i = 0; i < 4; i++) selected(h, s, "test:selection");
            h.assertTrue(
                    before.equals(s.save(h.getLevel().registryAccess()))
                            && tank.getFluidAmount() == 500
                            && count == service.tanks().size()
                            && changes[0] == notifications,
                    "Selection mutated live storage/status");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void strategiesRankByComplexityConsumptionThenId(GameTestHelper h) {
        var r = itemRecipe("carrot", 1);
        var strategies = r.getAsJsonArray("strategies");
        var complex = recipe("[" + item("carrot", 1) + "," + item("carrot", 1) + "]", "[]")
                .getAsJsonArray("strategies")
                .get(0)
                .getAsJsonObject();
        complex.addProperty("id", "z");
        complex.addProperty("priority", -1000);
        strategies.add(complex);
        var tied = complex.deepCopy();
        tied.addProperty("id", "a");
        strategies.add(tied);
        var larger = complex.deepCopy();
        larger.addProperty("id", "b");
        larger.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("amount", 2);
        strategies.add(larger);
        scoped(Map.of("test:strategies", r), () -> {
            var s = state(new ItemStack(Items.CARROT, 3));
            h.assertTrue(s.select().orElseThrow().strategy().equals("b"), "Strategy ignored consumption");
            removeItems(h, s, 0, 1);
            h.assertTrue(s.select().orElseThrow().strategy().equals("a"), "Strategy ranking ignored complexity/ID");
            removeItems(h, s, 0, 1);
            h.assertTrue(s.select().orElseThrow().strategy().equals("default"), "Strategy fallback failed");
        });
        h.succeed();
    }
}
