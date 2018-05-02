package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.api.multiblock.MultiblockTileEntityBase;
import it.zerono.mods.zerocore.api.multiblock.rectangular.RectangularMultiblockTileEntityBase;
import it.zerono.mods.zerocore.lib.block.BlockMultiblockPart;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.helpers.MachinePartState;
import net.impactvector.mobvats.helpers.Properties;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

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
    @SideOnly(Side.CLIENT)
    public void onRegisterModels()
    {
        this.registerItemModel(Item.getItemFromBlock(this));
    }

    @Override
    public PartBase setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }
}
