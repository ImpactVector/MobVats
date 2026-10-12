package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceReservation.Slot;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.*;

/** On-demand routing of controller-owned inventories on the server thread. Never ticks stores.
 * Connectivity supplies candidates; operation plans supply selection and strategy order. */
public final class ResourceRouting {
    private final ConduitTopology topology;
    private final List<ResourceEndpoint> endpoints;
    private record Connection(BlockPos owner,ConduitResource resource){}
    private final Map<Connection,List<ResourceEndpoint>> candidates=new HashMap<>();
    public ResourceRouting(ConduitTopology topology,Collection<? extends ResourceEndpoint> endpoints){
        this.topology=topology;this.endpoints=endpoints.stream().map(e->(ResourceEndpoint)e).sorted(Comparator.comparing(ResourceEndpoint::getBlockPos,ConduitTopology.POSITION)).toList();
    }
    private List<ResourceEndpoint> reachable(ResourceEndpoint owner,ConduitResource resource,ConduitTopology.Role role){
        var physical=candidates.computeIfAbsent(new Connection(owner.getBlockPos().immutable(),resource),key->{
            var connected=topology.connectivity(key.owner(),resource).membership();
            return endpoints.stream().filter(e->!e.getBlockPos().equals(key.owner())
                && (connected.contains(e.getBlockPos()) || e.getBlockPos().distManhattan(key.owner())==1)).toList();
        });
        return physical.stream().filter(e->e!=owner && e.routingAvailable() && e.roles(resource).contains(role)).toList();
    }
    private record Supply(ResourceEndpoint endpoint,ConduitTopology.Role role){}
    private List<Supply> supply(ResourceEndpoint owner,ConduitResource resource){
        var result=new ArrayList<Supply>();for(var role:List.of(STORE,PRODUCER))for(var endpoint:reachable(owner,resource,role))result.add(new Supply(endpoint,role));return result;
    }
    public final class Acquisition {
        private final ResourceReservation changes;
        private final List<ItemStack> items;
        private final List<FluidStack> fluids;
        private final String strategy;
        private boolean completed;
        private Acquisition(ResourceReservation changes,List<ItemStack> items,List<FluidStack> fluids,String strategy){this.changes=changes;this.items=items;this.fluids=fluids;this.strategy=strategy;}
        public List<ItemStack> items(){return items.stream().map(ItemStack::copy).toList();}
        public List<FluidStack> fluids(){return fluids.stream().map(FluidStack::copy).toList();}
        public String strategy(){return strategy;}
        public boolean commit(Runnable operation,Runnable undo){if(completed)return false;completed=true;return changes.commit(operation,undo);}
    }
    /** Reservations accumulate across demands, including repeated requirements and shared port views. */
    public Optional<Acquisition> prepare(ResourceEndpoint consumer,List<ResourceBundle.Strategy> plan){
        if(!consumer.routingAvailable())return Optional.empty();
        for(var strategy:plan){var changes=new ResourceReservation();var items=new ArrayList<ItemStack>();var fluids=new ArrayList<FluidStack>();boolean complete=true;
            if(!strategy.inputs().items().isEmpty() && !consumer.roles(ConduitResource.ITEM).contains(CONSUMER))continue;
            if(!strategy.inputs().fluids().isEmpty() && !consumer.roles(ConduitResource.FLUID).contains(CONSUMER))continue;
            for(var demand:strategy.inputs().items()){
                int remaining=demand.count();var seen=new HashSet<Slot>();
                for(var candidate:supply(consumer,ConduitResource.ITEM))for(var handler:candidate.endpoint().itemInventories(candidate.role())){var e=candidate.endpoint();
                    for(int slot=0;slot<handler.getSlots() && remaining>0;slot++){var key=new Slot(handler,slot);var stack=changes.item(key);
                        if(stack.isEmpty()||!e.accepts(stack)||!consumer.accepts(stack)||!demand.selector().test(stack.copy())||!seen.add(key))continue;
                        int count=Math.min(remaining,stack.getCount());items.add(stack.copyWithCount(count));stack.shrink(count);remaining-=count;
                    }if(remaining==0)break;
                }if(remaining>0){complete=false;break;}
            }
            if(complete)for(var demand:strategy.inputs().fluids()){
                int remaining=demand.amount();var seen=Collections.newSetFromMap(new IdentityHashMap<FluidTank,Boolean>());
                for(var candidate:supply(consumer,ConduitResource.FLUID)){var e=candidate.endpoint();
                    for(var tank:e.fluids(candidate.role())){var stack=changes.fluid(tank);
                        if(stack.isEmpty()||!e.accepts(stack)||!consumer.accepts(stack)||!demand.selector().test(stack.copy())||!seen.add(tank))continue;
                        int count=Math.min(remaining,stack.getAmount());fluids.add(stack.copyWithAmount(count));stack.shrink(count);remaining-=count;if(remaining==0)break;
                    }if(remaining==0)break;
                }if(remaining>0){complete=false;break;}
            }
            if(complete)return Optional.of(new Acquisition(changes,items,fluids,strategy.id()));
        }return Optional.empty();
    }
    private static int insertItems(ResourceReservation changes,ResourceEndpoint destination,ConduitTopology.Role role,ItemStack stack){return insertItems(changes,destination,role,stack,null);}
    private static int insertItems(ResourceReservation changes,ResourceEndpoint destination,ConduitTopology.Role role,ItemStack stack,IItemHandlerModifiable excluded){
        if(!destination.accepts(stack))return 0;int remaining=stack.getCount();
        for(var handler:destination.itemInventories(role))for(int slot=0;handler!=excluded && slot<handler.getSlots() && remaining>0;slot++){
            if(!handler.isItemValid(slot,stack))continue;var key=new Slot(handler,slot);var existing=changes.item(key);
            if(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,stack))continue;
            int moved=Math.min(remaining,Math.max(0,Math.min(handler.getSlotLimit(slot),stack.getMaxStackSize())-existing.getCount()));
            if(moved>0){changes.afterItems.put(key,stack.copyWithCount(existing.getCount()+moved));remaining-=moved;}
        }return stack.getCount()-remaining;
    }
    private static int insertFluid(ResourceReservation changes,ResourceEndpoint destination,ConduitTopology.Role role,FluidStack stack){return insertFluid(changes,destination,role,stack,null);}
    private static int insertFluid(ResourceReservation changes,ResourceEndpoint destination,ConduitTopology.Role role,FluidStack stack,FluidTank excluded){
        if(!destination.accepts(stack))return 0;int remaining=stack.getAmount();
        for(var tank:destination.fluids(role)){if(tank==excluded||!tank.isFluidValid(stack))continue;var existing=changes.fluid(tank);
            if(!existing.isEmpty()&&!FluidStack.isSameFluidSameComponents(existing,stack))continue;
            int moved=Math.min(remaining,Math.max(0,tank.getCapacity()-existing.getAmount()));
            if(moved>0){changes.afterFluids.put(tank,stack.copyWithAmount(existing.getAmount()+moved));remaining-=moved;}if(remaining==0)break;
        }return stack.getAmount()-remaining;
    }
    /** Completion is atomic across item/fluid outputs. Stores first; bounded local production escrow last. */
    public boolean complete(ResourceEndpoint producer,List<ItemStack> items,List<FluidStack> fluids){
        if(!producer.routingAvailable() || !items.isEmpty()&&!producer.roles(ConduitResource.ITEM).contains(PRODUCER) || !fluids.isEmpty()&&!producer.roles(ConduitResource.FLUID).contains(PRODUCER))return false;
        var changes=new ResourceReservation();
        for(var output:items){var remaining=output.copy();for(var store:reachable(producer,ConduitResource.ITEM,STORE)){remaining.shrink(insertItems(changes,store,STORE,remaining));if(remaining.isEmpty())break;}if(!remaining.isEmpty())remaining.shrink(insertItems(changes,producer,PRODUCER,remaining));if(!remaining.isEmpty())return false;}
        for(var output:fluids){var remaining=output.copy();for(var store:reachable(producer,ConduitResource.FLUID,STORE)){remaining.shrink(insertFluid(changes,store,STORE,remaining));if(remaining.isEmpty())break;}if(!remaining.isEmpty())remaining.shrink(insertFluid(changes,producer,PRODUCER,remaining));if(!remaining.isEmpty())return false;}
        return changes.commit(()->{},()->{});
    }
    /** Retry only committed producer escrow; never redistribute store contents. */
    public long publish(ResourceEndpoint producer){return publish(producer,null);}
    public long publish(ResourceEndpoint producer,ConduitResource resource){
        if(!producer.routingAvailable())return 0;var changes=new ResourceReservation();long moved=0;
        if((resource==null||resource==ConduitResource.ITEM)&&producer.roles(ConduitResource.ITEM).contains(PRODUCER)){for(var handler:producer.itemInventories(PRODUCER))for(int slot=0;slot<handler.getSlots();slot++){var key=new Slot(handler,slot);var stack=changes.item(key);for(var store:reachable(producer,ConduitResource.ITEM,STORE)){int count=insertItems(changes,store,STORE,stack,handler);stack.shrink(count);moved+=count;if(stack.isEmpty())break;}}}
        if((resource==null||resource==ConduitResource.FLUID)&&producer.roles(ConduitResource.FLUID).contains(PRODUCER))for(var tank:producer.fluids(PRODUCER)){var stack=changes.fluid(tank);for(var store:reachable(producer,ConduitResource.FLUID,STORE)){int count=insertFluid(changes,store,STORE,stack,tank);stack.shrink(count);moved+=count;if(stack.isEmpty())break;}}
        return moved>0&&changes.commit(()->{},()->{})?moved:0;
    }
    /** Boundary/motor demand moves into its private consumer inventory. Partial capacity is allowed. */
    public long pull(ResourceEndpoint consumer){return pull(consumer,s->true,s->true);}
    public long pull(ResourceEndpoint consumer,Predicate<ItemStack> itemDemand,Predicate<FluidStack> fluidDemand){
        if(!consumer.routingAvailable())return 0;var changes=new ResourceReservation();long moved=0;var seenItems=new HashSet<Slot>();var seenFluids=Collections.newSetFromMap(new IdentityHashMap<FluidTank,Boolean>());
        if(consumer.roles(ConduitResource.ITEM).contains(CONSUMER))for(var candidate:supply(consumer,ConduitResource.ITEM))for(var handler:candidate.endpoint().itemInventories(candidate.role())){var source=candidate.endpoint();if(consumer.itemInventories(CONSUMER).contains(handler))continue;for(int slot=0;slot<handler.getSlots();slot++){var key=new Slot(handler,slot);var stack=changes.item(key);if(stack.isEmpty()||!source.accepts(stack)||!itemDemand.test(stack.copy())||!seenItems.add(key))continue;int count=insertItems(changes,consumer,CONSUMER,stack);stack.shrink(count);moved+=count;}}
        if(consumer.roles(ConduitResource.FLUID).contains(CONSUMER))for(var candidate:supply(consumer,ConduitResource.FLUID))for(var tank:candidate.endpoint().fluids(candidate.role())){var source=candidate.endpoint();if(consumer.fluids(CONSUMER).contains(tank))continue;var stack=changes.fluid(tank);if(stack.isEmpty()||!source.accepts(stack)||!fluidDemand.test(stack.copy())||!seenFluids.add(tank))continue;int count=insertFluid(changes,consumer,CONSUMER,stack);stack.shrink(count);moved+=count;}
        return moved>0&&changes.commit(()->{},()->{})?moved:0;
    }
    /** External drains are consumer demand, including simulation. No periodic boundary staging. */
    public IFluidHandler fluidConsumer(ResourceEndpoint consumer,Predicate<FluidStack> filter){return new IFluidHandler(){
        private List<FluidTank> tanks(){if(ConduitPerformance.ENABLED)++ConduitPerformance.enumerations;var result=new ArrayList<FluidTank>();var seen=Collections.newSetFromMap(new IdentityHashMap<FluidTank,Boolean>());for(var candidate:supply(consumer,ConduitResource.FLUID))for(var tank:candidate.endpoint().fluids(candidate.role()))if(seen.add(tank))result.add(tank);return result;}
        public int getTanks(){return tanks().size();}
        public FluidStack getFluidInTank(int tank){var stack=tanks().get(tank).getFluid();return filter.test(stack)&&consumer.accepts(stack)?stack.copy():FluidStack.EMPTY;}
        public int getTankCapacity(int tank){return tanks().get(tank).getCapacity();}
        public boolean isFluidValid(int tank,FluidStack stack){return false;}
        public int fill(FluidStack stack,FluidAction action){return 0;}
        public FluidStack drain(FluidStack request,FluidAction action){
            if(request.isEmpty()||!filter.test(request)||!consumer.accepts(request)||!consumer.roles(ConduitResource.FLUID).contains(CONSUMER))return FluidStack.EMPTY;
            Predicate<FluidStack> selected=s->FluidStack.isSameFluidSameComponents(s,request)&&filter.test(s);
            long available=0;var seen=Collections.newSetFromMap(new IdentityHashMap<FluidTank,Boolean>());
            for(var candidate:supply(consumer,ConduitResource.FLUID))for(var tank:candidate.endpoint().fluids(candidate.role()))if(candidate.endpoint().accepts(tank.getFluid())&&selected.test(tank.getFluid())&&seen.add(tank))available+=tank.getFluidAmount();
            int amount=(int)Math.min(request.getAmount(),available);if(amount<=0)return FluidStack.EMPTY;
            var bundle=new ResourceBundle(List.of(),List.of(new ResourceBundle.FluidDemand(selected,amount)));
            var acquisition=prepare(consumer,List.of(new ResourceBundle.Strategy("drain",bundle))).orElse(null);
            return acquisition!=null && (action.simulate()||acquisition.commit(()->{},()->{}))?request.copyWithAmount(amount):FluidStack.EMPTY;
        }
        public FluidStack drain(int amount,FluidAction action){if(amount<=0)return FluidStack.EMPTY;for(var tank:tanks()){var stack=tank.getFluid();if(stack.isEmpty()||!filter.test(stack)||!consumer.accepts(stack))continue;var result=drain(stack.copyWithAmount(amount),action);if(!result.isEmpty())return result;}return FluidStack.EMPTY;}
    };}
}
