package net.impactvector.mobvats.gamecontent.mob;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A short suspension curve: endpoint interpolation minus a restrained parabolic sag. */
public record CableCurve(List<Vec3> points) {
    public CableCurve {
        points = List.copyOf(points);
        if (points.size() < 3 || points.size() > 65) throw new IllegalArgumentException("A cable needs 2 to 64 segments");
        for (int i = 0; i < points.size(); i++) {
            if (!finite(points.get(i)) || i > 0 && points.get(i).distanceToSqr(points.get(i - 1)) < 1.0e-12)
                throw new IllegalArgumentException("Cable points must be finite and distinct");
        }
    }
    public static CableCurve between(Vec3 start, Vec3 end, double sag, int segments) {
        if (segments < 2 || segments > 64 || !Double.isFinite(sag) || sag < 0
                || !finite(start) || !finite(end) || start.distanceToSqr(end) < 1.0e-12) {
            throw new IllegalArgumentException("Invalid cable curve parameters");
        }
        var points = new ArrayList<Vec3>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double)i / segments;
            points.add(start.lerp(end, t).add(0, -4 * sag * t * (1 - t), 0));
        }
        return new CableCurve(points);
    }
    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
