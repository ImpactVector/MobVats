package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.base.multiblock.part.io.fluid.*;
import it.zerono.mods.zerocore.lib.data.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;

public final class VatFluidPortEntity extends AbstractVatPortEntity
        implements IFluidPort,
                ResourceEndpoint,
                net.impactvector.mobvats.gamecontent.multiblock.common.ActiveBoundaryPort {
    public int transferBoundary(int limit) {
        return boundary.transfer(limit);
    }

    public java.util.Set<ConduitTopology.Role> roles(ConduitResource resource) {
        return resource == ConduitResource.FLUID
                ? java.util.Set.of(
                        getIoDirection().isInput() ? ConduitTopology.Role.STORE : ConduitTopology.Role.CONSUMER)
                : java.util.Set.of();
    }

    public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(
            ConduitTopology.Role role) {
        return role == ConduitTopology.Role.STORE && getIoDirection().isInput()
                ? getMultiblockController()
                        .map(c -> c.storage().bulkConnected(getBlockPos()))
                        .orElse(java.util.List.of())
                : java.util.List.of();
    }

    public boolean routingAvailable() {
        return !isRemoved();
    }

    private final net.impactvector.mobvats.gamecontent.multiblock.common.MachineFluidPort<
                    MultiblockVat, VatFluidPortEntity>
            boundary = new net.impactvector.mobvats.gamecontent.multiblock.common.MachineFluidPort<>(this);

    public VatFluidPortEntity(BlockPos pos, BlockState state) {
        super(VatPartType.FluidPort, pos, state);
    }

    public boolean accepts(net.neoforged.neoforge.fluids.FluidStack stack) {
        return endpoint().accepts(stack);
    }

    public IFluidPortHandler getFluidPortHandler() {
        return boundary.handler();
    }

    public IFluidHandler externalFluids() {
        return boundary.external();
    }

    public boolean interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return isMachineAssembled()
                && net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidContainerInteraction.interact(
                        player, hand, externalFluids(), getIoDirection().isInput());
    }

    protected void portChanged() {
        if (boundary != null) boundary.refresh(this::delegated);
    }

    public void refreshRouting() {
        refreshPortBoundary();
    }

    private IFluidHandler delegated(IoDirection direction) {
        return getMultiblockController()
                .filter(MultiblockVat::isAssembled)
                .map(c -> direction.isInput()
                        ? c.storage().destination(getBlockPos(), true, this::accepts)
                        : c.resourceRouting().fluidConsumer(this, this::accepts))
                .orElse(EmptyFluidHandler.INSTANCE);
    }
}
