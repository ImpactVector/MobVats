package net.impactvector.mobvats.gamecontent.multiblock.common.client;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.VatCasingModel;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;
import org.joml.*;

/** Chunk-owned stationary machinery. Assembly and TAP coverage are chunk snapshots,
 * never frame-time neighborhood reads. A resource reload recreates all mesh caches. */
public final class FixtureModel extends BakedModelWrapper<BakedModel> {
    public static final Set<String> INSTALLED = Set.of(
            "vat_controller", "mixer_controller", "vat_harness", "vat_drain", "feed_port", "blender", "tool_port");
    public static final Set<String> IDS = Set.of(
            "item_port",
            "fluid_port",
            "fill_valve",
            "drain_valve",
            "pull_pump",
            "push_pump",
            "fluid_void",
            "item_void",
            "vat_controller",
            "mixer_controller",
            "vat_harness",
            "vat_drain",
            "feed_port",
            "blender",
            "tool_port");

    private record Snapshot(boolean formed, Direction facing, int covered, VatCasingState shell, Direction inlet) {}

    private static final ModelProperty<Snapshot> STATE = new ModelProperty<>();
    private static final Snapshot UNFORMED =
            new Snapshot(false, Direction.NORTH, 0, VatCasingState.UNFORMED, Direction.EAST);
    private List<BakedQuad> core;
    private final List<BakedQuad> construction;
    private final List<BakedQuad> sleeve;
    private BakedModel shell;
    private CladdingMaterial material = CladdingMaterial.VAT_T1_CASING;
    private final boolean pump, sink;
    private boolean installed, controller, actor, toolPort, feeder, port;
    private final Set<BakedQuad> constructionInlet = new HashSet<>();
    private final Map<Snapshot, List<BakedQuad>> meshes = new ConcurrentHashMap<>();

    FixtureModel(
            BakedModel original,
            BakedModel shell,
            List<BakedQuad> core,
            List<BakedQuad> construction,
            boolean pump,
            boolean sink) {
        this(original, shell, core, construction, pump, sink, List.of());
    }

    FixtureModel(
            BakedModel original,
            BakedModel shell,
            List<BakedQuad> core,
            List<BakedQuad> construction,
            boolean pump,
            boolean sink,
            List<BakedQuad> sleeve) {
        super(original);
        this.sleeve = List.copyOf(sleeve);
        this.shell = shell;
        this.core = List.copyOf(core);
        this.construction = List.copyOf(construction);
        this.pump = pump;
        this.sink = sink;
    }

    FixtureModel(BakedModel original, BakedModel shell, List<BakedQuad> core, List<BakedQuad> construction, String id) {
        this(original, shell, core, construction, false, false);
        installed = INSTALLED.contains(id);
        controller = id.endsWith("_controller");
        port = id.equals("item_port") || id.equals("fluid_port");
        actor = actor(id);
        toolPort = id.equals("tool_port");
        feeder = id.equals("feed_port");
        if (feeder) {
            constructionInlet.addAll(construction);
            constructionInlet.removeAll(core);
        }
    }

    public FixtureModel(BakedModel original, BakedModel casing, String id, BakedModel extension) {
        this(original, casing, id, id.endsWith("_pump") ? loadAuger() : List.of(), extension);
    }

