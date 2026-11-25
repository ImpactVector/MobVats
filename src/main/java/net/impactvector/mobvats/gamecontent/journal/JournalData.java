package net.impactvector.mobvats.gamecontent.journal;

import com.google.gson.*;
import java.util.*;

/** Resource-only model; no client, world, registry or GPU dependencies. */
public final class JournalData {
    public record Rule(
            boolean enabled,
            double probability,
            int min,
            int max,
            double minScale,
            double maxScale,
            double minOpacity,
            double maxOpacity,
            List<Double> rotations,
            boolean mirror,
            String placement,
            double severity) {}

    public record Profile(String id, Map<String, Double> paper, Map<String, Rule> damage) {}

    public record Asset(
            String id,
            String texture,
            String category,
            double weight,
            String placement,
            double minScale,
            double maxScale,
            double minOpacity,
            double maxOpacity,
            List<Double> rotations,
            boolean mirror,
            double minimumSeverity) {}

    public record Page(
            String entry,
            String anchor,
            String leaf,
            boolean verso,
            String profile,
            long seedOffset,
            String type,
            JsonElement title,
            JsonElement text,
            String recipe,
            JsonObject damage,
            List<Decoration> authored) {}

    public record Decoration(
            String asset,
            double x,
            double y,
            double scale,
            double rotation,
            double opacity,
            boolean mirror,
            boolean physical) {}

    public record DamageEvent(
            String id,
            String originLeaf,
            String category,
            String asset,
            double x,
            double y,
            double scale,
            double rotation,
            double intensity,
            int depth,
            double falloff) {
        public double intensityAt(int distance) {
            return distance < 0 || distance >= depth ? 0 : intensity * Math.pow(falloff, distance);
        }
    }

    public record Book(long seed, List<Page> pages, List<String> leaves, List<DamageEvent> events) {}

    private JournalData() {}

    public static JsonObject resolveProfile(String id, Map<String, JsonObject> raw, Set<String> visiting) {
        if (!visiting.add(id)) throw new IllegalArgumentException("Profile inheritance cycle: " + id);
        var value = raw.get(id);
        if (value == null) throw new IllegalArgumentException("Missing inherited profile: " + id);
        var result = value.has("extends")
                ? merge(resolveProfile(value.get("extends").getAsString(), raw, visiting), value)
                : value.deepCopy();
        visiting.remove(id);
        return result;
    }

    public static JsonObject merge(JsonObject parent, JsonObject child) {
        var result = parent.deepCopy();
        child.entrySet().forEach(e -> {
            if (e.getValue().isJsonObject()
                    && result.has(e.getKey())
                    && result.get(e.getKey()).isJsonObject())
                result.add(
                        e.getKey(),
                        merge(result.getAsJsonObject(e.getKey()), e.getValue().getAsJsonObject()));
            else result.add(e.getKey(), e.getValue().deepCopy());
        });
        return result;
    }

    public static Profile profile(String id, JsonObject json) {
        var papers = new TreeMap<String, Double>();
        json.getAsJsonObject("paper").entrySet().forEach(e -> {
            double weight = e.getValue().getAsDouble();
            if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Paper weight: " + id);
            papers.put(e.getKey(), weight);
        });
        if (papers.isEmpty()) throw new IllegalArgumentException("No paper: " + id);
        var damage = new TreeMap<String, Rule>();
        json.getAsJsonObject("damage")
                .entrySet()
                .forEach(e -> damage.put(e.getKey(), rule(e.getValue().getAsJsonObject())));
        return new Profile(id, Collections.unmodifiableMap(papers), Collections.unmodifiableMap(damage));
    }

