package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class EnvironmentalToolGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            TOOL = O.offset(2, 1, 4),
            SOURCE = TOOL.west();

    private static VatHeadEntity head(GameTestHelper h) {
        return (VatHeadEntity) h.getBlockEntity(HEAD);
    }

    private static VatToolPortEntity tool(GameTestHelper h) {
        return (VatToolPortEntity) h.getBlockEntity(TOOL);
    }

    private static void rig(GameTestHelper h, String entity) {
        VatGameTests.build(h, 5);
        head(h).encode(ResourceLocation.withDefaultNamespace(entity));
        h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
        h.setBlock(SOURCE, Content.state(VatPartType.ItemPort));
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        MachineFixtures.specimen((p, s) -> h.setBlock(p, s), HEAD, head(h).geometry());
    }

    private static ProcessContext context(GameTestHelper h) {
        var vat = head(h).getMultiblockController().orElseThrow();
        vat.rebuildTopology();
        return new ProcessContext(
                vat.processEngine(),
                vat.getAssemblies().getFirst(),
                vat.processEngine().state(head(h).getBlockPos()),
                MobProcessRegistry.DEFAULT.resolve(head(h).getIdentity()));
    }

    private static ToolInteractionDefinition operation(String entity) {
        return PassiveDefaults.data().get("minecraft:" + entity).toolInteractions().stream()
                .filter(d -> d.executor()
                        .equals("mobvats:"
                                + (entity.equals("goat")
                                        ? "goat_horn"
                                        : entity.equals("sniffer") ? "sniffer_dig" : "armadillo_brush")))
                .findFirst()
                .orElseThrow();
    }

    private static void withDefaults(String entity, Runnable action) {
        var old = new PassiveMobDefinitionRegistry.Loaded(
                PassiveMobDefinitionRegistry.definitions(), Map.of(), List.of());
        var all = new HashMap<>(old.definitions());
        var d = PassiveDefaults.data().get("minecraft:" + entity);
        all.put(d.identity(), d);
        PassiveMobDefinitionRegistry.publish(new PassiveMobDefinitionRegistry.Loaded(all, Map.of(), List.of()));
        try {
            action.run();
        } finally {
            PassiveMobDefinitionRegistry.publish(old);
        }
    }

    private static ProcessResult execute(GameTestHelper h, ProcessState state, ToolInteractionDefinition d) {
        tool(h).setInteractionStart(Long.MIN_VALUE);
        return ToolInteractions.execute(
                context(h), state, d, ToolInteractionExecutorRegistry.require(ResourceLocation.parse(d.executor())));
    }

    private static void acquire(GameTestHelper h, String entity, Item item) {
        ((VatItemPortEntity) h.getBlockEntity(SOURCE)).staging().setStackInSlot(0, new ItemStack(item, 2));
        h.assertTrue(
                ToolInteractions.acquire(context(h), PassiveMobDefinitionRegistry.resolve(head(h).getIdentity()))
                                .changed()
                        && tool(h).heldItem().is(item)
                        && ((VatItemPortEntity) h.getBlockEntity(SOURCE))
                                        .staging()
                                        .getStackInSlot(0)
                                        .getCount()
                                == 1,
                "Environmental input did not use routed resource acquisition");
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void goatRetainedTargetProgressBackpressureAndFiniteHorns(GameTestHelper h) {
        rig(h, "goat");
        h.runAtTickTime(
                35,
                () -> withDefaults("goat", () -> {
                    var d = operation("goat");
                    acquire(h, "goat", Items.STONE);
                    var state = new ProcessState();
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.RUNNING && state.elapsed == 10,
                            "Authored ram duration ignored");
                    state = ProcessState.load(
                            state.save(h.getLevel().registryAccess()),
                            h.getLevel().registryAccess());
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.RUNNING && state.elapsed == 20,
                            "Progress/binding did not survive serialization");
                    var before = head(h).specimenState();
                    tool(h).results().store(List.of(new ItemStack(Items.DIRT)));
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.OUTPUT_BLOCKED
                                    && state.elapsed == 20
                                    && head(h).specimenState().equals(before),
                            "Backpressure changed progress/horns");
                    tool(h).results().clear();
                    execute(h, state, d);
                    h.assertTrue(
                            execute(h, state, d).changed()
                                    && state.phase == ProcessState.Phase.IDLE
                                    && tool(h).heldItem().is(Items.STONE)
                                    && tool(h).results().snapshot().getFirst().is(Items.GOAT_HORN),
                            "Ram did not retain target/commit horn");
                    var first = head(h).specimenState();
                    h.assertTrue(
                            first.getBoolean("HasLeftHorn") != first.getBoolean("HasRightHorn"),
                            "First ram did not remove exactly one horn");
                    tool(h).results().clear();
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.RUNNING && state.elapsed == 0,
                            "Authored ram cooldown ignored");
                    state.data.putLong(
                            "interactionCooldown:" + d.id(), h.getLevel().getGameTime() - d.cooldown_ticks());
                    for (int i = 0; i < 4; i++) execute(h, state, d);
                    h.assertTrue(
                            !head(h).specimenState().getBoolean("HasLeftHorn")
                                    && !head(h).specimenState().getBoolean("HasRightHorn"),
                            "Second horn not depleted");
                    tool(h).results().clear();
                    state.data.remove("interactionCooldown:" + d.id());
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.CONDITION_FAILED
                                    && tool(h).results().isEmpty(),
                            "Hornless goat generated another horn");
                    h.succeed();
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void snifferRetainedSubstrateVanillaLootAndCooldown(GameTestHelper h) {
        rig(h, "sniffer");
        h.runAtTickTime(
                35,
                () -> withDefaults("sniffer", () -> {
                    var d = operation("sniffer");
                    acquire(h, "sniffer", Items.MOSS_BLOCK);
                    var state = new ProcessState();
                    for (int i = 0; i < 11; i++)
                        h.assertTrue(
                                execute(h, state, d).reason() == ProcessResult.Reason.RUNNING,
                                "Dig completed before authored duration");
                    var result = execute(h, state, d);
                    // The larger specimen has less comfort in this compact rig. Fractional native
                    // loot must carry into later digs rather than forcing a minimum one-item yield.
                    h.assertTrue(
                            result.reason() == ProcessResult.Reason.COMMITTED
                                    && tool(h).heldItem().is(Items.MOSS_BLOCK),
                            "Dig did not commit/retain substrate");
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.RUNNING && state.elapsed == 0,
                            "Dig cooldown ignored");
                    for (int attempt = 0; attempt < 4 && tool(h).results().isEmpty(); attempt++) {
                        state.data.remove("interactionCooldown:" + d.id());
                        for (int i = 0; i < 12; i++) result = execute(h, state, d);
                    }
                    h.assertTrue(
                            result.reason() == ProcessResult.Reason.COMMITTED
                                    && tool(h).heldItem().is(Items.MOSS_BLOCK)
                                    && !tool(h).results().isEmpty()
                                    && tool(h).results().snapshot().stream()
                                            .allMatch(s -> s.is(Items.TORCHFLOWER_SEEDS) || s.is(Items.PITCHER_POD)),
                            "Dig lost retained substrate or vanilla seeds: " + result + " held=" + tool(h).heldItem()
                                    + " drops=" + tool(h).results().snapshot());
                    tool(h).results().clear();
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.RUNNING && state.elapsed == 0,
                            "Repeated dig cooldown ignored");
                    var executor = ToolInteractionExecutorRegistry.require(ResourceLocation.parse(d.executor()));
                    h.assertTrue(
                            !executor.supports(h.getLevel(), head(h).getIdentity(), new ItemStack(Items.STONE))
                                    && !d.accepts(new ItemStack(Items.STONE)),
                            "Invalid substrate accepted");
                    h.succeed();
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void armadilloBrushDamageBreakageAndBlockedOutput(GameTestHelper h) {
        rig(h, "armadillo");
        h.runAtTickTime(
                35,
                () -> withDefaults("armadillo", () -> {
                    var d = operation("armadillo");
                    var brush = new ItemStack(Items.BRUSH);
                    brush.setDamageValue(brush.getMaxDamage() - 1);
                    tool(h).setHeldItem(brush);
                    var state = new ProcessState();
                    execute(h, state, d);
                    tool(h).results().store(List.of(new ItemStack(Items.STONE)));
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.OUTPUT_BLOCKED
                                    && tool(h).heldItem().getDamageValue() == brush.getDamageValue()
                                    && state.elapsed == 10,
                            "Blocked brushing damaged tool or progressed");
                    tool(h).results().clear();
                    h.assertTrue(
                            execute(h, state, d).reason() == ProcessResult.Reason.COMMITTED
                                    && tool(h).heldItem().isEmpty()
                                    && tool(h).results().snapshot().getFirst().is(Items.ARMADILLO_SCUTE),
                            "Brush breakage lost scute");
                    state = new ProcessState();
                    tool(h).results().clear();
                    tool(h).setHeldItem(new ItemStack(Items.BRUSH));
                    execute(h, state, d);
                    execute(h, state, d);
                    h.assertTrue(tool(h).heldItem().getDamageValue() == 16, "Vanilla brush durability cost ignored");
                    h.succeed();
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void armadilloAcceptanceRequiresMountedReplacementAfterBreakage(GameTestHelper h) {
        rig(h, "armadillo");
        var output = TOOL.east();
        h.setBlock(output, Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(output))
                .setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        h.runAtTickTime(
                35,
                () -> withDefaults("armadillo", () -> {
                    var input = (VatItemPortEntity) h.getBlockEntity(SOURCE);
                    input.staging().setStackInSlot(0, new ItemStack(Items.BRUSH));
                    var brush = new ItemStack(Items.BRUSH);
                    brush.setDamageValue(brush.getMaxDamage() - 1);
                    tool(h).setHeldItem(brush);
                    var state = new ProcessState();
                    var definition = operation("armadillo");
                    execute(h, state, definition);
                    h.assertTrue(execute(h, state, definition).changed(), "Brush did not commit its final scute");
                    h.assertTrue(
                            tool(h).heldItem().isEmpty() && OperatorVatFixtures.brushReplacementPending(tool(h), input),
                            "Expected a broken brush with staged replenishment");
                    h.assertTrue(
                            !OperatorVatFixtures.suppliedInput(tool(h), input, Items.BRUSH),
                            "Acceptance incorrectly allowed a staged brush to substitute for a mounted tool");
                    var context = context(h);
                    var profile = PassiveMobDefinitionRegistry.resolve(head(h).getIdentity());
                    h.assertTrue(
                            !ToolInteractions.acquire(context, profile).changed(),
                            "Pending scute did not block acquisition");
                }));
        // Let ordinary controller ticks drain the scute and acquire the routed replacement.
        h.runAtTickTime(55, () -> {
            var input = (VatItemPortEntity) h.getBlockEntity(SOURCE);
            h.assertTrue(
                    ((VatItemPortEntity) h.getBlockEntity(output))
                            .staging()
                            .getStackInSlot(0)
                            .is(Items.ARMADILLO_SCUTE),
                    "Output drain lost the final scute");
            h.assertTrue(
                    OperatorVatFixtures.suppliedInput(tool(h), input, Items.BRUSH)
                            && !OperatorVatFixtures.brushReplacementPending(tool(h), input)
                            && tool(h).heldItem().getDamageValue() <= 16
                            && input.staging().getStackInSlot(0).isEmpty(),
                    "Acceptance did not require an actually mounted, conserved replacement brush");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 210)
    public static void armadilloResumeSnapshotCatchesBreakageAndReplenishes(GameTestHelper h) {
        rig(h, "armadillo");
        var output = TOOL.east();
        h.setBlock(output, Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(output))
                .setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        h.runAtTickTime(
                35,
                () -> withDefaults("armadillo", () -> {
                    ((VatItemPortEntity) h.getBlockEntity(SOURCE))
                            .staging()
                            .setStackInSlot(0, new ItemStack(Items.BRUSH));
                    tool(h).setHeldItem(new ItemStack(Items.BRUSH));
                    var context = context(h);
                    var state = context.state().process("mobvats:armadillo_brush");
                    h.assertTrue(
                            execute(h, state, operation("armadillo")).reason() == ProcessResult.Reason.RUNNING
                                    && state.elapsed == 10
                                    && tool(h).heldItem().getDamageValue() == 0,
                            "Pause must preserve a fresh brush halfway through its first operation");
                    context.controller().setMachineActive(false);
                }));
        h.runAtTickTime(
                75, () -> head(h).getMultiblockController().orElseThrow().setMachineActive(true));
        h.runAtTickTime(175, () -> {
            var input = (VatItemPortEntity) h.getBlockEntity(SOURCE);
            h.assertTrue(
                    OperatorVatFixtures.brushReplacementPending(tool(h), input)
                            && !OperatorVatFixtures.suppliedInput(tool(h), input, Items.BRUSH),
                    "The fixed 100-tick resume snapshot must expose genuine brush breakage, not pass staged stock");
        });
        h.runAtTickTime(185, () -> {
            var input = (VatItemPortEntity) h.getBlockEntity(SOURCE);
            h.assertTrue(
                    OperatorVatFixtures.suppliedInput(tool(h), input, Items.BRUSH)
                            && tool(h).heldItem().getDamageValue() == 0
                            && input.staging().getStackInSlot(0).isEmpty()
                            && ((VatItemPortEntity) h.getBlockEntity(output))
                                    .staging()
                                    .getStackInSlot(0)
                                    .is(Items.ARMADILLO_SCUTE),
                    "Native acquisition did not restore the strict acceptance state within one more cadence");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void environmentalDefinitionValidationAndExistingEggProcess(GameTestHelper h) {
        for (var entity : List.of("goat", "sniffer", "armadillo")) {
            var d = operation(entity);
            d.validate();
            var loaded = PassiveMobDefinitionRegistry.JSON.fromJson(
                    PassiveMobDefinitionRegistry.JSON.toJson(d), ToolInteractionDefinition.class);
            h.assertTrue(d.equals(loaded), "Environmental authored data did not round trip");
            boolean rejected = false;
            try {
                var invalid = new ToolInteractionDefinition(
                        d.id(),
                        d.executor(),
                        d.items(),
                        d.abilities(),
                        d.actor_use(),
                        d.requires_stage(),
                        d.cooldown_ticks(),
                        d.priority(),
                        d.output_policy(),
                        null);
                ToolInteractionExecutorRegistry.require(ResourceLocation.parse(d.executor()))
                        .validateDefinition(PassiveDefaults.data().get("minecraft:" + entity), invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            h.assertTrue(rejected, "Environmental zero duration accepted");
        }
        h.assertTrue(
                PassiveDefaults.data().get("minecraft:chicken").processes().stream()
                                .filter(p -> p.id().equals("mobvats:egg"))
                                .count()
                        == 1,
                "Chicken egg output missing/duplicated");
        h.succeed();
    }
}
