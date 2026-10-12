package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MixerCreativeDriveEntity extends AbstractMixerDriveEntity {
    public MixerCreativeDriveEntity(BlockPos pos,BlockState state){super(MixerPartType.Drive,pos,state);}
    public double availableDrive(MultiblockMixer mixer){return attachedTo(mixer)?1.0:0.0;}
}
