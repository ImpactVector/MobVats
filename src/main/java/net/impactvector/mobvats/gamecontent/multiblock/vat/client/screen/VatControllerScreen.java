package net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/** Local observer tabs plus ZeroCore operator activation commands. */
public final class VatControllerScreen extends AbstractContainerScreen<VatControllerMenu> {
    private static final String[] TABS = {"Overview", "Production", "Efficiency", "Diagnostics"};
    private int page, scroll;
    private String lastText = "";
    private List<FormattedCharSequence> lines = List.of();
    private Button activation;

    public VatControllerScreen(VatControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 218;
    }

    @Override
    protected void init() {
        super.init();
        menu.onScreenOpened();
        activation =
                addRenderableWidget(Button.builder(Component.literal("Disable"), b -> menu.setActive(!menu.active()))
                        .bounds(leftPos + imageWidth - 78, topPos + 2, 70, 18)
                        .build());
        for (int i = 0; i < TABS.length; ++i) {
            final int tab = i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> {
                        page = tab;
                        scroll = 0;
                        lastText = "";
                    })
                    .bounds(leftPos + 8 + i * 72, topPos + 23, 70, 20)
                    .build());
        }
    }

    private int visibleLines() {
        return 13;
    }

    private void updateLines() {
        var text = menu.page(page);
        if (!lastText.equals(text)) {
            lastText = text;
            var wrapped = new ArrayList<FormattedCharSequence>();
            for (var line : text.split("\n")) wrapped.addAll(font.split(Component.literal(line), imageWidth - 24));
            lines = List.copyOf(wrapped);
            scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visibleLines()));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        g.fill(leftPos + 6, topPos + 47, leftPos + imageWidth - 6, topPos + 191, 0xff242b2e);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 8, 0xff202020, false);
        updateLines();
        for (int i = scroll; i < Math.min(lines.size(), scroll + visibleLines()); ++i)
            g.drawString(font, lines.get(i), 12, 52 + (i - scroll) * 10, 0xffe2e8e8, false);
        g.drawString(font, "Scroll to inspect / " + TABS[page], 8, 198, 0xff202020, false);
        if (!menu.displayedTool().isEmpty()) {
            g.renderItem(menu.displayedTool(), imageWidth - 24, 195);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        updateLines();
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 3, 0, Math.max(0, lines.size() - visibleLines()));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        activation.setMessage(Component.literal(menu.active() ? "Disable" : "Enable"));
        super.render(g, mouseX, mouseY, tick);
        if (!menu.displayedTool().isEmpty()
                && mouseX >= leftPos + imageWidth - 24
                && mouseX < leftPos + imageWidth - 8
                && mouseY >= topPos + 195
                && mouseY < topPos + 211) g.renderTooltip(font, menu.displayedTool(), mouseX, mouseY);
    }
}
