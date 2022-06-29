package net.impactvector.mobvats.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.fluid.ModFluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModItems {

    //region Item Definitions
    public static final Item MEAT_BUCKET = registerItem("meat_bucket",
            new BucketItem(ModFluids.MEAT_STILL, new FabricItemSettings().group(ModItemGroup.MEAT).maxCount(1)));
    public static final Item NUTRIENT_BUCKET = registerItem("nutrient_bucket",
            new BucketItem(ModFluids.NUTRIENT_STILL, new FabricItemSettings().group(ModItemGroup.MEAT).maxCount(1)));

    public static final Item MEAT_NUGGET = registerItem("meat_nugget",
            new Item(new FabricItemSettings().group(ModItemGroup.MEAT).food(ModFoodComponents.MEAT_NUGGET)));
    public static final Item MEAT_BAR = registerItem("meat_bar",
            new Item(new FabricItemSettings().group(ModItemGroup.MEAT).food(ModFoodComponents.MEAT_BAR)));
    public static final Item NUTRIENT_BAR = registerItem("nutrient_bar",
            new Item(new FabricItemSettings().group(ModItemGroup.MEAT).food(ModFoodComponents.NUTRIENT_BAR)));
    //endregion

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registry.ITEM, new Identifier(MobVats.MOD_ID, name), item);
    }

    public static void registerModItems() {
        MobVats.LOGGER.info("Registering Mod Items for " + MobVats.MOD_ID);
    }
}
