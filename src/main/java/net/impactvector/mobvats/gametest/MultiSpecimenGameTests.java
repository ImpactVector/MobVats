package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDescriptor;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Real assembled multi-Head fixtures, with transactions and lifecycle driven autonomously. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MultiSpecimenGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), A = O.offset(2, 2, 2);

    private static BlockPos b(int distance) {
        return A.east(distance);
    }

    private static VatHeadEntity head(GameTestHelper h, BlockPos p) {
        return (VatHeadEntity) h.getBlockEntity(p);
    }

    private static MultiblockVat vat(GameTestHelper h) {
        var c = head(h, A).getMultiblockController().orElseThrow();
        h.assertTrue(
                c.isAssembled(),
                "Multi-specimen fixture unformed: "
                        + c.getLastError()
                                .map(e -> e.getChatMessage().getString())
                                .orElse("no error"));
        return c;
    }

    private static void specimen(GameTestHelper h, BlockPos p, ResourceLocation identity) {
        var g = SimulacrumGeometry.resolve(.9, 1.4, .9, Direction.NORTH, null);
        h.setBlock(p, Content.state(VatPartType.Head));
        h.setBlock(p.below(), Content.state(VatPartType.Flesh));
        head(h, p).encode(identity, g);
        h.setBlock(p.above(), Content.state(VatPartType.Harness));
        h.setBlock(p.below(2), Content.state(VatPartType.Sump));
    }

    private static void rig(GameTestHelper h, int distance) {
        for (int x = 0; x < 9; x++)
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < 5; z++)
                    if (x == 0 || x == 8 || y == 0 || y == 3 || z == 0 || z == 4)
                        h.setBlock(
                                O.offset(x, y, z),
                                Content.state(y == 0 || y == 3 ? VatPartType.Casing : VatPartType.Glass));
        h.setBlock(O.offset(1, 1, 0), Content.state(VatPartType.Controller));
        specimen(h, A, ItemEssence.COW);
        specimen(h, b(distance), ItemEssence.COW);
        Slice11Fixtures.vatStorage(h, O, 9, 5);
    }

    private static BlockPos toolPos(BlockPos p) {
        return new BlockPos(p.getX(), O.getY() + 1, O.getZ() + 4);
    }

    private static BlockPos feedPos(BlockPos p) {
        return new BlockPos(p.getX(), O.getY() + 1, O.getZ());
    }

    private static VatToolPortEntity tool(GameTestHelper h, BlockPos p) {
        return (VatToolPortEntity) h.getBlockEntity(toolPos(p));
    }

    private static void sword(GameTestHelper h, BlockPos p) {
        h.setBlock(toolPos(p), Content.state(VatPartType.ToolPort));
        tool(h, p).setHeldItem(new ItemStack(Items.IRON_SWORD));
    }

    private static void blender(GameTestHelper h, BlockPos p) {
        h.setBlock(toolPos(p), Content.state(VatPartType.Blender));
    }

    private static VatItemPortEntity feed(GameTestHelper h, BlockPos p) {
        return (VatItemPortEntity) h.getBlockEntity(feedPos(p));
    }

    private static void input(GameTestHelper h, BlockPos p, int count) {
        h.setBlock(feedPos(p), Content.state(VatPartType.ItemPort));
        feed(h, p).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, count));
        Slice11Fixtures.feedFrom(h, feedPos(p), feedPos(p).south());
    }

    private static SpecimenProcessState state(GameTestHelper h, BlockPos p) {
        return vat(h).processEngine().state(h.absolutePos(p));
    }

    private static void cycles(VatProcessEngine engine, int n) {
        for (int i = 0; i < n * VatProcessEngine.CADENCE; i++) engine.tick();
    }

    private static void ready(SpecimenProcessState s, String id, int duration) {
        var p = s.process(id);
        p.elapsed = duration;
        p.phase = ProcessState.Phase.READY;
    }

    private static void adult(GameTestHelper h, BlockPos p) {
        state(h, p).stage(LifeStage.ADULT);
        head(h, p).setLifeStage(LifeStage.ADULT);
    }

    private static void fill(MultiblockVat c, net.minecraft.world.level.material.Fluid fluid, int amount) {
        c.fluidHandler(IoDirection.Input).fill(new FluidStack(fluid, amount), FluidAction.EXECUTE);
    }

    private static final BlockPos SHARED_INPUT = O.offset(3, 0, 3);

    private static void sharedToolRig(GameTestHelper h) {
        rig(h, 3);
        input(h, A, 0);
        input(h, b(3), 0);
        for (var p : List.of(A, b(3))) h.setBlock(toolPos(p), Content.state(VatPartType.ToolPort));
        h.setBlock(SHARED_INPUT, Content.state(VatPartType.ItemPort));
        for (int x = 2; x <= 5; x++)
            h.setBlock(
                    O.offset(x, 0, 4),
                    ConduitContent.state(ConduitResource.ITEM, ConduitCladding.CASING)
                            .setValue(
                                    ConduitBlock.MODE,
                                    x == 2 || x == 3 || x == 5 ? ConduitMode.TAP : ConduitMode.TRUNK));
    }

    private static void rejects(GameTestHelper h, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        h.fail(message);
    }

    private static void spacing(GameTestHelper h, int distance) {
        rig(h, distance);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var assemblies = c.getAssemblies();
            h.assertTrue(
                    assemblies.size() == 2
                            && assemblies.getFirst().head() == head(h, A)
                            && assemblies.getLast().head() == head(h, b(distance)),
                    "Discovery did not return two sorted assemblies");
            h.assertTrue(
                    assemblies.getFirst().drain() != assemblies.getLast().drain()
                            && Collections.disjoint(
                                    assemblies.getFirst().harnessPositions(),
                                    assemblies.getLast().harnessPositions()),
                    "Support ownership shared");
            var ca = assemblies.getFirst().clearance(h.getLevel());
            var cb = assemblies.getLast().clearance(h.getLevel());
            h.assertTrue(
                    (distance == 1) == !ca.neighboringBodies().isEmpty()
                            && (distance == 1) == !cb.neighboringBodies().isEmpty(),
                    "Comfort shells missed neighboring bodies");
            h.assertTrue((distance <= 2) == !ca.sharedShell(cb).isEmpty(), "Spacer shell intersection incorrect");
            h.assertTrue(
                    c.frequencyMultiplier(c.getAssemblies().getFirst().head().getBlockPos()) == 0.5,
                    "Spacing changed productivity before Slice 11");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void touchingSpecimensForm(GameTestHelper h) {
        spacing(h, 1);
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void oneGapSpecimensForm(GameTestHelper h) {
        spacing(h, 2);
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void twoGapSpecimensForm(GameTestHelper h) {
        spacing(h, 3);
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void independentStagesTimersDescriptorsAndStatus(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var a = state(h, A);
            var b = state(h, b(3));
            h.assertTrue(a != b, "Shared specimen state");
            a.stage(LifeStage.JUVENILE);
            a.growth().start();
            a.growth().elapsed = 137;
            a.process(PassiveProcessCompiler.MILK).elapsed = 500;
            b.process(PassiveProcessCompiler.MILK).elapsed = 700;
            c.processEngine().assembled();
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    a.growth().elapsed == 142 && b.growth().elapsed == 0 && b.stage() == LifeStage.ADULT,
                    "Growth or stage leaked to B");
            h.assertTrue(
                    a.process(PassiveProcessCompiler.MILK).elapsed == 500
                            && b.process(PassiveProcessCompiler.MILK).elapsed == 705,
                    "Milk timers not independent");
            h.assertTrue(
                    head(h, A).lifeStage() == LifeStage.JUVENILE
                            && head(h, b(3)).lifeStage() == LifeStage.ADULT,
                    "Head stage transport collapsed");
            var da = VatRenderDescriptor.from(c, head(h, A));
            var db = VatRenderDescriptor.from(c, head(h, b(3)));
            h.assertTrue(
                    da.vatBounds().equals(db.vatBounds())
                            && !da.specimenPosition().equals(db.specimenPosition())
                            && !da.harnessPositions().equals(db.harnessPositions())
                            && !da.attachmentBounds().equals(db.attachmentBounds()),
                    "Per-Head render descriptors collapsed");
            var text = c.statusSnapshot().toString();
            h.assertTrue(
                    text.contains("Simulacra: 2")
                            && text.contains("Stage: Juvenile")
                            && text.contains("Stage: Adult")
                            && text.contains(h.absolutePos(b(3)).toShortString()),
                    "Status omitted independent specimens");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void twoAdultDairyCowsShareMilkAndJuvenilePauses(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            ready(state(h, A), PassiveProcessCompiler.MILK, 1200);
            ready(state(h, b(3)), PassiveProcessCompiler.MILK, 1200);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    c.milkAmount()
                            == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(A)))
                                    + (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(b(3)))),
                    "Two dairy outputs did not enter shared buffer: " + c.milkAmount() + " clusters="
                            + c.storage().clusters() + " routes="
                            + c.getTopology().actors() + " status="
                            + c.processEngine().status(h.absolutePos(A)));
            state(h, A).stage(LifeStage.JUVENILE);
            ready(state(h, A), PassiveProcessCompiler.MILK, 1200);
            ready(state(h, b(3)), PassiveProcessCompiler.MILK, 1200);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    c.milkAmount()
                                    == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(A)))
                                            + (int) Math.floor(2000 * c.yieldMultiplier(h.absolutePos(b(3))))
                            && state(h, A).process(PassiveProcessCompiler.MILK).phase == ProcessState.Phase.READY,
                    "Juvenile milk pause affected adult B");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void swordBlenderAndUnclaimedSpecimenRemainIndependent(GameTestHelper h) {
        rig(h, 3);
        specimen(h, A.east(5), ItemEssence.COW);
        sword(h, A);
        blender(h, b(3));
        input(h, A, 1);
        input(h, b(3), 1);
        input(h, A.east(5), 2);
        h.runAtTickTime(60, () -> {
            var c = vat(h);
            h.assertTrue(c.getAssemblies().size() == 3, "Third specimen missing");
            h.assertTrue(
                    state(h, A).stage() == LifeStage.JUVENILE
                            && state(h, b(3)).stage() == LifeStage.JUVENILE
                            && state(h, A.east(5)).stage() == LifeStage.ADULT,
                    "Harvest lifecycle crossed specimens");
            h.assertTrue(
                    !tool(h, A).results().isEmpty()
                            && c.meatAmount() > 0
                            && tool(h, A).heldItem().getDamageValue() == 2,
                    "Sword/Blender outputs not independent");
            h.assertTrue(
                    feed(h, A.east(5)).staging().getStackInSlot(0).getCount() == 2, "Unclaimed specimen consumed feed");
            h.assertTrue(
                    c.getTopology().actors().stream()
                                    .anyMatch(r -> r.actor().equals(h.absolutePos(toolPos(A)))
                                            && r.head().equals(h.absolutePos(A)))
                            && c.getTopology().actors().stream()
                                    .anyMatch(r -> r.actor().equals(h.absolutePos(toolPos(b(3))))
                                            && r.head().equals(h.absolutePos(b(3)))),
                    "Actor assignments crossed specimens");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 90)
    public static void sharedSwordBacklogBlocksAllSwordsAndResumesAfterDrain(GameTestHelper h) {
        rig(h, 3);
        sword(h, A);
        sword(h, b(3));
        input(h, A, 1);
        input(h, b(3), 1);
        h.runAtTickTime(60, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.metrics().total(MachineMetrics.HARVESTS) == 2
                            && !tool(h, A).results().isEmpty()
                            && !tool(h, b(3)).results().isEmpty(),
                    "One Tool backlog suppressed independent Sword");
            var before = tool(h, b(3)).results().itemCount();
            tool(h, A).results().clear();
            h.assertTrue(tool(h, b(3)).results().itemCount() == before, "Local drain cleared another Tool's batch");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void blenderProceedsWithExistingSwordBacklog(GameTestHelper h) {
        rig(h, 3);
        sword(h, A);
        blender(h, b(3));
        input(h, A, 0);
        input(h, b(3), 0);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            tool(h, A).results().store(List.of(new ItemStack(Items.LEATHER, 2)));
            fill(c, ProductFluids.FEEDING_SLURRY.get(), 500);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    state(h, A).stage() == LifeStage.ADULT
                            && tool(h, A).heldItem().getDamageValue() == 0
                            && state(h, b(3)).stage() == LifeStage.JUVENILE
                            && c.meatAmount() > 0
                            && c.feedingAmount() == 250
                            && c.killOutputItemCount() == 2,
                    "Sword backlog blocked Blender or consumed blocked Sword feed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void scarceFeedingSlurryRotatesAcrossCadences(GameTestHelper h) {
        rig(h, 3);
        blender(h, A);
        blender(h, b(3));
        input(h, A, 0);
        input(h, b(3), 0);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var winners = new ArrayList<BlockPos>();
            for (int i = 0; i < 6; i++) {
                adult(h, A);
                adult(h, b(3));
                c.processFluids().buffer(ProductBuffers.MEAT).setFluid(FluidStack.EMPTY);
                fill(c, ProductFluids.FEEDING_SLURRY.get(), 250);
                cycles(c.processEngine(), 1);
                boolean first = state(h, A).stage() == LifeStage.JUVENILE,
                        second = state(h, b(3)).stage() == LifeStage.JUVENILE;
                h.assertTrue(
                        first != second && c.feedingAmount() == 0, "One dose accelerated more than one replacement");
                winners.add(first ? A : b(3));
            }
            for (int i = 1; i < winners.size(); i++)
                h.assertTrue(!winners.get(i).equals(winners.get(i - 1)), "Scarce Slurry repeatedly starved later Head");
            h.assertTrue(
                    c.metrics().total("feedingConsumed") == 1500,
                    "Feeding charges did not scale with committed specimens");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void directFeedContactsDoNotBecomeGlobal(GameTestHelper h) {
        rig(h, 5);
        blender(h, A);
        blender(h, b(5));
        input(h, A, 2);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(
                    state(h, A).stage() == LifeStage.JUVENILE
                            && state(h, b(5)).stage() == LifeStage.ADULT
                            && feed(h, A).staging().getStackInSlot(0).getCount() == 1,
                    "Direct feed reached unrelated specimen");
            h.assertTrue(
                    c.getTopology().actors().stream()
                            .filter(r -> r.use().equals(PassiveUses.FEED))
                            .allMatch(r -> r.head().equals(h.absolutePos(A))),
                    "Compiled contact leaked to B");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void storedCultureDoesNotAccelerateSpecimens(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var a = state(h, A);
            var b = state(h, b(3));
            a.process(PassiveProcessCompiler.MILK).start();
            a.process(PassiveProcessCompiler.MILK).elapsed = 0;
            b.process(PassiveProcessCompiler.MILK).start();
            b.process(PassiveProcessCompiler.MILK).elapsed = 0;
            fill(c, ProductFluids.CULTURE_SLURRY.get(), 10);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    a.process(PassiveProcessCompiler.MILK).elapsed == 5
                            && b.process(PassiveProcessCompiler.MILK).elapsed == 5,
                    "Dry culture must progress at 0.5x");
            h.assertTrue(
                    c.metrics().total("cultureConsumed") == 0
                            && c.cultureAmount() == 10
                            && c.medium().isEmpty(),
                    "Buffer culture was consumed or teleported");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void legacyCultureCreditsAreRetiredOnReload(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            fill(c, ProductFluids.CULTURE_SLURRY.get(), 100);
            var saved = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            saved.putInt("cultureCredit", 1000);
            c.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    c.cultureAmount() == 100
                            && c.frequencyMultiplier(h.absolutePos(A)) == 0.5
                            && c.metrics().total("cultureConsumed") == 0,
                    "Legacy credits affected speed or consumed fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void wasteServiceIsLocalAndBuffersShared(GameTestHelper h) {
        rig(h, 3);
        h.setBlock(A.below(2).south(), Content.state(VatPartType.Casing));
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            ready(state(h, A), PassiveProcessCompiler.WASTE, 1200);
            ready(state(h, b(3)), PassiveProcessCompiler.WASTE, 1200);
            cycles(c.processEngine(), 1);
            int amount = PassiveMobDefinitionRegistry.resolve(ItemEssence.COW).secondary_outputs().stream()
                    .filter(o -> o.process().equals(PassiveProcessCompiler.WASTE))
                    .findFirst()
                    .orElseThrow()
                    .amount();
            h.assertTrue(
                    c.wasteAmount()
                                    + ((VatSumpEntity) h.getBlockEntity(A.below(2)))
                                            .service()
                                            .tank(new FluidStack(ProductFluids.WASTE.get(), 1))
                                            .getFluidAmount()
                                    + ((VatSumpEntity) h.getBlockEntity(b(3).below(2)))
                                            .service()
                                            .tank(new FluidStack(ProductFluids.WASTE.get(), 1))
                                            .getFluidAmount()
                            == (int) Math.floor(amount * c.yieldMultiplier(h.absolutePos(A)))
                                    + (int) Math.floor(amount * c.yieldMultiplier(h.absolutePos(b(3)))),
                    "Passive production still required an auxiliary actor");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void bothSumpsProduceSharedWaste(GameTestHelper h) {
        rig(h, 3);
        for (var p : List.of(A, b(3))) h.setBlock(p.below(2).south(), Content.state(VatPartType.Casing));
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            for (var p : List.of(A, b(3))) ready(state(h, p), PassiveProcessCompiler.WASTE, 1200);
            cycles(c.processEngine(), 1);
            int amount = PassiveMobDefinitionRegistry.resolve(ItemEssence.COW).secondary_outputs().stream()
                    .filter(o -> o.process().equals(PassiveProcessCompiler.WASTE))
                    .findFirst()
                    .orElseThrow()
                    .amount();
            h.assertTrue(
                    c.wasteAmount()
                                    == (int) Math.floor(amount * c.yieldMultiplier(h.absolutePos(A)))
                                            + (int) Math.floor(amount * c.yieldMultiplier(h.absolutePos(b(3))))
                            && c.getTopology().actors().stream()
                                            .filter(r -> r.use().equals(PassiveUses.DRAIN)
                                                    && h.getLevel().getBlockEntity(r.actor()) instanceof VatSumpEntity)
                                            .count()
                                    == 2,
                    "Independent Sumps did not share configured Waste buffer");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void outputCapacityFailureRetainsEachSpecimenState(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            c.processFluids().buffer(ProductBuffers.MILK).setFluid(new FluidStack(NeoForgeMod.MILK.get(), 3500));
            for (var assembly : c.getAssemblies())
                assembly.drain()
                        .service()
                        .tank(new FluidStack(NeoForgeMod.MILK.get(), 1))
                        .fill(new FluidStack(NeoForgeMod.MILK.get(), 2000), FluidAction.EXECUTE);
            ready(state(h, A), PassiveProcessCompiler.MILK, 1200);
            ready(state(h, b(3)), PassiveProcessCompiler.MILK, 1200);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    c.milkAmount() == 3500
                            && state(h, A).process(PassiveProcessCompiler.MILK).phase == ProcessState.Phase.READY
                            && state(h, b(3)).process(PassiveProcessCompiler.MILK).phase == ProcessState.Phase.READY,
                    "Capacity failure lost per-specimen pending work");
            c.processFluids().buffer(ProductBuffers.MILK).drain(3000, FluidAction.EXECUTE);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    c.milkAmount()
                            == 500
                                    + (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(A)))
                                    + (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(b(3)))),
                    "Both outputs failed to recover atomically");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void multipleStateBuffersAndCursorSurviveReload(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            state(h, A).stage(LifeStage.JUVENILE);
            state(h, A).growth().elapsed = 123;
            state(h, b(3)).process(PassiveProcessCompiler.MILK).elapsed = 456;
            state(h, A).process("test:pending").pending =
                    new ProcessState.PreparedOutput(List.of(new ItemStack(Items.LEATHER, 3)), 7);
            fill(c, NeoForgeMod.MILK.get(), 2000);
            fill(c, ProductFluids.FEEDING_SLURRY.get(), 500);
            cycles(c.processEngine(), 1);
            var saved = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            var next = c.processEngine().nextHead();
            var expectedA = state(h, A).save(h.getLevel().registryAccess());
            var expectedB = state(h, b(3)).save(h.getLevel().registryAccess());
            c.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            c.processEngine().assembled();
            h.assertTrue(
                    expectedA.equals(state(h, A).save(h.getLevel().registryAccess()))
                            && expectedB.equals(state(h, b(3)).save(h.getLevel().registryAccess()))
                            && next.equals(c.processEngine().nextHead())
                            && c.milkAmount() == 2000
                            && c.feedingAmount() == 500,
                    "Multi-state/buffer/cursor reload lost state");
            h.assertTrue(
                    c.getAssemblies().size() == 2
                            && head(h, A).lifeStage() == LifeStage.JUVENILE
                            && head(h, b(3)).lifeStage() == LifeStage.ADULT,
                    "Reload transport lost stages");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 110)
    public static void removingAndAddingSpecimenPreservesSurvivor(GameTestHelper h) {
        rig(h, 3);
        final int[] progress = {0};
        h.runAtTickTime(35, () -> {
            state(h, b(3)).process("test:retained").elapsed = 761;
            h.setBlock(A, Blocks.AIR);
            h.setBlock(A.below(), Blocks.AIR);
            h.setBlock(A.above(), Content.state(VatPartType.Casing));
            h.setBlock(A.below(2), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(65, () -> {
            var c = head(h, b(3)).getMultiblockController().orElseThrow();
            h.assertTrue(
                    c.isAssembled()
                            && c.getAssemblies().size() == 1
                            && c.processEngine().state(h.absolutePos(A)) == null
                            && c.processEngine().state(h.absolutePos(b(3))).process("test:retained").elapsed == 761,
                    "Removed Head state corrupted survivor");
            progress[0] = c.processEngine().state(h.absolutePos(b(3))).process(PassiveProcessCompiler.MILK).elapsed;
            specimen(h, A, ItemEssence.COW);
        });
        h.runAtTickTime(95, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.getAssemblies().size() == 2
                            && state(h, A).process("test:retained").elapsed == 0
                            && state(h, b(3)).process("test:retained").elapsed == 761
                            && state(h, b(3)).process(PassiveProcessCompiler.MILK).elapsed > progress[0],
                    "New Head inherited removed state or survivor stopped");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 110)
    public static void samePositionReplacementDoesNotInheritRemovedState(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            state(h, A).stage(LifeStage.JUVENILE);
            state(h, A).growth().elapsed = 901;
            state(h, A).process("test:old").elapsed = 39;
            state(h, b(3)).process("test:retained").elapsed = 71;
            h.setBlock(A, Blocks.AIR);
            h.setBlock(A, Content.state(VatPartType.Head));
            head(h, A).encode(ItemEssence.COW);
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(
                    vat(h).getAssemblies().size() == 2
                            && state(h, A).stage() == LifeStage.ADULT
                            && state(h, A).growth().elapsed == 0
                            && state(h, A).process("test:old").elapsed == 0
                            && state(h, b(3)).process("test:retained").elapsed == 71,
                    "Immediate replacement inherited old Head state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 110)
    public static void breakReformRetainsBothStatesAndGeometry(GameTestHelper h) {
        rig(h, 3);
        var ga = head(h, A).geometry();
        var gb = head(h, b(3)).geometry();
        h.runAtTickTime(35, () -> {
            state(h, A).process("test:retained").elapsed = 31;
            state(h, b(3)).process("test:retained").elapsed = 47;
            h.setBlock(O.offset(0, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(!head(h, A).isMachineAssembled(), "Broken shell stayed formed");
            h.setBlock(O.offset(0, 3, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(
                    vat(h).getAssemblies().size() == 2
                            && state(h, A).process("test:retained").elapsed == 31
                            && state(h, b(3)).process("test:retained").elapsed == 47
                            && ga.equals(head(h, A).geometry())
                            && gb.equals(head(h, b(3)).geometry()),
                    "Reformation duplicated or lost Head state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Mixed", timeoutTicks = 65)
    public static void mixedEntityProfilesUnionBuffers(GameTestHelper h) {
        var original = new PassiveMobDefinitionRegistry.Loaded(
                PassiveMobDefinitionRegistry.definitions(), Map.of(), List.of());
        var defaults = new HashMap<ResourceLocation, PassiveMobDefinition>();
        PassiveDefaults.data().forEach((id, d) -> defaults.put(ResourceLocation.parse(id), d));
        PassiveMobDefinitionRegistry.publish(new PassiveMobDefinitionRegistry.Loaded(defaults, Map.of(), List.of()));
        rig(h, 3);
        head(h, b(3))
                .encode(
                        ResourceLocation.withDefaultNamespace("sheep"),
                        head(h, b(3)).geometry());
        h.runAtTickTime(35, () -> {
            try {
                var c = vat(h);
                h.assertTrue(
                        MobProcessRegistry.DEFAULT.resolve(head(h, A).getIdentity())
                                != MobProcessRegistry.DEFAULT.resolve(
                                        head(h, b(3)).getIdentity()),
                        "Species profiles collapsed");
                ready(state(h, A), PassiveProcessCompiler.MILK, 1200);
                cycles(c.processEngine(), 1);
                h.assertTrue(
                        c.milkAmount() == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(A)))
                                && c.getAssemblies().size() == 2,
                        "Sheep configuration removed Cow milk buffer");
                h.assertTrue(
                        VatRenderDescriptor.from(c, head(h, b(3))) != null
                                && c.statusSnapshot().toString().contains("minecraft:sheep"),
                        "Mixed identity presentation missing");
                var cowBuffer = ResourceLocation.parse("slice10:cow_output");
                var sheepBuffer = ResourceLocation.parse("slice10:sheep_output");
                var custom = new MobProcessRegistry();
                custom.register(
                        ItemEssence.COW,
                        new MobProcessRegistry.Profile(
                                List.of(),
                                Map.of(
                                        ProductBuffers.MILK,
                                        ProductBuffers.builtins().get(ProductBuffers.MILK),
                                        cowBuffer,
                                        new ProductBufferDefinition(List.of("minecraft:water"), 1000, false))));
                custom.register(
                        head(h, b(3)).getIdentity(),
                        new MobProcessRegistry.Profile(
                                List.of(),
                                Map.of(
                                        sheepBuffer,
                                        new ProductBufferDefinition(List.of("minecraft:water"), 1000, false))));
                var customEngine = new VatProcessEngine(c, custom.freeze());
                customEngine.assembled();
                h.assertTrue(
                        c.processFluids().ids().containsAll(List.of(cowBuffer, sheepBuffer, ProductBuffers.MILK))
                                && c.processFluids().compatible(ProductBuffers.MILK),
                        "Last specimen profile replaced controller buffer union");
                h.succeed();
            } finally {
                PassiveMobDefinitionRegistry.publish(original);
            }
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 10)
    public static void actorClaimLocalPriorityAndUniqueTarget(GameTestHelper h) {
        var a = new BlockPos(2, 2, 2);
        var b = a.east(3);
        var tool = new BlockPos(0, 2, 2);
        var blender = tool.north();
        var other = tool.south();
        var candidates = List.of(
                new VatTopologyCompiler.Claim(tool, a, "HARVEST", 100),
                new VatTopologyCompiler.Claim(tool, b, "HARVEST", 100),
                new VatTopologyCompiler.Claim(blender, a, "HARVEST", 200),
                new VatTopologyCompiler.Claim(other, b, "MILK", 100));
        var claims = VatTopologyCompiler.claim(candidates);
        h.assertTrue(
                claims.stream()
                                .anyMatch(c ->
                                        c.actor().equals(blender) && c.head().equals(a))
                        && claims.stream()
                                .anyMatch(
                                        c -> c.actor().equals(tool) && c.head().equals(b)),
                "Priority escaped specimen/use scope");
        var ambiguous = List.of(
                new VatTopologyCompiler.Claim(tool, b, "HARVEST", 100),
                new VatTopologyCompiler.Claim(tool, a, "HARVEST", 100));
        h.assertTrue(
                VatTopologyCompiler.claim(ambiguous).size() == 2
                        && VatTopologyCompiler.claim(ambiguous)
                                .getFirst()
                                .head()
                                .equals(a),
                "One actor claimed two Heads or unstable target");
        var reverse = new ArrayList<>(candidates);
        Collections.reverse(reverse);
        h.assertTrue(claims.equals(VatTopologyCompiler.claim(reverse)), "Discovery order changed claims");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void conduitSharedInputAcquiresForAssignedSpecimensFairly(GameTestHelper h) {
        sharedToolRig(h);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var input = (VatItemPortEntity) h.getBlockEntity(SHARED_INPUT);
            var winners = new ArrayList<BlockPos>();
            h.assertTrue(
                    c.getTopology().toolLoading().size() == 2
                            && c.getTopology().toolLoading().stream()
                                    .allMatch(r -> r.itemInputs().contains(h.absolutePos(SHARED_INPUT))),
                    "Shared conduit input failed to reach independent Tool assignments");
            var rendered = c.getAssemblies().stream()
                    .flatMap(a -> a.head().presentation().tools().stream())
                    .map(VatPresentation.Link::port)
                    .toList();
            h.assertTrue(
                    rendered.size() == 2 && rendered.stream().distinct().count() == 2,
                    "Tool mechanism descriptors duplicated across Heads");
            for (int i = 0; i < 6; i++) {
                adult(h, A);
                adult(h, b(3));
                for (var p : List.of(A, b(3))) {
                    state(h, p).process(PassiveProcessCompiler.HARVEST).reset();
                    state(h, p).process(PassiveProcessCompiler.HARVEST).data.remove("killHealth");
                    state(h, p).process(PassiveProcessCompiler.HARVEST).data.remove("killActor");
                    state(h, p)
                            .process(PassiveProcessCompiler.HARVEST)
                            .data
                            .remove("interactionCooldown:mobvats:weapon_harvest");
                }
                tool(h, A).setHeldItem(ItemStack.EMPTY);
                tool(h, b(3)).setHeldItem(ItemStack.EMPTY);
                tool(h, A).results().clear();
                tool(h, b(3)).results().clear();
                tool(h, A).setInteractionStart(Long.MIN_VALUE);
                tool(h, b(3)).setInteractionStart(Long.MIN_VALUE);
                input.staging().setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
                fill(c, ProductFluids.FEEDING_SLURRY.get(), 250);
                cycles(c.processEngine(), 1);
                boolean first = !tool(h, A).heldItem().isEmpty(),
                        second = !tool(h, b(3)).heldItem().isEmpty();
                h.assertTrue(
                        first != second && input.staging().getStackInSlot(0).isEmpty(),
                        "One shared sword loaded multiple Tool Ports");
                var winner = first ? A : b(3);
                winners.add(winner);
                h.assertTrue(
                        state(h, winner).stage() == LifeStage.ADULT
                                && tool(h, winner).heldItem().getDamageValue() == 1,
                        "Acquired Sword serviced wrong specimen");
            }
            for (int i = 1; i < winners.size(); i++)
                h.assertTrue(
                        !winners.get(i).equals(winners.get(i - 1)),
                        "Shared tool acquisition permanently favored one Head");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void sharedBucketDoesNotLoadJuvenileOrUnneededTool(GameTestHelper h) {
        sharedToolRig(h);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            state(h, A).stage(LifeStage.JUVENILE);
            state(h, A).growth().start();
            c.processEngine().assembled();
            fill(c, NeoForgeMod.MILK.get(), 2000);
            var source = (VatItemPortEntity) h.getBlockEntity(SHARED_INPUT);
            source.staging().setStackInSlot(0, new ItemStack(Items.BUCKET));
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    tool(h, A).heldItem().isEmpty()
                            && tool(h, b(3)).results().snapshot().stream().anyMatch(s -> s.is(Items.MILK_BUCKET))
                            && c.milkAmount() == 1000,
                    "Bucket loaded juvenile or serviced wrong specimen");
            tool(h, b(3)).setHeldItem(ItemStack.EMPTY);
            source.staging().setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    tool(h, A).heldItem().isEmpty()
                            && tool(h, b(3)).heldItem().isEmpty()
                            && source.staging().getStackInSlot(0).is(Items.IRON_SWORD),
                    "Acquisition loaded a weapon without eligible replacement feed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void sharedInputAndAcquisitionRotateWithoutRescans(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var supply = new ItemStackHandler(1);
            var winners = new ArrayList<BlockPos>();
            var operation = new ProcessDefinition(
                    "scarce",
                    "test:scarce",
                    ProcessDefinition.Stage.CADENCE,
                    id -> true,
                    List.of(),
                    null,
                    ProcessDefinition.Backpressure.NONE,
                    (context, s) -> {
                        var step = new ProcessInput.Item(stack -> stack.is(Items.IRON_SWORD), 1).plan(supply);
                        if (step == null) return ProcessResult.of(ProcessResult.Reason.WAITING_INPUT);
                        if (!new ProcessTransaction().add(step).commit())
                            return ProcessResult.of(ProcessResult.Reason.WAITING_INPUT);
                        winners.add(context.head());
                        return ProcessResult.committed(true);
                    },
                    List.of(),
                    List.of());
            var registry = new MobProcessRegistry();
            registry.register(ItemEssence.COW, new MobProcessRegistry.Profile(List.of(operation)));
            var engine = new VatProcessEngine(c, registry.freeze());
            engine.assembled();
            int compilations = engine.compilationCount();
            long revision = c.getTopologyRevision();
            for (int i = 0; i < 8; i++) {
                supply.setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
                cycles(engine, 1);
            }
            h.assertTrue(
                    winners.size() == 8 && winners.stream().distinct().count() == 2,
                    "Shared supply starved one specimen");
            for (int i = 1; i < winners.size(); i++)
                h.assertTrue(!winners.get(i).equals(winners.get(i - 1)), "Acquisition ordering did not rotate");
            h.assertTrue(
                    engine.compilationCount() == compilations && c.getTopologyRevision() == revision,
                    "Normal multi-specimen tick rebuilt contexts/topology");
            var saved = new CompoundTag();
            engine.save(saved, h.getLevel().registryAccess());
            var restored = new VatProcessEngine(c, registry);
            restored.load(saved, h.getLevel().registryAccess());
            restored.assembled();
            supply.setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
            cycles(restored, 1);
            h.assertTrue(
                    winners.getLast()
                            .equals(
                                    saved.contains("processNextHead")
                                            ? BlockPos.of(saved.getLong("processNextHead"))
                                            : h.absolutePos(A)),
                    "Fairness cursor lost through reload");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void assimilationKeepsAuthorityWithoutDuplicatingHeads(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var target = new VatProcessEngine(c);
            target.assembled();
            target.state(h.absolutePos(A)).process("test:authority").elapsed = 11;
            var source = new VatProcessEngine(c);
            source.assembled();
            source.state(h.absolutePos(A)).process("test:authority").elapsed = 99;
            source.state(h.absolutePos(b(3))).process("test:authority").elapsed = 77;
            source.addExperienceMb(100);
            target.assimilate(source);
            var data = new CompoundTag();
            target.save(data, h.getLevel().registryAccess());
            h.assertTrue(
                    data.getList("specimenProcesses", Tag.TAG_COMPOUND).size() == 2
                            && target.state(h.absolutePos(A)).process("test:authority").elapsed == 11
                            && target.experienceMb() == 100,
                    "Assimilation duplicated Head or replaced authority");
            var stores = new ProductBuffers(() -> {});
            var other = new ProductBuffers(() -> {});
            stores.buffer(ProductBuffers.MILK).setFluid(new FluidStack(NeoForgeMod.MILK.get(), 1000));
            other.buffer(ProductBuffers.MILK).setFluid(new FluidStack(NeoForgeMod.MILK.get(), 2000));
            stores.assimilate(other);
            h.assertTrue(
                    stores.buffer(ProductBuffers.MILK).getFluidAmount() == 3000,
                    "Assimilation discarded shared committed fluids");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void physicalActorBlockOnADoesNotBlockB(GameTestHelper h) {
        rig(h, 3);
        sword(h, A);
        sword(h, b(3));
        input(h, A, 1);
        input(h, b(3), 1);
        h.setBlock(
                A.below().south(),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                        .setValue(ConduitBlock.MODE, ConduitMode.TRUNK));
        h.runAtTickTime(60, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.getTopology().physical().blocked().stream()
                            .anyMatch(r -> r.kind() == VatPhysicalState.Kind.TOOL
                                    && r.head().equals(h.absolutePos(A))),
                    "A Tool obstruction not diagnosed");
            h.assertTrue(
                    state(h, A).stage() == LifeStage.ADULT
                            && state(h, b(3)).stage() == LifeStage.JUVENILE
                            && tool(h, A).heldItem().getDamageValue() == 0
                            && tool(h, b(3)).heldItem().getDamageValue() == 2,
                    "Blocked A suppressed B actor");
            h.assertTrue(
                    !head(h, A).presentation().tools().stream().anyMatch(VatPresentation.Link::assigned)
                            && head(h, b(3)).presentation().tools().stream().anyMatch(VatPresentation.Link::assigned),
                    "Obstruction presentation crossed specimens");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void blenderPriorityRemainsLocalInRealTopology(GameTestHelper h) {
        rig(h, 3);
        sword(h, A);
        sword(h, b(3));
        input(h, A, 0);
        input(h, b(3), 0);
        h.setBlock(O.offset(0, 1, 2), Content.state(VatPartType.Blender));
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.getTopology().actors().stream()
                                    .anyMatch(r -> r.head().equals(h.absolutePos(A))
                                            && r.actor().equals(h.absolutePos(O.offset(0, 1, 2)))
                                            && r.use().equals(PassiveUses.BLEND))
                            && c.getTopology().actors().stream()
                                    .anyMatch(r -> r.head().equals(h.absolutePos(b(3)))
                                            && r.actor().equals(h.absolutePos(toolPos(b(3))))
                                            && r.use().equals(PassiveUses.HARVEST)),
                    "Blender priority on A displaced B Sword");
            fill(c, ProductFluids.FEEDING_SLURRY.get(), 500);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    tool(h, A).heldItem().getDamageValue() == 0
                            && tool(h, b(3)).heldItem().getDamageValue() == 1
                            && c.meatAmount() > 0
                            && state(h, b(3))
                                            .process(PassiveProcessCompiler.HARVEST)
                                            .data
                                            .getFloat("killHealth")
                                    == 4,
                    "Local actors failed independent commits");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void obstructionOnOneHeadDoesNotSuppressOther(GameTestHelper h) {
        rig(h, 3);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var pos = h.absolutePos(A);
            var geometry = ConduitGeometry.bake(new ConduitGeometry.Snapshot(
                    c.getBoundingBox().getMin(),
                    c.getBoundingBox().getMax(),
                    List.of(new ConduitGeometry.Cell(
                            pos, ConduitResource.ITEM, ConduitCladding.BARE, ConduitMode.TRUNK, 0, List.of()))));
            var physical = VatPhysicalState.harnesses(c.getAssemblies(), geometry);
            h.assertTrue(
                    !physical.operational(pos) && physical.operational(h.absolutePos(b(3))),
                    "Harness obstruction leaked to B");
            var t = c.getTopology();
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
                                physical));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
            ready(state(h, A), PassiveProcessCompiler.MILK, 1200);
            ready(state(h, b(3)), PassiveProcessCompiler.MILK, 1200);
            cycles(c.processEngine(), 1);
            h.assertTrue(
                    c.milkAmount() == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(b(3))))
                            && state(h, A).process(PassiveProcessCompiler.MILK).phase == ProcessState.Phase.READY,
                    "Blocked A prevented valid B output");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void drainTieAndSharedHarnessRejected(GameTestHelper h) {
        rig(h, 2);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var hs = c.getAssemblies().stream()
                    .flatMap(a -> a.harnesses().stream())
                    .toList();
            h.setBlock(A.east().below(2), Content.state(VatPartType.Sump));
            var shared = (VatSumpEntity) h.getBlockEntity(A.east().below(2));
            rejects(
                    h,
                    () -> SpecimenGeometry.associateAll(
                            h.getLevel(), List.of(head(h, A), head(h, b(2))), hs, List.of(shared)),
                    "Equidistant Drain ownership accepted");
            var first = c.getAssemblies().getFirst();
            var second = c.getAssemblies().getLast();
            rejects(
                    h,
                    () -> SpecimenGeometry.supportOwnership(
                            List.of(first, new MobAssembly(first.harnesses(), second.head(), second.drain()))),
                    "Shared Harness ownership accepted");
            rejects(
                    h,
                    () -> SpecimenGeometry.supportOwnership(
                            List.of(first, new MobAssembly(second.harnesses(), second.head(), first.drain()))),
                    "Shared Drain ownership accepted");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice10Runtime", timeoutTicks = 65)
    public static void overlappingSavedCellsRejectFormation(GameTestHelper h) {
        rig(h, 1);
        var g = SimulacrumGeometry.resolve(2, 2, 1, Direction.NORTH, null);
        head(h, A).encode(ItemEssence.COW, g);
        h.runAtTickTime(35, () -> {
            var c = head(h, A).getMultiblockController().orElseThrow();
            h.assertTrue(
                    !c.isAssembled()
                            && c.getLastError()
                                    .map(e -> e.getChatMessage().getString().contains("Overlapping"))
                                    .orElse(false),
                    "Overlapping persisted cells formed");
            h.succeed();
        });
    }
}
