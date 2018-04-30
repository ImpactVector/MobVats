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
    @GameRegistry.ObjectHolder("mobvats:vat_drain")
    public static VatDrain vatDrain;
    @GameRegistry.ObjectHolder("mobvats:vat_glass")
    public static VatGlass vatGlass;
    @GameRegistry.ObjectHolder("mobvats:vat_harness")
    public static VatHarness vatHarness;
    @GameRegistry.ObjectHolder("mobvats:vat_item_port")
    public static VatIOPort vatItemPort;
    @GameRegistry.ObjectHolder("mobvats:vat_fluid_port")
    public static VatIOPort vatFluidPort;

    public static void register(IForgeRegistry<Block> registry) {
        registry.register(new VatFlesh("vat_flesh"));
        registry.register(new VatHead("vat_head"));
        registry.register(new VatCasing("vat_casing"));
        registry.register(new VatController("vat_controller"));
        registry.register(new VatDrain("vat_drain"));
        registry.register(new VatGlass("vat_glass"));
        registry.register(new VatHarness("vat_harness"));
        registry.register(new VatIOPort("vat_item_port", PartType.VatItemPort));
        registry.register(new VatIOPort("vat_fluid_port", PartType.VatFluidPort));
    }

    public static void registerItemBlocks(IForgeRegistry<Item> registry) {
        registry.register(new ItemBlock(ModBlocks.vatFlesh).setRegistryName(ModBlocks.vatFlesh.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatHead).setRegistryName(ModBlocks.vatHead.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatCasing).setRegistryName(ModBlocks.vatCasing.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatController).setRegistryName(ModBlocks.vatController.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatDrain).setRegistryName(ModBlocks.vatDrain.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatGlass).setRegistryName(ModBlocks.vatGlass.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatHarness).setRegistryName(ModBlocks.vatHarness.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatItemPort).setRegistryName(ModBlocks.vatItemPort.getRegistryName()));
        registry.register(new ItemBlock(ModBlocks.vatFluidPort).setRegistryName(ModBlocks.vatFluidPort.getRegistryName()));
    }

    public static void registerModels() {
        vatHead.registerItemModel(Item.getItemFromBlock(vatHead));
        vatFlesh.registerItemModel(Item.getItemFromBlock(vatFlesh));
        vatCasing.registerItemModel(Item.getItemFromBlock(vatCasing));
        vatController.registerItemModel(Item.getItemFromBlock(vatController));
        vatDrain.registerItemModel(Item.getItemFromBlock(vatDrain));
        vatGlass.registerItemModel(Item.getItemFromBlock(vatGlass));
        vatHarness.registerItemModel(Item.getItemFromBlock(vatHarness));
        vatItemPort.registerItemModel(Item.getItemFromBlock(vatItemPort));
        vatFluidPort.registerItemModel(Item.getItemFromBlock(vatFluidPort));
    }

}