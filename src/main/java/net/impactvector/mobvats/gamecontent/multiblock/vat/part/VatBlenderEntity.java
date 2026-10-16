package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveUses;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Physical actor descriptor only; controller owns every timer and transaction. */
public final class VatBlenderEntity extends AbstractVatEntity implements VatActor, ResourceEndpoint {
    public java.util.Set<ConduitTopology.Role> roles(ConduitResource resource) {
        return resource == ConduitResource.FLUID ? java.util.Set.of(ConduitTopology.Role.PRODUCER) : java.util.Set.of();
    }

    public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(
            ConduitTopology.Role role) {
        return role == ConduitTopology.Role.PRODUCER ? service().tanks() : java.util.List.of();
    }

    public boolean routingAvailable() {
        return !isRemoved() && !redstoneInhibited();
    }

    public VatBlenderEntity(BlockPos pos, BlockState state) {
        super(VatPartType.Blender, pos, state);
    }

    public int priority() {
        return PassiveUses.BLENDER_PRIORITY;
    }

    public String useKey() {
        return PassiveUses.BLEND;
    }

    public boolean compatible(MobAssembly assembly) {
        var definition =
                net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(
                        assembly.head().getIdentity());
        return definition != null
                && definition.lifecycle()
                && !definition.harvest().blender().isEmpty();
    }
}
