package net.impactvector.mobvats.gamecontent.journal.client;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.journal.JournalItem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class JournalClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> JournalItem.open = () -> Minecraft.getInstance().setScreen(new JournalScreen(false)));
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(JournalResources.INSTANCE);
    }
}
