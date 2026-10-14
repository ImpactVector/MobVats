package net.impactvector.mobvats.gamecontent.multiblock.mixer.client;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render.MixerControllerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Retires cached vessel geometry even when its Controller is no longer rendered. */
@EventBusSubscriber(modid = "mobvats", value = Dist.CLIENT)
public final class MixerRenderLifecycle {
    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide)
            MixerControllerRenderer.retireChunk(level, event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) MixerControllerRenderer.retireLevel(level);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        MixerControllerRenderer.maintainVisualCaches(Minecraft.getInstance().level);
    }

    private MixerRenderLifecycle() {}
}
