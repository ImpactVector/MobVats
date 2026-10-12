package net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.config.Config;
import net.impactvector.mobvats.gamecontent.mob.CableCurve;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerRenderDescriptor;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
/** One controller anchors the cached vessel and first-level rotor. */
public final class MixerControllerRenderer implements BlockEntityRenderer<AbstractMixerEntity> {
    // Same authored material family as the roof bearing and construction Shaft.
    private static final ResourceLocation SHAFT_MATERIAL=ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,"textures/block/procedural/t1/mixer_shaft.png");
    private static final ResourceLocation IMPELLER_MATERIAL=ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,"textures/block/procedural/t1/mixer_paddle.png");
    private record Geometry(MixerRenderDescriptor descriptor,MixerTankRenderer.Mesh tank,CableRenderer.Mesh shaft,List<CableRenderer.Mesh> paddles,LiquidCache liquid){}
    private static final class LiquidCache {
        int amount=-1,layer=-1;
        CableRenderer.Mesh mesh=new CableRenderer.Mesh(List.of());
        void update(MixerRenderDescriptor descriptor,int currentAmount) {
            if(amount==currentAmount)return;amount=currentAmount;
            int currentLayer=descriptor.liquidLayer(amount);
            if(layer!=currentLayer){layer=currentLayer;mesh=MixerTankRenderer.liquid(descriptor,layer);}
        }
    }
    private final WeakHashMap<AbstractMixerEntity,Geometry> cache=new WeakHashMap<>();
    public MixerControllerRenderer(BlockEntityRendererProvider.Context context){}
    /** Opt-in client smoke coverage; bakes rendering meshes without constructing or changing a world. */
    public static void assertSmokeGeometry() {
        var assembly=new net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerAssembly(net.minecraft.core.BlockPos.ZERO,2,5,1);
        var descriptor=MixerRenderDescriptor.from(assembly);var mesh=MixerTankRenderer.bake(descriptor);
        if(mesh.walls().quads().isEmpty() || mesh.rim().quads().isEmpty() || mesh.interior().quads().size()!=mesh.walls().quads().size())
            throw new AssertionError("Mixer vessel is missing walls, interior or open rim");
        if(mesh.wallColors().size()!=mesh.walls().quads().size() || mesh.interiorColors().size()!=mesh.interior().quads().size()
            || mesh.rimColors().size()!=mesh.rim().quads().size() || !mesh.rimColors().contains(0xa86d4b))
            throw new AssertionError("Mixer verdigris mesh lost cached materials or copper rim accents");
        for(int color:mesh.wallColors())if(((color>>8)&255)<=((color>>16)&255) || ((color>>8)&255)<=(color&255))
            throw new AssertionError("Mixer wall material is no longer copper-green");
        for(var surface:MixerTankRenderer.liquid(descriptor,16).quads()) {
            if(surface.normal().y!=1 || surface.a().y>=MixerRenderDescriptor.RIM_Y || surface.a().subtract(surface.b()).length()==0)
                throw new AssertionError("Mixer liquid mesh has invalid height, winding or area");
        }
        for(int i=0;i<mesh.walls().quads().size();i++) {
            var outer=mesh.walls().quads().get(i);var inner=mesh.interior().quads().get(i);
            if(Math.abs(outer.normal().dot(inner.normal())+1)>1e-12)throw new AssertionError("Mixer interior normal does not face the opening");
        }
    }
    public int getViewDistance(){return Config.CLIENT.cullDistance.get();}
    public boolean shouldRenderOffScreen(AbstractMixerEntity part){return true;}
    public AABB getRenderBoundingBox(AbstractMixerEntity part){return part.getMultiblockController().filter(MultiblockMixer::isAssembled)
        .map(c -> new AABB(Vec3.atLowerCornerOf(c.getBoundingBox().getMin()),Vec3.atLowerCornerOf(c.getBoundingBox().getMax().offset(1,1,1)))).orElse(new AABB(part.getBlockPos()));}
    public boolean shouldRender(AbstractMixerEntity part,Vec3 camera){return part.isMachineAssembled() && VatRenderDetail.distanceSquared(getRenderBoundingBox(part),camera)<=getViewDistance()*getViewDistance();}
    private static CableRenderer.Mesh rod(Vec3 from,Vec3 to,double width){return CableRenderer.bake(CableCurve.between(from,to,0,2),width);}
    public static CableRenderer.Mesh bakeShaft(MixerRenderDescriptor descriptor){
        return rod(descriptor.shaftBottom(),descriptor.shaftTop(),net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerVesselGeometry.SHAFT_WIDTH);
    }
    public static List<CableRenderer.Mesh> bakePaddles(MixerRenderDescriptor descriptor){
        double reach=descriptor.armReach(),y=descriptor.armY();
        if(reach<=0)return List.of();
        double width=MixerRenderDescriptor.ARM_WIDTH;
        double lower=descriptor.shaftBottom().y+width/2+1.0/128;
        var frame=new ArrayList<CableRenderer.Mesh>();
        // Two intersecting rigid anchor frames, driven by the existing shaft transform.
        // Small reduced vessels retain one frame instead of crowding the shaft.
        for(int axis=0;axis<(reach>.5?2:1);axis++) {
            Vec3 left=axis==0?new Vec3(-reach,y,0):new Vec3(0,y,-reach);
            Vec3 right=axis==0?new Vec3(reach,y,0):new Vec3(0,y,reach);
            Vec3 lowLeft=new Vec3(left.x,lower,left.z),lowRight=new Vec3(right.x,lower,right.z);
            frame.add(rod(left,right,width));
            frame.add(rod(left,lowLeft,width));frame.add(rod(right,lowRight,width));
            frame.add(rod(lowLeft,lowRight,width));
        }
        return List.copyOf(frame);
    }
    public void render(AbstractMixerEntity part,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!part.isMachineAssembled()){cache.remove(part);return;}
        var bounds=getRenderBoundingBox(part);var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var detail=VatRenderDetail.select(VatRenderDetail.distanceSquared(bounds,camera),Config.CLIENT.fullDetailDistance.get(),Config.CLIENT.reducedDetailDistance.get(),Config.CLIENT.cullDistance.get());
        if(detail==VatRenderDetail.CULLED)return;
        part.getMultiblockController().ifPresent(c->net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitRenderer.render(c.conduitGeometry(),detail,Vec3.atLowerCornerOf(part.getBlockPos()),pose,buffers,light));
        var controller=part.getMultiblockController().orElse(null);
        var descriptor=controller==null?null:controller.renderDescriptor().orElse(null);
        if(descriptor==null){cache.remove(part);return;}
        var assembly=descriptor.assembly();
        var geometry=cache.get(part);
        if(geometry==null || geometry.descriptor()!=descriptor){
            geometry=new Geometry(descriptor,MixerTankRenderer.bake(descriptor),bakeShaft(descriptor),bakePaddles(descriptor),new LiquidCache());cache.put(part,geometry);
        }
        var center=Vec3.atLowerCornerOf(assembly.impeller()).add(.5,0,.5).subtract(Vec3.atLowerCornerOf(part.getBlockPos()));
        pose.pushPose();pose.translate(center.x,center.y,center.z);
        MixerTankRenderer.render(geometry.tank(),pose,buffers,light);
        var fluid=controller.workingFluid();geometry.liquid().update(descriptor,fluid.getAmount());
        int liquidLight=light;
        if(!fluid.isEmpty() && !geometry.liquid().mesh.quads().isEmpty()) {
            var surface=net.minecraft.core.BlockPos.containing(Vec3.atLowerCornerOf(assembly.impeller())
                .add(.5,geometry.liquid().mesh.quads().getFirst().a().y,.5));
            liquidLight=net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation.liquidLight(
                net.minecraft.client.renderer.LevelRenderer.getLightColor(part.getLevel(),surface),
                net.minecraft.client.renderer.LevelRenderer.getLightColor(part.getLevel(),surface.above()),
                fluid.getFluidType().getLightLevel(fluid));
        }
        MixerTankRenderer.renderLiquid(geometry.liquid().mesh,fluid,pose,buffers,liquidLight);
        pose.mulPose(Axis.YP.rotationDegrees((float)controller.rotorAnimation().angle(partial)));
        CableRenderer.renderCable(geometry.shaft(),Vec3.ZERO,SHAFT_MATERIAL,pose,buffers,light);
        for(var paddle:geometry.paddles())CableRenderer.renderCable(paddle,Vec3.ZERO,IMPELLER_MATERIAL,pose,buffers,light);
        pose.popPose();
    }
}
