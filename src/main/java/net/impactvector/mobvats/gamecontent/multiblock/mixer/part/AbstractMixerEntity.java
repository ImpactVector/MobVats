package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;
import it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public abstract class AbstractMixerEntity extends AbstractMultiblockEntity<MultiblockMixer> {
    private void diagnostic(String event){net.impactvector.mobvats.gamecontent.multiblock.common.SyncDiagnostics.record("MultiblockMixer",event,this,level==null?Long.MIN_VALUE:level.getGameTime());}
    @Override public void setChanged(){diagnostic("partDirty");super.setChanged();}
    @Override public void notifyBlockUpdate(BlockState oldState,BlockState newState){diagnostic("partUpdate");super.notifyBlockUpdate(oldState,newState);}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){diagnostic("packetConstructed");return super.getUpdatePacket();}
    private net.minecraft.nbt.CompoundTag retainedMechanicalState=new net.minecraft.nbt.CompoundTag();
    private final net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging service=new net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging(this::setChanged);
    public net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging service(){return service;}
    @Override public net.minecraft.nbt.CompoundTag syncDataTo(net.minecraft.nbt.CompoundTag data,net.minecraft.core.HolderLookup.Provider registries,SyncReason reason){super.syncDataTo(data,registries,reason);data.put("retainedMechanicalState",retainedMechanicalState.copy());data.put("serviceStaging",service.save(registries));return data;}
    @Override public void syncDataFrom(net.minecraft.nbt.CompoundTag data,net.minecraft.core.HolderLookup.Provider registries,SyncReason reason){super.syncDataFrom(data,registries,reason);retainedMechanicalState=data.getCompound("retainedMechanicalState").copy();service.load(data.getCompound("serviceStaging"),registries);}
    private final MixerPartType type;
    protected AbstractMixerEntity(MixerPartType type,BlockPos pos,BlockState state){super(MixerContent.entityType(type),pos,state);this.type=type;}
    @Override public java.util.Set<MultiblockMixer> attachToNeighbors(java.util.function.Function<it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<MultiblockMixer>,java.util.Set<MultiblockMixer>> ignored) {
        // ZC's default registry callback checks six physical BE neighbors. Include passive structural bridges.
        var controllers=new java.util.HashSet<MultiblockMixer>();
        for(var neighbor:getNeighboringParts())if(!neighbor.isPartInvalid() && neighbor.getControllerType()==getControllerType())neighbor.getMultiblockController().ifPresent(controllers::add);
        var current=getMultiblockController().filter(controller -> controller.containsPart(this));
        if(current.isPresent()){
            controllers.add(current.get());return controllers;
        }
        return super.attachToNeighbors(part -> controllers);
    }
    @Override public java.util.List<it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<MultiblockMixer>> getNeighboringParts() {
        return net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure.neighbors(level,getBlockPos(),MultiblockMixer.class,super.getNeighboringParts());
    }
    public MixerPartType getPartType(){return type;}
    public Class<MultiblockMixer> getControllerType(){return MultiblockMixer.class;}
    public MultiblockMixer createController(){return new MultiblockMixer(getPartWorldOrFail());}
    protected net.neoforged.neoforge.client.model.data.ModelData getUpdatedModelData(){
        var panel=net.impactvector.mobvats.gamecontent.multiblock.vat.VatWallPanelState.UNFORMED;
        var controller=getMultiblockController().orElse(null);
        if(controller!=null && controller.isAssembled()){
            var bounds=controller.getBoundingBox();int glassEdges=0;
            for(var direction:net.minecraft.core.Direction.values()){
                var block=level.getBlockState(getBlockPos().relative(direction)).getBlock();
                if(block==MixerContent.block(MixerPartType.Glass) || block instanceof net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock conduit && conduit.cladding(this.getLevel().getBlockState(this.getBlockPos().relative(direction)))==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.GLASS)glassEdges|=1<<direction.ordinal();
            }
            panel=new net.impactvector.mobvats.gamecontent.multiblock.vat.VatWallPanelState(net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState.from(getBlockPos(),bounds.getMin(),bounds.getMax()),glassEdges);
        }
        return net.neoforged.neoforge.client.model.data.ModelData.builder().with(net.impactvector.mobvats.gamecontent.multiblock.vat.VatWallPanelState.PROPERTY,panel).build();
    }
    public void onMachineActivated(){}
    public void onMachineDeactivated(){}
    public boolean isGoodForPosition(PartPosition position,IMultiblockValidator validator){
        boolean materialAllowed=true;
        if(getBlockState().getBlock() instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock){
            var material=net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.installed(getBlockState());
            materialAllowed=position==PartPosition.Interior || material==net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.NONE?position==PartPosition.Interior:
                material.shell()==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitShell.MIXER && (material.cladding()==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.GLASS?MixerPartType.Glass:MixerPartType.Casing).allows(position);
        }
        if(materialAllowed && type.allows(position))return true;validator.setLastError(getBlockPos(),"multiblock.mobvats.invalid_position",type.getSerializedName(),position.getSerializedName());return false;
    }
}
