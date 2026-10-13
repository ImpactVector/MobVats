package net.impactvector.mobvats.gamecontent.fluid.client;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class ProductFluidClient {
    @SubscribeEvent
    public static void bucketColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                new DynamicFluidContainerModel.Colors(), ProductFluids.buckets().toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var fluid : ProductFluids.sources()) {
                ItemBlockRenderTypes.setRenderLayer(fluid, RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(fluid.getFlowing(), RenderType.translucent());
            }
        });
    }

    private ProductFluidClient() {}
}
