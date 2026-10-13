package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPumpEntity;

/** Controller-owned scheduling. Only an inward TAP grants access to physical buffers. */
public final class MixerPumps {
    public static boolean tick(MultiblockMixer mixer, Collection<?> parts) {
        return tickOrdered(
                mixer,
                parts.stream()
                        .filter(MixerPumpEntity.class::isInstance)
                        .map(MixerPumpEntity.class::cast)
                        .sorted(Comparator.comparing(MixerPumpEntity::getBlockPos, ConduitTopology.POSITION))
                        .toList());
    }

    public static boolean tickOrdered(MultiblockMixer mixer, List<MixerPumpEntity> pumps) {
        if (!mixer.isAssembled()
                || mixer.getWorld().isClientSide
                || mixer.getWorld().getGameTime() % WorldFluidPumps.CADENCE != 0) return false;
        boolean changed = false;
        for (var pump : pumps) {
            if (pump.isRemoved() || !pump.isMachineAssembled()) continue;
            var face = WorldFluidPumps.outward(
                    pump.getBlockPos(),
                    mixer.getBoundingBox().getMin(),
                    mixer.getBoundingBox().getMax());
            if (face == null) continue;
            var reachable = mixer.conduitTopology().reachable(pump.getBlockPos(), ConduitResource.FLUID);
            var tanks = mixer.storage().clusters().stream()
                    .filter(c -> c.members().stream().anyMatch(m -> reachable.contains(m.getBlockPos())))
                    .map(PhysicalFluidStorage.Cluster::tank)
                    .toList();
            if (tanks.isEmpty()) {
                pump.status("No inward TAP storage");
                continue;
            }
            boolean pull = pump.getPartType() == MixerPartType.PullPump;
            var handler = PhysicalFluidStorage.handler(tanks, pull, pump::accepts);
            var root = pump.getBlockPos().relative(face);
            changed |= pull
                    ? WorldFluidPumps.pull(mixer.getWorld(), root, handler, pump)
                    : WorldFluidPumps.push(mixer.getWorld(), root, handler, pump);
        }
        return changed;
    }

    private MixerPumps() {}
}
