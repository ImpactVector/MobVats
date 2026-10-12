package net.impactvector.mobvats.gametest.client;

import com.mojang.logging.LogUtils;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render.MixerControllerRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Inert unless the existing opt-in client smoke task is explicitly launched. */
@EventBusSubscriber(modid=MobVats.MOD_ID,value=Dist.CLIENT)
public final class MixerClientSmokeTest {
    private static boolean checked;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(checked || !Boolean.getBoolean("mobvats.clientSmokeTest") || Minecraft.getInstance().level==null)return;
        checked=true;MixerControllerRenderer.assertSmokeGeometry();
        LogUtils.getLogger().info("MOBVATS SMOKE: opaque Mixer vessel, interior, rim and liquid mesh assertions passed");
    }
    private MixerClientSmokeTest() {}
}
