package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

/** Server-owned JSON definitions. Each file validates atomically; sorted later files override IDs. */
public final class MixerRecipeRegistry {
    public record Name(ResourceLocation family, CompoundComposition composition, String literal, String translation) {
        public Component display() {
            return translation.isEmpty() ? Component.literal(literal) : Component.translatable(translation);
        }
    }

    public record Loaded(Map<String, MixerRecipeDefinition> recipes, List<Name> names, List<String> errors) {}

    private static Loaded current = new Loaded(Map.of(), List.of(), List.of());
    private static List<Name> clientNames = List.of();

    public static Collection<MixerRecipeDefinition> recipes() {
        return current.recipes().values().stream()
                .sorted(Comparator.comparingInt(MixerRecipeDefinition::priority)
                        .reversed()
                        .thenComparing(MixerRecipeDefinition::id))
                .toList();
    }

    public static MixerRecipeDefinition recipe(String id) {
        return current.recipes().get(id);
    }

    public static Loaded snapshot() {
        return current;
    }

    public static void publish(Loaded loaded) {
        current = loaded;
        clientNames = loaded.names();
    }

    public static boolean acceptsItem(net.minecraft.world.item.ItemStack stack) {
        return recipes().stream()
                .flatMap(r -> r.strategies().stream())
                .filter(MixerRecipeDefinition.Strategy::enabled)
                .flatMap(s -> s.items().stream())
                .anyMatch(i -> i.selector().test(stack));
    }

    public static boolean acceptsFluid(FluidStack stack) {
        return recipes().stream()
                .anyMatch(r -> r.strategies().stream()
                        .filter(MixerRecipeDefinition.Strategy::enabled)
                        .anyMatch(s -> java.util.stream.IntStream.range(
                                        0, s.fluids().size())
                                .anyMatch(i -> r.matchesInput(s, i, stack))));
    }

    public static boolean outputItem(net.minecraft.world.item.ItemStack stack) {
        return recipes().stream().flatMap(r -> r.itemOutputs().stream()).anyMatch(o -> stack.is(o.item()));
    }

    public static boolean outputFluid(FluidStack stack) {
        return recipes().stream().anyMatch(r -> r.outputMatches(stack));
    }

    public static Component name(FluidStack stack, boolean client) {
        var composition = stack.get(CompoundFluids.COMPOSITION.get());
        return (client ? clientNames : current.names())
                .stream()
                        .filter(n ->
                                BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(n.family())
                                        && n.composition().equals(composition))
                        .map(Name::display)
                        .findFirst()
                        .orElse(null);
    }

    public static String namesJson() {
        var array = new JsonArray();
        for (var n : current.names()) {
            var o = new JsonObject();
            o.addProperty("family", n.family().toString());
            o.add("composition", compositionJson(n.composition()));
            o.addProperty("display_name", n.literal());
            o.addProperty("translation_key", n.translation());
            array.add(o);
        }
        return array.toString();
    }

    public static void clientNames(String json) {
        clientNames = parseNames(JsonParser.parseString(json).getAsJsonArray());
    }

    private static JsonArray compositionJson(CompoundComposition composition) {
        var array = new JsonArray();
        for (var e : composition.entries()) {
            var o = new JsonObject();
            o.addProperty("kind", e.kind().name());
            o.add("selector", JsonParser.parseString(e.selector()));
            o.addProperty("amount", e.amount());
            array.add(o);
        }
        return array;
    }

