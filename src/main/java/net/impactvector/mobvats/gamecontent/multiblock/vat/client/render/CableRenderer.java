package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.impactvector.mobvats.gamecontent.mob.CableCurve;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Small reusable, cached cable mesh. No ticking entities, wire simulation, or external assets. */
public final class CableRenderer {
    public record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, float v0, float v1) {}
    public record Mesh(List<Quad> quads) { public Mesh { quads = List.copyOf(quads); } }

    public static Mesh bake(CableCurve curve, double thickness) {
        if (!Double.isFinite(thickness) || thickness <= 0) throw new IllegalArgumentException("Cable thickness must be positive");
        var points = curve.points();
        var rings = new ArrayList<Vec3[]>(points.size());
        double radius = thickness / 2;
        var direction = points.getLast().subtract(points.getFirst()).normalize();
        var reference = Math.abs(direction.y) > 0.90 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        for (int i = 0; i < points.size(); i++) {
            var tangent = points.get(Math.min(i + 1, points.size() - 1)).subtract(points.get(Math.max(i - 1, 0))).normalize();
            var u = tangent.cross(reference).normalize().scale(radius);
            var v = tangent.cross(u.normalize()).normalize().scale(radius);
            var center = points.get(i);
            rings.add(new Vec3[]{center.add(u).add(v), center.subtract(u).add(v),
                center.subtract(u).subtract(v), center.add(u).subtract(v)});
        }
        var quads = new ArrayList<Quad>();
        for (int i = 0; i < rings.size() - 1; i++) for (int side = 0; side < 4; side++) {
            int next = (side + 1) % 4;
            var a = rings.get(i)[side];
            var b = rings.get(i)[next];
            var c = rings.get(i + 1)[next];
            var d = rings.get(i + 1)[side];
            var normal = b.subtract(a).cross(d.subtract(a)).normalize();
            quads.add(new Quad(a, b, c, d, normal, (float)i / (rings.size() - 1), (float)(i + 1) / (rings.size() - 1)));
        }
        var first = rings.getFirst();
        var last = rings.getLast();
        quads.add(new Quad(first[3], first[2], first[1], first[0], points.getFirst().subtract(points.get(1)).normalize(), 0, 1));
        quads.add(new Quad(last[0], last[1], last[2], last[3], points.getLast().subtract(points.get(points.size() - 2)).normalize(), 0, 1));
        return new Mesh(quads);
    }

    public static void renderCable(Mesh mesh, Vec3 origin, ResourceLocation material, PoseStack pose,
            MultiBufferSource buffers, int light) {
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(material));
        var matrix = pose.last();
        for (var quad : mesh.quads()) {
            vertex(buffer, matrix, quad.a(), origin, quad.normal(), 0, quad.v0(), light);
            vertex(buffer, matrix, quad.b(), origin, quad.normal(), 1, quad.v0(), light);
            vertex(buffer, matrix, quad.c(), origin, quad.normal(), 1, quad.v1(), light);
            vertex(buffer, matrix, quad.d(), origin, quad.normal(), 0, quad.v1(), light);
        }
    }
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point, Vec3 origin,
            Vec3 normal, float u, float v, int light) {
        buffer.addVertex(pose, (float)(point.x - origin.x), (float)(point.y - origin.y), (float)(point.z - origin.z))
            .setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
    }
    private CableRenderer() {}
}
