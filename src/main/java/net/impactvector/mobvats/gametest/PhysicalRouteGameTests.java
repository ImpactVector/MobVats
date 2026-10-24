package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class PhysicalRouteGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            TOOL = O.offset(2, 1, 4),
            BLENDER = O.offset(2, 1, 0),
            FEED = O.offset(0, 1, 2);

    private static MultiblockVat vat(GameTestHelper h) {
        var controller = ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElse(null);
        h.assertTrue(controller != null, "Waiting for vat controller attachment");
        return controller;
    }

    private static void rig(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity) h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
    }

    private static void pipe(
            GameTestHelper h, BlockPos pos, ConduitResource resource, ConduitCladding cladding, ConduitMode mode) {
        h.setBlock(pos, ConduitContent.state(resource, cladding).setValue(ConduitBlock.MODE, mode));
    }

    private static boolean selected(GameTestHelper h, BlockPos pos) {
        return vat(h).getTopology().actors().stream().anyMatch(a -> a.actor().equals(h.absolutePos(pos)));
    }

    private static boolean blocked(GameTestHelper h, VatPhysicalState.Kind kind) {
        return vat(h).getTopology().physical().blocked().stream().anyMatch(b -> b.kind() == kind);
    }

    private static ConduitGeometry.Geometry isolated(
            BlockPos pos, ConduitCladding cladding, ConduitMode mode, BlockPos min, BlockPos max) {
        return ConduitGeometry.bake(new ConduitGeometry.Snapshot(
                min, max, List.of(new ConduitGeometry.Cell(pos, ConduitResource.ITEM, cladding, mode, 0, List.of()))));
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void narrowVolumesBlockOnlyActualPathsIncludingCladding(GameTestHelper h) {
        var pos = new BlockPos(0, 1, 2);
        var min = BlockPos.ZERO;
        var max = new BlockPos(4, 3, 4);
        for (var cladding : ConduitCladding.values()) {
            var geometry = isolated(pos, cladding, ConduitMode.TRUNK, min, max);
            var center = geometry.pipeSegments().getFirst().bounds().getCenter();
            var crossing = PhysicalRoute.line(center.add(-.4, 0, 0), center.add(.4, 0, 0), .02);
            var nearby = PhysicalRoute.line(center.add(-.4, 0, .3), center.add(.4, 0, .3), .02);
            h.assertTrue(
                    RouteIntersection.first(crossing, geometry, null).isPresent(),
                    "Narrow pipe failed to block crossing: " + cladding);
            h.assertTrue(
                    RouteIntersection.first(nearby, geometry, null).isEmpty(),
                    "Whole conduit cell used as obstruction: " + cladding);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void ownServiceFittingExceptionDoesNotIgnoreForeignGeometry(GameTestHelper h) {
        var endpointPos = new BlockPos(0, 1, 2);
        var node = endpointPos.above();
        var geometry = ConduitGeometry.bake(new ConduitGeometry.Snapshot(
                BlockPos.ZERO,
                new BlockPos(4, 3, 4),
                List.of(new ConduitGeometry.Cell(
                        node,
                        ConduitResource.ITEM,
                        ConduitCladding.GLASS,
                        ConduitMode.TAP,
                        0,
                        List.of(new ConduitGeometry.Attachment(endpointPos, EndpointGeometry.Kind.TOOL))))));
        var fitting = geometry.endpointFittings().getFirst();
        var anchor = fitting.endpoint().serviceConnectionAnchor();
        var throughFitting = PhysicalRoute.line(anchor, anchor.add(.04, 0, 0), .01);
        h.assertTrue(
                RouteIntersection.first(throughFitting, geometry, endpointPos).isEmpty(),
                "Own endpoint service fitting self-blocked");
        h.assertTrue(
                RouteIntersection.first(throughFitting, geometry, endpointPos.south())
                        .orElseThrow()
                        .fitting(),
                "Foreign endpoint fitting was globally ignored");
        var body = fitting.volumes().getLast();
        var foreignBody = new ConduitGeometry.Geometry(geometry.snapshot(), List.of(body), geometry.endpointFittings());
        h.assertTrue(
                RouteIntersection.first(throughFitting, foreignBody, endpointPos)
                        .isPresent(),
                "Own-endpoint exception ignored pipe bodies");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void toolBlockerAdditionRemovalAndDiagnostics(GameTestHelper h) {
        rig(h);
        var obstacle = O.offset(2, 1, 3);
        var nearby = O.offset(3, 1, 3);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(vat(h).isAssembled() && selected(h, TOOL), "Clear Tool route absent"))
                .thenExecute(() -> pipe(h, nearby, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK))
                .thenIdle(10)
                .thenWaitUntil(() ->
                        h.assertTrue(vat(h).isAssembled() && selected(h, TOOL), "Nearby narrow pipe blocked Tool"))
                .thenExecute(() -> pipe(h, obstacle, ConduitResource.FLUID, ConduitCladding.BARE, ConduitMode.TRUNK))
                .thenWaitUntil(() -> h.assertTrue(
                        vat(h).isAssembled() && !selected(h, TOOL) && blocked(h, VatPhysicalState.Kind.TOOL),
                        "Crossing pipe failed to disable Tool while vat formed"))
                .thenExecute(() -> {
                    var reason = vat(h).getTopology().physical().blocked().stream()
                            .filter(r -> r.kind() == VatPhysicalState.Kind.TOOL)
                            .findFirst()
                            .orElseThrow();
                    h.assertTrue(
                            reason.blocker().position().equals(h.absolutePos(obstacle))
                                    && reason.blocker().resource() == ConduitResource.FLUID,
                            "Blocker coordinates/type absent");
                    h.assertTrue(
                            vat(h).statusSnapshot()
                                    .toString()
                                    .contains("Fluid Conduit @ "
                                            + h.absolutePos(obstacle).toShortString()),
                            "Controller status omitted physical blocker");
                    h.assertTrue(
                            ((VatHeadEntity) h.getBlockEntity(HEAD))
                                    .presentation().tools().stream().noneMatch(VatPresentation.Link::assigned),
                            "Blocked Tool still animates as assigned");
                    h.setBlock(obstacle, Blocks.AIR);
                })
                .thenIdle(10)
                .thenWaitUntil(() -> h.assertTrue(
                        vat(h).isAssembled() && selected(h, TOOL) && !blocked(h, VatPhysicalState.Kind.TOOL),
                        "Removing blocker did not restore Tool"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blockedBlenderFallsBackToSwordAndRecoversPriority(GameTestHelper h) {
        rig(h);
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        h.setBlock(FEED, Content.state(VatPartType.FeedPort));
        h.setBlock(FEED.above(), Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(FEED.above())).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        var obstacle = O.offset(2, 1, 1);
        h.runAtTickTime(30, () -> {
            h.assertTrue(selected(h, BLENDER) && selected(h, TOOL), "Clear Blender did not win HARVEST");
            pipe(h, obstacle, ConduitResource.FLUID, ConduitCladding.BARE, ConduitMode.TRUNK);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(
                    !selected(h, BLENDER) && selected(h, TOOL) && blocked(h, VatPhysicalState.Kind.BLENDER),
                    "Blocked high-priority Blender suppressed clear Sword");
            seed(h, "READY", 1200, 0);
            h.setBlock(obstacle, Blocks.AIR);
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    selected(h, BLENDER) && selected(h, TOOL) && !blocked(h, VatPhysicalState.Kind.BLENDER),
                    "Clear Blender did not regain priority");
            seed(h, "READY", 1200, 0);
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(vat(h).meatAmount() > 0, "Restored Blender did not execute");
            h.succeed();
        });
    }

    private static void feedRig(GameTestHelper h) {
        rig(h);
        h.setBlock(HEAD, Content.state(VatPartType.Head).setValue(VatHeadBlock.FACING, Direction.WEST));
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        h.setBlock(FEED, Content.state(VatPartType.FeedPort));
        h.setBlock(FEED.above(), Content.state(VatPartType.ItemPort));
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void feedCurveNarrowClearanceBlockerAndRecovery(GameTestHelper h) {
        feedRig(h);
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        var low = O.offset(1, 1, 2);
        var high = low.above();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        vat(h).isAssembled()
                                && vat(h).getTopology().actors().stream()
                                        .anyMatch(r -> r.use().equals(PassiveUses.FEED)),
                        "Clear feed contact absent"))
                .thenExecute(() -> pipe(h, low, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK))
                .thenIdle(20)
                .thenWaitUntil(() -> h.assertTrue(
                        vat(h).isAssembled()
                                && vat(h).getTopology().actors().stream()
                                        .anyMatch(r -> r.use().equals(PassiveUses.FEED)),
                        "Same-cell nearby pipe blocked curved feed tube"))
                .thenExecute(() -> pipe(h, high, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK))
                .thenIdle(20)
                .thenWaitUntil(() -> h.assertTrue(
                        vat(h).isAssembled()
                                && vat(h).getTopology().actors().stream()
                                        .noneMatch(r -> r.use().equals(PassiveUses.FEED))
                                && blocked(h, VatPhysicalState.Kind.FEED),
                        "Vertical pipe did not block actual feed curve"))
                .thenExecute(() -> {
                    h.assertTrue(
                            ((VatHeadEntity) h.getBlockEntity(HEAD))
                                    .presentation()
                                    .feeds()
                                    .isEmpty(),
                            "Blocked feed still rendered as valid");
                    seed(h, "IDLE", 0, 0);
                    ((VatItemPortEntity) h.getBlockEntity(FEED.above()))
                            .staging()
                            .setStackInSlot(0, new ItemStack(Items.WHEAT, 10));
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(
                            vat(h).cowState(h.absolutePos(HEAD)).replacement().equals("IDLE")
                                    && ((VatItemPortEntity) h.getBlockEntity(FEED.above()))
                                                    .staging()
                                                    .getStackInSlot(0)
                                                    .getCount()
                                            == 10,
                            "Obstructed feed consumed wheat");
                    h.setBlock(high, Blocks.AIR);
                    h.setBlock(low, Blocks.AIR);
                })
                .thenIdle(20)
                .thenWaitUntil(() -> h.assertTrue(
                        vat(h).isAssembled()
                                && !blocked(h, VatPhysicalState.Kind.FEED)
                                && vat(h).cowState(h.absolutePos(HEAD))
                                        .replacement()
                                        .equals("GROWING"),
                        "Clear feed failed to resume"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void actualOwnToolFeedAndBlenderTapsStayClear(GameTestHelper h) {
        feedRig(h);
        h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        pipe(h, TOOL.above(), ConduitResource.ITEM, ConduitCladding.GLASS, ConduitMode.TAP);
        pipe(h, FEED.above(), ConduitResource.ITEM, ConduitCladding.GLASS, ConduitMode.TAP);
        pipe(h, BLENDER.above(), ConduitResource.FLUID, ConduitCladding.GLASS, ConduitMode.TAP);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.isAssembled() && c.getTopology().physical().blocked().isEmpty(),
                    "Own service taps blocked interaction paths");
            for (var pos : List.of(TOOL, FEED, BLENDER))
                h.assertTrue(
                        c.conduitGeometry().endpointFittings().stream()
                                .anyMatch(f -> f.endpoint().position().equals(h.absolutePos(pos))),
                        "Expected service fitting absent");
            h.assertTrue(selected(h, BLENDER) && selected(h, FEED), "Own fitting lost valid relationships");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 85)
    public static void tapModeChangeRebuildsPhysicalValidity(GameTestHelper h) {
        rig(h);
        var node = O.offset(1, 1, 3);
        var endpoint = node.below();
        h.setBlock(endpoint, Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(endpoint)).setIoDirection(IoDirection.Output);
        pipe(h, node, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
        var probe = new PhysicalRoute[1];
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            long revision = c.getTopologyRevision();
            var before = c.getTopology().physical();
            var anchor = EndpointGeometry.from(
                            h.absolutePos(endpoint),
                            c.getBoundingBox().getMin(),
                            c.getBoundingBox().getMax(),
                            h.absolutePos(node),
                            EndpointGeometry.Kind.PASSIVE)
                    .serviceConnectionAnchor();
            probe[0] = PhysicalRoute.line(anchor, anchor.add(0, .04, 0), .01);
            h.assertTrue(
                    RouteIntersection.first(probe[0], c.conduitGeometry(), null).isEmpty(),
                    "TRUNK unexpectedly obstructed service-fitting probe");
            pipe(h, node, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TAP);
            h.runAtTickTime(50, () -> {
                h.assertTrue(
                        c.getTopologyRevision() > revision && c.getTopology().physical() != before,
                        "TAP mode did not rebuild physical state");
                h.assertTrue(
                        c.conduitGeometry().endpointFittings().stream()
                                .anyMatch(f -> f.endpoint().position().equals(h.absolutePos(endpoint))),
                        "TAP fitting not included");
                h.assertTrue(
                        RouteIntersection.first(probe[0], c.conduitGeometry(), null)
                                .orElseThrow()
                                .fitting(),
                        "New TAP fitting did not change physical obstruction");
                pipe(h, node, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
            });
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(
                    vat(h).conduitGeometry().endpointFittings().isEmpty()
                            && RouteIntersection.first(probe[0], vat(h).conduitGeometry(), null)
                                    .isEmpty()
                            && selected(h, TOOL),
                    "TRUNK retained stale fitting or rejected clear Sword");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void harnessSharedAnchorsAndNarrowFixtureObstruction(GameTestHelper h) {
        rig(h);
        pipe(h, O.offset(1, 2, 2), ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(c.getTopology().operational(h.absolutePos(HEAD)), "Nearby world pipe blocked required straps");
            for (var cladding : ConduitCladding.values()) {
                var blockedGeometry = isolated(
                        h.absolutePos(HEAD),
                        cladding,
                        ConduitMode.TRUNK,
                        c.getBoundingBox().getMin(),
                        c.getBoundingBox().getMax());
                var physical = VatPhysicalState.harnesses(c.getAssemblies(), blockedGeometry);
                h.assertTrue(
                        !physical.operational(h.absolutePos(HEAD))
                                && physical.blocked().getFirst().kind() == VatPhysicalState.Kind.HARNESS,
                        "Pipe crossing a rendered strap did not block harness: " + cladding);
                var nearbyGeometry = isolated(
                        h.absolutePos(O.offset(1, 2, 2)),
                        cladding,
                        ConduitMode.TRUNK,
                        c.getBoundingBox().getMin(),
                        c.getBoundingBox().getMax());
                h.assertTrue(
                        VatPhysicalState.harnesses(c.getAssemblies(), nearbyGeometry)
                                .operational(h.absolutePos(HEAD)),
                        "Nearby narrow pipe blocked straps: " + cladding);
            }
            h.assertTrue(
                    VatPhysicalState.harnesses(c.getAssemblies(), ConduitGeometry.Geometry.EMPTY)
                            .operational(h.absolutePos(HEAD)),
                    "Clearing shared geometry did not restore harness");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 85)
    public static void headFacingChangeRebuildsFeedObstruction(GameTestHelper h) {
        feedRig(h);
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        var low = O.offset(1, 1, 2);
        var previousRevision = new java.util.concurrent.atomic.AtomicLong();
        var previousTarget = new java.util.concurrent.atomic.AtomicReference<Vec3>();
        pipe(h, low, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
        pipe(h, low.above(), ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK);
        h.runAtTickTime(35, () -> {
            h.assertTrue(blocked(h, VatPhysicalState.Kind.FEED), "West-facing feed fixture not blocked");
            var c = vat(h);
            previousRevision.set(c.getTopologyRevision());
            previousTarget.set(VatInteractionGeometry.link(
                            c.getBoundingBox().getMin(),
                            c.getBoundingBox().getMax(),
                            c.getAssemblies().getFirst(),
                            h.absolutePos(FEED),
                            true,
                            true)
                    .target());
            h.setBlock(HEAD, h.getBlockState(HEAD).setValue(VatHeadBlock.FACING, Direction.NORTH));
        });
        h.runAtTickTime(65, () -> {
            var c = vat(h);
            var link = VatInteractionGeometry.link(
                    c.getBoundingBox().getMin(),
                    c.getBoundingBox().getMax(),
                    c.getAssemblies().getFirst(),
                    h.absolutePos(FEED),
                    true,
                    true);
            h.assertTrue(
                    c.isAssembled()
                            && c.getTopologyRevision() > previousRevision.get()
                            && !link.target().equals(previousTarget.get()),
                    "Head-facing change did not recompile the changed feed target");
            var hit = RouteIntersection.first(
                    VatInteractionGeometry.route(link, true),
                    c.getTopology().physical().geometry(),
                    h.absolutePos(FEED));
            h.assertTrue(
                    blocked(h, VatPhysicalState.Kind.FEED) == hit.isPresent(),
                    "Cached obstruction differs from the new facing's physical route");
            h.succeed();
        });
    }
    /** Seed a cached obstruction fixture: a conduit cannot occupy the mandatory Head block in a valid world. */
    private static void harnessFixture(GameTestHelper h) {
        var c = vat(h);
        // Reconcile loaded lifecycle/profile state before installing synthetic physical geometry.
        c.processEngine().assembled();
        var t = c.getTopology();
        var geometry = isolated(
                ((VatHeadEntity) h.getBlockEntity(HEAD)).lifeStage() == LifeStage.JUVENILE
                        ? h.absolutePos(HEAD).below()
                        : h.absolutePos(HEAD),
                ConduitCladding.BARE,
                ConduitMode.TRUNK,
                c.getBoundingBox().getMin(),
                c.getBoundingBox().getMax());
        var state = VatPhysicalState.harnesses(c.getAssemblies(), geometry);
        h.assertTrue(!state.operational(h.absolutePos(HEAD)), "Harness fixture did not cross strap");
        try {
            var field = MultiblockVat.class.getDeclaredField("topology");
            field.setAccessible(true);
            field.set(
                    c,
                    new VatTopology(
                            t.assemblies(),
                            t.itemInputs(),
                            t.itemOutputs(),
                            t.fluidInputs(),
                            t.fluidOutputs(),
                            t.actors(),
                            t.itemContacts(),
                            t.toolLoading(),
                            t.reservedItemOutputs(),
                            t.genericItemOutputs(),
                            t.conduits(),
                            state));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        ((VatHeadEntity) h.getBlockEntity(HEAD))
                .setPresentation(VatPresentation.compile(
                        c, c.getAssemblies().getFirst(), List.of((AbstractVatEntity) h.getBlockEntity(TOOL))));
    }

    private static void seed(GameTestHelper h, String cycle, int progress, int milkTicks) {
        var c = vat(h);
        var data = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        var specimen = new SpecimenProcessState();
        specimen.stage(cycle.equals("GROWING") ? LifeStage.JUVENILE : LifeStage.ADULT);
        specimen.growth().elapsed = cycle.equals("GROWING") ? progress : 0;
        specimen.growth().phase = cycle.equals("GROWING") ? ProcessState.Phase.RUNNING : ProcessState.Phase.IDLE;
        var milk = specimen.process(PassiveProcessCompiler.MILK);
        milk.elapsed = milkTicks;
        milk.phase = milkTicks >= 1200 ? ProcessState.Phase.READY : ProcessState.Phase.RUNNING;
        var states = new ListTag();
        var entry = new CompoundTag();
        entry.putLong("head", h.absolutePos(HEAD).asLong());
        entry.put("processes", specimen.save(h.getLevel().registryAccess()));
        states.add(entry);
        data.put("specimenProcesses", states);
        c.syncDataFrom(data, h.getLevel().registryAccess(), SyncReason.FullSync);
        c.processEngine().assembled();
        if (cycle.equals("READY")
                && h.getBlockEntity(FEED) instanceof VatItemPortEntity feed
                && feed.staging().getStackInSlot(0).isEmpty())
            feed.staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
    }
    // Registry reload tests invalidate synthetic topology, so this fixture needs an isolated batch.
    @GameTest(template = "empty", batch = "physicalHarnessFixture", timeoutTicks = 260)
    public static void cachedHarnessBlockPausesAllProcessesAndRestoresProgress(GameTestHelper h) {
        feedRig(h);
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        h.runAtTickTime(30, () -> {
            seed(h, "GROWING", 450, CowBalance.MILK_INTERVAL - 1);
            harnessFixture(h);
        });
        h.runAtTickTime(55, () -> {
            var c = vat(h);
            var state = c.cowState(h.absolutePos(HEAD));
            h.assertTrue(
                    c.isAssembled()
                            && state.replacementTicks() == 450
                            && state.milkTicks() == CowBalance.MILK_INTERVAL - 1
                            && c.milkAmount() == 0,
                    "Harness-blocked specimen progressed growth/milk");
            h.assertTrue(
                    !((VatHeadEntity) h.getBlockEntity(HEAD)).presentation().operational(),
                    "Client presentation omitted inactive specimen state");
            seed(h, "IDLE", 0, CowBalance.MILK_INTERVAL);
            c.fluidHandler(IoDirection.Input)
                    .fill(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 250), FluidAction.EXECUTE);
            ((VatItemPortEntity) h.getBlockEntity(FEED.above()))
                    .staging()
                    .setStackInSlot(0, new ItemStack(Items.WHEAT, 10));
            harnessFixture(h);
        });
        h.runAtTickTime(80, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.cowState(h.absolutePos(HEAD)).replacement().equals("IDLE")
                            && c.feedingAmount() == 250
                            && c.milkAmount() == 0
                            && ((VatItemPortEntity) h.getBlockEntity(FEED.above()))
                                            .staging()
                                            .getStackInSlot(0)
                                            .getCount()
                                    == 10,
                    "Inactive harness consumed feed/slurry or produced milk");
            seed(h, "READY", 1200, CowBalance.MILK_INTERVAL);
            harnessFixture(h);
        });
        h.runAtTickTime(105, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.cowState(h.absolutePos(HEAD)).replacement().equals("IDLE")
                            && c.getExperienceMb() == 0
                            && c.killOutputItemCount() == 0
                            && ((VatToolPortEntity) h.getBlockEntity(TOOL))
                                            .heldItem()
                                            .getDamageValue()
                                    == 0,
                    "Inactive harness harvested READY specimen");
            c.rebuildTopology();
        });
        h.startSequence()
                .thenIdle(140)
                .thenWaitUntil(() -> {
                    var c = vat(h);
                    // A native 1-XP cow reward can yield zero whole XP at low happiness.
                    // The committed harvest counter verifies resumption independently of reward RNG.
                    h.assertTrue(
                            c.getTopology().operational(h.absolutePos(HEAD))
                                    && c.metrics().total(MachineMetrics.HARVESTS) > 0
                                    && c.milkAmount() > 0
                                    && ((VatToolPortEntity) h.getBlockEntity(TOOL))
                                                    .heldItem()
                                                    .getDamageValue()
                                            > 0,
                            "Removing cached harness obstruction failed to restore operations: operational="
                                    + c.getTopology().operational(h.absolutePos(HEAD)) + " harvests="
                                    + c.metrics().total(MachineMetrics.HARVESTS) + " milk=" + c.milkAmount()
                                    + " swordDamage="
                                    + ((VatToolPortEntity) h.getBlockEntity(TOOL))
                                            .heldItem()
                                            .getDamageValue());
                })
                .thenExecute(() -> {
                    var bounds = vat(h).getBoundingBox();
                    for (var pos : bounds) h.getLevel().removeBlock(pos, false);
                })
                .thenSucceed();
    }
}
