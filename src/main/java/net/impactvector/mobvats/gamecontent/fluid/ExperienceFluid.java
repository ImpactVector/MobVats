package net.impactvector.mobvats.gamecontent.fluid;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;

/** One canonical conversion shared by combat, recipes, deposits and migration. */
public final class ExperienceFluid {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MB_PER_POINT;

    static {
        var b = new ModConfigSpec.Builder();
        MB_PER_POINT = b.comment("Experience fluid mB per XP point").defineInRange("experienceMbPerPoint", 20, 1, 1000);
        SPEC = b.build();
    }

    public static int ratio() {
        return MB_PER_POINT.get();
    }

    public static FluidStack stack(long points) {
        return new FluidStack(ProductFluids.EXPERIENCE.get(), Math.toIntExact(Math.multiplyExact(points, ratio())));
    }

    private ExperienceFluid() {}
}