    public static Loaded load(Path directory, Consumer<String> diagnostic) {
        var recipes = new TreeMap<String, MixerRecipeDefinition>();
        var names = new ArrayList<Name>();
        var errors = new ArrayList<String>();
        try {
            Files.createDirectories(directory);
            var defaults = directory.resolve("_defaults.json");
            try (var in = MixerRecipeRegistry.class.getResourceAsStream("/data/mobvats/mixer_defaults.json")) {
                if (in == null) throw new IllegalStateException("Missing bundled Mixer defaults");
                var bundled = in.readAllBytes();
                if (!Files.exists(defaults))
                    try {
                        Files.write(defaults, bundled, StandardOpenOption.CREATE_NEW);
                    } catch (FileAlreadyExistsException ignored) {
                    }
            }
            var files = new ArrayList<Path>();
            files.add(defaults);
            try (var stream = Files.list(directory)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json") && !p.equals(defaults))
                        .sorted()
                        .forEach(files::add);
            }
            for (var path : files)
                try {
                    var reader = new com.google.gson.stream.JsonReader(
                            new java.io.StringReader(Files.readString(path, StandardCharsets.UTF_8)));
                    var json = readUnique(reader);
                    if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                        throw new IllegalArgumentException("Trailing data");
                    var parsed = parse(json);
                    recipes.putAll(parsed.recipes());
                    for (var name : parsed.names()) {
                        names.removeIf(n -> n.family().equals(name.family())
                                && n.composition().equals(name.composition()));
                        names.add(name);
                    }
                } catch (Exception e) {
                    errors.add(path.getFileName() + ": " + e.getMessage());
                }
        } catch (Exception e) {
            errors.add(directory + ": " + e.getMessage());
        }
        errors.forEach(diagnostic);
        return new Loaded(Collections.unmodifiableMap(recipes), List.copyOf(names), List.copyOf(errors));
    }

    public static Loaded parse(JsonElement json) {
        var root = json.getAsJsonObject();
        fields(root, Set.of("recipes", "names"));
        var recipes = new TreeMap<String, MixerRecipeDefinition>();
        if (root.has("recipes"))
            for (var e : root.getAsJsonObject("recipes").entrySet())
                recipes.put(e.getKey(), parseRecipe(e.getKey(), e.getValue().getAsJsonObject()));
        return new Loaded(
                Collections.unmodifiableMap(recipes),
                root.has("names") ? parseNames(root.getAsJsonArray("names")) : List.of(),
                List.of());
    }

    private static MixerRecipeDefinition parseRecipe(String id, JsonObject o) {
        fields(
                o,
                Set.of(
                        "priority",
                        "strategies",
                        "outputs",
                        "items",
                        "fluids",
                        "output",
                        "output_amount",
                        "batch_ticks",
                        "composition",
                        "transform"));
        int priority = integer(o, "priority", 0);
        if (o.has("strategies") || o.has("outputs")) {
            for (var key : List.of("items", "fluids", "output", "output_amount", "composition", "transform"))
                if (o.has(key)) throw new IllegalArgumentException("Cannot mix legacy and v2 recipe fields");
            var strategies = new ArrayList<MixerRecipeDefinition.Strategy>();
            for (var v : o.getAsJsonArray("strategies")) {
                var s = v.getAsJsonObject();
                fields(s, Set.of("id", "priority", "enabled", "items", "fluids"));
                strategies.add(parseStrategy(
                        s.get("id").getAsString(),
                        integer(s, "priority", 0),
                        !s.has("enabled") || s.get("enabled").getAsBoolean(),
                        s));
            }
            var outputs = o.getAsJsonObject("outputs");
            fields(outputs, Set.of("items", "fluids"));
            var items = new ArrayList<MixerRecipeDefinition.ItemOutput>();
            var fluids = new ArrayList<MixerRecipeDefinition.FluidOutput>();
            if (outputs.has("items"))
                for (var v : outputs.getAsJsonArray("items")) {
                    var i = v.getAsJsonObject();
                    fields(i, Set.of("item", "amount"));
                    var key = ResourceLocation.parse(i.get("item").getAsString());
                    if (!BuiltInRegistries.ITEM.containsKey(key))
                        throw new IllegalArgumentException("Unknown output item: " + key);
                    items.add(new MixerRecipeDefinition.ItemOutput(
                            BuiltInRegistries.ITEM.get(key), positive(i, "amount")));
                }
            if (outputs.has("fluids"))
                for (var v : outputs.getAsJsonArray("fluids")) {
                    var f = v.getAsJsonObject();
                    fields(f, Set.of("fluid", "amount", "composition", "transform"));
                    fluids.add(parseOutput(f, "fluid", "amount"));
                }
            return new MixerRecipeDefinition(id, priority, strategies, items, fluids, positive(o, "batch_ticks"));
        }
        var strategy = parseStrategy("default", 0, true, o);
        return new MixerRecipeDefinition(
                id,
                priority,
                List.of(strategy),
                List.of(),
                List.of(parseOutput(o, "output", "output_amount")),
                positive(o, "batch_ticks"));
    }

