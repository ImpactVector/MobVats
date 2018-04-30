package net.impactvector.mobvats.tileentity;

import it.zerono.mods.zerocore.api.multiblock.MultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.rectangular.RectangularMultiblockTileEntityBase;
import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;
import net.impactvector.mobvats.MultiblockVat;
import net.impactvector.mobvats.block.ModBlocks;
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

    public MultiblockVat getReactorController() { return (MultiblockVat)this.getMultiblockController(); }

    @Override
    public MultiblockControllerBase createNewMultiblock() {
        return new MultiblockVat(this.getWorld());
    }

    @Override
    public Class<? extends MultiblockControllerBase> getMultiblockControllerType() { return MultiblockVat.class; }
}
