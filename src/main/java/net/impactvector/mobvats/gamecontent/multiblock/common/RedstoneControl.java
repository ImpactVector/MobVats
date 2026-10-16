package net.impactvector.mobvats.gamecontent.multiblock.common;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Derived execution permission. Never changes ownership, storage, or operator preference. */
public final class RedstoneControl {
    private RedstoneControl() {}

    /** Six local vanilla neighbor queries, including strong power through conductors; no topology scan. */
    public static boolean inhibited(BlockEntity part) {
        var level = part.getLevel();
        return level != null && level.hasNeighborSignal(part.getBlockPos());
    }
}