    private static MixerRecipeDefinition.Strategy parseStrategy(
            String id, int priority, boolean enabled, JsonObject o) {
        var fluids = new ArrayList<MixerRecipeDefinition.FluidInput>();
        var items = new ArrayList<MixerRecipeDefinition.ItemInput>();
        if (o.has("fluids"))
            for (var v : o.getAsJsonArray("fluids")) {
                var i = v.getAsJsonObject();
                fields(i, Set.of("selector", "amount", "record_in_composition"));
                fluids.add(new MixerRecipeDefinition.FluidInput(
                        selector(i.get("selector")),
                        positive(i, "amount"),
                        !i.has("record_in_composition")
                                || i.get("record_in_composition").getAsBoolean()));
            }
        if (o.has("items"))
            for (var v : o.getAsJsonArray("items")) {
                var i = v.getAsJsonObject();
                fields(i, Set.of("selector", "amount", "record_in_composition"));
                items.add(new MixerRecipeDefinition.ItemInput(
                        selector(i.get("selector")),
                        positive(i, "amount"),
                        !i.has("record_in_composition")
                                || i.get("record_in_composition").getAsBoolean()));
            }
        return new MixerRecipeDefinition.Strategy(id, priority, enabled, fluids, items);
    }

    private static MixerRecipeDefinition.FluidOutput parseOutput(JsonObject o, String fluidField, String amountField) {
        var fluidId = ResourceLocation.parse(o.get(fluidField).getAsString());
        if (!BuiltInRegistries.FLUID.containsKey(fluidId)
                || BuiltInRegistries.FLUID.get(fluidId) == net.minecraft.world.level.material.Fluids.EMPTY)
            throw new IllegalArgumentException("Unknown output fluid: " + fluidId);
        var output = BuiltInRegistries.FLUID.get(fluidId);
        var composition = o.has("composition") ? parseComposition(o.getAsJsonArray("composition")) : null;
        if (composition != null && !CompoundFluids.isCompound(new FluidStack(output, 1)))
            throw new IllegalArgumentException("Composition on ordinary output");
        MixerRecipeDefinition.Transform transform = null;
        if (o.has("transform")) {
            var t = o.getAsJsonObject("transform");
            fields(t, Set.of("source_input", "remove", "required", "excluded", "restore_ordinary"));
            int source = t.has("source_input")
                    ? t.get("source_input").getAsBigDecimal().intValueExact()
                    : 0;
            var remove = new ArrayList<MixerRecipeDefinition.Removal>();
            for (var v : t.getAsJsonArray("remove")) {
                var r = v.getAsJsonObject();
                fields(r, Set.of("kind", "selector"));
                var kind = CompoundComposition.Kind.valueOf(r.get("kind").getAsString());
                var selector = selector(r.get("selector"));
                if (kind == CompoundComposition.Kind.FLUID) new MixerRecipeDefinition.FluidInput(selector, 1);
                else new MixerRecipeDefinition.ItemInput(selector, 1);
                remove.add(new MixerRecipeDefinition.Removal(kind, selector));
            }
            transform = new MixerRecipeDefinition.Transform(
                    source,
                    remove,
                    t.has("required") ? parseComposition(t.getAsJsonArray("required")) : null,
                    t.has("excluded") ? parseComposition(t.getAsJsonArray("excluded")) : null,
                    t.has("restore_ordinary") && t.get("restore_ordinary").getAsBoolean());
            if (!CompoundFluids.isCompound(new FluidStack(output, 1)))
                throw new IllegalArgumentException("Transform output must declare a compound family");
        }
        return new MixerRecipeDefinition.FluidOutput(() -> output, positive(o, amountField), composition, transform);
    }

