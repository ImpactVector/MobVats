package net.impactvector.mobvats.gamecontent.multiblock.common;

import it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity;
import it.zerono.mods.zerocore.base.multiblock.part.io.fluid.*;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.fluid.handler.FluidHandlerForwarder;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.AbstractCuboidMultiblockController;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import java.util.function.Function;

/** ZeroCore owns forwarding and remote discovery; the entity owns configuration/lifetime.
 * See ATTRIBUTION.md for the XR2/ZeroCore architecture and license notices. */
public final class MachineFluidPort<C extends AbstractCuboidMultiblockController<C>,
        P extends AbstractMultiblockEntity<C> & IFluidPort & ConfigurableEndpoint> {
    private final P port;
    private FluidPortHandlerForge<C,P> handler;
    private it.zerono.mods.zerocore.base.multiblock.part.io.IOPortBlockCapabilitySource<C,P,IFluidHandler> remote;
    private IFluidHandler local=EmptyFluidHandler.INSTANCE;
    private final FluidHandlerForwarder external=new FluidHandlerForwarder(EmptyFluidHandler.INSTANCE);

    public MachineFluidPort(P port){this.port=port;handler=new FluidPortHandlerForge<>(port,port.getIoMode());}
    public IFluidPortHandler handler(){return handler;}
    public IFluidHandler external(){return external;}
    public void refresh(Function<IoDirection,IFluidHandler> target){
        // ZeroCore mode and source are immutable/no-clear. Retire the previous local target
        // before replacing its source, so retained old handlers cannot access machine storage.
        handler.update(direction->EmptyFluidHandler.INSTANCE);
        handler=new FluidPortHandlerForge<>(port,port.getIoMode());
        local=EmptyFluidHandler.INSTANCE;
        remote=null;
        if(port.boundaryAvailable() && !port.isRemoved() && port.isMachineAssembled()){
            local=target.apply(port.getIoDirection());
            handler.update(target);
            if(port.getIoMode().isActive()){
                handler.onPortChanged();
                remote=new it.zerono.mods.zerocore.base.multiblock.part.io.IOPortBlockCapabilitySource<>(port,net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK);
                remote.onPortChanged();
            }
        }
        external.setHandler(port.getIoMode().isPassive()?handler:EmptyFluidHandler.INSTANCE);
    }

    /** ZeroCore's active helpers reject Both via isPassive(). Use its discovery pattern
     * with NeoForge's simulated, component-exact transfer against the same local view. */
    public int transfer(int limit){
        if(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitPerformance.ENABLED)++net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitPerformance.transfers;
        if(limit<=0 || remote==null || !port.boundaryAvailable() || port.isRemoved() || !port.isMachineAssembled() || !port.getIoMode().isActive())return 0;
        var outward=port.getOutwardDirection().orElse(null);
        if(outward==null)return 0;
        var neighborPos=port.getBlockPos().relative(outward);
        if(port.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && level.getChunkSource().getChunkNow(neighborPos.getX()>>4,neighborPos.getZ()>>4)==null)return 0;
        var neighbor=remote.getCapability();
        if(neighbor==null)return 0;
        boolean input=port.getIoDirection().isInput();
        // Direct machine adjacency has a single owner, independent of controller tick order.
        if(input){
            var entity=port.getLevel().getBlockEntity(neighborPos);
            if(entity instanceof ActiveBoundaryPort other && other.endpoint().resource()==EndpointConfiguration.Resource.FLUID
                    && other.getIoDirection().isOutput() && other.getIoMode().isActive())return 0;
        }
        var source=input?neighbor:local;
        var destination=input?local:neighbor;
        int moved=0;
        // Typed requests allow a rejected first tank to coexist with accepted later tanks.
        for(int tank=0;tank<source.getTanks() && moved<limit;tank++){
            var stack=source.getFluidInTank(tank).copy();
            if(stack.isEmpty())continue;
            stack.setAmount(limit-moved);
            moved+=net.neoforged.neoforge.fluids.FluidUtil.tryFluidTransfer(destination,source,stack,true).getAmount();
        }
        return moved;
    }
}
