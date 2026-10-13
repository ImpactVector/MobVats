package net.impactvector.mobvats.gamecontent.multiblock.vat.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatGlassState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Vector3f;

/** Chunk-rendered tank skin, with no neighbor connectivity or glass BER. */
public final class VatGlassModel extends BakedModelWrapper<BakedModel> {
    private final List<List<BakedQuad>> planes = new ArrayList<>(96);

    private record Skin(VatGlassState shell, Direction opening) {}

    private final Map<Skin, List<BakedQuad>> states = new ConcurrentHashMap<>();
    private final List<List<BakedQuad>> openings = new ArrayList<>(96);

    public VatGlassModel(BakedModel original, BakedModel material) {
        super(original);
        var surface = material.getQuads(null, Direction.UP, RandomSource.create())
                .getFirst()
                .getSprite();
        var rim = material.getParticleIcon();
        for (var face : Direction.values())
            for (int mask = 0; mask < 16; mask++) {
                planes.add(bakePlane(face, mask, surface, rim));
                openings.add(bakePlane(face, mask, surface, rim, true));
            }
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        var glass = data.get(VatGlassState.PROPERTY);
        if (glass == null || !glass.formed()) return originalModel.getQuads(state, side, random, data, layer);
        if (side != null || layer != null && layer != RenderType.translucent()) return List.of();
        // General quads intentionally bypass same-block face culling at vertical glass edges.
        // Only bounds-selected planes exist; there are no cube-internal faces to cull.
        return states.computeIfAbsent(new Skin(glass, data.get(VatCasingModel.OPENING)), snapshot -> {
            var quads = new ArrayList<BakedQuad>();
            for (var face : Direction.values())
                if (snapshot.shell().hasFace(face)) {
                    quads.addAll((face == snapshot.opening() ? openings : planes)
                            .get(face.ordinal() * 16 + snapshot.shell().edges(face)));
                }
            return List.copyOf(quads);
        });
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        var glass = data.get(VatGlassState.PROPERTY);
        return glass != null && glass.formed()
                ? ChunkRenderTypeSet.of(RenderType.translucent())
                : originalModel.getRenderTypes(state, random, data);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType layer) {
        return TriState.FALSE;
    }

    private static List<BakedQuad> bakePlane(
            Direction face, int mask, TextureAtlasSprite surface, TextureAtlasSprite rim) {
        return bakePlane(face, mask, surface, rim, false);
    }

    private static List<BakedQuad> bakePlane(
            Direction face, int mask, TextureAtlasSprite surface, TextureAtlasSprite rim, boolean opening) {
        var quads = new ArrayList<BakedQuad>();
        float[] cuts = opening ? new float[] {0, 1, 3, 13, 15, 16} : new float[] {0, 1, 15, 16};
        var bakery = new FaceBakery();
        // Partition rather than overlay: no coplanar border/surface overlap, including corners.
        for (int u = 0; u < cuts.length - 1; u++)
            for (int v = 0; v < cuts.length - 1; v++) {
                if (opening && cuts[u] >= 3 && cuts[u + 1] <= 13 && cuts[v] >= 3 && cuts[v + 1] <= 13) continue;
                boolean border = u == 0 && (mask & VatGlassState.LEFT) != 0
                        || u == cuts.length - 2 && (mask & VatGlassState.RIGHT) != 0
                        || v == 0 && (mask & VatGlassState.BOTTOM) != 0
                        || v == cuts.length - 2 && (mask & VatGlassState.TOP) != 0;
                var sprite = border ? rim : surface;
                float boundary = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : 16;
                var from = point(face, boundary, cuts[u], cuts[v]);
                var to = point(face, boundary, cuts[u + 1], cuts[v + 1]);
                // Two opposite windings of ONE plane. Backface culling selects one from either side,
                // allowing the far wall's perimeter to be seen without a second offset glass layer.
                for (var normal : new Direction[] {face, face.getOpposite()}) {
                    var element = new BlockElementFace(null, -1, "", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0));
                    quads.add(
                            bakery.bakeQuad(from, to, element, sprite, normal, BlockModelRotation.X0_Y0, null, false));
                }
            }
        return List.copyOf(quads);
    }

    private static Vector3f point(Direction face, float boundary, float u, float v) {
        return switch (face.getAxis()) {
            case X -> new Vector3f(boundary, v, u);
            case Y -> new Vector3f(u, boundary, v);
            case Z -> new Vector3f(u, v, boundary);
        };
    }
}
