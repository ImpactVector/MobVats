package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MixerItemMeterEntity extends AbstractMixerMeterEntity {
    public MixerItemMeterEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.ItemMeter, pos, state);
    }
}
