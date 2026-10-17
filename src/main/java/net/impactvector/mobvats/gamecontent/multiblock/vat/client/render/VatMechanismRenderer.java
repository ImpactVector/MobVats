package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.mob.CableCurve;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Topology geometry is baked once. Frames only transform rods and render actual part ItemStacks. */
public final class VatMechanismRenderer {
    public static final net.minecraft.client.resources.model.ModelResourceLocation VISE_MODEL =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/procedural/t1/tool_vise"));
    private static final ResourceLocation MATERIAL =
            ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "textures/blocks/vat_casing_inner.png");
    private static final CableRenderer.Mesh MIDDLE = rod(.11), TIP = rod(.07);

    private static CableRenderer.Mesh rod(double width) {
        return CableRenderer.bake(CableCurve.between(Vec3.ZERO, new Vec3(0, 0, 1), 0, 2), width);
    }

    public record Arm(VatPresentation.Link link, Quaternionf rotation, CableRenderer.Mesh base) {}

    public record Geometry(List<Arm> arms, List<CableRenderer.Mesh> hoses) {}

    public static Geometry bake(VatPresentation presentation) {
        var arms = presentation.tools().stream()
                .map(link -> {
                    var direction = Vec3.atLowerCornerOf(link.inward().getNormal());
                    var travel = link.target().subtract(link.base());
                    var rotation = aimRotation(travel.lengthSqr() > 1e-8 ? travel : direction);
                    var base = CableRenderer.bake(
                            CableCurve.between(link.base(), link.base().add(direction.scale(.14)), 0, 2), .15);
                    return new Arm(link, rotation, base);
                })
                .toList();
        var hoses = presentation.feeds().stream()
                .filter(l -> l.base().distanceToSqr(l.target()) > .0001)
                .map(link -> CableRenderer.bake(
                        net.impactvector.mobvats.gamecontent.multiblock.vat.VatInteractionGeometry.feedCurve(link),
                        net.impactvector.mobvats.gamecontent.multiblock.vat.VatInteractionGeometry.FEED_WIDTH))
                .toList();
        return new Geometry(arms, hoses);
    }

    public static void render(
            Geometry geometry,
            VatHeadEntity head,
            VatRenderDetail detail,
            float partialTicks,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        var origin = Vec3.atLowerCornerOf(head.getBlockPos());
        for (var hose : geometry.hoses()) CableRenderer.renderCable(hose, origin, MATERIAL, pose, buffers, light);
        for (var arm : geometry.arms()) {
            var link = arm.link();
            if (!(head.getLevel().getBlockEntity(link.port()) instanceof VatToolPortEntity tool)) continue;
            double full = link.base().distanceTo(link.target()), idle = Math.min(.28, full);
            double age = head.getLevel().getGameTime() + partialTicks - tool.interactionStart();
            double extension = detail.animates() && link.assigned() && age >= 4 && age < 20
                    ? age < 9 ? (age - 4) / 5 : age < 11 ? 1 : (20 - age) / 9
                    : 0;
            double length = idle + Math.max(0, full - idle) * extension;
            CableRenderer.renderCable(arm.base(), origin, MATERIAL, pose, buffers, light);
            pose.pushPose();
            pose.translate(link.base().x - origin.x, link.base().y - origin.y, link.base().z - origin.z);
            var rotation = new Quaternionf(arm.rotation());
            if (detail.animates() && tool.previousAim() != null && age >= 0 && age < 4) {
                var direction = tool.previousAim().subtract(link.base()).normalize();
                if (direction.lengthSqr() > 1e-8)
                    rotation = aimRotation(direction).slerp(rotation, (float) (age / 4));
            }
            pose.mulPose(rotation);
            segment(MIDDLE, .08, Math.max(.04, length * .65), pose, buffers, light);
            segment(TIP, length * .5, Math.max(.02, length * .5), pose, buffers, light);
            var held = tool.heldItem();
            var renderer = Minecraft.getInstance().getItemRenderer();
            var model = held.isEmpty()
                    ? null
                    : renderer.getModel(held, head.getLevel(), null, link.port().hashCode());
            var scale = model == null
                    ? new Vector3f(.55f, .55f, .55f)
                    : model.getTransforms().getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).scale;
            boolean container = held.getItem() instanceof net.minecraft.world.item.BucketItem
                    || held.getItem() instanceof net.minecraft.world.item.MilkBucketItem;
            var center = ToolViseArt.center(
                    scale, length, held.is(ItemTags.SWORDS), held.is(Content.SYRINGE.get()), container);
            double viseY = center.y, viseZ = center.z;
            // Decorative stem reaches the rear housing; neither item nor physical arm target moves.
            var stem = new Vector3f(0, (float) viseY, (float) (viseZ - 3.5 / 16 - length));
            if (stem.lengthSquared() > 1e-8) {
                pose.pushPose();
                pose.translate(0, 0, length);
                pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f(stem).normalize()));
                segment(TIP, 0, stem.length(), pose, buffers, light);
                pose.popPose();
            }
            pose.pushPose();
            // Fixed 7x5x5 envelope and .45px gap. Generated item depth is 1px;
            // actual hand scales give .85px swords / .55px shears and buckets.
            // Decorative grip follows the hilt/handles/rim; the native item mount is unchanged.
            pose.translate(0, viseY, viseZ);
            pose.translate(-.5, -.5, -.5);
            var mc = Minecraft.getInstance();
            mc.getBlockRenderer()
                    .getModelRenderer()
                    .renderModel(
                            pose.last(),
                            buffers.getBuffer(net.minecraft.client.renderer.RenderType.solid()),
                            null,
                            mc.getModelManager().getModel(VISE_MODEL),
                            1,
                            1,
                            1,
                            light,
                            overlay,
                            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                            net.minecraft.client.renderer.RenderType.solid());
            pose.popPose();
            if (!held.isEmpty()) {
                renderMountedItem(held, model, scale, length, pose, buffers, light, overlay);
            }
            pose.popPose();
        }
    }

    /** Aim local +Z with world-up roll, including the opposite (-Z) facing. */
    public static Quaternionf aimRotation(Vec3 direction) {
        double horizontal = Math.hypot(direction.x, direction.z);
        float yaw = horizontal > 1e-8 ? (float) Math.atan2(direction.x, direction.z) : 0;
        float pitch = (float) -Math.atan2(direction.y, horizontal);
        return new Quaternionf().rotationY(yaw).rotateX(pitch);
    }

    /** Shared native item mount for the world mechanism and the controller miniature. */
    public static void renderMountedItem(
            ItemStack held,
            BakedModel model,
            Vector3f scale,
            double length,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        pose.pushPose();
        try {
            // A generated sword's grip is near (3,3) in its 16-pixel image, measured
            // from the bottom left. Pin that point to the arm tip, not transparent image
            // padding at mid-height. Even the full rear edge stays inside the shell at idle.
            if (held.is(ItemTags.SWORDS)) pose.translate(0, scale.y() * 5.0 / 16, length + scale.x() * 5.0 / 16);
            else pose.translate(0, 0, length + Math.abs(scale.x()) / 2);
            // Native image right (+X) faces the specimen, preserving its natural diagonal.
            // MobVats' syringe has its needle on the image left (-X), so reverse its mount.
            pose.mulPose(Axis.YP.rotationDegrees(held.is(Content.SYRINGE.get()) ? 90 : -90));
            pose.scale(scale.x(), scale.y(), scale.z());
            // NONE avoids applying display/hand rotations again after the mechanical mount.
            // Vanilla dispatch still resolves actual components, model overrides and glint.
            Minecraft.getInstance()
                    .getItemRenderer()
                    .render(held, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        } finally {
            pose.popPose();
        }
    }

    private static void segment(
            CableRenderer.Mesh mesh,
            double start,
            double length,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        pose.pushPose();
        pose.translate(0, 0, start);
        pose.scale(1, 1, (float) length);
        CableRenderer.renderCable(mesh, Vec3.ZERO, MATERIAL, pose, buffers, light);
        pose.popPose();
    }

    private VatMechanismRenderer() {}
}
