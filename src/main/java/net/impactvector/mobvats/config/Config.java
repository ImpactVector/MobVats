package net.impactvector.mobvats.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** XR-style dedicated client settings and spec holder, registered only on the physical client. */
public final class Config {
    public static final Client CLIENT;
    private static final ModConfigSpec CLIENT_SPEC;

    static {
        var pair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = pair.getLeft();
        CLIENT_SPEC = pair.getRight();
    }

    public static void initialize(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, MixerEnergyConfig.SPEC, "mobvats-mixer-energy.toml");
        container.registerConfig(
                ModConfig.Type.COMMON,
                net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.SPEC,
                "mobvats-common.toml");
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
            container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "mobvats-client.toml");
    }

    private Config() {}
}
