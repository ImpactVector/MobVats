package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Top-face anchor for the Mixer shaft assembly. */
public final class MixerImpellerEntity extends AbstractMixerEntity {
    public MixerImpellerEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.Impeller, pos, state);
    }
}
