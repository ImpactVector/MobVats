package net.impactvector.mobvats.gamecontent.multiblock.conduit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** World-owned Buffer render state is independent of the reusable world-free shape cache. */
@EventBusSubscriber(modid = "mobvats", value = Dist.CLIENT)
public final class FluidBufferRenderLifecycle {
    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide)
            FluidBufferRenderer.retireChunk(level, event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) FluidBufferRenderer.retireLevel(level);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        FluidBufferRenderer.maintainVisualCaches(Minecraft.getInstance().level);
    }

    private FluidBufferRenderLifecycle() {}
}
