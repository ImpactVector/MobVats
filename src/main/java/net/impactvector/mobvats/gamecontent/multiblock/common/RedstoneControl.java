package net.impactvector.mobvats.gamecontent.multiblock.common;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Derived execution permission. Never changes ownership, storage, or operator preference. */
public final class RedstoneControl {
    private RedstoneControl() {}

    /** Six local vanilla neighbor queries, including strong power through conductors; no topology scan. */
    public static boolean inhibited(BlockEntity part) {
        var level = part.getLevel();
        if (level == null) return false;
        // Vanilla can inspect the conductor's neighbors as well. Suspend on an incomplete neighborhood.
        var pos = part.getBlockPos();
        for (int x = (pos.getX() - 2) >> 4; x <= ((pos.getX() + 2) >> 4); ++x)
            for (int z = (pos.getZ() - 2) >> 4; z <= ((pos.getZ() + 2) >> 4); ++z)
                if (LoadedWorld.chunk(level, new net.minecraft.core.BlockPos(x << 4, pos.getY(), z << 4)) == null)
                    return true;
        // SignalGetter preserves vanilla strong-power/conductor rules, with ticket-free world reads.
        return new net.minecraft.world.level.SignalGetter() {
            public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos p) {
                var chunk = LoadedWorld.chunk(level, p);
                return chunk == null
                        ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                        : chunk.getBlockState(p);
            }

            public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos p) {
                return getBlockState(p).getFluidState();
            }

            public BlockEntity getBlockEntity(net.minecraft.core.BlockPos p) {
                return LoadedWorld.blockEntity(level, p);
            }

            public int getHeight() {
                return level.getHeight();
            }

            public int getMinBuildHeight() {
                return level.getMinBuildHeight();
            }
        }.hasNeighborSignal(pos);
    }
}
