package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Geometry follows vat bounds; only glass-adjacent thickness edges need rendering. */
public record VatWallPanelState(VatCasingState shell, int glassEdges) {
    public static final ModelProperty<VatWallPanelState> PROPERTY = new ModelProperty<>();
    public static final VatWallPanelState UNFORMED = new VatWallPanelState(VatCasingState.UNFORMED, 0);
    public boolean visible(VatCasingGeometry.Patch patch) {
        if (blockFaceFor(patch.normal()) != null) return true;
        return (glassEdges & 1 << patch.normal().ordinal()) != 0;
    }
    /** Null denotes a thickness edge using the casing lip material. */
    public Direction blockFaceFor(Direction normal) {
        for (var outward : Direction.values()) if (shell.outward(outward)
                && outward.getAxis() == normal.getAxis()) return outward;
        return null;
    }
}
