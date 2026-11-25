package net.impactvector.mobvats.gamecontent.journal.client;

import net.impactvector.mobvats.MobVats;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class JournalDebugCommands {
    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        if (FMLLoader.isProduction()) return;
        event.getDispatcher()
                .register(Commands.literal("mobvats_journal_preview").executes(c -> {
                    Minecraft.getInstance()
                            .execute(() -> Minecraft.getInstance().setScreen(new JournalScreen(true)));
                    return 1;
                }));
    }
}
