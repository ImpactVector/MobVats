package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

/** Shared geometry tests run on the dedicated server, without loading client rendering classes. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class ConduitGeometryGameTests {
    private static final BlockPos MIN = BlockPos.ZERO, MAX = new BlockPos(4, 3, 4);

    private static ConduitGeometry.Cell cell(
            BlockPos pos,
            ConduitResource resource,
            ConduitCladding cladding,
            ConduitMode mode,
            int mask,
            List<ConduitGeometry.Attachment> attachments) {
        return new ConduitGeometry.Cell(pos, resource, cladding, mode, mask, attachments);
    }

    private static ConduitGeometry.Geometry isolated(
            BlockPos pos, ConduitResource resource, ConduitCladding cladding, ConduitMode mode) {
        return ConduitGeometry.bake(
                new ConduitGeometry.Snapshot(MIN, MAX, List.of(cell(pos, resource, cladding, mode, 0, List.of()))));
    }

    private static void put(
            GameTestHelper h, BlockPos pos, ConduitResource resource, ConduitCladding cladding, ConduitMode mode) {
        h.setBlock(pos, ConduitContent.state(resource, cladding).setValue(ConduitBlock.MODE, mode));
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void bareGeometryIsSixPixelPipe(GameTestHelper h) {
        var pos = new BlockPos(2, 1, 2);
        var geometry = isolated(pos, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
        h.assertTrue(
                geometry.pipeSegments().size() == 1
                        && geometry.endpointFittings().isEmpty(),
                "Isolated TRUNK must have one hub and no fitting");
        var bounds = geometry.pipeSegments().getFirst().bounds();
        h.assertTrue(
                bounds.getXsize() == 6.0 / 16 && bounds.getYsize() == 6.0 / 16 && bounds.getZsize() == 6.0 / 16,
                "Bare pipe became full block geometry");
        h.assertTrue(bounds.getCenter().equals(Vec3.atCenterOf(pos)), "Interior center moved");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void claddingAndCornerVolumesRemainInsideShell(GameTestHelper h) {
        for (var cladding : List.of(ConduitCladding.GLASS, ConduitCladding.CASING))
            for (var resource : ConduitResource.values())
                for (var pos : List.of(new BlockPos(0, 1, 2), new BlockPos(0, 1, 0), MIN, MAX)) {
                    var geometry = isolated(pos, resource, cladding, ConduitMode.TAP);
                    h.assertTrue(
                            geometry.snapshot().cells().getFirst().cladding() == cladding,
                            "Lost structural cladding metadata");
                    var shell = VatCasingState.from(pos, MIN, MAX);
                    for (var volume : geometry.occupied())
                        for (var face : Direction.values())
                            if (shell.outward(face)) {
                                var b = volume.bounds();
                                var axis = face.getAxis();
                                double low =
                                        axis == Direction.Axis.X ? b.minX : axis == Direction.Axis.Y ? b.minY : b.minZ;
                                double high =
                                        axis == Direction.Axis.X ? b.maxX : axis == Direction.Axis.Y ? b.maxY : b.maxZ;
                                double boundary = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                                        ? MIN.get(axis) + EndpointGeometry.PANEL_DEPTH
                                        : MAX.get(axis) + 1 - EndpointGeometry.PANEL_DEPTH;
                                h.assertTrue(
                                        face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                                                ? low >= boundary
                                                : high <= boundary,
                                        "Pipe/cuff crossed shell plane: " + pos + " " + face);
                            }
                }
        // Connected corner -> edge -> face runs must remain behind all three outer shell planes.
        for (var resource : ConduitResource.values()) {
            var cells = List.of(
                    cell(
                            MIN,
                            resource,
                            ConduitCladding.CASING,
                            ConduitMode.TAP,
                            1 << Direction.EAST.ordinal(),
                            List.of()),
                    cell(
                            MIN.east(),
                            resource,
                            ConduitCladding.CASING,
                            ConduitMode.TRUNK,
                            (1 << Direction.WEST.ordinal()) | (1 << Direction.SOUTH.ordinal()),
                            List.of()),
                    cell(
                            MIN.east().south(),
                            resource,
                            ConduitCladding.CASING,
                            ConduitMode.TRUNK,
                            1 << Direction.NORTH.ordinal(),
                            List.of()));
            for (var volume : ConduitGeometry.bake(new ConduitGeometry.Snapshot(MIN, MAX, cells))
                    .occupied()) {
                var b = volume.bounds();
                h.assertTrue(
                        b.minX >= EndpointGeometry.PANEL_DEPTH
                                && b.minY >= EndpointGeometry.PANEL_DEPTH
                                && b.minZ >= EndpointGeometry.PANEL_DEPTH,
                        "Connected corner/edge pipe crossed shell");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void connectionsUseResourceNotCladdingOrMode(GameTestHelper h) {
        var first = new BlockPos(2, 1, 2);
        var second = first.east();
        var third = second.east();
        put(h, first, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
        put(h, second, ConduitResource.ITEM, ConduitCladding.CASING, ConduitMode.TAP);
        put(h, third, ConduitResource.FLUID, ConduitCladding.GLASS, ConduitMode.TRUNK);
        var min = h.absolutePos(MIN);
        var max = h.absolutePos(new BlockPos(5, 3, 4));
        var graph = ConduitTopology.compile(h.getLevel(), min, max, List.of());
        var snapshot = ConduitGeometry.compile(h.getLevel(), min, max, graph);
        var a = snapshot.cells().stream()
                .filter(c -> c.position().equals(h.absolutePos(first)))
                .findFirst()
                .orElseThrow();
        var b = snapshot.cells().stream()
                .filter(c -> c.position().equals(h.absolutePos(second)))
                .findFirst()
                .orElseThrow();
        h.assertTrue(
                (a.connections() & 1 << Direction.EAST.ordinal()) != 0
                        && (b.connections() & 1 << Direction.WEST.ordinal()) != 0,
                "Same-resource mixed cladding did not connect");
        h.assertTrue((b.connections() & 1 << Direction.EAST.ordinal()) == 0, "Different resources connected");
        var geometry = ConduitGeometry.bake(snapshot);
        h.assertTrue(
                geometry.pipeSegments().stream().anyMatch(v -> v.bounds().getXsize() > ConduitGeometry.DIAMETER),
                "Connecting segment missing");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fittingRequiresActualTopologyEndpoint(GameTestHelper h) {
        var endpoint = new BlockPos(0, 1, 2);
        var tap = endpoint.above();
        h.setBlock(endpoint, Content.state(VatPartType.ItemPort));
        put(h, tap, ConduitResource.ITEM, ConduitCladding.GLASS, ConduitMode.TRUNK);
        var min = h.absolutePos(MIN);
        var max = h.absolutePos(MAX);
        var endpoints = List.of(new ConduitTopology.Endpoint(
                h.absolutePos(endpoint), ConduitResource.ITEM, ConduitTopology.Role.CONSUMER));
        var graph = ConduitTopology.compile(h.getLevel(), min, max, endpoints);
        h.assertTrue(
                ConduitGeometry.bake(ConduitGeometry.compile(h.getLevel(), min, max, graph))
                        .endpointFittings()
                        .isEmpty(),
                "TRUNK attached endpoint");
        put(h, tap, ConduitResource.ITEM, ConduitCladding.GLASS, ConduitMode.TAP);
        graph = ConduitTopology.compile(h.getLevel(), min, max, endpoints);
        h.assertTrue(
                ConduitGeometry.bake(ConduitGeometry.compile(h.getLevel(), min, max, graph))
                                .endpointFittings()
                                .size()
                        == 1,
                "Valid TAP fitting missing");
        graph = ConduitTopology.compile(h.getLevel(), min, max, List.of());
        h.assertTrue(
                ConduitGeometry.bake(ConduitGeometry.compile(h.getLevel(), min, max, graph))
                        .endpointFittings()
                        .isEmpty(),
                "Arbitrary neighbor attached without topology compatibility");
        h.setBlock(endpoint, Blocks.STONE);
        graph = ConduitTopology.compile(h.getLevel(), min, max, List.of());
        h.assertTrue(
                ConduitGeometry.bake(ConduitGeometry.compile(h.getLevel(), min, max, graph))
                        .endpointFittings()
                        .isEmpty(),
                "STONE acquired fitting");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void serviceFittingsAvoidActorCorridorsOnAllFaces(GameTestHelper h) {
        for (var face : Direction.values()) {
            var pos = switch (face) {
                case WEST -> new BlockPos(0, 1, 2);
                case EAST -> new BlockPos(4, 1, 2);
                case DOWN -> new BlockPos(2, 0, 2);
                case UP -> new BlockPos(2, 3, 2);
                case NORTH -> new BlockPos(2, 1, 0);
                case SOUTH -> new BlockPos(2, 1, 4);
            };
            var tangent = face.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
            for (var conduit : List.of(pos.relative(tangent), pos.relative(face.getOpposite())))
                for (var kind : List.of(
                        EndpointGeometry.Kind.TOOL, EndpointGeometry.Kind.FEED, EndpointGeometry.Kind.BLENDER)) {
                    var cladding = conduit.equals(pos.relative(face.getOpposite()))
                            ? ConduitCladding.BARE
                            : ConduitCladding.CASING;
                    var snapshot = new ConduitGeometry.Snapshot(
                            MIN,
                            MAX,
                            List.of(cell(
                                    conduit,
                                    ConduitResource.ITEM,
                                    cladding,
                                    ConduitMode.TAP,
                                    0,
                                    List.of(new ConduitGeometry.Attachment(pos, kind)))));
                    var fitting =
                            ConduitGeometry.bake(snapshot).endpointFittings().getFirst();
                    var endpoint = fitting.endpoint();
                    h.assertTrue(
                            !endpoint.serviceConnectionAnchor()
                                    .equals(endpoint.interactionOrigin().orElseThrow()),
                            "Service and interaction anchors identical");
                    var corridor = endpoint.interactionCorridor().orElseThrow();
                    h.assertTrue(
                            fitting.volumes().stream().noneMatch(v -> v.bounds().intersects(corridor)),
                            "Service fitting entered actor corridor: " + face + " " + kind + " " + conduit);
                    var cuff = fitting.volumes().getLast().bounds();
                    var anchor = endpoint.serviceConnectionAnchor();
                    var axis = face.getAxis();
                    double terminal = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                            ? (axis == Direction.Axis.X ? cuff.minX : axis == Direction.Axis.Y ? cuff.minY : cuff.minZ)
                            : (axis == Direction.Axis.X ? cuff.maxX : axis == Direction.Axis.Y ? cuff.maxY : cuff.maxZ);
                    double expected =
                            axis == Direction.Axis.X ? anchor.x : axis == Direction.Axis.Y ? anchor.y : anchor.z;
                    h.assertTrue(
                            Math.abs(terminal - expected) < 1e-9,
                            "Fitting did not terminate at backside service anchor");
                }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void geometryAndSnapshotRoundTripAreDeterministic(GameTestHelper h) {
        var node = new BlockPos(0, 2, 2);
        var snapshot = new ConduitGeometry.Snapshot(
                MIN,
                MAX,
                List.of(cell(
                        node,
                        ConduitResource.FLUID,
                        ConduitCladding.GLASS,
                        ConduitMode.TAP,
                        0,
                        List.of(new ConduitGeometry.Attachment(node.below(), EndpointGeometry.Kind.FEED)))));
        var restored = ConduitGeometry.Snapshot.load(snapshot.save());
        h.assertTrue(restored.equals(snapshot), "Render snapshot changed across network NBT");
        var a = ConduitGeometry.bake(snapshot);
        var b = ConduitGeometry.bake(restored);
        h.assertTrue(
                a.occupied().equals(b.occupied()) && a.endpointFittings().equals(b.endpointFittings()),
                "Geometry not deterministic");
        var item = isolated(new BlockPos(2, 1, 2), ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TAP);
        var fluid = isolated(new BlockPos(2, 1, 2), ConduitResource.FLUID, ConduitCladding.BARE, ConduitMode.TAP);
        h.assertTrue(
                item.pipeSegments().size() != fluid.pipeSegments().size(), "Resource distinction relies only on color");
        var cuffs = fluid.pipeSegments().stream()
                .filter(v -> v.material() == ConduitGeometry.Material.COLLAR)
                .toList();
        for (int i = 0; i < cuffs.size(); i++)
            for (int j = i + 1; j < cuffs.size(); j++)
                h.assertTrue(!cuffs.get(i).bounds().intersects(cuffs.get(j).bounds()), "Fluid cuff rectangles overlap");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void formedControllerOwnsAndReusesCompiledGeometry(GameTestHelper h) {
        ConduitGameTests.routedRig(h);
        h.runAtTickTime(40, () -> {
            var vat = ConduitGameTests.vat(h);
            h.assertTrue(vat.isAssembled(), "Routed vat did not form");
            var cached = vat.conduitGeometry();
            h.assertTrue(
                    cached.snapshot().cells().size() == 10
                            && cached.endpointFittings().size() >= 4,
                    "Controller omitted conduit or endpoint render descriptors");
            var network = vat.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            h.assertTrue(
                    ConduitGeometry.Snapshot.load(network.getCompound("conduitGeometry"))
                            .equals(cached.snapshot()),
                    "Controller network update omitted geometry");
            h.assertTrue(
                    cached.endpointFittings().stream()
                            .anyMatch(f -> f.endpoint().kind() == EndpointGeometry.Kind.TOOL),
                    "Valid Tool service endpoint absent");
            h.runAtTickTime(50, () -> {
                h.assertTrue(vat.conduitGeometry() == cached, "Static geometry was rebuilt without topology change");
                h.assertTrue(
                        vat.getTopology().actors().stream()
                                        .noneMatch(r -> vat.getWorld().getBlockEntity(r.actor())
                                                instanceof
                                                net.impactvector.mobvats.gamecontent.multiblock.vat.part
                                                        .VatToolPortEntity)
                                && !vat.getTopology().toolLoading().isEmpty(),
                        "Rendering changed empty-tool loading semantics");
                var tap = new BlockPos(3, 1, 5);
                put(h, tap, ConduitResource.ITEM, ConduitCladding.CASING, ConduitMode.TRUNK);
                h.runAtTickTime(60, () -> {
                    h.assertTrue(vat.conduitGeometry() != cached, "Mode change retained stale render geometry");
                    h.assertTrue(
                            vat.conduitGeometry().endpointFittings().stream()
                                    .noneMatch(f -> f.conduit().equals(h.absolutePos(tap))),
                            "TRUNK retained obsolete endpoint fitting");
                    h.succeed();
                });
            });
        });
    }
}
