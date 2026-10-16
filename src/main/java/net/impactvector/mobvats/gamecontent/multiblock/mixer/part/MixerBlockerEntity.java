package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerBlockerSample;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Optional shell control; configuration belongs to the physical part, with no resource storage. */
public final class MixerBlockerEntity extends AbstractMixerEntity {
    private MixerBlockerSample sample = MixerBlockerSample.EMPTY;

    public MixerBlockerEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.Blocker, pos, state);
    }

    public MixerBlockerSample sample() {
        return sample;
    }

    public void sample(MixerBlockerSample value) {
        if (!sample.equals(value)) {
            sample = value;
            setChanged();
            if (level != null && !level.isClientSide) notifyBlockUpdate();
        }
    }

    public boolean configure(Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return false;
        if (player.level().isClientSide) return true;
        var held = player.getItemInHand(hand);
        var fluid = FluidUtil.getFluidContained(held);
        sample(fluid.isPresent() ? MixerBlockerSample.sampled(fluid.get()) : MixerBlockerSample.sampled(held));
        player.displayClientMessage(
                Component.translatable(
                        sample.equals(MixerBlockerSample.EMPTY)
                                ? "message.mobvats.filter_cleared"
                                : "message.mobvats.blocker_sample_set"),
                true);
        return true;
    }

    public CompoundTag configuration() {
        var tag = new CompoundTag();
        tag.putString("blockerItem", sample.item());
        tag.putString("blockerFluid", sample.fluid());
        return tag;
    }

    public void loadConfiguration(CompoundTag tag) {
        sample = new MixerBlockerSample(tag.getString("blockerItem"), tag.getString("blockerFluid"));
    }

    public CompoundTag syncDataTo(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(tag, registries, reason);
        tag.merge(configuration());
        return tag;
    }

    public void syncDataFrom(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(tag, registries, reason);
        loadConfiguration(tag);
    }
}