    public static Rule rule(JsonObject j) {
        double probability = number(j, "probability", 1), severity = number(j, "severity", 0);
        int min = j.get("min").getAsInt(), max = j.get("max").getAsInt();
        double[] scale = range(j, "scale", 0.01, 8), opacity = range(j, "opacity", 0, 1);
        if (probability < 0
                || probability > 1
                || !Double.isFinite(probability)
                || severity < 0
                || severity > 1
                || !Double.isFinite(severity)
                || min < 0
                || max < min
                || max > 32) throw new IllegalArgumentException("Invalid damage rule: " + j);
        return new Rule(
                bool(j, "enabled", true),
                probability,
                min,
                max,
                scale[0],
                scale[1],
                opacity[0],
                opacity[1],
                rotations(j),
                bool(j, "mirror", true),
                placement(j),
                severity);
    }

    public static Asset asset(String id, JsonObject j) {
        double[] scale = range(j, "scale", 0.01, 8), opacity = range(j, "opacity", 0, 1);
        double weight = number(j, "weight", 1), severity = number(j, "minimum_severity", 0);
        String texture = j.get("texture").getAsString();
        if (!texture.matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png")
                || texture.contains("..")
                || !Double.isFinite(weight)
                || weight <= 0
                || severity < 0
                || severity > 1
                || !Double.isFinite(severity)) throw new IllegalArgumentException("Invalid asset: " + id);
        return new Asset(
                id,
                texture,
                j.get("category").getAsString(),
                weight,
                placement(j),
                scale[0],
                scale[1],
                opacity[0],
                opacity[1],
                rotations(j),
                bool(j, "mirror", true),
                severity);
    }

    public static Book book(JsonObject j, Map<String, Profile> profiles, Map<String, Asset> assets) {
        var pages = new ArrayList<Page>();
        var leaves = new ArrayList<String>();
        var anchors = new HashSet<String>();
        var surfaces = new HashSet<String>();
        for (var entryElement : j.getAsJsonArray("entries")) {
            var entry = entryElement.getAsJsonObject();
            String entryId = required(entry, "id");
            for (var element : entry.getAsJsonArray("pages")) {
                var p = element.getAsJsonObject();
                String anchor = required(p, "anchor"), leaf = required(p, "leaf");
                boolean verso = bool(p, "verso", false);
                if (!anchors.add(entryId + "/" + anchor))
                    throw new IllegalArgumentException("Duplicate anchor: " + anchor);
                if (!surfaces.add(leaf + "/" + verso))
                    throw new IllegalArgumentException("Duplicate leaf surface: " + leaf);
                if (!leaves.contains(leaf)) leaves.add(leaf);
                else if (!leaves.getLast().equals(leaf))
                    throw new IllegalArgumentException("Nonconsecutive leaf: " + leaf);
                if (!pages.isEmpty()
                        && pages.getLast().leaf().equals(leaf)
                        && pages.getLast().verso())
                    throw new IllegalArgumentException("Recto must precede verso: " + leaf);
                var appearance = p.has("appearance") ? p.getAsJsonObject("appearance") : new JsonObject();
                String profile =
                        appearance.has("profile") ? required(appearance, "profile") : required(entry, "profile");
                if (!profiles.containsKey(profile)) throw new IllegalArgumentException("Missing profile: " + profile);
                var authored = new ArrayList<Decoration>();
                if (appearance.has("decorations"))
                    for (var d : appearance.getAsJsonArray("decorations")) {
                        var a = d.getAsJsonObject();
                        String asset = required(a, "asset");
                        if (!assets.containsKey(asset)
                                || assets.get(asset).category().equals("masks"))
                            throw new IllegalArgumentException("Authored decoration must be visible: " + asset);
                        validatePlacement(a);
                        authored.add(new Decoration(
                                asset,
                                number(a, "x", 0.5),
                                number(a, "y", 0.5),
                                number(a, "scale", 1),
                                number(a, "rotation", 0),
                                number(a, "opacity", 1),
                                bool(a, "mirror", false),
                                false));
                    }
                String type = p.has("type") ? required(p, "type") : "text";
                pages.add(new Page(
                        entryId,
                        anchor,
                        leaf,
                        verso,
                        profile,
                        (long) number(appearance, "seed_offset", 0),
                        type,
                        p.get("title"),
                        p.get("text"),
                        p.has("recipe") ? required(p, "recipe") : "",
                        appearance.has("damage") ? appearance.getAsJsonObject("damage") : new JsonObject(),
                        List.copyOf(authored)));
            }
        }
        var events = new ArrayList<DamageEvent>();
        var eventIds = new HashSet<String>();
        if (j.has("events"))
            for (var e : j.getAsJsonArray("events")) {
                var a = e.getAsJsonObject();
                String id = required(a, "id"), origin = required(a, "origin_leaf"), asset = required(a, "asset");
                String category = required(a, "category");
                validatePlacement(a);
                int depth = a.get("depth").getAsInt();
                double intensity = number(a, "intensity", 1), falloff = number(a, "falloff", 0.7);
                if (!eventIds.add(id)
                        || !leaves.contains(origin)
                        || !assets.containsKey(asset)
                        || !assets.get(asset).category().equals(category)
                        || category.equals("masks")
                        || depth < 1
                        || intensity < 0
                        || intensity > 1
                        || falloff < 0
                        || falloff > 1) throw new IllegalArgumentException("Invalid damage event: " + id);
                events.add(new DamageEvent(
                        id,
                        origin,
                        category,
                        asset,
                        number(a, "x", 0.5),
                        number(a, "y", 0.5),
                        number(a, "scale", 1),
                        number(a, "rotation", 0),
                        intensity,
                        depth,
                        falloff));
            }
        events.sort(Comparator.comparing(DamageEvent::id));
        if (pages.isEmpty()) throw new IllegalArgumentException("Empty journal");
        return new Book(j.get("seed").getAsLong(), List.copyOf(pages), List.copyOf(leaves), List.copyOf(events));
    }

