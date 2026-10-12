package net.impactvector.mobvats.gamecontent.multiblock.common;

import it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity;
import it.zerono.mods.zerocore.base.multiblock.part.io.IOPortBlockCapabilitySource;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.AbstractCuboidMultiblockController;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** Shared XR2-style boundary lifecycle; topology depends on direction/filter, never mode.
 * See ATTRIBUTION.md. Neighbor callbacks never notify back, avoiding notification cycles. */
public final class PortLifecycle<C extends AbstractCuboidMultiblockController<C>,
        P extends AbstractMultiblockEntity<C> & ConfigurableEndpoint> {
    private final P port;
    private final Runnable refreshLocal;
    private final Runnable rebuildTopology;
    private IOPortBlockCapabilitySource<C,P,IItemHandler> remoteItems;
    private IoDirection direction;
    private String filter;
    private Direction outward;
    private boolean refreshing;
    private boolean unloading;

    public PortLifecycle(P port,Runnable refreshLocal,Runnable rebuildTopology){
        this.port=port;this.refreshLocal=refreshLocal;this.rebuildTopology=rebuildTopology;
        direction=port.getIoDirection();filter=port.filter();
    }
    public void configurationChanged(){
        boolean routingChanged=direction!=port.getIoDirection() || !filter.equals(port.filter());
        direction=port.getIoDirection();filter=port.filter();
        refresh(true);port.setChanged();port.notifyBlockUpdate();
        if(routingChanged && port.getLevel()!=null && !port.getLevel().isClientSide)rebuildTopology.run();
    }
    public boolean isItemConnected(){return remoteItems!=null && remoteItems.getCapability()!=null;}
    public boolean boundaryAvailable(){return !unloading && outward!=null && !port.isRemoved() && port.isMachineAssembled()
            && (!(port.getLevel() instanceof net.minecraft.server.level.ServerLevel server) || server.getServer().isRunning());}
    /** XR2-style ejection of staged output. Item input never pulls. */
    public int ejectItems(IItemHandler source){
        if(!boundaryAvailable() || !port.getIoMode().isActive()
                || !port.getIoDirection().isOutput() || remoteItems==null)return 0;
        var neighborPos=port.getBlockPos().relative(outward);
        if(port.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && level.getChunkSource().getChunkNow(neighborPos.getX()>>4,neighborPos.getZ()>>4)==null)return 0;
        var destination=remoteItems.getCapability();
        if(destination==null)return 0;
        int moved=0;
        for(int slot=0;slot<source.getSlots();slot++){
            var offered=source.extractItem(slot,Integer.MAX_VALUE,true);
            if(offered.isEmpty() || !port.endpoint().accepts(offered))continue;
            var remainder=net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(destination,offered.copy(),true);
            int accepted=offered.getCount()-remainder.getCount();
            if(accepted<=0)continue;
            // Server-thread insertion cannot race a hopper tick. Extract only the committed amount.
            remainder=net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(destination,offered.copyWithCount(accepted),false);
            int committed=accepted-remainder.getCount();
            source.extractItem(slot,committed,false);moved+=committed;
        }
        return moved;
    }
    public void beginUnload(){unloading=true;}
    public void endUnload(){unloading=false;}
    public void refresh(boolean notify){
        if(refreshing)return;
        refreshing=true;
        try{
            var previous=outward;
            outward=unloading || port.isRemoved() || !port.isMachineAssembled()?null:port.getOutwardDirection().orElse(null);
            remoteItems=null;
            if(boundaryAvailable() && port.getIoMode().isActive() && port.endpoint().resource()==EndpointConfiguration.Resource.ITEM){
                remoteItems=new IOPortBlockCapabilitySource<>(port,Capabilities.ItemHandler.BLOCK);
                remoteItems.onPortChanged();
            }
            refreshLocal.run();
            var level=port.getLevel();
            if(level!=null){
                // Invalidating capabilities during chunk removal can reacquire the unloading
                // chunk. Vanilla handles removal invalidation; only live ports notify here.
                boolean live=!unloading && !port.isRemoved() && level.hasChunkAt(port.getBlockPos())
                    && (!(level instanceof net.minecraft.server.level.ServerLevel server) || server.getServer().isRunning());
                if(notify && live){
                    level.invalidateCapabilities(port.getBlockPos());
                    // The cuboid superclass clears facings on break; retain the old outward side.
                    if(!level.isClientSide){
                        if(previous!=null && level.hasChunkAt(port.getBlockPos().relative(previous)))level.neighborChanged(port.getBlockPos().relative(previous),port.getBlockState().getBlock(),port.getBlockPos());
                        if(outward!=null && outward!=previous && level.hasChunkAt(port.getBlockPos().relative(outward)))level.neighborChanged(port.getBlockPos().relative(outward),port.getBlockState().getBlock(),port.getBlockPos());
                    }
                }
                port.requestClientRenderUpdate();
            }
        }finally{refreshing=false;}
    }
}
