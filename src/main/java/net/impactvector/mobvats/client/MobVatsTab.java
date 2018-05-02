package net.impactvector.mobvats.client;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.init.ModItems;

public class MobVatsTab extends CreativeTabs {

    public MobVatsTab() {
        super(MobVats.MODID);
        setBackgroundImageName("item_search.png");
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(ModItems.itemSyringe);
    }

    @Override
    public boolean hasSearchBar() {
        return true;
    }

}