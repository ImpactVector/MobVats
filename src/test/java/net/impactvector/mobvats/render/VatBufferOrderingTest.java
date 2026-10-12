package net.impactvector.mobvats.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** CPU-only test of Minecraft's actual buffer scheduling; never opens a window or draws. */
public final class VatBufferOrderingTest {
    static {
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final RenderType COW=RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"));
    private static final RenderType STRAP=RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath("mobvats","textures/blocks/vat_casing_inner.png"));
    private static final RenderType LIQUID=net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMediumRenderer.liquidRenderType();
    private static final class RecordingBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
        final List<RenderType> flushed=new ArrayList<>();
        RecordingBuffers() { super(new ByteBufferBuilder(4096),new LinkedHashMap<>());fixedBuffers.put(LIQUID,new ByteBufferBuilder(4096)); }
        @Override public void endBatch(RenderType type) {
            var builder=startedBuilders.remove(type);
            if(builder!=null) { var mesh=builder.build();if(mesh!=null)mesh.close();flushed.add(type); }
            if(type.equals(lastSharedType))lastSharedType=null;
        }
        public void close() { endBatch();sharedBuffer.close();fixedBuffers.values().forEach(ByteBufferBuilder::close); }
    }
    private static void quad(RecordingBuffers source,RenderType type) {
        if(type==COW) {
            var model=new net.minecraft.client.model.CowModel<net.minecraft.world.entity.animal.Cow>(net.minecraft.client.model.CowModel.createBodyLayer().bakeRoot());
            var submission=new net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDiagnostics.Submission(source);
            for(boolean young:new boolean[]{false,true}) {
                model.young=young;int before=submission.vertices();
                model.renderToBuffer(new com.mojang.blaze3d.vertex.PoseStack(),submission.getBuffer(COW),15728880,0,-1);
                require(submission.vertices()>before,"Initialized adult/juvenile cow model did not submit geometry");
            }
            return;
        }
        var buffer=source.getBuffer(type);
        for(int i=0;i<4;i++)buffer.addVertex(i&1,i>>1,0).setColor(255,255,255,160).setUv(0,0).setOverlay(0).setLight(15728880).setNormal(0,0,1);
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        require(LIQUID==RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),"Liquid type is not Minecraft's fixed translucent atlas buffer");
        require(net.impactvector.mobvats.gamecontent.multiblock.conduit.client.FluidBufferRenderer.translucentType()==LIQUID,"Buffer glass/liquid bypass deferred transparency");
        var old=RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        try(var source=new RecordingBuffers()) {
            quad(source,old);quad(source,STRAP);quad(source,COW);source.endLastBatch();
            require(source.flushed.indexOf(old)<source.flushed.indexOf(COW),"Old path did not reproduce premature translucent flush");
        }
        // Both Head/Controller orders, and two cows with independently ordered Controllers.
        var sequences=List.of(List.of(LIQUID,STRAP,COW),List.of(STRAP,COW,LIQUID),
            List.of(LIQUID,STRAP,COW,LIQUID,STRAP,COW),List.of(STRAP,COW,LIQUID,STRAP,COW,LIQUID),
            List.of(LIQUID,LIQUID,STRAP,COW,STRAP,COW),List.of(STRAP,COW,STRAP,COW,LIQUID,LIQUID));
        for(var sequence:sequences)try(var source=new RecordingBuffers()) {
            sequence.forEach(type->quad(source,type));
            require(!source.flushed.contains(LIQUID),"Liquid flushed while BERs were submitting opaque geometry");
            // LevelRenderer's actual ordering at the end of the opaque BER pass.
            source.endLastBatch();source.endBatch(LIQUID);
            require(source.flushed.lastIndexOf(COW)<source.flushed.indexOf(LIQUID),"Cow drawn after liquid");
        }
        System.out.println("PASS: old premature flush reproduced; adult/juvenile cow models submit vertices; deferred liquid follows all cows in six BER orders.");
    }
}
