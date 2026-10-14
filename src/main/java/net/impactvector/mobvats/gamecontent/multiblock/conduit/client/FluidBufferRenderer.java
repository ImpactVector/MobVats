package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.CableRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** One reservoir render owner in both assembly states. No neighborhood reads or per-frame baking. */
public final class FluidBufferRenderer implements BlockEntityRenderer<FluidBufferEntity> {
    private static final ResourceLocation GLASS =
            ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/procedural/t1/buffer_glass");
    private static final ResourceLocation METAL =
            ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/procedural/t1/buffer_metal");

    public static RenderType translucentType() {
        return net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMediumRenderer.liquidRenderType();
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(texture);
    }

    private record Shape(CableRenderer.Mesh metal, CableRenderer.Mesh glass) {}

    private static final Map<List<FluidBufferArt.Panel>, Shape> SHAPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        int joins = -1;
        double height = -1;
        Shape shape;
        CableRenderer.Mesh liquid;

        void update(FluidBufferEntity entity) {
            boolean connection = joins != entity.visualJoins();
            if (connection) {
                joins = entity.visualJoins();
                shape = SHAPES.computeIfAbsent(
                        FluidBufferArt.mesh(joins),
                        panels -> new Shape(
                                mesh(panels, FluidBufferArt.Material.METAL),
                                mesh(panels, FluidBufferArt.Material.GLASS)));
            }
            if (connection || height != entity.visualHeight()) {
                height = entity.visualHeight();
                liquid = mesh(FluidBufferArt.liquid(joins, height), null);
            }
        }
    }

    private final Map<FluidBufferEntity, State> states = new WeakHashMap<>();
    private static final java.util.Set<FluidBufferRenderer> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    public FluidBufferRenderer(BlockEntityRendererProvider.Context context) {
        INSTANCES.add(this);
    }

    public static void clearVisualCaches() {
        for (var renderer : INSTANCES) renderer.states.clear();
    }

    public static void retireChunk(net.minecraft.world.level.Level level, net.minecraft.world.level.ChunkPos chunk) {
        for (var renderer : INSTANCES)
            renderer.states
                    .keySet()
                    .removeIf(part -> part.getLevel() == level
                            && new net.minecraft.world.level.ChunkPos(part.getBlockPos()).equals(chunk));
    }

    public static void retireLevel(net.minecraft.world.level.Level level) {
        for (var renderer : INSTANCES) renderer.states.keySet().removeIf(part -> part.getLevel() == level);
    }

    public static void maintainVisualCaches(net.minecraft.client.multiplayer.ClientLevel current) {
        for (var renderer : INSTANCES)
            renderer.states
                    .keySet()
                    .removeIf(part -> current == null
                            || part.getLevel() != current
                            || part.isRemoved()
                            || current.getChunkSource()
                                            .getChunk(
                                                    part.getBlockPos().getX() >> 4,
                                                    part.getBlockPos().getZ() >> 4,
                                                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                                    false)
                                    == null
                            || current.getBlockEntity(part.getBlockPos()) != part);
    }

    public static CableRenderer.Mesh mesh(List<FluidBufferArt.Panel> panels, FluidBufferArt.Material material) {
        var quads = new ArrayList<CableRenderer.Quad>();
        for (var panel : panels)
            if (material == null || material == panel.material()) {
                var b = panel.bounds();
                var face = panel.face();
                var ua = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.uAxis(face);
                var va = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.vAxis(face);
                var low = new double[] {b.minX, b.minY, b.minZ};
                var high = new double[] {b.maxX, b.maxY, b.maxZ};
                var points = new ArrayList<Vec3>();
                for (int i = 0; i < 4; i++) {
                    var p = low.clone();
                    p[ua.ordinal()] = (i == 1 || i == 2) ? high[ua.ordinal()] : low[ua.ordinal()];
                    p[va.ordinal()] = i >= 2 ? high[va.ordinal()] : low[va.ordinal()];
                    points.add(new Vec3(p[0], p[1], p[2]));
                }
                var normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                if (points.get(1)
                                .subtract(points.get(0))
                                .cross(points.get(3).subtract(points.get(0)))
                                .dot(normal)
                        < 0) Collections.reverse(points);
                quads.add(new CableRenderer.Quad(
                        points.get(0), points.get(1), points.get(2), points.get(3), normal, 0, 1));
                if (material != null)
                    quads.add(new CableRenderer.Quad(
                            points.get(3), points.get(2), points.get(1), points.get(0), normal.scale(-1), 0, 1));
            }
        return new CableRenderer.Mesh(quads);
    }

    @Override
    public void render(
            FluidBufferEntity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        var state = states.computeIfAbsent(entity, k -> new State());
        state.update(entity);
        draw(state.shape.metal(), sprite(METAL), 0xffffffff, pose, buffers, light, false);
        var fluid = entity.visualFluid();
        if (!fluid.isEmpty() && !state.liquid.quads().isEmpty()) {
            var appearance = IClientFluidTypeExtensions.of(fluid.getFluid());
            var texture = appearance.getStillTexture(fluid);
            if (texture != null) {
                var sprite = sprite(texture);
                int tint = MixerLiquidPresentation.compoundTint(fluid, appearance.getTintColor(fluid));
                int liquidLight = MixerLiquidPresentation.liquidLight(
                        light, light, fluid.getFluidType().getLightLevel(fluid));
                draw(state.liquid, sprite, tint, pose, buffers, liquidLight, true);
            }
        }
        draw(state.shape.glass(), sprite(GLASS), 0xffffffff, pose, buffers, light, true);
    }

    private static void draw(
            CableRenderer.Mesh mesh,
            TextureAtlasSprite sprite,
            int tint,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            boolean translucent) {
        // Fixed atlas buffers defer transparency until after all opaque block entities.
        var buffer = buffers.getBuffer(translucent ? translucentType() : Sheets.solidBlockSheet());
        var matrix = pose.last();
        for (var quad : mesh.quads())
            for (var p : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                var n = quad.normal();
                double u = n.x != 0 ? p.z : p.x, v = n.y != 0 ? p.z : p.y;
                float tu = sprite.getU((float) u), tv = sprite.getV((float) v);
                buffer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                        .setColor((tint >>> 16) & 255, (tint >>> 8) & 255, tint & 255, (tint >>> 24) & 255)
                        .setUv(tu, tv)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(matrix, (float) n.x, (float) n.y, (float) n.z);
            }
    }
}
