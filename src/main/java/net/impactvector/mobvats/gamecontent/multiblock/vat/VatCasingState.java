package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Bounds-derived casing snapshot; channel edges share the formed glass footprint. */
public record VatCasingState(int outwardFaces, int outerEdges) {
    public static final ModelProperty<VatCasingState> PROPERTY = new ModelProperty<>();
    public static final VatCasingState UNFORMED = new VatCasingState(0, 0);

    public boolean formed() {
        return outwardFaces != 0;
    }

    public boolean outward(Direction face) {
        return (outwardFaces & 1 << face.ordinal()) != 0;
    }

    public int outerEdges(Direction face) {
        return outerEdges >>> (face.ordinal() * 4) & 15;
    }

    public static Direction.Axis uAxis(Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis vAxis(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    public static VatCasingState from(BlockPos pos, BlockPos min, BlockPos max) {
        int faces = 0, outer = 0;
        for (var face : Direction.values()) {
            int boundary = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    ? min.get(face.getAxis())
                    : max.get(face.getAxis());
            if (pos.get(face.getAxis()) != boundary) continue;
            faces |= 1 << face.ordinal();
            outer |= edges(pos, min, max, face) << (face.ordinal() * 4);
        }
        return new VatCasingState(faces, outer);
    }

    private static int edges(BlockPos pos, BlockPos min, BlockPos max, Direction face) {
        int mask = 0;
        var u = uAxis(face);
        var v = vAxis(face);
        if (pos.get(u) == min.get(u)) mask |= VatGlassState.LEFT;
        if (pos.get(u) == max.get(u)) mask |= VatGlassState.RIGHT;
        if (pos.get(v) == min.get(v)) mask |= VatGlassState.BOTTOM;
        if (pos.get(v) == max.get(v)) mask |= VatGlassState.TOP;
        return mask;
    }
}
