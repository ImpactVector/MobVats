package net.impactvector.mobvats.gamecontent.multiblock.vat.client;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatHeadEntityRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client event ownership, independent of whether a Head passes the renderer's distance gate. */
@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class VatRenderLifecycle {
    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide)
            VatHeadEntityRenderer.retireChunk(level, event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide)
            VatHeadEntityRenderer.retireLevel(level);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        VatHeadEntityRenderer.maintainVisualCaches(Minecraft.getInstance().level);
    }

    private VatRenderLifecycle() {}
}
