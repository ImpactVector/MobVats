package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** Local occupancy of one storage cluster, including the neighbors needed to close edge/corner junctions. */
public final class FluidBufferShape {
    public static final int MASK = (1 << 26) - 1;
    private static final int[][][] BITS = new int[3][3][3];

    static {
        for (var face : Direction.values())
            BITS[face.getStepX() + 1][face.getStepY() + 1][face.getStepZ() + 1] = 1 << face.ordinal();
        int index = 6;
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > 1) BITS[x + 1][y + 1][z + 1] = 1 << index++;
    }

    public static int neighbors(Set<BlockPos> members, BlockPos pos) {
        int mask = 0;
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (members.contains(pos.offset(x, y, z))) mask |= BITS[x + 1][y + 1][z + 1];
        return mask;
    }

    private static boolean member(int mask, int x, int y, int z) {
        return x == 0 && y == 0 && z == 0
                || x >= -1
                        && x <= 1
                        && y >= -1
                        && y <= 1
                        && z >= -1
                        && z <= 1
                        && (mask & BITS[x + 1][y + 1][z + 1]) != 0;
    }

    public static boolean contains(int mask, Vec3 point, AABB body) {
        int[] low = new int[3], high = new int[3];
        double[] p = {point.x, point.y, point.z};
        for (var axis : Direction.Axis.values()) {
            int a = axis.ordinal(), cell = (int) Math.floor(p[a]);
            double fraction = p[a] - cell;
            low[a] = cell - (fraction < body.min(axis) ? 1 : 0);
            high[a] = cell + (fraction > body.max(axis) ? 1 : 0);
        }
        for (int x = low[0]; x <= high[0]; x++)
            for (int y = low[1]; y <= high[1]; y++)
                for (int z = low[2]; z <= high[2]; z++) if (!member(mask, x, y, z)) return false;
        return true;
    }

    /** Disjoint tiles of the inset union. Junctions close only when every participating member exists. */
    public static List<AABB> tiles(int mask, AABB body, boolean cornersOnly) {
        var result = new ArrayList<AABB>();
        double[][] cuts = {{0, body.minX, body.maxX, 1}, {0, body.minY, body.maxY, 1}, {0, body.minZ, body.maxZ, 1}};
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) {
                    if (cornersOnly && (x != 1 ? 1 : 0) + (y != 1 ? 1 : 0) + (z != 1 ? 1 : 0) < 2) continue;
                    var box = new AABB(
                            cuts[0][x], cuts[1][y], cuts[2][z], cuts[0][x + 1], cuts[1][y + 1], cuts[2][z + 1]);
                    if (contains(mask, box.getCenter(), body)) result.add(box);
                }
        return result;
    }

    public static List<AABB> volumes(int mask, AABB body) {
        var result = new ArrayList<>(tiles(mask, body, false));
        for (var axis : Direction.Axis.values())
            for (int i = 0; i < result.size(); i++)
                for (int j = i + 1; j < result.size(); j++) {
                    var a = result.get(i);
                    var b = result.get(j);
                    boolean adjacent = a.max(axis) == b.min(axis) || b.max(axis) == a.min(axis);
                    for (var tangent : Direction.Axis.values())
                        if (tangent != axis && (a.min(tangent) != b.min(tangent) || a.max(tangent) != b.max(tangent)))
                            adjacent = false;
                    if (adjacent) {
                        result.set(i, a.minmax(b));
                        result.remove(j);
                        j = i;
                    }
                }
        return List.copyOf(result);
    }

    private FluidBufferShape() {}
}
