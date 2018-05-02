package net.impactvector.mobvats.init;

import it.zerono.mods.zerocore.lib.config.ConfigHandler;
import it.zerono.mods.zerocore.lib.init.GameObjectsHandler;
import it.zerono.mods.zerocore.util.OreDictionaryHelper;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.block.*;
import net.impactvector.mobvats.item.*;
import net.impactvector.mobvats.tileentity.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nonnull;

public class ObjectsHandler extends GameObjectsHandler {

    public ObjectsHandler(ConfigHandler... configs) {
        super(configs);
    }

    protected void onRegisterBlocks(@Nonnull IForgeRegistry<Block> registry) {
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

    protected void onRegisterTileEntities() {
        this.registerTileEntity("mobvats", TileEntityVatFlesh.class);
        this.registerTileEntity("mobvats", TileEntityVatHead.class);
        this.registerTileEntity("mobvats", TileEntityVatGlass.class);
        this.registerTileEntity("mobvats", TileEntityVatHarness.class);
        this.registerTileEntity("mobvats", TileEntityVatController.class);
        this.registerTileEntity("mobvats", TileEntityVatDrain.class);
        this.registerTileEntity("mobvats", TileEntityVatItemPort.class);
        this.registerTileEntity("mobvats", TileEntityVatFluidPort.class);
        this.registerTileEntity("mobvats", TileEntityVatPart.class);
    }

    protected void onRegisterItems(@Nonnull IForgeRegistry<Item> registry) {
        registry.register(new ItemSyringe("item_syringe"));
        registry.register(new ItemEssence("item_essence"));
    }
}
