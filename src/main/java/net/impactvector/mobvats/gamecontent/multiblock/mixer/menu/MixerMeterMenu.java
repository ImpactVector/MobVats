package net.impactvector.mobvats.gamecontent.multiblock.mixer.menu;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerMeterEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class MixerMeterMenu extends AbstractContainerMenu {
    public final AbstractMixerMeterEntity meter;
    private final ContainerData data = new SimpleContainerData(2);

    public MixerMeterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, (AbstractMixerMeterEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }

    public MixerMeterMenu(int id, Inventory inventory, AbstractMixerMeterEntity meter) {
        super(Content.METER_MENU.get(), id);
        this.meter = meter;
        updateQuantity();
        addDataSlots(data);
    }

    private void updateQuantity() {
        data.set(0, meter.quantity() & 65535);
        data.set(1, meter.quantity() >>> 16);
    }

    public int quantity() {
        return data.get(0) & 65535 | (data.get(1) & 65535) << 16;
    }

    public boolean stillValid(Player player) {
        return !meter.isRemoved()
                && player.level() == meter.getLevel()
                && player.level().getBlockEntity(meter.getBlockPos()) == meter
                && player.distanceToSqr(
                                meter.getBlockPos().getX() + .5,
                                meter.getBlockPos().getY() + .5,
                                meter.getBlockPos().getZ() + .5)
                        <= 64;
    }
    /** Used only by the server payload handler, after checking the open container ID. */
    public boolean applyQuantity(Player player, String value) {
        if (player.level().isClientSide || !stillValid(player) || !meter.quantity(value)) return false;
        broadcastChanges();
        return true;
    }

    public void broadcastChanges() {
        if (meter.getLevel() != null && !meter.getLevel().isClientSide) updateQuantity();
        super.broadcastChanges();
    }

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
