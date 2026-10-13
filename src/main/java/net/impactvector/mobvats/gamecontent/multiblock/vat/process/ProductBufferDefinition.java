package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.List;
import java.util.function.Predicate;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.neoforged.neoforge.fluids.FluidStack;

/** Profile-owned metadata; omitted capacity defaults to the historical 4000 mB. */
public record ProductBufferDefinition(List<String> fluids, Integer capacity, boolean input) {
    public ProductBufferDefinition {
        if (fluids == null || fluids.isEmpty()) throw new IllegalArgumentException("buffer.fluids required");
        fluids = List.copyOf(fluids);
    }

    public int limit() {
        return capacity == null ? CowBalance.FLUID_CAPACITY : capacity;
    }

    public Predicate<FluidStack> selector() {
        return ResourceSelectors.fluids(fluids);
    }
}
