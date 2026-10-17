package net.impactvector.mobvats.gametest;

import java.util.LinkedHashMap;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.extruder.ExtruderEntity;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

/** Player-operated, indirect-power demonstrations installed only after production acceptance. */
final class WorldRedstoneControls {
    static BlockPos install(
            ServerLevel level, StringBuilder manifest, String id, BlockPos controller, Direction outward) {
        BlockPos support = null;
        boolean standalone = level.getBlockEntity(controller) instanceof ExtruderEntity;
        for (var side :
                new Direction[] {outward.getClockWise(), outward.getCounterClockWise(), Direction.DOWN, Direction.UP}) {
            var candidate = controller.relative(side);
            var state = level.getBlockState(candidate);
            boolean shell = state.is(Content.block(VatPartType.Casing))
                    || state.is(Content.block(VatPartType.Glass))
                    || state.is(MixerContent.block(MixerPartType.Casing))
                    || state.is(MixerContent.block(MixerPartType.Glass));
            var leverPos = candidate.relative(outward);
            var labelPos = leverPos.relative(outward).above(2);
            if (!(standalone ? level.isEmptyBlock(candidate) : shell)
                    || !level.isEmptyBlock(leverPos)
                    || !level.isEmptyBlock(labelPos)) continue;
            boolean clear = true;
            for (var source : new BlockPos[] {candidate, leverPos})
                for (var direction : Direction.values()) {
                    var neighbor = source.relative(direction);
                    var neighborState = level.getBlockState(neighbor);
                    if (!neighbor.equals(controller)
                            && !neighbor.equals(candidate)
                            && level.getBlockEntity(neighbor) != null
                            && !neighborState.is(Content.block(VatPartType.Casing))
                            && !neighborState.is(Content.block(VatPartType.Glass))
                            && !neighborState.is(MixerContent.block(MixerPartType.Casing))
                            && !neighborState.is(MixerContent.block(MixerPartType.Glass))) clear = false;
                }
            if (clear) {
                support = candidate;
                break;
            }
        }
        if (support == null) throw new IllegalStateException(id + " has no safe wall position beside its controller");
        var lever = support.relative(outward);
        var label = lever.relative(outward).above(2);

        // Strong power must not also inhibit an endpoint, motor or vanilla supply hopper.
        var neighbors = new LinkedHashMap<BlockPos, Boolean>();
        for (var pos : BlockPos.betweenClosed(support.offset(-3, -3, -3), support.offset(3, 3, 3)))
            if (!pos.equals(controller)
                    && level.getBlockEntity(pos) != null
                    && !level.getBlockState(pos).is(Content.block(VatPartType.Casing))
                    && !level.getBlockState(pos).is(Content.block(VatPartType.Glass))
                    && !level.getBlockState(pos).is(MixerContent.block(MixerPartType.Casing))
                    && !level.getBlockState(pos).is(MixerContent.block(MixerPartType.Glass)))
                neighbors.put(pos.immutable(), level.hasNeighborSignal(pos));
        // A solid wall tile conducts the lever's strong power to the adjacent controller.
        // Replace only decorative glass; functional parts and the controller face stay clear.
        if (standalone) level.setBlockAndUpdate(support, Blocks.SMOOTH_STONE.defaultBlockState());
        else if (level.getBlockState(support).is(Content.block(VatPartType.Glass)))
            level.setBlockAndUpdate(support, Content.state(VatPartType.Casing));
        else if (level.getBlockState(support).is(MixerContent.block(MixerPartType.Glass)))
            level.setBlockAndUpdate(support, MixerContent.state(MixerPartType.Casing));
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
        return lever;
    }

    private WorldRedstoneControls() {}
}
