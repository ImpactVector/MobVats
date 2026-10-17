package net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;

/** Fixed containment-window view; no mouse input or simulation clock. */
final class VatSpecimenPreviewRenderer {
    private VatSpecimenPreviewRenderer() {}

    static void render(GuiGraphics g, Mob mob, int x, int y, int width, int height) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var camera = new Quaternionf(dispatcher.cameraOrientation());
        boolean hitboxes = dispatcher.shouldRenderHitBoxes();
        // A private pose stack isolates the screen even if a third-party renderer throws mid-pose.
        var pose = new PoseStack();
        pose.mulPose(g.pose().last().pose());
        float scale = Math.min((width - 24) / (mob.getBbWidth() * 1.4f), (height - 16) / mob.getBbHeight());
        // Limit tiny/juvenile enlargement while still fitting unusually large bounding boxes.
        scale = Math.min(48, scale);
        pose.translate(x + width / 2.0, y + height / 2.0, 100);
        pose.scale(scale, scale, -scale);
        pose.translate(0, mob.getBbHeight() / 2.0, 0);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        g.flush();
        try {
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(new Quaternionf().rotateY((float) Math.PI));
            dispatcher.setRenderShadow(false);
            dispatcher.setRenderHitBoxes(false);
            dispatcher.render(mob, 0, 0, 0, 0, 0, pose, g.bufferSource(), 15728880);
        } finally {
            try {
                g.flush();
            } finally {
                dispatcher.overrideCameraOrientation(camera);
                dispatcher.setRenderShadow(true);
                dispatcher.setRenderHitBoxes(hitboxes);
                Lighting.setupFor3DItems();
            }
        }
    }
}
