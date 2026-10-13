package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** Presentation geometry only; never used for placement, collision, routing or capacity. */
public final class FluidBufferArt {
    public static final AABB BODY = new AABB(3. / 16, 3. / 16, 3. / 16, 13. / 16, 13. / 16, 13. / 16);

    public enum Material {
        GLASS,
        METAL
    }

    public record Panel(AABB bounds, Direction face, Material material) {}

    private static final Map<Integer, List<Panel>> CACHE = new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<Panel>> e) {
            return size() > 256;
        }
    };

    public static int bit(Direction face) {
        return 1 << face.ordinal();
    }

    public static boolean has(int mask, Direction face) {
        return (mask & bit(face)) != 0;
    }

    public static AABB extension(Direction face) {
        double lo = 3. / 16, hi = 13. / 16;
        double start = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : 13. / 16;
        double end = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 3. / 16 : 1;
        return switch (face.getAxis()) {
            case X -> new AABB(start, lo, lo, end, hi, hi);
            case Y -> new AABB(lo, start, lo, hi, end, hi);
            case Z -> new AABB(lo, lo, start, hi, hi, end);
        };
    }

    public static List<AABB> volumes(int joins) {
        var boxes = new ArrayList<AABB>();
        boxes.add(BODY);
        for (var face : Direction.values()) if (has(joins, face)) boxes.add(extension(face));
        return List.copyOf(boxes);
    }

    public static synchronized List<Panel> mesh(int joins) {
        return CACHE.computeIfAbsent(joins & 63, FluidBufferArt::bake);
    }

    private static List<Panel> bake(int joins) {
        var boxes = volumes(joins);
        var panels = new ArrayList<Panel>();
        for (var face : Direction.values())
            for (int plane = 0; plane <= 16; plane++) {
                var mask = new int[16][16];
                for (int u = 0; u < 16; u++)
                    for (int v = 0; v < 16; v++) {
                        if (!surface(boxes, joins, face, plane, u + .5, v + .5)) continue;
                        boolean edge = !surface(boxes, joins, face, plane, u - .5, v + .5)
                                || !surface(boxes, joins, face, plane, u + 1.5, v + .5)
                                || !surface(boxes, joins, face, plane, u + .5, v - .5)
                                || !surface(boxes, joins, face, plane, u + .5, v + 1.5);
                        mask[u][v] = edge ? 2 : 1;
                    }
                rectangles(mask, face, plane, panels);
            }
        return List.copyOf(panels);
    }

    private static boolean surface(List<AABB> boxes, int joins, Direction face, int plane, double u, double v) {
        var axis = face.getAxis();
        var ua = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.uAxis(face);
        var va = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.vAxis(face);
        // Continue the face outline across joined block boundaries instead of adding a seam ring.
        for (var tangent : List.of(ua, va)) {
            double value = tangent == ua ? u : v;
            if (value < 0 || value > 16) {
                var direction = Direction.fromAxisAndDirection(
                        tangent, value < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
                if (!has(joins, direction)) return false;
                if (tangent == ua) u = Math.clamp(u, .5, 15.5);
                else v = Math.clamp(v, .5, 15.5);
            }
        }
        if ((plane == 0 || plane == 16) && has(joins, face)) return false;
        double sign = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        var inward = point(axis, (plane - sign * .01) / 16, ua, u / 16, va, v / 16);
        var outward = point(axis, (plane + sign * .01) / 16, ua, u / 16, va, v / 16);
        return boxes.stream().anyMatch(b -> b.contains(inward))
                && !boxes.stream().anyMatch(b -> b.contains(outward));
    }

    private static net.minecraft.world.phys.Vec3 point(
            Direction.Axis axis, double a, Direction.Axis ua, double u, Direction.Axis va, double v) {
        var c = new double[3];
        c[axis.ordinal()] = a;
        c[ua.ordinal()] = u;
        c[va.ordinal()] = v;
        return new net.minecraft.world.phys.Vec3(c[0], c[1], c[2]);
    }

    private static void rectangles(int[][] mask, Direction face, int plane, List<Panel> panels) {
        for (int u = 0; u < 16; u++)
            for (int v = 0; v < 16; v++)
                if (mask[u][v] != 0) {
                    int material = mask[u][v], w = 1, h = 1;
                    while (u + w < 16 && mask[u + w][v] == material) w++;
                    outer:
                    while (v + h < 16) {
                        for (int x = u; x < u + w; x++) if (mask[x][v + h] != material) break outer;
                        h++;
                    }
                    var ua = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.uAxis(face);
                    var va = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.vAxis(face);
                    var a = point(face.getAxis(), plane / 16., ua, u / 16., va, v / 16.);
                    var b = point(face.getAxis(), plane / 16., ua, (u + w) / 16., va, (v + h) / 16.);
                    panels.add(new Panel(new AABB(a, b), face, material == 1 ? Material.GLASS : Material.METAL));
                    for (int x = u; x < u + w; x++) for (int y = v; y < v + h; y++) mask[x][y] = 0;
                }
    }
    /** Shared height integrates the visible connected reservoir, including its bridges. */
    public static double level(Map<BlockPos, Integer> members, double fraction) {
        var changes = new TreeMap<Double, Double>();
        double total = 0;
        for (var entry : members.entrySet())
            for (var b : volumes(entry.getValue())) {
                double area = b.getXsize() * b.getZsize();
                total += area * b.getYsize();
                changes.merge(entry.getKey().getY() + b.minY, area, Double::sum);
                changes.merge(entry.getKey().getY() + b.maxY, -area, Double::sum);
            }
        if (changes.isEmpty()) return 0;
        if (fraction <= 0) return changes.firstKey();
        if (fraction >= 1) return changes.lastKey();
        double remaining = total * fraction, previous = changes.firstKey(), area = 0;
        for (var change : changes.entrySet()) {
            double volume = (change.getKey() - previous) * area;
            if (area > 0 && remaining <= volume) return previous + remaining / area;
            remaining -= volume;
            area += change.getValue();
            previous = change.getKey();
        }
        return changes.lastKey();
    }
    /** Clip the cached surface and add one horizontal surface through the union. */
    public static List<Panel> liquid(int joins, double height) {
        if (height <= 0) return List.of();
        var panels = new ArrayList<Panel>();
        double epsilon = .25 / 16;
        double bottom = has(joins, Direction.DOWN) ? 0 : BODY.minY, top = has(joins, Direction.UP) ? 1 : BODY.maxY;
        if (height <= bottom) return List.of();
        if (!has(joins, Direction.UP)) height = Math.min(height, top - epsilon);
        if (!has(joins, Direction.DOWN) && height < bottom + epsilon)
            height = bottom + epsilon + (height - bottom) / 1000;
        for (var panel : mesh(joins)) {
            var b = panel.bounds();
            if (b.minY > height || b.minY == height && panel.face() != Direction.UP) continue;
            double[] f = {b.minX, b.minY, b.minZ}, t = {b.maxX, Math.min(b.maxY, height), b.maxZ};
            for (int axis = 0; axis < 3; axis++) {
                // Inset outer glass surfaces, leaving shared block boundaries exactly aligned.
                for (int end = 0; end < 2; end++) {
                    var p = end == 0 ? f : t;
                    if (p[axis] == 3. / 16) p[axis] += epsilon;
                    else if (p[axis] == 13. / 16) p[axis] -= epsilon;
                }
            }
            if (t[1] < f[1]) continue;
            panels.add(new Panel(new AABB(f[0], f[1], f[2], t[0], t[1], t[2]), panel.face(), Material.GLASS));
        }
        // Cross-sections are disjoint body/arm rectangles; no internal liquid walls.
        for (var b : volumes(joins))
            if (height > b.minY && height < b.maxY) {
                double x0 = b.minX == 3. / 16 ? b.minX + epsilon : b.minX,
                        x1 = b.maxX == 13. / 16 ? b.maxX - epsilon : b.maxX;
                double z0 = b.minZ == 3. / 16 ? b.minZ + epsilon : b.minZ,
                        z1 = b.maxZ == 13. / 16 ? b.maxZ - epsilon : b.maxZ;
                // Horizontal arms meet the inset body without a gap.
                if (b.maxX == 3. / 16) x1 = b.maxX + epsilon;
                if (b.minX == 13. / 16) x0 = b.minX - epsilon;
                if (b.maxZ == 3. / 16) z1 = b.maxZ + epsilon;
                if (b.minZ == 13. / 16) z0 = b.minZ - epsilon;
                panels.add(new Panel(new AABB(x0, height, z0, x1, height, z1), Direction.UP, Material.GLASS));
            }
        return List.copyOf(panels);
    }

    private FluidBufferArt() {}
}
