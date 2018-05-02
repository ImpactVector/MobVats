package net.impactvector.mobvats.tileentity;

import it.zerono.mods.zerocore.api.multiblock.MultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.rectangular.PartPosition;
import it.zerono.mods.zerocore.api.multiblock.rectangular.RectangularMultiblockTileEntityBase;
import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;
import it.zerono.mods.zerocore.lib.BlockFacings;
import it.zerono.mods.zerocore.lib.world.WorldHelper;
import net.impactvector.mobvats.MultiblockVat;
import net.impactvector.mobvats.init.ModBlocks;
import net.minecraft.util.math.BlockPos;

public abstract class TileEntityVatPartBase extends RectangularMultiblockTileEntityBase {

    public TileEntityVatPartBase() {
    }

    @Override
    public boolean isGoodForFrame(IMultiblockValidator validatorCallback) {

        if (this.getBlockType() == ModBlocks.vatCasing)
            return true;

        BlockPos position = this.getWorldPosition();

        validatorCallback.setLastError("multiblock.validation.vat.invalid_frame_block", position.getX(), position.getY(), position.getZ());
        return false;
    }

    protected void setBlockLocationErrorMessage(IMultiblockValidator validatorCallback) {

        BlockPos position = this.getPos();

        validatorCallback.setLastError("multiblock.validation.vat.invalid_" + this.getBlockType().getUnlocalizedName() +"_location",
                position.getX(), position.getY(), position.getZ());
    }

    public boolean isGoodForSides(IMultiblockValidator validatorCallback) {
        return true;
    }

    public boolean isGoodForTop(IMultiblockValidator validatorCallback) {
        return true;
    }

    public boolean isGoodForBottom(IMultiblockValidator validatorCallback) {
        return true;
    }

    public boolean isGoodForInterior(IMultiblockValidator validatorCallback) {
        validatorCallback.setLastError("multiblock.validation.vat.invalid_part_for_interior", new Object[]{this.getWorldPosition()});
        return false;
    }

    public MultiblockVat getVatController() { return (MultiblockVat)this.getMultiblockController(); }

    @Override
    public MultiblockControllerBase createNewMultiblock() {
        return new MultiblockVat(this.getWorld());
    }

    @Override
    public Class<? extends MultiblockControllerBase> getMultiblockControllerType() { return MultiblockVat.class; }

    @Override
    public void onPostMachineBroken() {
        super.onPostMachineBroken();

        // Re-render this block on the client
        if(this.world.isRemote) {
            WorldHelper.notifyBlockUpdate(this.world, this.getPos(), null, null);
        }
    }

    @Override
    public void onPreMachineAssembled(MultiblockControllerBase controller) {
        super.onPreMachineAssembled(controller);

        // Re-render this block on the client
        if(this.world.isRemote) {
            WorldHelper.notifyBlockUpdate(this.world, this.getPos(), null, null);
        }
    }
}
