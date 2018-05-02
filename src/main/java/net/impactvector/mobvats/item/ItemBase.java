package net.impactvector.mobvats.item;

import it.zerono.mods.zerocore.lib.item.ModItem;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.impactvector.mobvats.MobVats;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemBase extends ModItem {

    protected String name;

    public ItemBase(String name) {
        super(name);
        this.name = name;
        setUnlocalizedName(name);
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
    @Override
    @SideOnly(Side.CLIENT)
    public void onRegisterModels()
    {
        this.registerItemModel();
    }

}