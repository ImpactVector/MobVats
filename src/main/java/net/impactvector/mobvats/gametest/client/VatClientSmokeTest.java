package net.impactvector.mobvats.gametest.client;

import com.mojang.logging.LogUtils;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatGlassState;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.VatGlassModel;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatHeadEntityRenderer;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in developer acceptance run. Inert in ordinary clients. Uses only its dedicated test world. */
@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class VatClientSmokeTest {
    private static final BlockPos BASE = new BlockPos(0, -60, 0);
    private static final BlockPos HEAD = BASE.offset(1, 2, 1);
    private static boolean opened;
    private static int ticks;
    private static boolean initialized;
    private static boolean failed;
    private static final boolean RELOAD = Boolean.getBoolean("mobvats.smokeReload");

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mobvats.clientSmokeTest") || failed) return;
        var mc = Minecraft.getInstance();
        try {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.setScreen(new TitleScreen());
            }
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true;
                String name = "MobVats-Smoke";
                if (mc.gameDirectory
                        .toPath()
                        .resolve("saves/" + name + "/level.dat")
                        .toFile()
                        .exists()) {
                    mc.createWorldOpenFlows().openWorld(name, () -> {});
                } else {
                    mc.createWorldOpenFlows()
                            .createFreshLevel(
                                    name,
                                    new LevelSettings(
                                            name,
                                            GameType.CREATIVE,
                                            false,
                                            Difficulty.PEACEFUL,
                                            true,
                                            new GameRules(),
                                            WorldDataConfiguration.DEFAULT),
                                    new WorldOptions(1234L, false, false),
                                    registries -> registries
                                            .registryOrThrow(Registries.WORLD_PRESET)
                                            .getOrThrow(WorldPresets.FLAT)
                                            .createWorldDimensions(),
                                    mc.screen);
                }
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (!initialized) {
                initialized = true;
                mc.options.hideGui = true;
                mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    level.setDayTime(6000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer());
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, mc.getSingleplayerServer());
                    if (RELOAD) {
                        var head = (VatHeadEntity) level.getBlockEntity(HEAD);
                        require(
                                head != null
                                        && ItemEssence.COW.equals(head.getIdentity())
                                        && head.getMultiblockController()
                                                .map(c -> c.cowState(head.getBlockPos())
                                                                .milkTicks()
                                                        > 0)
                                                .orElse(false),
                                "Disk reload lost encoded identity or production progress");
                        LogUtils.getLogger().info("MOBVATS SMOKE: disk reload identity and progress preserved");
                    } else {
                        level.setBlockAndUpdate(HEAD, Blocks.AIR.defaultBlockState());
                        for (int x = 0; x < 3; x++)
                            for (int y = 0; y < 4; y++)
                                for (int z = 0; z < 3; z++) {
                                    if (x == 1 && z == 1 && y > 0 && y < 3) continue;
                                    level.setBlockAndUpdate(
                                            BASE.offset(x, y, z),
                                            Content.block(y == 0 || y == 3 ? VatPartType.Casing : VatPartType.Glass)
                                                    .defaultBlockState());
                                }
                        level.setBlockAndUpdate(
                                BASE.offset(1, 1, 0),
                                Content.block(VatPartType.Controller).defaultBlockState());
                        level.setBlockAndUpdate(
                                BASE.offset(1, 3, 1),
                                Content.block(VatPartType.Harness).defaultBlockState());
                        level.setBlockAndUpdate(
                                BASE.offset(1, 0, 1),
                                Content.block(VatPartType.Sump).defaultBlockState());
                        level.setBlockAndUpdate(
                                HEAD, Content.block(VatPartType.Head).defaultBlockState());
                        level.setBlockAndUpdate(
                                HEAD.below(), Content.block(VatPartType.Flesh).defaultBlockState());
                    }
                    var player = mc.getSingleplayerServer()
                            .getPlayerList()
                            .getPlayers()
                            .getFirst();
                    player.connection.teleport(5.5, -59.5, -4.5, 36, 5);
                });
            }
            ++ticks;
            if (ticks == 80) {
                check(mc, RELOAD);
                capture(mc, RELOAD ? "04-reloaded.png" : "00-construction.png");
                if (!RELOAD)
                    mc.getSingleplayerServer()
                            .execute(() -> ((VatHeadEntity) mc.getSingleplayerServer()
                                            .overworld()
                                            .getBlockEntity(HEAD))
                                    .encode(ItemEssence.COW));
            }
            if (ticks == 140 && !RELOAD) {
                check(mc, true);
                capture(mc, "01-formed.png");
                mc.getSingleplayerServer()
                        .execute(() -> mc.getSingleplayerServer()
                                .overworld()
                                .setBlockAndUpdate(BASE.offset(0, 1, 1), Blocks.AIR.defaultBlockState()));
            }
            if (ticks == 190 && !RELOAD) {
                check(mc, false);
                capture(mc, "02-disassembled.png");
                mc.getSingleplayerServer()
                        .execute(() -> mc.getSingleplayerServer()
                                .overworld()
                                .setBlockAndUpdate(
                                        BASE.offset(0, 1, 1),
                                        Content.block(VatPartType.Glass).defaultBlockState()));
            }
            if (ticks == 250) {
                check(mc, true);
                if (!RELOAD) capture(mc, "03-reformed.png");
                LogUtils.getLogger().info("MOBVATS CLIENT SMOKE PASSED (reload={})", RELOAD);
                mc.stop();
            }
        } catch (Throwable error) {
            failed = true;
            LogUtils.getLogger().error("MOBVATS CLIENT SMOKE FAILED", error);
            mc.stop();
        }
    }

    private static void check(Minecraft mc, boolean assembled) {
        var vise = mc.getModelManager()
                .getModel(
                        net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMechanismRenderer
                                .VISE_MODEL);
        require(
                vise != mc.getModelManager().getMissingModel()
                        && !vise.getQuads(null, null, RandomSource.create()).isEmpty(),
                "Procedural vise did not bake");
        for (var type : java.util.List.of(VatPartType.FeedPort, VatPartType.Blender, VatPartType.ToolPort)) {
            var item = new net.minecraft.world.item.ItemStack(Content.block(type));
            var model = mc.getItemRenderer().getModel(item, mc.level, null, 0);
            var quads = model.getQuads(null, null, RandomSource.create());
            require(
                    model instanceof net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel
                            && quads.size() >= 24,
                    "Machinery inventory lost 3D geometry: " + type);
            require(
                    quads.stream()
                            .noneMatch(q ->
                                    q.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation())),
                    "Machinery inventory material is missing: " + type);
        }
        for (var item : java.util.List.of(
                net.minecraft.world.item.Items.IRON_SWORD,
                net.minecraft.world.item.Items.SHEARS,
                net.minecraft.world.item.Items.BUCKET)) {
            var model = mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item), mc.level, null, 0);
            var scale = model.getTransforms()
                    .getTransform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .scale;
            require(
                    scale.z() > net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.ToolViseArt.JAW_GAP,
                    "Vise jaws float apart from vanilla tool");
        }
        var head = (VatHeadEntity) mc.level.getBlockEntity(HEAD);
        require(
                head != null && head.isMachineAssembled() == assembled,
                "Unexpected client assembly state; identity=" + (head == null ? null : head.getIdentity()));
        for (var pos : new BlockPos[] {HEAD, HEAD.below()}) {
            var part = (AbstractVatEntity) mc.level.getBlockEntity(pos);
            var state = mc.level.getBlockState(pos);
            var model = mc.getBlockRenderer().getBlockModel(state);
            int quads = model.getQuads(state, null, RandomSource.create(), part.getModelData(), null)
                    .size();
            for (var face : Direction.values())
                quads += model.getQuads(state, face, RandomSource.create(), part.getModelData(), null)
                        .size();
            require(
                    assembled ? quads == 0 : quads > 0,
                    "Construction model visibility incorrect at " + pos + ": " + quads);
        }
        var renderer =
                (VatHeadEntityRenderer) mc.getBlockEntityRenderDispatcher().getRenderer((AbstractVatEntity) head);
        if (assembled) require(renderer.hasVisual(head), "Cow representation has not rendered");
        var descriptor = renderer.getDescriptor(head);
        require(
                assembled ? descriptor != null : descriptor == null,
                "Vat descriptor survived disassembly or was missing after formation");
        if (assembled) {
            require(descriptor == renderer.getDescriptor(head), "Vat geometry was rebuilt each frame");
            require(
                    descriptor.interiorBounds().getXsize() == 1
                            && descriptor.interiorBounds().getYsize() == 2
                            && descriptor.interiorBounds().getZsize() == 1,
                    "Cached interior bounds are incorrect");
            require(
                    renderer.getRenderBoundingBox(head).equals(descriptor.vatBounds()),
                    "Render bounds do not cover the vat");
        }
        for (var pos : new BlockPos[] {BASE.offset(0, 1, 0), BASE.offset(2, 2, 2), BASE.offset(0, 1, 1)}) {
            var glass = (VatGlassEntity) mc.level.getBlockEntity(pos);
            var state = mc.level.getBlockState(pos);
            var model = mc.getBlockRenderer().getBlockModel(state);
            require(model instanceof VatGlassModel, "Glass model wrapper was not installed");
            var data = glass.getModelData();
            var skin = data.get(VatGlassState.PROPERTY);
            require(skin != null && skin.formed() == assembled, "Glass model data did not follow assembly");
            var layers = model.getRenderTypes(state, RandomSource.create(), data);
            require(
                    assembled
                            ? layers.contains(RenderType.translucent()) && !layers.contains(RenderType.cutout())
                            : layers.contains(RenderType.cutout()) && !layers.contains(RenderType.translucent()),
                    "Glass selected duplicate or incorrect render layers");
            if (assembled) {
                var quads = model.getQuads(state, null, RandomSource.create(), data, RenderType.translucent());
                require(
                        quads.stream()
                                .noneMatch(q -> q.getSprite()
                                        .contents()
                                        .name()
                                        .equals(MissingTextureAtlasSprite.getLocation())),
                        "Formed glass material is missing from the block atlas");
                int faces = 0;
                for (var face : Direction.values()) if (skin.hasFace(face)) ++faces;
                require(quads.size() == faces * 18, "Glass skin contains redundant or missing planes");
                for (var face : Direction.values()) {
                    require(
                            model.getQuads(state, face, RandomSource.create(), data, RenderType.translucent())
                                    .isEmpty(),
                            "Glass emitted both general and culled copies");
                    if (skin.hasFace(face)) {
                        float plane = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0 : 1;
                        int axis = face.getAxis() == Direction.Axis.X ? 0 : face.getAxis() == Direction.Axis.Y ? 1 : 2;
                        long outward = quads.stream()
                                .filter(q -> q.getDirection() == face
                                        && Float.intBitsToFloat(q.getVertices()[axis]) == plane)
                                .count();
                        long inward = quads.stream()
                                .filter(q -> q.getDirection() == face.getOpposite()
                                        && Float.intBitsToFloat(q.getVertices()[axis]) == plane)
                                .count();
                        require(outward == 9 && inward == 9, "Far-side plane is missing an inward winding");
                    }
                }
                require(
                        model.getQuads(state, null, RandomSource.create(), data, RenderType.cutout())
                                .isEmpty(),
                        "Formed glass also emitted the construction skin");
            }
        }
        for (var entity : mc.level.entitiesForRendering())
            require(!(entity instanceof Cow), "Render-only Cow entered client entity manager");
        LogUtils.getLogger().info("MOBVATS SMOKE: assembly={}, models and entity isolation correct", assembled);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(
                mc.gameDirectory,
                name,
                mc.getMainRenderTarget(),
                c -> LogUtils.getLogger().info("MOBVATS SMOKE screenshot: {}", name));
    }
}
