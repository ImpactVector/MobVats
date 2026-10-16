package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatItemPortEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Plans all outputs against private staging snapshots before any real slot changes. */
final class VatItemTransaction {
    private record Slot(VatItemPortEntity port, ItemStack before, ItemStack after) {}

    private final List<Slot> slots;

    private VatItemTransaction(List<Slot> slots) {
        this.slots = slots;
    }

    static Optional<VatItemTransaction> plan(List<VatItemPortEntity> ports, List<ItemStack> outputs) {
        var shadows = new ArrayList<ItemStackHandler>();
        var before = new ArrayList<ItemStack>();
        for (var port : ports) {
            var stack = port.staging().getStackInSlot(0).copy();
            before.add(stack);
            var shadow = new ItemStackHandler(1) {
                public boolean isItemValid(int slot, ItemStack stack) {
                    return port.accepts(stack);
                }
            };
            shadow.setStackInSlot(0, stack.copy());
            shadows.add(shadow);
        }
        for (var output : outputs) {
            var remaining = output.copy();
            for (var shadow : shadows) {
                remaining = shadow.insertItem(0, remaining, false);
                if (remaining.isEmpty()) break;
            }
            if (!remaining.isEmpty()) return Optional.empty();
        }
        var slots = new ArrayList<Slot>();
        for (int i = 0; i < ports.size(); i++)
            slots.add(new Slot(
                    ports.get(i),
                    before.get(i),
                    shadows.get(i).getStackInSlot(0).copy()));
        return Optional.of(new VatItemTransaction(List.copyOf(slots)));
    }

    boolean commit() {
        // Minecraft's server thread cannot interleave hopper ticks with this commit.
        // Only controller-owned one-slot staging handlers are used, never arbitrary
        // external handlers that could accept differently from their simulation.
        for (var slot : slots)
            if (slot.port().isRemoved()
                    || !slot.port().getIoDirection().isOutput()
                    || slot.port().redstoneInhibited()
                    || !ItemStack.matches(slot.before(), slot.port().staging().getStackInSlot(0))) return false;
        for (var slot : slots)
            if (!ItemStack.matches(slot.before(), slot.after()))
                slot.port().staging().setStackInSlot(0, slot.after());
        return true;
    }
}
