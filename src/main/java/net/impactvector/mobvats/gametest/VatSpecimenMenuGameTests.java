package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.menu.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatSpecimenMenuGameTests {
    @GameTest(template = "empty", timeoutTicks = 10)
    public static void dioramaPrimaryActorStableAcrossAssignmentOrder(GameTestHelper h) {
        var tool = new VatSpecimenSnapshot.Actor("tool port", 20, "milk", "minecraft:bucket", "Bucket", "Assigned", 0);
        var secondTool = new VatSpecimenSnapshot.Actor("tool port", 30, "shear", "", "", "Assigned", 0);
        var blender = new VatSpecimenSnapshot.Actor("blender", 1, "harvest", "", "", "Assigned", 0);
        var harness = new VatSpecimenSnapshot.Actor("harness", 0, "suspension", "", "", "Assigned", 0);
        var loading = new VatSpecimenSnapshot.Actor("tool port", 0, "tool loading", "", "", "Assigned", 0);
        var actors = new ArrayList<>(List.of(blender, secondTool, tool, harness, loading));
        for (int i = 0; i < actors.size(); i++) {
            Collections.rotate(actors, 1);
            h.assertTrue(
                    actorRow(actors).primaryProductionActor().orElseThrow().equals(tool),
                    "Assignment order changed primary production actor");
        }
        h.assertTrue(
                actorRow(List.of(blender, harness))
                        .primaryProductionActor()
                        .orElseThrow()
                        .equals(blender),
                "Blender-only assignment missing");
        h.assertTrue(
                actorRow(List.of(harness, loading)).primaryProductionActor().isEmpty()
                        && actorRow(List.of()).primaryProductionActor().isEmpty(),
                "Invented production actor");
        h.succeed();
    }

    private static VatSpecimenSnapshot actorRow(List<VatSpecimenSnapshot.Actor> actors) {
        return new VatSpecimenSnapshot(
                UUID.randomUUID(),
                0,
                "minecraft:cow",
                "Cow",
                true,
                "Operational",
                1,
                1,
                1,
                1,
                "None",
                "Waiting",
                List.of(),
                actors,
                "{}");
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void dioramaBoundedFramingAndSeparateActorRegion(GameTestHelper h) {
        // Chicken, cow, sheep, tall/large and wide/low generic bounds; no species-specific fit rules.
        float[][] dimensions = {{.4f, .7f}, {.9f, 1.4f}, {.9f, 1.3f}, {4, 8}, {8, .5f}, {.1f, .1f}};
        for (int width : new int[] {90, 180, 280}) {
            for (var size : dimensions) {
                for (boolean actor : new boolean[] {false, true}) {
                    var adult = VatDioramaLayout.fit(width, 88, size[0], size[1], size[0], size[1], actor);
                    var baby = VatDioramaLayout.fit(width, 88, size[0] / 2, size[1] / 2, size[0], size[1], actor);
                    h.assertTrue(adult.specimenScale() > 0 && adult.specimenScale() <= 64, "Unbounded scale");
                    h.assertTrue(
                            adult.specimenX() - adult.specimenWidth() / 2 >= 0
                                    && adult.specimenX() + adult.specimenWidth() / 2 <= width
                                    && adult.centerY() - adult.specimenHeight() / 2 >= 2
                                    && adult.centerY() + adult.specimenHeight() / 2 <= 86,
                            "Specimen escaped viewport");
                    h.assertTrue(
                            baby.specimenScale() == adult.specimenScale()
                                    && baby.specimenHeight() < adult.specimenHeight(),
                            "Juvenile enlarged to adult size");
                    if (actor) {
                        h.assertTrue(
                                adult.specimenX() + adult.specimenWidth() / 2
                                        < adult.actorX() - adult.actorScale() * .9f,
                                "Actor overlaps specimen region");
                        h.assertTrue(
                                adult.actorX() + adult.actorScale() * .9f <= width
                                        && adult.centerY() + 6 + adult.actorScale() * .9f <= 88,
                                "Actor escaped viewport");
                    }
                }
            }
        }
        boolean invalid = false;
        try {
            VatDioramaLayout.fit(180, 88, Float.NaN, 1, 1, 1, true);
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        h.assertTrue(invalid, "Invalid dimensions accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blockedTimerHasNoCountdownAndPreservesPhase(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            var timer = c.processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.MILK);
            timer.phase = ProcessState.Phase.BLOCKED;
            timer.elapsed = 123;
            c.processEngine().tick();
            var before = save(h);
            var process = VatSpecimenSnapshot.capture(c).getFirst().processes().stream()
                    .filter(p -> p.id().equals(PassiveProcessCompiler.MILK))
                    .findFirst()
                    .orElseThrow();
            h.assertTrue(
                    process.phase().equals("BLOCKED")
                            && process.elapsed() == 123
                            && process.duration() > 0
                            && process.remainingTicks() == -1
                            && process.status().contains("output blocked"),
                    "Blocked timer forecast/phase wrong: " + process);
            h.assertTrue(before.equals(save(h)), "Blocked snapshot mutated process state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void operatorCommandsAndRedstoneRemainDistinct(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            var part = (VatControllerEntity) h.getBlockEntity(CONTROLLER);
            part.handleCommand(net.minecraft.network.protocol.PacketFlow.SERVERBOUND, "deactivate", new CompoundTag());
            h.assertTrue(
                    !c.isMachineActive()
                            && !VatSpecimenNetworking.View.capture(c).enabled(),
                    "Existing deactivate command failed");
            part.handleCommand(net.minecraft.network.protocol.PacketFlow.SERVERBOUND, "activate", new CompoundTag());
            MixerRedstoneGameTests.power(h, CONTROLLER.north(), false);
            var view = VatSpecimenNetworking.View.capture(c);
            h.assertTrue(
                    c.isMachineActive() && view.enabled() && view.inhibited() && !c.processingEnabled(),
                    "Redstone conflated with operator state");
            h.assertTrue(
                    view.specimens().getFirst().status().equals("Redstone inhibited")
                            && view.specimens().getFirst().processes().stream().allMatch(p -> p.remainingTicks() == -1),
                    "Inhibited forecast still running");
            MixerRedstoneGameTests.unpower(h, CONTROLLER.north());
            h.assertTrue(c.processingEnabled(), "Redstone release failed to resume existing behavior");
            h.succeed();
        });
    }

    private static final BlockPos HEAD = new BlockPos(3, 3, 3),
            CONTROLLER = new BlockPos(2, 2, 1),
            TOOL = new BlockPos(3, 2, 5),
            FEED = new BlockPos(3, 2, 1);

    private static VatHeadEntity head(GameTestHelper h) {
        return (VatHeadEntity) h.getBlockEntity(HEAD);
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return head(h).getMultiblockController().orElseThrow();
    }

    private static void rig(GameTestHelper h) {
        VatGameTests.build(h, 5);
        head(h).encode(ItemEssence.COW);
        h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity) h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.setBlock(FEED, Content.state(VatPartType.ItemPort));
        Slice11Fixtures.vatStorage(h, new BlockPos(1, 1, 1), 5, 5);
        Slice11Fixtures.feedFrom(h, FEED, FEED.south());
    }

    private static VatControllerMenu menu(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.CREATIVE);
        return new VatControllerMenu(7, player.getInventory(), (VatControllerEntity) h.getBlockEntity(CONTROLLER));
    }

    private static CompoundTag save(GameTestHelper h) {
        return vat(h).syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void identityBiologyActorsAndReadOnlyProjection(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            h.assertTrue(c.isAssembled(), "Fixture must form");
            var named = head(h).specimenState();
            named.putString(
                    "CustomName",
                    Component.Serializer.toJson(
                            Component.literal("Ada"), h.getLevel().registryAccess()));
            head(h).updateSpecimenState(named);
            ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 8));
            var before = save(h);
            int compilations = c.processEngine().compilationCount();
            var rows = VatSpecimenSnapshot.capture(c);
            h.assertTrue(rows.size() == 1, "One Head must produce one row");
            var s = rows.getFirst();
            h.assertTrue(
                    s.id().equals(head(h).specimenId())
                            && s.head() == h.absolutePos(HEAD).asLong(),
                    "Head identity/position lost");
            h.assertTrue(
                    s.name().equals("Ada") && s.species().equals("minecraft:cow") && s.adult(),
                    "Captured identity/stage lost");
            h.assertTrue(
                    s.yield() == c.yieldMultiplier(h.absolutePos(HEAD))
                            && Math.abs(s.happiness() - (s.yield() - .5) / 1.5) < 1e-9,
                    "Happiness formula diverged");
            h.assertTrue(
                    s.frequency() == c.frequencyMultiplier(h.absolutePos(HEAD))
                            && s.culture() == c.medium().frequency(ItemEssence.COW),
                    "Culture formula diverged");
            h.assertTrue(
                    s.feeding().equals("Item feed available")
                            && s.feedSource()
                                    .equals(c.processEngine()
                                            .state(h.absolutePos(HEAD))
                                            .feedSource()),
                    "Feed projection lost");
            h.assertTrue(
                    s.actors().stream()
                            .anyMatch(a -> a.position() == h.absolutePos(TOOL).asLong()
                                    && a.item().equals("minecraft:iron_sword")),
                    "Assigned tool lost");
            h.assertTrue(
                    s.actors().stream().anyMatch(a -> a.type().equals("harness"))
                            && s.actors().stream().anyMatch(a -> a.type().equals("sump")),
                    "Structure actors lost");
            VatSpecimenNetworking.View.capture(c);
            h.assertTrue(
                    before.equals(save(h)) && compilations == c.processEngine().compilationCount(),
                    "Snapshot mutated persistence or recompiled topology");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void growthTimersAndPausedForecasts(GameTestHelper h) {
        rig(h);
        var baby = new CompoundTag();
        baby.putBoolean("Baby", true);
        head(h).encode(ItemEssence.COW, head(h).geometry(), baby);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            var state = c.processEngine().state(h.absolutePos(HEAD));
            state.stage(LifeStage.JUVENILE);
            state.growth().start();
            state.growth().elapsed = 123;
            c.processEngine().tick();
            var s = VatSpecimenSnapshot.capture(c).getFirst();
            var growth = s.processes().stream()
                    .filter(p -> p.id().equals(PassiveProcessCompiler.GROWTH))
                    .findFirst()
                    .orElseThrow();
            h.assertTrue(
                    !s.adult()
                            && growth.elapsed() == state.growth().elapsed
                            && growth.duration()
                                    == PassiveMobDefinitionRegistry.resolve(ItemEssence.COW)
                                            .juvenile_growth_ticks(),
                    "Growth projection wrong");
            h.assertTrue(
                    growth.rate() == c.processEngine().multiplier(h.absolutePos(HEAD)) && growth.remainingTicks() > 0,
                    "Growth forecast missing authoritative rate");
            c.setMachineActive(false);
            var paused = VatSpecimenSnapshot.capture(c).getFirst();
            h.assertTrue(
                    paused.status().equals("Paused by operator")
                            && paused.processes().stream().allMatch(p -> p.remainingTicks() == -1),
                    "Paused processes show countdowns");
            c.setMachineActive(true);
            var tool = (VatToolPortEntity) h.getBlockEntity(TOOL);
            tool.setInteractionStart(h.getLevel().getGameTime());
            h.assertTrue(
                    VatSpecimenSnapshot.capture(c).getFirst().actors().stream().anyMatch(a -> a.cooldownTicks() == 20),
                    "Actor cooldown missing");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void menuRefreshSelectionReplacementAndDiagnostics(GameTestHelper h) {
        rig(h);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            var m = menu(h);
            var first = VatSpecimenNetworking.View.capture(c);
            m.applyView(first);
            var id = m.selectedId();
            var preview = new VatSpecimenPreview();
            var visual = preview.update(m.selected(), h.getLevel());
            m.applyView(VatSpecimenNetworking.View.decode(first.encode()));
            h.assertTrue(m.selectedId().equals(id) && m.selected().id().equals(id), "Refresh lost selection");
            h.assertTrue(
                    visual != null && preview.update(m.selected(), h.getLevel()) == visual,
                    "Snapshot round trip unnecessarily rebuilt preview");
            h.assertTrue(
                    m.page(0).contains("Simulacra") && !m.page(3).isBlank() && m.slots.isEmpty(),
                    "Diagnostics/inventory contract changed");
            head(h).encode(ResourceLocation.parse("minecraft:pig"));
            var replaced = VatSpecimenNetworking.View.capture(c);
            m.applyView(replaced);
            var replacement = preview.update(m.selected(), h.getLevel());
            h.assertTrue(
                    replacement != null
                            && replacement != visual
                            && replacement.getType() == net.minecraft.world.entity.EntityType.PIG,
                    "Replacement did not rebuild selected preview identity");
            h.assertTrue(
                    !m.selectedId().equals(id) && m.selected().id().equals(head(h).specimenId()),
                    "Replacement inherited identity");
            m.applyView(VatSpecimenNetworking.View.EMPTY);
            h.assertTrue(preview.update(m.selected(), h.getLevel()) == null, "Empty view retained preview");
            h.assertTrue(
                    m.selected() == null && m.view().specimens().isEmpty(),
                    "Removal/disconnection retained stale rows");
            h.succeed();
        });
    }

    private static VatSpecimenSnapshot previewRow(UUID id, String type, boolean adult, String appearance) {
        return new VatSpecimenSnapshot(
                id,
                0,
                type,
                "Preview",
                adult,
                "Operational",
                1,
                1,
                1,
                1,
                "None",
                "Waiting",
                List.of(),
                List.of(),
                appearance);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void previewAgeAppearanceAndDetachedConstruction(GameTestHelper h) {
        var cache = new VatSpecimenPreview();
        var id = UUID.randomUUID();
        var row = previewRow(id, "minecraft:sheep", false, "{Color:14b,Sheared:1b,Baby:1b}");
        var view = new VatSpecimenNetworking.View(true, true, false, List.of(row), List.of("a", "b", "c", "d"));
        var decoded =
                VatSpecimenNetworking.View.decode(view.encode()).specimens().getFirst();
        var sheep = (net.minecraft.world.entity.animal.Sheep) cache.update(decoded, h.getLevel());
        h.assertTrue(
                sheep != null
                        && sheep.isBaby()
                        && sheep.isSheared()
                        && sheep.getColor() == net.minecraft.world.item.DyeColor.RED,
                "Captured appearance or juvenile stage lost");
        h.assertTrue(
                h.getLevel().getEntity(sheep.getUUID()) == null && sheep.isNoAi(), "Preview entered world simulation");
        h.assertTrue(
                sheep.yBodyRot == sheep.yHeadRot && sheep.yBodyRotO == sheep.yHeadRotO && sheep.getXRot() == 0,
                "Preview head/body pose diverged");
        var adult = cache.update(previewRow(id, "minecraft:sheep", true, row.appearance()), h.getLevel());
        h.assertTrue(adult != sheep && !adult.isBaby(), "Runtime adult stage did not override captured baby flag");
        var cow = cache.update(previewRow(id, "minecraft:cow", false, "{}"), h.getLevel());
        h.assertTrue(
                cow != null && cow.isBaby() && cow.getType() == net.minecraft.world.entity.EntityType.COW,
                "Species change with stable UUID did not rebuild");
        cache.clear();
        h.assertTrue(cache.update(row, h.getLevel()) != sheep, "Close/reopen retained old preview");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void previewRefreshInvalidationAndFailureCaching(GameTestHelper h) {
        var cache = new VatSpecimenPreview();
        var id = UUID.randomUUID();
        var row = previewRow(id, "minecraft:sheep", true, "{Color:0b}");
        var first = cache.update(row, h.getLevel());
        h.assertTrue(
                first != null
                        && cache.update(previewRow(id, row.species(), true, row.appearance()), h.getLevel()) == first,
                "Equal render identity rebuilt entity");
        var changed = cache.update(previewRow(id, row.species(), true, "{Color:14b}"), h.getLevel());
        h.assertTrue(
                changed != first
                        && ((net.minecraft.world.entity.animal.Sheep) changed).getColor()
                                == net.minecraft.world.item.DyeColor.RED,
                "Appearance refresh retained stale model");
        var replaced = cache.update(previewRow(UUID.randomUUID(), row.species(), true, "{Color:14b}"), h.getLevel());
        h.assertTrue(replaced != changed, "New UUID retained old entity");
        cache.failed(new IllegalStateException("Simulated renderer failure"));
        h.assertTrue(
                cache.update(previewRow(id, row.species(), true, row.appearance()), h.getLevel()) != null,
                "New key failed to recover");
        cache.failed(new IllegalStateException("Simulated renderer failure"));
        h.assertTrue(cache.update(row, h.getLevel()) == null, "Failed key retried every frame");
        cache.clear();
        h.assertTrue(cache.update(row, h.getLevel()) != null, "Cleared cache did not recover");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void previewMissingInvalidAndFilteredStateSafe(GameTestHelper h) {
        var cache = new VatSpecimenPreview();
        var id = UUID.randomUUID();
        h.assertTrue(cache.update(null, h.getLevel()) == null, "Missing selection failed");
        for (var type : List.of("Unknown", "invalid:missing", "minecraft:item"))
            h.assertTrue(
                    cache.update(previewRow(id, type, true, "{}"), h.getLevel()) == null,
                    "Unrenderable type accepted: " + type);
        h.assertTrue(
                cache.update(previewRow(id, "minecraft:cow", true, "malformed{"), h.getLevel()) == null,
                "Malformed appearance did not fall back");
        var row = previewRow(id, "minecraft:cow", true, "{}");
        h.assertTrue(
                cache.update(row, null) == null && cache.update(row, h.getLevel()) != null,
                "Unavailable world recovery failed");
        var state = new CompoundTag();
        state.putByte("Color", (byte) 14);
        state.putString("Brain", "private");
        net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.registerInteractionStateField("PreviewTestPrivate");
        state.putString("PreviewTestPrivate", "private");
        var appearance = net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.appearance(state);
        h.assertTrue(
                appearance.getByte("Color") == 14
                        && !appearance.contains("Brain")
                        && !appearance.contains("PreviewTestPrivate"),
                "Projection exposed non-presentation state");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void unformedAndMissingIdentitySafe(GameTestHelper h) {
        VatGameTests.build(h, 5);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            var rows = VatSpecimenSnapshot.capture(c);
            h.assertTrue(
                    rows.size() == 1
                            && rows.getFirst().species().equals("Unknown")
                            && rows.getFirst().processes().isEmpty(),
                    "Missing identity not handled");
            var m = menu(h);
            m.applyView(VatSpecimenNetworking.View.capture(c));
            h.assertTrue(m.selected() != null && !m.view().formed(), "Unformed menu failed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void largeChunkedCollectionsRoundTripAtomically(GameTestHelper h) {
        var rows = new ArrayList<VatSpecimenSnapshot>();
        for (int i = 0; i < 512; i++)
            rows.add(new VatSpecimenSnapshot(
                    UUID.randomUUID(),
                    new BlockPos(i, 2, 3).asLong(),
                    "minecraft:cow",
                    "Specimen " + i + " 🧪",
                    true,
                    "Operational",
                    1,
                    2,
                    .5,
                    .5,
                    "None",
                    "Waiting",
                    List.of(new VatSpecimenSnapshot.Process("milk", "RUNNING", "running", 30, 100, 1, 70)),
                    List.of(),
                    "{}"));
        var view = new VatSpecimenNetworking.View(
                true, true, false, rows, List.of("Overview", "Production", "Efficiency", "Diagnostics"));
        var chunks = VatSpecimenNetworking.chunks(7, 1, view.encode());
        h.assertTrue(chunks.size() > 1, "Large list did not exercise chunking");
        var receiver = new VatSpecimenNetworking.Receiver();
        for (int i = 0; i < chunks.size(); i++) {
            var buffer = new RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                VatSpecimenNetworking.Chunk.CODEC.encode(buffer, chunks.get(i));
                var decoded = VatSpecimenNetworking.Chunk.CODEC.decode(buffer);
                var result = receiver.accept(decoded);
                h.assertTrue(
                        i == chunks.size() - 1 ? result.orElseThrow().equals(view) : result.isEmpty(),
                        "Partial generation exposed or list truncated");
            } finally {
                buffer.release();
            }
        }
        h.assertTrue(receiver.accept(chunks.getFirst()).isEmpty(), "Stale generation replay accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deterministicMultipleHeads(GameTestHelper h) {
        MachineFixtures.vat(h::setBlock, new BlockPos(1, 1, 1), 9, 5, 4);
        var center = new BlockPos(5, 3, 3);
        h.setBlock(center, net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(center.below(), net.minecraft.world.level.block.Blocks.AIR);
        for (var p : List.of(new BlockPos(3, 3, 3), new BlockPos(7, 3, 3))) {
            h.setBlock(p, Content.state(VatPartType.Head));
            ((VatHeadEntity) h.getBlockEntity(p)).encode(ItemEssence.COW);
            h.setBlock(p.below(), Content.state(VatPartType.Flesh));
            h.setBlock(p.above(), Content.state(VatPartType.Harness));
            h.setBlock(p.below(2), Content.state(VatPartType.Sump));
        }
        h.runAtTickTime(30, () -> {
            var rows = VatSpecimenSnapshot.capture(vat(h));
            h.assertTrue(
                    rows.size() == 2
                            && rows.get(0).head() == h.absolutePos(HEAD).asLong()
                            && rows.get(1).head()
                                    == h.absolutePos(new BlockPos(7, 3, 3)).asLong(),
                    "Non-deterministic Head order");
            var m = menu(h);
            m.applyView(VatSpecimenNetworking.View.capture(vat(h)));
            m.select(rows.get(1).id());
            m.applyView(new VatSpecimenNetworking.View(
                    true, true, false, List.of(rows.get(1), rows.get(0)), List.of("a", "b", "c", "d")));
            h.assertTrue(m.selectedId().equals(rows.get(1).id()), "Reorder changed UUID selection");
            h.succeed();
        });
    }
}
