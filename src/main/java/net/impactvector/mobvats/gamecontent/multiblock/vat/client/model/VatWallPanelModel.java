package net.impactvector.mobvats.gamecontent.multiblock.vat.client.model;

import java.util.EnumMap;
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

/** Shared formed skin for functional wall parts; construction/items retain their original model. */
public final class VatWallPanelModel extends BakedModelWrapper<BakedModel> {
    private final Map<Direction, TextureAtlasSprite> faces = new EnumMap<>(Direction.class);
    private final TextureAtlasSprite lip;
    private final Map<VatWallPanelState, List<BakedQuad>> states = new ConcurrentHashMap<>();
    public VatWallPanelModel(BakedModel original, BakedModel materials) {
        super(original);
        lip = materials.getQuads(null, Direction.UP, RandomSource.create()).getFirst().getSprite();
        for (var face : Direction.values()) {
            var quads = original.getQuads(null, face, RandomSource.create());
            faces.put(face, quads.isEmpty() ? original.getParticleIcon() : quads.getFirst().getSprite());
        }
    }
    @Override public List<BakedQuad> getQuads(BlockState block, Direction side, RandomSource random,
            ModelData data, RenderType layer) {
        var state = data.get(VatWallPanelState.PROPERTY);
        if (state == null || !state.shell().formed()) return originalModel.getQuads(block, side, random, data, layer);
        if (side != null || layer != null && layer != RenderType.solid()) return List.of();
        return states.computeIfAbsent(state, snapshot -> VatCasingGeometry.build(snapshot.shell()).stream()
            .filter(snapshot::visible).map(patch -> bake(snapshot, patch)).toList());
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState block, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.solid());
    }
    private BakedQuad bake(VatWallPanelState state, VatCasingGeometry.Patch patch) {
        // Inner and outer broad faces use the same outward block-face material.
        var outward = state.blockFaceFor(patch.normal());
        var sprite = outward == null ? lip : faces.get(outward);
        var element = new BlockElementFace(null, -1, "", new BlockFaceUV(
            new float[]{patch.u0(), patch.v0(), patch.u1(), patch.v1()}, 0));
        return new FaceBakery().bakeQuad(point(patch.normal(), patch.plane(), patch.u0(), patch.v0()),
            point(patch.normal(), patch.plane(), patch.u1(), patch.v1()), element, sprite,
            patch.normal(), BlockModelRotation.X0_Y0, null, true);
    }
    private static Vector3f point(Direction face, float plane, float u, float v) {
        return switch (face.getAxis()) {
            case X -> new Vector3f(plane, v, u);
            case Y -> new Vector3f(u, plane, v);
            case Z -> new Vector3f(u, v, plane);
        };
    }
}
