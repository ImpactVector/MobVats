package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.List;
import net.impactvector.mobvats.gamecontent.mob.CableCurve;
import net.minecraft.world.phys.Vec3;

/** Polyline with a conservative square cross-section envelope, in world-space blocks. */
public record PhysicalRoute(List<Vec3> points, double radius) {
    public PhysicalRoute {
        points = List.copyOf(points);
        if (points.size() < 2 || !Double.isFinite(radius) || radius < 0)
            throw new IllegalArgumentException("Invalid physical route");
        for (var p : points)
            if (!Double.isFinite(p.lengthSqr())) throw new IllegalArgumentException("Non-finite route point");
    }

    public static PhysicalRoute line(Vec3 start, Vec3 end, double radius) {
        return new PhysicalRoute(List.of(start, end), radius);
    }

    public static PhysicalRoute cable(CableCurve curve, double width) {
        return new PhysicalRoute(curve.points(), width / Math.sqrt(2));
    }
}
