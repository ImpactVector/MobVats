package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

/** Fuel and container remainders belong exclusively to this motor. */
public final class MixerCombustionDriveEntity extends AbstractMixerDriveEntity implements ResourceEndpoint {
    private final DemandConfiguration demand = new DemandConfiguration(this::setChanged);

    public DemandConfiguration demandConfiguration() {
        return demand;
    }

    public java.util.List<ResourceBundle.Strategy> demandPlan() {
        return demand.plan(java.util.List.of(
                new ResourceBundle.Strategy("fuel", ResourceBundle.item(s -> fuelTicks(s) > 0, 1))));
    }

    public java.util.Set<ConduitTopology.Role> roles(ConduitResource resource) {
        return resource == ConduitResource.ITEM ? java.util.Set.of(ConduitTopology.Role.CONSUMER) : java.util.Set.of();
    }

    public net.neoforged.neoforge.items.IItemHandlerModifiable items(ConduitTopology.Role role) {
        return role == ConduitTopology.Role.CONSUMER ? inventory : null;
    }

    public boolean routingAvailable() {
        return !isRemoved();
    }

    private int burnTicks;
    // Inspection only: the consumed fuel is no longer in the inventory while it burns.
    private int burnDuration;
    private ItemStack burningFuel = ItemStack.EMPTY;

    public int burnDuration() {
        return burnDuration;
    }

    public ItemStack burningFuel() {
        return burnTicks > 0 ? burningFuel.copy() : ItemStack.EMPTY;
    }

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && fuelTicks(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler fuel = new IItemHandler() {
        public int getSlots() {
            return 2;
        }

        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot).copy();
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.extractItem(slot, amount, simulate);
        }

        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };

    public boolean refill(ResourceRouting routing) {
        if (redstoneInhibited()) return false;
        if (burnTicks > 0 || !inventory.getStackInSlot(0).isEmpty()) return false;
        var acquisition = routing.prepare(this, demandPlan()).orElse(null);
        if (acquisition == null) return false;
        var stack = acquisition.items().getFirst();
        return acquisition.commit(
                () -> inventory.setStackInSlot(0, stack), () -> inventory.setStackInSlot(0, ItemStack.EMPTY));
    }

    public MixerCombustionDriveEntity(BlockPos pos, BlockState state) {
        super(MixerPartType.CombustionDrive, pos, state);
    }

    public boolean canRefill(ResourceRouting routing) {
        return !redstoneInhibited()
                && burnTicks == 0
                && inventory.getStackInSlot(0).isEmpty()
                && routing.prepare(this, demandPlan()).isPresent();
    }

    public static int fuelTicks(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    public IItemHandler fuel() {
        return fuel;
    }

    public int burnTicks() {
        return burnTicks;
    }

    private boolean canIgnite() {
        var stack = inventory.getStackInSlot(0);
        if (fuelTicks(stack) <= 0) return false;
        var remainder = stack.getCraftingRemainingItem();
        var output = inventory.getStackInSlot(1);
        return remainder.isEmpty()
                || output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, remainder)
                        && output.getCount() + remainder.getCount() <= output.getMaxStackSize();
    }

    @Override
    public double availableDrive(MultiblockMixer mixer) {
        return attachedTo(mixer) && (burnTicks > 0 || canIgnite()) ? 1.0 : 0.0;
    }

    @Override
    public boolean driveTick(MultiblockMixer mixer) {
        if (!attachedTo(mixer)) return false;
        if (burnTicks == 0) {
            if (!canIgnite()) return false;
            var stack = inventory.getStackInSlot(0);
            burnTicks = fuelTicks(stack);
            burnDuration = burnTicks;
            burningFuel = stack.copyWithCount(1);
            var remainder = stack.getCraftingRemainingItem();
            inventory.extractItem(0, 1, false);
            if (!remainder.isEmpty()) {
                var output = inventory.getStackInSlot(1).copy();
                if (output.isEmpty()) output = remainder.copy();
                else output.grow(remainder.getCount());
                inventory.setStackInSlot(1, output);
            }
        }
        burnTicks--;
        setChanged();
        return true;
    }

    public CompoundTag saveFuel(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("resourceDemand", demand.save());
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("burnTicks", burnTicks);
        tag.putInt("burnDuration", burnDuration);
        if (!burningFuel.isEmpty()) tag.put("burningFuel", burningFuel.save(registries));
        return tag;
    }

    public void loadFuel(CompoundTag tag, HolderLookup.Provider registries) {
        demand.load(tag.getCompound("resourceDemand"));
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        burnTicks = Math.max(0, tag.getInt("burnTicks"));
        burnDuration = Math.max(burnTicks, tag.getInt("burnDuration"));
        burningFuel = ItemStack.parseOptional(registries, tag.getCompound("burningFuel"));
        setChanged();
    }

    @Override
    public CompoundTag syncDataTo(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(tag, registries, reason);
        tag.put("combustionFuel", saveFuel(registries));
        return tag;
    }

    @Override
    public void syncDataFrom(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(tag, registries, reason);
        if (tag.contains("combustionFuel")) loadFuel(tag.getCompound("combustionFuel"), registries);
    }
}
