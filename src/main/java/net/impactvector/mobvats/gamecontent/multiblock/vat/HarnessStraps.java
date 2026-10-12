package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.List;
import net.impactvector.mobvats.gamecontent.mob.CableCurve;
import net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Four underside corners converge on a body-relative attachment, independent of gaze. */
public final class HarnessStraps {
    public static final double SAG = 0.10, THICKNESS = 3.0 / 64;
    public static final int SEGMENTS = 16;
    public static List<Vec3> corners(BlockPos harness) {
        double x = harness.getX(), y = harness.getY() + (VatCasingGeometry.PIXELS - VatCasingGeometry.DEPTH)
            / (double)VatCasingGeometry.PIXELS, z = harness.getZ();
        return List.of(new Vec3(x, y, z), new Vec3(x + 1, y, z),
            new Vec3(x, y, z + 1), new Vec3(x + 1, y, z + 1));
    }
    public static List<CableCurve> curves(BlockPos harness, AABB body, MobRenderDefinition definition) {
        var anchor = definition.bodyAnchor(body);
        return corners(harness).stream().map(start -> CableCurve.between(start, anchor, SAG, SEGMENTS)).toList();
    }
    /** A collapsed 1x1 footprint uses its four panel corners. Larger footprints use one
     * outer attachment per required corner Harness, spreading support over the specimen. */
    public static List<Vec3> origins(List<BlockPos> harnesses,AABB occupied){
        if(harnesses.size()==1)return corners(harnesses.getFirst());
        return harnesses.stream().map(p->new Vec3(p.getX()==(int)occupied.minX?p.getX():p.getX()+1,
            p.getY()+13.0/16,p.getZ()==(int)occupied.minZ?p.getZ():p.getZ()+1)).toList();
    }
    public static List<CableCurve> curves(List<BlockPos> harnesses,AABB occupied,AABB body,MobRenderDefinition definition){
        var anchor=definition.bodyAnchor(body);
        return origins(harnesses,occupied).stream().map(start->CableCurve.between(start,anchor,SAG,SEGMENTS)).toList();
    }
    private HarnessStraps() {}
}
