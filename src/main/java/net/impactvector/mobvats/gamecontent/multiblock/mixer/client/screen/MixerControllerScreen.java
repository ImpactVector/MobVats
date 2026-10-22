package net.impactvector.mobvats.gamecontent.multiblock.mixer.client.screen;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerControllerSnapshot;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerControllerSnapshot.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.menu.MixerControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** One production view, one resource list. All navigation is local and read-only. */
public final class MixerControllerScreen extends AbstractContainerScreen<MixerControllerMenu> {
    private static final int TEXT = 0xffdce6e7, MUTED = 0xff9baeb2, ACCENT = 0xff67b6aa;
    private boolean diagnostics;
    private int resourceScroll, diagnosticScroll, currentScroll, completedScroll;
    private List<Component> tooltip = List.of();
    private int tooltipScroll;
    private String tooltipKey = "";
    private MixerControllerSnapshot cached;
    private List<net.minecraft.util.FormattedCharSequence> diagnosticLines = List.of();
    private final Map<String, ItemStack> itemIcons = new HashMap<>();
    private final Map<String, FluidStack> fluidIcons = new HashMap<>();

    public MixerControllerScreen(MixerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected void init() {
        imageWidth = Math.min(440, width - 12);
        imageHeight = Math.min(320, height - 12);
        super.init();
        menu.onScreenOpened();
        addRenderableWidget(Button.builder(Component.literal("Diagnostics"), b -> {
                    diagnostics = !diagnostics;
                    b.setMessage(Component.literal(diagnostics ? "Production" : "Diagnostics"));
                })
                .bounds(leftPos + imageWidth - 88, topPos + 7, 80, 18)
                .build());
        cached = null;
    }

    private int resourceTop() {
        return 163;
    }

    private int resourceBottom() {
        return imageHeight - 35;
    }

    private int resourceRows() {
        return Math.max(1, (resourceBottom() - resourceTop()) / 20);
    }

    private void text(GuiGraphics g, String text, int x, int y, int width, int color, int mx, int my) {
        g.drawString(font, font.plainSubstrByWidth(text, Math.max(1, width)), leftPos + x, topPos + y, color, false);
        if (mx >= leftPos + x && mx < leftPos + x + width && my >= topPos + y && my < topPos + y + 10)
            tooltip = List.of(Component.literal(text));
    }

    private void card(GuiGraphics g, int y, int end) {
        g.fill(leftPos + 7, topPos + y, leftPos + imageWidth - 7, topPos + end, 0xff202e35);
    }

    private String time(long ticks) {
        if (ticks < 0) return menu.view().condition();
        long seconds = (ticks + 19) / 20;
        return seconds < 60 ? seconds + "s remaining" : seconds / 60 + "m " + seconds % 60 + "s remaining";
    }

    private void icon(GuiGraphics g, Product product, int x, int y) {
        try {
            if (!product.fluid()) {
                var stack = itemIcons.computeIfAbsent(product.stack(), key -> {
                    try {
                        return ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(key));
                    } catch (Exception e) {
                        return ItemStack.EMPTY;
                    }
                });
                g.renderItem(stack, x, y);
            } else {
                var stack = fluidIcons.computeIfAbsent(product.stack(), key -> {
                    try {
                        return FluidStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(key));
                    } catch (Exception e) {
                        return FluidStack.EMPTY;
                    }
                });
                if (stack.isEmpty()) return;
                var extension = IClientFluidTypeExtensions.of(stack.getFluid());
                var texture = extension.getStillTexture(stack);
                if (texture == null) return;
                var sprite =
                        minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
                int tint = extension.getTintColor(stack);
                g.setColor(
                        (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, (tint >>> 24) / 255f);
                g.blit(x, y, 0, 16, 16, sprite);
                g.setColor(1, 1, 1, 1);
            }
        } catch (RuntimeException ignored) {
            g.setColor(1, 1, 1, 1);
        }
    }

