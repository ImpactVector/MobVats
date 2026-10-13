package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** Immutable physical polygon, cached by diameter. All coordinates are relative to shaft center. */
public final class MixerVesselGeometry {
    public static final double SAFETY_CLEARANCE = 1.0 / 64;
    public static final int SHAFT_WIDTH_PIXELS = 6;
    public static final double SHAFT_WIDTH = SHAFT_WIDTH_PIXELS / 16.0;
    public static final double SHAFT_HALF_WIDTH = SHAFT_WIDTH / 2;
    public static final int SHELL_DEPTH_PIXELS =
            net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.DEPTH;
    public static final double ARM_WIDTH = .14;
    public static final double ARM_CLEARANCE = Math.sqrt(2) * ARM_WIDTH / 2 + 1.0 / 1024;
    public static final double REFERENCE_FACE_RADIUS = deriveReferenceRadius();
    private static final Map<Integer, MixerVesselGeometry> CACHE = createCache();
    private final int diameter, radiusReduction, sides, gridRadiusPixels;
    private final double faceRadius, vertexRadius;
    private final List<Vec3> normals, vertices;
    private final BitSet columns;

    private MixerVesselGeometry(int diameter, int radiusReduction) {
        this.diameter = diameter;
        this.radiusReduction = radiusReduction;
        sides = 4 * (diameter - 1);
        faceRadius = REFERENCE_FACE_RADIUS * (diameter - 1) / 4.0 - radiusReduction;
        vertexRadius = faceRadius / Math.cos(Math.PI / sides);
        normals = normals(sides);
        var points = new ArrayList<Vec3>();
        for (int i = 0; i < sides; i++) {
            double angle = (i + .5) * 2 * Math.PI / sides;
            points.add(new Vec3(vertexRadius * Math.cos(angle), 0, vertexRadius * Math.sin(angle)));
        }
        vertices = List.copyOf(points);
        gridRadiusPixels = (int) Math.ceil(faceRadius * 16);
        columns = new BitSet(gridSide() * gridSide());
        for (int z = 0; z < gridSide(); z++)
            for (int x = 0; x < gridSide(); x++) {
                double dx = (x - gridRadiusPixels + .5) / 16, dz = (z - gridRadiusPixels + .5) / 16;
                if (contains(dx, dz) && !shaftContains(dx, dz)) columns.set(z * gridSide() + x);
            }
    }

    public static MixerVesselGeometry forDiameter(int diameter) {
        return forDiameter(diameter, 0);
    }

    public static MixerVesselGeometry forDiameter(int diameter, int radiusReduction) {
        var value = CACHE.get(diameter * 16 + radiusReduction);
        if (diameter < 3 || diameter > 9 || diameter % 2 == 0 || radiusReduction < 0 || value == null)
            throw new IllegalArgumentException("Unsupported Mixer vessel: " + diameter + " / " + radiusReduction);
        return value;
    }

    private static Map<Integer, MixerVesselGeometry> createCache() {
        var result = new HashMap<Integer, MixerVesselGeometry>();
        for (int d : List.of(3, 5, 7, 9))
            for (int shrink = 0;
                    REFERENCE_FACE_RADIUS * (d - 1) / 4.0 - shrink > SHAFT_HALF_WIDTH + ARM_CLEARANCE;
                    shrink++) result.put(d * 16 + shrink, new MixerVesselGeometry(d, shrink));
        return Map.copyOf(result);
    }

    public static Optional<MixerVesselGeometry> reduced(int diameter, int radiusReduction) {
        return Optional.ofNullable(CACHE.get(diameter * 16 + radiusReduction));
    }

    public int diameter() {
        return diameter;
    }

    public int radiusReduction() {
        return radiusReduction;
    }

    public int sides() {
        return sides;
    }

    public double faceRadius() {
        return faceRadius;
    }

    public double vertexRadius() {
        return vertexRadius;
    }

    public int gridRadiusPixels() {
        return gridRadiusPixels;
    }

    public int gridSide() {
        return 2 * gridRadiusPixels;
    }

    public List<Vec3> vertices() {
        return vertices;
    }

    public boolean contains(double x, double z) {
        for (var n : normals) if (n.x * x + n.z * z > faceRadius + 1e-12) return false;
        return true;
    }

    public static boolean shaftContains(double x, double z) {
        return Math.abs(x) < SHAFT_HALF_WIDTH && Math.abs(z) < SHAFT_HALF_WIDTH;
    }

    public boolean sweptColumn(int x, int z) {
        return x >= 0 && z >= 0 && x < gridSide() && z < gridSide() && columns.get(z * gridSide() + x);
    }

    public int sweptColumns() {
        return columns.cardinality();
    }

