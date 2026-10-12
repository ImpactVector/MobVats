package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatGlassState;
import net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VatGlassEntity extends AbstractVatEntity {
    public VatGlassEntity(BlockPos pos, BlockState state) { super(VatPartType.Glass, pos, state); }
    public VatGlassState getGlassState() {
        return getMultiblockController().filter(MultiblockVat::isAssembled)
            .map(vat -> VatGlassState.from(getBlockPos(), vat.getBoundingBox().getMin(), vat.getBoundingBox().getMax()))
            .orElse(VatGlassState.UNFORMED);
    }
    @Override protected ModelData getUpdatedModelData() {
        return super.getUpdatedModelData().derive().with(VatGlassState.PROPERTY, getGlassState()).build();
    }
}
