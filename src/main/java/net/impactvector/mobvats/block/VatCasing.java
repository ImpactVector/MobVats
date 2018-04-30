package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.api.multiblock.rectangular.PartPosition;
import it.zerono.mods.zerocore.api.multiblock.rectangular.RectangularMultiblockTileEntityBase;
import net.impactvector.mobvats.helpers.CasingType;
import net.impactvector.mobvats.helpers.Properties;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class VatCasing extends PartBase {
    public VatCasing(String name){
        super(PartType.VatCasing, name, Material.IRON);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos position) {

        TileEntity te = world.getTileEntity(position);
        CasingType type = CasingType.Single;

        if (te instanceof RectangularMultiblockTileEntityBase) {

            RectangularMultiblockTileEntityBase mbTile = (RectangularMultiblockTileEntityBase)te;

            if (mbTile.isConnected() && mbTile.getMultiblockController().isAssembled())
                type = CasingType.from(mbTile.getPartPosition());
        }

        return state.withProperty(Properties.CASINGTYPE, type);
    }

    protected void buildBlockState(BlockStateContainer.Builder builder) {

        super.buildBlockState(builder);
        builder.add(Properties.CASINGTYPE);
    }

    @Override
    protected IBlockState buildDefaultState(IBlockState state) {

        return super.buildDefaultState(state).withProperty(Properties.CASINGTYPE, CasingType.Single);
    }
}
