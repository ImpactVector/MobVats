package net.impactvector.mobvats.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.impactvector.mobvats.MobVats;

public class ItemBase extends Item {

    protected String name;

    public ItemBase(String name) {
        this.name = name;
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(MobVats.getTab());
    }

    public void registerItemModel() {
        MobVats.getProxy().registerItemRenderer(this, 0, name);
    }

    @Override
    public ItemBase setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }

}