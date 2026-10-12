package net.impactvector.mobvats.gamecontent.fluid.compound;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.*;

public final class CompoundFluidType extends ComponentPreservingFluidType {
    public CompoundFluidType(Properties properties){super(properties);}
    public Component getDescription(FluidStack stack){var name=MixerRecipeRegistry.name(stack,true);return name==null?super.getDescription(stack):name;}
}
