package net.impactvector.mobvats.gamecontent.multiblock.mixer.client;

import java.util.IdentityHashMap;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.client.model.MixerShaftModel;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render.MixerControllerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class MixerClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(
                net.impactvector.mobvats.gamecontent.Content.MIXER_CONTROLLER_MENU.get(),
                net.impactvector.mobvats.gamecontent.multiblock.mixer.client.screen.MixerControllerScreen::new);
        event.register(
                net.impactvector.mobvats.gamecontent.Content.METER_MENU.get(),
                net.impactvector.mobvats.gamecontent.multiblock.mixer.client.screen.MixerMeterScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MixerContent.entityType(MixerPartType.Controller), MixerControllerRenderer::new);
    }

    @SubscribeEvent
    public static void bakeModels(ModelEvent.ModifyBakingResult event) {
        var id = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, MixerPartType.Shaft.getSerializedName());
        var models = new IdentityHashMap<BakedModel, MixerShaftModel>();
        event.getModels()
                .replaceAll((location, original) ->
                        location.id().equals(id) && !location.variant().equals("inventory")
                                ? models.computeIfAbsent(original, MixerShaftModel::new)
                                : original);
    }

    private MixerClient() {}
}
