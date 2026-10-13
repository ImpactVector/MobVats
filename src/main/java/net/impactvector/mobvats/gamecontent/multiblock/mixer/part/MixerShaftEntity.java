package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Interior segment attached through ZeroCore's normal part lifecycle. */
public final class MixerShaftEntity extends AbstractMixerEntity {
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<Boolean> FORMED =
            new net.neoforged.neoforge.client.model.data.ModelProperty<>();

    public MixerShaftEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.Shaft, pos, state);
    }

    @Override
    protected net.neoforged.neoforge.client.model.data.ModelData getUpdatedModelData() {
        // Interior parts have no outward shell faces, so shell.formed() cannot identify them.
        return super.getUpdatedModelData()
                .derive()
                .with(FORMED, isMachineAssembled())
                .build();
    }
}
