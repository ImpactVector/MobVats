package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessResult.Reason.*;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class GenericProcessGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            TOOL = O.offset(2, 1, 4),
            FEED = O.offset(2, 1, 0);

    private static void rig(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity) h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.setBlock(FEED, Content.state(VatPartType.ItemPort));
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        Slice11Fixtures.feedFrom(h, FEED, FEED.south());
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
    }

    private static ProcessContext context(GameTestHelper h) {
        var vat = vat(h);
        return new ProcessContext(
                vat.processEngine(),
                vat.getAssemblies().getFirst(),
                new SpecimenProcessState(),
                MobProcessRegistry.DEFAULT.resolve(ItemEssence.COW));
    }

    private static ProcessDefinition action(
            String id,
            List<ProcessRequirement> conditions,
            ProcessDefinition.Operation operation,
            ProcessEffect... effects) {
        return new ProcessDefinition(
                id,
                id,
                ProcessDefinition.Stage.CADENCE,
                ItemEssence.COW::equals,
                conditions,
                null,
                ProcessDefinition.Backpressure.NONE,
                operation,
                List.of(),
                List.of(effects));
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void registryResolvesSpeciesAndStableDefinitionOrder(GameTestHelper h) {
        var a = action("z", List.of(), (c, s) -> ProcessResult.committed(true));
        var b = action("a", List.of(), (c, s) -> ProcessResult.committed(true));
        var registry = new MobProcessRegistry();
        registry.register(ItemEssence.COW, new MobProcessRegistry.Profile(List.of(a, b)));
        registry.freeze();
        h.assertTrue(
                registry.resolve(ItemEssence.COW).definitions().getFirst().id().equals("a")
                        && registry.resolve(ResourceLocation.withDefaultNamespace("pig")) == null,
                "Registry lookup/order incorrect");
        h.assertTrue(
                MobProcessRegistry.DEFAULT.resolve(ItemEssence.COW).definitions().stream()
                                .anyMatch(d -> d.id().contains("mobvats:weapon_harvest"))
                        && MobProcessRegistry.DEFAULT.resolve(ItemEssence.COW).definitions().stream()
                                .anyMatch(d -> d.id().contains("mobvats:milk_container")),
                "Cow definitions incomplete");
        boolean rejected = false;
        try {
            registry.register(ItemEssence.COW, new MobProcessRegistry.Profile(List.of()));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "Frozen registry mutated");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void typedRequirementsAndConditionFailure(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var c = context(h);
            var state = new ProcessState();
            final int[] executions = {0};
            var denied = action(
                    "denied", List.of(ProcessRequirement.condition(ctx -> false, CONDITION_FAILED)), (ctx, s) -> {
                        ++executions[0];
                        return ProcessResult.committed(true);
                    });
            h.assertTrue(
                    denied.execute(c, state).reason() == CONDITION_FAILED && executions[0] == 0,
                    "Failed condition executed operation");
            h.assertTrue(
                    ProcessRequirement.item(s -> s.is(Items.WHEAT), 2).evaluate(c, state) == WAITING_INPUT,
                    "Missing item accepted");
            ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 3));
            h.assertTrue(
                    ProcessRequirement.item(s -> s.is(Items.WHEAT), 2).evaluate(c, state) == null,
                    "Exact item requirement rejected");
            h.assertTrue(
                    ProcessRequirement.item(s -> s.is(net.minecraft.tags.ItemTags.LOGS), 1)
                                    .evaluate(c, state)
                            == WAITING_INPUT,
                    "Tag predicate ignored");
            h.assertTrue(
                    ProcessRequirement.fluid(NeoForgeMod.MILK.get(), 1000).evaluate(c, state) == WAITING_FLUID,
                    "Missing fluid accepted");
            c.controller()
                    .fluidHandler(it.zerono.mods.zerocore.lib.data.IoDirection.Input)
                    .fill(new FluidStack(NeoForgeMod.MILK.get(), 1000), FluidAction.EXECUTE);
            h.assertTrue(
                    ProcessRequirement.fluid(NeoForgeMod.MILK.get(), 1000).evaluate(c, state) == null,
                    "Fluid requirement rejected");
            h.assertTrue(ProcessRequirement.experienceMb(60).evaluate(c, state) == WAITING_XP, "Missing XP accepted");
            c.engine().addExperienceMb(60);
            h.assertTrue(ProcessRequirement.experienceMb(60).evaluate(c, state) == null, "XP rejected");
            h.assertTrue(
                    ProcessRequirement.actor(CowBalance.HARVEST_USE).evaluate(c, state) == null
                            && ProcessRequirement.actor("absent").evaluate(c, state) == MISSING_ACTOR,
                    "Compiled actor requirement ignored");
            h.assertTrue(
                    ProcessRequirement.tool(CowBalance.HARVEST_USE, CowHarvestLoot::isWeapon)
                                            .evaluate(c, state)
                                    == null
                            && ProcessRequirement.tool(CowBalance.HARVEST_USE, s -> s.is(Items.BUCKET))
                                            .evaluate(c, state)
                                    == MISSING_TOOL,
                    "Tool predicate ignored");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void timedInstantaneousAndMetricEffects(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var c = context(h);
            var state = new ProcessState();
            var timer = new ProcessDefinition(
                    "timer",
                    "timer",
                    ProcessDefinition.Stage.TICK,
                    ItemEssence.COW::equals,
                    List.of(),
                    new ProcessDefinition.Progress(4, true, ctx -> 1),
                    ProcessDefinition.Backpressure.NONE,
                    null,
                    List.of(ProcessEffect.metric("genericStarted", 1)),
                    List.of(ProcessEffect.metric("genericFinished", 1)));
            for (int i = 0; i < 3; i++)
                h.assertTrue(timer.execute(c, state).reason() == RUNNING, "Timer finished early");
            h.assertTrue(
                    timer.execute(c, state).reason() == READY && state.remaining(4) == 0, "Timer duration incorrect");
            timer.execute(c, state);
            h.assertTrue(
                    c.metrics().total("genericStarted") == 1 && c.metrics().total("genericFinished") == 1,
                    "Timer effects repeated");
            var instant = action(
                    "instant",
                    List.of(),
                    (ctx, s) -> ProcessResult.committed(true),
                    ProcessEffect.metric("genericInstant", 2));
            h.assertTrue(
                    instant.execute(c, new ProcessState()).changed()
                            && c.metrics().total("genericInstant") == 2,
                    "Instant effect did not run");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void atomicInputsXpStateAndRollback(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var engine = vat(h).processEngine();
            engine.addExperienceMb(101);
            var item = new ItemStackHandler(1);
            item.setStackInSlot(0, new ItemStack(Items.WHEAT, 3));
            var tank = new FluidTank(2000);
            tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            var state = new ProcessState();
            final int[] mutation = {0};
            var failed = new ProcessTransaction()
                    .add(new ProcessInput.Item(s -> s.is(Items.WHEAT), 1).plan(item))
                    .add(new ProcessInput.Liquid(Fluids.WATER, 250).plan(tank))
                    .add(new ProcessInput.Experience(41).plan(engine))
                    .add(ProcessTransaction.state(state, ProcessState.Phase.RUNNING, 0))
                    .add(new ProcessTransaction.Step(
                            mutation,
                            () -> true,
                            () -> {
                                mutation[0] = 1;
                                return false;
                            },
                            () -> mutation[0] = 0));
            h.assertTrue(
                    !failed.commit()
                            && item.getStackInSlot(0).getCount() == 3
                            && tank.getFluidAmount() == 1000
                            && engine.experienceMb() == 101
                            && state.phase == ProcessState.Phase.IDLE
                            && mutation[0] == 0,
                    "Partial failure consumed resources/state");
            var success = new ProcessTransaction()
                    .add(new ProcessInput.Item(s -> s.is(Items.WHEAT), 1).plan(item))
                    .add(new ProcessInput.Liquid(Fluids.WATER, 250).plan(tank))
                    .add(new ProcessInput.Experience(41).plan(engine))
                    .add(ProcessTransaction.state(state, ProcessState.Phase.RUNNING, 0));
            h.assertTrue(
                    success.commit()
                            && !success.commit()
                            && item.getStackInSlot(0).getCount() == 2
                            && tank.getFluidAmount() == 750
                            && engine.experienceMb() == 60
                            && state.phase == ProcessState.Phase.RUNNING,
                    "Successful transaction incorrect or repeatable");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void stalePlanAndOutputBackpressureDoNotConsume(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var c = context(h);
            var input = new ItemStackHandler(1);
            input.setStackInSlot(0, new ItemStack(Items.WHEAT, 3));
            var output = new ItemStackHandler(1);
            output.setStackInSlot(0, new ItemStack(Items.STONE, 64));
            var consume = new ProcessInput.Item(s -> s.is(Items.WHEAT), 1).plan(input);
            var tx = new ProcessTransaction().add(consume);
            input.setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
            h.assertTrue(!tx.commit() && input.getStackInSlot(0).getCount() == 4, "Stale plan consumed input");
            var blocked = action("output", List.of(), (ctx, s) -> {
                var produced = ProcessOutput.item(output, new ItemStack(Items.BEEF));
                if (produced == null) return ProcessResult.of(OUTPUT_BLOCKED);
                return ProcessResult.committed(new ProcessTransaction()
                        .add(new ProcessInput.Item(stack -> stack.is(Items.WHEAT), 1).plan(input))
                        .add(produced)
                        .commit());
            });
            h.assertTrue(
                    blocked.execute(c, new ProcessState()).reason() == OUTPUT_BLOCKED
                            && input.getStackInSlot(0).getCount() == 4,
                    "Blocked output consumed input");
            output.setStackInSlot(0, ItemStack.EMPTY);
            h.assertTrue(
                    blocked.execute(c, new ProcessState()).changed()
                            && input.getStackInSlot(0).getCount() == 3
                            && output.getStackInSlot(0).is(Items.BEEF),
                    "Output recovery failed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void genericStateRoundTripWithPendingOutput(GameTestHelper h) {
        var specimen = new SpecimenProcessState();
        var state = specimen.process("test:cooldown");
        state.phase = ProcessState.Phase.BLOCKED;
        state.elapsed = 97;
        state.data.putString("choice", "stable");
        state.pending = new ProcessState.PreparedOutput(List.of(new ItemStack(Items.BEEF, 2)), 4);
        state.pendingTool = new ItemStack(Items.IRON_SWORD);
        var loaded = SpecimenProcessState.load(
                        specimen.save(h.getLevel().registryAccess()),
                        h.getLevel().registryAccess())
                .process("test:cooldown");
        h.assertTrue(
                loaded.phase == ProcessState.Phase.BLOCKED
                        && loaded.elapsed == 97
                        && loaded.data.getString("choice").equals("stable")
                        && loaded.pending.items().getFirst().getCount() == 2
                        && loaded.pending.xp() == 4
                        && loaded.pendingTool.is(Items.IRON_SWORD),
                "Generic state lost progress/pending data");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void schedulerStableOrderAndNoNormalTickTopologyRescan(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var controller = vat(h);
            var order = new StringBuilder();
            var registry = new MobProcessRegistry();
            registry.register(
                    ItemEssence.COW,
                    new MobProcessRegistry.Profile(List.of(
                            action("b", List.of(), (c, s) -> {
                                order.append('b');
                                return ProcessResult.committed(true);
                            }),
                            action("a", List.of(), (c, s) -> {
                                order.append('a');
                                return ProcessResult.committed(true);
                            }))));
            var engine = new VatProcessEngine(controller, registry.freeze());
            engine.assembled();
            int compiled = engine.compilationCount();
            long revision = controller.getTopologyRevision();
            var geometry = controller.conduitGeometry();
            for (int i = 0; i < 20; i++) engine.tick();
            h.assertTrue(
                    order.toString().equals("abab")
                            && engine.compilationCount() == compiled
                            && controller.getTopologyRevision() == revision
                            && controller.conduitGeometry() == geometry,
                    "Normal engine ticks recompiled topology or ordering changed");
            h.assertTrue(engine.status(h.absolutePos(HEAD)).get("a").reason() == COMMITTED, "Generic status missing");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void legacyCowMigrationPreservesGenericExtraState(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var controller = vat(h);
            var data = controller.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            data.remove("passiveStateVersion");
            data.remove("processXp");
            data.remove("legacyXpMigration");
            var legacy = new CompoundTag();
            legacy.putLong("head", h.absolutePos(HEAD).asLong());
            var oldList = new ListTag();
            oldList.add(legacy);
            data.put("cowProcesses", oldList);
            legacy.putString("cycle", "GROWING");
            legacy.putInt("replacementTicks", 400);
            legacy.putInt("milkTicks", 800);
            legacy.putInt("wasteTicks", 700);
            data.putLong("cowXp", 9);
            var extra = new ProcessState();
            extra.phase = ProcessState.Phase.RUNNING;
            extra.elapsed = 42;
            data.getList("specimenProcesses", Tag.TAG_COMPOUND)
                    .getCompound(0)
                    .getCompound("processes")
                    .put("test:future", extra.save(h.getLevel().registryAccess()));
            controller.syncDataFrom(data, h.getLevel().registryAccess(), SyncReason.FullSync);
            var snapshot = controller.cowState(h.absolutePos(HEAD));
            h.assertTrue(
                    snapshot.replacement().equals("GROWING")
                            && snapshot.replacementTicks() == 8000
                            && snapshot.milkTicks() == 800
                            && controller.processEngine().legacyXpPending() == 9
                            && controller
                                            .processEngine()
                                            .state(h.absolutePos(HEAD))
                                            .process("test:future")
                                            .elapsed
                                    == 42,
                    "Cow migration lost old or future process state");
            data.remove("specimenProcesses");
            controller.syncDataFrom(data, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    controller.cowState(h.absolutePos(HEAD)).replacementTicks() == 8000,
                    "Legacy-only world state failed to load");
            var pending = controller.processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.HARVEST);
            pending.phase = ProcessState.Phase.BLOCKED;
            pending.pending = new ProcessState.PreparedOutput(List.of(new ItemStack(Items.LEATHER, 2)), 7);
            pending.pendingTool = new ItemStack(Items.IRON_SWORD);
            var blockedSave =
                    controller.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            controller.syncDataFrom(blockedSave, h.getLevel().registryAccess(), SyncReason.FullSync);
            var restored =
                    controller.processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.HARVEST);
            h.assertTrue(
                    restored.phase == ProcessState.Phase.BLOCKED
                            && controller
                                            .processEngine()
                                            .state(h.absolutePos(HEAD))
                                            .growth()
                                            .elapsed
                                    == 8000
                            && restored.pending.xp() == 7
                            && restored.pending.items().getFirst().getCount() == 2,
                    "Blocked/pending process lost state through compatibility adapter");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void processUseExclusivityDoesNotRecomputeActorPriority(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var controller = vat(h);
            final int[] runs = {0};
            var registry = new MobProcessRegistry();
            var first = action("a", List.of(ProcessRequirement.actor(CowBalance.HARVEST_USE)), (c, state) -> {
                        ++runs[0];
                        return ProcessResult.committed(true);
                    })
                    .withExclusiveUse(CowBalance.HARVEST_USE);
            var second = action("b", List.of(ProcessRequirement.actor(CowBalance.HARVEST_USE)), (c, state) -> {
                        runs[0] += 100;
                        return ProcessResult.committed(true);
                    })
                    .withExclusiveUse(CowBalance.HARVEST_USE);
            registry.register(ItemEssence.COW, new MobProcessRegistry.Profile(List.of(second, first)));
            var engine = new VatProcessEngine(controller, registry.freeze());
            long revision = controller.getTopologyRevision();
            for (int i = 0; i < 10; i++) engine.tick();
            h.assertTrue(
                    runs[0] == 1
                            && engine.status(h.absolutePos(HEAD)).get("b").reason() == EXCLUSIVE_USE
                            && controller.getTopologyRevision() == revision,
                    "Process use was duplicated or actor priority recomputed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void transactionExceptionRestoresInputsAndOmitsEffects(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var context = context(h);
            var input = new ItemStackHandler(1);
            input.setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
            var tank = new FluidTank(1000);
            var definition = action(
                    "throws",
                    List.of(),
                    (c, state) -> {
                        var transaction = new ProcessTransaction()
                                .add(new ProcessInput.Item(stack -> stack.is(Items.WHEAT), 1).plan(input))
                                .add(ProcessOutput.fluid(tank, new FluidStack(Fluids.WATER, 250)))
                                .add(new ProcessTransaction.Step(
                                        new Object(),
                                        () -> true,
                                        () -> {
                                            throw new IllegalStateException("test failure");
                                        },
                                        () -> {}));
                        return ProcessResult.committed(transaction.commit());
                    },
                    ProcessEffect.metric("mustNotCommit", 1));
            boolean failed = false;
            try {
                definition.execute(context, new ProcessState());
            } catch (IllegalStateException expected) {
                failed = true;
            }
            h.assertTrue(
                    failed
                            && input.getStackInSlot(0).getCount() == 2
                            && tank.isEmpty()
                            && context.metrics().total("mustNotCommit") == 0,
                    "Exception left partial inputs/outputs or completion metric");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void genericOnlyEngineStateReloadAndReadyBoundary(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(35, () -> {
            var controller = vat(h);
            var engine = controller.processEngine();
            engine.addExperienceMb(120);
            var state = engine.state(h.absolutePos(HEAD)).process("test:future");
            state.phase = ProcessState.Phase.RUNNING;
            state.elapsed = 4;
            state.data.putInt("seed", 17);
            var saved = new CompoundTag();
            engine.save(saved, h.getLevel().registryAccess());
            saved.remove("cowProcesses");
            saved.remove("cowXp");
            var loaded = new VatProcessEngine(controller);
            loaded.load(saved, h.getLevel().registryAccess());
            var recovered = loaded.state(h.absolutePos(HEAD)).process("test:future");
            h.assertTrue(
                    loaded.experienceMb() == 120 && recovered.elapsed == 4 && recovered.data.getInt("seed") == 17,
                    "Generic-only state or XP reload failed");
            var definition = new ProcessDefinition(
                    "boundary",
                    "test:future",
                    ProcessDefinition.Stage.TICK,
                    ItemEssence.COW::equals,
                    List.of(),
                    new ProcessDefinition.Progress(4, false, c -> 1),
                    ProcessDefinition.Backpressure.NONE,
                    null,
                    List.of(),
                    List.of());
            var result = definition.execute(context(h), recovered);
            h.assertTrue(
                    result.changed() && result.reason() == READY && recovered.phase == ProcessState.Phase.READY,
                    "Ready boundary did not mark its state transition dirty");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void productOverflowAndIncompatibleOutputRemainUntouched(GameTestHelper h) {
        var tank = new FluidTank(1000);
        tank.setFluid(new FluidStack(Fluids.WATER, Integer.MAX_VALUE));
        h.assertTrue(
                ProcessOutput.fluid(tank, new FluidStack(Fluids.WATER, 1000)) == null
                        && tank.getFluidAmount() == Integer.MAX_VALUE,
                "Reconnection overflow wrapped or discarded committed fluid");
        tank.setFluid(new FluidStack(Fluids.WATER, 500));
        h.assertTrue(
                ProcessOutput.fluid(tank, new FluidStack(Fluids.LAVA, 250)) == null
                        && tank.getFluid().is(Fluids.WATER),
                "Incompatible fluid output changed storage");
        var output = new ItemStackHandler(1);
        output.setStackInSlot(0, new ItemStack(Items.BEEF, 64));
        h.assertTrue(
                ProcessOutput.item(output, new ItemStack(Items.BEEF)) == null
                        && output.getStackInSlot(0).getCount() == 64,
                "Full item output was accepted");
        h.succeed();
    }

    private GenericProcessGameTests() {}
}
