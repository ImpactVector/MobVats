package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import java.util.WeakHashMap;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat;
import net.impactvector.mobvats.gamecontent.multiblock.vat.HarnessStraps;
import net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.config.Config;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Each validated Head renders its own specimen; detail and conduits are coordinated at Vat scope. */
public class VatHeadEntityRenderer implements BlockEntityRenderer<AbstractVatEntity> {
    private static final ResourceLocation STRAP_MATERIAL = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,
        "textures/blocks/vat_casing_inner.png");
    // Values contain geometry and a client specimen, never a part/controller reference back to the weak key.
    private final WeakHashMap<AbstractVatEntity, VisualState> visuals = new WeakHashMap<>();
    private record DetailState(Vec3 camera,AABB bounds,int full,int reduced,int cull,VatRenderDetail detail) {}
    private final WeakHashMap<MultiblockVat,DetailState> details=new WeakHashMap<>();
    private VatRenderDetail detail(AbstractVatEntity entity,VatRenderDescriptor descriptor,Vec3 camera){
        var vat=entity.getMultiblockController().orElseThrow();
        int full=Config.CLIENT.fullDetailDistance.get(),reduced=Config.CLIENT.reducedDetailDistance.get(),cull=Config.CLIENT.cullDistance.get();
        var cached=details.get(vat);
        if(cached==null || !cached.camera().equals(camera) || !cached.bounds().equals(descriptor.vatBounds()) || cached.full()!=full || cached.reduced()!=reduced || cached.cull()!=cull){
            cached=new DetailState(camera,descriptor.vatBounds(),full,reduced,cull,
                VatRenderDetail.select(VatRenderDetail.distanceSquared(descriptor.vatBounds(),camera),full,reduced,cull));details.put(vat,cached);
        }
        return cached.detail();
    }
    private static final class VisualState {
        final long revision;
        final VatRenderDescriptor descriptor;
        net.minecraft.world.entity.Mob specimen;
        boolean specimenAttempted;
        List<CableRenderer.Mesh> straps;
        long presentationRevision = Long.MIN_VALUE;
        VatMechanismRenderer.Geometry mechanisms;
        VisualState(long revision, VatRenderDescriptor descriptor) {
            this.revision = revision;
            this.descriptor = descriptor;
        }
    }
    public VatHeadEntityRenderer(BlockEntityRendererProvider.Context context) {}
    public boolean hasVisual(AbstractVatEntity entity) {
        var state = visualState(entity);
        return state != null && state.specimen != null;
    }
    public VatRenderDescriptor getDescriptor(AbstractVatEntity entity) {
        var state = visualState(entity);
        return state == null ? null : state.descriptor;
    }
    private VisualState visualState(AbstractVatEntity entity) {
        var vat = entity.getMultiblockController().filter(MultiblockVat::isAssembled);
        if (!(entity instanceof VatHeadEntity head) || vat.isEmpty() || entity.getLevel() == null) {
            visuals.remove(entity);
            return null;
        }
        var state = visuals.get(entity);
        if (state == null || state.revision != head.getRenderRevision()
                || state.descriptor.specimenYaw() != head.getSpecimenYaw()) {
            state = new VisualState(head.getRenderRevision(), VatRenderDescriptor.from(vat.get(), head));
            visuals.put(entity, state);
        }
        return state;
    }
    @Override public boolean shouldRenderOffScreen(AbstractVatEntity entity) { return true; }
    @Override public int getViewDistance() { return Config.CLIENT.cullDistance.get(); }
    @Override public boolean shouldRender(AbstractVatEntity entity, Vec3 camera) {
        // Apply the BER gate to the same whole-vat bounds, never to the Head anchor.
        var descriptor = getDescriptor(entity);
        boolean visible=descriptor != null && detail(entity,descriptor,camera)!=VatRenderDetail.CULLED;
        if (!visible && entity instanceof VatHeadEntity head)
            VatRenderDiagnostics.record(head,head.getRenderRevision(),descriptor==null?"not-assembled-or-no-level":"distance-culled",0);
        return visible;
    }
    @Override public AABB getRenderBoundingBox(AbstractVatEntity entity) {
        var descriptor = getDescriptor(entity);
        return descriptor == null ? new AABB(entity.getBlockPos()) : descriptor.vatBounds();
    }
    private static void renderConduits(AbstractVatEntity entity,VatRenderDetail detail,PoseStack pose,MultiBufferSource buffers,int light){
        entity.getMultiblockController().ifPresent(c->net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitRenderer.render(c.conduitGeometry(),detail,Vec3.atLowerCornerOf(entity.getBlockPos()),pose,buffers,light));
    }
    @Override public void render(AbstractVatEntity entity, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = visualState(entity);
        if (state == null) {
            if(entity instanceof VatHeadEntity head)VatRenderDiagnostics.record(head,head.getRenderRevision(),"not-assembled-or-no-level",0);
            return;
        }
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var detail = detail(entity,state.descriptor,camera);
        if (detail == VatRenderDetail.CULLED) {
            VatRenderDiagnostics.record((VatHeadEntity)entity,state.revision,"distance-culled",0);return;
        }
        if(entity.getMultiblockController().orElseThrow().getAssemblies().getFirst().head()==entity)
            renderConduits(entity,detail,pose,buffers,light);
        // This class is only loaded on CLIENT. Never add the visual to any Level entity manager or tick it.
        if (!state.specimenAttempted) {
            state.specimenAttempted=true;
            var id=((VatHeadEntity)entity).getIdentity();
            try {
                if(!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id))throw new IllegalArgumentException("Entity type missing");
                var visual=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).create(entity.getLevel());
                if(!(visual instanceof net.minecraft.world.entity.Mob mob))throw new IllegalArgumentException("Specimen rendering requires a Mob instance");
                net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.apply(mob,((VatHeadEntity)entity).specimenState());
                state.specimen=mob;
                if(mob instanceof net.minecraft.world.entity.AgeableMob ageable)ageable.setBaby(((VatHeadEntity)entity).lifeStage()==net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.JUVENILE);
            }catch(RuntimeException error){
                com.mojang.logging.LogUtils.getLogger().warn("Specimen visual unavailable for {}: {}",id,error.getMessage());
            }
        }
        var specimen = state.specimen;
        if (specimen == null) {
            VatRenderDiagnostics.record((VatHeadEntity)entity,state.revision,"specimen-creation-failed-cached",0);return;
        }
        specimen.setNoAi(true);
        var position = state.descriptor.specimenPosition();
        specimen.setPos(position.x, position.y, position.z);
        if (detail.rendersStraps() && state.straps == null) {
            var definition = MobRenderDefinition.forEntity(((VatHeadEntity)entity).getIdentity());
            state.straps = HarnessStraps.curves(state.descriptor.harnessPositions(),state.descriptor.occupiedBounds(),state.descriptor.attachmentBounds(), definition)
                .stream().map(curve -> CableRenderer.bake(curve, HarnessStraps.THICKNESS)).toList();
        }
        var origin = Vec3.atLowerCornerOf(entity.getBlockPos());
        if (detail.rendersStraps())
            for (var strap : state.straps) CableRenderer.renderCable(strap, origin, STRAP_MATERIAL, pose, buffers, light);
        if (detail.rendersMechanisms()) {
            var head = (VatHeadEntity)entity;
            if (state.mechanisms == null || state.presentationRevision != head.presentationRevision()) {
                state.mechanisms = VatMechanismRenderer.bake(head.presentation());
                state.presentationRevision = head.presentationRevision();
            }
            VatMechanismRenderer.render(state.mechanisms,head,detail,partialTicks,pose,buffers,light,overlay);
        }
        boolean animate=detail.animates() && ((VatHeadEntity)entity).presentation().operational();
        specimen.tickCount = animate ? (int)(entity.getLevel().getGameTime() % Integer.MAX_VALUE) : 0;
        float yaw = state.descriptor.specimenYaw();
        specimen.setYRot(yaw);
        specimen.yRotO = yaw;
        specimen.yBodyRot = specimen.yBodyRotO = yaw;
        specimen.yHeadRot = specimen.yHeadRotO = yaw;
        specimen.setXRot(0);
        specimen.xRotO = 0;
        var player = Minecraft.getInstance().player;
        if (animate && player != null) {
            var delta = player.getEyePosition().subtract(position.add(0, specimen.getEyeHeight(), 0));
            float targetYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            specimen.yHeadRot = specimen.yHeadRotO = yaw + Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), -60, 60);
            specimen.setXRot((float)Math.clamp(-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))), -30, 30));
            specimen.xRotO = specimen.getXRot();
        }
        pose.pushPose();
        pose.translate(position.x - entity.getBlockPos().getX(), position.y - entity.getBlockPos().getY(), position.z - entity.getBlockPos().getZ());
        // Saved body extents also govern visuals after an entity type changes its dimensions.
        var saved=((VatHeadEntity)entity).geometry().bodyBounds(((VatHeadEntity)entity).getBlockPos(),1);
        var runtime=specimen.getType().getDimensions();
        pose.scale((float)(saved.getXsize()/runtime.width()),(float)(saved.getYsize()/runtime.height()),(float)(saved.getZsize()/runtime.width()));
        // Use the registered entity renderer directly, avoiding entity-dispatcher shadows and world registration.
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(specimen);
        var submission=VatRenderDiagnostics.active()?new VatRenderDiagnostics.Submission(buffers):null;
        try {
            renderer.render(specimen, yaw, animate ? partialTicks : 0, pose, submission==null?buffers:submission, light);
            VatRenderDiagnostics.record((VatHeadEntity)entity,state.revision,"render-attempted",submission==null?0:submission.vertices());
        } finally { pose.popPose(); }
    }
}