    private static List<Name> parseNames(JsonArray array) {
        var result = new ArrayList<Name>();
        for (var value : array) {
            var o = value.getAsJsonObject();
            fields(o, Set.of("family", "composition", "display_name", "translation_key"));
            var family = ResourceLocation.parse(o.get("family").getAsString());
            if (!BuiltInRegistries.FLUID.containsKey(family)
                    || !CompoundFluids.isCompound(new FluidStack(BuiltInRegistries.FLUID.get(family), 1)))
                throw new IllegalArgumentException("Unknown compound family: " + family);
            var literal = o.has("display_name") ? o.get("display_name").getAsString() : "";
            var translation =
                    o.has("translation_key") ? o.get("translation_key").getAsString() : "";
            if (literal.isEmpty() && translation.isEmpty())
                throw new IllegalArgumentException("Formula name requires display_name or translation_key");
            result.add(new Name(family, parseComposition(o.getAsJsonArray("composition")), literal, translation));
        }
        return List.copyOf(result);
    }

    public static CompoundComposition parseComposition(JsonArray array) {
        var result = new ArrayList<CompoundComposition.Entry>();
        for (var value : array) {
            var o = value.getAsJsonObject();
            fields(o, Set.of("kind", "selector", "amount"));
            var kind = CompoundComposition.Kind.valueOf(o.get("kind").getAsString());
            var selector = selector(o.get("selector"));
            if (kind == CompoundComposition.Kind.ITEM) new MixerRecipeDefinition.ItemInput(selector, 1);
            else new MixerRecipeDefinition.FluidInput(selector, 1);
            // A formula contains underlying resources, never a compound family with an implicit nested formula.
            if (kind == CompoundComposition.Kind.FLUID) {
                var tree = JsonParser.parseString(CompoundComposition.canonicalSelector(kind, selector));
                rejectCompoundReferences(tree);
            }
            result.add(new CompoundComposition.Entry(kind, selector, positive(o, "amount")));
        }
        return new CompoundComposition(result);
    }

    private static void rejectCompoundReferences(JsonElement tree) {
        if (tree.isJsonObject()) {
            var o = tree.getAsJsonObject();
            if (o.has("mobvats:composition"))
                throw new IllegalArgumentException("Nested composition is not allowed in a fluid formula selector");
            for (var key : List.of("fluid", "fluids")) if (o.has(key)) rejectFamily(o.get(key));
            for (var e : o.entrySet())
                if (e.getValue().isJsonArray() || e.getValue().isJsonObject()) rejectCompoundReferences(e.getValue());
        } else if (tree.isJsonArray()) for (var e : tree.getAsJsonArray()) rejectCompoundReferences(e);
    }

    private static void rejectFamily(JsonElement value) {
        if (value.isJsonArray()) {
            for (var entry : value.getAsJsonArray()) rejectFamily(entry);
            return;
        }
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().startsWith("#")) return;
        var id = ResourceLocation.tryParse(value.getAsString());
        if (id != null
                && BuiltInRegistries.FLUID.containsKey(id)
                && CompoundFluids.isCompound(new FluidStack(BuiltInRegistries.FLUID.get(id), 1)))
            throw new IllegalArgumentException("Composition cannot reference a compound family");
    }

    private static String selector(JsonElement e) {
        if (e == null) throw new IllegalArgumentException("Missing selector");
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static int positive(JsonObject o, String key) {
        var v = o.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be a positive integer");
        int n = v.getAsBigDecimal().intValueExact();
        if (n <= 0) throw new IllegalArgumentException(key + " must be positive");
        return n;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        var v = o.get(key);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be an integer");
        return v.getAsBigDecimal().intValueExact();
    }

    private static void fields(JsonObject o, Set<String> allowed) {
        for (var key : o.keySet())
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
    }

    private static JsonElement readUnique(com.google.gson.stream.JsonReader r) throws java.io.IOException {
        return switch (r.peek()) {
            case BEGIN_OBJECT -> {
                var o = new JsonObject();
                r.beginObject();
                while (r.hasNext()) {
                    var key = r.nextName();
                    if (o.has(key)) throw new IllegalArgumentException("Duplicate key: " + key);
                    o.add(key, readUnique(r));
                }
                r.endObject();
                yield o;
            }
            case BEGIN_ARRAY -> {
                var a = new JsonArray();
                r.beginArray();
                while (r.hasNext()) a.add(readUnique(r));
                r.endArray();
                yield a;
            }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> {
                r.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Invalid JSON");
        };
    }

    private MixerRecipeRegistry() {}
}
