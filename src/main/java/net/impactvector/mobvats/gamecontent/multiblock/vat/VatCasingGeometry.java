package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/** Pixel-exact render geometry, independent of client classes and collision/validation. */
public final class VatCasingGeometry {
    public static final int PIXELS = 16, DEPTH = 3, EDGE = 1;

    public enum Material {
        FACE,
        EDGE,
        INNER
    }

    public record Patch(
            Direction normal, int plane, int u0, int v0, int u1, int v1, Material material, Direction edgeDirection) {}

    private record Surface(Material material, Direction edgeDirection) {}

    private static final int LOW = 0, HIGH = PIXELS, SIZE = HIGH - LOW;

    public static List<Patch> build(VatCasingState state) {
        return build(state, null);
    }
    /** Render-only opening, independent of collision and machine validation. */
    public static List<Patch> build(VatCasingState state, Direction opening) {
        if (!state.formed()) return List.of();
        if (opening != null && !state.outward(opening)) opening = null;
        var patches = new ArrayList<Patch>();
        var solid = trim(state);
        if (opening != null && state.outward(opening)) {
            for (int layer = 0; layer < PIXELS; layer++)
                for (int u = 3; u < 13; u++)
                    for (int v = 3; v < 13; v++) {
                        var xyz = coordinates(opening, layer, u, v);
                        solid[xyz[0]][xyz[1]][xyz[2]] = false;
                    }
        }
        for (var face : Direction.values())
            for (int layer = LOW; layer < HIGH; layer++) {
                var cells = new Surface[SIZE][SIZE];
                for (int u = LOW; u < HIGH; u++)
                    for (int v = LOW; v < HIGH; v++) {
                        var xyz = coordinates(face, layer, u, v);
                        if (!occupied(solid, xyz)) continue;
                        var next = xyz.clone();
                        next[axis(face.getAxis())] += face.getAxisDirection().getStep();
                        if (occupied(solid, next)) continue;
                        // The sleeve owns the opening walls. Do not add coincident shell reveals.
                        if (opening != null
                                && face.getAxis() != opening.getAxis()
                                && next[axis(VatCasingState.uAxis(opening))] >= 3
                                && next[axis(VatCasingState.uAxis(opening))] < 13
                                && next[axis(VatCasingState.vAxis(opening))] >= 3
                                && next[axis(VatCasingState.vAxis(opening))] < 13) continue;
                        int plane = layer + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                        cells[u - LOW][v - LOW] = material(state, face, xyz);
                    }
                merge(
                        patches,
                        cells,
                        face,
                        layer + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0));
            }
        return List.copyOf(patches);
    }

    private static boolean[][][] trim(VatCasingState state) {
        var solid = new boolean[SIZE][SIZE][SIZE];
        // Formed casing is a 3px shell measured from each OUTER vat plane.
        // Union perpendicular panels; do not fill their chamber-side cavity.
        for (var face : Direction.values())
            if (state.outward(face)) {
                var low = new int[] {0, 0, 0};
                var high = new int[] {PIXELS, PIXELS, PIXELS};
                int normal = axis(face.getAxis());
                if (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE) high[normal] = DEPTH;
                else low[normal] = PIXELS - DEPTH;
                fill(solid, low, high);
            }
        return solid;
    }

    private static void fill(boolean[][][] solid, int[] low, int[] high) {
        for (int x = low[0]; x < high[0]; x++)
            for (int y = low[1]; y < high[1]; y++)
                for (int z = low[2]; z < high[2]; z++) solid[x - LOW][y - LOW][z - LOW] = true;
    }

    private static Surface material(VatCasingState state, Direction normal, int[] xyz) {
        // Exposed shell ends retain the actual machine-edge direction.
        for (var face : Direction.values())
            if (state.outward(face) && face.getAxis() != normal.getAxis()) {
                int edgeCell = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : PIXELS - EDGE;
                if (xyz[axis(face.getAxis())] == edgeCell) return new Surface(Material.EDGE, face);
            }
        if (state.outward(normal)) {
            int mask = state.outerEdges(normal);
            var u = VatCasingState.uAxis(normal);
            var v = VatCasingState.vAxis(normal);
            if ((mask & VatGlassState.LEFT) != 0 && xyz[axis(u)] == 0)
                return new Surface(Material.EDGE, direction(u, false));
            if ((mask & VatGlassState.RIGHT) != 0 && xyz[axis(u)] == PIXELS - 1)
                return new Surface(Material.EDGE, direction(u, true));
            if ((mask & VatGlassState.BOTTOM) != 0 && xyz[axis(v)] == 0)
                return new Surface(Material.EDGE, direction(v, false));
            if ((mask & VatGlassState.TOP) != 0 && xyz[axis(v)] == PIXELS - 1)
                return new Surface(Material.EDGE, direction(v, true));
        }
        var support = normal.getOpposite();
        int innerCell = support.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? DEPTH - 1 : PIXELS - DEPTH;
        boolean inner = state.outward(support) && xyz[axis(normal.getAxis())] == innerCell;
        return new Surface(inner ? Material.INNER : Material.FACE, null);
    }

    private static void merge(List<Patch> patches, Surface[][] cells, Direction normal, int plane) {
        for (int v = 0; v < SIZE; v++)
            for (int u = 0; u < SIZE; u++) {
                var surface = cells[u][v];
                if (surface == null) continue;
                int endU = u + 1;
                while (endU < SIZE && surface.equals(cells[endU][v])) ++endU;
                int endV = v + 1;
                outer:
                while (endV < SIZE) {
                    for (int x = u; x < endU; x++) if (!surface.equals(cells[x][endV])) break outer;
                    ++endV;
                }
                patches.add(new Patch(
                        normal,
                        plane,
                        u + LOW,
                        v + LOW,
                        endU + LOW,
                        endV + LOW,
                        surface.material(),
                        surface.edgeDirection()));
                for (int x = u; x < endU; x++) for (int y = v; y < endV; y++) cells[x][y] = null;
            }
    }

    private static int[] coordinates(Direction face, int normal, int u, int v) {
        var result = new int[3];
        result[axis(face.getAxis())] = normal;
        result[axis(VatCasingState.uAxis(face))] = u;
        result[axis(VatCasingState.vAxis(face))] = v;
        return result;
    }

    private static boolean occupied(boolean[][][] solid, int[] xyz) {
        for (int value : xyz) if (value < LOW || value >= HIGH) return false;
        return solid[xyz[0] - LOW][xyz[1] - LOW][xyz[2] - LOW];
    }

    public static int axis(Direction.Axis axis) {
        return axis == Direction.Axis.X ? 0 : axis == Direction.Axis.Y ? 1 : 2;
    }

    private static Direction direction(Direction.Axis axis, boolean positive) {
        return Direction.fromAxisAndDirection(
                axis, positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    private VatCasingGeometry() {}
}
