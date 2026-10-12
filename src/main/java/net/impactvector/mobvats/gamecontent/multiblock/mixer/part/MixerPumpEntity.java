package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.*;

/** Thin pump boundary adapter; the controller schedules shared world-fluid mechanics. */
public final class MixerPumpEntity extends AbstractMixerEntity implements WorldFluidPumps.Endpoint {
    private String filter="";
    private String status="Idle";
    public MixerPumpEntity(MixerPartType type,BlockPos pos,BlockState state){super(type,pos,state);}
    public String filter(){return filter;}
    public void filter(String value){if(!value.isEmpty())ResourceSelectors.fluids(java.util.List.of(value));filter=value;setChanged();notifyBlockUpdate();}
    public boolean accepts(FluidStack stack){return isMachineAssembled() && !stack.isEmpty() && (filter.isEmpty() || ResourceSelectors.fluids(java.util.List.of(filter)).test(stack));}
    public String status(){return isMachineAssembled()?status:"Unformed";}
    public void status(String value){status=value;}
    public boolean configure(Player player,InteractionHand hand){
        if(!player.isShiftKeyDown())return false;
        if(level.isClientSide)return true;
        if(isMachineAssembled()){player.displayClientMessage(Component.translatable("message.mobvats.filter_locked"),true);return true;}
        var held=player.getItemInHand(hand);String value="";
        if(!held.isEmpty()){var sample=FluidUtil.getFluidContained(held);if(sample.isEmpty())return true;value=EndpointState.fluidId(sample.get());}
        filter(value);player.displayClientMessage(value.isEmpty()?Component.translatable("message.mobvats.filter_cleared"):Component.translatable("tooltip.mobvats.endpoint_filter",value),true);return true;
    }
    public CompoundTag syncDataTo(CompoundTag data,HolderLookup.Provider registries,SyncReason reason){super.syncDataTo(data,registries,reason);data.putString("boundaryFilter",filter);return data;}
    public void syncDataFrom(CompoundTag data,HolderLookup.Provider registries,SyncReason reason){super.syncDataFrom(data,registries,reason);filter=data.getString("boundaryFilter");}
}
