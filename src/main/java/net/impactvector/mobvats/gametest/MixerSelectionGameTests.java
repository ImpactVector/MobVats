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