    private static void validatePlacement(JsonObject j) {
        double x = number(j, "x", 0.5),
                y = number(j, "y", 0.5),
                scale = number(j, "scale", 1),
                opacity = number(j, "opacity", 1);
        number(j, "rotation", 0);
        if (x < 0 || x > 1 || y < 0 || y > 1 || scale <= 0 || scale > 8 || opacity < 0 || opacity > 1)
            throw new IllegalArgumentException("Decoration placement: " + j);
    }

    public static String required(JsonObject j, String key) {
        String value = j.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("Empty " + key);
        return value;
    }

    public static double number(JsonObject j, String key, double fallback) {
        double result = j.has(key) ? j.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Nonfinite " + key);
        return result;
    }

    public static boolean bool(JsonObject j, String key, boolean fallback) {
        return j.has(key) ? j.get(key).getAsBoolean() : fallback;
    }

    private static double[] range(JsonObject j, String key, double min, double max) {
        var a = j.getAsJsonArray(key);
        if (a.size() != 2) throw new IllegalArgumentException("Range: " + key);
        double low = a.get(0).getAsDouble(), high = a.get(1).getAsDouble();
        if (!Double.isFinite(low) || !Double.isFinite(high) || low < min || high > max || high < low)
            throw new IllegalArgumentException("Range: " + key);
        return new double[] {low, high};
    }

    private static List<Double> rotations(JsonObject j) {
        if (!j.has("rotation")
                || j.get("rotation").isJsonPrimitive()
                        && j.get("rotation").getAsString().equals("free")) return List.of();
        var result = new ArrayList<Double>();
        for (var e : j.getAsJsonArray("rotation")) {
            double value = e.getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Rotation");
            result.add(value);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Empty rotation set");
        return List.copyOf(result);
    }

    private static String placement(JsonObject j) {
        String result = j.has("placement") ? j.get("placement").getAsString() : "anywhere";
        if (!Set.of("anywhere", "edge", "outside_content").contains(result))
            throw new IllegalArgumentException("Placement: " + result);
        return result;
    }
}
