package net.impactvector.mobvats.fluid;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.fluid.custom.MeatFluid;
import net.impactvector.mobvats.fluid.custom.NutrientFluid;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModFluids {
    public static final FlowableFluid MEAT_STILL = register("meat_still", new MeatFluid.Still());
    public static final FlowableFluid MEAT_FLOWING = register("meat_flowing", new MeatFluid.Flowing());


    public static final FlowableFluid NUTRIENT_STILL = register("nutrient_still", new NutrientFluid.Still());
    public static final FlowableFluid NUTRIENT_FLOWING = register("nutrient_flowing", new NutrientFluid.Flowing());

    private static FlowableFluid register(String name, FlowableFluid flowableFluid) {
        return Registry.register(Registry.FLUID, new Identifier(MobVats.MOD_ID, name), flowableFluid);
    }



    public static void registerFluids() {

        MobVats.LOGGER.info("Registering ModFluids for " + MobVats.MOD_ID);

    }

}