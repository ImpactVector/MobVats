package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.api.multiblock.MultiblockTileEntityBase;
import net.impactvector.mobvats.helpers.PortDirection;
import net.impactvector.mobvats.helpers.Properties;
import net.impactvector.mobvats.interfaces.IInputOutputPort;
import net.impactvector.mobvats.interfaces.INeighborUpdatableEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class VatIOPort extends MachinePartBase {
    public VatIOPort(String name, PartType type) {
        super(type, name, Material.IRON);
    }

    /**
     * Called when a tile entity on a side of this block changes is created or is destroyed.
     * @param world The world
     * @param position Block position in world
     * @param neighbor Block position of neighbor
     */
    @Override
    public void onNeighborChange(IBlockAccess world, BlockPos position, BlockPos neighbor) {

        TileEntity te = world.getTileEntity(position);

        // Signal power taps and other ports when their neighbors change, etc.
        if (te instanceof INeighborUpdatableEntity) {
            ((INeighborUpdatableEntity)te).onNeighborTileChange(world, position, neighbor);
        }
    }

    @Override
    protected void buildBlockState(BlockStateContainer.Builder builder) {

        super.buildBlockState(builder);
        builder.add(Properties.PORTDIRECTION);
    }

    @Override
    protected IBlockState buildDefaultState(IBlockState state) {
        return super.buildDefaultState(state).withProperty(Properties.PORTDIRECTION, PortDirection.Inlet);
    }

    @Override
    protected IBlockState buildActualState(IBlockState state, IBlockAccess world, BlockPos position, MultiblockTileEntityBase part) {

        state = super.buildActualState(state, world, position, part);

        if (part instanceof IInputOutputPort)
            state = state.withProperty(Properties.PORTDIRECTION,
                    ((IInputOutputPort)part).getDirection().isInput() ? PortDirection.Inlet : PortDirection.Outlet);

        return state;
    }
}
