package net.impactvector.mobvats.block;

import net.impactvector.mobvats.MobVats;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

public class ModBlocks {

    @GameRegistry.ObjectHolder("mobvats:vat_flesh")
    public static VatFlesh vatFlesh;
    @GameRegistry.ObjectHolder("mobvats:vat_head")
    public static VatHead vatHead;
    @GameRegistry.ObjectHolder("mobvats:vat_casing")
    public static VatCasing vatCasing;
    @GameRegistry.ObjectHolder("mobvats:vat_controller")
    public static VatController vatController;
    @GameRegistry.ObjectHolder("mobvats:vat_glass")
    public static VatGlass vatGlass;
    @GameRegistry.ObjectHolder("mobvats:vat_harness")
    public static VatHarness vatHarness;
    @GameRegistry.ObjectHolder("mobvats:vat_item_input_port")
    public static VatItemInputPort vatItemInputPort;
    @GameRegistry.ObjectHolder("mobvats:vat_item_output_port")
    public static VatItemOutputPort vatItemOutputPort;

    public static void register(IForgeRegistry<Block> registry) {
        registry.register(new VatFlesh("vat_flesh"));
        registry.register(new VatHead("vat_head"));
        registry.register(new VatCasing("vat_casing"));
        registry.register(new VatController("vat_controller"));
        registry.register(new VatGlass("vat_glass"));
        registry.register(new VatHarness("vat_harness"));
        registry.register(new VatItemInputPort("vat_item_input_port"));
        registry.register(new VatItemOutputPort("vat_item_output_port"));
    }

    public static void registerItemBlocks(IForgeRegistry<Item> registry) {
        registry.register(new ItemBlock(ModBlocks.vatFlesh).setRegistryName(ModBlocks.vatFlesh.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatHead).setRegistryName(ModBlocks.vatHead.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatCasing).setRegistryName(ModBlocks.vatCasing.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatController).setRegistryName(ModBlocks.vatController.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatGlass).setRegistryName(ModBlocks.vatGlass.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatHarness).setRegistryName(ModBlocks.vatHarness.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatItemInputPort).setRegistryName(ModBlocks.vatItemInputPort.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatItemOutputPort).setRegistryName(ModBlocks.vatItemOutputPort.getRegistryName()));
    }

    public static void registerModels() {
        vatHead.registerItemModel(Item.getItemFromBlock(vatHead));
        vatFlesh.registerItemModel(Item.getItemFromBlock(vatFlesh));
        vatCasing.registerItemModel(Item.getItemFromBlock(vatCasing));
        vatController.registerItemModel(Item.getItemFromBlock(vatController));
        vatGlass.registerItemModel(Item.getItemFromBlock(vatGlass));
        vatHarness.registerItemModel(Item.getItemFromBlock(vatHarness));
        vatItemInputPort.registerItemModel(Item.getItemFromBlock(vatItemInputPort));
        vatItemOutputPort.registerItemModel(Item.getItemFromBlock(vatItemOutputPort));
    }

}