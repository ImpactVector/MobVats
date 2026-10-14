package net.impactvector.mobvats.gametest;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Development-only scheduler trigger. Native ChunkMap ticking performs all save/unload work. */
final class ServerChunkEviction {
    private static final Method SCHEDULE =
            method(ChunkMap.class, "updateChunkScheduling", long.class, int.class, ChunkHolder.class, int.class);
    private static final Field FUTURES = field(DistanceManager.class, "chunksToUpdateFutures");
    private static final Field UPDATING = field(ChunkMap.class, "updatingChunkMap");
    private static final Field PENDING = field(ChunkMap.class, "pendingUnloads");
    private static final Field LOADED = field(net.minecraft.world.level.chunk.LevelChunk.class, "loaded");

    static boolean loaded(net.minecraft.world.level.chunk.LevelChunk chunk) throws IllegalAccessException {
        return LOADED.getBoolean(chunk);
    }

    @SuppressWarnings("unchecked")
    static void request(ServerLevel level, ChunkPos pos) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("mobvats.lifecycleAcceptance"))
            throw new IllegalStateException("Dedicated acceptance run required");
        var map = level.getChunkSource().chunkMap;
        var holder = map.getVisibleChunkIfPresent(pos.toLong());
        if (holder == null || holder.getTicketLevel() != 34 || holder.getGenerationRefCount() != 0)
            throw new IllegalStateException("Expected settled dependency holder at level 34");
        // Match the scheduler/future-update boundary used by DistanceManager. Do not edit chunk maps,
        // save futures, block entities, lifecycle flags or events; processUnloads owns those transitions.
        var scheduled = (ChunkHolder)
                SCHEDULE.invoke(map, pos.toLong(), ChunkLevel.MAX_LEVEL + 1, holder, holder.getTicketLevel());
        ((Set<ChunkHolder>) FUTURES.get(map.getDistanceManager())).add(scheduled);
    }

    static boolean absent(ServerLevel level, ChunkPos pos) throws ReflectiveOperationException {
        var map = level.getChunkSource().chunkMap;
        return map.getVisibleChunkIfPresent(pos.toLong()) == null
                && !((Long2ObjectMap<?>) UPDATING.get(map)).containsKey(pos.toLong())
                && !((Long2ObjectMap<?>) PENDING.get(map)).containsKey(pos.toLong());
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) {
        try {
            var method = type.getDeclaredMethod(name, arguments);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private ServerChunkEviction() {}
}
