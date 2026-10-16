package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.lib.item.inventory.handler.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public final class VatItemPortEntity extends AbstractVatPortEntity
        implements ResourceEndpoint, net.impactvector.mobvats.gamecontent.multiblock.common.ActiveBoundaryPort {
    public int transferBoundary(int limit) {
        return redstoneInhibited() ? 0 : ejectItems(output);
    }

    public java.util.Set<ConduitTopology.Role> roles(ConduitResource resource) {
        return resource == ConduitResource.ITEM
                ? java.util.Set.of(
                        getIoDirection().isInput() ? ConduitTopology.Role.STORE : ConduitTopology.Role.CONSUMER)
                : java.util.Set.of();
    }

    public net.neoforged.neoforge.items.IItemHandlerModifiable items(ConduitTopology.Role role) {
        return roles(ConduitResource.ITEM).contains(role) ? inventory : null;
    }

    public boolean routingAvailable() {
        return !isRemoved() && !redstoneInhibited();
    }

    private java.util.List<net.minecraft.world.item.ItemStack> outputDemand = java.util.List.of();
    /** The producing operation announces its output types; the boundary owns retained demand. */
    public void demandOutputs(java.util.List<net.minecraft.world.item.ItemStack> stacks) {
        var values = new java.util.ArrayList<>(outputDemand);
        for (var stack : stacks)
            if (!stack.isEmpty()
                    && values.stream()
                            .noneMatch(s -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, stack)))
                values.add(stack.copyWithCount(1));
        if (values.size() == outputDemand.size()) return;
        outputDemand = java.util.List.copyOf(values);
        setChanged();
    }

    public boolean demands(net.minecraft.world.item.ItemStack stack) {
        return !filter().isEmpty()
                ? accepts(stack)
                : outputDemand.stream()
                        .anyMatch(s -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, stack));
    }

    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        return endpoint().accepts(stack);
    }

    private final TileEntityItemStackHandler inventory = new TileEntityItemStackHandler(this, 1) {
        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return accepts(stack);
        }
    };
    private final IItemHandler input = ItemHandlerPolicyWrapper.inputOnly(inventory, (slot, stack) -> accepts(stack));
    private final IItemHandler output = new IItemHandler() {
        public int getSlots() {
            return 1;
        }

        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            var stack = inventory.getStackInSlot(slot);
            return accepts(stack) ? stack : net.minecraft.world.item.ItemStack.EMPTY;
        }

        public net.minecraft.world.item.ItemStack insertItem(
                int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return stack;
        }

        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return accepts(inventory.getStackInSlot(slot))
                    ? inventory.extractItem(slot, amount, simulate)
                    : net.minecraft.world.item.ItemStack.EMPTY;
        }

        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return false;
        }
    };

    public VatItemPortEntity(BlockPos pos, BlockState state) {
        super(VatPartType.ItemPort, pos, state);
        inventory.setContentsChangedListener(slot -> {
            notifyBlockUpdate();
        });
    }

    public TileEntityItemStackHandler staging() {
        return inventory;
    }

    private final IItemHandler external = new IItemHandler() {
        private IItemHandler current() {
            return !isRemoved() && getIoMode().isPassive()
                    ? (getIoDirection().isInput() ? input : output)
                    : net.neoforged.neoforge.items.wrapper.EmptyItemHandler.INSTANCE;
        }

        public int getSlots() {
            return current().getSlots();
        }

        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return current().getStackInSlot(slot);
        }

        public net.minecraft.world.item.ItemStack insertItem(
                int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return redstoneInhibited() ? stack : current().insertItem(slot, stack, simulate);
        }

        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return redstoneInhibited()
                    ? net.minecraft.world.item.ItemStack.EMPTY
                    : current().extractItem(slot, amount, simulate);
        }

        public int getSlotLimit(int slot) {
            return current().getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return current().isItemValid(slot, stack);
        }
    };

    public IItemHandler externalItems() {
        return external;
    }

    public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        var demand = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (var tag : data.getList("outputDemand", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var stack = net.minecraft.world.item.ItemStack.parseOptional(registries, (CompoundTag) tag);
            if (!stack.isEmpty()) demand.add(stack);
        }
        outputDemand = java.util.List.copyOf(demand);
        inventory.deserializeNBT(registries, data.getCompound("staging"));
    }

    public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        data.put("staging", inventory.serializeNBT(registries));
        var demand = new net.minecraft.nbt.ListTag();
        outputDemand.forEach(s -> demand.add(s.save(registries)));
        data.put("outputDemand", demand);
        return data;
    }
}
