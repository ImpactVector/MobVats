package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.lib.data.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractVatPortEntity extends AbstractVatEntity
        implements ConfigurableEndpoint, it.zerono.mods.zerocore.lib.block.INeighborChangeListener, MenuProvider {
    private final EndpointConfiguration endpoint;
    private final PortLifecycle<MultiblockVat, AbstractVatPortEntity> lifecycle;

    protected AbstractVatPortEntity(VatPartType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        var resource = type == VatPartType.ItemPort
                ? EndpointConfiguration.Resource.ITEM
                : type == VatPartType.FluidPort
                        ? EndpointConfiguration.Resource.FLUID
                        : EndpointConfiguration.Resource.NONE;
        endpoint = new EndpointConfiguration(resource, IoDirection.Input, this::configurationChanged);
        lifecycle = new PortLifecycle<>(
                this, this::portChanged, () -> getMultiblockController().ifPresent(MultiblockVat::rebuildTopology));
    }

    public EndpointConfiguration endpoint() {
        return endpoint;
    }

    private void configurationChanged() {
        if (lifecycle != null) lifecycle.configurationChanged();
        PortVisualState.refresh(this, this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        PortVisualState.refresh(this, this);
    }

    protected void portChanged() {}
    /** Sneak-use samples configuration only; ordinary clicks retain the existing menu. */
    public boolean configure(Player player, net.minecraft.world.InteractionHand hand) {
        return endpoint.configure(player, hand, isMachineAssembled());
    }

    public Component getDisplayName() {
        return Component.translatable(getPartType().getTranslationKey());
    }

    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MachinePortMenu(id, inventory, this);
    }

    public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        endpoint.load(data);
    }

    public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        endpoint.save(data);
        return data;
    }

    public void refreshPortBoundary() {
        if (lifecycle != null) lifecycle.refresh(true);
    }

    public boolean isItemPortConnected() {
        return lifecycle != null && lifecycle.isItemConnected();
    }

    public boolean boundaryAvailable() {
        return lifecycle != null && lifecycle.boundaryAvailable();
    }

    protected int ejectItems(net.neoforged.neoforge.items.IItemHandler source) {
        return lifecycle.ejectItems(source);
    }

    @Override
    public void onNeighborBlockChanged(BlockState state, BlockPos pos, boolean moving) {
        if (lifecycle != null) lifecycle.refresh(false);
    }

    @Override
    public void onNeighborTileChanged(BlockState state, BlockPos pos) {
        if (lifecycle != null) lifecycle.refresh(false);
    }

    @Override
    public void onPostMachineAssembled(MultiblockVat c) {
        super.onPostMachineAssembled(c);
        refreshPortBoundary();
    }

    @Override
    public void onPostMachineBroken() {
        super.onPostMachineBroken();
        refreshPortBoundary();
    }

    @Override
    public void onAttached(MultiblockVat c) {
        super.onAttached(c);
        refreshPortBoundary();
    }

    @Override
    public void onDetached(MultiblockVat c) {
        super.onDetached(c);
        refreshPortBoundary();
    }

    @Override
    public void onAssimilated(MultiblockVat c) {
        super.onAssimilated(c);
        refreshPortBoundary();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        refreshPortBoundary();
    }

    @Override
    public void onChunkUnloaded() {
        if (lifecycle != null) lifecycle.beginUnload();
        super.onChunkUnloaded();
        if (lifecycle != null) lifecycle.refresh(false);
    }

    @Override
    public void clearRemoved() {
        if (lifecycle != null) lifecycle.endUnload();
        super.clearRemoved();
        refreshPortBoundary();
    }
}
