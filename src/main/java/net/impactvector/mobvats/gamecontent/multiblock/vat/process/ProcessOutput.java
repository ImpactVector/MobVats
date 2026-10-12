package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
public final class ProcessOutput {
    public static ProcessTransaction.Step item(ItemStackHandler handler,ItemStack output){var before=handler.getStackInSlot(0);if(output.isEmpty() || before.getCount()>Math.min(handler.getSlotLimit(0),output.getMaxStackSize())-output.getCount())return null;if(!before.isEmpty() && !ItemStack.isSameItemSameComponents(before,output))return null;var after=output.copy();after.grow(before.getCount());if(after.getCount()>Math.min(handler.getSlotLimit(0),after.getMaxStackSize()))return null;return ProcessTransaction.item(handler,after);}
    public static ProcessTransaction.Step fluid(FluidTank tank,FluidStack output){var before=tank.getFluid();if(output.isEmpty() || before.getAmount()>tank.getCapacity()-output.getAmount())return null;if(!before.isEmpty() && !FluidStack.isSameFluidSameComponents(before,output))return null;var after=output.copy();after.grow(before.getAmount());if(after.getAmount()>tank.getCapacity() || !tank.isFluidValid(0,after))return null;return ProcessTransaction.fluid(tank,after);}
    public static ProcessTransaction.Step buffered(ItemResultBuffer buffer,java.util.List<ItemStack> drops){var copies=drops.stream().map(ItemStack::copy).toList();return new ProcessTransaction.Step(buffer,buffer::isEmpty,()->buffer.store(copies),buffer::clear);}
    private ProcessOutput(){}
}
