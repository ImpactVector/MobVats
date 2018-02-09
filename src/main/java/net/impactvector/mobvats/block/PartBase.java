package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.lib.block.BlockMultiblockPart;
import net.impactvector.mobvats.MobVats;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;

public class PartBase extends BlockMultiblockPart {

    protected String name;

    public PartBase(PartType type, String name, Material material) {
        super(type, name, material);

        this.name = name;

        //setUnlocalizedName(name);
        //setRegistryName(name); //does this in the super class
        setCreativeTab(MobVats.getTab());
    }

    public void registerItemModel(Item itemBlock) {
        MobVats.getProxy().registerItemRenderer(itemBlock, 0, name);
    }

    public Item createItemBlock() {
        return new ItemBlock(this);
    }

    @Override
    public PartBase setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }
}
