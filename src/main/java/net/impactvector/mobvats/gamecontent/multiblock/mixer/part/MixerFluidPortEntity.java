package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.impactvector.mobvats.gamecontent.multiblock.common.EndpointConfiguration;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer;
import it.zerono.mods.zerocore.base.multiblock.part.io.fluid.*;
import it.zerono.mods.zerocore.lib.data.IoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
public final class MixerFluidPortEntity extends MixerPortEntity implements IFluidPort, net.impactvector.mobvats.gamecontent.multiblock.common.ActiveBoundaryPort {
    public int transferBoundary(int limit){return boundary.transfer(limit);}
    private final net.impactvector.mobvats.gamecontent.multiblock.common.MachineFluidPort<MultiblockMixer,MixerFluidPortEntity> boundary=new net.impactvector.mobvats.gamecontent.multiblock.common.MachineFluidPort<>(this);
    public MixerFluidPortEntity(BlockPos pos,BlockState state){this(MixerPartType.FluidPort,pos,state);}
    public MixerFluidPortEntity(MixerPartType type,BlockPos pos,BlockState state){super(type,pos,state,EndpointConfiguration.Resource.FLUID);}
    public IFluidPortHandler getFluidPortHandler(){return boundary.handler();}
    @Override public IFluidHandler fluids(){return boundary.external();}
    @Override protected void portChanged(){if(boundary!=null)boundary.refresh(this::delegatedFluids);}
    public void refreshRouting(){refreshPortBoundary();}
}
