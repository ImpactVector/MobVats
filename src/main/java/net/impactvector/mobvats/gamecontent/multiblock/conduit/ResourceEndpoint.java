package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Private, mutable inventories only. External automation remains behind the machine ports.
 * A role is independent for each resource; the same endpoint may own all three roles. */
public interface ResourceEndpoint {
    BlockPos getBlockPos();
    default boolean routingAvailable(){return true;}
    Set<ConduitTopology.Role> roles(ConduitResource resource);
    default IItemHandlerModifiable items(ConduitTopology.Role role){return null;}
    default List<IItemHandlerModifiable> itemInventories(ConduitTopology.Role role){var handler=items(role);return handler==null?List.of():List.of(handler);}
    default List<FluidTank> fluids(ConduitTopology.Role role){return List.of();}
    default boolean accepts(ItemStack stack){return true;}
    default boolean accepts(FluidStack stack){return true;}
    default List<ConduitTopology.Endpoint> connections(){
        var result=new ArrayList<ConduitTopology.Endpoint>();
        for(var resource:ConduitResource.values())if(!roles(resource).isEmpty())
            result.add(new ConduitTopology.Endpoint(getBlockPos(),resource,roles(resource),null));
        return List.copyOf(result);
    }
}
