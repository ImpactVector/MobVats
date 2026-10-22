package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.*;

import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerControllerSnapshot.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.menu.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerControllerGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 3);

    private static void recipe(java.util.function.Consumer<MixerState> test) {
        var saved = MixerRecipeRegistry.snapshot();
        var definition = new MixerRecipeDefinition(
                "test:controller_batch",
                0,
                List.of(new MixerRecipeDefinition.Strategy(
                        "default",
                        0,
                        true,
                        List.of(),
                        List.of(new MixerRecipeDefinition.ItemInput("minecraft:wheat", 1)))),
                List.of(
                        new MixerRecipeDefinition.ItemOutput(Items.IRON_INGOT, 2),
                        new MixerRecipeDefinition.ItemOutput(Items.GOLD_INGOT, 3)),
                List.of(
                        new MixerRecipeDefinition.FluidOutput(() -> Fluids.WATER, 125, null, null),
                        new MixerRecipeDefinition.FluidOutput(
                                ProductFluids.CULTURE_SLURRY,
                                250,
                                new CompoundComposition(List.of(new CompoundComposition.Entry(
                                        CompoundComposition.Kind.ITEM, "minecraft:wheat", 1))),
                                null)),
                45);
        try {
            MixerRecipeRegistry.publish(
                    new MixerRecipeRegistry.Loaded(Map.of(definition.id(), definition), List.of(), List.of()));
            var state = new MixerState(() -> {});
            state.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 8), false);
            test.accept(state);
        } finally {
            MixerRecipeRegistry.publish(saved);
        }
    }

    private static void finish(MixerState state) {
        for (int i = 0; i < 5; i++) state.cadence();
    }

    private static void rig(
            GameTestHelper h, java.util.function.BiConsumer<MultiblockMixer, MixerControllerEntity> test) {
        MixerGameTests.rig(h);
        h.runAtTickTime(35, () -> {
            var controller = (MixerControllerEntity) h.getBlockEntity(CONTROLLER);
            var mixer = controller.getMultiblockController().orElseThrow();
            h.assertTrue(mixer.isAssembled(), "Fixture unformed");
            test.accept(mixer, controller);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void nextAndActiveBatchesPreserveAllProducts(GameTestHelper h) {
        recipe(state -> {
            var next = state.batchView();
            h.assertTrue(
                    !next.active() && next.items().size() == 2 && next.fluids().size() == 2,
                    "Next batch/product projection wrong");
            state.cadence();
            var active = state.batchView();
            h.assertTrue(active.active() && active.progress() == 10 && active.duration() == 45, "Active timing wrong");
            h.assertTrue(
                    active.items().get(1).getCount() == 3
                            && active.fluids().get(1).getAmount() == 250,
                    "Output quantity lost");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void projectionDoesNotChangeRuntimeOrPlannerStatus(GameTestHelper h) {
        recipe(state -> {
            var before = state.save(h.getLevel().registryAccess());
            for (int i = 0; i < 5; i++) state.batchView();
            h.assertTrue(before.equals(state.save(h.getLevel().registryAccess())), "Projection mutated runtime");
            h.assertTrue(state.select().orElseThrow().commit(), "Projection invalidated selection");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void etaUsesCadenceAndNeverCountsPausedTime(GameTestHelper h) {
        h.assertTrue(MixerControllerSnapshot.remainingTicks(20, 45, 7, true) == 23, "Cadence rounding/phase wrong");
        h.assertTrue(MixerControllerSnapshot.remainingTicks(20, 45, 7, false) == -1, "Paused ETA fabricated");
        h.assertTrue(MixerControllerSnapshot.remainingTicks(45, 45, 0, true) == 10, "Commit retry cadence wrong");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void completionContainsActualProductsAndDefensiveCopies(GameTestHelper h) {
        recipe(state -> {
            finish(state);
            var last = state.lastCompletion();
            h.assertTrue(
                    last != null && last.items().size() == 2 && last.fluids().size() == 2,
                    "Missing completion outputs");
            h.assertTrue(
                    last.items().get(0).getCount() == 2 && last.fluids().get(0).getAmount() == 125,
                    "Actual quantities wrong");
            h.assertTrue(last.fluids().get(1).get(CompoundFluids.COMPOSITION.get()) != null, "Actual composition lost");
            last.items().getFirst().setCount(64);
            last.fluids().getFirst().setAmount(999);
            h.assertTrue(
                    last.items().getFirst().getCount() == 2
                            && last.fluids().getFirst().getAmount() == 125,
                    "Mutable completion escaped");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedCommitPreservesPreviousCompletion(GameTestHelper h) {
        recipe(state -> {
            finish(state);
            var last = state.lastCompletion();
            for (int i = 0; i < 4; i++) state.itemPort(false, s -> true).extractItem(i, 64, false);
            state.cadence();
            var definition = MixerRecipeRegistry.recipe("test:controller_batch");
            var reservation = state.prepare(definition).orElseThrow();
            state.itemInput().insertItem(0, new ItemStack(Items.WHEAT), false);
            h.assertTrue(!reservation.commit() && state.lastCompletion() == last, "Failed commit replaced completion");
            h.assertTrue(state.batchView().active(), "Failed commit lost retained batch");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void completionSurvivesNextBatchSaveReloadAndOwnership(GameTestHelper h) {
        recipe(state -> {
            finish(state);
            var last = state.lastCompletion();
            state.cadence();
            h.assertTrue(state.lastCompletion() == last && state.batchView().active(), "Next batch erased completion");
            var restored = new MixerState(() -> {});
            restored.load(
                    state.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(
                    restored.lastCompletion().recipe().equals(last.recipe())
                            && FluidStack.matches(
                                    restored.lastCompletion().fluids().getFirst(),
                                    last.fluids().getFirst())
                            && FluidStack.matches(
                                    restored.lastCompletion().fluids().get(1),
                                    last.fluids().get(1)),
                    "Completion reload mismatch");
            var owner = new MixerState(() -> {});
            owner.assimilate(restored);
            restored.clear();
            h.assertTrue(
                    owner.lastCompletion() != null && restored.lastCompletion() == null,
                    "Ownership transfer lost completion");
        });
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void liveSnapshotReportsInternalAndPhysicalFluidsWithoutMutation(GameTestHelper h) {
        rig(h, (mixer, controller) -> {
            mixer.state()
                    .fluids(true)
                    .fill(
                            new FluidStack(Fluids.WATER, 1000),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            mixer.state().itemInput().insertItem(0, new ItemStack(Items.WHEAT, 7), false);
            var before = mixer.state().save(h.getLevel().registryAccess());
            var view = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    view.resources().stream()
                            .anyMatch(r -> !r.product().fluid()
                                    && r.product().quantity() == 7
                                    && r.section().contains("staged inputs")),
                    "Staged items missing");
            h.assertTrue(
                    view.resources().stream()
                                    .filter(r -> r.product().fluid())
                                    .mapToInt(r -> r.product().quantity())
                                    .sum()
                            == 1000,
                    "Connected fluids missing/doubled");
            h.assertTrue(
                    before.equals(mixer.state().save(h.getLevel().registryAccess())), "Live snapshot mutated state");
            h.assertTrue(
                    view.completed().recipe().isEmpty() && view.diagnostics().contains("Processing slots:"),
                    "Empty completion/diagnostics wrong");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void operatorPauseAndDrivePowerHaveNoEta(GameTestHelper h) {
        rig(h, (mixer, controller) -> {
            mixer.setMachineActive(false);
            var view = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    !view.advancing()
                            && view.current().remainingTicks() == -1
                            && view.condition().contains("Paused"),
                    "Pause projection wrong");
            h.assertTrue(view.drive().equals("None") || view.drive().equals("Creative drive"), "Drive label wrong");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void disconnectedControllerMenuIsValidAndReadOnly(GameTestHelper h) {
        h.setBlock(CONTROLLER, MixerContent.state(MixerPartType.Controller));
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(CONTROLLER)));
        var controller = (MixerControllerEntity) h.getBlockEntity(CONTROLLER);
        var menu = new MixerControllerMenu(1, player.getInventory(), controller);
        h.assertTrue(
                menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(),
                "Disconnected menu invalid/writable");
        h.assertTrue(!menu.view().formed(), "Disconnected view claimed formed");
        player.setPos(player.position().add(20, 0, 0));
        h.assertTrue(!menu.stillValid(player), "Remote menu remained valid");
        h.succeed();
    }

    private static ResourceEndpoint endpoint(
            BlockPos pos, Set<ConduitTopology.Role> roles, ItemStackHandler items, boolean accepted) {
        return new ResourceEndpoint() {
            public BlockPos getBlockPos() {
                return pos;
            }

            public Set<ConduitTopology.Role> roles(ConduitResource resource) {
                return resource == ConduitResource.ITEM ? roles : Set.of();
            }

            public IItemHandlerModifiable items(ConduitTopology.Role role) {
                return items;
            }

            public boolean accepts(ItemStack stack) {
                return accepted;
            }
        };
    }

    @GameTest(template = "empty")
    public static void routedItemsExcludeDisconnectedDeduplicateAliasesAndKeepComponents(GameTestHelper h) {
        var handler = new ItemStackHandler(2);
        var a = new ItemStack(Items.WHEAT, 3);
        a.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        var b = new ItemStack(Items.WHEAT, 5);
        b.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        handler.setStackInSlot(0, a);
        handler.setStackInSlot(1, b);
        var consumer = endpoint(new BlockPos(0, 0, 0), Set.of(CONSUMER), new ItemStackHandler(0), true);
        var store = endpoint(new BlockPos(1, 0, 0), Set.of(STORE, PRODUCER), handler, true);
        var alias = endpoint(new BlockPos(0, 0, 1), Set.of(STORE), handler, true);
        var unrelated = endpoint(new BlockPos(20, 0, 0), Set.of(STORE), new ItemStackHandler(1), true);
        unrelated.items(STORE).setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        var routing = new ResourceRouting(ConduitTopology.EMPTY, List.of(consumer, store, alias, unrelated));
        var rows = routing.storedItems(consumer);
        h.assertTrue(
                rows.size() == 2
                        && rows.stream().mapToInt(r -> r.stack().getCount()).sum() == 8,
                "Route aliases/disconnected storage counted");
        h.assertTrue(
                !ItemStack.isSameItemSameComponents(
                        rows.get(0).stack(), rows.get(1).stack()),
                "Components aggregated");
        h.assertTrue(rows.stream().allMatch(ResourceRouting.StoredItem::available), "Routable supply unavailable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void routingFilterMarksStoredItemsUnavailable(GameTestHelper h) {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.COAL, 12));
        var consumer = endpoint(BlockPos.ZERO, Set.of(CONSUMER), new ItemStackHandler(0), true);
        var store = endpoint(BlockPos.ZERO.east(), Set.of(STORE), handler, false);
        var rows = new ResourceRouting(ConduitTopology.EMPTY, List.of(consumer, store)).storedItems(consumer);
        h.assertTrue(rows.size() == 1 && !rows.getFirst().available(), "Filter ignored");
        h.assertTrue(handler.getStackInSlot(0).getCount() == 12, "Read consumed supply");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void componentPayloadsRoundTripWithoutAggregation(GameTestHelper h) {
        var a = new FluidStack(Fluids.WATER, 100);
        var b = a.copy();
        b.set(DataComponents.CUSTOM_NAME, Component.literal("Different composition surrogate"));
        var first = Product.fluid(a, h.getLevel().registryAccess());
        var second = Product.fluid(b, h.getLevel().registryAccess());
        h.assertTrue(!first.stack().equals(second.stack()), "Fluid components lost");
        try {
            var restored = FluidStack.parseOptional(
                    h.getLevel().registryAccess(), net.minecraft.nbt.TagParser.parseTag(second.stack()));
            h.assertTrue(FluidStack.matches(restored, b), "Fluid component codec mismatch");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void largeSnapshotChunksReplaceAtomicallyAndRetainUnicode(GameTestHelper h) {
        var resources = new ArrayList<Resource>();
        var product = Product.item(new ItemStack(Items.WHEAT, 64), h.getLevel().registryAccess());
        for (int i = 0; i < 5000; i++) resources.add(new Resource("Connected 🧪", "Buffer " + i, "Stored", 0, product));
        var view = new MixerControllerSnapshot(
                true,
                true,
                false,
                false,
                "Waiting",
                "Creative drive",
                "Unlimited",
                Batch.EMPTY,
                Batch.EMPTY,
                resources,
                "Technical diagnostics");
        var chunks = MixerControllerNetworking.chunks(7, 1, view.encode());
        var receiver = new MixerControllerNetworking.Receiver();
        h.assertTrue(chunks.size() > 1, "Large fixture not chunked");
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            h.assertTrue(chunk.text().length() <= MixerControllerNetworking.CHUNK_CHARS, "Unbounded packet");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            MixerControllerNetworking.Chunk.CODEC.encode(buffer, chunk);
            var decoded = MixerControllerNetworking.Chunk.CODEC.decode(buffer);
            buffer.release();
            var result = receiver.accept(decoded);
            if (i < chunks.size() - 1) h.assertTrue(result.isEmpty(), "Partial generation published");
            else
                h.assertTrue(
                        result.orElseThrow().resources().size() == 5000
                                && result.orElseThrow()
                                        .resources()
                                        .getFirst()
                                        .section()
                                        .equals("Connected 🧪"),
                        "Large generation truncated");
        }
        h.assertTrue(receiver.accept(chunks.getFirst()).isEmpty(), "Stale generation accepted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidProjectionExcludesDisconnectedAndDeduplicatesServiceAliases(GameTestHelper h) {
        var storage = new PhysicalFluidStorage();
        var local = new ServiceStaging(() -> {});
        var connected = new ServiceStaging(() -> {});
        var disconnected = new ServiceStaging(() -> {});
        var first = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 123);
        first.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(
                        List.of(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:wheat", 1))));
        var second = first.copyWithAmount(456);
        second.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(
                        List.of(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:carrot", 1))));
        local.tank(first).setFluid(first);
        connected.tank(second).setFluid(second);
        disconnected.tank(new FluidStack(Fluids.LAVA, 999)).setFluid(new FluidStack(Fluids.LAVA, 999));
        storage.services(Map.of(
                BlockPos.ZERO,
                local,
                BlockPos.ZERO.east(),
                connected,
                BlockPos.ZERO.west(),
                connected,
                new BlockPos(20, 0, 0),
                disconnected));
        var rows = storage.storedFluids(BlockPos.ZERO);
        h.assertTrue(
                rows.size() == 2
                        && rows.stream().mapToInt(r -> r.stack().getAmount()).sum() == 579,
                "Fluid aliases/disconnected endpoint counted");
        h.assertTrue(
                !FluidStack.isSameFluidSameComponents(
                        rows.get(0).stack(), rows.get(1).stack()),
                "Compound compositions merged");
        h.assertTrue(
                rows.stream().filter(PhysicalFluidStorage.StoredFluid::local).count() == 1, "Local fluid purpose lost");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockerAndOutputFailureDoNotCreateCompletionOrMutateSelection(GameTestHelper h) {
        recipe(state -> {
            state.blockerSource(() -> List.of(MixerBlockerSample.sampled(new ItemStack(Items.IRON_INGOT))));
            var before = state.save(h.getLevel().registryAccess());
            var view = state.batchView();
            h.assertTrue(!view.executable() && view.condition().contains("Blocker"), "Blocker not projected");
            h.assertTrue(before.equals(state.save(h.getLevel().registryAccess())), "Blocked projection changed status");
            state.cadence();
            h.assertTrue(state.lastCompletion() == null && state.progress() == 0, "Blocker created completion");
            state.blockerSource(List::of);
            var definition = MixerRecipeRegistry.recipe("test:controller_batch");
            state.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 56), false);
            state.cadence();
            for (int i = 0; i < 100 && state.commit(definition); i++) {}
            var last = state.lastCompletion();
            var congested = state.batchView();
            h.assertTrue(
                    congested.active()
                            && !congested.executable()
                            && congested.productsResolved()
                            && congested.fluids().get(1).get(CompoundFluids.COMPOSITION.get()) != null,
                    "Output congestion hid resolved expected products");
            h.assertTrue(
                    last != null
                            && !state.commit(definition)
                            && state.lastCompletion() == last
                            && state.blocked().contains("output blocked"),
                    "Blocked output changed completion");
        });
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void runningBatchEtaStopsImmediatelyOnControllerRedstone(GameTestHelper h) {
        MixerGameTests.rig(h);
        var motorPos = new BlockPos(4, 4, 3);
        h.setBlock(motorPos, MixerContent.state(MixerPartType.Drive));
        h.runAtTickTime(35, () -> {
            var controller = (MixerControllerEntity) h.getBlockEntity(CONTROLLER);
            var mixer = controller.getMultiblockController().orElseThrow();
            MixerCombustionGameTests.ticks(mixer, mixer.spinUpTicks());
            mixer.state()
                    .fluids(true)
                    .fill(
                            new FluidStack(Fluids.WATER, 1000),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            mixer.state().depositExperienceMb(250);
            MixerCombustionGameTests.ticks(mixer, 17);
            var running = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    running.advancing()
                            && running.current().remainingTicks() > 0
                            && running.drive().equals("Creative drive"),
                    "Running ETA/drive wrong");
            MixerRedstoneGameTests.power(h, CONTROLLER.west(), false);
            var inhibited = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    inhibited.inhibited()
                            && !inhibited.advancing()
                            && inhibited.current().remainingTicks() == -1
                            && inhibited.condition().contains("redstone"),
                    "Inhibited ETA fabricated");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void activeFeDriveAvailabilityAndSpinupAreProjected(GameTestHelper h) {
        MixerGameTests.rig(h);
        var pos = new BlockPos(4, 4, 3);
        h.setBlock(pos, MixerContent.state(MixerPartType.FeDrive));
        h.runAtTickTime(35, () -> {
            var controller = (MixerControllerEntity) h.getBlockEntity(CONTROLLER);
            var mixer = controller.getMultiblockController().orElseThrow();
            var empty = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    empty.drive().equals("FE drive") && empty.condition().contains("Insufficient"),
                    "Empty FE state wrong");
            ((MixerFeDriveEntity) h.getBlockEntity(pos)).energy().receiveEnergy(10000, false);
            MixerCombustionGameTests.ticks(mixer, 1);
            var startup = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    startup.condition().contains("Spin-up")
                            && startup.power().contains("FE")
                            && startup.current().remainingTicks() == -1,
                    "Spinup ETA fabricated");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void connectedMotorFuelBufferIsIncludedOnce(GameTestHelper h) {
        MixerGameTests.rig(h);
        var drivePos = new BlockPos(4, 4, 3);
        var portPos = new BlockPos(4, 4, 2);
        h.setBlock(drivePos, MixerContent.state(MixerPartType.CombustionDrive));
        h.setBlock(portPos, MixerContent.state(MixerPartType.ItemPort));
        h.runAtTickTime(35, () -> {
            var controller = (MixerControllerEntity) h.getBlockEntity(CONTROLLER);
            var mixer = controller.getMultiblockController().orElseThrow();
            ((MixerPortEntity) h.getBlockEntity(portPos)).fuelStore().setStackInSlot(0, new ItemStack(Items.COAL, 9));
            h.assertTrue(mixer.isAssembled(), "Fuel fixture unformed: " + mixer.getLastError());
            var view = MixerControllerSnapshot.capture(mixer, controller);
            h.assertTrue(
                    view.resources().stream()
                                    .filter(r -> r.section().equals("Connected buffers · items"))
                                    .mapToInt(r -> r.product().quantity())
                                    .sum()
                            == 9,
                    "Connected fuel buffer absent or duplicated");
            h.assertTrue(
                    view.drive().equals("Combustion drive") && view.power().contains("routed fuel available"),
                    "Fuel availability missing");
            h.succeed();
        });
    }
}
