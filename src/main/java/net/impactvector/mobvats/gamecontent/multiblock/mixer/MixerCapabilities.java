package net.impactvector.mobvats.gamecontent.multiblock.mixer;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPortEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
@EventBusSubscriber(modid=MobVats.MOD_ID)
public final class MixerCapabilities {
    @SubscribeEvent public static void register(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,MixerContent.entityType(MixerPartType.FeDrive),(part,side)->((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFeDriveEntity)part).energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,MixerContent.entityType(MixerPartType.CombustionDrive),(part,side)->((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerCombustionDriveEntity)part).fuel());
        for(var type:java.util.List.of(MixerPartType.FluidPort))event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,MixerContent.entityType(type),(part,side)->((MixerPortEntity)part).fluids());
        for(var type:java.util.List.of(MixerPartType.ItemPort))event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,MixerContent.entityType(type),(part,side)->((MixerPortEntity)part).items());
    }
}
