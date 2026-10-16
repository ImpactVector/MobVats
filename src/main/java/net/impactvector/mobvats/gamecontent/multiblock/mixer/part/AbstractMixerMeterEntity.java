package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Sampled processing control; exposes no resource endpoint, capability or inventory. */
public abstract class AbstractMixerMeterEntity extends AbstractMixerEntity implements MenuProvider {
    private String filter = "";
    private int quantity;

    protected AbstractMixerMeterEntity(MixerPartType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        quantity = type == MixerPartType.ItemMeter ? 1 : 1000;
    }

    public boolean itemMeter() {
        return getPartType() == MixerPartType.ItemMeter;
    }

    public String filter() {
        return filter;
    }

    public int quantity() {
        return quantity;
    }

    private void validate(String value, int amount) {
        if (value.isEmpty()) throw new IllegalArgumentException("missing filter");
        if (itemMeter()) new ItemMeterRequirement(value, amount);
        else new FluidMeterRequirement(value, amount);
    }

    public String problem() {
        try {
            validate(filter, quantity);
            return "";
        } catch (RuntimeException e) {
            return getDisplayName().getString() + " at " + getBlockPos().toShortString() + ": "
                    + (quantity <= 0
                            ? "quantity must be positive"
                            : filter.isEmpty() ? "missing filter" : "invalid or incompatible filter");
        }
    }

    public void filter(String value) {
        if (!value.isEmpty()) validate(value, Math.max(1, quantity));
        if (!filter.equals(value)) {
            filter = value;
            configurationChanged();
        }
    }
    /** Rejection leaves the previous effective quantity intact. */
    public static int parseQuantity(String text) {
        if (text == null || !text.matches("[0-9]{1,10}"))
            throw new IllegalArgumentException("positive integer required");
        int value = Integer.parseInt(text);
        if (value <= 0) throw new IllegalArgumentException("positive integer required");
        return value;
    }

    public boolean quantity(String text) {
        final int value;
        try {
            value = parseQuantity(text);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (quantity != value) {
            quantity = value;
            configurationChanged();
        }
        return true;
    }

    private void configurationChanged() {
        setChanged();
        if (level != null && !level.isClientSide) notifyBlockUpdate();
    }

    public boolean configure(Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return false;
        if (player.level().isClientSide) return true;
        var held = player.getItemInHand(hand);
        String value = "";
        if (!held.isEmpty()) {
            if (itemMeter()) value = EndpointState.itemId(held);
            else {
                var sample = FluidUtil.getFluidContained(held);
                if (sample.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.mobvats.meter_fluid_sample"), true);
                    return true;
                }
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

    public CompoundTag configuration() {
        var tag = new CompoundTag();
        tag.putString("meterFilter", filter);
        tag.putInt("meterQuantity", quantity);
        return tag;
    }

    public void loadConfiguration(CompoundTag tag) {
        // Preserve invalid saved state so it blocks processing instead of becoming a default.
        filter = tag.getString("meterFilter");
        quantity = tag.contains("meterQuantity", net.minecraft.nbt.Tag.TAG_INT) ? tag.getInt("meterQuantity") : 0;
    }

    public CompoundTag syncDataTo(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(tag, registries, reason);
        tag.merge(configuration());
        return tag;
    }

    public void syncDataFrom(CompoundTag tag, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(tag, registries, reason);
        loadConfiguration(tag);
    }

    public Component getDisplayName() {
        return Component.translatable(getPartType().getTranslationKey());
    }

    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new net.impactvector.mobvats.gamecontent.multiblock.mixer.menu.MixerMeterMenu(id, inventory, this);
    }
}
