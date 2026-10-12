package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Component-preserving view of one physical fluid identity. Unlike compositions are never aggregated. */
public final class ResourceFluidTank extends FluidTank {
    private final PhysicalFluidStorage storage;
    private final BlockPos endpoint;
    private final java.util.function.Supplier<Fluid> fluidType;
    public ResourceFluidTank(PhysicalFluidStorage storage,BlockPos endpoint,java.util.function.Supplier<Fluid> type){super(0);this.storage=storage;this.endpoint=endpoint;this.fluidType=type;}
    private IFluidHandler handler(boolean input){return storage.handler(endpoint,input,s->s.is(fluidType.get()));}
    public FluidStack getFluid(){
        FluidStack result=FluidStack.EMPTY;
        for(var tank:storage.connected(endpoint)){
            var value=tank.getFluid();if(!value.is(fluidType.get()))continue;
            if(result.isEmpty())result=value.copy();else if(FluidStack.isSameFluidSameComponents(result,value))result.setAmount(Math.addExact(result.getAmount(),value.getAmount()));
        }return result;
    }
    public int getFluidAmount(){return getFluid().getAmount();}
    public int getCapacity(){var identity=getFluid();if(identity.isEmpty())identity=new FluidStack(fluidType.get(),1);final var sample=identity;
        return storage.connected(endpoint).stream().filter(t->t.isEmpty()||FluidStack.isSameFluidSameComponents(t.getFluid(),sample)).filter(t->t.isFluidValid(sample)).mapToInt(FluidTank::getCapacity).sum();}
    public boolean isEmpty(){return getFluid().isEmpty();}
    public boolean isFluidValid(FluidStack s){return s.is(fluidType.get());}
    public int fill(FluidStack s,IFluidHandler.FluidAction a){return handler(true).fill(s,a);}
    public FluidStack drain(FluidStack s,IFluidHandler.FluidAction a){return handler(false).drain(s,a);}
    public FluidStack drain(int amount,IFluidHandler.FluidAction a){var request=getFluid();return request.isEmpty()?FluidStack.EMPTY:drain(request.copyWithAmount(amount),a);}
    public void setFluid(FluidStack after){var before=getFluid();
        if(!after.isEmpty()&&!before.isEmpty()&&!FluidStack.isSameFluidSameComponents(before,after))throw new IllegalArgumentException("Cannot change physical resource identity through a view");
        if(after.getAmount()>before.getAmount()){var delta=after.copyWithAmount(after.getAmount()-before.getAmount());if(fill(delta,IFluidHandler.FluidAction.SIMULATE)!=delta.getAmount())throw new IllegalStateException("Physical capacity required");fill(delta,IFluidHandler.FluidAction.EXECUTE);}
        else if(!before.isEmpty())drain(before.copyWithAmount(before.getAmount()-after.getAmount()),IFluidHandler.FluidAction.EXECUTE);
    }
}
