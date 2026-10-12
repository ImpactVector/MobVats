package net.impactvector.mobvats.gamecontent.multiblock.common;

/** Controller-owned cadence; implementations only perform boundary transport. */
public interface ActiveBoundaryPort extends ConfigurableEndpoint {
    net.minecraft.core.BlockPos getBlockPos();
    int transferBoundary(int fluidLimit);

    static boolean tick(java.util.Collection<?> parts,long gameTime){
        var ports=parts.stream().filter(ActiveBoundaryPort.class::isInstance).map(ActiveBoundaryPort.class::cast)
                .sorted(java.util.Comparator.comparing(ActiveBoundaryPort::getBlockPos,
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.POSITION)).toList();
        return tickOrdered(ports,gameTime);
    }

    /** Controller role collections already maintain deterministic position order. */
    static boolean tickOrdered(java.util.List<? extends ActiveBoundaryPort> ports,long gameTime){
        if(ports.isEmpty())return false;
        int start=(int)Math.floorMod(gameTime,ports.size());
        boolean changed=false;
        for(int i=0;i<ports.size();i++)changed|=ports.get((start+i)%ports.size()).transferBoundary(1000)>0;
        return changed;
    }
}
