package net.impactvector.mobvats.gamecontent.journal.client;

import static net.impactvector.mobvats.gamecontent.journal.JournalData.*;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.journal.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;

/** Render-thread resource owner. GPU cache: 16 pages; decoded CPU cache: 48 images. */
public final class JournalResources implements ResourceManagerReloadListener {
    public static final JournalResources INSTANCE = new JournalResources();
    private Map<String, Profile> profiles = Map.of();
    private Map<String, Asset> assets = Map.of();
    private Book book;
    private long revision;
    private final Set<String> warned = new HashSet<>();
    private final JournalCache<String, JournalPageCompositor.Pixels> images = new JournalCache<>(48, p -> {});
    private final JournalCache<Key, ResourceLocation> composites = new JournalCache<>(
            16, id -> Minecraft.getInstance().getTextureManager().release(id));

    public record Key(
            Page page,
            long seed,
            long offset,
            String profile,
            JournalPageCompositor.Layer layer,
            Set<String> disabled) {
        public Key {
            disabled = Set.copyOf(disabled);
        }
    }

    public Book book() {
        if (book == null) onResourceManagerReload(Minecraft.getInstance().getResourceManager());
        if (book == null) throw new IllegalStateException("Journal resources failed; see log");
        return book;
    }

    public long revision() {
        return revision;
    }

    public List<String> profileIds() {
        return profiles.keySet().stream().sorted().toList();
    }

    public void closeTextures() {
        composites.close();
        images.close();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        closeTextures();
        warned.clear();
        revision++;
        try {
            var rawProfiles = readDirectory(manager, "journal/profiles");
            var newProfiles = new TreeMap<String, Profile>();
            for (String id : rawProfiles.keySet())
                newProfiles.put(
                        id, JournalData.profile(id, JournalData.resolveProfile(id, rawProfiles, new HashSet<>())));
            var newAssets = new TreeMap<String, Asset>();
            readDirectory(manager, "journal/texture_library")
                    .forEach((id, json) -> newAssets.put(id, JournalData.asset(id, json)));
            for (var profile : newProfiles.values())
                for (String paper : profile.paper().keySet())
                    if (!newAssets.containsKey(paper)
                            || !newAssets.get(paper).category().equals("paper"))
                        throw new IllegalArgumentException("Missing paper asset: " + paper);
            var entries = readDirectory(manager, "journal/entries");
            var root = read(manager, ResourceLocation.parse("mobvats:journal/book.json"));
            var ordered = new JsonArray();
            for (var id : root.getAsJsonArray("entries")) {
                var entry = entries.get(id.getAsString());
                if (entry == null) throw new IllegalArgumentException("Missing entry: " + id);
                ordered.add(entry);
            }
            root.add("entries", ordered);
            var newBook = JournalData.book(root, newProfiles, newAssets);
            for (var page : newBook.pages()) {
                JournalDecorationGenerator.effective(page, newProfiles);
                if (!PageRendererRegistry.supports(page.type()))
                    throw new IllegalArgumentException("Unknown page type: " + page.type());
            }
            profiles = Collections.unmodifiableMap(newProfiles);
            assets = Collections.unmodifiableMap(newAssets);
            book = newBook;
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Journal reload rejected; retaining last valid model", e);
        }
    }

    private static Map<String, JsonObject> readDirectory(ResourceManager manager, String directory) throws IOException {
        var result = new TreeMap<String, JsonObject>();
        for (var id : manager
                .listResources(
                        directory,
                        p -> p.getNamespace().equals("mobvats") && p.getPath().endsWith(".json"))
                .keySet()
                .stream()
                .sorted()
                .toList()) {
            String key =
                    id.getPath().substring(directory.length() + 1, id.getPath().length() - 5);
            result.put(key, read(manager, id));
        }
        return result;
    }

    private static JsonObject read(ResourceManager manager, ResourceLocation id) throws IOException {
        try (var reader = manager.getResourceOrThrow(id).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public ResourceLocation prepare(Key key) {
        return composites.get(key, k -> {
            var p = k.page();
            if (!k.profile().isEmpty())
                p = new Page(
                        p.entry(),
                        p.anchor(),
                        p.leaf(),
                        p.verso(),
                        k.profile(),
                        p.seedOffset(),
                        p.type(),
                        p.title(),
                        p.text(),
                        p.recipe(),
                        p.damage(),
                        p.authored());
            var previewBook = book();
            if (!k.profile().isEmpty()) {
                var pages = new ArrayList<Page>();
                for (var old : previewBook.pages())
                    pages.add(new Page(
                            old.entry(),
                            old.anchor(),
                            old.leaf(),
                            old.verso(),
                            k.profile(),
                            old.seedOffset(),
                            old.type(),
                            old.title(),
                            old.text(),
                            old.recipe(),
                            old.damage(),
                            old.authored()));
                previewBook = new Book(k.seed(), List.copyOf(pages), previewBook.leaves(), previewBook.events());
            }
            var layout = JournalDecorationGenerator.generate(
                    previewBook, p, profiles, assets, k.seed(), k.offset(), k.disabled());
            var pixels = JournalPageCompositor.compose(layout, assets, this::pixels, k.layer());
            var image = new NativeImage(pixels.width(), pixels.height(), false);
            DynamicTexture texture = null;
            try {
                for (int y = 0; y < pixels.height(); y++)
                    for (int x = 0; x < pixels.width(); x++) {
                        int argb = pixels.argb()[y * pixels.width() + x];
                        image.setPixelRGBA(x, y, (argb & 0xff00ff00) | ((argb >> 16) & 255) | ((argb & 255) << 16));
                    }
                texture = new DynamicTexture(image);
                return Minecraft.getInstance().getTextureManager().register("mobvats_journal", texture);
            } catch (RuntimeException e) {
                if (texture != null) texture.close();
                else image.close();
                throw e;
            }
        });
    }

    private JournalPageCompositor.Pixels pixels(String texture) {
        return images.get(texture, id -> {
            try (var stream = Minecraft.getInstance()
                            .getResourceManager()
                            .getResourceOrThrow(ResourceLocation.parse(id))
                            .open();
                    var image = NativeImage.read(stream)) {
                if (image.getWidth() > 1024 || image.getHeight() > 1024)
                    throw new IOException("Journal image exceeds 1024 pixels");
                int[] argb = new int[image.getWidth() * image.getHeight()];
                for (int y = 0; y < image.getHeight(); y++)
                    for (int x = 0; x < image.getWidth(); x++) {
                        int rgba = image.getPixelRGBA(x, y);
                        argb[y * image.getWidth() + x] =
                                (rgba & 0xff00ff00) | ((rgba >> 16) & 255) | ((rgba & 255) << 16);
                    }
                return new JournalPageCompositor.Pixels(image.getWidth(), image.getHeight(), argb);
            } catch (Exception e) {
                if (warned.add(id))
                    com.mojang.logging.LogUtils.getLogger()
                            .warn("Missing/invalid journal image {}; using safe fallback", id, e);
                // Missing decals/masks are transparent. Missing paper remains readable.
                boolean paper = assets.values().stream()
                        .anyMatch(a -> a.texture().equals(id) && a.category().equals("paper"));
                return JournalPageCompositor.missingTexture(paper);
            }
        });
    }
}
