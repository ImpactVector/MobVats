package net.impactvector.mobvats.gamecontent.multiblock.conduit;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
/** Each member persists its own contents; splitting/reforming never duplicates a shared tank. */
public final class FluidBufferEntity extends BlockEntity implements ResourceEndpoint {
    public java.util.Set<ConduitTopology.Role> roles(ConduitResource resource){return resource==ConduitResource.FLUID?java.util.Set.of(ConduitTopology.Role.STORE):java.util.Set.of();}
    public java.util.List<FluidTank> fluids(ConduitTopology.Role role){return role==ConduitTopology.Role.STORE?java.util.List.of(tank):java.util.List.of();}
    public boolean routingAvailable(){return !isRemoved();}

    public static final int CAPACITY=4000;
    private String filter="";
    private FluidStack geometryIdentity=FluidStack.EMPTY;
    private int visualJoins;
    private FluidStack visualFluid=FluidStack.EMPTY;
    private double visualHeight;
    private long visualRevision;
    public int visualJoins(){return visualJoins;}
    public FluidStack visualFluid(){return visualFluid;}
    public double visualHeight(){return visualHeight;}
    public long visualRevision(){return visualRevision;}
    public void presentation(int joins,FluidStack fluid,double height) {
        if(joins==visualJoins&&height==visualHeight&&FluidStack.matches(fluid,visualFluid))return;
        visualJoins=joins;visualFluid=fluid.copy();visualHeight=height;++visualRevision;
        if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    @Override public void onLoad(){super.onLoad();FluidBufferPresentation.changed(level,worldPosition);}
    @Override public void onChunkUnloaded(){FluidBufferPresentation.changed(level,worldPosition);super.onChunkUnloaded();}
    @Override public void setRemoved(){FluidBufferPresentation.changed(level,worldPosition);super.setRemoved();}
    @Override public void clearRemoved(){super.clearRemoved();FluidBufferPresentation.changed(level,worldPosition);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=super.getUpdateTag(registries);
        tag.putInt("visualJoins",visualJoins);tag.putDouble("visualHeight",visualHeight);
        if(!visualFluid.isEmpty())tag.put("visualFluid",visualFluid.save(registries));return tag;
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) {
        // Old visualMounts tags are deliberately ignored; hardware cannot survive a reload.
        presentation(tag.getInt("visualJoins"),tag.contains("visualFluid")?FluidStack.parseOptional(registries,tag.getCompound("visualFluid")):FluidStack.EMPTY,tag.getDouble("visualHeight"));
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){handleUpdateTag(packet.getTag(),registries);}
    public final FluidTank tank=new FluidTank(CAPACITY,this::accepts){protected void onContentsChanged(){
        setChanged();
        FluidBufferPresentation.changed(level,worldPosition);
        // Amount changes do not alter bridges; identity transitions can split an adjacent cluster.
        if(!FluidStack.isSameFluidSameComponents(geometryIdentity,getFluid())) {
            geometryIdentity=getFluid().copy();
            if(level!=null && !level.isClientSide)ConduitStructure.mixerGeometryChanged(level,worldPosition);
        }
    }
        @Override public void setFluid(FluidStack stack){super.setFluid(stack);onContentsChanged();}
    };
    public FluidBufferEntity(BlockPos pos,BlockState state){super(FluidBufferContent.ENTITY.get(),pos,state);}
    public String filter(){return filter;}
    /** Build the addressed logical cluster even outside a formed machine. */
    public PhysicalFluidStorage.Cluster interactionCluster() {
        var members=new java.util.ArrayList<FluidBufferEntity>();var seen=new java.util.HashSet<BlockPos>();
        var queue=new java.util.ArrayDeque<BlockPos>();queue.add(worldPosition);
        while(!queue.isEmpty()) {
            var pos=queue.removeFirst();if(!seen.add(pos))continue;
            if(level.getBlockEntity(pos) instanceof FluidBufferEntity member) {
                members.add(member);for(var face:Direction.values())queue.add(pos.relative(face));
            }
        }
        var storage=new PhysicalFluidStorage();storage.compile(members,ConduitTopology.EMPTY);
        return storage.clusters().stream().filter(c->c.members().contains(this)).findFirst().orElseThrow();
    }
    public boolean interact(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        var held=player.getItemInHand(hand);
        var container=net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(held.copyWithCount(1));
        if(container.isEmpty())return false;
        if(level.isClientSide)return true;
        var cluster=interactionCluster();var logical=cluster.tank();
        var sample=net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(held);
        if(sample.isEmpty() && logical.isEmpty() && !filter.isEmpty()) {
            boolean emptyContainer=true;
            for(int i=0;i<container.get().getTanks();i++)if(!container.get().getFluidInTank(i).isEmpty())emptyContainer=false;
            if(!emptyContainer)return true;
            cluster.members().forEach(m->m.filter(""));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mobvats.filter_cleared"),true);return true;
        }
        var previous=filter;
        if(sample.isEmpty() && !logical.isEmpty() && !FluidContainerInteraction.preservesFill(held,logical,logical.getFluid(),Integer.MAX_VALUE))return true;
        boolean retarget=sample.isPresent() && logical.isEmpty();
        if(retarget) {
            var trial=new FluidTank(logical.getCapacity());
            if(!net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainer(held,trial,Integer.MAX_VALUE,null,false).isSuccess())return true;
            var id=net.impactvector.mobvats.gamecontent.item.EndpointState.fluidId(sample.get());
            cluster.members().forEach(m->m.filter(id));
        }
        var inventory=player.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY);
        var result=sample.isPresent()
            ?net.neoforged.neoforge.fluids.FluidUtil.tryEmptyContainerAndStow(held,logical,inventory,Integer.MAX_VALUE,player,true)
            :net.neoforged.neoforge.fluids.FluidUtil.tryFillContainerAndStow(held,logical,inventory,Integer.MAX_VALUE,player,true);
        boolean transferred=result.isSuccess();
        if(transferred)player.setItemInHand(hand,result.getResult());
        if(retarget && !transferred)cluster.members().forEach(m->m.filter(previous));
        if(transferred)player.displayClientMessage(net.minecraft.network.chat.Component.translatable("tooltip.mobvats.endpoint_filter",filter),true);
        return true; // Consume rejected container clicks to prevent bucket world placement.
    }
    public boolean accepts(FluidStack stack){return filter.isEmpty() || ResourceSelectors.fluids(java.util.List.of(filter)).test(stack);}
    public void filter(String value){if(value==null)throw new IllegalArgumentException("filter");if(!value.isEmpty())ResourceSelectors.fluids(java.util.List.of(value));if(!tank.isEmpty() && !value.isEmpty() && !ResourceSelectors.fluids(java.util.List.of(value)).test(tank.getFluid()))throw new IllegalArgumentException("Filter excludes stored contents");filter=value;setChanged();FluidBufferPresentation.changed(level,worldPosition);if(level!=null)ConduitStructure.changed(level,worldPosition,false);}
    protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putString("filter",filter);tag.put("tank",tank.writeToNBT(registries,new CompoundTag()));}
    protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);filter=tag.getString("filter");tank.readFromNBT(registries,tag.getCompound("tank"));}
}
