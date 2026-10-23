package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;

/** Exercises the loaded recipe manager, including real crafting grids and registry resolution. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class SurvivalRecipeGameTests {
    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("mobvats", name);
    }

    private static Recipe<?> recipe(GameTestHelper h, String name) {
        return h.getLevel().getRecipeManager().byKey(id(name)).orElseThrow().value();
    }

    private static void requires(GameTestHelper h, String recipe, Item item) {
        h.assertTrue(
                recipe(h, recipe).getIngredients().stream().anyMatch(i -> i.test(new ItemStack(item))),
                recipe + " must require " + item);
    }

    private static void excludes(GameTestHelper h, String recipe, Item item) {
        h.assertTrue(
                recipe(h, recipe).getIngredients().stream().noneMatch(i -> i.test(new ItemStack(item))),
                recipe + " unexpectedly uses " + item);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void allStructuralRecipesCraftAndT3RemainsCreativeOnly(GameTestHelper h) {
        for (var tier : VatTier.values()) {
            for (var type : new VatPartType[] {VatPartType.Casing, VatPartType.Glass, VatPartType.Controller}) {
                var item = Content.block(tier, type).asItem();
                var producing = h.getLevel().getRecipeManager().getRecipes().stream()
                        .filter(r -> r.value()
                                .getResultItem(h.getLevel().registryAccess())
                                .is(item))
                        .toList();
                h.assertTrue(producing.size() == (tier == VatTier.T3 ? 0 : 1), "Survival recipe count " + tier + type);
                if (tier == VatTier.T3) continue;
                var r = (CraftingRecipe) producing.getFirst().value();
                var base = tier == VatTier.T1 ? Items.COPPER_INGOT : Items.IRON_INGOT;
                var casing = Content.block(tier, VatPartType.Casing).asItem();
                var input = CraftingInput.of(
                        2,
                        1,
                        List.of(
                                new ItemStack(type == VatPartType.Casing ? base : casing),
                                new ItemStack(type == VatPartType.Controller ? Items.REDSTONE : Items.GLASS)));
                h.assertTrue(r.matches(input, h.getLevel()), "Structural recipe cannot craft " + tier + type);
                var output = r.assemble(input, h.getLevel().registryAccess());
                h.assertTrue(
                        output.is(item) && output.getCount() == (type == VatPartType.Controller ? 1 : 4),
                        "Structural output/quantity " + tier + type);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void functionalMaterialCostsAndQuantities(GameTestHelper h) {
        for (var name : List.of(
                "fluid_conduit",
                "fluid_buffer",
                "fill_valve",
                "drain_valve",
                "pull_pump",
                "fluid_void",
                "feed_port",
                "mixer_fluid_meter",
                "mixer_intake")) {
            requires(h, name, Items.COPPER_INGOT);
            excludes(h, name, Items.IRON_INGOT);
        }
        for (var name : List.of("fluid_port", "vat_drain")) {
            requires(h, name, Content.block(VatTier.T1, VatPartType.Casing).asItem());
            requires(h, name, Items.BUCKET);
            excludes(h, name, Items.IRON_INGOT);
        }
        for (var name : List.of(
                "item_conduit",
                "item_port",
                "tool_port",
                "item_void",
                "mixer_item_meter",
                "mixer_shaft",
                "mixer_impeller",
                "mixer_combustion_drive",
                "extruder",
                "vat_harness",
                "blender")) {
            requires(h, name, Items.IRON_INGOT);
            excludes(h, name, Items.COPPER_INGOT);
        }
        requires(h, "vat_harness", Items.LEATHER);
        requires(h, "blender", Items.DIAMOND);
        for (var name : List.of("fluid_conduit", "item_conduit")) {
            h.assertTrue(
                    recipe(h, name).getResultItem(h.getLevel().registryAccess()).getCount() == 4,
                    "Bulk conduit output reduced " + name);
            for (var direction : List.of("to_tap", "to_trunk"))
                h.assertTrue(
                        recipe(h, name + "_" + direction)
                                        .getResultItem(h.getLevel().registryAccess())
                                        .getCount()
                                == 1,
                        "Conduit conversion quantity changed");
        }
        h.assertTrue(
                recipe(h, "mixer_shaft")
                                .getResultItem(h.getLevel().registryAccess())
                                .getCount()
                        == 3,
                "Shaft batch output changed");
        // Existing reversible mode switches continue to reuse their universal component.
        requires(h, "push_pump_switch", BuiltInRegistries.ITEM.get(id("pull_pump")));
        requires(h, "fluid_void_switch", BuiltInRegistries.ITEM.get(id("item_void")));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void loadedCatalogResolvesAndFunctionalItemsAreUniversal(GameTestHelper h) {
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals("mobvats")) continue;
            var r = holder.value();
            var output = r.getResultItem(h.getLevel().registryAccess());
            h.assertTrue(
                    !output.isEmpty() && output.getCount() > 0 && output.getCount() <= output.getMaxStackSize(),
                    "Invalid recipe result " + holder.id());
            for (var ingredient : r.getIngredients()) {
                if (ingredient.isEmpty()) continue; // Shaped recipe padding.
                h.assertTrue(ingredient.getItems().length > 0, "Unresolved ingredient " + holder.id());
                for (var stack : ingredient.getItems())
                    h.assertTrue(
                            !stack.isEmpty()
                                    && BuiltInRegistries.ITEM.containsKey(
                                            BuiltInRegistries.ITEM.getKey(stack.getItem())),
                            "Unavailable ingredient " + holder.id());
            }
        }
        var structural = Set.of(VatPartType.Casing, VatPartType.Glass, VatPartType.Controller);
        for (var type : VatPartType.values()) {
            if (structural.contains(type)) continue;
            var base = Content.block(type);
            for (var tier : VatTier.values()) {
                h.assertTrue(
                        Content.entityType(type).isValid(base.defaultBlockState()),
                        "Universal entity registration lost " + type);
                var baseId = BuiltInRegistries.BLOCK.getKey(base);
                h.assertTrue(
                        !BuiltInRegistries.ITEM.containsKey(
                                id(baseId.getPath() + "_" + tier.name().toLowerCase(Locale.ROOT))),
                        "Tier variant registered for " + type);
            }
        }
        h.succeed();
    }

    private SurvivalRecipeGameTests() {}
}
