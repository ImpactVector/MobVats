package net.impactvector.mobvats.gamecontent.multiblock.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Reads must not acquire or renew UNKNOWN tickets, even when the chunk is already present. */
public final class LoadedWorld {
    private LoadedWorld() {}

    public static ChunkAccess chunk(Level level, BlockPos pos) {
        if (level == null || level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return null;
        return level instanceof ServerLevel server
                ? server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4)
                : level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
    }

    public static BlockEntity blockEntity(Level level, BlockPos pos) {
        var chunk = chunk(level, pos);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    public static net.minecraft.world.level.block.state.BlockState blockState(Level level, BlockPos pos) {
        var chunk = chunk(level, pos);
        return chunk == null
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : chunk.getBlockState(pos);
    }
}
