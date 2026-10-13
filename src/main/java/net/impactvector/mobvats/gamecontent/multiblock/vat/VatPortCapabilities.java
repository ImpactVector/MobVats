package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;

@EventBusSubscriber(modid = MobVats.MOD_ID)
public final class VatPortCapabilities {
    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                Content.entityType(VatPartType.ItemPort),
                (part, side) -> ((VatItemPortEntity) part).externalItems());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                Content.entityType(VatPartType.FluidPort),
                (part, side) -> ((VatFluidPortEntity) part).externalFluids());
    }
}
