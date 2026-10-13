package net.impactvector.mobvats.gamecontent.mob;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinition;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

/** Version 1 owns a filled cuboid. Local X runs left-to-right, Z front-to-back, Y upward.
 * The Head is the only root; neither substrate nor service endpoints determine its extent. */
public record SimulacrumGeometry(
        int width,
        int height,
        int depth,
        BlockPos headOffset,
        Direction facing,
        double bodyWidth,
        double bodyHeight,
        double bodyDepth) {
    private static final Map<ResourceLocation, SimulacrumGeometry> HISTORICAL = Map.of(
            ResourceLocation.withDefaultNamespace("cow"),
            new SimulacrumGeometry(
                    1, 2, 1, new BlockPos(0, 1, 0), Direction.NORTH, (double) .9f, (double) 1.4f, (double) .9f));

    public SimulacrumGeometry {
        if (width < 1
                || height < 1
                || depth < 1
                || width > 4096
                || height > 4096
                || depth > 4096
                || (long) width * height * depth > 1_000_000)
            throw new IllegalArgumentException("Invalid simulacrum dimensions");
        if (headOffset == null
                || headOffset.getX() < 0
                || headOffset.getX() >= width
                || headOffset.getY() < 0
                || headOffset.getY() >= height
                || headOffset.getZ() < 0
                || headOffset.getZ() >= depth)
            throw new IllegalArgumentException("Head offset outside simulacrum dimensions");
        if (facing == null || facing.getAxis() == Direction.Axis.Y)
            throw new IllegalArgumentException("Horizontal facing required");
        if (!positive(bodyWidth) || !positive(bodyHeight) || !positive(bodyDepth))
            throw new IllegalArgumentException("Invalid body extents");
        headOffset = headOffset.immutable();
    }

    private static boolean positive(double d) {
        return Double.isFinite(d) && d > 0;
    }

    public static SimulacrumGeometry resolve(
            double x, double y, double z, Direction facing, PassiveMobDefinition.Simulacrum override) {
        if (!positive(x) || !positive(y) || !positive(z))
            throw new IllegalArgumentException("Invalid captured extents");
        int w = (int) Math.ceil(x), h = (int) Math.ceil(y), d = (int) Math.ceil(z);
        if (override != null && override.dimensions() != null) {
            var a = override.dimensions();
            w = a.get(0);
            h = a.get(1);
            d = a.get(2);
        }
        var head = new BlockPos((w - 1) / 2, h - 1, 0);
        if (override != null && override.head_offset() != null) {
            var a = override.head_offset();
            head = new BlockPos(a.get(0), a.get(1), a.get(2));
        }
        return new SimulacrumGeometry(w, h, d, head, facing, x, y, z);
    }

    public static SimulacrumGeometry capture(Entity entity) {
        var box = entity.getBoundingBox();
        var face = entity.getDirection();
        var definition = PassiveMobDefinitionRegistry.resolve(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        // Convert world X/Z extents into facing-relative width/depth before storing them.
        boolean turn = face.getAxis() == Direction.Axis.X;
        return resolve(
                turn ? box.getZsize() : box.getXsize(),
                box.getYsize(),
                turn ? box.getXsize() : box.getZsize(),
                face,
                definition == null ? null : definition.simulacrum());
    }

    public static SimulacrumGeometry legacy(ResourceLocation id, Direction facing) {
        var historical = HISTORICAL.get(id);
        if (historical != null) return historical.withFacing(facing);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            throw new IllegalArgumentException("Unknown legacy entity: " + id);
        var size = BuiltInRegistries.ENTITY_TYPE.get(id).getDimensions();
        // Unknown historical profiles use registered type dimensions, excluding config overrides.
        return resolve(size.width(), size.height(), size.width(), facing, null);
    }

    public SimulacrumGeometry withFacing(Direction face) {
        return new SimulacrumGeometry(width, height, depth, headOffset, face, bodyWidth, bodyHeight, bodyDepth);
    }

    public BlockPos rotate(BlockPos local) {
        var right = facing.getClockWise();
        var back = facing.getOpposite();
        return new BlockPos(
                right.getStepX() * local.getX() + back.getStepX() * local.getZ(),
                local.getY(),
                right.getStepZ() * local.getX() + back.getStepZ() * local.getZ());
    }

    public BlockPos origin(BlockPos head) {
        return head.subtract(rotate(headOffset));
    }

    public BlockPos cell(BlockPos head, int x, int y, int z) {
        return origin(head).offset(rotate(new BlockPos(x, y, z)));
    }

    public Set<BlockPos> occupiedCells(BlockPos head) {
        var cells = new LinkedHashSet<BlockPos>();
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) cells.add(cell(head, x, y, z));
        return Collections.unmodifiableSet(cells);
    }

    public AABB occupiedBounds(BlockPos head) {
        var a = cell(head, 0, 0, 0);
        var b = cell(head, width - 1, height - 1, depth - 1);
        return new AABB(
                Math.min(a.getX(), b.getX()),
                a.getY(),
                Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1,
                b.getY() + 1,
                Math.max(a.getZ(), b.getZ()) + 1);
    }

    public Set<BlockPos> harnessCorners(BlockPos head, int plane) {
        var b = occupiedBounds(head);
        var result = new LinkedHashSet<BlockPos>();
        for (int x : new int[] {(int) b.minX, (int) b.maxX - 1})
            for (int z : new int[] {(int) b.minZ, (int) b.maxZ - 1}) result.add(new BlockPos(x, plane, z));
        return Collections.unmodifiableSet(result);
    }

    public Vec3 renderPosition(BlockPos head) {
        var b = occupiedBounds(head);
        return new Vec3((b.minX + b.maxX) / 2, b.minY, (b.minZ + b.maxZ) / 2);
    }

    public AABB bodyBounds(BlockPos head, double scale) {
        var p = renderPosition(head);
        boolean turn = facing.getAxis() == Direction.Axis.X;
        double x = (turn ? bodyDepth : bodyWidth) * scale, z = (turn ? bodyWidth : bodyDepth) * scale;
        return new AABB(p.x - x / 2, p.y, p.z - z / 2, p.x + x / 2, p.y + bodyHeight * scale, p.z + z / 2);
    }

    public Set<BlockPos> comfortShell(BlockPos head) {
        var b = occupiedBounds(head);
        var result = new LinkedHashSet<BlockPos>();
        var owned = occupiedCells(head);
        for (var p : BlockPos.betweenClosed(
                (int) b.minX - 1, (int) b.minY - 1, (int) b.minZ - 1, (int) b.maxX, (int) b.maxY, (int) b.maxZ))
            if (!owned.contains(p)) result.add(p.immutable());
        return Collections.unmodifiableSet(result);
    }

    public CompoundTag save() {
        var t = new CompoundTag();
        t.putInt("version", 1);
        t.putIntArray("dimensions", new int[] {width, height, depth});
        t.putIntArray("headOffset", new int[] {headOffset.getX(), headOffset.getY(), headOffset.getZ()});
        t.putString("facing", facing.getSerializedName());
        t.putDouble("bodyWidth", bodyWidth);
        t.putDouble("bodyHeight", bodyHeight);
        t.putDouble("bodyDepth", bodyDepth);
        return t;
    }

    public static SimulacrumGeometry load(CompoundTag t) {
        if (t.getInt("version") != 1) throw new IllegalArgumentException("Unsupported simulacrum geometry version");
        var d = t.getIntArray("dimensions");
        var h = t.getIntArray("headOffset");
        if (d.length != 3 || h.length != 3)
            throw new IllegalArgumentException("Geometry vectors require three integers");
        return new SimulacrumGeometry(
                d[0],
                d[1],
                d[2],
                new BlockPos(h[0], h[1], h[2]),
                Direction.byName(t.getString("facing")),
                t.getDouble("bodyWidth"),
                t.getDouble("bodyHeight"),
                t.getDouble("bodyDepth"));
    }
}
