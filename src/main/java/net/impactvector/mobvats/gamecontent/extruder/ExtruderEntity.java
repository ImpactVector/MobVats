package net.impactvector.mobvats.gamecontent.extruder;

import com.mojang.logging.LogUtils;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.product.ProductContent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;

/** One full, component-exact batch per cycle; no tank or multiblock ownership. */
public final class ExtruderEntity extends BlockEntity {
    public static final int CYCLE_TICKS = 10;
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler output = new IItemHandler() {
        public int getSlots() {
            return 1;
        }

        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot).copy();
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.extractItem(slot, amount, simulate);
        }

        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };
    private BlockCapabilityCache<IFluidHandler, Direction> inputCache;
    private BlockCapabilityCache<IItemHandler, Direction> destinationCache;
    private Direction cachedFacing;
    private boolean reportedBadDrain;

    public ExtruderEntity(BlockPos pos, BlockState state) {
        super(ExtruderContent.ENTITY.get(), pos, state);
    }

    public IItemHandler output() {
        return output;
    }

    public void tick() {
        if (level instanceof ServerLevel && level.getGameTime() % CYCLE_TICKS == 0) processCycle();
    }

    public void processCycle() {
        // Query at execution time: retained capabilities and reloads cannot leave a stale signal.
        // Vanilla also checks strong power through neighboring conducting blocks.
        if (!(level instanceof ServerLevel server) || server.hasNeighborSignal(worldPosition)) return;
        var facing = getBlockState().getValue(ExtruderBlock.FACING);
        if (inputCache == null || facing != cachedFacing) {
            cachedFacing = facing;
            inputCache = BlockCapabilityCache.create(
                    Capabilities.FluidHandler.BLOCK,
                    server,
                    worldPosition.relative(facing.getOpposite()),
                    facing,
                    () -> !isRemoved(),
                    () -> {});
        }
        if (destinationCache == null)
            destinationCache = BlockCapabilityCache.create(
                    Capabilities.ItemHandler.BLOCK,
                    server,
                    worldPosition.below(),
                    Direction.UP,
                    () -> !isRemoved(),
                    () -> {});
        var destination = destinationCache.getCapability();
        var stored = inventory.getStackInSlot(0);
        if (destination != null && !stored.isEmpty()) {
            var remainder = ItemHandlerHelper.insertItemStacked(destination, stored.copy(), false);
            if (remainder.getCount() != stored.getCount()) inventory.setStackInSlot(0, remainder);
        }
        var source = inputCache.getCapability();
        if (source == null) return;
        boolean block = getBlockState().getValue(ExtruderBlock.FORM) == ExtruderBlock.Form.BLOCK;
        // Stable handler order. Never merge identities or use amount-only drain.
        for (int tank = 0; tank < source.getTanks(); tank++) {
            var sample = source.getFluidInTank(tank);
            boolean meat = sample.is(ProductFluids.MEAT.get());
            if (!meat && !sample.is(ProductFluids.WASTE.get())) continue;
            int amount = block ? 1000 : meat ? 250 : 100;
            var product = new ItemStack(
                    block
                            ? (meat ? ProductContent.MEAT_BLOCK.get() : ProductContent.WASTE_BLOCK.get()).asItem()
                            : meat ? ProductContent.RAW_MEAT.get() : ProductContent.FERTILIZER.get());
            if (!inventory.insertItem(0, product, true).isEmpty()) continue;
            var request = sample.copyWithAmount(amount);
            var simulated = source.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (!exact(request, simulated)) continue;
            var drained = source.drain(request, IFluidHandler.FluidAction.EXECUTE);
            if (!exact(request, drained)) {
                if (!reportedBadDrain) {
                    LogUtils.getLogger()
                            .warn(
                                    "Extruder at {}: fluid handler violated simulated drain; no item produced. Extracted fluid cannot safely be restored.",
                                    worldPosition);
                    reportedBadDrain = true;
                }
                return;
            }
            inventory.insertItem(0, product, false);
            return;
        }
    }

    private static boolean exact(FluidStack expected, FluidStack actual) {
        return actual.getAmount() == expected.getAmount() && FluidStack.isSameFluidSameComponents(expected, actual);
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", inventory.serializeNBT(registries));
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("output"));
    }
}
