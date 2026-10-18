package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.impactvector.mobvats.MobVats;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;

/** Glass must not occlude liquid when their differently sized quads sort by center. */
@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class FluidBufferRenderTypes extends RenderStateShard {
    public static final RenderType GLASS = RenderType.create(
            "mobvats_buffer_glass",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    @SubscribeEvent
    public static void registerBuffers(RegisterRenderBuffersEvent event) {
        event.registerRenderBuffer(GLASS);
    }

    private FluidBufferRenderTypes() {
        super("mobvats_buffer_glass", () -> {}, () -> {});
    }
}
