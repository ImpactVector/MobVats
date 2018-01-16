package net.impactvector.mobvats.block;

import net.impactvector.mobvats.MobVats;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.registries.IForgeRegistry;

public class ModBlocks {

    public static BlockBase vatFlesh = new BlockBase(Material.CLAY, "vat_flesh");


    public static void register(IForgeRegistry<Block> registry) {
        registry.registerAll(
                vatFlesh
        );
    }

    public static void registerItemBlocks(IForgeRegistry<Item> registry) {
        registry.registerAll(
                vatFlesh.createItemBlock()
        );
    }

    public static void registerModels() {
        vatFlesh.registerItemModel(Item.getItemFromBlock(vatFlesh));
    }

}