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
        examples.put("buffer_scenes", scenes);
        System.out.println(
                "PASS: centered buffer, equal six-way surfaces, all 64 joins inside physical envelopes, internal cap suppression, cache identity and shared levels.");
    }

    private static Map<String, Object> scene(String name, List<BlockPos> positions, boolean compatible) {
        var joins = new LinkedHashMap<BlockPos, Integer>();
        for (var pos : positions) {
            int mask = 0;
            for (var face : Direction.values())
                if (compatible && positions.contains(pos.relative(face))) mask |= FluidBufferArt.bit(face);
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
}
