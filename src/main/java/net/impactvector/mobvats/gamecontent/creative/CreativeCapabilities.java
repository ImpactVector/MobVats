package net.impactvector.mobvats.gamecontent.creative;

import net.impactvector.mobvats.MobVats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;

@EventBusSubscriber(modid=MobVats.MOD_ID)
public final class CreativeCapabilities {
    @SubscribeEvent public static void register(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,CreativeContent.ENDPOINT.get(),(e,side)->e.type().item()?e.items():null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CreativeContent.ENDPOINT.get(),(e,side)->e.type().item()?null:e.fluids());
    }
}
