package net.impactvector.mobvats.gamecontent.fluid.compound;

import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/** Standard product bucket whose contained fluid retains all data components. */
public class ComponentPreservingBucketItem extends BucketItem {
    public ComponentPreservingBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    public static FluidStack contents(ItemStack stack) {
        var content = stack.get(CompoundFluids.BUCKET_CONTENTS.get());
        return content == null
                ? new FluidStack(((BucketItem) stack.getItem()).content, FluidType.BUCKET_VOLUME)
                : content.copy();
    }

    public static final class Handler extends FluidBucketWrapper {
        public Handler(ItemStack stack) {
            super(stack);
        }

        public FluidStack getFluid() {
            return container.getItem() instanceof ComponentPreservingBucketItem
                    ? contents(container)
                    : super.getFluid();
        }
    }
}
