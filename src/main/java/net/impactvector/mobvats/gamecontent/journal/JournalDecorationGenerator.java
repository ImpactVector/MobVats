package net.impactvector.mobvats.gamecontent.journal;

import static net.impactvector.mobvats.gamecontent.journal.JournalData.*;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Version 1: length-delimited UTF-8 FNV-1a-64, then SplitMix64. Overflow is intentional. */
public final class JournalDecorationGenerator {
    public record Layout(
            String paper, List<Decoration> physical, List<Decoration> surface, List<Decoration> authored) {}

    public static long hash(long seed, String... parts) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < 8; i++) {
            h ^= (seed >>> (i * 8)) & 255;
            h *= 0x100000001b3L;
        }
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 4; i++) {
                h ^= (bytes.length >>> (i * 8)) & 255;
                h *= 0x100000001b3L;
            }
            for (byte b : bytes) {
                h ^= b & 255;
                h *= 0x100000001b3L;
            }
        }
        return h;
    }

    public static final class RandomStream {
        private long state;

        public RandomStream(long seed) {
            state = seed;
        }

        public long nextLong() {
            long z = (state += 0x9e3779b97f4a7c15L);
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }

        public double next() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        public double between(double a, double b) {
            return a + next() * (b - a);
        }
    }

    public static Profile effective(Page page, Map<String, Profile> profiles) {
        var base = profiles.get(page.profile());
        var rules = new TreeMap<>(base.damage());
        page.damage().entrySet().forEach(e -> {
            if (!rules.containsKey(e.getKey())) throw new IllegalArgumentException("Unknown category: " + e.getKey());
            Rule r = rules.get(e.getKey());
            var j = new JsonObject();
            j.addProperty("enabled", r.enabled());
            j.addProperty("probability", r.probability());
            j.addProperty("min", r.min());
            j.addProperty("max", r.max());
            j.addProperty("severity", r.severity());
            j.add("scale", new Gson().toJsonTree(List.of(r.minScale(), r.maxScale())));
            j.add("opacity", new Gson().toJsonTree(List.of(r.minOpacity(), r.maxOpacity())));
            j.add(
                    "rotation",
                    r.rotations().isEmpty() ? new JsonPrimitive("free") : new Gson().toJsonTree(r.rotations()));
            j.addProperty("mirror", r.mirror());
            j.addProperty("placement", r.placement());
            rules.put(
                    e.getKey(),
                    JournalData.rule(JournalData.merge(j, e.getValue().getAsJsonObject())));
        });
        return new Profile(base.id(), base.paper(), Collections.unmodifiableMap(rules));
    }

    public static Layout generate(
            Book book,
            Page page,
            Map<String, Profile> profiles,
            Map<String, Asset> assets,
            long seed,
            long previewOffset,
            Set<String> disabled) {
        var profile = effective(page, profiles);
        long pageSeed = hash(seed, page.entry(), page.anchor(), Long.toString(page.seedOffset() + previewOffset));
        var paperRandom = new RandomStream(hash(pageSeed, "paper"));
        String paper = weighted(profile.paper(), paperRandom);
        var surface = new ArrayList<Decoration>();
        var physical = new ArrayList<Decoration>();
        // Both surfaces select the same strongest physical rule, independent of display/filter order.
        var physicalRules = new TreeMap<String, Rule>();
        for (var other : book.pages())
            if (other.leaf().equals(page.leaf())) {
                effective(other, profiles).damage().forEach((category, rule) -> {
                    if (category.equals("masks") || category.equals("wear")) {
                        var old = physicalRules.get(category);
                        if (old == null || rule.severity() > old.severity()) physicalRules.put(category, rule);
                    }
                });
            }
        for (var e : profile.damage().entrySet()) {
            if (physicalRules.containsKey(e.getKey()) || disabled.contains(e.getKey())) continue;
            generateCategory(surface, e.getKey(), e.getValue(), assets, hash(pageSeed, e.getKey()), false);
        }
        for (var e : physicalRules.entrySet())
            if (!disabled.contains(e.getKey()))
                generateCategory(
                        physical,
                        e.getKey(),
                        e.getValue(),
                        assets,
                        hash(seed, "leaf", page.leaf(), e.getKey(), Long.toString(previewOffset)),
                        true);
        int index = book.leaves().indexOf(page.leaf());
        for (var event : book.events())
            if (!disabled.contains(event.category())) {
                double intensity = event.intensityAt(index - book.leaves().indexOf(event.originLeaf()));
                if (intensity > 0)
                    surface.add(new Decoration(
                            event.asset(),
                            page.verso() ? 1 - event.x() : event.x(),
                            event.y(),
                            event.scale(),
                            page.verso() ? -event.rotation() : event.rotation(),
                            intensity,
                            page.verso(),
                            false));
            }
        if (page.verso())
            physical.replaceAll(d -> new Decoration(
                    d.asset(), 1 - d.x(), d.y(), d.scale(), -d.rotation(), d.opacity(), !d.mirror(), true));
        return new Layout(paper, List.copyOf(physical), List.copyOf(surface), page.authored());
    }

    private static void generateCategory(
            List<Decoration> output, String category, Rule r, Map<String, Asset> assets, long seed, boolean physical) {
        var random = new RandomStream(seed);
        if (!r.enabled() || random.next() >= r.probability()) return;
        var eligible = new TreeMap<String, Double>();
        assets.values().stream()
                .filter(a -> a.category().equals(category) && a.minimumSeverity() <= r.severity())
                .forEach(a -> eligible.put(a.id(), a.weight()));
        if (eligible.isEmpty()) return;
        int count = r.min() + (int) (random.next() * (r.max() - r.min() + 1));
        for (int i = 0; i < count; i++) {
            String id = weighted(eligible, random);
            var a = assets.get(id);
            String placement = !a.placement().equals("anywhere") ? a.placement() : r.placement();
            double x = random.between(0.12, 0.88), y = random.between(0.10, 0.90);
            if (!placement.equals("anywhere")) {
                if (random.next() < 0.5) x = random.next() < 0.5 ? 0.015 : 0.985;
                else y = random.next() < 0.5 ? 0.015 : 0.985;
            }
            List<Double> rotations = !a.rotations().isEmpty() ? a.rotations() : r.rotations();
            double rotation =
                    rotations.isEmpty() ? random.next() * 360 : rotations.get((int) (random.next() * rotations.size()));
            output.add(new Decoration(
                    id,
                    x,
                    y,
                    random.between(a.minScale(), a.maxScale()) * random.between(r.minScale(), r.maxScale()),
                    rotation,
                    random.between(a.minOpacity(), a.maxOpacity()) * random.between(r.minOpacity(), r.maxOpacity()),
                    a.mirror() && r.mirror() && random.next() < 0.5,
                    physical));
        }
    }

    private static String weighted(Map<String, Double> weights, RandomStream random) {
        double target = random.next()
                * weights.values().stream().mapToDouble(Double::doubleValue).sum();
        for (var e : new TreeMap<>(weights).entrySet()) {
            target -= e.getValue();
            if (target < 0) return e.getKey();
        }
        return new TreeMap<>(weights).lastKey();
    }
}
