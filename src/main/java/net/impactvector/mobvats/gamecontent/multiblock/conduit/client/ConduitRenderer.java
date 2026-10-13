package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;

/** Cached immutable meshes shared by the Vat Head and Mixer Controller renderer. No world discovery. */
public final class ConduitRenderer {
    private static final ResourceLocation
            ITEM =
                    ResourceLocation.fromNamespaceAndPath(
                            MobVats.MOD_ID, "textures/block/procedural/t1/conduit_pipe_item.png"),
            FLUID =
                    ResourceLocation.fromNamespaceAndPath(
                            MobVats.MOD_ID, "textures/block/procedural/t1/conduit_pipe_fluid.png"),
            COLLAR = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "textures/blocks/vat_casing_face.png");
    private static final ResourceLocation FEED =
            ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "textures/block/procedural/t1/vat_frame.png");

    private record Batch(ResourceLocation material, CableRenderer.Mesh mesh) {}

    private static final Map<ConduitGeometry.Geometry, List<Batch>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean visible(VatRenderDetail detail) {
        return detail == VatRenderDetail.FULL || detail == VatRenderDetail.REDUCED;
    }

    private static List<Batch> bake(ConduitGeometry.Geometry geometry) {
        var batches = new LinkedHashMap<ResourceLocation, List<CableRenderer.Quad>>();
        for (var volume : geometry.pipeSegments()) {
            var material = volume.material() == ConduitGeometry.Material.COLLAR
                    ? COLLAR
                    : volume.resource() == ConduitResource.ITEM ? ITEM : FLUID;
            batches.computeIfAbsent(material, k -> new ArrayList<>()).addAll(box(volume.bounds()));
        }
        for (var fitting : geometry.endpointFittings())
            for (var volume : fitting.volumes()) {
                var material = fitting.endpoint().kind() == EndpointGeometry.Kind.FEED
                        ? FEED
                        : volume.material() == ConduitGeometry.Material.COLLAR
                                ? COLLAR
                                : volume.resource() == ConduitResource.ITEM ? ITEM : FLUID;
                batches.computeIfAbsent(material, k -> new ArrayList<>()).addAll(box(volume.bounds()));
            }
        // One neutral terminal bridge per physical Feed Port, independent of cow count.
        for (var terminal :
                net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.FeederArt.terminals(geometry))
            for (var bounds : terminal.bridge())
                batches.computeIfAbsent(FEED, k -> new ArrayList<>()).addAll(box(bounds));
        return batches.entrySet().stream()
                .map(e -> new Batch(e.getKey(), new CableRenderer.Mesh(e.getValue())))
                .toList();
    }

    private static List<CableRenderer.Quad> box(AABB b) {
        var vertices = new Vec3[] {
            new Vec3(b.minX, b.minY, b.minZ),
            new Vec3(b.maxX, b.minY, b.minZ),
            new Vec3(b.maxX, b.maxY, b.minZ),
            new Vec3(b.minX, b.maxY, b.minZ),
            new Vec3(b.minX, b.minY, b.maxZ),
            new Vec3(b.maxX, b.minY, b.maxZ),
            new Vec3(b.maxX, b.maxY, b.maxZ),
            new Vec3(b.minX, b.maxY, b.maxZ)
        };
        int[][] faces = {{3, 2, 1, 0}, {6, 7, 4, 5}, {7, 3, 0, 4}, {2, 6, 5, 1}, {0, 1, 5, 4}, {7, 6, 2, 3}};
        var quads = new ArrayList<CableRenderer.Quad>();
        for (var indices : faces) {
            var a = vertices[indices[0]];
            var c = vertices[indices[2]];
            var d = vertices[indices[3]];
            var next = vertices[indices[1]];
            quads.add(new CableRenderer.Quad(
                    a, next, c, d, next.subtract(a).cross(d.subtract(a)).normalize(), 0, 1));
        }
        return quads;
    }

    public static void render(
            ConduitGeometry.Geometry geometry,
            VatRenderDetail detail,
            Vec3 origin,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        if (!visible(detail) || geometry.snapshot().cells().isEmpty()) return;
        for (var batch : CACHE.computeIfAbsent(geometry, ConduitRenderer::bake))
            CableRenderer.renderCable(batch.mesh(), origin, batch.material(), pose, buffers, light);
    }

    private ConduitRenderer() {}
}
