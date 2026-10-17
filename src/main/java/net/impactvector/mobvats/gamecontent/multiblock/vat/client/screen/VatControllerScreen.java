package net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatSpecimenSnapshot;
import net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu;
import net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatSpecimenPreview;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveProcessCompiler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Specimen observer; navigation is local and activation retains the existing controller command. */
public final class VatControllerScreen extends AbstractContainerScreen<VatControllerMenu> {
    private static final String[] PAGES = {"Overview", "Production", "Efficiency", "Diagnostics"};
    private static final int TEXT = 0xffdce6e7, MUTED = 0xff9baeb2, ACCENT = 0xff67b6aa;
    private boolean diagnostics;
    private static final int PREVIEW_HEIGHT = 88;
    private final VatSpecimenPreview preview = new VatSpecimenPreview();
    private int page, listScroll, detailScroll, diagnosticScroll, detailHeight, diagnosticHeight, listWidth;
    private Button activation, mode, diagnosticPage;
    private VatSpecimenSnapshot cachedSpecimen;
    private int cachedWidth;
    private List<Line> cachedDetails = List.of();
    private String cachedDiagnostic = "";
    private List<net.minecraft.util.FormattedCharSequence> cachedDiagnosticLines = List.of();

    private record Line(String text, double progress, String item) {}

    public VatControllerScreen(VatControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(460, width - 12);
        imageHeight = Math.min(310, height - 12);
        listWidth = Math.min(150, imageWidth / 3);
        super.init();
        menu.onScreenOpened();
        activation =
                addRenderableWidget(Button.builder(Component.literal("Disable"), b -> menu.setActive(!menu.active()))
                        .bounds(leftPos + imageWidth - 72, topPos + 7, 64, 18)
                        .build());
        mode = addRenderableWidget(Button.builder(Component.literal("Diagnostics"), b -> {
                    diagnostics = !diagnostics;
                    mode.setMessage(Component.literal(diagnostics ? "Specimens" : "Diagnostics"));
                })
                .bounds(leftPos + imageWidth - 160, topPos + 7, 84, 18)
                .build());
        diagnosticPage = addRenderableWidget(Button.builder(Component.literal("Next page"), b -> {
                    page = (page + 1) % PAGES.length;
                    diagnosticScroll = 0;
                })
                .bounds(leftPos + imageWidth - 84, topPos + 31, 76, 18)
                .build());
    }

    private int bodyHeight() {
        return imageHeight - 80;
    }

    private int rows() {
        return Math.max(1, bodyHeight() / 36);
    }

