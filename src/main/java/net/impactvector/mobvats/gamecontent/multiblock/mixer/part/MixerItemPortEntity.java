package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.common.EndpointConfiguration;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MixerItemPortEntity extends MixerPortEntity
        implements net.impactvector.mobvats.gamecontent.multiblock.common.ActiveBoundaryPort {
    public int transferBoundary(int limit) {
        return getMultiblockController()
                .filter(c -> c.isAssembled())
                .map(c -> ejectItems(c.itemsForPort(this)))
                .orElse(0);
    }

    public MixerItemPortEntity(BlockPos pos, BlockState state) {
        this(MixerPartType.ItemPort, pos, state);
    }

    public MixerItemPortEntity(MixerPartType type, BlockPos pos, BlockState state) {
        super(type, pos, state, EndpointConfiguration.Resource.ITEM);
    }
}
