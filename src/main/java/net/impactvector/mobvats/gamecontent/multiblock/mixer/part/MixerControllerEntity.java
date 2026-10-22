package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MixerControllerEntity extends AbstractMixerEntity
        implements net.minecraft.world.MenuProvider,
                net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceEndpoint {
    public MixerControllerEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.Controller, pos, state);
        setCommandDispatcher(it.zerono.mods.zerocore.lib.block.TileCommandDispatcher.<MixerControllerEntity>builder()
                .addServerHandler("activate", p -> p.setActive(true))
                .addServerHandler("deactivate", p -> p.setActive(false))
                .build(this));
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.mobvats.mixer_controller");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id,
            net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new net.impactvector.mobvats.gamecontent.multiblock.mixer.menu.MixerControllerMenu(id, inventory, this);
    }

    private void setActive(boolean active) {
        getMultiblockController()
                .filter(net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer::isAssembled)
                .ifPresent(c -> c.setMachineActive(active));
    }

    public java.util.Set<net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role> roles(
            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource resource) {
        return resource == net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.FLUID
                ? java.util.Set.of(
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.PRODUCER,
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.CONSUMER)
                : java.util.Set.of(
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.CONSUMER);
    }

    public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(
            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role role) {
        return role == net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.PRODUCER
                ? service().tanks()
                : java.util.List.of();
    }

    public boolean routingAvailable() {
        return !isRemoved();
    }
}
