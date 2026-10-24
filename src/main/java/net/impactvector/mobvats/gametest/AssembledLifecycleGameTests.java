package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Distant, separately ticketed fixtures. Observation never fetches blocks after ticket release. */
@GameTestHolder("mobvats_lifecycle")
@PrefixGameTestTemplate(false)
public final class AssembledLifecycleGameTests {
    private static void control(GameTestHelper h, int kind) {
        var level = h.getLevel();
        var o = new BlockPos(27000001 + kind * 512, 80, 27000001);
        var cp = new ChunkPos(o);
        level.setChunkForced(cp.x, cp.z, true);
        var controller = new IMultiblockController<?>[1];
        var original = new net.minecraft.world.level.chunk.LevelChunk[1];
        var labels = new String[] {"XR2 idle", "Vat", "Mixer", "unassembled shell", "XR2 manual idle"};
        h.runAtTickTime(40, () -> {
            for (var p : BlockPos.betweenClosed(o, o.offset(4, 3, 4))) level.removeBlock(p, false);
            if (kind == 0 || kind == 4) {
                var casing = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("bigreactors:basic_reactorcasing"));
                h.assertTrue(casing != Blocks.AIR, "Actual XR2 runtime is required");
                for (var p : BlockPos.betweenClosed(o, o.offset(2, 2, 2)))
                    level.setBlockAndUpdate(p, casing.defaultBlockState());
                level.setBlockAndUpdate(
                        o.offset(1, 1, 1),
                        BuiltInRegistries.BLOCK
                                .get(ResourceLocation.parse("bigreactors:basic_reactorfuelrod"))
                                .defaultBlockState());
                level.setBlockAndUpdate(
                        o.offset(1, 2, 1),
                        BuiltInRegistries.BLOCK
                                .get(ResourceLocation.parse("bigreactors:basic_reactorcontrolrod"))
                                .defaultBlockState());
                level.setBlockAndUpdate(
                        o.offset(1, 1, 0),
                        BuiltInRegistries.BLOCK
                                .get(ResourceLocation.parse("bigreactors:basic_reactorcontroller"))
                                .defaultBlockState());
            } else if (kind == 2) MachineFixtures.mixer(level::setBlockAndUpdate, o, 5, 4, 5, 2, 2, true);
            else {
                MachineFixtures.vat(level::setBlockAndUpdate, o, 5, 5, 4);
                ((net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity)
                                level.getBlockEntity(o.offset(2, 2, 2)))
                        .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
                Slice11Fixtures.vatStorage(h, o.subtract(h.absolutePos(BlockPos.ZERO)), 5, 5);
                if (kind == 3) level.removeBlock(o.offset(0, 0, 0), false);
            }
        });
        h.runAtTickTime(100, () -> {
            var pos = kind == 2 ? o.offset(0, 1, 2) : o.offset(1, 1, 0);
            controller[0] = ((AbstractMultiblockEntity<?>) level.getBlockEntity(pos))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(
                    controller[0].isAssembled() == (kind != 3),
                    "Control assembly: " + labels[kind] + " " + controller[0].getLastError());
            if (kind == 4) setManualEjection(controller[0]);
            original[0] = level.getChunkAt(o);
            com.mojang.logging.LogUtils.getLogger()
                    .info("LIFECYCLE_CONTROL " + labels[kind] + " release "
                            + ServerChunkEviction.diagnostic(level, cp));
            level.setChunkForced(cp.x, cp.z, false);
        });
        for (int tick : new int[] {101, 102, 103, 104, 105, 110, 120, 180, 500, 1300, 6100, 12100, 24100})
            h.runAtTickTime(tick, () -> {
                com.mojang.logging.LogUtils.getLogger()
                        .info("LIFECYCLE_CONTROL " + labels[kind] + " tick=" + tick + " assembled="
                                + controller[0].isAssembled() + " " + ServerChunkEviction.diagnostic(level, cp));
            });
        h.runAtTickTime(24110, () -> {
            if (kind == 1 || kind == 2 || kind == 4) {
                try {
                    h.assertTrue(
                            ServerChunkEviction.absent(level, cp) && !ServerChunkEviction.loaded(original[0]),
                            labels[kind] + " did not naturally unload");
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(error);
                }
                level.setChunkForced(cp.x, cp.z, true);
                h.assertTrue(level.getChunkAt(o) != original[0], labels[kind] + " chunk instance not replaced");
                return;
            }
            if (kind == 0) {
                var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(cp.toLong());
                h.assertTrue(
                        holder != null && holder.getTicketLevel() == 33 && controller[0].isAssembled(),
                        "XR2 Automatic retention control changed; re-evaluate dependency behavior");
            }
            level.setChunkForced(cp.x, cp.z, true);
            for (var p : BlockPos.betweenClosed(o, o.offset(4, 3, 4))) level.removeBlock(p, false);
            level.setChunkForced(cp.x, cp.z, false);
            h.succeed();
        });
        if (kind == 1 || kind == 2 || kind == 4)
            h.runAtTickTime(24180, () -> {
                var restored = ((AbstractMultiblockEntity<?>)
                                level.getBlockEntity(kind == 2 ? o.offset(0, 1, 2) : o.offset(1, 1, 0)))
                        .getMultiblockController()
                        .orElseThrow();
                h.assertTrue(
                        restored != controller[0] && restored.isAssembled(),
                        labels[kind] + " did not reform intact with a new controller");
                try {
                    if (kind == 4)
                        h.assertTrue(
                                restored.getClass()
                                        .getMethod("getWasteEjectionMode")
                                        .invoke(restored)
                                        .toString()
                                        .equals("Manual"),
                                "XR2 lost waste configuration");
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(error);
                }
                com.mojang.logging.LogUtils.getLogger()
                        .info("LIFECYCLE_CONTROL " + labels[kind]
                                + " PASS actual native intact unload/save/reload/reform");
                for (var p : BlockPos.betweenClosed(o, o.offset(4, 3, 4))) level.removeBlock(p, false);
                level.setChunkForced(cp.x, cp.z, false);
                h.succeed();
            });
    }

    private static void setManualEjection(IMultiblockController<?> controller) {
        try {
            var type =
                    Class.forName("it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.WasteEjectionSetting");
            controller
                    .getClass()
                    .getMethod("setWasteEjectionMode", type)
                    .invoke(controller, type.getField("Manual").get(null));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    @GameTest(batch = "assembledLifecycleControl", template = "empty", timeoutTicks = 24500)
    public static void manualXr2Control(GameTestHelper h) {
        control(h, 4);
    }

    @GameTest(batch = "assembledLifecycleControl", template = "empty", timeoutTicks = 24500)
    public static void actualXr2Control(GameTestHelper h) {
        control(h, 0);
    }

    @GameTest(batch = "assembledLifecycleControl", template = "empty", timeoutTicks = 24500)
    public static void vatControl(GameTestHelper h) {
        control(h, 1);
    }

    @GameTest(batch = "assembledLifecycleControl", template = "empty", timeoutTicks = 24500)
    public static void mixerControl(GameTestHelper h) {
        control(h, 2);
    }

    @GameTest(batch = "assembledLifecycleControl", template = "empty", timeoutTicks = 24500)
    public static void unassembledControl(GameTestHelper h) {
        control(h, 3);
    }
}
