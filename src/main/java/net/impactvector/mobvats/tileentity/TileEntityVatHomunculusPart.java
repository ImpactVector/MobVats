package net.impactvector.mobvats.tileentity;

import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;

public class TileEntityVatHomunculusPart extends TileEntityVatPart {

    @Override
    public boolean isGoodForSides(IMultiblockValidator validatorCallback) {
        this.setBlockLocationErrorMessage(validatorCallback);
        return false;
    }
    @Override
    public boolean isGoodForTop(IMultiblockValidator validatorCallback) {
        this.setBlockLocationErrorMessage(validatorCallback);
        return false;
    }
    @Override
    public boolean isGoodForBottom(IMultiblockValidator validatorCallback) {
        this.setBlockLocationErrorMessage(validatorCallback);
        return false;
    }
    @Override
    public boolean isGoodForInterior(IMultiblockValidator validatorCallback) {
        return true;
    }
}
