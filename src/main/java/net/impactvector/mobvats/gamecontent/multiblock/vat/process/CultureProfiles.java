package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

/** Ordered server data: explicit mob assignment, ordered groups, default, generic dry fallback. */
public final class CultureProfiles {
    public record Rule(
            MixerRecipeDefinition.FluidInput medium,
            CompoundComposition required,
            double frequency,
            double dirtyFrequency) {
        public Rule(MixerRecipeDefinition.FluidInput medium, CompoundComposition required, double frequency) {
            this(medium, required, frequency, frequency);
        }

        public boolean matches(FluidStack stack) {
            if (!medium.selector().test(stack)) return false;
            if (required == null) return true;
            var composition = stack.get(CompoundFluids.COMPOSITION.get());
            if (composition == null) return false;
            for (var need : required.entries()) {
                long amount = composition.entries().stream()
                        .filter(e -> e.kind() == need.kind() && contains(need, e))
                        .mapToLong(CompoundComposition.Entry::amount)
                        .sum();
                if (amount < need.amount()) return false;
            }
            return true;
        }
    }

    public record Group(List<ResourceLocation> mobs, String profile) {}

    public record Data(
            Map<String, List<Rule>> profiles,
            Map<ResourceLocation, String> mobs,
            List<Group> groups,
            String defaultProfile,
            int wasteFormulaMb,
            int defaultFoulingMb,
            Map<String, Integer> actions,
            double maxFoulingMultiplier) {
        public Data {
            if (!Double.isFinite(maxFoulingMultiplier) || maxFoulingMultiplier < 1)
                throw new IllegalArgumentException("max_multiplier must be finite and >= 1");
        }

        public Data(
                Map<String, List<Rule>> profiles,
                Map<ResourceLocation, String> mobs,
                List<Group> groups,
                String defaultProfile,
                int wasteFormulaMb,
                int defaultFoulingMb,
                Map<String, Integer> actions) {
            this(profiles, mobs, groups, defaultProfile, wasteFormulaMb, defaultFoulingMb, actions, 5.0);
        }

        public Data(
                Map<String, List<Rule>> profiles,
                Map<ResourceLocation, String> mobs,
                List<Group> groups,
                String defaultProfile) {
            this(profiles, mobs, groups, defaultProfile, 100, 50, Map.of());
        }
    }

    private static Data current = new Data(Map.of(), Map.of(), List.of(), "");

    public static Data snapshot() {
        return current;
    }

    public static void publish(Data data) {
        current = data;
    }

    public static double multiplier(ResourceLocation mob, FluidStack medium) {
        var rule = resolve(mob, medium);
        return rule == null ? 0.5 : rule.frequency();
    }

    public static Rule resolve(ResourceLocation mob, FluidStack medium) {
        if (medium.isEmpty()) return null;
        String profile = current.mobs().get(mob);
        if (profile == null)
            profile = current.groups().stream()
                    .filter(g -> g.mobs().contains(mob))
                    .map(Group::profile)
                    .findFirst()
                    .orElse(current.defaultProfile());
        for (var rule : current.profiles().getOrDefault(profile, List.of())) if (rule.matches(medium)) return rule;
        return null;
    }

    public static int foulingMb(String action) {
        return current.actions().getOrDefault(action, current.defaultFoulingMb());
    }

    public static int wasteFormulaMb() {
        return current.wasteFormulaMb();
    }

