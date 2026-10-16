package net.impactvector.mobvats.gametest;

import java.util.LinkedHashMap;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

/** Player-operated, indirect-power demonstrations installed only after production acceptance. */
final class WorldRedstoneControls {
    static void install(ServerLevel level, StringBuilder manifest, String id, BlockPos controller, Direction outward) {
        var support = controller.relative(outward);
        var lever = support.relative(outward);
        var label = support.above();
        for (var pos : new BlockPos[] {support, lever, label})
            if (!level.isEmptyBlock(pos))
                throw new IllegalStateException(id + " redstone control would overwrite " + pos);

        // Strong power must not also inhibit an endpoint, motor or vanilla supply hopper.
        var neighbors = new LinkedHashMap<BlockPos, Boolean>();
        for (var pos : BlockPos.betweenClosed(support.offset(-3, -3, -3), support.offset(3, 3, 3)))
            if (!pos.equals(controller) && level.getBlockEntity(pos) != null)
                neighbors.put(pos.immutable(), level.hasNeighborSignal(pos));
        level.setBlockAndUpdate(support, Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(
                lever,
                Blocks.LEVER
                        .defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                        .setValue(LeverBlock.FACING, outward)
                        .setValue(LeverBlock.POWERED, true));
        ManualTestWorld.sign(level, label, id + " REDSTONE", "ON = paused", "OFF = run", "Ports stay live");
        if (!level.hasNeighborSignal(controller))
            throw new IllegalStateException(id + " controller did not receive indirect lever power");
        for (var entry : neighbors.entrySet())
            if (level.hasNeighborSignal(entry.getKey()) != entry.getValue())
                throw new IllegalStateException(id + " redstone control interferes with " + entry.getKey());
        manifest.append("- ")
                .append(id)
                .append(": controller ")
                .append(controller.toShortString())
                .append("; powered lever ")
                .append(lever.toShortString())
                .append("\n");
    }

    private WorldRedstoneControls() {}
}