    private void productTooltip(Product p, String context) {
        String key = p.stack() + context;
        if (!key.equals(tooltipKey)) {
            tooltipScroll = 0;
            tooltipKey = key;
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(p.label()));
        lines.add(Component.literal(context));
        // Exact component data is wrapped in a scrollable resource row's hover, including compound composition.
        for (var line : font.split(Component.literal(p.details()), Math.min(360, width - 24))) {
            var text = new StringBuilder();
            line.accept((index, style, code) -> {
                text.appendCodePoint(code);
                return true;
            });
            lines.add(Component.literal(text.toString()));
        }
        int visible = Math.max(3, (height - 40) / 10);
        if (lines.size() > visible) {
            tooltipScroll = Math.clamp(tooltipScroll, 0, lines.size() - visible);
            var page = new ArrayList<>(lines.subList(tooltipScroll, tooltipScroll + visible));
            page.add(Component.literal("Shift + scroll for details · " + (tooltipScroll + 1) + "/" + lines.size()));
            tooltip = page;
        } else tooltip = lines;
    }

    private void products(GuiGraphics g, Batch batch, int y, int scroll, int mx, int my) {
        int available = imageWidth - 24;
        int total = batch.products().size() * 150;
        scroll = Math.clamp(scroll, 0, Math.max(0, total - available));
        g.enableScissor(leftPos + 12, topPos + y, leftPos + imageWidth - 12, topPos + y + 19);
        for (int i = 0; i < batch.products().size(); i++) {
            var product = batch.products().get(i);
            int x = 12 + i * 150 - scroll;
            if (x + 150 < 12 || x >= imageWidth - 12) continue;
            icon(g, product, leftPos + x, topPos + y);
            g.drawString(
                    font, font.plainSubstrByWidth(product.label(), 128), leftPos + x + 20, topPos + y + 4, TEXT, false);
            if (mx >= leftPos + Math.max(12, x)
                    && mx < leftPos + Math.min(imageWidth - 12, x + 150)
                    && my >= topPos + y
                    && my < topPos + y + 19)
                productTooltip(product, batch.note() + (total > available ? " · Scroll to see all products" : ""));
        }
        g.disableScissor();
        if (total > available) bar(g, y + 18, scroll, total, available);
    }

    private void bar(GuiGraphics g, int y, int offset, int total, int visible) {
        int w = imageWidth - 24;
        g.fill(leftPos + 12, topPos + y, leftPos + 12 + w, topPos + y + 2, 0xff35464d);
        int thumb = Math.max(8, w * visible / Math.max(1, total));
        int start = (w - thumb) * offset / Math.max(1, total - visible);
        g.fill(leftPos + 12 + start, topPos + y, leftPos + 12 + start + thumb, topPos + y + 2, ACCENT);
    }

    protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        var view = menu.view();
        tooltip = List.of();
        if (cached != view) {
            cached = view;
            itemIcons.clear();
            fluidIcons.clear();
            diagnosticLines = font.split(Component.literal(view.diagnostics()), imageWidth - 32);
        }
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff172127);
        text(g, title.getString(), 12, 10, imageWidth - 108, TEXT, mx, my);
        text(
                g,
                (view.formed() ? "Formed · " : "Unformed · ") + view.condition(),
                12,
                29,
                imageWidth - 24,
                ACCENT,
                mx,
                my);
        if (diagnostics) {
            card(g, 43, imageHeight - 8);
            int rows = Math.max(1, (imageHeight - 62) / 10);
            diagnosticScroll = Math.clamp(diagnosticScroll, 0, Math.max(0, diagnosticLines.size() - rows));
            for (int i = 0; i < rows && i + diagnosticScroll < diagnosticLines.size(); i++)
                g.drawString(
                        font,
                        diagnosticLines.get(i + diagnosticScroll),
                        leftPos + 13,
                        topPos + 49 + i * 10,
                        TEXT,
                        false);
            return;
        }
        var batch = view.current();
        card(g, 43, 108);
        text(
                g,
                batch.recipe().isEmpty()
                        ? "Current batch · Waiting for inputs"
                        : (batch.active() ? "Current batch · " : "Next recipe · ") + batch.name(),
                12,
                48,
                imageWidth - 24,
                TEXT,
                mx,
                my);
        products(g, batch, 62, currentScroll, mx, my);
        double fraction = batch.duration() <= 0 ? 0 : Math.clamp((double) batch.progress() / batch.duration(), 0, 1);
        g.fill(leftPos + 12, topPos + 85, leftPos + imageWidth - 12, topPos + 90, 0xff35464d);
        g.fill(leftPos + 12, topPos + 85, leftPos + 12 + (int) ((imageWidth - 24) * fraction), topPos + 90, ACCENT);
        text(
                g,
                String.format(Locale.ROOT, "%.0f%% · %s", fraction * 100, time(batch.remainingTicks())),
                12,
                95,
                imageWidth - 24,
                MUTED,
                mx,
                my);
        card(g, 111, 151);
        var last = view.completed();
        text(
                g,
                last.recipe().isEmpty() ? "No completed batch" : "Last completed · " + last.name(),
                12,
                115,
                imageWidth - 24,
                MUTED,
                mx,
                my);
        products(g, last, 129, completedScroll, mx, my);
        text(
                g,
                "Resources · " + view.resources().size() + " entries · Scroll to inspect",
                12,
                154,
                imageWidth - 24,
                MUTED,
                mx,
                my);
        int rows = resourceRows();
        resourceScroll =
                Math.clamp(resourceScroll, 0, Math.max(0, view.resources().size() - rows));
        g.enableScissor(leftPos + 7, topPos + resourceTop(), leftPos + imageWidth - 7, topPos + resourceBottom());
        for (int i = 0; i < rows && i + resourceScroll < view.resources().size(); i++) {
            var resource = view.resources().get(i + resourceScroll);
            int y = resourceTop() + i * 20;
            if (i % 2 == 0) card(g, y, y + 20);
            icon(g, resource.product(), leftPos + 12, topPos + y + 2);
            text(g, resource.product().label(), 33, y + 1, imageWidth - 48, TEXT, mx, my);
            text(g, resource.section(), 33, y + 11, imageWidth - 48, MUTED, mx, my);
            if (mx >= leftPos + 7 && mx < leftPos + imageWidth - 7 && my >= topPos + y && my < topPos + y + 20)
                productTooltip(
                        resource.product(),
                        resource.section() + " @ " + resource.location() + "\n" + resource.availability()
                                + (resource.capacity() > 0 ? "\nCapacity: " + resource.capacity() + " mB" : ""));
        }
        g.disableScissor();
        if (view.resources().size() > rows) {
            int h = Math.max(1, resourceBottom() - resourceTop());
            int thumb = Math.max(5, h * rows / view.resources().size());
            int y = topPos
                    + resourceTop()
                    + (h - thumb) * resourceScroll / (view.resources().size() - rows);
            g.fill(leftPos + imageWidth - 9, y, leftPos + imageWidth - 7, y + thumb, ACCENT);
        }
        text(g, view.drive() + " · " + view.power(), 12, imageHeight - 28, imageWidth - 24, TEXT, mx, my);
        text(
                g,
                (view.advancing() ? "Advancing · " : "Not advancing · ") + view.condition(),
                12,
                imageHeight - 15,
                imageWidth - 24,
                MUTED,
                mx,
                my);
    }

    protected void renderLabels(GuiGraphics g, int x, int y) {}

    public void render(GuiGraphics g, int x, int y, float tick) {
        super.render(g, x, y, tick);
        if (!tooltip.isEmpty()) g.renderComponentTooltip(font, tooltip, x, y);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < leftPos || x >= leftPos + imageWidth || y < topPos || y >= topPos + imageHeight) return false;
        int amount = (int) Math.signum(dy);
        if (hasShiftDown() && !tooltip.isEmpty()) {
            tooltipScroll = Math.max(0, tooltipScroll - amount * 3);
            return true;
        }
        int local = (int) y - topPos;
        if (diagnostics) diagnosticScroll = Math.max(0, diagnosticScroll - amount * 3);
        else if (local >= 62 && local < 83)
            currentScroll = Math.clamp(
                    currentScroll - amount * 80,
                    0,
                    Math.max(0, menu.view().current().products().size() * 150 - (imageWidth - 24)));
        else if (local >= 129 && local < 150)
            completedScroll = Math.clamp(
                    completedScroll - amount * 80,
                    0,
                    Math.max(0, menu.view().completed().products().size() * 150 - (imageWidth - 24)));
        else if (local >= resourceTop() && local < resourceBottom())
            resourceScroll = Math.max(0, resourceScroll - amount * 3);
        else return false;
        return true;
    }
}
