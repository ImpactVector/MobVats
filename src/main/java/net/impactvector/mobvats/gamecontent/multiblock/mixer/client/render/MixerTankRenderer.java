package net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerRenderDescriptor;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.CableRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Cached opaque polygon vessel and one translucent working-fluid fan. */
final class MixerTankRenderer {
    private static final ResourceLocation WALL = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private static final int VERDIGRIS = 0x5f8f78, LIGHT = 0x83af99, COPPER = 0xa86d4b;

    record Mesh(
            CableRenderer.Mesh walls,
            CableRenderer.Mesh interior,
            CableRenderer.Mesh rim,
            CableRenderer.Mesh bottom,
            List<Integer> wallColors,
            List<Integer> interiorColors,
            List<Integer> rimColors) {}

    static Mesh bake(MixerRenderDescriptor descriptor) {
        var walls = new CableRenderer.Mesh(
                descriptor.walls().stream().map(MixerTankRenderer::quad).toList());
        var interior = new CableRenderer.Mesh(
                descriptor.interior().stream().map(MixerTankRenderer::quad).toList());
        var rim = new CableRenderer.Mesh(
                descriptor.rim().stream().map(MixerTankRenderer::quad).toList());
        var bottom = new CableRenderer.Mesh(
                descriptor.bottom().stream().map(MixerTankRenderer::quad).toList());
        var accents = new ArrayList<Integer>();
        for (int i = 0; i < descriptor.rim().size(); i++) accents.add(i % 8 == 0 ? COPPER : LIGHT);
        return new Mesh(
                walls,
                interior,
                rim,
                bottom,
                java.util.Collections.nCopies(walls.quads().size(), VERDIGRIS),
                java.util.Collections.nCopies(interior.quads().size(), VERDIGRIS),
                List.copyOf(accents));
    }
    /** Quad-format buffer: one fan triangle followed by a degenerate triangle, never an overlapping face. */
    private static CableRenderer.Quad quad(MixerRenderDescriptor.Face face) {
        var p = face.vertices();
        return new CableRenderer.Quad(p.get(0), p.get(1), p.get(2), p.get(p.size() == 3 ? 2 : 3), face.normal(), 0, 1);
    }

    static void render(Mesh mesh, PoseStack pose, MultiBufferSource buffers, int light) {
        metal(
                mesh.bottom(),
                java.util.Collections.nCopies(mesh.bottom().quads().size(), VERDIGRIS),
                pose,
                buffers,
                light);
        metal(mesh.rim(), mesh.rimColors(), pose, buffers, light);
        metal(mesh.walls(), mesh.wallColors(), pose, buffers, light);
        metal(mesh.interior(), mesh.interiorColors(), pose, buffers, light);
    }

    private static void metal(
            CableRenderer.Mesh mesh, List<Integer> colors, PoseStack pose, MultiBufferSource buffers, int light) {
        var buffer = buffers.getBuffer(RenderType.entitySolid(WALL));
        var matrix = pose.last();
        for (int index = 0; index < mesh.quads().size(); index++) {
            var q = mesh.quads().get(index);
            int color = colors.get(index);
            var points = List.of(q.a(), q.b(), q.c(), q.d());
            for (int i = 0; i < 4; i++) {
                var p = points.get(i);
                var n = q.normal();
                buffer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                        .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
                        .setUv(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(matrix, (float) n.x, (float) n.y, (float) n.z);
            }
        }
    }

    static CableRenderer.Mesh liquid(MixerRenderDescriptor descriptor, int layer) {
        return new CableRenderer.Mesh(descriptor.liquidSurface(layer).stream()
                .map(MixerTankRenderer::quad)
                .toList());
    }

    static void renderLiquid(
            CableRenderer.Mesh mesh, FluidStack fluid, PoseStack pose, MultiBufferSource buffers, int light) {
        if (fluid.isEmpty() || mesh.quads().isEmpty()) return;
        var appearance = IClientFluidTypeExtensions.of(fluid.getFluid());
        var texture = appearance.getStillTexture(fluid);
        if (texture == null) return;
        var sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(texture);
        int color = MixerLiquidPresentation.compoundTint(fluid, appearance.getTintColor(fluid));
        double radius = 0;
        for (var q : mesh.quads())
            radius = Math.max(radius, Math.max(Math.hypot(q.b().x, q.b().z), Math.hypot(q.c().x, q.c().z)));
        var buffer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        var matrix = pose.last();
        for (var q : mesh.quads()) {
            for (var p : List.of(q.a(), q.b(), q.c(), q.d()))
                buffer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                        .setColor(
                                (color >> 16) & 255,
                                (color >> 8) & 255,
                                color & 255,
                                MixerLiquidPresentation.vertexAlpha(color))
                        .setUv(sprite.getU((float) (.5 + p.x / (2 * radius))), sprite.getV((float)
                                (.5 + p.z / (2 * radius))))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(matrix, 0, 1, 0);
        }
    }

    private MixerTankRenderer() {}
}
