package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Persisted lightweight per-process state. No world/entity/controller references. */
public final class ProcessState {
    public enum Phase {
        IDLE,
        RUNNING,
        READY,
        BLOCKED
    }

    public record PreparedOutput(List<ItemStack> items, int xp) {
        public PreparedOutput {
            items = items.stream().map(ItemStack::copy).toList();
        }
    }

    public Phase phase = Phase.IDLE;
    public int elapsed;
    public double frequencyRemainder;

    public int advance(double rate) {
        if (!Double.isFinite(rate) || rate < 0) throw new IllegalArgumentException("Invalid progress rate");
        double total = rate + frequencyRemainder;
        int whole = (int) Math.floor(total + 1e-10);
        frequencyRemainder = Math.max(0, total - whole);
        return whole;
    }

    public CompoundTag data = new CompoundTag();
    public PreparedOutput pending;
    public ItemStack pendingTool = ItemStack.EMPTY;

    public int remaining(int duration) {
        return Math.max(0, duration - elapsed);
    }

    public void start() {
        phase = Phase.RUNNING;
        elapsed = 0;
    }

    public void reset() {
        phase = Phase.IDLE;
        elapsed = 0;
        pending = null;
        pendingTool = ItemStack.EMPTY;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("phase", phase.name());
        tag.putInt("elapsed", elapsed);
        tag.putDouble("frequencyRemainder", frequencyRemainder);
        tag.put("data", data.copy());
        if (pending != null) {
            var items = new ListTag();
            for (var item : pending.items()) if (!item.isEmpty()) items.add(item.save(registries));
            tag.put("pendingItems", items);
            tag.putInt("pendingXp", pending.xp());
            if (!pendingTool.isEmpty()) tag.put("pendingTool", pendingTool.save(registries));
        }
        return tag;
    }

    public static ProcessState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new ProcessState();
        try {
            state.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException ignored) {
        }
        state.elapsed = Math.max(0, tag.getInt("elapsed"));
        state.data = tag.getCompound("data").copy();
        double remainder = tag.getDouble("frequencyRemainder");
        state.frequencyRemainder = Double.isFinite(remainder) && remainder >= 0 && remainder < 1 ? remainder : 0;
        if (tag.contains("pendingItems")) {
            var items = new ArrayList<ItemStack>();
            var list = tag.getList("pendingItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var stack = ItemStack.parseOptional(registries, list.getCompound(i));
                if (!stack.isEmpty()) items.add(stack);
            }
            state.pending = new PreparedOutput(items, Math.max(0, tag.getInt("pendingXp")));
            state.pendingTool = ItemStack.parseOptional(registries, tag.getCompound("pendingTool"));
        }
        return state;
    }
}
