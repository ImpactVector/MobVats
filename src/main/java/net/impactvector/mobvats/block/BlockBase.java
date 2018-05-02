package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.lib.block.ModBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.impactvector.mobvats.MobVats;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockBase extends ModBlock {

    protected String name;

    public BlockBase(String name, Material material) {
        super(name, material);

        this.name = name;

        setUnlocalizedName(name);
        //setRegistryName(name);
        setCreativeTab(MobVats.getTab());
    }

    public void registerItemModel(Item itemBlock) {
        MobVats.getProxy().registerItemRenderer(itemBlock, 0, name);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onRegisterModels()
    {
        this.registerItemModel(Item.getItemFromBlock(this));
    }

    @Override
    public BlockBase setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }

}
