package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Presentation mesh in world coordinates. Glass coverage is height-clipped, never an opacity percentage. */
public final class VatMediumMesh {
    public static final double INSET = .002;

    public record Face(List<Vec3> vertices, Direction normal) {
        public Face {
            vertices = List.copyOf(vertices);
        }
    }

    public record Mesh(double height, List<Face> faces) {
        public Mesh {
            faces = List.copyOf(faces);
        }
    }

    public static double height(VatMediumStorage medium, BlockPos min, BlockPos max) {
        double result = min.getY() + 1;
        for (int y = min.getY() + 1; y < max.getY(); y++) if (medium.levelFill(y) > 0) result = y + medium.levelFill(y);
        return result;
    }

    public static Mesh bake(Level level, BlockPos min, BlockPos max, VatMediumStorage medium) {
        if (medium.isEmpty() || medium.getCapacity() == 0) return new Mesh(min.getY() + 1, List.of());
        double height = height(medium, min, max);
        var faces = new ArrayList<Face>();
        for (var p : BlockPos.betweenClosed(min, max)) {
            var block = level.getBlockState(p).getBlock();
            boolean glass = level.getBlockEntity(p)
                            instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatGlassEntity
                    || block instanceof ConduitBlock c && c.cladding(level.getBlockState(p)) == ConduitCladding.GLASS
                    || block instanceof FluidBufferBlock b
                            && b.cladding(level.getBlockState(p)) == ConduitCladding.GLASS;
            if (!glass) continue;
            double low = Math.max(p.getY(), min.getY() + 1), high = Math.min(p.getY() + 1, height);
            if (high <= low) continue;
            var skin = VatGlassState.from(p, min, max);
            for (var side : Direction.Plane.HORIZONTAL)
                if (skin.hasFace(side)) {
                    double x0 = p.getX(), x1 = x0 + 1, z0 = p.getZ(), z1 = z0 + 1;
                    faces.add(
                            switch (side) {
                                case NORTH ->
                                    face(
                                            side,
                                            new Vec3(x1, low, z0 + INSET),
                                            new Vec3(x0, low, z0 + INSET),
                                            new Vec3(x0, high, z0 + INSET),
                                            new Vec3(x1, high, z0 + INSET));
                                case SOUTH ->
                                    face(
                                            side,
                                            new Vec3(x0, low, z1 - INSET),
                                            new Vec3(x1, low, z1 - INSET),
                                            new Vec3(x1, high, z1 - INSET),
                                            new Vec3(x0, high, z1 - INSET));
                                case WEST ->
                                    face(
                                            side,
                                            new Vec3(x0 + INSET, low, z0),
                                            new Vec3(x0 + INSET, low, z1),
                                            new Vec3(x0 + INSET, high, z1),
                                            new Vec3(x0 + INSET, high, z0));
                                case EAST ->
                                    face(
                                            side,
                                            new Vec3(x1 - INSET, low, z1),
                                            new Vec3(x1 - INSET, low, z0),
                                            new Vec3(x1 - INSET, high, z0),
                                            new Vec3(x1 - INSET, high, z1));
                                default -> throw new IllegalStateException();
                            });
                }
        }
        // A tiled surface preserves the fluid texture scale and omits fully displaced interior cells.
        int surfaceY = Math.min(max.getY() - 1, (int) Math.ceil(height) - 1);
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (x > min.getX()
                        && x < max.getX()
                        && z > min.getZ()
                        && z < max.getZ()
                        && VatDisplacement.displaced(level, new BlockPos(x, surfaceY, z)) == 1000) continue;
                double x0 = Math.max(x, min.getX() + INSET), x1 = Math.min(x + 1, max.getX() + 1 - INSET);
                double z0 = Math.max(z, min.getZ() + INSET),
                        z1 = Math.min(z + 1, max.getZ() + 1 - INSET),
                        y = height - INSET;
                faces.add(face(
                        Direction.UP,
                        new Vec3(x0, y, z0),
                        new Vec3(x0, y, z1),
                        new Vec3(x1, y, z1),
                        new Vec3(x1, y, z0)));
            }
        return new Mesh(height, faces);
    }

    private static Face face(Direction normal, Vec3... points) {
        return new Face(List.of(points), normal);
    }

    private VatMediumMesh() {}
}
