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
        return FluidBufferShape.volumes(joins, BODY);
    }

    public static synchronized List<Panel> mesh(int joins) {
        return CACHE.computeIfAbsent(joins & FluidBufferShape.MASK, FluidBufferArt::bake);
    }

    private static List<Panel> bake(int joins) {
        var panels = new ArrayList<Panel>();
        for (var face : Direction.values())
            for (int plane : new int[] {0, 3, 13, 16}) {
                var mask = new int[16][16];
                for (int u = 0; u < 16; u++)
                    for (int v = 0; v < 16; v++) {
                        if (!surface(joins, face, plane, u + .5, v + .5)) continue;
                        boolean edge = !surface(joins, face, plane, u - .5, v + .5)
                                || !surface(joins, face, plane, u + 1.5, v + .5)
                                || !surface(joins, face, plane, u + .5, v - .5)
                                || !surface(joins, face, plane, u + .5, v + 1.5);
                        mask[u][v] = edge ? 2 : 1;
                    }
                rectangles(mask, face, plane, panels);
            }
        return List.copyOf(panels);
    }

    private static boolean surface(int joins, Direction face, int plane, double u, double v) {
        var axis = face.getAxis();
        var ua = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.uAxis(face);
        var va = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.vAxis(face);
        double sign = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        var inward = point(axis, (plane - sign * .01) / 16, ua, u / 16, va, v / 16);
        var outward = point(axis, (plane + sign * .01) / 16, ua, u / 16, va, v / 16);
        return FluidBufferShape.contains(joins, inward, BODY) && !FluidBufferShape.contains(joins, outward, BODY);
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
    /** Inset the same cluster union, then clip it at the shared liquid height. */
    public static List<Panel> liquid(int joins, double height) {
        if (height <= 0) return List.of();
        var panels = new ArrayList<Panel>();
        double epsilon = .25 / 16;
        double bottom = has(joins, Direction.DOWN) ? 0 : BODY.minY, top = has(joins, Direction.UP) ? 1 : BODY.maxY;
        if (height <= bottom) return List.of();
        if (!has(joins, Direction.UP)) height = Math.min(height, top - epsilon);
        if (!has(joins, Direction.DOWN) && height < bottom + epsilon)
            height = bottom + epsilon + (height - bottom) / 1000;
        var body = BODY.deflate(epsilon);
        var cuts = new ArrayList<List<Double>>();
        for (var axis : Direction.Axis.values()) {
            var values = new TreeSet<>(List.of(0., body.min(axis), body.max(axis), 1.));
            if (axis == Direction.Axis.Y && height > 0 && height < 1) values.add(height);
            cuts.add(List.copyOf(values));
        }
        for (var face : Direction.values()) {
            var axis = face.getAxis();
            var ua = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.uAxis(face);
            var va = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.vAxis(face);
            var us = cuts.get(ua.ordinal());
            var vs = cuts.get(va.ordinal());
            double sign = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
            for (double plane : cuts.get(axis.ordinal()))
                for (int u = 0; u + 1 < us.size(); u++)
                    for (int v = 0; v + 1 < vs.size(); v++) {
                        double midU = (us.get(u) + us.get(u + 1)) / 2;
                        double midV = (vs.get(v) + vs.get(v + 1)) / 2;
                        var inside = point(axis, plane - sign * 1e-6, ua, midU, va, midV);
                        var outside = point(axis, plane + sign * 1e-6, ua, midU, va, midV);
                        if (inside.y > height
                                || !FluidBufferShape.contains(joins, inside, body)
                                || outside.y <= height && FluidBufferShape.contains(joins, outside, body)) continue;
                        panels.add(new Panel(
                                new AABB(
                                        point(axis, plane, ua, us.get(u), va, vs.get(v)),
                                        point(axis, plane, ua, us.get(u + 1), va, vs.get(v + 1))),
                                face,
                                Material.GLASS));
                    }
        }
        return List.copyOf(panels);
    }

    private FluidBufferArt() {}
}