    public static double maxFoulingMultiplier() {
        return current.maxFoulingMultiplier();
    }
    /** Identical authored selectors match directly; exact resources can satisfy a tag/ingredient rule.
     * Unknown tag formulas are never expanded speculatively into a more specific ingredient. */
    public static boolean contains(CompoundComposition.Entry need, CompoundComposition.Entry actual) {
        if (need.selector().equals(actual.selector())) return true;
        var o = JsonParser.parseString(actual.selector());
        if (!o.isJsonObject()) return false;
        String key = actual.kind() == CompoundComposition.Kind.ITEM ? "item" : "fluid";
        if (!o.getAsJsonObject().has(key) || !o.getAsJsonObject().get(key).isJsonPrimitive()) return false;
        var id = ResourceLocation.parse(o.getAsJsonObject().get(key).getAsString());
        if (actual.kind() == CompoundComposition.Kind.ITEM)
            return new MixerRecipeDefinition.ItemInput(need.selector(), 1)
                    .selector()
                    .test(new net.minecraft.world.item.ItemStack(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)));
        return new MixerRecipeDefinition.FluidInput(need.selector(), 1)
                .selector()
                .test(new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id), 1));
    }

    public static Data parse(JsonObject root) {
        var profiles = new TreeMap<String, List<Rule>>();
        var mobs = new HashMap<ResourceLocation, String>();
        var groups = new ArrayList<Group>();
        for (var e : root.getAsJsonObject("profiles").entrySet()) {
            var rules = new ArrayList<Rule>();
            for (var value : e.getValue().getAsJsonArray()) {
                var o = value.getAsJsonObject();
                double frequency = o.get("frequency").getAsDouble();
                if (!Double.isFinite(frequency) || frequency < 0.5)
                    throw new IllegalArgumentException("Frequency must be finite and >= 0.5");
                double dirty =
                        o.has("dirty_frequency") ? o.get("dirty_frequency").getAsDouble() : frequency;
                if (!Double.isFinite(dirty) || dirty < 0.5)
                    throw new IllegalArgumentException("Dirty frequency must be finite and >= 0.5");
                var selector = o.get("medium");
                rules.add(new Rule(
                        new MixerRecipeDefinition.FluidInput(
                                selector.isJsonPrimitive() ? selector.getAsString() : selector.toString(), 1),
                        o.has("required") ? MixerRecipeRegistry.parseComposition(o.getAsJsonArray("required")) : null,
                        frequency,
                        dirty));
            }
            profiles.put(e.getKey(), List.copyOf(rules));
        }
        if (root.has("mobs"))
            root.getAsJsonObject("mobs")
                    .entrySet()
                    .forEach(e -> mobs.put(
                            ResourceLocation.parse(e.getKey()), e.getValue().getAsString()));
        if (root.has("groups"))
            for (var e : root.getAsJsonArray("groups")) {
                var o = e.getAsJsonObject();
                var ids = new ArrayList<ResourceLocation>();
                for (var id : o.getAsJsonArray("mobs")) ids.add(ResourceLocation.parse(id.getAsString()));
                groups.add(new Group(List.copyOf(ids), o.get("profile").getAsString()));
            }
        String fallback = root.has("default") ? root.get("default").getAsString() : "";
        var refs = new ArrayList<>(mobs.values());
        groups.forEach(g -> refs.add(g.profile()));
        if (!fallback.isEmpty()) refs.add(fallback);
        for (var ref : refs)
            if (!profiles.containsKey(ref)) throw new IllegalArgumentException("Unknown culture profile: " + ref);
        int waste = 100, fouling = 50;
        double maximum = 5.0;
        var actions = new TreeMap<String, Integer>();
        if (root.has("fouling")) {
            var o = root.getAsJsonObject("fouling");
            if (o.has("max_multiplier")) maximum = o.get("max_multiplier").getAsDouble();
            if (o.has("waste_formula_mb")) waste = integer(o, "waste_formula_mb", 1);
            if (o.has("default_mb")) fouling = integer(o, "default_mb", 0);
            if (o.has("actions"))
                for (var e : o.getAsJsonObject("actions").entrySet()) {
                    ResourceLocation.parse(e.getKey());
                    actions.put(e.getKey(), integer(e.getValue().getAsJsonObject(), "fouling_mb", 0));
                }
        }
        return new Data(
                Collections.unmodifiableMap(profiles),
                Map.copyOf(mobs),
                List.copyOf(groups),
                fallback,
                waste,
                fouling,
                Map.copyOf(actions),
                maximum);
    }

    private static int integer(JsonObject o, String key, int minimum) {
        int n = o.get(key).getAsBigDecimal().intValueExact();
        if (n < minimum) throw new IllegalArgumentException(key + " must be >= " + minimum);
        return n;
    }

    public static void reload() {
        var directory = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mobvats/culture");
        try {
            Files.createDirectories(directory);
            var defaults = directory.resolve("_defaults.json");
            try (var in = CultureProfiles.class.getResourceAsStream("/data/mobvats/culture_defaults.json")) {
                String bundled =
                        new String(Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (!Files.exists(defaults)) Files.writeString(defaults, bundled, StandardOpenOption.CREATE_NEW);
                else {
                    var old = JsonParser.parseString(bundled).getAsJsonObject();
                    old.remove("fouling");
                    for (var profile : old.getAsJsonObject("profiles").entrySet())
                        for (var rule : profile.getValue().getAsJsonArray())
                            rule.getAsJsonObject().remove("dirty_frequency");
                    // Upgrade only the exact untouched 12.1 bundled file; authored configurations survive.
                    boolean untouched = false;
                    try {
                        untouched = JsonParser.parseString(Files.readString(defaults))
                                .equals(old);
                    } catch (JsonParseException ignored) {
                    } // Report malformed files in the normal per-file validation below.
                    if (untouched) Files.writeString(defaults, bundled);
                }
            }
            // Each override is a complete culture configuration, validated before publication.
            var files = new ArrayList<Path>();
            files.add(defaults);
            try (var stream = Files.list(directory)) {
                stream.filter(p -> p.toString().endsWith(".json") && !p.equals(defaults))
                        .sorted()
                        .forEach(files::add);
            }
            Data loaded = current;
            for (var path : files)
                try {
                    loaded =
                            parse(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                } catch (Exception error) {
                    com.mojang.logging.LogUtils.getLogger().error("Culture profiles {}: {}", path, error.getMessage());
                }
            publish(loaded);
        } catch (Exception error) {
            com.mojang.logging.LogUtils.getLogger().error("Culture profiles: {}", error.getMessage());
        }
    }

    private CultureProfiles() {}
}
