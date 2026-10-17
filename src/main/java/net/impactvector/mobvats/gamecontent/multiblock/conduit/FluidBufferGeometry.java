package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Slice 2 physical contract. Storage clustering, not filter equality alone, owns bridges. */
public final class FluidBufferGeometry {
    public static final AABB BODY = new AABB(3.0 / 16, 2.0 / 16, 3.0 / 16, 13.0 / 16, 15.0 / 16, 13.0 / 16);

    public enum Kind {
        BODY,
        SUPPORT,
        MOUNT,
        BRIDGE
    }

    public record Volume(BlockPos owner, Kind kind, AABB bounds) {}

    /** Only fully occupied clusters spanning multiple axes may close the spaces between bridges. */
    public static boolean rectangular(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return false;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (var pos : positions) {
            x0 = Math.min(x0, pos.getX());
            y0 = Math.min(y0, pos.getY());
            z0 = Math.min(z0, pos.getZ());
            x1 = Math.max(x1, pos.getX());
            y1 = Math.max(y1, pos.getY());
            z1 = Math.max(z1, pos.getZ());
        }
        int axes = (x1 > x0 ? 1 : 0) + (y1 > y0 ? 1 : 0) + (z1 > z0 ? 1 : 0);
        return axes >= 2 && (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) == positions.size();
    }

    public static List<Volume> envelope(
            BlockPos pos, boolean supported, Set<Direction> mounts, Set<Direction> bridges) {
        return envelope(pos, supported, mounts, bridges, false);
    }

    public static List<Volume> envelope(
            BlockPos pos, boolean supported, Set<Direction> mounts, Set<Direction> bridges, boolean rectangular) {
        var result = new ArrayList<Volume>();
        result.add(new Volume(pos.immutable(), Kind.BODY, BODY.move(pos)));
        if (supported)
            result.add(new Volume(
                    pos.immutable(),
                    Kind.SUPPORT,
                    new AABB(6.0 / 16, 0, 6.0 / 16, 10.0 / 16, 2.0 / 16, 10.0 / 16).move(pos)));
        if (rectangular)
            result.add(new Volume(
                    pos.immutable(),
                    Kind.BRIDGE,
                    new AABB(
                                    bridges.contains(Direction.WEST) ? 0 : BODY.minX,
                                    bridges.contains(Direction.DOWN) ? 0 : BODY.minY,
                                    bridges.contains(Direction.NORTH) ? 0 : BODY.minZ,
                                    bridges.contains(Direction.EAST) ? 1 : BODY.maxX,
                                    bridges.contains(Direction.UP) ? 1 : BODY.maxY,
                                    bridges.contains(Direction.SOUTH) ? 1 : BODY.maxZ)
                            .move(pos)));
        for (var face : Direction.values()) {
            if (bridges.contains(face)) {
                if (rectangular) continue;
                // Each member owns its half of a continuous 10-pixel tank bridge.
                result.add(new Volume(
                        pos.immutable(),
                        Kind.BRIDGE,
                        extension(face, 3.0 / 16, 13.0 / 16, 2.0 / 16, 15.0 / 16)
                                .move(pos)));
            } else if (mounts.contains(face)) {
                result.add(new Volume(
                        pos.immutable(),
                        Kind.MOUNT,
                        extension(face, 6.0 / 16, 10.0 / 16, 6.0 / 16, 10.0 / 16)
                                .move(pos)));
            }
        }
        return List.copyOf(result);
    }

    private static AABB extension(Direction face, double lo, double hi, double bottom, double top) {
        double start = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? 0
                : face.getAxis() == Direction.Axis.Y ? BODY.maxY : BODY.maxX;
        double end = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? face.getAxis() == Direction.Axis.Y ? BODY.minY : BODY.minX
                : 1;
        return switch (face.getAxis()) {
            case X -> new AABB(start, bottom, lo, end, top, hi);
            case Z -> new AABB(lo, bottom, start, hi, top, end);
            case Y -> new AABB(lo, start, lo, hi, end, hi);
        };
    }

    public static List<Volume> compile(Level level, BlockPos min, BlockPos max) {
        var members = PhysicalFluidStorage.members(level, min, max);
        var storage = new PhysicalFluidStorage();
        storage.compile(members, ConduitTopology.EMPTY);
        var groups = new HashMap<BlockPos, Integer>();
        var rectangles = new HashSet<Integer>();
        for (int i = 0; i < storage.clusters().size(); i++) {
            var cluster = storage.clusters().get(i);
            if (rectangular(cluster.members().stream()
                    .map(FluidBufferEntity::getBlockPos)
                    .toList())) rectangles.add(i);
            for (var member : cluster.members()) groups.put(member.getBlockPos(), i);
        }
        var result = new ArrayList<Volume>();
        for (var member : members) {
            var pos = member.getBlockPos();
            var mounts = EnumSet.noneOf(Direction.class);
            var bridges = EnumSet.noneOf(Direction.class);
            var block = (FluidBufferBlock) level.getBlockState(pos).getBlock();
            var shell = VatCasingState.from(pos, min, max);
            for (var face : Direction.values()) {
                var neighbor = pos.relative(face);
                if (groups.containsKey(neighbor) && groups.get(pos).equals(groups.get(neighbor))) bridges.add(face);
                else if (level.getBlockState(neighbor).is(MixerContent.block(MixerPartType.Casing))
                        || level.getBlockState(neighbor).is(MixerContent.block(MixerPartType.Glass))
                        || block.cladding(level.getBlockState(pos)) != ConduitCladding.BARE && shell.outward(face))
                    mounts.add(face);
            }
            result.addAll(envelope(
                    pos,
                    !level.getBlockState(pos.below()).isAir(),
                    mounts,
                    bridges,
                    rectangles.contains(groups.get(pos))));
        }
        return List.copyOf(result);
    }

    private FluidBufferGeometry() {}
}
