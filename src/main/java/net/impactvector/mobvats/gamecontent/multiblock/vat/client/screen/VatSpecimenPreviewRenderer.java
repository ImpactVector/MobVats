package net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatSpecimenSnapshot;
import net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatDioramaLayout;
import net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatSpecimenPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;

/** Fixed schematic scene. Each element has an independent scale and an isolated pose stack. */
final class VatSpecimenPreviewRenderer {
    private VatPartType failedActor;

    void render(
            GuiGraphics g,
            Mob mob,
            VatSpecimenSnapshot selected,
            VatSpecimenPreview preview,
            int x,
            int y,
            int width,
            int height) {
        var actor = selected.primaryProductionActor().orElse(null);
        var type = actor == null ? null : actor.type().equals("tool port") ? VatPartType.ToolPort : VatPartType.Blender;
        if (type != failedActor) failedActor = null;
        var adult = mob == null ? null : mob.getType().getDimensions();
        var layout = VatDioramaLayout.fit(
                width,
                height,
                mob == null ? 1 : mob.getBbWidth(),
                mob == null ? 1 : mob.getBbHeight(),
                adult == null ? 1 : adult.width(),
                adult == null ? 1 : adult.height(),
                actor != null);
        if (mob != null) {
            // Screen-space straps approximate the upper torso without skeletal mappings.
            int attachmentY = y + (int) (layout.centerY() - layout.specimenHeight() * .23f);
            int separation = Math.max(3, (int) (layout.specimenWidth() * .20f));
            for (int offset : new int[] {-separation, separation}) {
                int cableX = x + (int) layout.specimenX() + offset;
                g.fill(cableX, y + 2, cableX + 1, attachmentY, 0xff71868c);
                g.fill(cableX - 1, attachmentY, cableX + 2, attachmentY + 2, 0xff9baeb2);
            }
        }
        g.flush();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var camera = new Quaternionf(dispatcher.cameraOrientation());
        try {
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(new Quaternionf().rotateY((float) Math.PI));
            if (mob != null) {
                var pose = pose(g, x + layout.specimenX(), y + layout.centerY(), layout.specimenScale());
                // Retain the established inventory specimen handedness.
                pose.scale(-1, 1, 1);
                pose.mulPose(Axis.XP.rotationDegrees(VatDioramaLayout.CAMERA_PITCH));
                pose.translate(0, -mob.getBbHeight() / 2, 0);
                try {
                    // Registered renderer: no dispatcher shadows, hitboxes, world registration or ticking.
                    dispatcher.getRenderer(mob).render(mob, 0, 0, pose, g.bufferSource(), 15728880);
                } catch (Exception error) {
                    preview.failed(error);
                } finally {
                    g.flush();
                }
            }
            if (type != null && failedActor != type) {
                try {
                    var renderer = Minecraft.getInstance().getBlockRenderer();
                    // Unclad construction model shares the baked inventory/world fixture geometry.
                    var state = Content.block(type).defaultBlockState();
                    if (renderer.getBlockModel(state)
                            == Minecraft.getInstance().getModelManager().getMissingModel())
                        throw new IllegalStateException("Actor model unavailable");
                    var pose = pose(g, x + layout.actorX(), y + layout.centerY() + 6, layout.actorScale());
                    pose.mulPose(Axis.XP.rotationDegrees(20));
                    // North functional face points left toward the mob and forward toward the viewer.
                    pose.mulPose(Axis.YP.rotationDegrees(45));
                    pose.translate(-.5, -.5, -.5);
                    renderer.renderSingleBlock(state, pose, g.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                } catch (Exception error) {
                    failedActor = type;
                    com.mojang.logging.LogUtils.getLogger().debug("Vat diorama actor unavailable: {}", type, error);
                } finally {
                    g.flush();
                }
            }
        } finally {
            dispatcher.overrideCameraOrientation(camera);
            Lighting.setupFor3DItems();
        }
    }

    private static PoseStack pose(GuiGraphics g, float x, float y, float scale) {
        var pose = new PoseStack();
        pose.mulPose(g.pose().last().pose());
        pose.translate(x, y, 100);
        pose.scale(scale, -scale, -scale);
        return pose;
    }
}
