package net.impactvector.mobvats.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferArt;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.client.FluidBufferRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** CPU-only test of Minecraft's actual buffer scheduling; never opens a window or draws. */
public final class VatBufferOrderingTest {
    static {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final RenderType COW =
            RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"));
    private static final RenderType STRAP = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath("mobvats", "textures/blocks/vat_casing_inner.png"));
    private static final RenderType LIQUID =
            net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMediumRenderer.liquidRenderType();
    private static final RenderType GLASS = FluidBufferRenderTypes.GLASS;

    private static final class RecordingBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
        final List<RenderType> flushed = new ArrayList<>();

        RecordingBuffers() {
            super(new ByteBufferBuilder(4096), new LinkedHashMap<>());
            fixedBuffers.put(LIQUID, new ByteBufferBuilder(4096));
            FluidBufferRenderTypes.registerBuffers(
                    new net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent(fixedBuffers));
        }

        @Override
        public void endBatch(RenderType type) {
            var builder = startedBuilders.remove(type);
            if (builder != null) {
                var mesh = builder.build();
                if (mesh != null) mesh.close();
                flushed.add(type);
            }
            if (type.equals(lastSharedType)) lastSharedType = null;
        }

        public void close() {
            endBatch();
            sharedBuffer.close();
            fixedBuffers.values().forEach(ByteBufferBuilder::close);
        }
    }

    private static void quad(RecordingBuffers source, RenderType type) {
        if (type == COW) {
            var model = new net.minecraft.client.model.CowModel<net.minecraft.world.entity.animal.Cow>(
                    net.minecraft.client.model.CowModel.createBodyLayer().bakeRoot());
            var submission =
                    new net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDiagnostics
                            .Submission(source);
            for (boolean young : new boolean[] {false, true}) {
                model.young = young;
                int before = submission.vertices();
                model.renderToBuffer(
                        new com.mojang.blaze3d.vertex.PoseStack(), submission.getBuffer(COW), 15728880, 0, -1);
                require(submission.vertices() > before, "Initialized adult/juvenile cow model did not submit geometry");
            }
            return;
        }
        var buffer = source.getBuffer(type);
        for (int i = 0; i < 4; i++)
            buffer.addVertex(i & 1, i >> 1, 0)
                    .setColor(255, 255, 255, 160)
                    .setUv(0, 0)
                    .setOverlay(0)
                    .setLight(15728880)
                    .setNormal(0, 0, 1);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void verifyGlassSorting() {
        int inversions = 0;
        // Use production faces: a large glass pane and a small liquid tile can sort in the
        // opposite order to their depths at an overlapping pixel, even on the same wall.
        for (int joins = 0; joins < 64; joins++)
            for (var glass : FluidBufferArt.mesh(joins)) {
                if (glass.material() != FluidBufferArt.Material.GLASS) continue;
                var normal = net.minecraft.world.phys.Vec3.atLowerCornerOf(
                        glass.face().getNormal());
                var camera = glass.bounds().getCenter().add(normal.scale(4)).add(.7, .3, .6);
                for (var liquid : FluidBufferArt.liquid(joins, .5)) {
                    if (liquid.face() != glass.face()) continue;
                    var point = liquid.bounds().getCenter();
                    var ray = point.subtract(camera);
                    double denominator = ray.dot(normal);
                    if (denominator >= 0) continue;
                    double t = glass.bounds().getCenter().subtract(camera).dot(normal) / denominator;
                    if (t <= 0 || t >= 1 || !glass.bounds().inflate(1e-8).contains(camera.add(ray.scale(t)))) continue;
                    var order = com.mojang.blaze3d.vertex.VertexSorting.byDistance(camera.toVector3f())
                            .sort(new org.joml.Vector3f[] {
                                glass.bounds().getCenter().toVector3f(), point.toVector3f()
                            });
                    if (order[0] == 0) inversions++;
                }
            }
        require(inversions > 0, "Production faces no longer reproduce mixed glass/liquid depth-sort inversions");
        require(GLASS != LIQUID, "Glass can still write depth ahead of liquid in the same sorted batch");
        require(
                GLASS.toString().contains("write_mask_state[writeColor=true, writeDepth=false]"),
                "Glass must preserve the depth buffer while overlaying liquid");
        System.out.println("PASS: reproduced " + inversions
                + " mixed glass/liquid sort inversions; glass uses a separate color-only pass.");
    }

    public static void main(String[] args) {
        verifyGlassSorting();
        require(
                LIQUID == RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
                "Liquid type is not Minecraft's fixed translucent atlas buffer");
        require(
                net.impactvector.mobvats.gamecontent.multiblock.conduit.client.FluidBufferRenderer.translucentType()
                        == LIQUID,
                "Buffer liquid bypasses deferred transparency");
        var old = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        try (var source = new RecordingBuffers()) {
            quad(source, old);
            quad(source, STRAP);
            quad(source, COW);
            source.endLastBatch();
            require(
                    source.flushed.indexOf(old) < source.flushed.indexOf(COW),
                    "Old path did not reproduce premature translucent flush");
        }
        // Both Head/Controller orders, and two cows with independently ordered Controllers.
        var sequences = List.of(
                List.of(LIQUID, STRAP, COW),
                List.of(STRAP, COW, LIQUID),
                List.of(LIQUID, STRAP, COW, LIQUID, STRAP, COW),
                List.of(STRAP, COW, LIQUID, STRAP, COW, LIQUID),
                List.of(LIQUID, LIQUID, STRAP, COW, STRAP, COW),
                List.of(STRAP, COW, STRAP, COW, LIQUID, LIQUID));
        for (var sequence : sequences)
            try (var source = new RecordingBuffers()) {
                sequence.forEach(type -> {
                    quad(source, type);
                    if (type == LIQUID) quad(source, GLASS);
                });
                require(!source.flushed.contains(LIQUID), "Liquid flushed while BERs were submitting opaque geometry");
                require(!source.flushed.contains(GLASS), "Glass flushed while BERs were submitting opaque geometry");
                // LevelRenderer's actual ordering at the end of the opaque BER pass.
                source.endLastBatch();
                source.endBatch(LIQUID);
                require(!source.flushed.contains(GLASS), "Glass flushed before the liquid pass finished");
                source.endBatch();
                require(source.flushed.lastIndexOf(COW) < source.flushed.indexOf(LIQUID), "Cow drawn after liquid");
                require(source.flushed.indexOf(LIQUID) < source.flushed.indexOf(GLASS), "Glass drawn before liquid");
            }
        System.out.println(
                "PASS: old premature flush reproduced; adult/juvenile cow models submit vertices; deferred liquid follows all cows in six BER orders.");
    }
}
