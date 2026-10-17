package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.impactvector.mobvats.MobVats;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Coalesced server presentation snapshots. Uses the storage compiler, never a renderer compatibility rule. */
@EventBusSubscriber(modid = MobVats.MOD_ID)
public final class FluidBufferPresentation {
    private static final Map<Level, Set<BlockPos>> DIRTY = new WeakHashMap<>();

    public static synchronized void changed(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) return;
        var positions = DIRTY.computeIfAbsent(level, k -> new HashSet<>());
        positions.add(pos.immutable());
        for (var face : Direction.values()) positions.add(pos.relative(face));
    }

    public static synchronized void structure(Level level, BlockPos min, BlockPos max) {
        if (level.isClientSide) return;
        // Lifecycle callbacks may run during chunk unload. Defer all world reads to the level tick.
        var positions = DIRTY.computeIfAbsent(level, k -> new HashSet<>());
        for (var pos : BlockPos.betweenClosed(min, max)) positions.add(pos.immutable());
    }

    private static synchronized Set<BlockPos> take(Level level) {
        var result = DIRTY.remove(level);
        return result == null ? Set.of() : result;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(LevelTickEvent.Post event) {
        refresh(event.getLevel(), take(event.getLevel()));
    }

    public static void refresh(Level level, Collection<BlockPos> dirty) {
        if (level.isClientSide) return;
        var visited = new HashSet<BlockPos>();
        for (var origin : dirty) {
            if (!level.hasChunkAt(origin)
                    || visited.contains(origin)
                    || !(level.getBlockEntity(origin) instanceof FluidBufferEntity)) continue;
            var owner = ConduitStructure.containing(level, origin);
            var members = new ArrayList<FluidBufferEntity>();
            var queue = new ArrayDeque<BlockPos>();
            queue.add(origin);
            while (!queue.isEmpty()) {
                var pos = queue.removeFirst();
                if (visited.contains(pos)
                        || !level.hasChunkAt(pos)
                        || !(level.getBlockEntity(pos) instanceof FluidBufferEntity member)
                        || ConduitStructure.containing(level, pos) != owner) continue;
                visited.add(pos);
                members.add(member);
                for (var face : Direction.values()) queue.add(pos.relative(face));
            }
            var storage = new PhysicalFluidStorage();
            storage.compile(members, ConduitTopology.EMPTY);
            for (var cluster : storage.clusters()) {
                var joins = new LinkedHashMap<BlockPos, Integer>();
                var positions = new HashSet<BlockPos>();
                cluster.members().forEach(m -> positions.add(m.getBlockPos()));
                for (var member : cluster.members()) {
                    joins.put(member.getBlockPos(), FluidBufferShape.neighbors(positions, member.getBlockPos()));
                }
                var fluid = cluster.tank().getFluid().copy();
                double height = FluidBufferArt.level(
                        joins, (double) fluid.getAmount() / cluster.tank().getCapacity());
                for (var member : cluster.members()) {
                    var pos = member.getBlockPos();
                    member.presentation(joins.get(pos), fluid, fluid.isEmpty() ? 0 : height - pos.getY());
                }
            }
        }
    }

    private FluidBufferPresentation() {}
}
