package net.impactvector.mobvats.gamecontent.multiblock.common;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.IoMode;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.*;

/** Shared endpoint state, independent of machine ownership and storage. */
public final class EndpointConfiguration {
    public enum Resource {
        ITEM,
        FLUID,
        NONE
    }

    private final Resource resource;
    private final Runnable changed;
    private final IoDirection initialDirection;
    private IoDirection direction;
    private IoMode mode = IoMode.Both;
    private String filter = "";

    public EndpointConfiguration(Resource resource, IoDirection initialDirection, Runnable changed) {
        this.resource = resource;
        this.initialDirection = initialDirection;
        this.direction = initialDirection;
        this.changed = changed;
    }

    public Resource resource() {
        return resource;
    }

    public IoDirection direction() {
        return direction;
    }

    public IoMode mode() {
        return mode;
    }

    public void mode(IoMode value) {
        java.util.Objects.requireNonNull(value);
        if (mode != value) {
            mode = value;
            changed.run();
        }
    }

    public void direction(IoDirection value) {
        if (direction != value) {
            direction = value;
            changed.run();
        }
    }

    public String filter() {
        return filter;
    }

    public void filter(String value) {
        if (!value.isEmpty()) {
            if (resource == Resource.ITEM) ResourceSelectors.items(java.util.List.of(value));
            else if (resource == Resource.FLUID) ResourceSelectors.fluids(java.util.List.of(value));
            else throw new IllegalArgumentException("Not a generic resource port");
        }
        if (!filter.equals(value)) {
            filter = value;
            changed.run();
        }
    }

    public boolean accepts(ItemStack stack) {
        return filter.isEmpty()
                || ResourceSelectors.items(java.util.List.of(filter)).test(stack);
    }

    public boolean accepts(FluidStack stack) {
        return filter.isEmpty()
                || ResourceSelectors.fluids(java.util.List.of(filter)).test(stack);
    }

    private String key() {
        return resource == Resource.ITEM ? "itemFilter" : "fluidFilter";
    }

    private static IoMode readMode(CompoundTag tag) {
        try {
            return IoMode.read(tag, "ioMode", IoMode.Both);
        } catch (IllegalArgumentException ignored) {
            return IoMode.Both;
        }
    }

    public void load(CompoundTag tag) {
        direction = IoDirection.read(tag, "ioDirection", initialDirection);
        mode = readMode(tag);
        filter = resource == Resource.NONE ? "" : tag.getString(key());
        changed.run();
    }

    public void save(CompoundTag tag) {
        IoDirection.write(tag, "ioDirection", direction);
        IoMode.write(tag, "ioMode", mode);
        if (resource != Resource.NONE) tag.putString(key(), filter);
    }

    public void capture(CompoundTag tag) {
        tag.putString("filter", filter);
        tag.putBoolean("output", direction.isOutput());
        IoMode.write(tag, "ioMode", mode);
    }

    public void restore(CompoundTag tag) {
        filter(tag.getString("filter"));
        direction(tag.getBoolean("output") ? IoDirection.Output : IoDirection.Input);
        mode(readMode(tag));
    }

    public boolean configure(Player player, InteractionHand hand, boolean assembled) {
        if (resource == Resource.NONE || !player.isShiftKeyDown()) return false;
        if (player.level().isClientSide) return true;
        if (assembled) {
            player.displayClientMessage(Component.translatable("message.mobvats.filter_locked"), true);
            return true;
        }
        var held = player.getItemInHand(hand);
        String value = "";
        if (!held.isEmpty()) {
            if (resource == Resource.ITEM) value = EndpointState.itemId(held);
            else {
                var sample = FluidUtil.getFluidContained(held);
                if (sample.isEmpty()) return true;
                value = EndpointState.fluidId(sample.get());
            }
        }
        filter(value);
        player.displayClientMessage(
                value.isEmpty()
                        ? Component.translatable("message.mobvats.filter_cleared")
                        : Component.translatable("tooltip.mobvats.endpoint_filter", value),
                true);
        return true;
    }
}
