package net.impactvector.mobvats.survival;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Runtime registry and recipe graph, including intermediates; one stable choice per tag ingredient. */
final class SurvivalStock {
    static final int STACKABLE_COUNT = 4096;
    static final int SINGLE_COUNT = 64;
    private static final String[] EXTRAS = {
        "copper_ingot",
        "iron_ingot",
        "gold_ingot",
        "glass",
        "stone",
        "cobblestone",
        "redstone",
        "leather",
        "diamond",
        "wheat",
        "wheat_seeds",
        "rotten_flesh",
        "bone",
        "soul_sand",
        "charcoal",
        "sand",
        "bucket",
        "glass_bottle",
        "hopper",
        "chest",
        "oak_log",
        "oak_planks",
        "water_bucket",
        "coal",
        "blaze_powder",
        "ender_pearl",
        "slime_ball",
        "cooked_beef"
    };

    static SortedMap<String, Item> discover(ServerLevel level) {
        var stock = new TreeMap<String, Item>();
        BuiltInRegistries.ITEM.entrySet().stream()
                .filter(e -> e.getKey().location().getNamespace().equals("mobvats"))
                .filter(e -> !e.getKey().location().getPath().startsWith("creative_"))
                .forEach(e -> stock.put(e.getKey().location().toString(), e.getValue()));
        for (String id : EXTRAS) add(stock, "minecraft:" + id);
        // Mixer selectors participate in the same recursive intermediate expansion.
        try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(
                net.impactvector.mobvats.MobVats.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")))) {
            collectSelectors(com.google.gson.JsonParser.parseReader(reader), stock, level);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read Mixer stock requirements", error);
        }
        var recipes = level.getRecipeManager().getRecipes().stream()
                .sorted(Comparator.comparing((RecipeHolder<?> h) -> h.value() instanceof CraftingRecipe ? 0 : 1)
                        .thenComparing(h -> h.id().toString()))
                .toList();
        var visited = new HashSet<Item>();
        // Every MobVats recipe is visited, not just one recipe per output.
        for (var holder : recipes) if (holder.id().getNamespace().equals("mobvats")) ingredients(holder.value(), stock);
        while (true) {
            var pending =
                    stock.values().stream().filter(i -> !visited.contains(i)).toList();
            if (pending.isEmpty()) break;
            for (Item item : pending) {
                visited.add(item);
                recipes.stream()
                        .filter(h ->
                                h.value().getResultItem(level.registryAccess()).is(item))
                        .findFirst()
                        .ifPresent(h -> ingredients(h.value(), stock));
            }
        }
        return stock;
    }

    private static void collectSelectors(
            com.google.gson.JsonElement element, Map<String, Item> stock, ServerLevel level) {
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) collectSelectors(child, stock, level);
        } else if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("items")) {
                for (var entry : object.getAsJsonArray("items")) {
                    var item = entry.getAsJsonObject();
                    if (!item.has("selector")) continue;
                    String selector = item.get("selector").getAsString();
                    if (selector.startsWith("#")) {
                        var tag = net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM,
                                net.minecraft.resources.ResourceLocation.parse(selector.substring(1)));
                        var choices = BuiltInRegistries.ITEM.getTag(tag).orElseThrow();
                        var selected = choices.stream()
                                .map(net.minecraft.core.Holder::value)
                                .min(Comparator.comparing(
                                        i -> BuiltInRegistries.ITEM.getKey(i).toString()))
                                .orElseThrow();
                        stock.put(BuiltInRegistries.ITEM.getKey(selected).toString(), selected);
                    } else add(stock, selector);
                }
            }
            for (var child : object.entrySet()) collectSelectors(child.getValue(), stock, level);
        }
    }

    private static void ingredients(Recipe<?> recipe, Map<String, Item> stock) {
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            var selected = Arrays.stream(ingredient.getItems())
                    .min(Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM
                                            .getKey(s.getItem())
                                            .getPath()
                                            .startsWith("oak_")
                                    ? 0
                                    : 1)
                            .thenComparing(s ->
                                    BuiltInRegistries.ITEM.getKey(s.getItem()).toString()))
                    .orElseThrow(() -> new IllegalStateException("Unresolved Survival ingredient: " + recipe));
            stock.put(BuiltInRegistries.ITEM.getKey(selected.getItem()).toString(), selected.getItem());
        }
    }

    private static void add(Map<String, Item> stock, String id) {
        var key = net.minecraft.resources.ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Unknown stock item: " + id);
        var item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR) throw new IllegalStateException("Invalid stock item: " + id);
        stock.put(id, item);
    }

    static int count(Item item) {
        return item.getDefaultInstance().getMaxStackSize() == 1 ? SINGLE_COUNT : STACKABLE_COUNT;
    }
}
