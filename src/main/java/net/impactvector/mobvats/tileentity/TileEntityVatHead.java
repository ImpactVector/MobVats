package net.impactvector.mobvats.tileentity;

import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;
import net.impactvector.mobvats.MultiblockVat;

public class TileEntityVatHead extends TileEntityVatHomunculusPart {

    @Override
    public boolean isGoodForInterior(IMultiblockValidator validatorCallback) {
        if (validatorCallback instanceof MultiblockVat) {
            MultiblockVat vat = (MultiblockVat) validatorCallback;
            if (!vat.attachedHeads.contains(this))
                vat.attachBlock(this);
            return true;
        }
        else
            return false;
    }
}
