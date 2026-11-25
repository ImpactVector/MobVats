package net.impactvector.mobvats.journal;

import static net.impactvector.mobvats.gamecontent.journal.JournalData.*;
import static net.impactvector.mobvats.gamecontent.journal.JournalPageCompositor.*;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.impactvector.mobvats.gamecontent.journal.*;

/** Headless CPU tests against shipped resources; never bootstraps Minecraft or OpenGL. */
public final class JournalTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/mobvats");
    private static int assertions;

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        require(rejected, message);
    }

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static Map<String, JsonObject> directory(String path) throws Exception {
        var result = new TreeMap<String, JsonObject>();
        try (var files = Files.list(ROOT.resolve(path))) {
            for (var file : files.sorted().toList())
                result.put(file.getFileName().toString().replace(".json", ""), read(file));
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        var raw = directory("journal/profiles");
        var profiles = new TreeMap<String, Profile>();
        raw.forEach((id, json) ->
                profiles.put(id, JournalData.profile(id, JournalData.resolveProfile(id, raw, new HashSet<>()))));
        var assets = new TreeMap<String, Asset>();
        directory("journal/texture_library").forEach((id, json) -> assets.put(id, JournalData.asset(id, json)));
        var json = read(ROOT.resolve("journal/book.json"));
        var entries = directory("journal/entries");
        var ordered = new JsonArray();
        for (var id : json.getAsJsonArray("entries")) ordered.add(entries.get(id.getAsString()));
        json.add("entries", ordered);
        Book book = JournalData.book(json, profiles, assets);
        require(book.pages().size() == 8, "Eight pages");
        require(assets.size() == 20, "Twenty modular assets");
        require(
                new JournalDecorationGenerator.RandomStream(0).nextLong() == 0xe220a8397b1dcdafL,
                "SplitMix64 golden vector");
        require(
                JournalDecorationGenerator.hash(1, "trial", "anchor", "7") == 0x1709c37a2d93a922L,
                "Length-prefixed FNV-1a golden vector");
        require(
                JournalDecorationGenerator.hash(1, "ab", "c") != JournalDecorationGenerator.hash(1, "a", "bc"),
                "Length-delimited hash");
        require(profiles.get("experimental").damage().get("blood").probability() == 0, "Inherited rule");
        require(profiles.get("experimental").damage().get("ink").max() == 2, "Inherited rule patch");
        rejects(
                () -> JournalData.resolveProfile(
                        "cycle",
                        Map.of(
                                "cycle",
                                JsonParser.parseString("{\"extends\":\"cycle\"}")
                                        .getAsJsonObject()),
                        new HashSet<>()),
                "Inheritance cycle rejected");
        var malformed = json.deepCopy();
        var first = malformed.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("pages");
        first.get(1).getAsJsonObject().addProperty("anchor", "scope");
        rejects(() -> JournalData.book(malformed, profiles, assets), "Duplicate anchor rejected");
        first.get(1).getAsJsonObject().addProperty("anchor", "");
        rejects(() -> JournalData.book(malformed, profiles, assets), "Empty anchor rejected");
        var badAsset = directory("journal/texture_library").get("blood_1").deepCopy();
        badAsset.addProperty("weight", -1);
        rejects(() -> JournalData.asset("bad", badAsset), "Invalid asset metadata rejected");
        var badRule = raw.get("pristine")
                .getAsJsonObject("damage")
                .getAsJsonObject("blood")
                .deepCopy();
        badRule.addProperty("probability", 2);
        rejects(() -> JournalData.rule(badRule), "Invalid profile probability rejected");
        var p = book.pages().get(6);
        var effective = JournalDecorationGenerator.effective(p, profiles);
        require(!effective.damage().get("burns").enabled(), "Per-page override");
        require(effective.damage().get("blood").max() == 7, "Override retains other rules");
        require(p.authored().size() == 1, "Authored decoration preserved");
        var a = JournalDecorationGenerator.generate(book, p, profiles, assets, book.seed(), 0, Set.of());
        var b = JournalDecorationGenerator.generate(book, p, profiles, assets, book.seed(), 0, Set.of());
        require(a.equals(b), "Deterministic layout");
        require(
                !a.equals(JournalDecorationGenerator.generate(book, p, profiles, assets, book.seed(), 1, Set.of())),
                "Explicit reroll changes local appearance");
        var withoutInk = JournalDecorationGenerator.generate(book, p, profiles, assets, book.seed(), 0, Set.of("ink"));
        require(
                a.surface().stream()
                        .filter(d -> !assets.get(d.asset()).category().equals("ink"))
                        .toList()
                        .equals(withoutInk.surface()),
                "Category streams independent");
        var insertedPages = new ArrayList<>(book.pages());
        insertedPages.addFirst(new Page(
                "unrelated",
                "inserted",
                "leaf_new",
                true,
                "pristine",
                0,
                "text",
                null,
                null,
                "",
                new JsonObject(),
                List.of()));
        var insertedLeaves = new ArrayList<>(book.leaves());
        insertedLeaves.addFirst("leaf_new");
        var inserted = new Book(book.seed(), List.copyOf(insertedPages), List.copyOf(insertedLeaves), book.events());
        require(
                a.equals(JournalDecorationGenerator.generate(inserted, p, profiles, assets, book.seed(), 0, Set.of())),
                "Insertion does not reroll existing page");
        var event = book.events().getFirst();
        require(
                event.intensityAt(0) > event.intensityAt(1)
                        && event.intensityAt(1) > 0
                        && event.intensityAt(2) == 0
                        && event.intensityAt(-1) == 0,
                "Spill falloff and bounds");
        var recto = book.pages().get(5);
        var verso = book.pages().get(6);
        var ra = JournalDecorationGenerator.generate(book, recto, profiles, assets, book.seed(), 0, Set.of());
        var va = JournalDecorationGenerator.generate(book, verso, profiles, assets, book.seed(), 0, Set.of());
        require(ra.physical().size() == va.physical().size(), "Leaf physical count");
        for (int i = 0; i < ra.physical().size(); i++) {
            var r = ra.physical().get(i);
            var v = va.physical().get(i);
            require(
                    r.asset().equals(v.asset())
                            && Math.abs(r.x() + v.x() - 1) < 1e-12
                            && r.y() == v.y()
                            && r.rotation() == -v.rotation()
                            && r.mirror() != v.mirror(),
                    "Reflected physical transform");
        }
        var decoded = new HashMap<String, Pixels>();
        for (var asset : assets.values()) {
            var file = ROOT.resolve(asset.texture().substring("mobvats:".length()));
            var image = ImageIO.read(file.toFile());
            require(image != null && image.getColorModel().hasAlpha(), "Valid RGBA resource: " + asset.id());
            decoded.put(
                    asset.texture(),
                    new Pixels(
                            image.getWidth(),
                            image.getHeight(),
                            image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())));
        }
        var r = compose(ra, assets, decoded::get, Layer.PHYSICAL);
        var v = compose(va, assets, decoded::get, Layer.PHYSICAL);
        int absent = 0;
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int alpha = r.argb()[y * WIDTH + x] >>> 24;
                require(alpha == (v.argb()[y * WIDTH + (WIDTH - 1 - x)] >>> 24), "Reflected alpha mask");
                if (alpha == 0) absent++;
                if (protectedContent(x, y)) require(alpha == 255, "Protected content intact");
            }
        require(absent > 0, "Physical tears remove paper");
        var all = compose(ra, assets, decoded::get, Layer.ALL);
        var repeated = compose(ra, assets, decoded::get, Layer.ALL);
        require(Arrays.equals(all.argb(), repeated.argb()), "Deterministic composite pixels");
        var transparentPaper = compose(
                ra,
                assets,
                id -> id.equals(assets.get(ra.paper()).texture()) ? new Pixels(1, 1, new int[] {0}) : decoded.get(id),
                Layer.ALL);
        require(
                Arrays.stream(transparentPaper.argb()).allMatch(pixel -> (pixel >>> 24) == 0),
                "Decals cannot restore transparent base paper");
        for (int i = 0; i < all.argb().length; i++)
            if ((r.argb()[i] >>> 24) == 0) require((all.argb()[i] >>> 24) == 0, "Decals clipped by final mask");
        require(
                missingTexture(false).argb()[0] == 0 && (missingTexture(true).argb()[0] >>> 24) == 255,
                "Safe missing texture behavior");
        var disposed = new ArrayList<Integer>();
        var cache = new JournalCache<String, Integer>(2, disposed::add);
        require(
                cache.get("a", k -> 1)
                        == cache.get("a", k -> {
                            throw new AssertionError("Cache miss");
                        }),
                "Composite reused");
        cache.get("b", k -> 2);
        cache.get("c", k -> 3);
        require(cache.size() == 2 && disposed.equals(List.of(1)), "Bounded cache eviction");
        cache.close();
        cache.close();
        require(cache.size() == 0 && disposed.equals(List.of(1, 2, 3)), "Reload disposal exactly once");
        cache.get("a", k -> 4);
        cache.close();
        require(disposed.getLast() == 4, "Reload forces regeneration");

        // Representative seed survey: all four profiles, 32 seeds each, export a CPU contact sheet.
        var sheet = new BufferedImage(8 * 128, 16 * 166, BufferedImage.TYPE_INT_ARGB);
        int row = 0;
        for (String profile : List.of("pristine", "experimental", "obsessive", "deranged")) {
            var layouts = new HashSet<JournalDecorationGenerator.Layout>();
            for (int seed = 0; seed < 32; seed++) {
                var preview = new Page(
                        p.entry(),
                        p.anchor(),
                        p.leaf(),
                        p.verso(),
                        profile,
                        0,
                        "text",
                        null,
                        null,
                        "",
                        new JsonObject(),
                        List.of());
                var previewBook = new Book(seed, List.of(preview), List.of(p.leaf()), List.of());
                var layout =
                        JournalDecorationGenerator.generate(previewBook, preview, profiles, assets, seed, 0, Set.of());
                layouts.add(layout);
                var result = compose(layout, assets, decoded::get, Layer.ALL);
                for (int y = 0; y < 166; y++)
                    for (int x = 0; x < 128; x++)
                        sheet.setRGB(
                                (seed % 8) * 128 + x,
                                (row + seed / 8) * 166 + y,
                                result.argb()[(y * 2) * WIDTH + x * 2]);
            }
            require(layouts.size() >= (profile.equals("pristine") ? 3 : 16), "Meaningful variation: " + profile);
            row += 4;
        }
        Path output = Path.of("build/reports/journal/seed-survey.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "png", output.toFile());
        System.out.println("Journal CPU checks passed: " + assertions
                + " assertions; 128 seed/profile composites. Survey: " + output);
    }
}
