package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatItemPortEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Committed item batch owned by one Tool; also used for legacy controller migration escrow. */
public class ItemResultBuffer {
    private final Runnable changed;

    public ItemResultBuffer() {
        this(() -> {});
    }

    public ItemResultBuffer(Runnable changed) {
        this.changed = changed;
    }

    private final List<ItemStack> stacks = new ArrayList<>();

    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    public int stackCount() {
        return (int) stacks.stream().filter(s -> !s.isEmpty()).count();
    }

    public long itemCount() {
        return stacks.stream().mapToLong(ItemStack::getCount).sum();
    }

    public List<ItemStack> snapshot() {
        return stacks.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
    }

    private final net.neoforged.neoforge.items.IItemHandlerModifiable resources =
            new net.neoforged.neoforge.items.IItemHandlerModifiable() {
                public int getSlots() {
                    return Math.max(64, stacks.size());
                }

                public ItemStack getStackInSlot(int slot) {
                    return slot < stacks.size() ? stacks.get(slot).copy() : ItemStack.EMPTY;
                }

                public void setStackInSlot(int slot, ItemStack stack) {
                    while (stacks.size() <= slot) stacks.add(ItemStack.EMPTY);
                    stacks.set(slot, stack.copy());
                    changed.run();
                }

                public int getSlotLimit(int slot) {
                    return 64;
                }

                public boolean isItemValid(int slot, ItemStack stack) {
                    return true;
                }

                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    var old = getStackInSlot(slot);
                    if (!old.isEmpty() && !ItemStack.isSameItemSameComponents(old, stack)) return stack;
                    int moved = Math.min(
                            stack.getCount(), Math.max(0, Math.min(64, stack.getMaxStackSize()) - old.getCount()));
                    if (!simulate && moved > 0) setStackInSlot(slot, stack.copyWithCount(old.getCount() + moved));
                    return stack.copyWithCount(stack.getCount() - moved);
                }

                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    var old = getStackInSlot(slot);
                    int count = Math.min(Math.max(amount, 0), old.getCount());
                    if (!simulate && count > 0) setStackInSlot(slot, old.copyWithCount(old.getCount() - count));
                    return old.copyWithCount(count);
                }
            };

    public net.neoforged.neoforge.items.IItemHandlerModifiable resources() {
        return resources;
    }
    /** Prepare every copy before changing the buffer. A second kill cannot overwrite a backlog. */
    public boolean store(List<ItemStack> drops) {
        if (!isEmpty() || drops == null) return false;
        var prepared = new ArrayList<ItemStack>();
        for (var stack : drops) {
            if (stack == null || stack.getCount() < 0 || (!stack.isEmpty() && stack.getMaxStackSize() <= 0))
                return false;
            if (!stack.isEmpty()) prepared.add(stack.copy());
        }
        stacks.clear();
        stacks.addAll(prepared);
        changed.run();
        return true;
    }
    /** Called on the server cadence with already classified, sorted, controller-owned endpoints. */
    public long drain(List<VatItemPortEntity> ports) {
        long moved = 0;
        for (var iterator = stacks.listIterator(); iterator.hasNext(); ) {
            var remaining = iterator.next();
            for (var port : ports) {
                if (port.isRemoved()
                        || port.redstoneInhibited()
                        || !port.getIoDirection().isOutput()) continue;
                int before = remaining.getCount();
                // Private one-slot staging only: no arbitrary external handler or simulated acceptance.
                remaining = port.staging().insertItem(0, remaining, false);
                moved += before - remaining.getCount();
                if (remaining.isEmpty()) break;
            }
            if (remaining.isEmpty()) iterator.remove();
            else iterator.set(remaining);
        }
        if (moved > 0) changed.run();
        return moved;
    }

    public long discard(
            java.util.List<net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryEntity> voids) {
        long removed = 0;
        for (var iterator = stacks.iterator(); iterator.hasNext(); ) {
            var stack = iterator.next();
            for (var sink : voids)
                if (sink.accepts(stack)) {
                    removed += stack.getCount();
                    iterator.remove();
                    sink.consumed();
                    break;
                }
        }
        if (removed > 0) changed.run();
        return removed;
    }

    public ListTag save(HolderLookup.Provider registries) {
        var saved = new ListTag();
        for (var stack : snapshot()) saved.add(stack.save(registries));
        return saved;
    }

    public void load(ListTag saved, HolderLookup.Provider registries) {
        stacks.clear();
        for (int i = 0; i < saved.size(); ++i) {
            var stack = ItemStack.parseOptional(registries, saved.getCompound(i));
            if (!stack.isEmpty()) stacks.add(stack);
        }
    }
    /** Reconnection must preserve both controllers' committed items, even if that combines batches. */
    public void assimilate(ItemResultBuffer source) {
        stacks.addAll(source.snapshot());
        changed.run();
    }

    public void clear() {
        stacks.clear();
        changed.run();
    }
}
