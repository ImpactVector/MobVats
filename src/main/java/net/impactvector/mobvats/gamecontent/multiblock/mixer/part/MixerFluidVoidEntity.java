package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidSink;
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

/** Mixer adapter for the shared terminal sink contract; never exposes a Store or tank. */
public final class MixerFluidVoidEntity extends AbstractMixerEntity implements FluidSink {
    private String filter="";
    private long flareUntil;
    private long disposedMb;
    public long disposedMb(){return disposedMb;}
    public MixerFluidVoidEntity(BlockPos pos,BlockState state){super(MixerPartType.FluidVoid,pos,state);}
    public String filter(){return filter;}
    public void filter(String value){if(!value.isEmpty())ResourceSelectors.fluids(java.util.List.of(value));filter=value;setChanged();notifyBlockUpdate();}
    public boolean accepts(FluidStack stack){return isMachineAssembled() && !stack.isEmpty() && (filter.isEmpty() || ResourceSelectors.fluids(java.util.List.of(filter)).test(stack));}
    public boolean flaring(){return level!=null && level.getGameTime()<flareUntil;}
    public String status(){return !isMachineAssembled()?"Unformed":flaring()?"Voiding":"Idle";}
    public void consumed(){if(level!=null && !level.isClientSide){flareUntil=level.getGameTime()+8;notifyBlockUpdate();}}
    public void consumed(int amount){if(amount>0 && level!=null && !level.isClientSide){disposedMb=amount>Long.MAX_VALUE-disposedMb?Long.MAX_VALUE:disposedMb+amount;setChanged();consumed();}}
    public boolean configure(Player player,InteractionHand hand){
        if(!player.isShiftKeyDown())return false;
        if(level.isClientSide)return true;
        if(isMachineAssembled()){player.displayClientMessage(Component.translatable("message.mobvats.filter_locked"),true);return true;}
        var held=player.getItemInHand(hand);String value="";
        if(!held.isEmpty()){var sample=FluidUtil.getFluidContained(held);if(sample.isEmpty())return true;value=EndpointState.fluidId(sample.get());}
        filter(value);player.displayClientMessage(value.isEmpty()?Component.translatable("message.mobvats.filter_cleared"):Component.translatable("tooltip.mobvats.endpoint_filter",value),true);return true;
    }
    public CompoundTag syncDataTo(CompoundTag data,HolderLookup.Provider registries,SyncReason reason){super.syncDataTo(data,registries,reason);data.putString("boundaryFilter",filter);data.putLong("voidFlareUntil",flareUntil);data.putLong("disposedMb",disposedMb);return data;}
    public void syncDataFrom(CompoundTag data,HolderLookup.Provider registries,SyncReason reason){super.syncDataFrom(data,registries,reason);filter=data.getString("boundaryFilter");flareUntil=data.getLong("voidFlareUntil");disposedMb=Math.max(0,data.getLong("disposedMb"));}
}
