package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import java.util.List;
import java.util.Objects;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.neoforged.neoforge.fluids.FluidStack;

/** One sampled fluid identity (including sampled components) and exact mB for one ingredient slot. */
public record FluidMeterRequirement(String filter, int exactAmountMb) {
    public FluidMeterRequirement {
        Objects.requireNonNull(filter);
        if (filter.startsWith("#") || exactAmountMb <= 0)
            throw new IllegalArgumentException("Fluid meter requires one exact filter and a positive volume");
        ResourceSelectors.fluids(List.of(filter));
    }

    public static FluidMeterRequirement sampled(FluidStack sample, int exactAmountMb) {
        if (sample.isEmpty()) throw new IllegalArgumentException("Empty fluid sample");
        return new FluidMeterRequirement(EndpointState.fluidId(sample), exactAmountMb);
    }

    public boolean matches(FluidStack stack) {
        return ResourceSelectors.fluids(List.of(filter)).test(stack);
    }
}
