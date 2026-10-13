package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import java.util.Set;
import net.impactvector.mobvats.config.MixerEnergyConfig;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Receive-only native FE motor. Every successful drive tick pays its full cost. */
public final class MixerFeDriveEntity extends AbstractMixerDriveEntity {
    public static final int CAPACITY = 1_000_000;
    private int stored;
    // Prepaid fractions of one FE, in 1/200 units; retained across stops and saves.
    private int credit;
    private final IEnergyStorage energy = new IEnergyStorage() {
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (isRemoved() || maxReceive <= 0) return 0;
            int received = Math.min(maxReceive, CAPACITY - stored);
            if (!simulate && received > 0) {
                stored += received;
                setChanged();
            }
            return received;
        }

        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        public int getEnergyStored() {
            return stored;
        }

        public int getMaxEnergyStored() {
            return CAPACITY;
        }

        public boolean canExtract() {
            return false;
        }

        public boolean canReceive() {
            return !isRemoved();
        }
    };

    public MixerFeDriveEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.FeDrive, pos, state);
    }

    public IEnergyStorage energy() {
        return energy;
    }

    @Override
    public Set<ConduitResource> conduitResources() {
        return Set.of();
    }

    private int tickCost(int rate) {
        return (rate + MixerEnergyConfig.CYCLE_TICKS - 1 - credit) / MixerEnergyConfig.CYCLE_TICKS;
    }

    @Override
    public double availableDrive(MultiblockMixer mixer) {
        return attachedTo(mixer) && stored >= tickCost(MixerEnergyConfig.FE_PER_CYCLE.get()) ? 1.0 : 0.0;
    }

    @Override
    public boolean driveTick(MultiblockMixer mixer) {
        int rate = MixerEnergyConfig.FE_PER_CYCLE.get();
        int cost = tickCost(rate);
        if (!attachedTo(mixer) || stored < cost) return false;
        stored -= cost;
        credit += cost * MixerEnergyConfig.CYCLE_TICKS - rate;
        setChanged();
        return true;
    }

    public CompoundTag saveEnergy() {
        var tag = new CompoundTag();
        tag.putInt("stored", stored);
        tag.putInt("credit", credit);
        return tag;
    }

    public void loadEnergy(CompoundTag tag) {
        stored = Math.clamp(tag.getInt("stored"), 0, CAPACITY);
        credit = Math.clamp(tag.getInt("credit"), 0, MixerEnergyConfig.CYCLE_TICKS - 1);
        setChanged();
    }

    @Override
    public CompoundTag syncDataTo(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(tag, registries, reason);
        tag.put("feDrive", saveEnergy());
        return tag;
    }

    @Override
    public void syncDataFrom(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(tag, registries, reason);
        if (tag.contains("feDrive")) loadEnergy(tag.getCompound("feDrive"));
    }
}
