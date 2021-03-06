package net.impactvector.mobvats.tileentity;

import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;
import net.impactvector.mobvats.MultiblockVat;

public class TileEntityVatFlesh extends TileEntityVatHomunculusPart {

    @Override
    public boolean isGoodForInterior(IMultiblockValidator validatorCallback) {
        if (validatorCallback instanceof MultiblockVat) {
            MultiblockVat vat = (MultiblockVat) validatorCallback;
            if (!vat.attachedFlesh.contains(this))
                vat.attachBlock(this);
            return true;
        }
        else
            return false;
    }
}
