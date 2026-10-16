package net.impactvector.mobvats.gamecontent.multiblock.mixer.client.screen;

import net.impactvector.mobvats.gamecontent.multiblock.mixer.menu.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerMeterEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MixerMeterScreen extends AbstractContainerScreen<MixerMeterMenu> {
    private EditBox quantity;
    private Button apply;
    private int lastQuantity;

    public MixerMeterScreen(MixerMeterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 240;
        imageHeight = 132;
        inventoryLabelY = -1000;
    }

    protected void init() {
        super.init();
        quantity = new EditBox(
                font, leftPos + 12, topPos + 66, 132, 20, Component.translatable("gui.mobvats.meter_quantity"));
        quantity.setMaxLength(64);
        lastQuantity = menu.quantity();
        quantity.setValue(Integer.toString(lastQuantity));
        addRenderableWidget(quantity);
        apply = addRenderableWidget(Button.builder(Component.translatable("gui.mobvats.meter_apply"), b -> {
                    PacketDistributor.sendToServer(
                            new MixerMeterNetworking.Quantity(menu.containerId, quantity.getValue()));
                })
                .bounds(leftPos + 156, topPos + 66, 72, 20)
                .build());
        quantity.setResponder(value -> validateQuantity());
        validateQuantity();
    }

    private void validateQuantity() {
        try {
            AbstractMixerMeterEntity.parseQuantity(quantity.getValue());
            apply.active = true;
        } catch (IllegalArgumentException e) {
            apply.active = false;
        }
    }

    private Component filterLabel() {
        String value = menu.meter.filter();
        return value.isEmpty()
                ? Component.translatable("gui.mobvats.meter_no_filter")
                : Component.translatable("tooltip.mobvats.endpoint_filter", value);
    }

    protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
    }

    protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, title, 12, 10, 0x404040, false);
        g.drawString(font, font.plainSubstrByWidth(filterLabel().getString(), 216), 12, 30, 0x404040, false);
        g.drawString(
                font,
                Component.translatable(
                        menu.meter.itemMeter() ? "gui.mobvats.meter_quantity" : "gui.mobvats.meter_quantity_mb"),
                12,
                52,
                0x404040,
                false);
        g.drawString(
                font, Component.translatable("gui.mobvats.meter_stored", menu.quantity()), 12, 92, 0x404040, false);
        g.drawString(font, Component.translatable("gui.mobvats.meter_sample"), 12, 112, 0x404040, false);
    }

    public void render(GuiGraphics g, int x, int y, float tick) {
        if (menu.quantity() != lastQuantity) {
            lastQuantity = menu.quantity();
            quantity.setValue(Integer.toString(lastQuantity));
        }
        super.render(g, x, y, tick);
        if (x >= leftPos + 12 && x < leftPos + 228 && y >= topPos + 28 && y < topPos + 42)
            g.renderComponentTooltip(font, java.util.List.of(filterLabel()), x, y);
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        // Keep inventory hotkeys from closing the screen while typing a malformed value.
        if (quantity.isFocused() && key != 256) return quantity.keyPressed(key, scan, modifiers);
        return super.keyPressed(key, scan, modifiers);
    }
}
