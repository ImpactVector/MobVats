package net.impactvector.mobvats.render;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** CPU contracts plus preview scenes exported from the production reservoir mesh. */
public final class FluidBufferArtGeometryTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static double area(FluidBufferArt.Panel p) {
        var b = p.bounds();
        return switch (p.face().getAxis()) {
            case X -> b.getYsize() * b.getZsize();
            case Y -> b.getXsize() * b.getZsize();
            case Z -> b.getXsize() * b.getYsize();
        };
    }

    private static Set<Direction> directions(int mask) {
        var result = EnumSet.noneOf(Direction.class);
        for (var face : Direction.values()) if (FluidBufferArt.has(mask, face)) result.add(face);
        return result;
    }

    private static List<Map<String, Object>> elements(List<FluidBufferArt.Panel> panels, boolean liquid) {
        var result = new ArrayList<Map<String, Object>>();
        for (var p : panels) {
            var b = p.bounds();
            result.add(Map.of(
                    "from",
                    List.of(b.minX * 16, b.minY * 16, b.minZ * 16),
                    "to",
                    List.of(b.maxX * 16, b.maxY * 16, b.maxZ * 16),
                    "faces",
                    Map.of(
                            p.face().getName(),
                            Map.of(
                                    "texture",
                                    liquid
                                            ? "#liquid"
                                            : p.material() == FluidBufferArt.Material.GLASS ? "#glass" : "#metal"))));
        }
        return result;
    }

    public static void verify(Map<String, Object> examples) {
        require(
                FluidBufferArt.BODY.equals(new AABB(3. / 16, 3. / 16, 3. / 16, 13. / 16, 13. / 16, 13. / 16)),
                "Centered ten-pixel reservoir");
        var bare = FluidBufferArt.mesh(0);
        for (var face : Direction.values()) {
            require(
                    Math.abs(bare.stream()
                                            .filter(p -> p.face() == face)
                                            .mapToDouble(FluidBufferArtGeometryTest::area)
                                            .sum()
                                    - 100. / 256)
                            < 1e-12,
                    "Unequal face area");
            require(
                    Math.abs(bare.stream()
                                            .filter(p ->
                                                    p.face() == face && p.material() == FluidBufferArt.Material.GLASS)
                                            .mapToDouble(FluidBufferArtGeometryTest::area)
                                            .sum()
                                    - 64. / 256)
                            < 1e-12,
                    "Unequal glass face");
            var join = FluidBufferArt.extension(face);
            require(Math.abs(join.getSize() - (10. + 10 + 3) / 48) < 1e-12, "Rotated bridge dimensions");
            double joinedArea = FluidBufferArt.mesh(FluidBufferArt.bit(face)).stream()
                    .mapToDouble(FluidBufferArtGeometryTest::area)
                    .sum();
            require(Math.abs(joinedArea - 620. / 256) < 1e-12, "Six-axis joined surface differs");
            for (var panel : FluidBufferArt.liquid(FluidBufferArt.bit(face), .5)) {
                if (panel.face() == face)
                    require(
                            panel.bounds().min(face.getAxis()) != 0
                                    && panel.bounds().min(face.getAxis()) != 1,
                            "Liquid has shared-boundary end cap");
            }
        }
        for (int joins = 0; joins < 64; joins++) {
            var mesh = FluidBufferArt.mesh(joins);
            require(mesh == FluidBufferArt.mesh(joins), "Geometry cache miss");
            var reserved = FluidBufferGeometry.envelope(BlockPos.ZERO, false, Set.of(), directions(joins));
            for (var box : FluidBufferArt.volumes(joins)) {
                // Every visual voxel is in the union of the unchanged mechanical volumes.
                for (double x = box.minX + .5 / 16; x < box.maxX; x += 1. / 16)
                    for (double y = box.minY + .5 / 16; y < box.maxY; y += 1. / 16)
                        for (double z = box.minZ + .5 / 16; z < box.maxZ; z += 1. / 16) {
                            var point = new net.minecraft.world.phys.Vec3(x, y, z);
                            require(
                                    reserved.stream().anyMatch(v -> v.bounds().contains(point)),
                                    "Art exceeds physical envelope");
                        }
            }
            for (var panel : mesh) {
                require(area(panel) > 0, "Degenerate reservoir panel");
                var b = panel.bounds();
                double plane = b.min(panel.face().getAxis());
                if (FluidBufferArt.has(joins, panel.face())) require(plane != 0 && plane != 1, "Internal end cap");
            }
        }
        require(FluidBufferArt.liquid(0, 0).isEmpty(), "Invented empty liquid");
        var one = Map.of(BlockPos.ZERO, 0);
        require(Math.abs(FluidBufferArt.level(one, .5) - .5) < 1e-12, "Half liquid height");
        require(FluidBufferArt.level(one, 1) == 13. / 16, "Full liquid height");
        require(!FluidBufferArt.liquid(0, 13. / 16).isEmpty(), "Full liquid omitted");
        require(!FluidBufferArt.liquid(0, 3. / 16 + 1. / 10000).isEmpty(), "Small actual amount disappeared");
        var vertical = Map.of(
                BlockPos.ZERO,
                FluidBufferArt.bit(Direction.UP),
                BlockPos.ZERO.above(),
                FluidBufferArt.bit(Direction.DOWN));
        require(Math.abs(FluidBufferArt.level(vertical, .5) - 1) < 1e-12, "Vertical shared level");
        int exposedRoof = FluidBufferArt.bit(Direction.UP) | FluidBufferArt.bit(Direction.EAST);
        for (var liquid : FluidBufferArt.liquid(exposedRoof, FluidBufferArt.BODY.maxY))
            if (liquid.face() == Direction.UP)
                for (var shell : FluidBufferArt.mesh(exposedRoof)) {
                    if (shell.face() != Direction.UP) continue;
                    var a = liquid.bounds();
                    var b = shell.bounds();
                    require(
                            a.minY != b.minY
                                    || a.maxX <= b.minX
                                    || a.minX >= b.maxX
                                    || a.maxZ <= b.minZ
                                    || a.minZ >= b.maxZ,
                            "Liquid cap overlaps an exposed reservoir roof");
                }
        var scenes = new ArrayList<Map<String, Object>>();
        scenes.add(scene("Standalone portable / no mounting orientation", List.of(BlockPos.ZERO), true));
        for (var face : Direction.values())
            scenes.add(scene(
                    "Compatible pair " + face.getName(), List.of(BlockPos.ZERO, BlockPos.ZERO.relative(face)), true));
        scenes.add(scene("Incompatible pair / water and lava", List.of(BlockPos.ZERO, new BlockPos(1, 0, 0)), false));
        scenes.add(
                scene("Three-buffer line", List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)), true));
        scenes.add(
                scene("L-shaped cluster", List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(1, 0, 1)), true));
        scenes.add(scene(
                "Rectangular arrangement / reserved bridge union",
                List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)),
                true));
        scenes.add(scene(
                "Irregular vertical corner",
                List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(1, 1, 0), new BlockPos(1, 1, 1)),
                true));
        scenes.add(scene("Buffer beside casing", List.of(BlockPos.ZERO), true));
        scenes.add(scene("Buffer beside conduit", List.of(BlockPos.ZERO), true));
        for (var size : new int[][] {{2, 2, 1}, {2, 1, 2}, {1, 2, 2}, {2, 2, 2}, {3, 3, 3}}) {
            var positions = new ArrayList<BlockPos>();
            for (int x = 0; x < size[0]; x++)
                for (int y = 0; y < size[1]; y++)
                    for (int z = 0; z < size[2]; z++) positions.add(new BlockPos(x, y, z));
            verifyRectangle(positions, size);
            scenes.add(scene("Continuous " + size[0] + "x" + size[1] + "x" + size[2] + " reservoir", positions, true));
        }
        var cube = new ArrayList<BlockPos>();
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) cube.add(new BlockPos(x, y, z));
        for (var removed : List.of(
                Set.of(new BlockPos(2, 2, 2)),
                Set.of(new BlockPos(2, 2, 2), new BlockPos(2, 1, 2)),
                Set.of(new BlockPos(1, 0, 1), new BlockPos(1, 1, 1), new BlockPos(1, 2, 1)),
                Set.of(new BlockPos(1, 1, 1)))) {
            var irregular = cube.stream().filter(p -> !removed.contains(p)).toList();
            verifyIrregular(irregular);
            scenes.add(scene("Irregular merged reservoir / removed " + removed, irregular, true));
        }
        verifyIrregular(List.of(
                BlockPos.ZERO, BlockPos.ZERO.east(), BlockPos.ZERO.east().south()));
        examples.put("buffer_scenes", scenes);
        System.out.println(
                "PASS: centered buffer, equal six-way surfaces, all 64 joins inside physical envelopes, internal cap suppression, cache identity and shared levels.");
    }

    private static Map<String, Object> scene(String name, List<BlockPos> positions, boolean compatible) {
        var joins = new LinkedHashMap<BlockPos, Integer>();
        for (var pos : positions) {
            int mask = compatible ? FluidBufferShape.neighbors(new HashSet<>(positions), pos) : 0;
            joins.put(pos, mask);
        }
        var cells = new ArrayList<Map<String, Object>>();
        for (var pos : positions) {
            int mask = joins.get(pos);
            var fills = new ArrayList<Object>();
            for (double fill : new double[] {0, .5, 1})
                fills.add(elements(
                        FluidBufferArt.liquid(mask, fill == 0 ? 0 : FluidBufferArt.level(joins, fill) - pos.getY()),
                        true));
            var mesh = FluidBufferArt.mesh(mask);
            var worldVolumes = joins.entrySet().stream()
                    .flatMap(e -> FluidBufferArt.volumes(e.getValue()).stream().map(b -> b.move(e.getKey())))
                    .toList();
            for (var panel : mesh) {
                var center = panel.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
                var face = panel.face();
                var normal = new net.minecraft.world.phys.Vec3(face.getStepX(), face.getStepY(), face.getStepZ())
                        .scale(.00001);
                require(
                        worldVolumes.stream().anyMatch(b -> b.contains(center.subtract(normal))),
                        "Exterior face has no reservoir behind it");
                require(
                        worldVolumes.stream().noneMatch(b -> b.contains(center.add(normal))),
                        "Internal cluster face/reinforcement remains");
            }
            cells.add(Map.of(
                    "position",
                    List.of(pos.getX(), pos.getY(), pos.getZ()),
                    "elements",
                    elements(mesh, false),
                    "liquids",
                    fills,
                    "color",
                    !compatible && cells.size() > 0 ? "#ff6c13" : ""));
        }
        return Map.of(
                "name",
                name,
                "cells",
                cells,
                "context",
                name.contains("beside casing") ? "casing" : name.contains("beside conduit") ? "conduit" : "");
    }

    private static void verifyRectangle(List<BlockPos> positions, int[] size) {
        var joins = new LinkedHashMap<BlockPos, Integer>();
        for (var pos : positions) {
            int mask = FluidBufferShape.neighbors(new HashSet<>(positions), pos);
            joins.put(pos, mask);
        }
        var bounds = new AABB(3. / 16, 3. / 16, 3. / 16, size[0] - 3. / 16, size[1] - 3. / 16, size[2] - 3. / 16);
        double[] areas = new double[6], metal = new double[6];
        double height = FluidBufferArt.level(joins, .5), liquidArea = 0;
        require(Math.abs(height - size[1] / 2.) < 1e-9, "Rectangular shared liquid level differs");
        for (var entry : joins.entrySet()) {
            var pos = entry.getKey();
            int mask = entry.getValue();
            var reserved = FluidBufferGeometry.envelope(pos, false, Set.of(), directions(mask), mask);
            for (var volume : FluidBufferArt.volumes(mask))
                for (double x : new double[] {volume.minX + 1e-6, volume.maxX - 1e-6})
                    for (double y : new double[] {volume.minY + 1e-6, volume.maxY - 1e-6})
                        for (double z : new double[] {volume.minZ + 1e-6, volume.maxZ - 1e-6}) {
                            var point =
                                    new net.minecraft.world.phys.Vec3(x, y, z).add(pos.getX(), pos.getY(), pos.getZ());
                            require(
                                    reserved.stream().anyMatch(v -> v.bounds().contains(point)),
                                    "Merged art exceeds reserved geometry");
                        }
            for (var panel : FluidBufferArt.mesh(mask)) {
                var face = panel.face();
                var world = panel.bounds().move(pos);
                double expected = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? bounds.max(face.getAxis())
                        : bounds.min(face.getAxis());
                require(
                        Math.abs(world.min(face.getAxis()) - expected) < 1e-9,
                        "Internal rim or recessed face in rectangular cluster");
                areas[face.ordinal()] += area(panel);
                if (panel.material() == FluidBufferArt.Material.METAL) metal[face.ordinal()] += area(panel);
            }
            for (var panel : FluidBufferArt.liquid(mask, height - pos.getY()))
                if (panel.face() == Direction.UP) {
                    require(Math.abs(panel.bounds().minY + pos.getY() - height) < 1e-9, "Internal liquid cap");
                    liquidArea += area(panel);
                }
        }
        for (var face : Direction.values()) {
            var axis = face.getAxis();
            double a = axis == Direction.Axis.X ? bounds.getYsize() : bounds.getXsize();
            double b = axis == Direction.Axis.Z ? bounds.getYsize() : bounds.getZsize();
            require(Math.abs(areas[face.ordinal()] - a * b) < 1e-9, "Hole or duplicate in rectangular outer wall");
            require(
                    Math.abs(metal[face.ordinal()] - (a * b - (a - 2. / 16) * (b - 2. / 16))) < 1e-9,
                    "Metal trim is not confined to the outside perimeter");
        }
        double inset = .25 / 16;
        require(
                Math.abs(liquidArea - (bounds.getXsize() - 2 * inset) * (bounds.getZsize() - 2 * inset)) < 1e-9,
                "Liquid surface has junction holes or overlaps");
        var incomplete = new ArrayList<>(positions);
        incomplete.remove(incomplete.size() - 1);
        verifyIrregular(incomplete);
    }

    private static void verifyIrregular(List<BlockPos> positions) {
        var members = new HashSet<>(positions);
        var joins = new LinkedHashMap<BlockPos, Integer>();
        for (var pos : positions) joins.put(pos, FluidBufferShape.neighbors(members, pos));
        var world = joins.entrySet().stream()
                .flatMap(e -> FluidBufferArt.volumes(e.getValue()).stream().map(b -> b.move(e.getKey())))
                .toList();
        var occupied = new HashSet<BlockPos>();
        for (var b : world)
            for (int x = (int) Math.round(b.minX * 16); x < Math.round(b.maxX * 16); x++)
                for (int y = (int) Math.round(b.minY * 16); y < Math.round(b.maxY * 16); y++)
                    for (int z = (int) Math.round(b.minZ * 16); z < Math.round(b.maxZ * 16); z++)
                        require(occupied.add(new BlockPos(x, y, z)), "Reservoir volumes overlap");
        var expected = new HashSet<SurfacePixel>();
        for (var voxel : occupied)
            for (var face : Direction.values())
                if (!occupied.contains(voxel.relative(face))) expected.add(new SurfacePixel(voxel, face));
        var actual = new HashSet<SurfacePixel>();
        for (var entry : joins.entrySet()) {
            var pos = entry.getKey();
            int mask = entry.getValue();
            var reserved = FluidBufferGeometry.envelope(pos, false, Set.of(), directions(mask), mask);
            for (var box : FluidBufferArt.volumes(mask)) {
                require(
                        reserved.stream()
                                .anyMatch(v ->
                                        v.bounds().contains(box.getCenter().add(pos.getX(), pos.getY(), pos.getZ()))),
                        "Irregular reservoir exceeds reserved space");
            }
            for (var panel : FluidBufferArt.mesh(mask)) {
                var face = panel.face();
                var b = panel.bounds();
                var normal = new net.minecraft.world.phys.Vec3(face.getStepX(), face.getStepY(), face.getStepZ())
                        .scale(1e-6);
                // Check every pixel: no hidden faces, and trim only beside an actual change of exposed surface.
                var ua = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.uAxis(face);
                var va = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.vAxis(face);
                for (double u = b.min(ua) + .5 / 16; u < b.max(ua); u += 1. / 16)
                    for (double v = b.min(va) + .5 / 16; v < b.max(va); v += 1. / 16) {
                        double[] p = {b.minX, b.minY, b.minZ};
                        p[ua.ordinal()] = u;
                        p[va.ordinal()] = v;
                        var center = new net.minecraft.world.phys.Vec3(p[0], p[1], p[2])
                                .add(pos.getX(), pos.getY(), pos.getZ());
                        var inward = new net.minecraft.world.phys.Vec3(
                                        face.getStepX(), face.getStepY(), face.getStepZ())
                                .scale(.5 / 16);
                        require(
                                actual.add(new SurfacePixel(
                                        BlockPos.containing(
                                                center.subtract(inward).scale(16)),
                                        face)),
                                "Duplicate coplanar reservoir panel");
                        require(exposed(world, center, normal), "Hidden internal wall in irregular cluster");
                        boolean edge = false;
                        for (var tangent : new Direction.Axis[] {ua, va})
                            for (double delta : new double[] {-1. / 16, 1. / 16}) {
                                double[] offset = {0, 0, 0};
                                offset[tangent.ordinal()] = delta;
                                if (!exposed(world, center.add(offset[0], offset[1], offset[2]), normal)) edge = true;
                            }
                        require(
                                (panel.material() == FluidBufferArt.Material.METAL) == edge,
                                "Trim follows a member seam instead of the exposed irregular edge");
                    }
            }
        }
        require(actual.equals(expected), "Irregular shell has missing faces or internal partitions");
        for (double fraction : new double[] {.001, .5, 1}) {
            double height = FluidBufferArt.level(joins, fraction);
            var liquid = new ArrayList<FluidBufferArt.Panel>();
            for (var entry : joins.entrySet())
                for (var panel : FluidBufferArt.liquid(
                        entry.getValue(), height - entry.getKey().getY()))
                    liquid.add(new FluidBufferArt.Panel(
                            panel.bounds().move(entry.getKey()), panel.face(), panel.material()));
            require(new HashSet<>(liquid).size() == liquid.size(), "Duplicate liquid faces across member boundaries");
            var wet = joins.entrySet().stream()
                    .flatMap(e -> FluidBufferShape.volumes(e.getValue(), FluidBufferArt.BODY.deflate(.25 / 16)).stream()
                            .map(b -> b.move(e.getKey())))
                    .filter(b -> b.minY < height)
                    .map(b -> new AABB(b.minX, b.minY, b.minZ, b.maxX, Math.min(height, b.maxY), b.maxZ))
                    .toList();
            for (var panel : liquid) {
                // The tiny-amount visibility adjustment intentionally raises the first surface above the inset floor.
                if (fraction == .001) continue;
                var face = panel.face();
                var normal = new net.minecraft.world.phys.Vec3(face.getStepX(), face.getStepY(), face.getStepZ())
                        .scale(1e-6);
                require(exposed(wet, panel.bounds().getCenter(), normal), "Liquid retained an internal cap or wall");
            }
        }
        // The 2x2 junction opposite a removed corner must remain full rather than reverting to thin bridges.
        if (members.containsAll(List.of(
                BlockPos.ZERO,
                BlockPos.ZERO.east(),
                BlockPos.ZERO.south(),
                BlockPos.ZERO.east().south())))
            require(
                    world.stream().anyMatch(b -> b.contains(new net.minecraft.world.phys.Vec3(.95, .5, .95))),
                    "Removing a distant member reopened a completed junction");
    }

    private record SurfacePixel(BlockPos voxel, Direction face) {}

    private static boolean exposed(
            List<AABB> volumes, net.minecraft.world.phys.Vec3 center, net.minecraft.world.phys.Vec3 normal) {
        return volumes.stream().anyMatch(b -> b.contains(center.subtract(normal)))
                && volumes.stream().noneMatch(b -> b.contains(center.add(normal)));
    }
}
