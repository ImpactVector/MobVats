// SPDX-FileCopyrightText: 2022 klikli-dev
// SPDX-License-Identifier: MIT
// Selectively adapted from Modonomicon BookPageRenderer and BookCraftingRecipePageRenderer.
package net.impactvector.mobvats.gamecontent.journal.client;

import java.util.*;
import net.impactvector.mobvats.gamecontent.journal.JournalData.Page;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Page-type lifecycle remains independent of paper decoration. Coordinates are content-local. */
public abstract class BookPageRenderer {
    protected final Page page;
    protected JournalScreen parent;
    protected List<net.minecraft.util.FormattedCharSequence> title = List.of(), text = List.of();

    protected BookPageRenderer(Page page) {
        this.page = page;
    }

    public void onBeginDisplayPage(JournalScreen screen) {
        parent = screen;
        var mc = Minecraft.getInstance();
        title = mc.font.split(component(page.title()), 116);
        text = mc.font.split(component(page.text()), 116);
        if (title.size() > 3 || text.size() > 12)
            throw new IllegalArgumentException("Journal text overflow: " + page.anchor());
    }

    public void onEndDisplayPage() {
        parent = null;
    }

    public void render(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int ty = y;
        for (var line : title) {
            g.drawString(font, line, x + 2, ty, 0x382b24, false);
            ty += 9;
        }
        ty += 5;
        for (var line : text) {
            g.drawString(font, line, x + 2, ty, 0x302820, false);
            ty += 9;
        }
    }

    private static Component component(com.google.gson.JsonElement value) {
        if (value == null) return Component.empty();
        if (value.isJsonPrimitive()) return Component.literal(value.getAsString());
        return Component.Serializer.fromJson(
                value, Minecraft.getInstance().level.registryAccess());
    }

    public static final class Text extends BookPageRenderer {
        public Text(Page p) {
            super(p);
        }
    }

    public static final class Crafting extends BookPageRenderer {
        private record Slot(int x, int y, ItemStack stack) {}

        private List<Slot> slots = List.of();
        private boolean missing;

        public Crafting(Page p) {
            super(p);
        }

        @Override
        public void onBeginDisplayPage(JournalScreen screen) {
            super.onBeginDisplayPage(screen);
            var mc = Minecraft.getInstance();
            var holder = mc.level.getRecipeManager().byKey(ResourceLocation.parse(page.recipe()));
            missing = holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe);
            if (missing) return;
            Recipe<?> recipe = holder.get().value();
            int wrap = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            var prepared = new ArrayList<Slot>();
            var ingredients = recipe.getIngredients();
            for (int i = 0; i < ingredients.size(); i++) {
                var alternatives = ingredients.get(i).getItems();
                if (alternatives.length > 0)
                    prepared.add(new Slot(4 + (i % wrap) * 19, 69 + (i / wrap) * 19, alternatives[0]));
            }
            prepared.add(new Slot(91, 88, recipe.getResultItem(mc.level.registryAccess())));
            slots = List.copyOf(prepared);
        }

        @Override
        public void onEndDisplayPage() {
            slots = List.of();
            super.onEndDisplayPage();
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int mx, int my) {
            super.render(g, x, y, mx, my);
            if (missing) {
                g.drawString(Minecraft.getInstance().font, "Recipe unavailable", x + 2, y + 78, 0x802020, false);
                return;
            }
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 3; col++)
                    g.fill(x + 3 + col * 19, y + 68 + row * 19, x + 21 + col * 19, y + 86 + row * 19, 0x33706040);
            g.drawString(Minecraft.getInstance().font, "→", x + 69, y + 92, 0x302820, false);
            for (var slot : slots) {
                g.renderItem(slot.stack(), x + slot.x(), y + slot.y());
                g.renderItemDecorations(Minecraft.getInstance().font, slot.stack(), x + slot.x(), y + slot.y());
                if (mx >= x + slot.x() && mx < x + slot.x() + 16 && my >= y + slot.y() && my < y + slot.y() + 16)
                    parent.tooltip(slot.stack());
            }
        }
    }
}
