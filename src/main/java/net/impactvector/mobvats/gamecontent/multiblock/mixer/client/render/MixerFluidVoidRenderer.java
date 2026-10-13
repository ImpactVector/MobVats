package net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

public final class MixerFluidVoidRenderer implements BlockEntityRenderer<AbstractMixerEntity> {
    public MixerFluidVoidRenderer(BlockEntityRendererProvider.Context context) {}

    public void render(
            AbstractMixerEntity part,
            float partial,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        if (part instanceof MixerFluidVoidEntity sink && sink.flaring())
            net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatVoidRenderer.renderPulse(
                    poses, buffers);
    }
}
