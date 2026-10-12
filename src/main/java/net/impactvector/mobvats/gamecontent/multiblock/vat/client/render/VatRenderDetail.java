package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One shared detail decision for current and future coordinated interior features. */
public enum VatRenderDetail {
    FULL, REDUCED, MINIMAL, CULLED;

    public record Distances(int full, int reduced, int cull) {
        public Distances {
            cull = Math.clamp(cull, 8, 512);
            reduced = Math.min(cull, Math.clamp(reduced, 8, 512));
            full = Math.min(reduced, Math.clamp(full, 8, 512));
        }
    }
    public static double distanceSquared(AABB bounds, Vec3 camera) {
        double x = Math.clamp(camera.x, bounds.minX, bounds.maxX);
        double y = Math.clamp(camera.y, bounds.minY, bounds.maxY);
        double z = Math.clamp(camera.z, bounds.minZ, bounds.maxZ);
        return camera.distanceToSqr(x, y, z);
    }
    public static VatRenderDetail select(double distanceSquared, int full, int reduced, int cull) {
        var distances = new Distances(full, reduced, cull);
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return CULLED;
        if (distanceSquared <= distances.full() * distances.full()) return FULL;
        if (distanceSquared <= distances.reduced() * distances.reduced()) return REDUCED;
        if (distanceSquared <= distances.cull() * distances.cull()) return MINIMAL;
        return CULLED;
    }
    public boolean rendersStraps() { return this == FULL || this == REDUCED; }
    public boolean rendersMechanisms() { return this == FULL || this == REDUCED; }
    public boolean animates() { return this == FULL; }
}
