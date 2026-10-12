package net.impactvector.mobvats.gamecontent.fluid.compound;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.*;

/** Container metadata support does not make an ordinary product fluid a compound fluid. */
public class ComponentPreservingFluidType extends FluidType {
    public ComponentPreservingFluidType(Properties properties){super(properties);}
    public ItemStack getBucket(FluidStack stack){var bucket=super.getBucket(stack);if(!stack.getComponents().isEmpty())bucket.set(CompoundFluids.BUCKET_CONTENTS.get(),SimpleFluidContent.copyOf(stack.copyWithAmount(BUCKET_VOLUME)));return bucket;}
}