    private static List<Vec3> normals(int sides) {
        var result = new ArrayList<Vec3>();
        for (int i = 0; i < sides; i++) {
            double angle = i * 2 * Math.PI / sides;
            result.add(new Vec3(Math.cos(angle), 0, Math.sin(angle)));
        }
        return List.copyOf(result);
    }
    /** Exact separating-axis test in X/Z: rectangle axes are included among cardinal polygon normals. */
    private static double limitingRadius(List<Vec3> normals, AABB box) {
        double limit = Double.NEGATIVE_INFINITY;
        for (var n : normals)
            limit = Math.max(limit, n.x * (n.x >= 0 ? box.minX : box.maxX) + n.z * (n.z >= 0 ? box.minZ : box.maxZ));
        return limit;
    }

    public boolean intersects(AABB box) {
        return limitingRadius(normals, box) < faceRadius - 1e-12;
    }

    public boolean clears(Collection<AABB> boxes) {
        return boxes.stream().noneMatch(b -> intersects(b.inflate(SAFETY_CLEARANCE, 0, SAFETY_CLEARANCE)));
    }

    public double clearanceLimit(AABB box) {
        return limitingRadius(normals, box);
    }

    public static List<AABB> referenceBuffer(int diameter, int x, int z) {
        int half = (diameter - 1) / 2;
        var mounts = EnumSet.noneOf(Direction.class);
        // Outward shell mounts and adjoining shell panels; support is also reserved.
        if (x == -half) mounts.add(Direction.WEST);
        if (x == half) mounts.add(Direction.EAST);
        if (z == -half) mounts.add(Direction.NORTH);
        if (z == half) mounts.add(Direction.SOUTH);
        for (var face : Direction.Plane.HORIZONTAL) {
            int nx = x + face.getStepX(), nz = z + face.getStepZ();
            if (Math.abs(nx) <= half && Math.abs(nz) <= half && (Math.abs(nx) == half || Math.abs(nz) == half))
                mounts.add(face);
        }
        if (Math.abs(x) == half || Math.abs(z) == half) {
            mounts.add(Direction.UP);
            mounts.add(Direction.DOWN);
        }
        return FluidBufferGeometry.envelope(new BlockPos(x, 0, z), true, mounts, Set.of()).stream()
                .map(v -> v.bounds().move(-.5, 0, -.5))
                .toList();
    }
    /** Actual riser geometry, including collars. Both shell materials use the same occupied mesh. */
    public static List<AABB> referenceConduit(int diameter, int x, int z, ConduitCladding cladding, ConduitMode mode) {
        int half = (diameter - 1) / 2;
        var cells = new ArrayList<ConduitGeometry.Cell>();
        for (int y = -1; y <= 1; y++) {
            var attachments = new ArrayList<ConduitGeometry.Attachment>();
            // Reserve side service fittings to neighboring shell endpoints, including elbow boxes.
            if (mode == ConduitMode.TAP) {
                if (Math.abs(x) == half && z != 0)
                    attachments.add(new ConduitGeometry.Attachment(
                            new BlockPos(x, y, z - Integer.signum(z)), EndpointGeometry.Kind.PASSIVE));
                if (Math.abs(z) == half && x != 0)
                    attachments.add(new ConduitGeometry.Attachment(
                            new BlockPos(x - Integer.signum(x), y, z), EndpointGeometry.Kind.PASSIVE));
            }
            cells.add(new ConduitGeometry.Cell(
                    new BlockPos(x, y, z),
                    ConduitResource.FLUID,
                    cladding,
                    mode,
                    (y > -1 ? 1 << Direction.DOWN.ordinal() : 0) | (y < 1 ? 1 << Direction.UP.ordinal() : 0),
                    attachments));
        }
        var geometry = ConduitGeometry.bake(
                new ConduitGeometry.Snapshot(new BlockPos(-half, -2, -half), new BlockPos(half, 2, half), cells));
        return geometry.occupied().stream()
                .map(v -> v.bounds().move(-.5, 0, -.5))
                .toList();
    }

    private static double deriveReferenceRadius() {
        var normals = normals(16);
        double radius = Double.POSITIVE_INFINITY;
        for (int x : new int[] {-2, 2})
            for (int z : new int[] {-2, 2})
                for (var box : referenceBuffer(5, x, z))
                    radius = Math.min(
                            radius, limitingRadius(normals, box.inflate(SAFETY_CLEARANCE, 0, SAFETY_CLEARANCE)));
        for (int sign : new int[] {-1, 1})
            for (int offset : new int[] {-1, 1})
                for (boolean swap : new boolean[] {false, true})
                    for (var mode : ConduitMode.values())
                        for (var cladding : List.of(ConduitCladding.CASING, ConduitCladding.GLASS))
                            for (var box : referenceConduit(
                                    5, swap ? offset : 2 * sign, swap ? 2 * sign : offset, cladding, mode))
                                radius = Math.min(
                                        radius,
                                        limitingRadius(normals, box.inflate(SAFETY_CLEARANCE, 0, SAFETY_CLEARANCE)));
        // Cardinal shell panels must remain outside the physical vessel, too.
        return Math.min(radius, 2.5 - SHELL_DEPTH_PIXELS / 16.0 - SAFETY_CLEARANCE);
    }
}
