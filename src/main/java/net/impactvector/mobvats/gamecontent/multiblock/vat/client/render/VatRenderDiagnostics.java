package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.WeakHashMap;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.config.Config;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Development-only, opt-in bounded trace. No packets, persistence or render-state changes. */
@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class VatRenderDiagnostics {
    private record Outcome(long tick, long cacheRevision, String reason, int vertices) {}

    private static final WeakHashMap<VatHeadEntity, Outcome> outcomes = new WeakHashMap<>();
    private static final WeakHashMap<VatHeadEntity, String> previous = new WeakHashMap<>();
    private static long until;
    private static boolean hideLiquid;
    private static Object world;

    public static boolean active() {
        return !FMLLoader.isProduction() && until > System.nanoTime();
    }

    public static boolean hideLiquid() {
        return !FMLLoader.isProduction() && hideLiquid;
    }

    public static void record(VatHeadEntity head, long revision, String reason, int vertices) {
        if (active())
            outcomes.put(
                    head,
                    new Outcome(
                            head.getLevel() == null ? -1 : head.getLevel().getGameTime(), revision, reason, vertices));
    }

    public static final class Submission implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private int vertices;

        public Submission(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        public int vertices() {
            return vertices;
        }

        @Override
        public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
            var target = delegate.getBuffer(type);
            return new VertexConsumer() {
                public VertexConsumer addVertex(float x, float y, float z) {
                    ++vertices;
                    target.addVertex(x, y, z);
                    return this;
                }

                public VertexConsumer setColor(int r, int g, int b, int a) {
                    target.setColor(r, g, b, a);
                    return this;
                }

                public VertexConsumer setUv(float u, float v) {
                    target.setUv(u, v);
                    return this;
                }

                public VertexConsumer setUv1(int u, int v) {
                    target.setUv1(u, v);
                    return this;
                }

                public VertexConsumer setUv2(int u, int v) {
                    target.setUv2(u, v);
                    return this;
                }

                public VertexConsumer setNormal(float x, float y, float z) {
                    target.setNormal(x, y, z);
                    return this;
                }
            };
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        if (FMLLoader.isProduction()) return;
        event.getDispatcher()
                .register(Commands.literal("mobvats_render")
                        .then(Commands.literal("trace")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120))
                                        .executes(c -> {
                                            outcomes.clear();
                                            previous.clear();
                                            until = System.nanoTime()
                                                    + IntegerArgumentType.getInteger(c, "seconds") * 1_000_000_000L;
                                            c.getSource()
                                                    .sendSuccess(
                                                            () -> Component.literal(
                                                                    "Vat render transition trace enabled; see logs/latest.log."),
                                                            false);
                                            return 1;
                                        })))
                        .then(Commands.literal("status").executes(c -> {
                            scan(true);
                            c.getSource()
                                    .sendSuccess(
                                            () -> Component.literal(
                                                    "Loaded nearby Head snapshots written to logs/latest.log."),
                                            false);
                            return 1;
                        }))
                        .then(Commands.literal("liquid")
                                .then(Commands.argument("visible", BoolArgumentType.bool())
                                        .executes(c -> {
                                            hideLiquid = !BoolArgumentType.getBool(c, "visible");
                                            c.getSource()
                                                    .sendSuccess(
                                                            () -> Component.literal(
                                                                    "Vat liquid overlay visible=" + !hideLiquid),
                                                            false);
                                            return 1;
                                        }))));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (FMLLoader.isProduction()) return;
        var level = Minecraft.getInstance().level;
        if (world != level) {
            world = level;
            hideLiquid = false;
            outcomes.clear();
            previous.clear();
            int seconds = Math.clamp(Integer.getInteger("mobvats.renderTraceSeconds", 0), 0, 120);
            until = level == null ? 0 : System.nanoTime() + seconds * 1_000_000_000L;
        }
        if (active() && level != null && level.getGameTime() % 20 == 0) scan(false);
    }

    private static void scan(boolean force) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var center = mc.player.chunkPosition();
        int radius = (Config.CLIENT.cullDistance.get() + 15) / 16 + 1;
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        for (int x = center.x - radius; x <= center.x + radius; x++)
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                var chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var block : chunk.getBlockEntities().values())
                    if (block instanceof VatHeadEntity head) {
                        var vat = head.getMultiblockController().orElse(null);
                        var outcome = outcomes.get(head);
                        var bounds = vat == null
                                ? new net.minecraft.world.phys.AABB(head.getBlockPos())
                                : new net.minecraft.world.phys.AABB(
                                        net.minecraft.world.phys.Vec3.atLowerCornerOf(
                                                vat.getBoundingBox().getMin()),
                                        net.minecraft.world.phys.Vec3.atLowerCornerOf(
                                                vat.getBoundingBox().getMax().offset(1, 1, 1)));
                        var detail = VatRenderDetail.select(
                                VatRenderDetail.distanceSquared(bounds, camera),
                                Config.CLIENT.fullDetailDistance.get(),
                                Config.CLIENT.reducedDetailDistance.get(),
                                Config.CLIENT.cullDistance.get());
                        String snapshot = "pos=" + head.getBlockPos().toShortString() + " identity="
                                + head.getIdentity() + " uuid=" + head.specimenId()
                                + " geometry=" + (head.geometry() != null) + " error=" + head.geometryError() + " life="
                                + head.lifeStage()
                                + " specimenRevision=" + head.getRenderRevision() + " presentationRevision="
                                + head.presentationRevision()
                                + " operational=" + head.presentation().operational() + " assembled="
                                + (vat != null && vat.isAssembled())
                                + " assemblies="
                                + (vat == null ? 0 : vat.getAssemblies().size()) + " detail=" + detail + " bounds="
                                + bounds
                                + " cache=" + (outcome == null ? "none" : outcome.cacheRevision()) + " reason="
                                + (outcome == null ? "no-render-observed" : outcome.reason())
                                + " vertices=" + (outcome == null ? 0 : outcome.vertices()) + " recent="
                                + (outcome != null && mc.level.getGameTime() - outcome.tick() <= 20)
                                + " liquidVisible=" + !hideLiquid;
                        if (force || !snapshot.equals(previous.put(head, snapshot)))
                            com.mojang.logging.LogUtils.getLogger().info("Vat render: {}", snapshot);
                    }
            }
    }

    private VatRenderDiagnostics() {}
}
