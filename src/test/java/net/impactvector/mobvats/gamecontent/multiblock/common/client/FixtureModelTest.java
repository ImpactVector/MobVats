package net.impactvector.mobvats.gamecontent.multiblock.common.client;

import java.lang.reflect.Proxy;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public final class FixtureModelTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void verify() {
        var seed = new BakedQuad(new int[32], -1, Direction.NORTH, null, true);
        var points = new Vector3f[] {
            new Vector3f(.25f, .25f, .25f),
            new Vector3f(.25f, .75f, .25f),
            new Vector3f(.75f, .75f, .25f),
            new Vector3f(.75f, .25f, .25f)
        };
        var core = FixtureModel.withPoints(seed, points);
        int[] before = core.getVertices().clone();
        for (var face : Direction.values()) {
            var q = FixtureModel.orient(core, face);
            require(q.getDirection() == face, "Pump did not follow boundary facing: " + face);
            for (int v = 0; v < 4; v++) {
                var p = new Vector3f(
                        Float.intBitsToFloat(q.getVertices()[v * 8]),
                        Float.intBitsToFloat(q.getVertices()[v * 8 + 1]),
                        Float.intBitsToFloat(q.getVertices()[v * 8 + 2]));
                require(
                        Math.abs(p.get(face.getAxis().ordinal())
                                        - (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? .25f : .75f))
                                < 1e-6,
                        "Pump axis moved outside its plane");
                int normal = q.getVertices()[v * 8 + 7];
                require(
                        (byte) (normal >> (face.getAxis().ordinal() * 8))
                                == face.getAxisDirection().getStep() * 127,
                        "Packed normal incorrect");
            }
        }
        require(Arrays.equals(before, core.getVertices()), "Shared stationary geometry mutated");
        for (var attachment : Direction.values())
            for (var face : Direction.values()) {
                int mask = 1 << attachment.ordinal();
                require(
                        FixtureModel.coveredSurface("block/procedural/t1/fixture_grate", face, mask)
                                == (face == attachment),
                        "TAP obscured an unrelated grate surface");
                require(
                        !FixtureModel.coveredSurface("block/procedural/t1/fixture_metal", face, mask),
                        "TAP removed reinforced chamber");
                require(
                        !FixtureModel.coveredSurface("block/procedural/t1/fixture_hot", face, 0),
                        "Visible chamber lost hot interior");
            }
        var frame = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        var skin = new BakedQuad(new int[32], -1, Direction.NORTH, null, true);
        var casing = (BakedModel) Proxy.newProxyInstance(
                BakedModel.class.getClassLoader(), new Class<?>[] {BakedModel.class}, (p, m, a) -> {
                    if (m.getName().equals("getQuads")) return List.of(skin);
                    if (m.getName().equals("applyTransform")) return p;
                    throw new AssertionError(m.getName());
                });
        var model = new FixtureModel(casing, casing, List.of(core), List.of(core, frame), true, false);
        var random = RandomSource.create();
        for (String id : List.of("item_port", "fluid_port")) {
            var plate = FixtureModel.withPoints(seed, new Vector3f[] {
                new Vector3f(.125f, .125f, 0f),
                new Vector3f(.125f, .875f, 0f),
                new Vector3f(.875f, .875f, 0f),
                new Vector3f(.875f, .125f, 0f)
            });
            var port = new FixtureModel(casing, casing, List.of(plate), List.of(plate, frame), id)
                    .withCladding(net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.NONE, null);
            var opening = new java.util.concurrent.atomic.AtomicReference<Direction>();
            var cutout = (BakedModel) Proxy.newProxyInstance(
                    BakedModel.class.getClassLoader(), new Class<?>[] {BakedModel.class}, (p, m, args) -> {
                        if (m.getName().equals("getQuads")) {
                            opening.set(((net.neoforged.neoforge.client.model.data.ModelData) args[3])
                                    .get(
                                            net.impactvector.mobvats.gamecontent.multiblock.vat.client.model
                                                    .VatCasingModel.OPENING));
                            return List.of(skin);
                        }
                        throw new AssertionError(m.getName());
                    });
            var clad = new FixtureModel(casing, cutout, List.of(plate), List.of(plate, frame), id);
            for (Direction face : Direction.values()) {
                var quads = port.getQuads(
                        null,
                        null,
                        random,
                        FixtureModel.snapshotData(true, face, 0, new VatCasingState(1 << face.ordinal(), 0)),
                        null);
                require(quads.size() == 1, "Port duplicated faceplate or construction shell");
                require(
                        Arrays.equals(
                                quads.getFirst().getVertices(),
                                FixtureModel.orient(plate, face).getVertices()),
                        "Port faceplate does not follow exterior");
                for (int v = 0; v < 4; v++) {
                    float p = Float.intBitsToFloat(quads.getFirst()
                            .getVertices()[v * 8 + face.getAxis().ordinal()]);
                    require(
                            Math.abs(p - (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) < 1e-6,
                            "Port connector is not flush with exterior");
                }
                require(
                        port.getQuads(
                                        null,
                                        null,
                                        random,
                                        FixtureModel.snapshotData(false, Direction.NORTH, 0, VatCasingState.UNFORMED),
                                        null)
                                .equals(List.of(plate, frame)),
                        "Unformed port lost construction model");
                var shell = new VatCasingState(1 << face.ordinal(), 0);
                require(
                        clad.getQuads(null, null, random, FixtureModel.snapshotData(true, face, 0, shell), null)
                                                .size()
                                        == 2
                                && opening.get() == face,
                        "Clad port is missing its oriented shell cutout");
                for (var patch :
                        net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.build(shell, face))
                    require(
                            patch.normal() != face
                                    || patch.u1() <= 3
                                    || patch.u0() >= 13
                                    || patch.v1() <= 3
                                    || patch.v0() >= 13,
                            "Shell overlaps flush connector face");
            }
        }
        for (boolean fabulous : new boolean[] {false, true})
            require(
                    model.getRenderPasses(null, fabulous).equals(List.of(model)),
                    "Item render pass bypasses machinery mesh");
        require(
                model.applyTransform(
                                net.minecraft.world.item.ItemDisplayContext.GUI,
                                new com.mojang.blaze3d.vertex.PoseStack(),
                                false)
                        == model,
                "GUI transform discards machinery mesh");
        require(
                model.getQuads(null, null, random).equals(List.of(core, frame)),
                "Inventory lost or duplicated geometry");
        var sink = new FixtureModel(casing, casing, List.of(core), List.of(core, frame), false, true);
        for (var face : Direction.values())
            require(
                    sink.getQuads(
                                    null,
                                    null,
                                    random,
                                    FixtureModel.snapshotData(true, face, 0, VatCasingState.UNFORMED),
                                    null)
                            .equals(List.of(core)),
                    "Void geometry depends on placement facing");
        for (boolean assembled : new boolean[] {false, true, false, true, false}) {
            var shell = assembled ? new VatCasingState(1 << Direction.EAST.ordinal(), 0) : VatCasingState.UNFORMED;
            var data = FixtureModel.snapshotData(assembled, Direction.EAST, 0, shell);
            var quads = model.getQuads(null, null, random, data, null);
            require(quads.size() == 2, "Duplicate or missing fixture geometry across transition");
            require(quads.contains(skin) == assembled, "Formed casing replaced incorrectly");
            require(
                    model.getQuads(null, null, random, data, RenderType.translucent())
                            .isEmpty(),
                    "Extra translucent layer");
            require(model.getQuads(null, Direction.NORTH, random, data, null).isEmpty(), "Duplicate culled face");
            require(
                    model.getQuads(null, null, random, data, RenderType.solid()).size() == 2,
                    "Frame/shell switch lost geometry");
            require(
                    model.getQuads(null, null, random, data, RenderType.cutout())
                            .isEmpty(),
                    "Extra cutout layer");
            require(
                    model.getRenderTypes(null, random, data).asList().size() == 1,
                    "Fixture schedules more than one layer");
        }
        var interior = FixtureModel.snapshotData(true, Direction.NORTH, 0, VatCasingState.UNFORMED);
        require(
                model.getQuads(null, null, random, interior, null).size() == 1,
                "Interior fixture added a construction frame or shell");
        var bare = new FixtureModel(casing, casing, List.of(core), List.of(core, frame), false, false)
                .withCladding(net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.NONE, casing);
        var appliedData = FixtureModel.snapshotData(
                true, Direction.NORTH, 0, new VatCasingState(1 << Direction.NORTH.ordinal(), 0));
        require(
                bare.getQuads(null, null, random, appliedData, RenderType.solid())
                        .equals(List.of(core)),
                "Bare functional fixture added a shell or construction frame");
        for (var id : FixtureModel.INSTALLED) {
            var panel = FixtureModel.withPoints(core, new Vector3f[] {
                new Vector3f(.25f, .25f, 1f / 16),
                new Vector3f(.25f, .75f, 1f / 16),
                new Vector3f(.75f, .75f, 1f / 16),
                new Vector3f(.75f, .25f, 1f / 16)
            });
            var installedCore = id.endsWith("_controller") ? panel : core;
            var installed = new FixtureModel(casing, casing, List.of(installedCore), List.of(installedCore), id);
            for (boolean formed : new boolean[] {false, true, false, true})
                for (var face : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                    var data = FixtureModel.snapshotData(
                            formed,
                            face,
                            0,
                            formed ? new VatCasingState(1 << face.ordinal(), 0) : VatCasingState.UNFORMED);
                    var quads = installed.getQuads(null, null, random, data, RenderType.solid());
                    require(
                            quads.size() == 1 && !quads.contains(skin),
                            "Installed machinery covered or duplicated: " + id);
                    boolean oriented = id.endsWith("_controller") || id.equals("blender") || id.equals("tool_port");
                    var expected = oriented ? FixtureModel.orient(installedCore, face) : installedCore;
                    require(
                            Arrays.equals(quads.getFirst().getVertices(), expected.getVertices()),
                            "Controller orientation or fixed floor/ceiling orientation changed: " + id);
                    require(
                            installed
                                    .getQuads(null, null, random, data, RenderType.translucent())
                                    .isEmpty(),
                            "Duplicated installed layer");
                }
        }
        for (var id : List.of("feed_port", "tool_port")) {
            var installed = new FixtureModel(casing, casing, List.of(core), List.of(core), id);
            for (var face : Direction.values()) {
                var data = FixtureModel.snapshotData(true, face, 0, VatCasingState.UNFORMED);
                var quads = installed.getQuads(null, null, random, data, RenderType.solid());
                require(quads.size() == 1, "Interior actor duplicated mechanism");
                var expected =
                        id.equals("feed_port") ? core : FixtureModel.orient(FixtureModel.interiorMount(core), face);
                require(
                        Arrays.equals(quads.getFirst().getVertices(), expected.getVertices()),
                        "Interior actor ignored center anchor or facing");
            }
        }
        var inlet = FixtureModel.withPoints(seed, new Vector3f[] {
            new Vector3f(1, .4f, .4f), new Vector3f(1, .6f, .4f), new Vector3f(1, .6f, .6f), new Vector3f(1, .4f, .6f)
        });
        var feeder = new FixtureModel(casing, casing, List.of(core), List.of(core, inlet), "feed_port");
        for (var face : Direction.values()) {
            var data = FixtureModel.snapshotData(false, Direction.NORTH, 0, VatCasingState.UNFORMED, face);
            var quads = feeder.getQuads(null, null, random, data, RenderType.solid());
            require(
                    quads.size() == 2
                            && Arrays.equals(
                                    quads.getFirst().getVertices(),
                                    net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.FeederArt
                                            .orientInlet(core, face)
                                            .getVertices()),
                    "Reel and spindle do not share inlet orientation");
            require(
                    quads.getLast().getDirection() == face,
                    "Construction inlet did not mate to associated conduit side");
            require(
                    quads.getLast()
                            == feeder.getQuads(null, null, random, data, RenderType.solid())
                                    .getLast(),
                    "Feeder inlet rebaked without model-data change");
            var formed = FixtureModel.snapshotData(
                    true, Direction.NORTH, 0, new VatCasingState(1 << Direction.NORTH.ordinal(), 0), face);
            require(
                    feeder.getQuads(null, null, random, formed, RenderType.solid())
                                    .size()
                            == 1,
                    "Formed inlet duplicates controller-owned service bridge");
        }
        controllerHousing(seed, casing, random);
        exterior(seed, casing, random);
        generatedSleeves();
        System.out.println(
                "PASS: fixture six-axis orientation, immutable central auger, flush Pump sleeve/cutout, preserved border, formed/unformed/interior ownership and layer separation.");
    }

    private static void controllerHousing(BakedQuad seed, BakedModel casing, RandomSource random) {
        // Use the generated housing dimensions and all six faces, including its front cap at z=3.
        for (var id : List.of("vat_controller", "mixer_controller"))
            try (var input = FixtureModelTest.class.getResourceAsStream(
                    "/assets/mobvats/models/block/procedural/t1/" + id + ".json")) {
                var elements = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                                Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject()
                        .getAsJsonArray("elements");
                var vertices = new ArrayList<Vector3f[]>();
                for (var element : elements) {
                    var e = element.getAsJsonObject();
                    var f = e.getAsJsonArray("from");
                    var t = e.getAsJsonArray("to");
                    float x = f.get(0).getAsFloat() / 16,
                            y = f.get(1).getAsFloat() / 16,
                            z = f.get(2).getAsFloat() / 16;
                    float X = t.get(0).getAsFloat() / 16,
                            Y = t.get(1).getAsFloat() / 16,
                            Z = t.get(2).getAsFloat() / 16;
                    var p = new Vector3f[] {
                        new Vector3f(x, y, z),
                        new Vector3f(X, y, z),
                        new Vector3f(X, Y, z),
                        new Vector3f(x, Y, z),
                        new Vector3f(x, y, Z),
                        new Vector3f(X, y, Z),
                        new Vector3f(X, Y, Z),
                        new Vector3f(x, Y, Z)
                    };
                    var indices = Map.of(
                            "down",
                            new int[] {0, 1, 5, 4},
                            "up",
                            new int[] {3, 7, 6, 2},
                            "north",
                            new int[] {0, 3, 2, 1},
                            "south",
                            new int[] {4, 5, 6, 7},
                            "west",
                            new int[] {0, 4, 7, 3},
                            "east",
                            new int[] {1, 2, 6, 5});
                    for (var face : e.getAsJsonObject("faces").keySet())
                        vertices.add(Arrays.stream(indices.get(face))
                                .mapToObj(i -> p[i])
                                .toArray(Vector3f[]::new));
                }
                var all = vertices.stream()
                        .map(p -> FixtureModel.withPoints(seed, p))
                        .toList();
                var panel = all.stream()
                        .filter(q -> !FixtureModel.controllerRear(q))
                        .toList();
                require(all.size() - panel.size() == 6, "Controller rear cuboid or cap was not removed: " + id);
                require(
                        panel.stream().anyMatch(q -> FixtureModel.onPlane(q, Direction.SOUTH, 3f / 16)),
                        "Panel lost its inward-facing closure");
                var model = new FixtureModel(casing, casing, all, all, id);
                require(model.getQuads(null, null, random).equals(all), "Inventory lost full Controller housing");
                var world = (net.minecraft.world.level.BlockAndTintGetter) Proxy.newProxyInstance(
                        FixtureModelTest.class.getClassLoader(),
                        new Class<?>[] {net.minecraft.world.level.BlockAndTintGetter.class},
                        (p, m, a) -> {
                            if (m.getName().equals("getBlockEntity")) return null;
                            throw new AssertionError(m.getName());
                        });
                for (var face : Direction.Plane.HORIZONTAL) {
                    var state = net.minecraft.world.level.block.Blocks.FURNACE
                            .defaultBlockState()
                            .setValue(
                                    net.minecraft.world.level.block.state.properties.BlockStateProperties
                                            .HORIZONTAL_FACING,
                                    face);
                    var data = model.getModelData(
                            world,
                            net.minecraft.core.BlockPos.ZERO,
                            state,
                            net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                    var actual = model.getQuads(state, null, random, data, RenderType.solid());
                    require(actual.size() == all.size(), "Unformed Controller lost housing");
                    for (int i = 0; i < actual.size(); i++)
                        require(
                                Arrays.equals(
                                        actual.get(i).getVertices(),
                                        FixtureModel.orient(all.get(i), face).getVertices()),
                                "Unformed Controller model ignored placement facing: " + face);
                }
                for (var face : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST))
                    for (boolean formed : new boolean[] {false, true, false, true}) {
                        var data = FixtureModel.snapshotData(
                                formed,
                                face,
                                0,
                                formed ? new VatCasingState(1 << face.ordinal(), 0) : VatCasingState.UNFORMED);
                        var actual = model.getQuads(null, null, random, data, RenderType.solid());
                        var expected = formed ? panel : all;
                        require(
                                actual.size() == expected.size(),
                                "Controller housing transition lost/duplicated geometry");
                        for (int i = 0; i < actual.size(); i++)
                            require(
                                    Arrays.equals(
                                            actual.get(i).getVertices(),
                                            FixtureModel.orient(expected.get(i), face)
                                                    .getVertices()),
                                    "Rear clipping did not follow Controller wall orientation");
                    }
            } catch (java.io.IOException ex) {
                throw new AssertionError(ex);
            }
        System.out.println(
                "PASS: both Controllers retain complete inventory/construction housing and only a closed 3px panel while formed on all four walls.");
    }

    private static void exterior(BakedQuad seed, BakedModel casing, RandomSource random) {
        var flange = FixtureModel.withPoints(seed, new Vector3f[] {
            new Vector3f(3f / 16, 3f / 16, 3f / 16),
            new Vector3f(3f / 16, 6f / 16, 3f / 16),
            new Vector3f(13f / 16, 6f / 16, 3f / 16),
            new Vector3f(13f / 16, 3f / 16, 3f / 16)
        });
        var front = FixtureModel.withPoints(flange, new Vector3f[] {
            new Vector3f(3f / 16, 3f / 16, 0),
            new Vector3f(3f / 16, 6f / 16, 0),
            new Vector3f(13f / 16, 6f / 16, 0),
            new Vector3f(13f / 16, 3f / 16, 0)
        });
        var auger = FixtureModel.withPoints(seed, new Vector3f[] {
            new Vector3f(.4f, .4f, .3f),
            new Vector3f(.4f, .6f, .3f),
            new Vector3f(.6f, .6f, .3f),
            new Vector3f(.6f, .4f, .3f)
        });
        var model = new FixtureModel(
                casing, casing, List.of(flange, auger), List.of(flange, auger), true, false, List.of(front));
        for (var facing : Direction.values()) {
            var shell = new VatCasingState(1 << facing.ordinal(), 0);
            var formed = FixtureModel.snapshotData(true, facing, 0, shell);
            float plane = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : 1;
            for (int restore = 0; restore < 3; restore++) {
                var quads = model.getQuads(null, null, random, formed, RenderType.solid());
                require(quads.size() == 3, "Sleeve or central screw duplicated/lost");
                require(
                        FixtureModel.onPlane(quads.get(1), facing, plane),
                        "Exterior face not flush with shell: " + facing);
                require(
                        Arrays.equals(
                                quads.getFirst().getVertices(),
                                FixtureModel.orient(auger, facing).getVertices()),
                        "Central auger relocated");
                require(
                        model.getQuads(
                                        null,
                                        null,
                                        random,
                                        FixtureModel.snapshotData(false, Direction.NORTH, 0, VatCasingState.UNFORMED),
                                        null)
                                .equals(List.of(flange, auger)),
                        "Disassembly changed construction artwork");
            }
            var patches = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.build(shell, facing);
            int area = 0;
            for (var p : patches)
                if (p.normal() == facing) {
                    require(p.plane() == plane * 16, "Shell outer plane changed");
                    require(
                            p.u1() <= 3 || p.u0() >= 13 || p.v1() <= 3 || p.v0() >= 13,
                            "Cladding obscures Pump opening");
                    area += (p.u1() - p.u0()) * (p.v1() - p.v0());
                }
            require(area == 156, "Surrounding cladding border lost or overlapping");
            require(
                    patches.stream()
                            .noneMatch(p ->
                                    p.normal().getAxis() != facing.getAxis() && (p.plane() == 3 || p.plane() == 13)),
                    "Shell reveal overlaps sleeve");
            require(
                    net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.build(shell).stream()
                                    .filter(p -> p.normal() == facing)
                                    .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                                    .sum()
                            == 256,
                    "Ordinary cladding changed");
        }
        require(
                model.getQuads(
                                null,
                                null,
                                random,
                                FixtureModel.snapshotData(true, Direction.NORTH, 0, VatCasingState.UNFORMED),
                                null)
                        .equals(List.of(flange, auger)),
                "Interior pump gained exterior extension");
    }

    private static void generatedSleeves() {
        // Compare the generator's sleeve endpoints to the actual assembled shell surfaces.
        for (String id : List.of("pull_pump", "push_pump"))
            try (var input = FixtureModelTest.class.getResourceAsStream(
                    "/assets/mobvats/models/block/procedural/t1/" + id + "_sleeve.json")) {
                require(input != null, "Generated sleeve missing: " + id);
                var model = com.google.gson.JsonParser.parseReader(
                                new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var elements = model.getAsJsonArray("elements");
                require(
                        model.getAsJsonObject("textures")
                                .get("identity")
                                .getAsString()
                                .endsWith("fixture_" + id),
                        "Exterior Pull/Push identity exchanged");
                var state = new VatCasingState(1 << Direction.NORTH.ordinal(), 0);
                int outer = net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.build(state).stream()
                        .filter(p -> p.normal() == Direction.NORTH)
                        .findFirst()
                        .orElseThrow()
                        .plane();
                var coreInput = FixtureModelTest.class.getResourceAsStream(
                        "/assets/mobvats/models/block/procedural/t1/" + id + "_core.json");
                try (var reader = new java.io.InputStreamReader(
                        java.util.Objects.requireNonNull(coreInput), java.nio.charset.StandardCharsets.UTF_8)) {
                    var body = com.google.gson.JsonParser.parseReader(reader)
                            .getAsJsonObject()
                            .getAsJsonArray("elements")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("from")
                            .get(2)
                            .getAsFloat();
                    for (var value : elements) {
                        var e = value.getAsJsonObject();
                        var from = e.getAsJsonArray("from");
                        var to = e.getAsJsonArray("to");
                        require(from.get(2).getAsFloat() == outer, "Sleeve does not reach actual shell plane");
                        require(
                                to.get(2).getAsFloat() == outer || to.get(2).getAsFloat() == body,
                                "Sleeve gap at central housing");
                        require(
                                from.get(0).getAsFloat() >= 3
                                        && to.get(0).getAsFloat() <= 13
                                        && from.get(1).getAsFloat() >= 3
                                        && to.get(1).getAsFloat() <= 13,
                                "Sleeve intersects surrounding cladding");
                        require(
                                to.get(0).getAsFloat() <= 6
                                        || from.get(0).getAsFloat() >= 10
                                        || to.get(1).getAsFloat() <= 6
                                        || from.get(1).getAsFloat() >= 10,
                                "Sleeve caps the screw aperture");
                    }
                }
                require(elements.size() == 12, "Duplicate or missing sleeve surfaces");
            } catch (java.io.IOException ex) {
                throw new AssertionError(ex);
            }
        try (var input = FixtureModelTest.class.getResourceAsStream("/assets/mobvats/fixture_mesh.json")) {
            var mesh = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                            java.util.Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("auger");
            boolean visible = false;
            for (var quad : mesh) {
                float x = 0, y = 0;
                for (int i = 0; i < 3; i++) {
                    var point = quad.getAsJsonArray().get(i).getAsJsonArray();
                    x += point.get(0).getAsFloat() / 3;
                    y += point.get(1).getAsFloat() / 3;
                }
                visible |= x > 6 && x < 10 && y > 6 && y < 10;
            }
            require(visible, "Auger has no visible surface through exterior aperture");
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }
}