    FixtureModel(BakedModel original, BakedModel casing, String id, List<Vector3f[]> auger, BakedModel extension) {
        super(original);
        pump = id.endsWith("_pump");
        sink = id.endsWith("_void");
        installed = INSTALLED.contains(id);
        controller = id.endsWith("_controller");
        port = id.equals("item_port") || id.equals("fluid_port");
        actor = actor(id);
        toolPort = id.equals("tool_port");
        feeder = id.equals("feed_port");
        shell = new VatCasingModel(original, casing);
        sleeve = extension == null
                ? List.of()
                : List.copyOf(extension.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null));
        var all = new ArrayList<BakedQuad>();
        var body = new ArrayList<BakedQuad>();
        BakedQuad seed = null;
        for (var q : original.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null)) {
            String texture = q.getSprite().contents().name().getPath();
            if (texture.endsWith("/fixture_auger")) {
                seed = q;
                continue;
            }
            all.add(q);
            if (feeder && texture.endsWith("/feeder_connection")) constructionInlet.add(q);
            boolean structural = texture.endsWith("_frame") || texture.endsWith("_trim") || texture.endsWith("_accent");
            if (feeder
                    ? !constructionInlet.contains(q)
                    : !structural && !(toolPort && texture.contains("/machinery_vise_"))) body.add(q);
        }
        if (pump) {
            if (seed == null) throw new IllegalStateException("Missing auger material: " + id);
            for (var points : auger) {
                var baked = withPoints(seed, points);
                body.add(baked);
                all.add(baked);
            }
        }
        core = List.copyOf(body);
        construction = List.copyOf(all);
    }

    public FixtureModel withCladding(CladdingMaterial material, BakedModel glass) {
        this.material = material;
        if (actor) {
            installed = false;
            core = core.stream().filter(q -> !constructionFrame(q)).toList();
        }
        if (material.cladding() == ConduitCladding.GLASS)
            shell = new net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.VatGlassModel(
                    originalModel, glass);
        return this;
    }

    private static boolean constructionFrame(BakedQuad quad) {
        String texture = quad.getSprite().contents().name().getPath();
        if (!(texture.endsWith("_frame") || texture.endsWith("_trim") || texture.endsWith("_accent"))) return false;
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        int edgeAxes = 0;
        for (int axis = 0; axis < 3; axis++) {
            boolean low = true, high = true;
            for (int v = 0; v < 4; v++) {
                float value = Float.intBitsToFloat(vertices[v * stride + axis]);
                low &= value <= 2f / 16;
                high &= value >= 14f / 16;
            }
            if (low || high) edgeAxes++;
        }
        return edgeAxes >= 2;
    }

    private static List<Vector3f[]> loadAuger() {
        var location = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "fixture_mesh.json");
        try (var reader = Minecraft.getInstance()
                .getResourceManager()
                .getResourceOrThrow(location)
                .openAsReader()) {
            var result = new ArrayList<Vector3f[]>();
            for (var quad : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("auger")) {
                var points = new Vector3f[4];
                int i = 0;
                for (var p : quad.getAsJsonArray()) {
                    var a = p.getAsJsonArray();
                    points[i++] = new Vector3f(
                            a.get(0).getAsFloat() / 16,
                            a.get(1).getAsFloat() / 16,
                            a.get(2).getAsFloat() / 16);
                }
                result.add(points);
            }
            return List.copyOf(result);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing procedural auger mesh", ex);
        }
    }

    static ModelData snapshotData(boolean formed, Direction facing, int covered, VatCasingState casing) {
        return snapshotData(formed, facing, covered, casing, Direction.EAST);
    }

    static ModelData snapshotData(
            boolean formed, Direction facing, int covered, VatCasingState casing, Direction inlet) {
        return ModelData.builder()
                .with(STATE, new Snapshot(formed, facing, covered, casing, inlet))
                .with(VatCasingState.PROPERTY, casing)
                .build();
    }
    /** Explicit triangle normals replace the seed's axis normals. */
    public static BakedQuad withPoints(BakedQuad template, Vector3f[] points) {
        int[] vertices = template.getVertices().clone();
        int stride = vertices.length / 4;
        var normal = new Vector3f(points[1])
                .sub(points[0])
                .cross(new Vector3f(points[2]).sub(points[0]))
                .normalize();
        int packed = ((int) (normal.x * 127) & 255)
                | (((int) (normal.y * 127) & 255) << 8)
                | (((int) (normal.z * 127) & 255) << 16);
        for (int v = 0; v < 4; v++) {
            vertices[v * stride] = Float.floatToRawIntBits(points[v].x);
            vertices[v * stride + 1] = Float.floatToRawIntBits(points[v].y);
            vertices[v * stride + 2] = Float.floatToRawIntBits(points[v].z);
            vertices[v * stride + 7] = packed;
        }
        return new BakedQuad(
                vertices,
                template.getTintIndex(),
                Direction.getNearest(normal.x, normal.y, normal.z),
                template.getSprite(),
                true);
    }

    public static BakedQuad orient(BakedQuad quad, Direction facing) {
        if (facing == Direction.NORTH) return quad;
        var rotation = new Quaternionf()
                .rotationTo(
                        new Vector3f(0, 0, -1), new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
        int[] source = quad.getVertices();
        int stride = source.length / 4;
        var points = new Vector3f[4];
        for (int v = 0; v < 4; v++) {
            var p = new Vector3f(
                    Float.intBitsToFloat(source[v * stride]),
                    Float.intBitsToFloat(source[v * stride + 1]),
                    Float.intBitsToFloat(source[v * stride + 2]));
            points[v] = rotation.transform(p.sub(.5f, .5f, .5f)).add(.5f, .5f, .5f);
        }
        return withPoints(quad, points);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
        boolean formed = false;
        var casing = VatCasingState.UNFORMED;
        var glass = VatGlassState.UNFORMED;
        var entity = world.getBlockEntity(pos);
        if (entity instanceof AbstractVatEntity part) {
            var c = part.getMultiblockController()
                    .filter(MultiblockVat::isAssembled)
                    .orElse(null);
            if (c != null) {
                formed = true;
                var b = c.getBoundingBox();
                casing = VatCasingState.from(pos, b.getMin(), b.getMax());
                glass = VatGlassState.from(pos, b.getMin(), b.getMax());
            }
        } else if (entity instanceof AbstractMixerEntity part) {
            var c = part.getMultiblockController()
                    .filter(net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer::isAssembled)
                    .orElse(null);
            if (c != null) {
                formed = true;
                var b = c.getBoundingBox();
                casing = VatCasingState.from(pos, b.getMin(), b.getMax());
                glass = VatGlassState.mixer(pos, b.getMin(), b.getMax());
            }
        }
        Direction facing = controller
                        && state.hasProperty(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        int covered = 0;
        if (actor && !feeder && formed && !casing.formed() && entity instanceof AbstractVatEntity part) {
            var c = part.getMultiblockController().orElseThrow();
            if (!c.getAssemblies().isEmpty()) {
                var b = c.getBoundingBox();
                var link = VatInteractionGeometry.link(
                        b.getMin(), b.getMax(), c.getAssemblies().getFirst(), pos, true, !toolPort);
                facing = link.inward().getOpposite();
            }
        }
        for (var face : Direction.values()) {
            if (casing.outward(face)) facing = face;
            if (sink && formed) {
                var adjacent = world.getBlockState(pos.relative(face));
                if (adjacent.getBlock() instanceof ConduitBlock
                        && adjacent.getValue(ConduitBlock.MODE) == ConduitMode.TAP) covered |= 1 << face.ordinal();
            }
        }
        Direction inlet = Direction.EAST;
        if (feeder) {
            // Choose a spindle axis during normal chunk model-data collection.
            // Formed geometry prefers its immutable physical fitting snapshot.
            for (var resource : List.of(ConduitResource.FLUID, ConduitResource.ITEM)) {
                boolean found = false;
                for (var face : Direction.values())
                    if (world.getBlockState(pos.relative(face)).getBlock() instanceof ConduitBlock conduit
                            && conduit.resource() == resource) {
                        inlet = face;
                        found = true;
                        break;
                    }
                if (found) break;
            }
            if (formed && entity instanceof AbstractVatEntity part)
                inlet = net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.FeederArt.inlet(
                                part.getMultiblockController().orElseThrow().conduitGeometry(), pos)
                        .orElse(inlet);
        }
        return data.derive()
                .with(STATE, new Snapshot(formed, facing, covered, casing, inlet))
                .with(VatCasingState.PROPERTY, casing)
                .with(VatGlassState.PROPERTY, glass)
                .build();
    }

    private List<BakedQuad> body(Snapshot snapshot) {
        return meshes.computeIfAbsent(snapshot, s -> {
            boolean exterior = pump && s.formed && s.shell.outward(s.facing);
            var quads = new ArrayList<BakedQuad>();
            for (var q : s.formed ? core : construction) {
                if (actor
                        && !installed
                        && material != CladdingMaterial.NONE
                        && s.formed
                        && s.shell.formed()
                        && backingPlate(q)) continue;
                // Reels stay centered in their cell. Rotate the spindle and wound
                // hose together toward the actual inlet, with no wall-panel transform.
                if (feeder) {
                    quads.add(net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.FeederArt.orientInlet(
                            q, s.inlet));
                    continue;
                }
                // Assembled Controllers retain only the 3px front panel, before outward rotation.
                if (controller && s.formed && controllerRear(q)) continue;
                // Only the mating surface is hidden; central housing and auger stay in place.
                if (exterior && !sleeve.isEmpty() && onPlane(q, Direction.NORTH, 3f / 16)) continue;
                // Interior service origins are the block center instead of the wall's
                // 3.16px origin. Translate decorative machinery only, before rotation.
                var mounted = actor && s.formed && !s.shell.formed() ? interiorMount(q) : q;
                var oriented = pump || controller || actor || port ? orient(mounted, s.facing) : mounted;
                if (!hidden(oriented, s.covered)) quads.add(oriented);
            }
            if (exterior) for (var q : sleeve) quads.add(orient(q, s.facing));
            return List.copyOf(quads);
        });
    }
    /** The installed shell replaces the old full-width mounting plate on exterior service fixtures. */
    private static boolean backingPlate(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        float minX = 1, maxX = 0, minY = 1, maxY = 0, maxZ = 0;
        for (int v = 0; v < 4; v++) {
            float x = Float.intBitsToFloat(vertices[v * stride]),
                    y = Float.intBitsToFloat(vertices[v * stride + 1]),
                    z = Float.intBitsToFloat(vertices[v * stride + 2]);
            minX = java.lang.Math.min(minX, x);
            maxX = java.lang.Math.max(maxX, x);
            minY = java.lang.Math.min(minY, y);
            maxY = java.lang.Math.max(maxY, y);
            maxZ = java.lang.Math.max(maxZ, z);
        }
        return maxZ <= 3f / 16 && (minX == 0 && maxX == 1 || minY == 0 && maxY == 1);
    }

    private static boolean actor(String id) {
        return id.equals("feed_port") || id.equals("blender") || id.equals("tool_port");
    }

    static BakedQuad interiorMount(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        float offset = (float) (.5 - EndpointGeometry.PANEL_DEPTH - .01);
        for (int v = 0; v < 4; v++)
            vertices[v * stride + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[v * stride + 2]) + offset);
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    static boolean controllerRear(BakedQuad quad) {
        float panelDepth = VatCasingGeometry.DEPTH / 16f;
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int v = 0; v < 4; v++) if (Float.intBitsToFloat(vertices[v * stride + 2]) > panelDepth + 1e-6) return true;
        // Remove the rear cuboid's front cap at z=3, but retain the panel's inward-facing back.
        return onPlane(quad, Direction.NORTH, panelDepth);
    }

    static boolean onPlane(BakedQuad quad, Direction face, float plane) {
        if (quad.getDirection() != face) return false;
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int v = 0; v < 4; v++)
            if (java.lang.Math.abs(Float.intBitsToFloat(
                                    vertices[v * stride + face.getAxis().ordinal()])
                            - plane)
                    > 1e-6) return false;
        return true;
    }

    private boolean hidden(BakedQuad quad, int covered) {
        if (!sink || covered == 0) return false;
        // Clip the exposed opening and bars only; retain the reinforced chamber.
        String texture = quad.getSprite().contents().name().getPath();
        return coveredSurface(texture, quad.getDirection(), covered);
    }

    static boolean coveredSurface(String texture, Direction face, int covered) {
        return (covered & (1 << face.ordinal())) != 0
                && (texture.endsWith("/fixture_grate") || texture.endsWith("/fixture_hot"));
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        if (side != null) return List.of();
        var snapshot = data.get(STATE);
        if (snapshot == null) snapshot = UNFORMED;
        var result = new ArrayList<BakedQuad>();
        if (layer == null || layer == RenderType.solid()) result.addAll(body(snapshot));
        if (!installed
                && material != CladdingMaterial.NONE
                && snapshot.formed
                && snapshot.shell.formed()
                && (layer == null
                        || layer
                                == (material.cladding() == ConduitCladding.GLASS
                                        ? RenderType.translucent()
                                        : RenderType.solid()))) {
            var skinData = pump || port
                    ? data.derive()
                            .with(VatCasingModel.OPENING, snapshot.facing)
                            .build()
                    : data;
            result.addAll(shell.getQuads(state, null, random, skinData, layer));
        }
        return result;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? construction : List.of();
    }
    // The default wrapper delegates both methods to the seed model, discarding
    // the authored screw mesh during item rendering.
    @Override
    public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack, boolean fabulous) {
        return List.of(this);
    }

    @Override
    public BakedModel applyTransform(
            net.minecraft.world.item.ItemDisplayContext context,
            com.mojang.blaze3d.vertex.PoseStack poses,
            boolean leftHand) {
        originalModel.applyTransform(context, poses, leftHand);
        return this;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return material.cladding() == ConduitCladding.GLASS
                ? ChunkRenderTypeSet.of(RenderType.solid(), RenderType.translucent())
                : ChunkRenderTypeSet.of(RenderType.solid());
    }
}
