package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.lib.BlockFacings;
import it.zerono.mods.zerocore.lib.PropertyBlockFacings;
import it.zerono.mods.zerocore.util.OreDictionaryHelper;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class VatGlass extends PartBase {
    public VatGlass(String name){
        super(PartType.VatGlass, name, Material.GLASS);

        this.setSoundType(SoundType.GLASS);
        this._actualFacings = new boolean[EnumFacing.VALUES.length];
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos position) {

        Block thisBlock = state.getBlock();
        int len = EnumFacing.VALUES.length;

        for (int i = 0; i < len; ++i) {

            IBlockState neighbor = world.getBlockState(position.offset(EnumFacing.VALUES[i]));

            this._actualFacings[i] = thisBlock == neighbor.getBlock();
        }

        BlockFacings facings = BlockFacings.from(this._actualFacings);

        return state.withProperty(PropertyBlockFacings.FACINGS, facings.toProperty());
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    protected void buildBlockState(BlockStateContainer.Builder builder) {

        super.buildBlockState(builder);
        builder.add(PropertyBlockFacings.FACINGS);
    }

    @Override
    protected IBlockState buildDefaultState(IBlockState state) {

        return super.buildDefaultState(state).withProperty(PropertyBlockFacings.FACINGS, PropertyBlockFacings.None);
    }

    private boolean[] _actualFacings;
}
