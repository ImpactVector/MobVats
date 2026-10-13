package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerRenderingGameTests {
    @GameTest(template = "empty", timeoutTicks = 10)
    public static void sixPixelShaftCenteredRotationsAndRotorContainment(GameTestHelper h) {
        h.assertTrue(
                MixerVesselGeometry.SHAFT_WIDTH_PIXELS == 6
                        && MixerVesselGeometry.SHAFT_WIDTH == .375
                        && MixerVesselGeometry.SHAFT_HALF_WIDTH == .1875,
                "Shaft is not exactly six pixels");
        var block = MixerContent.block(MixerPartType.Shaft);
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, block);
        var expected = new AABB(5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 1, 11 / 16.0);
        for (var rotation : net.minecraft.world.level.block.Rotation.values())
            for (var mirror : net.minecraft.world.level.block.Mirror.values()) {
                var state = block.defaultBlockState().rotate(rotation).mirror(mirror);
                h.assertTrue(
                        state.getShape(h.getLevel(), h.absolutePos(pos))
                                        .bounds()
                                        .equals(expected)
                                && state.getCollisionShape(h.getLevel(), h.absolutePos(pos))
                                        .bounds()
                                        .equals(expected),
                        "Oriented shaft shape is off-axis or wrong width");
            }
        try (var in = MixerRenderingGameTests.class.getResourceAsStream(
                "/assets/mobvats/models/block/procedural/t1/mixer_shaft.json")) {
            var model = com.google.gson.JsonParser.parseString(new String(
                            java.util.Objects.requireNonNull(in).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
            var from = element.getAsJsonArray("from");
            var to = element.getAsJsonArray("to");
            h.assertTrue(
                    from.get(0).getAsDouble() == 5
                            && from.get(1).getAsDouble() == 0
                            && from.get(2).getAsDouble() == 5
                            && to.get(0).getAsDouble() == 11
                            && to.get(1).getAsDouble() == 16
                            && to.get(2).getAsDouble() == 11,
                    "Construction/inventory shaft model differs from physical shape");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (int diameter : List.of(3, 5, 7, 9))
            for (int reduction = 0;
                    MixerVesselGeometry.reduced(diameter, reduction).isPresent();
                    reduction++) {
                var a = new MixerAssembly(
                        BlockPos.ZERO, 8, diameter, reduction, MixerVesselGeometry.SHELL_DEPTH_PIXELS);
                var d = MixerRenderDescriptor.from(a);
                double half = MixerVesselGeometry.SHAFT_HALF_WIDTH;
                h.assertTrue(
                        d.shaftTop().x == 0
                                && d.shaftTop().z == 0
                                && d.shaftBottom().x == 0
                                && d.shaftBottom().z == 0
                                && d.shaftBottom().y == a.vesselBottom() + MixerRenderDescriptor.WALL_THICKNESS
                                && d.armY() > d.shaftBottom().y
                                && d.armY() < d.shaftTop().y
                                && d.armReach() > half,
                        "Shaft/rotor attachment alignment invalid");
                for (int angle = 0; angle < 360; angle++)
                    for (int sx : List.of(-1, 1))
                        for (int sz : List.of(-1, 1)) {
                            double theta = Math.toRadians(angle), x = sx * half, z = sz * half;
                            double scale = a.rotorRadius() / (a.rotorRadius() - MixerRenderDescriptor.WALL_THICKNESS);
                            h.assertTrue(
                                    a.vessel()
                                            .contains(
                                                    (x * Math.cos(theta) - z * Math.sin(theta)) * scale,
                                                    (x * Math.sin(theta) + z * Math.cos(theta)) * scale),
                                    "Rotating six-pixel shaft leaves canonical vessel interior");
                        }
            }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void liquidAlphaAndSurfaceLighting(GameTestHelper h) {
        h.assertTrue(
                MixerLiquidPresentation.vertexAlpha(0xff477f9d) == 255
                        && MixerLiquidPresentation.vertexAlpha(0x80477f9d) == 128,
                "Liquid alpha capped opaque fluids or discarded authored transparency");
        h.assertTrue(
                MixerLiquidPresentation.liquidLight(0x00200040, 0x00a00020, 0) == 0x00a00040,
                "Surface lighting did not retain independent brighter block/sky samples");
        h.assertTrue(
                MixerLiquidPresentation.liquidLight(0, 0, 15) == 240
                        && MixerLiquidPresentation.liquidLight(0, 0, 0) == 0,
                "Emitting liquid lost light or nonemitting liquid invented light");
        h.succeed();
    }

    private static final BlockPos O = new BlockPos(1, 1, 1), C = O.offset(0, 1, 2);

    private static BlockPos rig(GameTestHelper h, int length) {
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++)
                    h.setBlock(
                            O.offset(x, y, z),
                            x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4
                                    ? MixerContent.state(MixerPartType.Casing)
                                    : Blocks.AIR.defaultBlockState());
        h.setBlock(C, MixerContent.state(MixerPartType.Controller));
        var top = O.offset(2, 4, 2);
        h.setBlock(top, MixerContent.state(MixerPartType.Impeller));
        for (int i = 1; i <= length; i++) h.setBlock(top.below(i), MixerContent.state(MixerPartType.Shaft));
        return top;
    }

    private static MultiblockMixer controller(GameTestHelper h) {
        return ((MixerControllerEntity) h.getBlockEntity(C))
                .getMultiblockController()
                .orElseThrow();
    }

    private static MixerRenderDescriptor descriptor(GameTestHelper h) {
        var c = controller(h);
        h.assertTrue(c.isAssembled(), "Mixer fixture did not form: " + c.getLastError());
        return c.renderDescriptor().orElseThrow();
    }

    private static void verify(GameTestHelper h, MixerRenderDescriptor d, int depth) {
        var a = d.assembly();
        double r = a.rotorRadius();
        h.assertTrue(
                a.shaftLength() == depth
                        && d.shaftTop().equals(Vec3.ZERO)
                        && d.shaftBottom()
                                .equals(new Vec3(0, a.vesselBottom() + MixerRenderDescriptor.WALL_THICKNESS, 0)),
                "Rendered shaft differs from placed Shaft blocks");
        h.assertTrue(
                d.tankBounds().equals(new AABB(-r, a.vesselBottom(), -r, r, MixerRenderDescriptor.RIM_Y, r)),
                "Tank did not use cached radius and reached depth");
        h.assertTrue(
                d.armY() == -.5 && d.armReach() > 0 && d.armReach() <= r, "Arms are not at the first usable level");
        h.assertTrue(
                d.walls().stream().allMatch(p -> p.bounds().minY >= -depth && p.bounds().maxY <= 0),
                "Tank included machinery or unreached depth");
        int side = 2 * a.rotorRadiusPixels();
        long count = 0;
        for (int y = 0; y < 16 * depth; y++)
            for (int z = 0; z < side; z++)
                for (int x = 0; x < side; x++) if (MixerRenderDescriptor.occupied(a, x, y, z)) count++;
        h.assertTrue(count == a.geometricSweptVoxels(), "Visual mask differs from cached usable swept volume");
        h.assertTrue(
                d.walls().stream().noneMatch(p -> p.normal().y == 1 && p.bounds().maxY == 0),
                "Open tank has a machinery-layer lid");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shaftDepthRadiusAndTankBoundsUseCache(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var d = descriptor(h);
            verify(h, d, 3);
            h.assertTrue(controller(h).renderDescriptor().orElseThrow() == d, "Repeated rendering rebuilt geometry");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shortShaftExcludesUnreachedAndMachineryLayers(GameTestHelper h) {
        var top = rig(h, 1);
        h.setBlock(top.east(), FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
        h.runAtTickTime(35, () -> {
            verify(h, descriptor(h), 1);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fixedVesselAndArmClearanceSurviveNetworkRoundTrip(GameTestHelper h) {
        rig(h, 2);
        h.setBlock(
                O.offset(0, 3, 1),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING, ConduitShell.MIXER));
        h.runAtTickTime(35, () -> {
            var d = descriptor(h);
            verify(h, d, 2);
            var a = d.assembly();
            h.assertTrue(
                    a.radiusReduction() == 0
                            && d.armReach()
                                    == a.rotorRadius()
                                            - MixerVesselGeometry.ARM_CLEARANCE
                                            - MixerRenderDescriptor.WALL_THICKNESS,
                    "Outside conduit changed rotor clearance");
            var tag = controller(h)
                    .syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            var restored = MixerAssembly.load(tag.getCompound("mixerAssembly"));
            h.assertTrue(
                    a.equals(restored) && MixerRenderDescriptor.from(restored).equals(d),
                    "Network lost mask or descriptor geometry");
            controller(h).syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            h.assertTrue(
                    controller(h).renderDescriptor().orElseThrow() == d,
                    "Identical network update rebuilt visual geometry");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void breakReformAndShorteningRebuildDescriptor(GameTestHelper h) {
        var top = rig(h, 3);
        final MixerRenderDescriptor[] before = {null};
        h.runAtTickTime(35, () -> {
            before[0] = descriptor(h);
            h.setBlock(top, Blocks.AIR);
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(controller(h).renderDescriptor().isEmpty(), "Broken Mixer exposed stale rendering");
            h.setBlock(top, MixerContent.state(MixerPartType.Impeller));
        });
        h.runAtTickTime(95, () -> {
            var d = descriptor(h);
            h.assertTrue(d != before[0] && d.equals(before[0]), "Reform failed to rebuild matching visual descriptor");
            before[0] = d;
            h.setBlock(top.below(3), Blocks.AIR);
        });
        h.runAtTickTime(125, () -> {
            var d = descriptor(h);
            verify(h, d, 2);
            h.assertTrue(d != before[0], "Shortened shaft retained old visual descriptor");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void selectedRadiusPersistsWithoutLocalMasks(GameTestHelper h) {
        var a = new MixerAssembly(BlockPos.ZERO, 2, 5, 0);
        var b = new MixerAssembly(BlockPos.ZERO, 2, 5, 1);
        h.assertTrue(
                !a.equals(b) && !MixerRenderDescriptor.from(a).equals(MixerRenderDescriptor.from(b)),
                "Radius reduction hid geometry changes");
        var tag = b.save();
        tag.putLong("volume", 1);
        tag.putInt("slotCount", 999);
        h.assertTrue(
                MixerAssembly.load(tag).equals(b)
                        && MixerAssembly.load(tag).geometricSweptVoxels() == b.geometricSweptVoxels(),
                "Persistence accepted arbitrary local volume");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void smoothStartupThenOneRevolutionPerCookCycle(GameTestHelper h) {
        h.assertTrue(
                MixerRotorAnimation.speed(0) == 0
                        && MixerRotorAnimation.speed(20) == .9
                        && MixerRotorAnimation.speed(40) == 1.8
                        && MixerRotorAnimation.speed(400) == 1.8,
                "Startup speed/cap incorrect");
        h.assertTrue(
                MixerRotorAnimation.travel(240) - MixerRotorAnimation.travel(40) == 360,
                "Operating speed is not one revolution per 200 ticks");
        var rotor = new MixerRotorAnimation();
        rotor.tick(true);
        h.assertTrue(
                rotor.angle(0) == 0 && rotor.angle(1) > 0 && rotor.angle(1) < .01, "Rotor started at operating speed");
        for (int i = 1; i < 40; i++) rotor.tick(true);
        h.assertTrue(Math.abs(rotor.angle(1) - 36) < 1e-9, "Startup did not integrate the smooth speed ramp");
        double total = 0;
        for (int i = 0; i < 200; i++) {
            rotor.tick(true);
            total += rotor.angle(1) - rotor.angle(0);
        }
        h.assertTrue(Math.abs(total - 360) < 1e-8, "Tick interpolation drifted from the operating baseline");
        rotor.tick(false);
        double stopped = rotor.angle(1);
        rotor.tick(false);
        h.assertTrue(rotor.angle(.5f) == stopped, "Idle rotor kept turning");
        rotor.reset();
        h.assertTrue(rotor.angle(1) == 0, "Reform did not reset animation");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void minimumVesselAndLiquidStayInsideFormedBounds(GameTestHelper h) {
        var top = MachineFixtures.mixer(h::setBlock, O, 3, 3, 3, 1, 1, true);
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 1)))
                    .getMultiblockController()
                    .orElseThrow();
            var d = c.renderDescriptor().orElseThrow();
            verify(h, d, 1);
            h.assertTrue(
                    d.assembly().impeller().equals(h.absolutePos(top))
                            && d.assembly().vessel() == MixerVesselGeometry.forDiameter(3),
                    "Minimum vessel axis or polygon geometry changed");
            int slots = c.slotCount();
            var fluid = new FluidStack(Fluids.WATER, 1000);
            int layer = d.liquidLayer(fluid.getAmount());
            for (var panel : d.liquidSurface(layer))
                h.assertTrue(d.tankBounds().contains(panel.bounds().getCenter()), "Liquid escaped vessel bounds");
            h.assertTrue(
                    layer > 0 && layer <= 14 && c.slotCount() == slots,
                    "Liquid presentation changed capacity or rim headroom");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fullAndShortLargeVesselsPreserveEnvelopeAndSaveLoad(GameTestHelper h) {
        for (int depth : List.of(1, 7)) {
            var a = new MixerAssembly(new BlockPos(4, 8, 4), depth, 9, 0);
            var d = MixerRenderDescriptor.from(a);
            h.assertTrue(
                    d.equals(MixerRenderDescriptor.from(MixerAssembly.load(a.save()))),
                    "Save/load changed large vessel");
            h.assertTrue(
                    d.tankBounds().minY == -depth
                            && Math.hypot(d.armReach(), MixerRenderDescriptor.ARM_WIDTH / 2) < 4.25 - 1.0 / 32,
                    "Arms intersect steel wall");
            h.assertTrue(
                    d.walls().stream()
                            .allMatch(p -> p.bounds().minX >= -4.25
                                    && p.bounds().maxX <= 4.25
                                    && p.bounds().minZ >= -4.25
                                    && p.bounds().maxZ <= 4.25
                                    && p.bounds().minY >= -depth
                                    && p.bounds().maxY <= 0),
                    "Large vessel escaped legal inset/depth");
            h.assertTrue(
                    d.liquidLayer(Integer.MAX_VALUE) == depth * 16 - 2 && d.liquidLayer(0) == 0,
                    "Stored volume overflow or empty surface");
        }
        h.succeed();
    }

    private static FluidStack compound(String carrier, boolean dirty) {
        var entries = new java.util.ArrayList<CompoundComposition.Entry>();
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, carrier, 1000));
        if (dirty) entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:waste", 1000));
        var stack = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000);
        stack.set(CompoundFluids.COMPOSITION.get(), new CompoundComposition(entries));
        return stack;
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void polygonMeshWindingFansRimAndReducedEnvelope(GameTestHelper h) {
        for (int diameter : List.of(3, 5, 7, 9))
            for (int reduction = 0;
                    MixerVesselGeometry.reduced(diameter, reduction).isPresent();
                    reduction++)
                for (int depth : List.of(1, 7))
                    for (int inset : List.of(0, MixerVesselGeometry.SHELL_DEPTH_PIXELS)) {
                        var a = new MixerAssembly(BlockPos.ZERO, depth, diameter, reduction, inset);
                        var d = MixerRenderDescriptor.from(a);
                        var polygon = a.vessel();
                        int n = polygon.sides();
                        h.assertTrue(
                                n == 4 * (diameter - 1)
                                        && d.walls().size() == n
                                        && d.interior().size() == n
                                        && d.rim().size() == n
                                        && d.bottom().size() == 2 * n,
                                "Mesh did not consume canonical polygon side count");
                        var all = new java.util.ArrayList<MixerRenderDescriptor.Face>();
                        all.addAll(d.walls());
                        all.addAll(d.interior());
                        all.addAll(d.rim());
                        all.addAll(d.bottom());
                        for (var face : all) {
                            var p = face.vertices();
                            double winding = p.get(1)
                                    .subtract(p.getFirst())
                                    .cross(p.get(2).subtract(p.getFirst()))
                                    .dot(face.normal());
                            h.assertTrue(
                                    winding > 0 && Math.abs(face.normal().length() - 1) < 1e-12,
                                    "Face winding or normal incorrect");
                            for (var point : p)
                                h.assertTrue(
                                        polygon.contains(point.x, point.z)
                                                && point.y >= a.vesselBottom()
                                                && point.y <= 0,
                                        "Mesh left canonical envelope");
                        }
                        for (int i = 0; i < n; i++) {
                            int j = (i + 1) % n;
                            var wall = d.walls().get(i);
                            var rim = d.rim().get(i);
                            h.assertTrue(
                                    wall.vertices().size() == 4
                                            && wall.vertices()
                                                    .get(1)
                                                    .equals(new Vec3(
                                                            polygon.vertices().get(i).x,
                                                            MixerRenderDescriptor.RIM_Y,
                                                            polygon.vertices().get(i).z))
                                            && wall.vertices()
                                                    .get(2)
                                                    .equals(new Vec3(
                                                            polygon.vertices().get(j).x,
                                                            MixerRenderDescriptor.RIM_Y,
                                                            polygon.vertices().get(j).z)),
                                    "Wall introduced an independent polygon");
                            h.assertTrue(
                                    rim.vertices()
                                                    .get(3)
                                                    .equals(d.rim()
                                                            .get(j)
                                                            .vertices()
                                                            .get(0))
                                            && rim.vertices()
                                                    .get(2)
                                                    .equals(d.rim()
                                                            .get(j)
                                                            .vertices()
                                                            .get(1)),
                                    "Rim discontinuity");
                            h.assertTrue(
                                    wall.vertices().get(0).y == a.vesselBottom()
                                            && wall.vertices().get(1).y == MixerRenderDescriptor.RIM_Y,
                                    "Wall lost reached depth");
                        }
                        for (int i = 0; i < 4; i++) {
                            int edge = Math.floorMod(i * n / 4 - 1, n);
                            var wall = d.walls().get(edge);
                            double x =
                                    (wall.vertices().get(1).x + wall.vertices().get(2).x) / 2;
                            double z =
                                    (wall.vertices().get(1).z + wall.vertices().get(2).z) / 2;
                            double angle = i * Math.PI / 2;
                            h.assertTrue(
                                    Math.abs(x - polygon.faceRadius() * Math.cos(angle)) < 1e-12
                                            && Math.abs(z - polygon.faceRadius() * Math.sin(angle)) < 1e-12,
                                    "Cardinal face midpoint is not the canonical apothem");
                        }
                        for (int offset : List.of(0, n)) {
                            double area = 0;
                            for (int i = 0; i < n; i++) {
                                var p = d.bottom().get(offset + i).vertices();
                                var next = d.bottom().get(offset + (i + 1) % n).vertices();
                                h.assertTrue(
                                        p.size() == 3 && p.getFirst().x == 0 && p.getFirst().z == 0,
                                        "Bottom is not a center fan");
                                h.assertTrue(
                                        (offset == 0
                                                ? p.get(1).equals(next.get(2))
                                                : p.get(2).equals(next.get(1))),
                                        "Bottom fan perimeter is not shared");
                                area += p.get(1)
                                                .subtract(p.getFirst())
                                                .cross(p.get(2).subtract(p.getFirst()))
                                                .length()
                                        / 2;
                            }
                            double r = polygon.faceRadius() - (offset == 0 ? MixerRenderDescriptor.WALL_THICKNESS : 0);
                            h.assertTrue(
                                    Math.abs(area - n * r * r * Math.tan(Math.PI / n)) < 1e-10,
                                    "Bottom fan has gaps or overlaps");
                        }
                        int layer = d.liquidLayer(Integer.MAX_VALUE);
                        var liquid = d.liquidSurface(layer);
                        h.assertTrue(
                                layer == depth * 16 - 2
                                        && liquid.size() == n
                                        && d.liquidSurface(0).isEmpty()
                                        && d.liquidLayer(-1) == 0
                                        && d.liquidSurface(depth * 16).isEmpty(),
                                "Liquid clamping or empty behavior incorrect");
                        for (int i = 0; i < n; i++) {
                            var p = liquid.get(i).vertices();
                            var boundary = d.liquidBoundary().get(i);
                            h.assertTrue(
                                    p.get(2).x == boundary.x
                                            && p.get(2).z == boundary.z
                                            && p.getFirst().y <= -MixerRenderDescriptor.HEADROOM,
                                    "Liquid lost shared boundary or rim headroom");
                            h.assertTrue(
                                    p.get(1)
                                                    .subtract(p.getFirst())
                                                    .cross(p.get(2).subtract(p.getFirst()))
                                                    .y
                                            > 0,
                                    "Liquid fan faces down");
                        }
                        for (int angle = 0; angle < 360; angle += 5)
                            for (int sx : List.of(-1, 1))
                                for (int sz : List.of(-1, 1)) {
                                    double theta = Math.toRadians(angle),
                                            x = sx * d.armReach(),
                                            z = sz * MixerRenderDescriptor.ARM_WIDTH / 2;
                                    double px = x * Math.cos(theta) - z * Math.sin(theta),
                                            pz = x * Math.sin(theta) + z * Math.cos(theta);
                                    double scale = polygon.faceRadius()
                                            / (polygon.faceRadius() - MixerRenderDescriptor.WALL_THICKNESS);
                                    h.assertTrue(
                                            polygon.contains(px * scale, pz * scale),
                                            "Rotating paddle clips interior wall");
                                }
                    }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void executableStrategyAndActiveBatchPresentationDoNotMutateStorage(GameTestHelper h) {
        var saved = MixerRecipeRegistry.snapshot();
        try {
            var definitions = com.google.gson.JsonParser.parseString("""
                {"recipes":{"test:liquid":{"batch_ticks":200,"strategies":[
                  {"id":"water","priority":10,"fluids":[{"selector":"minecraft:water","amount":1000}]},
                  {"id":"lava","fluids":[{"selector":"minecraft:lava","amount":1000}]}],
                  "outputs":{"fluids":[{"fluid":"mobvats:meat","amount":1000}]}}}}
                """);
            MixerRecipeRegistry.publish(MixerRecipeRegistry.parse(definitions));
            var state = new MixerState(() -> {});
            state.fluids(true)
                    .fill(
                            new FluidStack(Fluids.WATER, 100),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            state.fluids(true)
                    .fill(
                            new FluidStack(Fluids.LAVA, 1000),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            var before = state.save(h.getLevel().registryAccess());
            h.assertTrue(
                    state.workingFluid().is(Fluids.LAVA)
                            && state.workingFluid().getAmount() == 1000
                            && before.equals(state.save(h.getLevel().registryAccess())),
                    "Presentation selected unavailable strategy or mutated state");
            int batchTicks = MixerRecipeRegistry.recipe("test:liquid").batchTicks();
            for (int i = 0; i < batchTicks / SlurryBalance.CADENCE - 1; i++) state.cadence();
            h.assertTrue(
                    state.progress() == batchTicks - SlurryBalance.CADENCE
                            && state.workingFluid().is(Fluids.LAVA),
                    "Active batch lost its carrier");
            var network = new MixerState(() -> {});
            network.load(
                    state.presentation(h.getLevel().registryAccess()),
                    h.getLevel().registryAccess());
            h.assertTrue(
                    network.workingFluid().is(Fluids.LAVA)
                            && network.workingFluid().getAmount() == 1000,
                    "Active batch network view lost identity or amount");
            h.assertTrue(
                    state.presentationFluids().stream().anyMatch(s -> s.is(Fluids.LAVA) && s.getAmount() == 1000),
                    "Inputs were consumed before batch completion");
            state.cadence();
            h.assertTrue(
                    state.progress() == 0
                            && state.presentationFluids().stream().noneMatch(s -> s.is(Fluids.LAVA))
                            && state.workingFluid().isEmpty(),
                    "Completed batch retained imaginary fluid or blended unrelated storage");
        } finally {
            MixerRecipeRegistry.publish(saved);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void liquidIdentityAmbiguityAndComponentsArePreserved(GameTestHelper h) {
        var water = new FluidStack(Fluids.WATER, 1000);
        var lava = new FluidStack(Fluids.LAVA, 500);
        h.assertTrue(
                MixerLiquidPresentation.select(null, List.of(water, lava)).isEmpty(),
                "Incompatible buffers became an invented blend");
        var dirty = compound("minecraft:water", true);
        var other = compound("minecraft:lava", true);
        h.assertTrue(
                MixerLiquidPresentation.select(null, List.of(dirty, other)).isEmpty(),
                "Distinct compound components were combined");
        var selected = MixerLiquidPresentation.select(null, List.of(dirty, dirty.copyWithAmount(250)));
        h.assertTrue(
                selected.getAmount() == 1250 && FluidStack.isSameFluidSameComponents(selected, dirty),
                "Selected fluid lost composition or split-buffer amount");
        selected.setAmount(1);
        h.assertTrue(dirty.getAmount() == 1000, "Presentation mutated physical storage");
        h.assertTrue(
                MixerLiquidPresentation.compoundTint(water, 0xff123456) == 0xff123456,
                "Ordinary fluid lost native appearance");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void recipeCarrierSelectionDoesNotBlendAdditivesOrOutputs(GameTestHelper h) {
        var water = new FluidStack(Fluids.WATER, 1000);
        var waste = new FluidStack(ProductFluids.WASTE.get(), 250);
        var xp = new FluidStack(ProductFluids.EXPERIENCE.get(), 250);
        var feeding = MixerRecipeRegistry.recipe("feeding_slurry");
        var culture = MixerRecipeRegistry.recipe("culture_slurry");
        h.assertTrue(feeding != null && culture != null, "Bundled acceptance recipes missing");
        for (var recipe : List.of(feeding, culture)) {
            var chosen = MixerLiquidPresentation.select(
                    recipe, List.of(water, waste, xp, compound("minecraft:water", false)));
            h.assertTrue(
                    chosen.is(Fluids.WATER) && chosen.getAmount() == 1000,
                    "Recipe carrier mixed additives or finished slurry");
        }
        for (String carrier : List.of("minecraft:water", "minecraft:lava")) {
            var dirty = compound(carrier, true);
            var recipe = MixerRecipeRegistry.recipe(
                    carrier.endsWith("lava") ? "mobvats:filter_lava" : "mobvats:filter_general");
            h.assertTrue(recipe != null, "Acceptance filtration recipe missing");
            var chosen =
                    MixerLiquidPresentation.select(recipe, List.of(dirty, water, new FluidStack(Fluids.LAVA, 500)));
            h.assertTrue(
                    FluidStack.isSameFluidSameComponents(chosen, dirty),
                    "Filtration selected output instead of dirty working input");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void compositionPaletteAndTenPercentWasteCap(GameTestHelper h) {
        int water = MixerLiquidPresentation.compoundTint(compound("minecraft:water", false), 0xff9877cf);
        int lava = MixerLiquidPresentation.compoundTint(compound("minecraft:lava", false), 0xff9877cf);
        int dirty = MixerLiquidPresentation.compoundTint(compound("minecraft:water", true), 0xff9877cf);
        h.assertTrue(
                (water & 255) > ((water >> 16) & 255) && ((lava >> 16) & 255) > (lava & 255),
                "Carrier palette lost blue/orange identity");
        for (int shift : List.of(16, 8, 0)) {
            int clean = (water >> shift) & 255, waste = (0x756142 >> shift) & 255, actual = (dirty >> shift) & 255;
            h.assertTrue(
                    actual == (int) Math.round(clean * .9 + waste * .1),
                    "Waste tint exceeded established ten-percent cap");
        }
        var end = compound("minecraft:water", false);
        end.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 1000),
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:chorus_fruit", 1))));
        h.assertTrue(
                MixerLiquidPresentation.compoundTint(end, 0xff67bba7) == 0xff8665ad,
                "Chorus compound lost purple identity");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void outsideBufferPreservesUniformLiquidLevel(GameTestHelper h) {
        rig(h, 2);
        h.setBlock(O.offset(0, 3, 0), FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
        h.runAtTickTime(35, () -> {
            var d = descriptor(h);
            int layer = 24;
            for (var panel : d.liquidSurface(layer)) {
                var p = panel.bounds().getCenter();
                int r = d.assembly().rotorRadiusPixels();
                h.assertTrue(
                        MixerRenderDescriptor.occupied(
                                d.assembly(),
                                (int) Math.floor(p.x * 16) + r,
                                layer - 1,
                                (int) Math.floor(p.z * 16) + r),
                        "Liquid rendered through physical buffer");
            }
            var state = new MixerState(() -> {});
            state.fluids(true)
                    .fill(
                            new FluidStack(Fluids.WATER, 1000),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            var restored = new MixerState(() -> {});
            restored.load(
                    state.presentation(h.getLevel().registryAccess()),
                    h.getLevel().registryAccess());
            h.assertTrue(
                    MixerLiquidPresentation.select(null, restored.presentationFluids())
                            .is(Fluids.WATER),
                    "Network presentation lost ordinary identity");
            h.succeed();
        });
    }
}
