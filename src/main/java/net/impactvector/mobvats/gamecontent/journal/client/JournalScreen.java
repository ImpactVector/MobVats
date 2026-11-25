// SPDX-FileCopyrightText: 2024 klikli-dev
// SPDX-FileCopyrightText: 2024 DaFuqs
// SPDX-License-Identifier: MIT
// Reduced adaptation of Modonomicon BookEntryDoublePageScreen / BookPaginatedScreen.
package net.impactvector.mobvats.gamecontent.journal.client;

import java.util.*;
import net.impactvector.mobvats.gamecontent.journal.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class JournalScreen extends Screen {
    public static final int BOOK_WIDTH = 272, BOOK_HEIGHT = 178, PAGE_WIDTH = 124, PAGE_HEIGHT = 155;
    private final JournalResources resources = JournalResources.INSTANCE;
    private final boolean preview;
    private final BookPageRenderer[] renderers = new BookPageRenderer[2];
    private final ResourceLocation[] textures = new ResourceLocation[2];
    private final Set<String> disabled = new HashSet<>();
    private final List<String> categories = List.of("blood", "chemical", "ink", "burns", "masks", "wear");
    private JournalData.Book book;
    private int openPagesIndex, bookLeft, bookTop;
    private long seed, offset, revision;
    private boolean seedInitialized;
    private String profile = "";
    private JournalPageCompositor.Layer layer = JournalPageCompositor.Layer.ALL;
    private Button previous, next;
    private ItemStack tooltip = ItemStack.EMPTY;

    public JournalScreen(boolean preview) {
        super(Component.literal("Research journal · prototype"));
        this.preview = preview;
    }

    @Override
    protected void init() {
        book = resources.book();
        if (!seedInitialized) {
            seed = book.seed();
            seedInitialized = true;
        }
        bookLeft = (width - BOOK_WIDTH) / 2;
        bookTop = (height - BOOK_HEIGHT) / 2 - 6;
        previous = addRenderableWidget(Button.builder(Component.literal("←"), b -> flipPage(true))
                .bounds(bookLeft + 6, bookTop + 174, 32, 18)
                .build());
        next = addRenderableWidget(Button.builder(Component.literal("→"), b -> flipPage(false))
                .bounds(bookLeft + 234, bookTop + 174, 32, 18)
                .build());
        beginDisplayPages();
    }

    private void flipPage(boolean left) {
        int proposed = openPagesIndex + (left ? -2 : 2);
        if (proposed >= 0 && proposed < book.pages().size()) {
            openPagesIndex = proposed;
            beginDisplayPages();
            minecraft
                    .getSoundManager()
                    .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1));
        }
    }

    private void endDisplayPages() {
        for (int i = 0; i < 2; i++) {
            if (renderers[i] != null) renderers[i].onEndDisplayPage();
            renderers[i] = null;
            textures[i] = null;
        }
    }

    private void beginDisplayPages() {
        endDisplayPages();
        revision = resources.revision();
        openPagesIndex = Math.min(openPagesIndex, (book.pages().size() - 1) & -2);
        for (int side = 0; side < 2; side++) {
            int index = openPagesIndex + side;
            if (index >= book.pages().size()) continue;
            var page = book.pages().get(index);
            textures[side] = resources.prepare(new JournalResources.Key(page, seed, offset, profile, layer, disabled));
            renderers[side] = PageRendererRegistry.create(page);
            renderers[side].onBeginDisplayPage(this);
        }
        previous.active = openPagesIndex > 0;
        next.active = openPagesIndex + 2 < book.pages().size();
    }

    @Override
    public void tick() {
        if (revision != resources.revision()) {
            book = resources.book();
            beginDisplayPages();
        }
    }

    public void tooltip(ItemStack stack) {
        tooltip = stack;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float ticks) {
        renderBackground(g, mx, my, ticks);
        tooltip = ItemStack.EMPTY;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(
                ResourceLocation.parse("mobvats:textures/gui/journal/binding/prototype.png"),
                bookLeft,
                bookTop,
                0,
                0,
                272,
                178,
                272,
                178);
        // Physical bounds are 128 x 166 at (8,6) and (137,6), not the 124 x 155 content bounds.
        for (int side = 0; side < 2; side++)
            if (textures[side] != null)
                g.blit(textures[side], bookLeft + (side == 0 ? 8 : 137), bookTop + 6, 0, 0, 128, 166, 128, 166);
        for (int side = 0; side < 2; side++)
            if (renderers[side] != null) {
                int x = bookLeft + (side == 0 ? 12 : 141), y = bookTop + 15;
                g.enableScissor(x + 2, y, x + 118, y + 146);
                renderers[side].render(g, x, y, mx, my);
                g.disableScissor();
            }
        for (var renderable : renderables) renderable.render(g, mx, my, ticks);
        g.drawCenteredString(
                font,
                (openPagesIndex + 1) + "–"
                        + Math.min(openPagesIndex + 2, book.pages().size()) + " / "
                        + book.pages().size(),
                width / 2,
                bookTop + 179,
                0xe4d5af);
        if (preview) {
            g.pose().pushPose();
            g.pose().scale(0.65f, 0.65f, 1);
            int x = (int) (bookLeft / 0.65f), y = (int) ((bookTop - 25) / 0.65f);
            g.drawString(font, "P profile  S seed (Shift −)  R reroll  L layer  1–6 categories", x, y, 0xffffff);
            g.drawString(
                    font,
                    "seed=" + seed + " offset=" + offset + " profile=" + (profile.isEmpty() ? "authored" : profile)
                            + " layer=" + layer,
                    x,
                    y + 10,
                    0xffffff);
            g.drawString(font, "disabled=" + disabled.stream().sorted().toList(), x, y + 20, 0xffffff);
            g.pose().popPose();
            for (int side = 0; side < 2 && openPagesIndex + side < book.pages().size(); side++) {
                var p = book.pages().get(openPagesIndex + side);
                int pageX = bookLeft + (side == 0 ? 8 : 137);
                if (mx >= pageX && mx < pageX + 128 && my >= bookTop + 155 && my < bookTop + 173)
                    g.renderTooltip(
                            font,
                            Component.literal(p.entry() + "/" + p.anchor() + " · leaf " + p.leaf() + " "
                                    + (p.verso() ? "verso" : "recto")),
                            mx,
                            my);
            }
        }
        if (!tooltip.isEmpty()) g.renderTooltip(font, tooltip, mx, my);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
            flipPage(true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            flipPage(false);
            return true;
        }
        if (preview) {
            boolean changed = true;
            if (key == GLFW.GLFW_KEY_P) {
                var ids = new ArrayList<>(resources.profileIds());
                ids.addFirst("");
                profile = ids.get((ids.indexOf(profile) + 1) % ids.size());
            } else if (key == GLFW.GLFW_KEY_S) seed += hasShiftDown() ? -1 : 1;
            else if (key == GLFW.GLFW_KEY_R) offset++;
            else if (key == GLFW.GLFW_KEY_L)
                layer = JournalPageCompositor.Layer.values()[
                        (layer.ordinal() + 1) % JournalPageCompositor.Layer.values().length];
            else if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_6) {
                String category = categories.get(key - GLFW.GLFW_KEY_1);
                if (!disabled.add(category)) disabled.remove(category);
            } else changed = false;
            if (changed) {
                beginDisplayPages();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void removed() {
        endDisplayPages();
        resources.closeTextures();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
