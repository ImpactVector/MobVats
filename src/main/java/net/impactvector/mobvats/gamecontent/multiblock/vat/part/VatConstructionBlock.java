package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** The substrate remains authoritative after forming, but must not shadow its replacement visual. */
public class VatConstructionBlock extends VatPartBlock {
    public VatConstructionBlock(VatPartType type) {
        super(type);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
