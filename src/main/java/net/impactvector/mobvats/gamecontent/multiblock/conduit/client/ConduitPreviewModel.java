package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import net.impactvector.mobvats.MobVats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Converts generated branch cuboids to square frusta once at bake time. No world queries. */
public final class ConduitPreviewModel extends BakedModelWrapper<BakedModel> {
    private final List<BakedQuad> quads;

    public ConduitPreviewModel(BakedModel original) {
        super(original);
        var location = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "conduit_preview.json");
        try (var reader = Minecraft.getInstance()
                .getResourceManager()
                .getResourceOrThrow(location)
                .openAsReader()) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            float width = config.get("nodeWidth").getAsFloat() / 16;
            float ratio = config.get("tapEndpointRatio").getAsFloat();
            quads = original.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null).stream()
                    .map(q -> taper(q, width, ratio))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Missing generated conduit dimensions", ex);
        }
    }

    private static BakedQuad taper(BakedQuad quad, float width, float ratio) {
        // Only the dedicated pipe material is tapered; corner fittings and the node remain cubic.
        if (!quad.getSprite().contents().name().getPath().contains("conduit_pipe_")) return quad;
        int[] vertices = taperVertices(quad.getVertices(), width, ratio);
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    public static int[] taperVertices(int[] source, float width, float ratio) {
        int[] vertices = source.clone();
        int stride = vertices.length / 4;
        float lo = .5f - width / 2, hi = .5f + width / 2;
        int axis = -1;
        boolean positive = false;
        for (int a = 0; a < 3; a++) {
            boolean below = true, above = true, endpoint = false;
            for (int v = 0; v < 4; v++) {
                float p = Float.intBitsToFloat(vertices[v * stride + a]);
                below &= p <= lo + 1e-6;
                above &= p >= hi - 1e-6;
                endpoint |= p < 1e-6 || p > 1 - 1e-6;
            }
            if (endpoint && (below || above)) {
                axis = a;
                positive = above;
                break;
            }
        }
        if (axis < 0) return vertices;
        for (int v = 0; v < 4; v++) {
            float p = Float.intBitsToFloat(vertices[v * stride + axis]);
            float distance = positive ? (p - hi) / (1 - hi) : (lo - p) / lo;
            float scale = 1 - distance * (1 - ratio);
            for (int a = 0; a < 3; a++)
                if (a != axis) {
                    int index = v * stride + a;
                    float transverse = Float.intBitsToFloat(vertices[index]);
                    vertices[index] = Float.floatToRawIntBits(.5f + (transverse - .5f) * scale);
                }
        }
        // Update packed vertex normals for the sloping sides.
        var a = point(vertices, 0, stride);
        var b = point(vertices, 1, stride);
        var c = point(vertices, 2, stride);
        var normal = b.sub(a, new org.joml.Vector3f())
                .cross(c.sub(a, new org.joml.Vector3f()))
                .normalize();
        int packed = ((int) (normal.x * 127) & 255)
                | (((int) (normal.y * 127) & 255) << 8)
                | (((int) (normal.z * 127) & 255) << 16);
        for (int v = 0; v < 4; v++) vertices[v * stride + 7] = packed;
        return vertices;
    }

    private static org.joml.Vector3f point(int[] vertices, int v, int stride) {
        return new org.joml.Vector3f(
                Float.intBitsToFloat(vertices[v * stride]),
                Float.intBitsToFloat(vertices[v * stride + 1]),
                Float.intBitsToFloat(vertices[v * stride + 2]));
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        return side == null
                        && (layer == null
                                || originalModel
                                        .getRenderTypes(state, random, data)
                                        .contains(layer))
                ? quads
                : List.of();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? quads : List.of();
    }
}
