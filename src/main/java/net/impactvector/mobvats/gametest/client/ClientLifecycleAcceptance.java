package net.impactvector.mobvats.gametest.client;

import com.mojang.logging.LogUtils;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatHeadEntityRenderer;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Bounded live-client probe. No input automation, screenshots, or per-frame logging. */
@EventBusSubscriber(modid = "mobvats", value = Dist.CLIENT)
public final class ClientLifecycleAcceptance {
    private static final BlockPos HEAD = new BlockPos(1, -58, 1);
    private static int phase, ticks, chunkUnloads, chunkLoads, levelUnloads;
    private static Level original;
    private static java.util.Map<?, ?> mixerCache, bufferCache;
    private static Object oldGeometry;
    private static int awayAt;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!enabled()
                || !(event.getLevel() instanceof Level level)
                || !level.isClientSide
                || !target(event.getChunk().getPos())) return;
        ++chunkUnloads;
        LogUtils.getLogger()
                .info("P0 CLIENT chunk unload phase={} {}", phase, VatHeadEntityRenderer.cacheOwnershipDiagnostics());
        if (event.getChunk().getPos().equals(new net.minecraft.world.level.ChunkPos(HEAD)))
            require(empty(), "Chunk event did not retire Vat cache");
        else if (mixerCache != null)
            require(mixerCache.isEmpty() && bufferCache.isEmpty(), "Chunk event did not retire Mixer/Buffer caches");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (enabled()
                && event.getLevel() instanceof Level level
                && level.isClientSide
                && target(event.getChunk().getPos())) {
            ++chunkLoads;
            LogUtils.getLogger().info("P0 CLIENT chunk load phase={}", phase);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (!enabled() || !(event.getLevel() instanceof Level level) || !level.isClientSide) return;
        ++levelUnloads;
        LogUtils.getLogger()
                .info("P0 CLIENT level unload phase={} {}", phase, VatHeadEntityRenderer.cacheOwnershipDiagnostics());
        require(empty(), "Level event did not retire Vat cache");
    }

    static void tick(Minecraft mc) {
        try {
            require(++ticks < 2400, "Timed out client phase=" + phase);
            if (phase == 0) {
                require(populated(), "Actual rendered specimen cache absent after reformation");
                original = mc.level;
                mixerCache = cache(mc, new BlockPos(-6, -59, 2), "cache");
                bufferCache = cache(mc, new BlockPos(-6, -58, 0), "states");
                require(!mixerCache.isEmpty() && !bufferCache.isEmpty(), "Mixer/Buffer did not render actual geometry");
                require(verifyConduitCache(mc), "Initial conduit geometry was not rendered/cached");
                oldGeometry = mixerCache.values().iterator().next();
                phase = 10;
                mc.getSingleplayerServer()
                        .execute(() -> mc.getSingleplayerServer()
                                .overworld()
                                .setBlockAndUpdate(
                                        new BlockPos(-6, -59, 1),
                                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
            } else if (phase == 10 && mixerCache.isEmpty()) {
                phase = 11;
                mc.getSingleplayerServer()
                        .execute(() -> mc.getSingleplayerServer()
                                .overworld()
                                .setBlockAndUpdate(
                                        new BlockPos(-6, -59, 1),
                                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.state(
                                                net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType
                                                        .Glass)));
            } else if (phase == 11 && !mixerCache.isEmpty() && !bufferCache.isEmpty()) {
                require(
                        mixerCache.values().iterator().next() != oldGeometry,
                        "Mixer reformation reused retired geometry");
                LogUtils.getLogger().info("P0 CLIENT Mixer disassembly/reformation retired and rebuilt geometry");
                LogUtils.getLogger()
                        .info("P0 CLIENT before travel {}", VatHeadEntityRenderer.cacheOwnershipDiagnostics());
                teleport(mc, 1024, -59, 1024);
                awayAt = ticks;
                phase = 1;
            } else if (phase == 1 && ticks - awayAt >= 40 && !chunkPresent(mc)) {
                require(empty(), "Cache survived absent client chunk");
                require(
                        mixerCache.isEmpty() && bufferCache.isEmpty(),
                        "Mixer/Buffer cache survived distant chunk loss");
                LogUtils.getLogger()
                        .info(
                                "P0 CLIENT distant chunk absent unloadEvents={} {}",
                                chunkUnloads,
                                VatHeadEntityRenderer.cacheOwnershipDiagnostics());
                teleport(mc, 5.5, -59.5, -4.5);
                phase = 2;
            } else if (phase == 2
                    && chunkLoads >= 2
                    && ready(mc)
                    && populated()
                    && !mixerCache.isEmpty()
                    && !bufferCache.isEmpty()) {
                require(chunkLoads == 4, "Client reload event absent or duplicated");
                LogUtils.getLogger()
                        .info("P0 CLIENT rebuilt after reload {}", VatHeadEntityRenderer.cacheOwnershipDiagnostics());
                phase = 7;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    server.getPlayerList()
                            .getPlayers()
                            .getFirst()
                            .connection
                            .send(new net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket(
                                    new net.minecraft.world.level.ChunkPos(HEAD)));
                    server.getPlayerList()
                            .getPlayers()
                            .getFirst()
                            .connection
                            .send(new net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket(
                                    new net.minecraft.world.level.ChunkPos(-1, 0)));
                });
            } else if (phase == 7 && chunkUnloads == 2 && !chunkPresent(mc)) {
                require(empty(), "Network chunk unload retained cache");
                require(mixerCache.isEmpty() && bufferCache.isEmpty(), "Network unload retained Mixer/Buffer cache");
                phase = 8;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.overworld();
                    server.getPlayerList()
                            .getPlayers()
                            .getFirst()
                            .connection
                            .send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                                    level.getChunkAt(HEAD), level.getLightEngine(), null, null));
                    server.getPlayerList()
                            .getPlayers()
                            .getFirst()
                            .connection
                            .send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                                    level.getChunk(-1, 0), level.getLightEngine(), null, null));
                });
            } else if (phase == 8 && ready(mc) && populated() && !mixerCache.isEmpty() && !bufferCache.isEmpty()) {
                require(chunkLoads == 6, "Network chunk reload event absent or duplicated");
                phase = 9;
                reload = mc.reloadResourcePacks();
            } else if (phase == 9 && reload.isDone()) {
                reload.join();
                require(mixerCache.isEmpty() && bufferCache.isEmpty(), "Resource reload retained old renderer state");
                mixerCache = cache(mc, new BlockPos(-6, -59, 2), "cache");
                bufferCache = cache(mc, new BlockPos(-6, -58, 0), "states");
                phase = 12;
            } else if (phase == 12 && ready(mc) && populated() && !mixerCache.isEmpty() && !bufferCache.isEmpty()) {
                LogUtils.getLogger().info("P0 CLIENT resource reload retired old states and rebuilt renderers");
                phase = 13;
                mc.getSingleplayerServer()
                        .execute(() -> ((net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity)
                                        mc.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(-6, -58, 0)))
                                .tank.setFluid(new net.neoforged.neoforge.fluids.FluidStack(
                                        net.minecraft.world.level.material.Fluids.WATER, 500)));
            } else if (phase == 13
                    && mc.level.getBlockEntity(new BlockPos(-6, -58, 0))
                            instanceof net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity buffer
                    && buffer.visualFluid().getAmount() == 500) {
                var state = bufferCache.values().iterator().next();
                var height = state.getClass().getDeclaredField("height");
                height.setAccessible(true);
                if (height.getDouble(state) != buffer.visualHeight()) return;
                LogUtils.getLogger()
                        .info(
                                "P0 CLIENT changed Buffer presentation rendered amount=500 height={}",
                                buffer.visualHeight());
                if (!verifyConduitCache(mc)) return;
                phase = 6;
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
                require(
                        mc.level == null && levelUnloads == 1 && empty(),
                        "Disconnect did not retire world entries exactly once");
                require(mixerCache.isEmpty() && bufferCache.isEmpty(), "Mixer/Buffer cache survived world departure");
                phase = 3;
            } else if (phase == 3 && mc.level == null && mc.screen instanceof TitleScreen) {
                phase = 4;
                mc.createWorldOpenFlows().openWorld("MobVats-Smoke", () -> {});
            } else if (phase == 4 && ready(mc) && populated() && !mixerCache.isEmpty() && !bufferCache.isEmpty()) {
                require(
                        mc.level != original && chunkLoads == 8,
                        "Rejoin did not load a new world/chunk or duplicated event delivery");
                if (!verifyConduitCache(mc)) return;
                require(
                        VatHeadEntityRenderer.cacheOwnershipDiagnostics().contains("foreignLevels=0"),
                        "Old Level retained");
                LogUtils.getLogger()
                        .info(
                                "P0 CLIENT PROBE PASSED chunkLoads={} chunkUnloads={} levelUnloads={} {}",
                                chunkLoads,
                                chunkUnloads,
                                levelUnloads,
                                VatHeadEntityRenderer.cacheOwnershipDiagnostics());
                mc.stop();
                phase = 5;
            }
        } catch (Throwable error) {
            LogUtils.getLogger().error("P0 CLIENT PROBE FAILED", error);
            mc.stop();
            phase = 5;
        }
    }

    private static void teleport(Minecraft mc, double x, double y, double z) {
        var server = mc.getSingleplayerServer();
        server.execute(
                () -> server.getPlayerList().getPlayers().getFirst().connection.teleport(x, y, z, 36, 5));
    }

    private static boolean ready(Minecraft mc) {
        return mc.level != null
                && chunkPresent(mc)
                && mc.level.getBlockEntity(HEAD) instanceof VatHeadEntity head
                && head.isMachineAssembled();
    }

    private static boolean chunkPresent(Minecraft mc) {
        return mc.level != null
                && mc.level
                                .getChunkSource()
                                .getChunk(0, 0, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false)
                        != null;
    }

    private static java.util.Map<?, ?> cache(Minecraft mc, BlockPos pos, String name) throws Exception {
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(mc.level.getBlockEntity(pos));
        var field = renderer.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (java.util.Map<?, ?>) field.get(renderer);
    }

    private static boolean populated() {
        var summary = VatHeadEntityRenderer.cacheOwnershipDiagnostics();
        return summary.contains("cachedHeads=1 ")
                && summary.contains("specimenMobs=1 ")
                && summary.contains("foreignLevels=0");
    }

    private static boolean empty() {
        return VatHeadEntityRenderer.cacheOwnershipDiagnostics().startsWith("cachedHeads=0 ");
    }

    private static boolean enabled() {
        return Boolean.getBoolean("mobvats.clientLifecycleAcceptance");
    }

    private static boolean target(net.minecraft.world.level.ChunkPos chunk) {
        return chunk.equals(new net.minecraft.world.level.ChunkPos(0, 0))
                || chunk.equals(new net.minecraft.world.level.ChunkPos(-1, 0));
    }

    private static boolean verifyConduitCache(Minecraft mc) throws Exception {
        var part = (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity)
                mc.level.getBlockEntity(new BlockPos(-6, -59, 2));
        var geometry = part.getMultiblockController().orElseThrow().conduitGeometry();
        var field =
                net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitRenderer.class.getDeclaredField(
                        "CACHE");
        field.setAccessible(true);
        return !geometry.pipeSegments().isEmpty() && ((java.util.Map<?, ?>) field.get(null)).containsKey(geometry);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private ClientLifecycleAcceptance() {}
}
