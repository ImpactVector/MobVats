package net.impactvector.mobvats.gamecontent.multiblock.vat.client.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
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
import org.joml.Vector3f;

/** Outward hull plus shallow inner rims; ordinary full-cube casing remains the item/build-time model. */
public final class VatCasingModel extends BakedModelWrapper<BakedModel> {
    private final TextureAtlasSprite face, edge, inner;
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<Direction> OPENING = new net.neoforged.neoforge.client.model.data.ModelProperty<>();
    private record Skin(VatCasingState shell, Direction opening) {}
    private final Map<Skin, List<BakedQuad>> states = new ConcurrentHashMap<>();
    public VatCasingModel(BakedModel original, BakedModel materials) {
        super(original);
        face = materials.getParticleIcon();
        edge = materials.getQuads(null, Direction.UP, RandomSource.create()).getFirst().getSprite();
        inner = materials.getQuads(null, Direction.NORTH, RandomSource.create()).getFirst().getSprite();
    }
    @Override public List<BakedQuad> getQuads(BlockState block, Direction side, RandomSource random,
            ModelData data, RenderType layer) {
        var state = data.get(VatCasingState.PROPERTY);
        if (state == null || !state.formed()) return originalModel.getQuads(block, side, random, data, layer);
        if (side != null || layer != null && layer != RenderType.solid()) return List.of();
        return states.computeIfAbsent(new Skin(state, data.get(OPENING)), snapshot -> VatCasingGeometry.build(snapshot.shell(), snapshot.opening()).stream().map(this::bake).toList());
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState block, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.solid());
    }
    private BakedQuad bake(VatCasingGeometry.Patch patch) {
        var sprite = switch (patch.material()) {
            case FACE -> face;
            case EDGE -> edge;
            case INNER -> inner;
        };
        var from = point(patch.normal(), patch.plane(), patch.u0(), patch.v0());
        var to = point(patch.normal(), patch.plane(), patch.u1(), patch.v1());
        var element = new BlockElementFace(null, -1, "", new BlockFaceUV(
            new float[]{textureCoordinate(patch.u0(), patch.u0(), patch.u1()),
                textureCoordinate(patch.v0(), patch.v0(), patch.v1()),
                textureCoordinate(patch.u1(), patch.u0(), patch.u1()),
                textureCoordinate(patch.v1(), patch.v0(), patch.v1())}, 0));
        var quad = new FaceBakery().bakeQuad(from, to, element, sprite, patch.normal(), BlockModelRotation.X0_Y0, null, true);
        if (patch.edgeDirection() != null) orientEdge(quad, patch, sprite);
        return quad;
    }
    private static void orientEdge(BakedQuad quad, VatCasingGeometry.Patch patch, TextureAtlasSprite sprite) {
        // The bright edge of this semantic texture (V=0) points toward the actual world edge.
        var edge = patch.edgeDirection();
        var vAxis = edge.getAxis() == patch.normal().getAxis() ? VatCasingState.vAxis(patch.normal()) : edge.getAxis();
        var uAxis = vAxis == VatCasingState.uAxis(patch.normal())
            ? VatCasingState.vAxis(patch.normal()) : VatCasingState.uAxis(patch.normal());
        int[] vertices = quad.getVertices();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            float u = Float.intBitsToFloat(vertices[offset + VatCasingGeometry.axis(uAxis)]);
            float v = Float.intBitsToFloat(vertices[offset + VatCasingGeometry.axis(vAxis)]);
            u = Math.clamp(u, 0, 1);
            boolean alongU = vAxis == VatCasingState.uAxis(patch.normal());
            float low = (alongU ? patch.u0() : patch.v0()) / 16.0f;
            float high = (alongU ? patch.u1() : patch.v1()) / 16.0f;
            v = Math.clamp((v - low) / (high - low), 0, 1);
            if (edge.getAxisDirection() == Direction.AxisDirection.POSITIVE) v = 1 - v;
            vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(u));
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(v));
        }
    }
    private static float textureCoordinate(int value, int low, int high) {
        // Extruded geometry can lie outside 0..16; its UVs must stay in this sprite.
        return high <= 0 ? value + 16 : low >= 16 ? value - 16 : Math.clamp(value, 0, 16);
    }
    private static Vector3f point(Direction face, float plane, float u, float v) {
        return switch (face.getAxis()) {
            case X -> new Vector3f(plane, v, u);
            case Y -> new Vector3f(u, plane, v);
            case Z -> new Vector3f(u, v, plane);
        };
    }
}
