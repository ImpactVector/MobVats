package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VatSumpEntity extends AbstractVatEntity implements net.impactvector.mobvats.gamecontent.multiblock.vat.VatActor, net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceEndpoint {
    public java.util.Set<net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role> roles(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource resource){return resource==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.FLUID?java.util.Set.of(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.PRODUCER):java.util.Set.of(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.PRODUCER,net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.CONSUMER);}
    public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role role){return role==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.PRODUCER?service().tanks():java.util.List.of();}
    public boolean routingAvailable(){return !isRemoved();}
    public int priority(){return 1000;}
    public String useKey(){return net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveUses.DRAIN;}
    public boolean usesLocalItemPorts(){return true;}
    public boolean compatible(net.impactvector.mobvats.gamecontent.multiblock.vat.MobAssembly assembly){return assembly.drain()==this;}
    public boolean reaches(net.minecraft.world.level.Level level,BlockPos actor,BlockPos min,BlockPos max,net.impactvector.mobvats.gamecontent.multiblock.vat.MobAssembly target,java.util.List<net.impactvector.mobvats.gamecontent.multiblock.vat.MobAssembly> all){return target.drain()==this;}
    public VatSumpEntity(BlockPos pos, BlockState state) { super(VatPartType.Sump, pos, state); }
}
