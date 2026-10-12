package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Snapshot transaction for controller-owned resources only, never external capabilities. */
public final class ResourceReservation {
    record Slot(IItemHandlerModifiable handler,int slot){}
    final Map<Slot,ItemStack> beforeItems=new LinkedHashMap<>(),afterItems=new LinkedHashMap<>();
    final Map<FluidTank,FluidStack> beforeFluids=new LinkedHashMap<>(),afterFluids=new LinkedHashMap<>();
    private final Map<FluidTank,Integer> capacities=new LinkedHashMap<>();
    private boolean completed;
    public ItemStack item(IItemHandlerModifiable inventory,int index){
        return item(new Slot(inventory,index));
    }
    ItemStack item(Slot slot){return afterItems.computeIfAbsent(slot,k->{var stack=k.handler().getStackInSlot(k.slot()).copy();beforeItems.put(k,stack.copy());return stack;});}
    public FluidStack fluid(FluidTank tank){
        return afterFluids.computeIfAbsent(tank,t->{var stack=t.getFluid().copy();beforeFluids.put(t,stack.copy());capacities.put(t,t.getCapacity());return stack;});
    }
    public void item(IItemHandlerModifiable inventory,int index,ItemStack stack){item(inventory,index);afterItems.put(new Slot(inventory,index),stack.copy());}
    public void fluid(FluidTank tank,FluidStack stack){fluid(tank);afterFluids.put(tank,stack.copy());}
    public boolean commit(){return commit(()->{},()->{});}
    boolean commit(Runnable action,Runnable undo){
        if(completed)return false;completed=true;
        for(var e:beforeItems.entrySet())if(e.getKey().slot()>=e.getKey().handler().getSlots() || !ItemStack.matches(e.getValue(),e.getKey().handler().getStackInSlot(e.getKey().slot())))return false;
        for(var e:beforeFluids.entrySet())if(capacities.get(e.getKey())!=e.getKey().getCapacity() || !FluidStack.matches(e.getValue(),e.getKey().getFluid()))return false;
        try{
            afterItems.forEach((s,v)->{if(!ItemStack.matches(beforeItems.get(s),v))s.handler().setStackInSlot(s.slot(),v.copy());});
            afterFluids.forEach((t,v)->{if(!FluidStack.matches(beforeFluids.get(t),v))t.setFluid(v.copy());});
            action.run();
            return true;
        }catch(RuntimeException failure){
            beforeItems.forEach((s,v)->s.handler().setStackInSlot(s.slot(),v.copy()));
            beforeFluids.forEach((t,v)->t.setFluid(v.copy()));
            undo.run();
            throw failure;
        }
    }
}
