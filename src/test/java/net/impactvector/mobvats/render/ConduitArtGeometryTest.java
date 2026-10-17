package net.impactvector.mobvats.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitPreviewModel;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMechanismRenderer;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** CPU geometry contracts and preview examples baked by the authoritative formed geometry code. */
public final class ConduitArtGeometryTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ToolArmPoseTest.verify();
        net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitShellOwnershipTest.verify();
        net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModelTest.verify();
        var descriptor =
                JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        require(
                descriptor.get("schemaVersion").getAsInt() == 1
                        && descriptor.get("units").getAsString().equals("model_pixels")
                        && descriptor.get("modelPixelsPerBlock").getAsInt() == 16,
                "Invalid build geometry units/version");
        var config = descriptor.getAsJsonObject("geometry");
        float width = config.get("nodeWidth").getAsFloat() / 16;
        float ratio = config.get("tapEndpointRatio").getAsFloat();
        require(
                config.get("bufferWidth").getAsInt() == FluidBufferArt.BODY.getXsize() * 16,
                "Generated buffer differs from runtime reservoir");
        require(width == ConduitGeometry.DIAMETER, "Preview exceeds physical pipe envelope");
        for (int axis = 0; axis < 3; axis++)
            for (boolean positive : new boolean[] {false, true}) {
                int tangent = (axis + 1) % 3, other = (axis + 2) % 3;
                float lo = .5f - width / 2, hi = .5f + width / 2;
                float[][] points = new float[4][3];
                for (int v = 0; v < 4; v++) {
                    points[v][axis] = (v < 2) ? (positive ? hi : lo) : (positive ? 1 : 0);
                    points[v][tangent] = (v == 0 || v == 3) ? lo : hi;
                    points[v][other] = hi;
                }
                int[] input = new int[32];
                for (int v = 0; v < 4; v++)
                    for (int a = 0; a < 3; a++) input[v * 8 + a] = Float.floatToRawIntBits(points[v][a]);
                int[] original = input.clone(), tapered = ConduitPreviewModel.taperVertices(input, width, ratio);
                require(Arrays.equals(input, original), "Taper mutated shared TRUNK geometry");
                for (int v = 0; v < 4; v++) {
                    require(tapered[v * 8 + axis] == input[v * 8 + axis], "Taper changed branch length");
                    float expected = width / 2 * (v < 2 ? 1 : ratio);
                    for (int a = 0; a < 3; a++)
                        if (a != axis)
                            require(
                                    Math.abs(Math.abs(Float.intBitsToFloat(tapered[v * 8 + a]) - .5f) - expected)
                                            < 1e-6,
                                    "TAP does not taper on all axes");
                }
            }
        var examples = new LinkedHashMap<String, Object>();
        // Export actual formed casing surfaces; preview must not invent shell depth.
        var pumpShell = new net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState(
                1 << Direction.NORTH.ordinal(), 0);
        examples.put(
                "pump_casing",
                net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.build(
                        pumpShell, Direction.NORTH));
        examples.put("pump_shell_depth", net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingGeometry.DEPTH);
        var min = BlockPos.ZERO;
        var max = new BlockPos(4, 4, 4);
        for (var resource : ConduitResource.values())
            for (var cladding : ConduitCladding.values())
                for (var role : ConduitMode.values())
                    for (String layout : List.of("straight", "elbow", "t_junction", "tap_endpoint")) {
                        if (role == ConduitMode.TRUNK && layout.equals("tap_endpoint")) continue;
                        var center = layout.equals("tap_endpoint")
                                ? new BlockPos(1, 2, 2)
                                : new BlockPos(2, 2, cladding == ConduitCladding.BARE ? 2 : 0);
                        var directions = switch (layout) {
                            case "straight" -> List.of(Direction.WEST, Direction.EAST);
                            case "elbow" -> List.of(Direction.EAST, Direction.UP);
                            case "t_junction" -> List.of(Direction.WEST, Direction.EAST, Direction.UP);
                            default -> List.of(Direction.EAST);
                        };
                        var cells = new ArrayList<ConduitGeometry.Cell>();
                        int mask = 0;
                        for (var direction : directions) {
                            mask |= 1 << direction.ordinal();
                            cells.add(new ConduitGeometry.Cell(
                                    center.relative(direction),
                                    resource,
                                    cladding,
                                    ConduitMode.TRUNK,
                                    1 << direction.getOpposite().ordinal(),
                                    List.of()));
                        }
                        var attachments = layout.equals("tap_endpoint")
                                ? List.of(new ConduitGeometry.Attachment(
                                        new BlockPos(0, 2, 2), EndpointGeometry.Kind.PASSIVE))
                                : List.<ConduitGeometry.Attachment>of();
                        cells.add(new ConduitGeometry.Cell(center, resource, cladding, role, mask, attachments));
                        var snapshot = new ConduitGeometry.Snapshot(min, max, cells);
                        require(
                                ConduitGeometry.Snapshot.load(snapshot.save()).equals(snapshot),
                                "Restored snapshot changed connectivity");
                        var geometry = ConduitGeometry.bake(snapshot);
                        require(
                                !layout.equals("tap_endpoint")
                                        || !geometry.endpointFittings().isEmpty(),
                                "TAP endpoint fitting missing");
                        var volumes = new ArrayList<Object>();
                        var block = new AABB(center);
                        for (var volume : geometry.occupied())
                            if (volume.bounds().intersects(block)) {
                                var b = volume.bounds()
                                        .intersect(block)
                                        .move(-center.getX(), -center.getY(), -center.getZ());
                                volumes.add(Map.of(
                                        "from",
                                        List.of(b.minX * 16, b.minY * 16, b.minZ * 16),
                                        "to",
                                        List.of(b.maxX * 16, b.maxY * 16, b.maxZ * 16),
                                        "material",
                                        volume.material() == ConduitGeometry.Material.COLLAR ? "frame" : "pipe"));
                            }
                        examples.put(
                                resource.name().toLowerCase(Locale.ROOT) + "_"
                                        + cladding.name().toLowerCase(Locale.ROOT) + "_"
                                        + role.name().toLowerCase(Locale.ROOT) + "_" + layout,
                                volumes);
                    }
        var harness = new BlockPos(1, 3, 1);
        var bounds = new AABB(1.05, 1, 1.05, 1.95, 2.4, 1.95);
        var definition = net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition.DEFAULT;
        var feedPort = new BlockPos(1, 1, 0);
        var endpoint = EndpointGeometry.from(
                feedPort, BlockPos.ZERO, new BlockPos(2, 3, 2), new BlockPos(1, 1, 1), EndpointGeometry.Kind.FEED);
        var feedBase = endpoint.interactionOrigin().orElseThrow();
        require(Math.abs(feedBase.z - (3.0 / 16 + .01)) < 1e-9, "Feeder anchor moved");
        var feedLink = new net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation.Link(
                feedPort, feedBase, definition.headAnchor(bounds, Direction.NORTH), Direction.SOUTH, true);
        require(
                net.impactvector.mobvats.gamecontent.multiblock.vat.VatInteractionGeometry.feedCurve(feedLink)
                                .points()
                                .size()
                        == 13,
                "Physical feeding route changed");
        var viseRecipe = JsonParser.parseString(
                        Files.readString(Path.of(args[2]).resolve("machinery.json")))
                .getAsJsonObject()
                .getAsJsonObject("vise");
        require(
                viseRecipe.get("width").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.WIDTH
                        && viseRecipe.get("height").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.HEIGHT
                        && viseRecipe.get("depth").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.DEPTH
                        && viseRecipe.get("jawGap").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .JAW_GAP,
                "Vise recipe differs from runtime dimensions");
        require(
                viseRecipe.get("gripX").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.GRIP_X
                        && viseRecipe.get("gripY").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.GRIP_Y,
                "Preview grip differs from runtime");
        require(
                viseRecipe.get("bucketGripX").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .BUCKET_GRIP_X
                        && viseRecipe.get("bucketGripY").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .BUCKET_GRIP_Y,
                "Bucket rim preview differs from runtime");
        require(
                viseRecipe.get("swordGripX").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .SWORD_GRIP_X
                        && viseRecipe.get("swordGripY").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .SWORD_GRIP_Y
                        && viseRecipe.get("reverseGripX").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .REVERSE_GRIP_X
                        && viseRecipe.get("reverseGripY").getAsDouble()
                                == net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt
                                        .REVERSE_GRIP_Y,
                "Special mount preview differs from runtime");
        var mount = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.center(
                new org.joml.Vector3f(.55f), .28, false, false);
        examples.put(
                "empty_vise_center",
                List.of((feedBase.x + mount.x) * 16, (feedBase.y + mount.y) * 16, (feedBase.z + mount.z) * 16));
        for (boolean sword : new boolean[] {false, true}) {
            float scale = sword ? .85f : .55f;
            var center = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.center(
                    new org.joml.Vector3f(scale), .28, sword, false);
            double itemZ = .28 + (sword ? scale * 5.0 / 16 : scale / 2);
            require(
                    Math.abs((center.z - itemZ) / scale - (sword ? -5.0 / 16 : -3.0 / 16)) < 1e-7,
                    "Vise does not grip mounting end");
            if (sword)
                require(
                        center.distanceTo(new net.minecraft.world.phys.Vec3(0, 0, .28)) < 1e-7,
                        "Sword hilt must remain pinned to original arm tip");
            require(
                    Math.abs(net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.center(
                                                    new org.joml.Vector3f(scale), 1, sword, false)
                                            .z
                                    - center.z
                                    - .72)
                            < 1e-7,
                    "Vise diverged from arm extension");
        }
        var immutableScale = new org.joml.Vector3f(.55f, .55f, .55f);
        var rear = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.center(
                immutableScale, .28, false, true);
        require(
                Math.abs(rear.y) < 1e-9 && Math.abs(rear.z - .28) < 1e-7,
                "Reversed syringe grips needle instead of rear barrel");
        require(immutableScale.equals(new org.joml.Vector3f(.55f)), "Vise mutated item-render scale");
        var bucket = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.center(
                immutableScale, .28, false, false, true);
        require(
                bucket.z == mount.z && bucket.y > 0 && mount.y < 0,
                "Bucket rim and shears handles require opposite vertical mounting regions");
        require(
                mount.equals(net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.center(
                        immutableScale, .28, false, false)),
                "Tool replacement retained a previous grip");
        var restored = net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation.load(
                new net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation(List.of(feedLink), List.of())
                        .save());
        require(restored.tools().getFirst().equals(feedLink), "Decoration changed restored assignment/target");
        var armQuads = new ArrayList<Object>();
        int rodIndex = 0;
        for (var curve : List.of(
                net.impactvector.mobvats.gamecontent.mob.CableCurve.between(feedBase, feedBase.add(0, 0, .14), 0, 2),
                net.impactvector.mobvats.gamecontent.mob.CableCurve.between(
                        feedBase.add(0, 0, .08), feedBase.add(0, 0, .28 * .65 + .08), 0, 2),
                net.impactvector.mobvats.gamecontent.mob.CableCurve.between(
                        feedBase.add(0, 0, .14), feedBase.add(0, 0, .28), 0, 2),
                net.impactvector.mobvats.gamecontent.mob.CableCurve.between(
                        feedBase.add(0, 0, .28), feedBase.add(mount).add(0, 0, -3.5 / 16), 0, 2))) {
            double rodWidth = rodIndex++ == 0 ? .15 : rodIndex == 2 ? .11 : .07;
            var mesh = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.CableRenderer.bake(
                    curve, rodWidth);
            for (var q : mesh.quads())
                armQuads.add(List.of(q.a(), q.b(), q.c(), q.d()).stream()
                        .map(v -> List.of(v.x * 16, v.y * 16, v.z * 16))
                        .toList());
        }
        examples.put("tool_arm", armQuads);
        var curves =
                net.impactvector.mobvats.gamecontent.multiblock.vat.HarnessStraps.curves(harness, bounds, definition);
        require(curves.size() == 4, "Preview duplicated existing suspension straps");
        var strapQuads = new ArrayList<Object>();
        for (var curve : curves) {
            require(curve.points().getFirst().y == 3 + 13.0 / 16, "Harness anchor plane changed");
            var mesh = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.CableRenderer.bake(
                    curve, net.impactvector.mobvats.gamecontent.multiblock.vat.HarnessStraps.THICKNESS);
            for (var q : mesh.quads())
                strapQuads.add(List.of(q.a(), q.b(), q.c(), q.d()).stream()
                        .map(v -> List.of(v.x * 16, v.y * 16, v.z * 16))
                        .toList());
        }
        // Submit the actual neutral adult CowModel into a recording consumer; no client launch.
        var specimenVertices = new ArrayList<List<Float>>();
        var consumer = new com.mojang.blaze3d.vertex.VertexConsumer() {
            public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
                specimenVertices.add(List.of(x * 16, y * 16, z * 16));
                return this;
            }

            public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
                return this;
            }

            public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
                return this;
            }

            public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
                return this;
            }

            public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        };
        var cow = new net.minecraft.client.model.CowModel<net.minecraft.world.entity.animal.Cow>(
                net.minecraft.client.model.CowModel.createBodyLayer().bakeRoot());
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.translate(1.5, 2.501, 1.5);
        pose.scale(1, -1, -1);
        cow.renderToBuffer(pose, consumer, 15728880, 0, -1);
        require(
                !specimenVertices.isEmpty() && specimenVertices.size() % 4 == 0,
                "Preview lost production specimen geometry");
        var specimenQuads = new ArrayList<Object>();
        for (int i = 0; i < specimenVertices.size(); i += 4)
            specimenQuads.add(List.copyOf(specimenVertices.subList(i, i + 4)));
        examples.put("specimen_quads", specimenQuads);
        examples.put("harness_straps", strapQuads);
        examples.put(
                "specimen_attachment_bounds",
                Map.of(
                        "from",
                        List.of(bounds.minX * 16, bounds.minY * 16, bounds.minZ * 16),
                        "to",
                        List.of(bounds.maxX * 16, bounds.maxY * 16, bounds.maxZ * 16)));
        FeederArtGeometryTest.verify(examples);
        FluidBufferArtGeometryTest.verify(examples);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(examples) + "\n");
        System.out.println(
                "PASS: six-axis frusta, 6-to-3 pixel taper, unchanged inputs, authoritative formed examples and snapshot restoration.");
    }
    /** CPU checks for the actual baked arm frame and its animated aim transitions. */
    private static final class ToolArmPoseTest {
        static void verify() {
            for (int degrees = 0; degrees < 360; degrees += 15) {
                double yaw = Math.toRadians(degrees);
                for (double slope : new double[] {-.5, 0, .5}) {
                    var travel = new Vec3(Math.sin(yaw), slope, Math.cos(yaw));
                    var link = new VatPresentation.Link(BlockPos.ZERO, Vec3.ZERO, travel, Direction.SOUTH, true);
                    var arm = VatMechanismRenderer.bake(new VatPresentation(List.of(link), List.of()))
                            .arms()
                            .getFirst();
                    assertFrame(arm.rotation(), travel);
                    var swordTip = arm.rotation().transform(new Vector3f(0, 1, 1));
                    require(swordTip.y > 0, "Sword blade points down at yaw " + degrees + ", slope " + slope);
                    var previous = VatMechanismRenderer.aimRotation(new Vec3(-travel.x, slope, -travel.z));
                    for (float progress : new float[] {0, .25f, .5f, .75f, 1}) {
                        var animated = new Quaternionf(previous).slerp(arm.rotation(), progress);
                        require(
                                animated.transform(new Vector3f(0, 1, 0)).y > 0,
                                "Aim transition inverted the vise/tool");
                    }
                }
            }
            for (var direction : Direction.values()) {
                var target = Vec3.atLowerCornerOf(direction.getNormal());
                assertFrame(VatMechanismRenderer.aimRotation(target), target);
                var link = new VatPresentation.Link(BlockPos.ZERO, Vec3.ZERO, Vec3.ZERO, direction, false);
                assertFrame(
                        VatMechanismRenderer.bake(new VatPresentation(List.of(link), List.of()))
                                .arms()
                                .getFirst()
                                .rotation(),
                        target);
            }
            assertFrame(VatMechanismRenderer.aimRotation(Vec3.ZERO), new Vec3(0, 0, 1));
            System.out.println(
                    "PASS: baked tool arms preserve upright tools through every wall facing, slope and aim transition.");
        }

        private static void assertFrame(Quaternionf rotation, Vec3 travel) {
            var forward = rotation.transform(new Vector3f(0, 0, 1));
            require(forward.distance(travel.normalize().toVector3f()) < 1e-5, "Arm no longer aims at its target");
            var right = rotation.transform(new Vector3f(1, 0, 0));
            require(Math.abs(right.y) < 1e-5, "Arm acquired roll around its aim direction");
            var up = rotation.transform(new Vector3f(0, 1, 0));
            require(up.y >= -1e-5, "Tool mount is upside down");
        }

        private static void require(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }

        private ToolArmPoseTest() {}
    }
}
