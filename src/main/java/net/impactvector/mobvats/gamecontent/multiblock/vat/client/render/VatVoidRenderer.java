package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Committed disposal briefly lights the single central core on all six sides. */
public final class VatVoidRenderer implements BlockEntityRenderer<AbstractVatEntity> {
    public VatVoidRenderer(BlockEntityRendererProvider.Context context){}
    public void render(AbstractVatEntity part,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(!(part instanceof VatBoundaryEntity e)||!e.flaring())return;
        renderPulse(poses,buffers);
    }
    public static void renderPulse(PoseStack poses,MultiBufferSource buffers){
        var sprite=Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("block/lava_still"));
        var buffer=buffers.getBuffer(RenderType.entitySolid(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));var pose=poses.last();
        float a=4.49f/16,b=11.51f/16;
        float[][] points={{a,a,a},{b,a,a},{b,b,a},{a,b,a},{a,a,b},{b,a,b},{b,b,b},{a,b,b}};
        int[][] faces={{0,1,5,4},{3,7,6,2},{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5}};
        int[][] normals={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
        for(int f=0;f<faces.length;f++)for(int v=0;v<4;v++) {
            var p=points[faces[f][v]];var n=normals[f];
            buffer.addVertex(pose,p[0],p[1],p[2]).setColor(255,245,210,255)
                .setUv(v<2?sprite.getU0():sprite.getU1(),v==0||v==3?sprite.getV0():sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose,n[0],n[1],n[2]);
        }
    }
}
