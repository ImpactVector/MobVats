package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;

/** Machine storage adapter. Endpoint configuration is shared with Vat ports. */
public abstract class MixerPortEntity extends AbstractMixerEntity
        implements net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceEndpoint,
                ConfigurableEndpoint,
                it.zerono.mods.zerocore.lib.block.INeighborChangeListener,
                net.minecraft.world.MenuProvider {
    private final ItemStackHandler fuelStore = new ItemStackHandler(2) {
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return slot == 0 && MixerCombustionDriveEntity.fuelTicks(stack) > 0;
        }

        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public IItemHandlerModifiable fuelStore() {
        return fuelStore;
    }

    public CompoundTag saveStore(HolderLookup.Provider registries) {
        return fuelStore.serializeNBT(registries);
    }

    public void loadStore(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Size")) fuelStore.deserializeNBT(registries, tag);
        else fuelStore.setSize(2);
    }

    public java.util.Set<net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role> roles(
            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource resource) {
        var own = endpoint.resource() == EndpointConfiguration.Resource.ITEM
                ? net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM
                : net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.FLUID;
        return resource == own
                ? java.util.Set.of(
                        getIoDirection().isInput()
                                ? net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.STORE
                                : net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.CONSUMER)
                : java.util.Set.of();
    }

    public IItemHandlerModifiable items(
            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role role) {
        return endpoint.resource() == EndpointConfiguration.Resource.ITEM
                        && getIoDirection().isInput()
                        && role == net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.STORE
                ? fuelStore
                : null;
    }

    public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(
            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role role) {
        return endpoint.resource() == EndpointConfiguration.Resource.FLUID
                        && getIoDirection().isInput()
                        && role == net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.STORE
                ? getMultiblockController()
                        .map(c -> c.storage().bulkConnected(getBlockPos()))
                        .orElse(java.util.List.of())
                : java.util.List.of();
    }

    public boolean routingAvailable() {
        return !isRemoved();
    }

    private final EndpointConfiguration endpoint;
    private final PortLifecycle<MultiblockMixer, MixerPortEntity> lifecycle;

    protected MixerPortEntity(
            MixerPartType type, BlockPos pos, BlockState state, EndpointConfiguration.Resource resource) {
        super(type, pos, state);
        endpoint = new EndpointConfiguration(
                resource,
                state.hasProperty(PortVisualState.OUTPUT) && state.getValue(PortVisualState.OUTPUT)
                        ? IoDirection.Output
                        : IoDirection.Input,
                this::configurationChanged);
        lifecycle = new PortLifecycle<>(
                this, this::portChanged, () -> getMultiblockController().ifPresent(MultiblockMixer::rebuildTopology));
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

    public boolean configure(
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return endpoint.configure(player, hand, isMachineAssembled());
    }

    public boolean accepts(net.neoforged.neoforge.fluids.FluidStack stack) {
        return endpoint.accepts(stack);
    }

    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        return endpoint.accepts(stack);
    }

    public void syncDataFrom(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(tag, registries, reason);
        endpoint.load(tag);
        loadStore(tag.getCompound("fuelStore"), registries);
    }

    public CompoundTag syncDataTo(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(tag, registries, reason);
        endpoint.save(tag);
        tag.put("fuelStore", fuelStore.serializeNBT(registries));
        return tag;
    }

    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(getPartType().getTranslationKey());
    }

    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id,
            net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new net.impactvector.mobvats.gamecontent.multiblock.common.MachinePortMenu(id, inventory, this);
    }

    protected IFluidHandler delegatedFluids(IoDirection direction) {
        return getMultiblockController()
                .filter(MultiblockMixer::isAssembled)
                .map(c -> direction.isInput()
                        ? c.storage()
                                .destination(
                                        getBlockPos(),
                                        true,
                                        s -> accepts(s)
                                                && net.impactvector.mobvats.gamecontent.multiblock.mixer.process
                                                        .MixerRecipeRegistry.acceptsFluid(s))
                        : c.resourceRouting()
                                .fluidConsumer(
                                        this, s -> accepts(s) && c.state().outputFluid(s)))
                .orElse(EmptyFluidHandler.INSTANCE);
    }

    public IFluidHandler fluids() {
        return delegatedFluids(getIoDirection());
    }

    public IItemHandler items() {
        return new IItemHandler() {
            private IItemHandler current() {
                if (!getIoMode().isPassive() || isRemoved()) return EmptyItemHandler.INSTANCE;
                return getMultiblockController()
                        .filter(MultiblockMixer::isAssembled)
                        .map(c -> c.itemsForPort(MixerPortEntity.this))
                        .orElse(EmptyItemHandler.INSTANCE);
            }

            public int getSlots() {
                return current().getSlots();
            }

            public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
                return current().getStackInSlot(slot);
            }

            public net.minecraft.world.item.ItemStack insertItem(
                    int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
                return current().insertItem(slot, stack, simulate);
            }

            public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
                return current().extractItem(slot, amount, simulate);
            }

            public int getSlotLimit(int slot) {
                return current().getSlotLimit(slot);
            }

            public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                return current().isItemValid(slot, stack);
            }
        };
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
    public void onPostMachineAssembled(MultiblockMixer c) {
        super.onPostMachineAssembled(c);
        refreshPortBoundary();
    }

    @Override
    public void onPostMachineBroken() {
        super.onPostMachineBroken();
        refreshPortBoundary();
    }

    @Override
    public void onAttached(MultiblockMixer c) {
        super.onAttached(c);
        refreshPortBoundary();
    }

    @Override
    public void onDetached(MultiblockMixer c) {
        super.onDetached(c);
        refreshPortBoundary();
    }

    @Override
    public void onAssimilated(MultiblockMixer c) {
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
