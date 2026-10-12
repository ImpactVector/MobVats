package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable chunk-model snapshot. Each face has a presence bit and four perimeter bits. */
public record VatGlassState(int faces) {
    public static final ModelProperty<VatGlassState> PROPERTY = new ModelProperty<>();
    public static final VatGlassState UNFORMED = new VatGlassState(0);
    public static final int TOP = 1, BOTTOM = 2, LEFT = 4, RIGHT = 8;
    private static final int PRESENT = 16;

    public boolean formed() { return faces != 0; }
    public boolean hasFace(Direction face) { return (faces >>> (face.ordinal() * 5) & PRESENT) != 0; }
    public int edges(Direction face) { return faces >>> (face.ordinal() * 5) & 15; }

    public static VatGlassState from(BlockPos pos, BlockPos min, BlockPos max) {
        return from(pos,min,max,true);
    }
    /** Mixers have no mandatory casing rings: perimeter rims follow the entire enclosure. */
    public static VatGlassState mixer(BlockPos pos,BlockPos min,BlockPos max){
        return from(pos,min,max,false);
    }
    private static VatGlassState from(BlockPos pos,BlockPos min,BlockPos max,boolean casingRings){
        int result = 0;
        for (var face : Direction.values()) {
            int coordinate = pos.get(face.getAxis());
            int boundary = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? min.get(face.getAxis()) : max.get(face.getAxis());
            if (coordinate != boundary) continue;
            // Side planes: U=X (N/S), U=Z (E/W), V=Y. Roof: U=X, V=Z.
            // Left/right mean low/high U; bottom/top mean low/high V in this projection.
            boolean cap = face.getAxis() == Direction.Axis.Y;
            var u = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            var v = cap ? Direction.Axis.Z : Direction.Axis.Y;
            int uInset = casingRings && cap ? 1 : 0;
            int vInset = casingRings ? 1 : 0;
            int mask = 0;
            if (pos.get(u) == min.get(u) + uInset) mask |= LEFT;
            if (pos.get(u) == max.get(u) - uInset) mask |= RIGHT;
            if (pos.get(v) == min.get(v) + vInset) mask |= BOTTOM;
            if (pos.get(v) == max.get(v) - vInset) mask |= TOP;
            result |= (PRESENT | mask) << (face.ordinal() * 5);
        }
        return new VatGlassState(result);
    }
}
