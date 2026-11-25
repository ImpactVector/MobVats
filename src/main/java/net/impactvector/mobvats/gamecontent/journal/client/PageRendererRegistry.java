// SPDX-FileCopyrightText: 2022 klikli-dev
// SPDX-License-Identifier: MIT
// Reduced adaptation of Modonomicon's renderer factory registry.
package net.impactvector.mobvats.gamecontent.journal.client;

import java.util.*;
import java.util.function.Function;
import net.impactvector.mobvats.gamecontent.journal.JournalData.Page;

public final class PageRendererRegistry {
    private static final Map<String, Function<Page, BookPageRenderer>> RENDERERS = new HashMap<>();

    static {
        register("text", BookPageRenderer.Text::new);
        register("crafting", BookPageRenderer.Crafting::new);
    }

    public static void register(String type, Function<Page, BookPageRenderer> factory) {
        if (RENDERERS.putIfAbsent(type, factory) != null)
            throw new IllegalArgumentException("Duplicate renderer: " + type);
    }

    public static BookPageRenderer create(Page page) {
        var factory = RENDERERS.get(page.type());
        if (factory == null) throw new IllegalArgumentException("Unknown renderer: " + page.type());
        return factory.apply(page);
    }

    public static boolean supports(String type) {
        return RENDERERS.containsKey(type);
    }
}
