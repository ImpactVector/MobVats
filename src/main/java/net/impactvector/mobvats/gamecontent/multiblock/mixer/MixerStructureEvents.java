package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import java.util.*;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Detect ordinary interior edits too, including correction of an invalidated assembly. */
@EventBusSubscriber(modid = MobVats.MOD_ID)
public final class MixerStructureEvents {
    private static final Map<MultiblockMixer, Map<BlockPos, BlockState>> STRUCTURES = new WeakHashMap<>();

    static synchronized void forget(MultiblockMixer mixer) {
        STRUCTURES.remove(mixer);
    }

    static synchronized void remember(MultiblockMixer mixer) {
        if (mixer.getWorld().isClientSide) return;
        var states = new HashMap<BlockPos, BlockState>();
        for (var pos : mixer.getBoundingBox())
            states.put(pos.immutable(), mixer.getWorld().getBlockState(pos));
        STRUCTURES.put(mixer, states);
    }

    @SubscribeEvent
    public static synchronized void neighbors(BlockEvent.NeighborNotifyEvent event) {
        for (var entry : STRUCTURES.entrySet()) {
            var mixer = entry.getKey();
            var states = entry.getValue();
            if (mixer.getWorld() != event.getLevel()
                    || mixer.isEmpty()
                    || mixer.isPaused()
                    || !states.containsKey(event.getPos())) continue;
            net.impactvector.mobvats.gamecontent.multiblock.common.SyncDiagnostics.record(
                    "MultiblockMixer",
                    "neighborDelivery",
                    mixer,
                    mixer.getWorld().getGameTime());
            var previous = states.put(event.getPos().immutable(), event.getState());
            if (!previous.equals(event.getState())) ConduitStructure.changed(mixer.getWorld(), event.getPos(), true);
        }
    }

    private MixerStructureEvents() {}
}
