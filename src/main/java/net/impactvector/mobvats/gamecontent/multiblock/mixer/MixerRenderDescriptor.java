package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable visual mesh in Impeller-bottom coordinates; physical polygon and capacity remain authoritative. */
public record MixerRenderDescriptor(
        MixerAssembly assembly,
        AABB tankBounds,
        Vec3 shaftTop,
        Vec3 shaftBottom,
        double armY,
        double armReach,
        List<Face> walls,
        List<Face> interior,
        List<Face> bottom,
        List<Face> rim,
        List<Vec3> liquidBoundary) {
    public static final double ARM_WIDTH = MixerVesselGeometry.ARM_WIDTH;
    public static final double WALL_THICKNESS = 1.0 / 32;
    public static final double HEADROOM = 1.0 / 8;
    /** Presentation only: expose the upper frame without changing installed mixing depth. */
    public static final double RIM_Y = -.625;

    public record Face(List<Vec3> vertices, Vec3 normal) {
        public Face {
            vertices = List.copyOf(vertices);
        }

        public AABB bounds() {
            double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0, x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
            for (var p : vertices) {
                x0 = Math.min(x0, p.x);
                y0 = Math.min(y0, p.y);
                z0 = Math.min(z0, p.z);
                x1 = Math.max(x1, p.x);
                y1 = Math.max(y1, p.y);
                z1 = Math.max(z1, p.z);
            }
            return new AABB(x0, y0, z0, x1, y1, z1);
        }
    }

    public MixerRenderDescriptor {
        walls = List.copyOf(walls);
        interior = List.copyOf(interior);
        bottom = List.copyOf(bottom);
        rim = List.copyOf(rim);
        liquidBoundary = List.copyOf(liquidBoundary);
    }

    public static MixerRenderDescriptor from(MixerAssembly assembly) {
        var vessel = assembly.vessel();
        var outer = vessel.vertices().stream().map(p -> at(p, RIM_Y)).toList();
        var inner = inset(outer, vessel.faceRadius(), WALL_THICKNESS);
        var liquid = inset(outer, vessel.faceRadius(), WALL_THICKNESS + 1.0 / 1024);
        var walls = new ArrayList<Face>();
        var interior = new ArrayList<Face>();
        var rim = new ArrayList<Face>();
        double bottomY = assembly.vesselBottom(), insideY = bottomY + WALL_THICKNESS;
        for (int i = 0; i < outer.size(); i++) {
            int j = (i + 1) % outer.size();
            var a = outer.get(i);
            var b = outer.get(j);
            var c = inner.get(i);
            var d = inner.get(j);
            var normal = new Vec3(b.z - a.z, 0, a.x - b.x).normalize();
            walls.add(new Face(List.of(at(a, bottomY), a, b, at(b, bottomY)), normal));
            interior.add(new Face(List.of(at(d, insideY), d, c, at(c, insideY)), normal.scale(-1)));
            rim.add(new Face(List.of(a, c, d, b), new Vec3(0, 1, 0)));
        }
        var bottom = new ArrayList<Face>();
        bottom.addAll(fan(inner, insideY, true));
        bottom.addAll(fan(outer, bottomY, false));
        double r = vessel.faceRadius();
        return new MixerRenderDescriptor(
                assembly,
                new AABB(-r, bottomY, -r, r, RIM_Y, r),
                Vec3.ZERO,
                new Vec3(0, insideY, 0),
                -.5,
                Math.max(0, r - MixerVesselGeometry.ARM_CLEARANCE - WALL_THICKNESS),
                walls,
                interior,
                bottom,
                rim,
                liquid);
    }

    private static List<Vec3> inset(List<Vec3> vertices, double apothem, double distance) {
        return vertices.stream()
                .map(p -> new Vec3(p.x * (apothem - distance) / apothem, p.y, p.z * (apothem - distance) / apothem))
                .toList();
    }

    private static Vec3 at(Vec3 p, double y) {
        return new Vec3(p.x, y, p.z);
    }

    private static List<Face> fan(List<Vec3> boundary, double y, boolean up) {
        var result = new ArrayList<Face>();
        var center = new Vec3(0, y, 0);
        var normal = new Vec3(0, up ? 1 : -1, 0);
        for (int i = 0; i < boundary.size(); i++) {
            var a = at(boundary.get(i), y);
            var b = at(boundary.get((i + 1) % boundary.size()), y);
            result.add(new Face(up ? List.of(center, b, a) : List.of(center, a, b), normal));
        }
        return List.copyOf(result);
    }
    /** Geometric rendering diagnostics only; independent of slot capacity; this grid does not construct render surfaces. */
    public static boolean occupied(MixerAssembly assembly, int x, int y, int z) {
        return y >= assembly.floorInsetPixels()
                && y < assembly.shaftLength() * 16
                && assembly.vessel().sweptColumn(x, z);
    }
    /** One bucket per block of cached usable swept volume; pixel-layer cache limits mesh updates. */
    public int liquidLayer(int millibuckets) {
        if (millibuckets <= 0) return 0;
        double height = millibuckets / 1000.0 / (assembly.vessel().sweptColumns() / 256.0);
        return Math.min(
                assembly.shaftLength() * 16 - 2,
                Math.max(assembly.floorInsetPixels() + 1, assembly.floorInsetPixels() + (int) Math.ceil(height * 16)));
    }

    public List<Face> liquidSurface(int layer) {
        if (layer <= assembly.floorInsetPixels() || layer > assembly.shaftLength() * 16 - 2) return List.of();
        // Preserve the existing volume/quantization cache, remapping only its displayed Y range.
        double fraction = (layer - assembly.floorInsetPixels())
                / (double) (assembly.shaftLength() * 16 - 2 - assembly.floorInsetPixels());
        double floor = shaftBottom.y;
        return fan(liquidBoundary, floor + fraction * (RIM_Y - HEADROOM - floor), true);
    }
}
