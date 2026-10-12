package net.impactvector.mobvats.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative operating balance, independent of recipes and slot count. */
public final class MixerEnergyConfig {
    public static final int CYCLE_TICKS=200;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue FE_PER_CYCLE;
    static {
        var builder=new ModConfigSpec.Builder();
        FE_PER_CYCLE=builder.comment("FE paid per 200 powered Mixer ticks, including startup and idle running.",
            "Independent of slot count. Default 2500 corresponds to 20000 FE per coal at current combustion balance.")
            .defineInRange("mixerFePerCycle",2500,1,100_000_000);
        SPEC=builder.build();
    }
    private MixerEnergyConfig(){}
}
