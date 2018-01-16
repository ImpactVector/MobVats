package net.impactvector.mobvats.item;

import net.impactvector.mobvats.MobVats;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.registries.IForgeRegistry;

public class ModItems {
    public static ItemBase itemSyringe = new ItemSyringe("item_syringe");
    public static ItemBase itemEssence = new ItemSyringe("item_essence");

    public static void register(IForgeRegistry<Item> registry) {
        registry.registerAll(
                itemSyringe,
                itemEssence
        );
    }

    public static void registerModels() {
        itemSyringe.registerItemModel();
        itemEssence.registerItemModel();
    }

}
