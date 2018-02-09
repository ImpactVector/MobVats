package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.lib.block.ModBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.impactvector.mobvats.MobVats;

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

    public Item createItemBlock() {
        return new ItemBlock(this);
    }

    @Override
    public BlockBase setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }

}
