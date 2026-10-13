package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VatHarnessEntity extends AbstractVatEntity {
    public VatHarnessEntity(BlockPos pos, BlockState state) {
        super(VatPartType.Harness, pos, state);
    }
}
