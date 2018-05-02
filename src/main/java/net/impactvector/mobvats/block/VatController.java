package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.api.multiblock.MultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.MultiblockTileEntityBase;
import net.impactvector.mobvats.helpers.ControllerState;
import net.impactvector.mobvats.helpers.Properties;
import net.impactvector.mobvats.interfaces.IActivateable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nonnull;

public class VatController extends MachinePartBase {
    public VatController(String name){
        super(PartType.VatController, name, Material.IRON);
    }

    @Override
    protected void buildBlockState(BlockStateContainer.Builder builder) {

        super.buildBlockState(builder);
        builder.add(Properties.CONTROLLERSTATE);
    }

    @Override
    protected IBlockState buildDefaultState(IBlockState state) {

        return super.buildDefaultState(state).withProperty(Properties.CONTROLLERSTATE, ControllerState.Off);
    }

    @Override
    protected IBlockState buildActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos position,
                                           @Nonnull MultiblockTileEntityBase part) {

        MultiblockControllerBase controller = part.getMultiblockController();
        ControllerState controllerState = null == controller || !controller.isAssembled() || !(controller instanceof IActivateable) ?
                ControllerState.Off : ((IActivateable)controller).getActive() ? ControllerState.Active : ControllerState.Idle;

        return super.buildActualState(state, world, position, part).withProperty(Properties.CONTROLLERSTATE, controllerState);
    }

}
