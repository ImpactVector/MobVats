package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.mob.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class SimulacrumGeometryGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1);

    private static SimulacrumGeometry geometry(int w, int h, int d, Direction face) {
        return SimulacrumGeometry.resolve(w, h, d, face, null);
    }

    private static void rejects(GameTestHelper h, Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        h.fail(message);
    }

    private static VatHeadEntity specimen(GameTestHelper h, BlockPos p, SimulacrumGeometry g) {
        for (var cell : g.occupiedCells(p))
            h.setBlock(cell, Content.state(cell.equals(p) ? VatPartType.Head : VatPartType.Flesh));
        var head = (VatHeadEntity) h.getBlockEntity(p);
        head.encode(ItemEssence.COW, g);
        return head;
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void proceduralDimensionsHeuristicAndRotation(GameTestHelper h) {
        var cow = SimulacrumGeometry.capture(EntityType.COW.create(h.getLevel()));
        h.assertTrue(cow.width() == 1 && cow.height() == 2 && cow.depth() == 1, "Cow capture dimensions changed");
        var asymmetric = SimulacrumGeometry.resolve(.8, 2.01, 4.2, Direction.NORTH, null);
        h.assertTrue(
                asymmetric.width() == 1 && asymmetric.height() == 3 && asymmetric.depth() == 5,
                "Real X/Y/Z extents not rounded independently");
        var wide = EntityType.COW.create(h.getLevel());
        wide.setYRot(270);
        wide.setBoundingBox(new AABB(0, 0, 0, 4.2, 2.01, .8));
        var turned = SimulacrumGeometry.capture(wide);
        h.assertTrue(
                turned.facing() == Direction.EAST && turned.width() == 1 && turned.depth() == 5,
                "Runtime X/Z extents not converted relative to FRONT");
        for (int w : new int[] {1, 2, 3, 4, 5})
            for (var face : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                var g = geometry(w, 3, 2, face);
                var head = new BlockPos(4, 5, 4);
                h.assertTrue(
                        g.headOffset().equals(new BlockPos((w - 1) / 2, 2, 0)), "Center/top/front heuristic changed");
                h.assertTrue(
                        g.cell(head, g.headOffset().getX(), 2, 0).equals(head)
                                && g.occupiedCells(head).size() == w * 6,
                        "Rotation lost Head root or occupied cells");
                var right = face.getClockWise();
                h.assertTrue(
                        g.rotate(new BlockPos(1, 0, 0)).equals(new BlockPos(right.getStepX(), 0, right.getStepZ())),
                        "Facing-relative right convention changed");
            }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void optionalOverridesAndInvalidGeometry(GameTestHelper h) {
        var dims = new PassiveMobDefinition.Simulacrum(List.of(2, 3, 1), null);
        var g = SimulacrumGeometry.resolve(.9, 1.4, .9, Direction.NORTH, dims);
        h.assertTrue(
                g.width() == 2 && g.height() == 3 && g.headOffset().equals(new BlockPos(0, 2, 0)),
                "Dimension-only override failed");
        var offset = new PassiveMobDefinition.Simulacrum(null, List.of(0, 0, 0));
        h.assertTrue(
                SimulacrumGeometry.resolve(.9, 1.4, .9, Direction.NORTH, offset)
                        .headOffset()
                        .equals(BlockPos.ZERO),
                "Head-only override failed");
        var both = new PassiveMobDefinition.Simulacrum(List.of(2, 3, 1), List.of(1, 1, 0));
        h.assertTrue(
                SimulacrumGeometry.resolve(.9, 1.4, .9, Direction.WEST, both)
                        .headOffset()
                        .equals(new BlockPos(1, 1, 0)),
                "Combined override failed");
        rejects(h, () -> new PassiveMobDefinition.Simulacrum(List.of(0, 2, 1), null), "Zero dimension accepted");
        rejects(
                h,
                () -> new PassiveMobDefinition.Simulacrum(List.of(2, 3, 1), List.of(2, 0, 0)),
                "Out-of-bounds Head accepted");
        rejects(
                h,
                () -> SimulacrumGeometry.resolve(
                        .9, 1.4, .9, Direction.NORTH, new PassiveMobDefinition.Simulacrum(null, List.of(1, 0, 0))),
                "Head-only override outside runtime bounds accepted");
        rejects(h, () -> geometry(1, 2, 1, Direction.UP), "Vertical FRONT accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void definitionSchemaOptionalGeometryAndAtomicRejection(GameTestHelper h) throws Exception {
        Files.createDirectories(Path.of("build"));
        var dir = Files.createTempDirectory(Path.of("build"), "geometry-definition-");
        var defaults = PassiveDefaults.data();
        var root = PassiveMobDefinitionRegistry.JSON.toJsonTree(defaults).getAsJsonObject();
        root.getAsJsonObject("minecraft:cow")
                .add(
                        "simulacrum",
                        com.google.gson.JsonParser.parseString("{\"dimensions\":[2,3,1],\"head_offset\":[1,1,0]}"));
        MobProfileFixtures.write(dir, root);
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().isEmpty()
                        && loaded.definitions()
                                .get(ItemEssence.COW)
                                .simulacrum()
                                .dimensions()
                                .equals(List.of(2, 3, 1)),
                "Valid geometry schema failed");
        root.getAsJsonObject("minecraft:cow")
                .getAsJsonObject("simulacrum")
                .add("head_offset", com.google.gson.JsonParser.parseString("[9,0,0]"));
        MobProfileFixtures.write(dir, root);
        loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                !loaded.errors().isEmpty() && !loaded.definitions().containsKey(ItemEssence.COW),
                "Invalid override replaced valid defaults");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void exactOwnershipTouchingOverlapAndMissingFlesh(GameTestHelper h) {
        var g = geometry(1, 2, 1, Direction.NORTH);
        var a = new BlockPos(2, 3, 2);
        var b = a.east();
        var first = specimen(h, a, g);
        var second = specimen(h, b, g);
        var claims = List.of(
                new SpecimenGeometry.Claim(first.getBlockPos(), g),
                new SpecimenGeometry.Claim(second.getBlockPos(), g));
        var owners = SpecimenGeometry.ownership(claims);
        h.assertTrue(
                owners.size() == 4
                        && owners.get(first.getBlockPos().below()).equals(first.getBlockPos())
                        && owners.get(second.getBlockPos().below()).equals(second.getBlockPos()),
                "Touching specimens merged");
        claims.forEach(c -> SpecimenGeometry.substrate(h.getLevel(), c));
        h.setBlock(a.west().below(), Content.state(VatPartType.Flesh));
        h.assertTrue(SpecimenGeometry.ownership(claims).equals(owners), "Adjacent Flesh expanded ownership");
        h.setBlock(a.below(), Blocks.AIR);
        rejects(
                h,
                () -> SpecimenGeometry.substrate(h.getLevel(), claims.getFirst()),
                "Missing required Flesh accepted");
        rejects(
                h,
                () -> SpecimenGeometry.ownership(List.of(
                        claims.getFirst(),
                        new SpecimenGeometry.Claim(first.getBlockPos().below(), g))),
                "Overlapping geometry accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void cornerCountsAndSpreadStrapOrigins(GameTestHelper h) {
        int[][] cases = {{1, 1, 1}, {1, 2, 2}, {1, 5, 2}, {3, 3, 4}, {2, 4, 4}};
        for (var c : cases)
            for (var face : List.of(Direction.NORTH, Direction.EAST)) {
                var g = geometry(c[0], 2, c[1], face);
                var p = new BlockPos(4, 3, 4);
                var corners = g.harnessCorners(p, 6);
                h.assertTrue(corners.size() == c[2], "Footprint corners not deduplicated");
                var origins = HarnessStraps.origins(List.copyOf(corners), g.occupiedBounds(p));
                h.assertTrue(
                        origins.size() == (c[2] == 1 ? 4 : c[2])
                                && origins.stream().allMatch(v -> v.y == 6 + 13.0 / 16),
                        "Strap mount count/height incorrect");
                var curves = HarnessStraps.curves(
                        List.copyOf(corners), g.occupiedBounds(p), g.bodyBounds(p, 1), MobRenderDefinition.DEFAULT);
                for (int i = 0; i < origins.size(); i++)
                    h.assertTrue(
                            curves.get(i).points().getFirst().equals(origins.get(i)),
                            "Strap render/obstruction origin diverged");
            }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void associationRejectsMissingCornersAndAmbiguousDrains(GameTestHelper h) {
        var g = geometry(2, 2, 2, Direction.NORTH);
        var p = new BlockPos(3, 4, 3);
        var head = specimen(h, p, g);
        var hs = new ArrayList<VatHarnessEntity>();
        for (var pos : g.harnessCorners(p, 6)) {
            h.setBlock(pos, Content.state(VatPartType.Harness));
            hs.add((VatHarnessEntity) h.getBlockEntity(pos));
        }
        var drain = p.below(3).west();
        h.setBlock(drain, Content.state(VatPartType.Sump));
        var ds = List.of((VatSumpEntity) h.getBlockEntity(drain));
        var assembly = SpecimenGeometry.associate(h.getLevel(), head, hs, ds);
        h.assertTrue(
                assembly.harnesses().size() == 4 && assembly.drain() == ds.getFirst(),
                "Spaced/offset services failed association");
        rejects(
                h,
                () -> SpecimenGeometry.associate(h.getLevel(), head, hs.subList(0, 3), ds),
                "Missing footprint Harness accepted");
        rejects(h, () -> SpecimenGeometry.associate(h.getLevel(), head, hs, List.of()), "Missing Drain accepted");
        h.setBlock(drain.east(), Content.state(VatPartType.Sump));
        rejects(
                h,
                () -> SpecimenGeometry.associate(
                        h.getLevel(), head, hs, List.of(ds.getFirst(), (VatSumpEntity) h.getBlockEntity(drain.east()))),
                "Ambiguous Drains accepted");
        h.setBlock(p.above(6), Content.state(VatPartType.Sump));
        rejects(
                h,
                () -> SpecimenGeometry.associate(
                        h.getLevel(), head, hs, List.of((VatSumpEntity) h.getBlockEntity(p.above(6)))),
                "Drain above specimen accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void persistedGeometryAndLegacyMigration(GameTestHelper h) {
        var p = new BlockPos(3, 4, 3);
        var saved = SimulacrumGeometry.resolve(
                .8, 1.3, 2.4, Direction.WEST, new PassiveMobDefinition.Simulacrum(List.of(2, 3, 4), List.of(1, 1, 2)));
        var head = specimen(h, p, saved);
        var tag = head.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                SimulacrumGeometry.load(tag.getCompound("geometry")).equals(saved), "Geometry serialization lost data");
        head.encode(ItemEssence.COW);
        head.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(head.geometry().equals(saved), "Reload recomputed saved geometry");
        var legacy = new CompoundTag();
        legacy.putString("entity", "minecraft:cow");
        head.syncDataFrom(legacy, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                head.geometry().width() == 1
                        && head.geometry().height() == 2
                        && head.geometry().depth() == 1
                        && head.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync)
                                .contains("geometry"),
                "Legacy Cow was not migrated and rewritten");
        legacy.putString("entity", "missing:entity");
        head.syncDataFrom(legacy, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                head.geometry() == null && head.geometryError() != null, "Unknown legacy identity fabricated geometry");
        var corrupt = tag.copy();
        corrupt.getCompound("geometry").putInt("version", 99);
        head.syncDataFrom(corrupt, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                head.geometry() == null
                        && head.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync)
                                        .getCompound("geometry")
                                        .getInt("version")
                                == 99,
                "Invalid geometry silently migrated on rewrite");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void captureEssenceAndDefinitionChangePreserveSavedGeometry(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.CREATIVE);
        var syringe = new ItemStack(Content.SYRINGE.get());
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        var cow = EntityType.COW.create(h.getLevel());
        cow.setYRot(90);
        var result = Content.SYRINGE.get().interactLivingEntity(syringe, player, cow, InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction(), "Capture interaction failed: " + result);
        var essence = player.getInventory().items.stream()
                .filter(s -> s.is(Content.ESSENCE.get()))
                .findFirst()
                .orElseThrow();
        var tag = essence.get(DataComponents.CUSTOM_DATA).copyTag();
        var saved = SimulacrumGeometry.load(tag.getCompound("geometry"));
        var d = PassiveMobDefinitionRegistry.resolve(ItemEssence.COW);
        var changed = new PassiveMobDefinition(
                d.entity(),
                d.feed(),
                d.juvenile_growth_ticks(),
                d.secondary_outputs(),
                d.harvest(),
                d.culture_slurry(),
                d.feeding_slurry(),
                new PassiveMobDefinition.Simulacrum(List.of(4, 4, 4), null));
        var original = PassiveMobDefinitionRegistry.definitions();
        try {
            PassiveMobDefinitionRegistry.publish(
                    new PassiveMobDefinitionRegistry.Loaded(Map.of(ItemEssence.COW, changed), Map.of(), List.of()));
            h.assertTrue(
                    SimulacrumGeometry.capture(cow).width() == 4
                            && SimulacrumGeometry.load(tag.getCompound("geometry"))
                                    .equals(saved),
                    "Config changes mutated existing essence");
        } finally {
            PassiveMobDefinitionRegistry.publish(
                    new PassiveMobDefinitionRegistry.Loaded(original, Map.of(), List.of()));
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void independentComfortShellsDistinguishSpacing(GameTestHelper h) {
        var g = geometry(1, 2, 1, Direction.NORTH);
        var a = new BlockPos(3, 4, 3);
        var first = new SpecimenClearance(g.comfortShell(a), Set.of(), List.of());
        var one = new SpecimenClearance(g.comfortShell(a.east(2)), Set.of(), List.of());
        var two = new SpecimenClearance(g.comfortShell(a.east(3)), Set.of(), List.of());
        h.assertTrue(
                !first.sharedShell(one).isEmpty() && first.sharedShell(two).isEmpty(),
                "One/two spacer clearance data indistinguishable");
        SpecimenGeometry.ownership(List.of(new SpecimenGeometry.Claim(a, g), new SpecimenGeometry.Claim(a.east(), g)));
        h.setBlock(a.west(), Blocks.STONE);
        var observed = SpecimenClearance.inspect(h.getLevel(), g, h.absolutePos(a));
        h.assertTrue(
                observed.solidIntrusions().contains(h.absolutePos(a.west()))
                        && observed.shell().size() == 34,
                "Independent shell omitted wall intrusion");
        h.succeed();
    }

    private static VatHeadEntity spacedRig(GameTestHelper h, int w, int d, boolean medium) {
        // Six-high shell: Drain / spacer / Flesh / Head / spacer / Harness.
        int size = 7;
        for (int x = 0; x < size; x++)
            for (int y = 0; y < 6; y++)
                for (int z = 0; z < size; z++)
                    if (x == 0 || z == 0 || x == size - 1 || z == size - 1 || y == 0 || y == 5)
                        h.setBlock(
                                O.offset(x, y, z),
                                Content.state(y == 0 || y == 5 ? VatPartType.Casing : VatPartType.Glass));
        h.setBlock(O.offset(1, 1, 0), Content.state(VatPartType.Controller));
        var g = geometry(w, 2, d, Direction.NORTH);
        var p = O.offset(2 + (w - 1) / 2, 3, 2);
        var head = specimen(h, p, g);
        for (var corner : g.harnessCorners(p, O.getY() + 5)) h.setBlock(corner, Content.state(VatPartType.Harness));
        h.setBlock(O.offset(1, 0, 2), Content.state(VatPartType.Sump));
        h.setBlock(O.offset(2, 0, 2), Content.state(VatPartType.Casing));
        if (medium) h.setBlock(p.above(), Blocks.WATER);
        return head;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void airSpacerLayoutFormsAndOffsetSumpOperates(GameTestHelper h) {
        var head = spacedRig(h, 1, 1, false);
        h.runAtTickTime(35, () -> {
            h.assertTrue(
                    head.isMachineAssembled(),
                    "Harness/Air/Head/Flesh/Air/Drain layout did not form: "
                            + head.getMultiblockController()
                                    .flatMap(c -> c.getLastError())
                                    .map(e -> e.getChatMessage().getString())
                                    .orElse("no controller"));
            var c = head.getMultiblockController().orElseThrow();
            var a = c.getAssemblies().getFirst();
            h.assertTrue(
                    a.drain().getBlockPos().equals(h.absolutePos(O.offset(1, 0, 2)))
                            && a.renderPosition().y
                                    == h.absolutePos(O.offset(2, 2, 2)).getY(),
                    "Drain offset changed body origin");
            h.assertTrue(
                    c.getTopology().actors().stream().anyMatch(r -> r.use().equals(PassiveUses.DRAIN)),
                    "Sump lost offset Drain assignment");
            h.assertTrue(
                    h.getLevel()
                                    .getCapability(
                                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                            a.drain().getBlockPos(),
                                            Direction.DOWN)
                            == null,
                    "Drain exposed culture-fluid capability");
            h.assertTrue(
                    c.frequencyMultiplier(c.getAssemblies().getFirst().head().getBlockPos()) == 0.5,
                    "Clearance applied a production multiplier");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void widerMediumSpacerLayoutUsesFourHarnesses(GameTestHelper h) {
        var head = spacedRig(h, 2, 4, true);
        h.runAtTickTime(35, () -> {
            h.assertTrue(
                    head.isMachineAssembled(),
                    "Wide geometry/medium spacer rejected: "
                            + head.getMultiblockController()
                                    .flatMap(c -> c.getLastError())
                                    .map(e -> e.getChatMessage().getString())
                                    .orElse("no controller"));
            var a = head.getMultiblockController().orElseThrow().getAssemblies().getFirst();
            h.assertTrue(a.harnesses().size() == 4 && a.fleshCount() == 15, "Wide specimen ownership/support wrong");
            var descriptor = net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDescriptor.from(
                    head.getMultiblockController().orElseThrow(), head);
            h.assertTrue(
                    descriptor.attachmentBounds().equals(a.attachmentBounds())
                            && descriptor.harnessPositions().equals(a.harnessPositions()),
                    "Renderer recomputed competing geometry");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wallFloorContactDoesNotRequireComfortSpace(GameTestHelper h) {
        VatGameTests.build(h, 3);
        var head = (VatHeadEntity) h.getBlockEntity(O.offset(1, 2, 1));
        head.encode(ItemEssence.COW);
        h.runAtTickTime(35, () -> {
            h.assertTrue(head.isMachineAssembled(), "Touching wall/floor invalidated specimen");
            var a = head.getMultiblockController().orElseThrow().getAssemblies().getFirst();
            h.assertTrue(
                    !a.clearance(h.getLevel()).solidIntrusions().isEmpty(),
                    "Cramped build has no clearance observations");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void mediumRulesRejectArbitraryObstructions(GameTestHelper h) {
        var p = O.offset(2, 2, 2);
        h.assertTrue(SpecimenGeometry.medium(h.getLevel(), h.absolutePos(p)), "Air not accepted");
        h.setBlock(p, Blocks.WATER);
        h.assertTrue(SpecimenGeometry.medium(h.getLevel(), h.absolutePos(p)), "Water medium not accepted");
        h.setBlock(p, Blocks.STONE);
        h.assertTrue(!SpecimenGeometry.medium(h.getLevel(), h.absolutePos(p)), "Stone silently accepted as spacer");
        h.setBlock(p, Blocks.LAVA);
        h.assertTrue(SpecimenGeometry.medium(h.getLevel(), h.absolutePos(p)), "Legacy lava medium must be recoverable");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void wideHarnessOriginsUseActualNarrowRouteIntersections(GameTestHelper h) {
        var g = geometry(3, 2, 3, Direction.NORTH);
        var p = new BlockPos(4, 4, 4);
        var corners = List.copyOf(g.harnessCorners(p, 8));
        var curves =
                HarnessStraps.curves(corners, g.occupiedBounds(p), g.bodyBounds(p, 1), MobRenderDefinition.DEFAULT);
        var curve = curves.getFirst();
        var sample = curve.points().get(3);
        var owner = BlockPos.containing(sample);
        // Both volumes belong to the same block; only the actual narrow geometry can block a strap.
        var hitBox = new AABB(
                sample.x - .1875,
                sample.y - .1875,
                sample.z - .1875,
                sample.x + .1875,
                sample.y + .1875,
                sample.z + .1875);
        var clearBox = hitBox.move(-.7, 0, 0);
        var hit = new ConduitGeometry.Geometry(
                ConduitGeometry.Snapshot.EMPTY,
                List.of(new ConduitGeometry.Volume(owner, ConduitResource.ITEM, ConduitGeometry.Material.BODY, hitBox)),
                List.of());
        var clear = new ConduitGeometry.Geometry(
                ConduitGeometry.Snapshot.EMPTY,
                List.of(new ConduitGeometry.Volume(
                        owner, ConduitResource.ITEM, ConduitGeometry.Material.BODY, clearBox)),
                List.of());
        var route = PhysicalRoute.cable(curve, HarnessStraps.THICKNESS);
        h.assertTrue(
                RouteIntersection.first(route, hit, null).isPresent()
                        && RouteIntersection.first(route, clear, null).isEmpty(),
                "Narrow pipe route distinction lost with spread origins");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void actorTargetsFollowSavedBodyRatherThanDrainOrEntityType(GameTestHelper h) {
        var g = geometry(2, 3, 2, Direction.NORTH);
        var p = O.offset(2, 3, 2);
        var head = specimen(h, p, g);
        head.encode(net.minecraft.resources.ResourceLocation.withDefaultNamespace("pig"), g);
        var hs = new ArrayList<VatHarnessEntity>();
        for (var corner : g.harnessCorners(p, O.getY() + 5)) {
            h.setBlock(corner, Content.state(VatPartType.Harness));
            hs.add((VatHarnessEntity) h.getBlockEntity(corner));
        }
        var d = O.offset(1, 0, 2);
        h.setBlock(d, Content.state(VatPartType.Sump));
        var a = new MobAssembly(hs, head, (VatSumpEntity) h.getBlockEntity(d));
        var min = h.absolutePos(O);
        var max = h.absolutePos(O.offset(6, 5, 6));
        var port = h.absolutePos(O.offset(3, 2, 6));
        h.assertTrue(
                a.bodyBounds().getXsize() == 2 && a.bodyBounds().getYsize() == 3,
                "Body bounds came from Pig type defaults instead of saved dimensions");
        var tool = VatInteractionGeometry.link(min, max, a, port, true, false);
        var feed = VatInteractionGeometry.link(min, max, a, port, true, true);
        h.assertTrue(
                a.attachmentBounds().contains(tool.target())
                        && a.attachmentBounds().contains(feed.target()),
                "Tool/feed endpoint missed resolved body");
        h.assertTrue(
                VatTopologyCompiler.targets(h.getLevel(), port, min, max, a, List.of(a)),
                "Blender/actor reach did not use resolved body");
        var baby = a.bodyBounds();
        head.setLifeStage(LifeStage.JUVENILE);
        h.assertTrue(a.attachmentBounds().getXsize() == baby.getXsize() / 2, "Resolved body lost juvenile scaling");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void interiorHarnessDrainAndSumpFormWithSharedPanelOrigins(GameTestHelper h) {
        var head = spacedRig(h, 1, 1, false);
        h.setBlock(O.offset(2, 5, 2), Content.state(VatPartType.Casing));
        h.setBlock(O.offset(2, 4, 2), Content.state(VatPartType.Harness));
        h.setBlock(O.offset(1, 0, 2), Content.state(VatPartType.Casing));
        h.setBlock(O.offset(2, 0, 2), Content.state(VatPartType.Casing));
        h.setBlock(O.offset(1, 1, 2), Content.state(VatPartType.Sump));
        h.setBlock(O.offset(2, 1, 2), net.minecraft.world.level.block.Blocks.AIR);
        h.runAtTickTime(35, () -> {
            h.assertTrue(head.isMachineAssembled(), "Interior semantic endpoints failed to form");
            var c = head.getMultiblockController().orElseThrow();
            var a = c.getAssemblies().getFirst();
            h.assertTrue(
                    a.harness().getWallPanelState().shell().outward(Direction.UP)
                            && a.drain().getWallPanelState().shell().outward(Direction.DOWN),
                    "Interior panels do not match deterministic origins");
            h.assertTrue(
                    c.getTopology().actors().stream().anyMatch(r -> r.use().equals(PassiveUses.DRAIN)),
                    "Interior Sump failed to reach selected Drain");
            h.succeed();
        });
    }
}
