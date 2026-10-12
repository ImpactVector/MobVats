package net.impactvector.mobvats.gamecontent.multiblock.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity;

/** Functional data only: never carries cladding, controller ownership or assembly snapshots. */
public final class MechanicalState {
    private static final java.util.List<String> KEYS=java.util.List.of("serviceStaging","ioDirection","ioMode","itemFilter","fluidFilter","staging","outputDemand","fuelStore","boundaryFilter","voidFlareUntil","disposedMb","boundaryStatus","fillDetails","resourceDemand","previousAim","currentAim","held","results","interactionStart","interactionHead");
    public static CompoundTag capture(BlockEntity entity){
        var source=((it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity)entity).syncDataTo(new CompoundTag(),entity.getLevel().registryAccess(),it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason.FullSync);
        var data=source.getCompound("retainedMechanicalState").copy();
        for(var key:KEYS)if(source.contains(key))data.put(key,source.get(key).copy());
        return data;
    }
    public static void restore(BlockEntity entity,CompoundTag data){
        var source=data.copy();source.put("retainedMechanicalState",data.copy());
        ((it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity)entity).syncDataFrom(source,entity.getLevel().registryAccess(),it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason.FullSync);entity.setChanged();
    }
    private MechanicalState(){}
}
