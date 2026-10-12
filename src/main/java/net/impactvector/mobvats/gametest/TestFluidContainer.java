package net.impactvector.mobvats.gametest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Items;

/** Dev-only capability fixture; all gametest classes are excluded from the distributable. */
@EventBusSubscriber(modid="mobvats")
public final class TestFluidContainer {
    private static final DataComponentType<SimpleFluidContent> CONTENTS=DataComponentType.<SimpleFluidContent>builder().persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build();
    @SubscribeEvent public static void registerComponent(RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mobvats","test_fluid_container"),()->CONTENTS);
    }
    @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,(stack,context)->new FluidHandlerItemStack(()->CONTENTS,stack,1500),Items.PRISMARINE_SHARD);
    }
}
