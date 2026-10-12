package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState;
import net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VatCasingEntity extends AbstractVatEntity {
    public VatCasingEntity(BlockPos pos, BlockState state) { super(VatPartType.Casing, pos, state); }
    public VatCasingState getCasingState() {
        return getMultiblockController().filter(MultiblockVat::isAssembled)
            .map(vat -> VatCasingState.from(getBlockPos(), vat.getBoundingBox().getMin(), vat.getBoundingBox().getMax()))
            .orElse(VatCasingState.UNFORMED);
    }
    @Override protected ModelData getUpdatedModelData() {
        return super.getUpdatedModelData().derive().with(VatCasingState.PROPERTY, getCasingState()).build();
    }
}
