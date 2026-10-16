package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import java.util.Set;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A machine-local motor actor. Resource endpoints describe topology, not fuel consumption. */
public abstract class AbstractMixerDriveEntity extends AbstractMixerEntity implements MixerDrive {
    protected AbstractMixerDriveEntity(MixerPartType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static boolean couples(BlockPos drive, BlockPos impeller) {
        return drive.getY() == impeller.getY()
                && Math.abs((long) drive.getX() - impeller.getX()) + Math.abs((long) drive.getZ() - impeller.getZ())
                        == 1;
    }

    public boolean redstoneInhibited() {
        return net.impactvector.mobvats.gamecontent.multiblock.common.RedstoneControl.inhibited(this);
    }
    /** Motors declare their supply resource without changing attachment geometry. */
    public Set<ConduitResource> conduitResources() {
        return Set.of(ConduitResource.ITEM);
    }

    protected boolean attachedTo(MultiblockMixer mixer) {
        return !isRemoved()
                && !redstoneInhibited()
                && !mixer.controllerInhibited()
                && level == mixer.getWorld()
                && mixer.containsPart(this)
                && getMultiblockController().filter(c -> c == mixer).isPresent()
                && mixer.ownsDrive(this);
    }
}
