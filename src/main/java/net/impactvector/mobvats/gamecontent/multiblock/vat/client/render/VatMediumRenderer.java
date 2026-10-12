package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import java.util.WeakHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.impactvector.mobvats.config.Config;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerLiquidPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** One renderer on the unique Vat Controller, with whole-Vat culling and cached liquid geometry. */
public final class VatMediumRenderer implements BlockEntityRenderer<AbstractVatEntity> {
    private record Cached(BlockPos min,BlockPos max,int amount,int capacity,long revision,VatMediumMesh.Mesh mesh){}
    private final WeakHashMap<MultiblockVat,Cached> cache=new WeakHashMap<>();
    public VatMediumRenderer(BlockEntityRendererProvider.Context context){}
    /** The memoized type registered as Sheets.translucentCullBlockSheet() in RenderBuffers. */
    public static RenderType liquidRenderType(){return RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);}
    public boolean shouldRenderOffScreen(AbstractVatEntity part){return true;}
    public int getViewDistance(){return Config.CLIENT.cullDistance.get();}
    public AABB getRenderBoundingBox(AbstractVatEntity part){return part.getMultiblockController().filter(MultiblockVat::isAssembled)
        .map(c->new AABB(Vec3.atLowerCornerOf(c.getBoundingBox().getMin()),Vec3.atLowerCornerOf(c.getBoundingBox().getMax().offset(1,1,1)))).orElse(new AABB(part.getBlockPos()));}
    public boolean shouldRender(AbstractVatEntity part,Vec3 camera){return part.isMachineAssembled()
        && VatRenderDetail.distanceSquared(getRenderBoundingBox(part),camera)<=getViewDistance()*getViewDistance();}
    public void render(AbstractVatEntity part,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(VatRenderDiagnostics.hideLiquid())return;
        var vat=part.getMultiblockController().filter(MultiblockVat::isAssembled).orElse(null);if(vat==null)return;
        var medium=vat.medium();if(medium.isEmpty())return;
        var fluid=medium.cleanPrototype();if(fluid.isEmpty())return;
        var min=vat.getBoundingBox().getMin();var max=vat.getBoundingBox().getMax();var cached=cache.get(vat);
        if(cached==null || !cached.min().equals(min) || !cached.max().equals(max) || cached.amount()!=medium.getFluidAmount() || cached.capacity()!=medium.getCapacity() || cached.revision()!=medium.presentationRevision()){
            cached=new Cached(min,max,medium.getFluidAmount(),medium.getCapacity(),medium.presentationRevision(),VatMediumMesh.bake(part.getLevel(),min,max,medium));cache.put(vat,cached);
        }
        var appearance=IClientFluidTypeExtensions.of(fluid.getFluid());var texture=appearance.getStillTexture(fluid);if(texture==null)return;
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        int tint=MixerLiquidPresentation.compoundTint(fluid,appearance.getTintColor(fluid));
        int alpha=Math.min(160,MixerLiquidPresentation.vertexAlpha(tint));
        // This fixed atlas buffer is flushed by LevelRenderer after opaque BER geometry.
        // A shared entityTranslucent buffer flushes on the next specimen/strap texture,
        // writing liquid depth before the cow and making visibility depend on BER order.
        var buffer=buffers.getBuffer(liquidRenderType());var matrix=pose.last();var origin=Vec3.atLowerCornerOf(part.getBlockPos());
        for(var face:cached.mesh().faces()){
            var first=face.vertices().getFirst();var normal=face.normal();var sample=BlockPos.containing(first.add(.01,.01,.01));
            int packed=MixerLiquidPresentation.liquidLight(LevelRenderer.getLightColor(part.getLevel(),sample),LevelRenderer.getLightColor(part.getLevel(),sample.above()),fluid.getFluidType().getLightLevel(fluid));
            double baseU=Math.floor(face.vertices().stream().mapToDouble(p->normal.getAxis()==Direction.Axis.X?p.z:p.x).min().orElseThrow());
            double baseV=Math.floor(face.vertices().stream().mapToDouble(p->normal==Direction.UP?p.z:p.y).min().orElseThrow());
            for(var point:face.vertices()){
                var p=point.subtract(origin);double u=(normal.getAxis()==Direction.Axis.X?point.z:point.x)-baseU;
                double v=(normal==Direction.UP?point.z:point.y)-baseV;
                buffer.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor((tint>>>16)&255,(tint>>>8)&255,tint&255,alpha)
                    .setUv(sprite.getU((float)u),sprite.getV((float)v)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packed)
                    .setNormal(matrix,normal.getStepX(),normal.getStepY(),normal.getStepZ());
            }
        }
    }
}
