package net.impactvector.mobvats.gametest;

import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Only activated by the dedicated Gradle run; this entire package is excluded from the mod jar. */
@EventBusSubscriber(modid = "mobvats")
public final class ManualTestWorld {
    private record Fixture(
            String id,
            String machine,
            boolean formed,
            String method,
            String description,
            BlockPos origin,
            BlockPos probe) {}

    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static int ticks;
    private static long performanceStart;
    private static boolean active;
    private static boolean verified;
    private static boolean operatorsSuspended;
    private static int operatorsResumeTick;
    private static final int Y = -60;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("mobvats.buildTestWorld")) {
            try {
                OperatorVatFixtures.restoreDefinitions(
                        event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
            } catch (Exception error) {
                LogUtils.getLogger().error("Could not restore manual-world operator definitions", error);
            }
            return;
        }
        FIXTURES.clear();
        ticks = 0;
        verified = false;
        operatorsSuspended = false;
        operatorsResumeTick = 0;
        active = true;
        performanceStart = System.nanoTime();
        try {
            var level = event.getServer().overworld();
            // Faster headless settling preserves ordinary game-tick semantics; restore 20 before saving.
            event.getServer().tickRateManager().setTickRate(100);
            level.setDefaultSpawnPos(new BlockPos(0, Y, 0), 0);
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
            // Keep all rows ticking while no players are connected.
            for (int x = -1; x <= 8; x++) for (int z = -2; z <= 11; z++) level.setChunkForced(x, z, true);
            var put = (java.util.function.BiConsumer<BlockPos, net.minecraft.world.level.block.state.BlockState>)
                    level::setBlockAndUpdate;
            for (int row = 0; row < 4; row++) {
                int z = 16 + row * 20;
                for (int x = -4; x <= 76; x++)
                    level.setBlockAndUpdate(
                            new BlockPos(x, Y - 1, z - 7),
                            (row % 2 == 0 ? Blocks.LIME_WOOL : Blocks.RED_WOOL).defaultBlockState());
                sign(
                        level,
                        new BlockPos(-3, Y, z - 5),
                        row < 2 ? "VAT" : "MIXER",
                        row % 2 == 0 ? "POSITIVE" : "NEGATIVE",
                        "",
                        "Supplies nearby");
                supplies(level, new BlockPos(-3, Y, z - 3), row < 2);
            }
            vat(
                    level,
                    "V-01",
                    true,
                    MachineFixtures.MINIMUM_VAT_WIDTH,
                    0,
                    0,
                    "cowLifecycleAndDrainSump",
                    "Minimum valid|3 x 4 x 3 Vat|Cow encoded");
            vat(
                    level,
                    "V-02",
                    true,
                    MachineFixtures.LARGER_VAT_WIDTH,
                    1,
                    0,
                    "captureEncodeAndLargerVat",
                    "Larger valid|5 x 4 x 5 Vat|Cow encoded");
            vat(
                    level,
                    "V-03",
                    false,
                    MachineFixtures.LARGER_VAT_WIDTH,
                    0,
                    1,
                    "grassInteriorRejectsFormation",
                    "Grass block|inside Vat|Remove to repair");
            var min = origin(0, 2);
            MachineFixtures.mixer(
                    put,
                    min,
                    MachineFixtures.MINIMUM_MIXER_WIDTH,
                    MachineFixtures.MINIMUM_MIXER_HEIGHT,
                    MachineFixtures.MINIMUM_MIXER_WIDTH,
                    MachineFixtures.MINIMUM_MIXER_SHAFT,
                    1,
                    false);
            add(
                    level,
                    new Fixture(
                            "M-01",
                            "Mixer",
                            true,
                            "minimumCylinderBaseline",
                            "Minimum valid|3 x 3 x 3 Mixer|2-deep shaft",
                            min,
                            min.offset(0, 1, 1)));
            var large = origin(1, 2);
            AcceptanceMixerFixtures.build(level, large);
            add(
                    level,
                    new Fixture(
                            "M-02",
                            "Mixer",
                            true,
                            "fullHeightAcceptanceMixer",
                            "Larger valid|5 x 4 x 5 Mixer|3-deep shaft",
                            large,
                            large.offset(0, 1, 2)));
            var faults = MachineFixtures.MixerFault.values();
            String[] methods = {
                "missingImpellerRejectsFormation",
                "offCenterImpellerRejectsFormation",
                "gapImmediatelyBelowImpellerRejectsFormation",
                "offAxisShaftRejectsFormation",
                "disconnectedShaftRejectsFormation"
            };
            String[] descriptions = {
                "Missing Impeller|Replace top center",
                "Off-center|Impeller|Move to center",
                "Shaft gap|below Impeller|Fill the gap",
                "Off-axis Shaft|beside rotor|Remove extra",
                "Disconnected|Shaft in corner|Remove extra"
            };
            for (int i = 0; i < faults.length; i++) {
                var o = origin(i, 3);
                AcceptanceMixerFixtures.build(level, o);
                MachineFixtures.mixerFault(put, o, faults[i]);
                add(
                        level,
                        new Fixture(
                                "M-0" + (i + 3), "Mixer", false, methods[i], descriptions[i], o, o.offset(0, 1, 2)));
            }
            OperatorVatFixtures.build(level);
            ProductionWorldFixtures.build(level);
            CreativeProductionFixtures.build(level);
        } catch (Exception e) {
            fail(event.getServer(), e);
        }
    }

    private static BlockPos origin(int column, int row) {
        return new BlockPos(8 + column * 16, Y, 16 + row * 20);
    }

    private static void vat(
            ServerLevel level,
            String id,
            boolean formed,
            int width,
            int column,
            int row,
            String method,
            String description) {
        var o = origin(column, row);
        MachineFixtures.vat(level::setBlockAndUpdate, o, width, width, 4);
        var head = o.offset(width / 2, 2, width / 2);
        ((VatHeadEntity) level.getBlockEntity(head)).encode(ItemEssence.COW);
        if (!formed) MachineFixtures.grassInterior(level::setBlockAndUpdate, o);
        add(level, new Fixture(id, "Vat", formed, method, description, o, head));
    }

    private static void add(ServerLevel level, Fixture fixture) {
        FIXTURES.add(fixture);
        sign(
                level,
                fixture.origin().offset(0, 0, -5),
                "Machine: " + fixture.machine(),
                "Constructed:",
                fixture.formed() ? "Yes" : "No",
                "Test ID: " + fixture.id());
        sign(level, fixture.origin().offset(2, 0, -5), fixture.description().split("\\|"));
    }

    static void sign(ServerLevel level, BlockPos pos, String... lines) {
        level.setBlockAndUpdate(
                pos,
                Blocks.OAK_SIGN
                        .defaultBlockState()
                        .setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, 8));
        var sign = (SignBlockEntity) level.getBlockEntity(pos);
        var text = new SignText();
        for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setChanged();
    }

    private static void supplies(ServerLevel level, BlockPos pos, boolean vat) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(pos);
        int slot = 0;
        if (vat) {
            for (var type : VatPartType.values()) {
                if (slot >= 22) break;
                chest.setItem(slot++, new ItemStack(Content.block(type), 64));
            }
            chest.setItem(slot++, new ItemStack(Content.SYRINGE.get()));
        } else {
            for (var type : MixerPartType.values()) {
                if (slot >= 22) break;
                chest.setItem(slot++, new ItemStack(MixerContent.block(type), 64));
            }
        }
        for (var item : List.of(
                Items.WATER_BUCKET, Items.WHEAT, Items.GLASS_BOTTLE, Items.COW_SPAWN_EGG, Items.EXPERIENCE_BOTTLE))
            if (slot < 27) chest.setItem(slot++, new ItemStack(item, item == Items.WATER_BUCKET ? 1 : 64));
        chest.setChanged();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!active) return;
        try {
            var level = event.getServer().overworld();
            if (!operatorsSuspended && (operatorsResumeTick == 0 || ticks >= operatorsResumeTick))
                OperatorVatFixtures.observe(level);
            ProductionWorldFixtures.observe(level);
            CreativeProductionFixtures.observe(level);
            if (net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitPerformance.ENABLED
                    && (ticks + 1) % 100 == 0) {
                LogUtils.getLogger()
                        .info(
                                "CONDUIT PERFORMANCE tick={} elapsedMs={} {}",
                                ticks + 1,
                                (System.nanoTime() - performanceStart) / 1_000_000,
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitPerformance
                                        .sampleAndReset());
                performanceStart = System.nanoTime();
            }
            if (++ticks == 100) OperatorVatFixtures.verifyFormation(level);
            if (ticks == OperatorVatFixtures.SETTLE_TICKS - 200) {
                OperatorVatFixtures.prepareFeedDemo(level);
                ProductionWorldFixtures.prepareRun(level);
            }
            if (ticks < OperatorVatFixtures.SETTLE_TICKS) return;
            // Four initial cow kills can yield only 160 mB XP; culture requires 250 mB.
            // Keep native processing running for a replacement cohort instead of depending on luck.
            if (!ProductionWorldFixtures.cultureObserved(level)
                    && ticks < ProductionWorldFixtures.observationDeadline()) {
                // Extended headless observation can exceed eight minutes at 100 TPS.
                // Advance ordinary ticks faster; biology still takes its full configured tick count.
                if (ticks == OperatorVatFixtures.SETTLE_TICKS) {
                    OperatorVatFixtures.verify(level, new StringBuilder());
                    // The operator row has finite demonstration storage. Preserve its verified
                    // state by disabling production while the production loop continues.
                    OperatorVatFixtures.pause(level);
                    operatorsSuspended = true;
                    event.getServer().tickRateManager().setTickRate(1000);
                }
                if (ticks % 1000 == 0)
                    LogUtils.getLogger()
                            .info(
                                    "MOBVATS ACCEPTANCE WAIT tick={} deadline={} {}",
                                    ticks,
                                    ProductionWorldFixtures.observationDeadline(),
                                    ProductionWorldFixtures.cultureProgress(level));
                return;
            }
            if (operatorsSuspended) {
                OperatorVatFixtures.resume(level);
                event.getServer().tickRateManager().setTickRate(100);
                operatorsSuspended = false;
                operatorsResumeTick = ticks + 100;
                return;
            }
            if (ticks < operatorsResumeTick) return;
            active = false;
            var manifest = new StringBuilder(
                    "# MobVats manual fixtures\n\nID | Machine | Constructed | GameTest method | Origin | Description\n---|---|---|---|---|---\n");
            for (var fixture : FIXTURES) {
                var entity = level.getBlockEntity(fixture.probe());
                boolean formed = entity instanceof VatHeadEntity head
                        ? head.isMachineAssembled()
                        : entity instanceof MixerControllerEntity mixer
                                && mixer.getMultiblockController()
                                        .map(c -> c.isAssembled())
                                        .orElse(false);
                if (formed != fixture.formed())
                    throw new IllegalStateException(fixture.id() + " / " + fixture.method() + " expected constructed="
                            + fixture.formed() + ", got " + formed);
                String owner = fixture.machine().equals("Vat")
                        ? "VatGameTests"
                        : fixture.id().equals("M-01")
                                ? "MixerCapacityGameTests"
                                : fixture.id().equals("M-02") ? "AcceptanceMixerGameTests" : "MixerGameTests";
                manifest.append(fixture.id() + " | " + fixture.machine() + " | " + formed + " | " + owner + "."
                        + fixture.method() + " | " + fixture.origin().toShortString() + " | "
                        + fixture.description().replace('|', ' ') + "\n");
            }
            // Forced chunks are only needed during generation. The saved world behaves like a normal creative world.
            OperatorVatFixtures.verify(level, manifest);
            // Preserve the existing fixture evidence even when production acceptance finds a gap.
            Files.writeString(Path.of(System.getProperty("mobvats.testWorldPath"), "fixtures.md"), manifest);
            IllegalStateException productionFailure = null;
            try {
                ProductionWorldFixtures.verify(level, manifest);
            } catch (IllegalStateException failure) {
                productionFailure = failure;
            } finally {
                Files.writeString(Path.of(System.getProperty("mobvats.testWorldPath"), "fixtures.md"), manifest);
            }
            try {
                CreativeProductionFixtures.verify(level, manifest);
            } finally {
                Files.writeString(Path.of(System.getProperty("mobvats.testWorldPath"), "fixtures.md"), manifest);
            }
            if (productionFailure != null) throw productionFailure;
            for (int x = -1; x <= 8; x++) for (int z = -2; z <= 11; z++) level.setChunkForced(x, z, false);
            Files.writeString(Path.of(System.getProperty("mobvats.testWorldPath"), "fixtures.md"), manifest);
            verified = true;
            LogUtils.getLogger()
                    .info(
                            "MOBVATS TEST WORLD VERIFIED: {} mirrored fixtures, {} operator Vats, {} observation ticks",
                            FIXTURES.size(),
                            OperatorVatFixtures.count(),
                            ticks);
            event.getServer().tickRateManager().setTickRate(20);
            event.getServer().halt(false);
        } catch (Exception e) {
            fail(event.getServer(), e);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (!Boolean.getBoolean("mobvats.buildTestWorld")) return;
        try {
            var world = Path.of(System.getProperty("mobvats.testWorldPath"));
            var path = world.resolve("level.dat");
            var root = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var data = root.getCompound("Data");
            // Dedicated servers save allowCommands=false; make the result a normal creative singleplayer save.
            data.putBoolean("allowCommands", true);
            data.putString("LevelName", world.getFileName().toString());
            var generation = data.getCompound("WorldGenSettings");
            if (data.getInt("GameType") != 1
                    || generation.getBoolean("generate_features")
                    || !generation
                            .getCompound("dimensions")
                            .getCompound("minecraft:overworld")
                            .getCompound("generator")
                            .getString("type")
                            .equals("minecraft:flat"))
                throw new IllegalStateException("Saved world is not creative superflat with structures disabled");
            net.minecraft.nbt.NbtIo.writeCompressed(root, path);
            if (verified)
                Files.writeString(
                        world.resolve("generation-complete.txt"),
                        "Verified " + FIXTURES.size() + " mirrored fixtures\nVerified " + OperatorVatFixtures.count()
                                + " operator fixtures\nSaved creative superflat, cheats enabled, structures disabled\n");
        } catch (Exception error) {
            LogUtils.getLogger().error("MOBVATS TEST WORLD SAVE VALIDATION FAILED", error);
        }
    }

    private static void fail(net.minecraft.server.MinecraftServer server, Exception error) {
        active = false;
        LogUtils.getLogger().error("MOBVATS TEST WORLD FAILED", error);
        try {
            Files.writeString(
                    Path.of(System.getProperty("mobvats.testWorldPath"), "generation-failed.txt"),
                    error.toString() + "\n");
        } catch (Exception writeError) {
            LogUtils.getLogger().error("Could not save manual-world failure report", writeError);
        }
        if (operatorsSuspended) {
            OperatorVatFixtures.resume(server.overworld());
            operatorsSuspended = false;
        }
        for (int x = -1; x <= 8; x++)
            for (int z = -2; z <= 11; z++) server.overworld().setChunkForced(x, z, false);
        server.tickRateManager().setTickRate(20);
        server.halt(false);
    }
}