    private String fit(String text, int pixels) {
        return font.plainSubstrByWidth(text, Math.max(1, pixels));
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String time(long ticks) {
        if (ticks < 0) return "Waiting";
        long seconds = (ticks + 19) / 20;
        return seconds < 60
                ? seconds + "s"
                : seconds < 3600
                        ? seconds / 60 + "m " + seconds % 60 + "s"
                        : seconds / 3600 + "h " + seconds / 60 % 60 + "m";
    }

    private void bar(GuiGraphics g, int x, int y, int width, double fraction) {
        g.fill(x, y, x + width, y + 3, 0xff35464d);
        g.fill(x, y, x + (int) (width * Math.clamp(fraction, 0, 1)), y + 3, ACCENT);
    }

    private void scrollbar(GuiGraphics g, int x, int y, int height, int offset, int total, int visible) {
        if (total <= visible) return;
        int thumb = Math.max(8, height * visible / total);
        int start = y + (height - thumb) * offset / Math.max(1, total - visible);
        g.fill(x, y, x + 2, y + height, 0xff35464d);
        g.fill(x, start, x + 2, start + thumb, ACCENT);
    }

    @Override
    protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff172127);
        g.fill(leftPos + 5, topPos + 53, leftPos + imageWidth - 5, topPos + imageHeight - 21, 0xff202e35);
        if (!diagnostics)
            g.fill(
                    leftPos + listWidth + 7,
                    topPos + 53,
                    leftPos + listWidth + 8,
                    topPos + imageHeight - 21,
                    0xff52646b);
    }

    private List<Line> details(VatSpecimenSnapshot s, int width) {
        var result = new ArrayList<Line>();
        java.util.function.Consumer<String> text = value -> {
            for (var line : font.split(Component.literal(value), width)) {
                var plain = new StringBuilder();
                line.accept((i, style, codepoint) -> {
                    plain.appendCodePoint(codepoint);
                    return true;
                });
                result.add(new Line(plain.toString(), -1, ""));
            }
        };
        text.accept(s.name());
        text.accept(s.species() + " / " + (s.adult() ? "Adult" : "Juvenile"));
        text.accept("Head: " + BlockPos.of(s.head()).toShortString());
        text.accept(s.status());
        text.accept("GROWTH");
        var growth = s.processes().stream()
                .filter(p -> p.id().equals(PassiveProcessCompiler.GROWTH) && p.duration() > 0)
                .findFirst();
        if (s.adult()) text.accept("Mature / 100%");
        else if (growth.isPresent()) {
            var p = growth.get();
            result.add(new Line("Growth: " + Math.round(p.fraction() * 100) + "%", p.fraction(), ""));
            text.accept("Maturity: " + (p.remainingTicks() < 0 ? "Waiting / paused" : "~" + time(p.remainingTicks())));
            text.accept("Growth rate: " + number(p.rate()) + "×");
        } else text.accept("Growth data unavailable");
        text.accept("BIOLOGICAL CONDITIONS");
        text.accept("Happiness: " + Math.round(s.happiness() * 100) + "% / Yield: " + number(s.yield()) + "×");
        text.accept("Culture frequency: " + number(s.frequency()) + "×");
        text.accept("Culture before exposure: " + number(s.culture()) + "×");
        text.accept("Last feed source: " + s.feedSource());
        text.accept(s.feeding());
        text.accept("PRODUCTION");
        for (var p : s.processes()) {
            if (p.id().equals(PassiveProcessCompiler.GROWTH)) continue;
            text.accept(p.status());
            if (p.duration() > 0) {
                result.add(new Line(
                        VatSpecimenSnapshot.label(p.phase()) + " / " + Math.round(p.fraction() * 100) + "%",
                        p.fraction(),
                        ""));
                if (p.remainingTicks() >= 0) text.accept("Next action: ~" + time(p.remainingTicks()));
            }
        }
        if (s.processes().isEmpty()) text.accept("Process data unavailable");
        text.accept("ASSIGNED ACTORS");
        for (var a : s.actors()) {
            text.accept(a.type() + " / " + a.operation());
            text.accept(BlockPos.of(a.position()).toShortString() + " / " + a.status());
            if (!a.item().isEmpty()) result.add(new Line(a.itemName(), -1, a.item()));
            if (a.cooldownTicks() > 0) text.accept("Actor cooldown: " + time(a.cooldownTicks()) + " (sampled)");
        }
        if (s.actors().isEmpty()) text.accept("No compiled assignments");
        return result;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        var view = menu.view();
        g.drawString(font, fit(title.getString(), imageWidth - 176), 9, 12, TEXT, false);
        g.drawString(
                font,
                fit(
                        (view.formed() ? "Formed" : "Unformed") + " / "
                                + (!view.enabled()
                                        ? "Operator paused"
                                        : view.inhibited() ? "Redstone inhibited" : "Enabled")
                                + " / " + view.specimens().size() + " specimens",
                        imageWidth - (diagnostics ? 100 : 18)),
                9,
                36,
                MUTED,
                false);
        if (diagnostics) {
            String text = menu.page(page);
            if (!text.equals(cachedDiagnostic) || cachedWidth != imageWidth) {
                cachedDiagnostic = text;
                cachedWidth = imageWidth;
                var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
                for (var line : text.split("\n")) wrapped.addAll(font.split(Component.literal(line), imageWidth - 24));
                cachedDiagnosticLines = List.copyOf(wrapped);
            }
            var lines = cachedDiagnosticLines;
            diagnosticHeight = lines.size() * 11;
            diagnosticScroll = Math.clamp(diagnosticScroll, 0, Math.max(0, diagnosticHeight - bodyHeight()));
            g.enableScissor(leftPos + 6, topPos + 54, leftPos + imageWidth - 6, topPos + imageHeight - 23);
            for (int i = diagnosticScroll / 11;
                    i < Math.min(lines.size(), (diagnosticScroll + bodyHeight()) / 11 + 1);
                    i++) g.drawString(font, lines.get(i), 12, 57 + i * 11 - diagnosticScroll, TEXT, false);
            g.disableScissor();
            scrollbar(g, imageWidth - 8, 55, bodyHeight(), diagnosticScroll, diagnosticHeight, bodyHeight());
        } else {
            listScroll = Math.clamp(listScroll, 0, Math.max(0, view.specimens().size() - rows()));
            for (int i = listScroll; i < Math.min(view.specimens().size(), listScroll + rows()); i++) {
                var s = view.specimens().get(i);
                int y = 55 + (i - listScroll) * 36;
                if (s.id().equals(menu.selectedId())) g.fill(7, y, listWidth + 5, y + 34, 0xff344a53);
                g.drawString(font, fit(s.name(), listWidth - 12), 11, y + 3, TEXT, false);
                g.drawString(font, s.adult() ? "Adult" : "Juvenile", 11, y + 13, MUTED, false);
                g.drawString(
                        font,
                        fit(s.status(), listWidth - 12),
                        11,
                        y + 23,
                        s.status().equals("Operational") || s.status().equals("Growing") ? ACCENT : 0xffdab279,
                        false);
                double fraction = s.adult()
                        ? 1
                        : s.processes().stream()
                                .filter(p -> p.id().equals(PassiveProcessCompiler.GROWTH))
                                .mapToDouble(VatSpecimenSnapshot.Process::fraction)
                                .findFirst()
                                .orElse(0);
                bar(g, 10, y + 33, listWidth - 14, fraction);
            }
            scrollbar(
                    g,
                    listWidth + 3,
                    55,
                    rows() * 36,
                    listScroll,
                    view.specimens().size(),
                    rows());
            var selected = menu.selected();
            int x = listWidth + 16, available = imageWidth - x - 12;
            if (selected == null) {
                g.drawString(
                        font,
                        fit(view.specimens().isEmpty() ? "No simulacra installed" : "Specimen unavailable", available),
                        x,
                        62,
                        TEXT,
                        false);
                g.drawString(font, fit("Select or install a specimen.", available), x, 76, MUTED, false);
            } else {
                if (cachedSpecimen != selected || cachedWidth != available) {
                    if (cachedSpecimen == null || !cachedSpecimen.id().equals(selected.id())) detailScroll = 0;
                    cachedSpecimen = selected;
                    cachedWidth = available;
                    cachedDetails = List.copyOf(details(selected, available));
                }
                var lines = cachedDetails;
                detailHeight = lines.stream()
                                .mapToInt(l -> !l.item.isEmpty() ? 20 : l.progress >= 0 ? 18 : 12)
                                .sum()
                        + PREVIEW_HEIGHT
                        + 6;
                detailScroll = Math.clamp(detailScroll, 0, Math.max(0, detailHeight - bodyHeight() + 8));
                g.enableScissor(leftPos + x, topPos + 55, leftPos + imageWidth - 8, topPos + imageHeight - 23);
                int y = 58 - detailScroll;
                renderPreview(g, x, y, available);
                y += PREVIEW_HEIGHT + 6;
                for (var line : lines) {
                    int size = !line.item.isEmpty() ? 20 : line.progress >= 0 ? 18 : 12;
                    if (y + size >= 55 && y < imageHeight - 23) {
                        if (!line.item.isEmpty()) {
                            var id = ResourceLocation.tryParse(line.item);
                            if (id != null) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)), x, y - 2);
                        }
                        g.drawString(
                                font,
                                fit(line.text, available - (line.item.isEmpty() ? 0 : 20)),
                                x + (line.item.isEmpty() ? 0 : 20),
                                y,
                                TEXT,
                                false);
                        if (line.progress >= 0) bar(g, x, y + 11, available, line.progress);
                    }
                    y += size;
                }
                g.disableScissor();
                scrollbar(g, imageWidth - 8, 55, bodyHeight(), detailScroll, detailHeight + 8, bodyHeight());
            }
        }
        g.drawString(
                font,
                diagnostics
                        ? PAGES[page] + " / scroll to inspect"
                        : "Scroll each pane / " + (view.specimens().isEmpty() ? "0" : listScroll + 1) + "–"
                                + Math.min(view.specimens().size(), listScroll + rows()) + " of "
                                + view.specimens().size(),
                9,
                imageHeight - 14,
                MUTED,
                false);
    }

    private void renderPreview(GuiGraphics g, int x, int y, int width) {
        if (y + PREVIEW_HEIGHT < 55 || y >= imageHeight - 23) return;
        g.fill(x, y, x + width, y + PREVIEW_HEIGHT, 0xff52646b);
        g.fill(x + 1, y + 1, x + width - 1, y + PREVIEW_HEIGHT - 1, 0xff172127);
        g.enableScissor(leftPos + x + 1, topPos + y + 1, leftPos + x + width - 1, topPos + y + PREVIEW_HEIGHT - 1);
        boolean available = false;
        try {
            var entity = preview.update(menu.selected(), minecraft.level);
            if (entity != null) {
                VatSpecimenPreviewRenderer.render(g, entity, x, y, width, PREVIEW_HEIGHT);
                available = true;
            }
        } catch (Exception error) {
            preview.failed(error);
        } finally {
            g.disableScissor();
        }
        if (!available) g.drawString(font, fit("Preview unavailable", width - 12), x + 6, y + 38, MUTED, false);
    }

    @Override
    public void removed() {
        preview.clear();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!diagnostics
                && button == 0
                && x >= leftPos + 6
                && x < leftPos + listWidth + 6
                && y >= topPos + 55
                && y < topPos + 55 + rows() * 36) {
            int index = listScroll + (int) (y - topPos - 55) / 36;
            if (index < menu.view().specimens().size()) {
                menu.select(menu.view().specimens().get(index).id());
                detailScroll = 0;
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int direction = (int) Math.signum(vertical);
        if (diagnostics)
            diagnosticScroll =
                    Math.clamp(diagnosticScroll - direction * 33, 0, Math.max(0, diagnosticHeight - bodyHeight()));
        else if (x < leftPos + listWidth + 8)
            listScroll = Math.clamp(
                    listScroll - direction * 2,
                    0,
                    Math.max(0, menu.view().specimens().size() - rows()));
        else detailScroll = Math.clamp(detailScroll - direction * 36, 0, Math.max(0, detailHeight - bodyHeight() + 8));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        preview.update(menu.selected(), minecraft.level);
        activation.setMessage(Component.literal(menu.active() ? "Disable" : "Enable"));
        diagnosticPage.visible = diagnostics;
        super.render(g, mouseX, mouseY, tick);
    }
}
