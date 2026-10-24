package net.impactvector.mobvats;

import net.impactvector.mobvats.config.Config;
import net.impactvector.mobvats.gamecontent.Content;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(MobVats.MOD_ID)
public final class MobVats {
    public static final String MOD_ID = "mobvats";

    public MobVats(IEventBus bus, ModContainer container) {
        net.neoforged.neoforge.common.NeoForgeMod.enableMilkFluid();
        Content.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.impactvector.mobvats.gamecontent.multiblock.vat.HostileActivation::lightningStrike);
        net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatSpecimenNetworking.register(bus);
        net.impactvector.mobvats.gamecontent.fluid.compound.CompoundFluids.register(bus);
        net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerDefinitionEvents.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveDefinitionEvents::startup);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveDefinitionEvents::commands);
        Config.initialize(container);
    }
}
