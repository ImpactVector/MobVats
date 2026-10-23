package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** File-level validation and whole-definition replacement. Published on the server thread. */
public final class PassiveMobDefinitionRegistry {
    public static final Gson JSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Map<ResourceLocation, PassiveMobDefinition> current = Map.of();
    private static Map<ResourceLocation, String> sources = Map.of();

    public static String source(ResourceLocation id) {
        return sources.getOrDefault(id, "missing / processing disabled");
    }

    public record Loaded(
            Map<ResourceLocation, PassiveMobDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<String> errors) {}

    public static PassiveMobDefinition resolve(ResourceLocation id) {
        return current.get(id);
    }

    public static Map<ResourceLocation, PassiveMobDefinition> definitions() {
        return current;
    }

    public static void publish(Loaded loaded) {
        var profiles = new HashMap<ResourceLocation, MobProcessRegistry.Profile>();
        loaded.definitions().forEach((id, d) -> profiles.put(id, PassiveProcessCompiler.compile(d)));
        current = loaded.definitions();
        sources = loaded.sources();
        MobProcessRegistry.DEFAULT.replace(profiles);
    }

    public static Loaded load(Path directory, Consumer<String> diagnostic) {
        var definitions =
                new TreeMap<ResourceLocation, PassiveMobDefinition>(Comparator.comparing(ResourceLocation::toString));
        var sources = new HashMap<ResourceLocation, String>();
        var errors = new ArrayList<String>();
        try {
            Files.createDirectories(directory);
            var defaults = directory.resolve("_defaults.json");
            if (!Files.exists(defaults)) {
                // CREATE_NEW ensures an existing user file cannot be replaced, including a concurrent creation.
                try {
                    Files.writeString(
                            defaults,
                            JSON.toJson(new TreeMap<>(PassiveDefaults.data())) + "\n",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW);
                } catch (FileAlreadyExistsException ignored) {
                }
            }
            var files = new ArrayList<Path>();
            files.add(defaults);
            try (var stream = Files.list(directory)) {
                stream.filter(p -> Files.isRegularFile(p)
                                && p.getFileName().toString().endsWith(".json")
                                && !p.equals(defaults))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(files::add);
            }
            for (var file : files) {
                try {
                    var reader = new com.google.gson.stream.JsonReader(
                            new java.io.StringReader(Files.readString(file, StandardCharsets.UTF_8)));
                    var tree = uniqueJson(reader);
                    require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT, "Trailing JSON data");
                    require(tree.isJsonObject(), "root must be an object keyed by entity ID");
                    var parsed = new LinkedHashMap<ResourceLocation, PassiveMobDefinition>();
                    for (var entry : tree.getAsJsonObject().entrySet()) {
                        shape(entry.getValue(), PassiveMobDefinition.class, entry.getKey());
                        var definition = JSON.fromJson(entry.getValue(), PassiveMobDefinition.class);
                        validate(entry.getKey(), definition);
                        parsed.put(definition.identity(), definition);
                    }
                    parsed.forEach((id, d) -> {
                        definitions.put(id, d);
                        sources.put(id, file.getFileName().toString());
                        d.tagDiagnostics()
                                .forEach(issue -> diagnostic.accept(
                                        file.getFileName() + ": " + issue + " (membership is evaluated live)"));
                    });
                } catch (Exception error) {
                    errors.add(file.getFileName() + ": " + error.getMessage());
                }
            }
        } catch (Exception error) {
            errors.add(directory + ": " + error.getMessage());
        }
        if (!definitions.containsKey(ResourceLocation.withDefaultNamespace("cow")))
            errors.add("Required built-in minecraft:cow has no valid loaded definition; its processing is disabled");
        errors.forEach(diagnostic);
        return new Loaded(Collections.unmodifiableMap(definitions), Map.copyOf(sources), List.copyOf(errors));
    }

    private static JsonElement uniqueJson(com.google.gson.stream.JsonReader reader) throws java.io.IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    var name = reader.nextName();
                    require(!object.has(name), "Duplicate definition/field: " + name);
                    object.add(name, uniqueJson(reader));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(uniqueJson(reader));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Invalid JSON token: " + reader.peek());
        };
    }

    private static void shape(JsonElement value, Class<?> type, String path) {
        require(value != null && value.isJsonObject(), path + " must be an object");
        var object = value.getAsJsonObject();
        var allowed = new HashSet<String>();
        for (var field : type.getRecordComponents()) allowed.add(field.getName());
        for (var key : object.keySet()) require(allowed.contains(key), path + ": unknown field " + key);
        for (var field : type.getRecordComponents()) {
            var name = field.getName();
            var element = object.get(name);
            var child = path + "." + name;
            if ((type == ToolInteractionDefinition.class
                                    && !Set.of("id", "executor").contains(name)
                            || type == PassiveMobDefinition.Capture.class
                            || type == PassiveMobDefinition.ResourceInput.class
                            || type == PassiveMobDefinition.ResourceOutput.class
                            || type == ProductBufferDefinition.class
                            || type == PassiveMobDefinition.ResourceProcess.class
                                    && !Set.of("id", "duration_ticks").contains(name)
                            || type == PassiveMobDefinition.class && !name.equals("entity")
                            || Set.of("container", "simulacrum", "dimensions", "head_offset", "fouling_mb")
                                    .contains(name))
                    && (element == null || element.isJsonNull())) continue;
            require(element != null && !element.isJsonNull(), child + " is required");
            var fieldType = field.getType();
            if (fieldType.isRecord()) shape(element, fieldType, child);
            else if (fieldType == Map.class) {
                require(element.isJsonObject(), child + " must be an object");
                for (var entry : element.getAsJsonObject().entrySet())
                    shape(entry.getValue(), ProductBufferDefinition.class, child + "." + entry.getKey());
            } else if (fieldType == List.class) {
                require(element.isJsonArray(), child + " must be an array");
                if (name.equals("interactions"))
                    for (var item : element.getAsJsonArray()) shape(item, ToolInteractionDefinition.class, child);
                else if (name.equals("processes"))
                    for (var item : element.getAsJsonArray())
                        shape(item, PassiveMobDefinition.ResourceProcess.class, child);
                else if (name.equals("inputs"))
                    for (var item : element.getAsJsonArray())
                        shape(item, PassiveMobDefinition.ResourceInput.class, child);
                else if (name.equals("outputs"))
                    for (var item : element.getAsJsonArray())
                        shape(item, PassiveMobDefinition.ResourceOutput.class, child);
                else if (name.equals("secondary_outputs"))
                    for (var item : element.getAsJsonArray())
                        shape(item, PassiveMobDefinition.SecondaryOutput.class, child);
                else if (name.equals("dimensions") || name.equals("head_offset")) {
                    require(element.getAsJsonArray().size() == 3, child + " requires three integers");
                    for (var item : element.getAsJsonArray())
                        require(
                                item.isJsonPrimitive()
                                        && item.getAsJsonPrimitive().isNumber()
                                        && item.getAsBigDecimal()
                                                        .stripTrailingZeros()
                                                        .scale()
                                                <= 0
                                        && item.getAsBigDecimal()
                                                        .compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE))
                                                <= 0
                                        && item.getAsBigDecimal()
                                                        .compareTo(java.math.BigDecimal.valueOf(Integer.MIN_VALUE))
                                                >= 0,
                                child + " requires 32-bit integers");
                } else
                    for (var item : element.getAsJsonArray())
                        require(
                                item.isJsonPrimitive()
                                        && item.getAsJsonPrimitive().isString(),
                                child + " must contain strings");
            } else {
                require(element.isJsonPrimitive(), child + " must be a primitive");
                var primitive = element.getAsJsonPrimitive();
                if (fieldType == int.class || fieldType == Integer.class)
                    require(
                            primitive.isNumber()
                                    && primitive
                                                    .getAsBigDecimal()
                                                    .stripTrailingZeros()
                                                    .scale()
                                            <= 0
                                    && primitive
                                                    .getAsBigDecimal()
                                                    .compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE))
                                            <= 0
                                    && primitive
                                                    .getAsBigDecimal()
                                                    .compareTo(java.math.BigDecimal.valueOf(Integer.MIN_VALUE))
                                            >= 0,
                            child + " must be a 32-bit integer");
                else if (fieldType == boolean.class || fieldType == Boolean.class)
                    require(primitive.isBoolean(), child + " must be boolean");
                else {
                    require(primitive.isString(), child + " must be a string");
                    if (fieldType.isEnum())
                        require(
                                Arrays.stream(fieldType.getEnumConstants())
                                        .anyMatch(v -> ((Enum<?>) v).name().equals(primitive.getAsString())),
                                child + " has unsupported value: " + primitive.getAsString());
                }
            }
        }
    }

    private static void id(String value) {
        require(value != null && ResourceLocation.tryParse(value) != null, "Invalid ID: " + value);
    }

    private static void positive(int value, String field) {
        require(value > 0, field + " must be positive");
    }

    private static void item(String value) {
        id(value);
        require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(value)), "Unknown item: " + value);
    }

    private static void use(String value) {
        require(
                Set.of(
                                "",
                                PassiveUses.HARVEST,
                                PassiveUses.SECONDARY,
                                PassiveUses.DRAIN,
                                PassiveUses.BLEND,
                                PassiveUses.SHEAR,
                                PassiveUses.FEED)
                        .contains(value),
                "Unsupported actor use: " + value);
    }

    private static void validate(String key, PassiveMobDefinition d) {
        id(key);
        id(d.entity());
        require(key.equals(d.entity()), "entity must match root key " + key);
        require(BuiltInRegistries.ENTITY_TYPE.containsKey(d.identity()), "Unknown entity: " + key);
        var capture = d.capture();
        require(!capture.tool().isEmpty(), "capture.tool must be an item/tag selector");
        ResourceSelectors.items(List.of(capture.tool()));
        require(capture.health_cost() >= 0, "capture.health_cost must be nonnegative");
        if (capture.cost() != null) {
            positive(capture.cost().count(), "capture.cost.count");
            require(!capture.cost().items().isEmpty(), "capture.cost.items cannot be empty");
            ResourceSelectors.items(capture.cost().items());
        }
        var size = BuiltInRegistries.ENTITY_TYPE.get(d.identity()).getDimensions();
        net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.resolve(
                size.width(), size.height(), size.width(), net.minecraft.core.Direction.NORTH, d.simulacrum());
        if (d.lifecycle()) {
            require(
                    d.harvest() != null && d.feeding_slurry() != null,
                    "Lifecycle requires harvest and feeding slurry sections");
            positive(d.juvenile_growth_ticks(), "juvenile_growth_ticks");
            positive(d.feed().count(), "feed.count");
            require(!d.feed().items().isEmpty(), "feed.items cannot be empty");
            ResourceSelectors.items(d.feed().items());
            require(
                    d.harvest().sword().equals("") || d.harvest().sword().equals("mobvats:vanilla_entity_death_loot"),
                    "Unsupported sword strategy");
            require(
                    d.harvest().blender().equals("")
                            || d.harvest().blender().equals("mobvats:meat")
                            || d.harvest().blender().equals("mobvats:strange_meat"),
                    "Unsupported blender strategy");
            positive(d.harvest().blender_amount(), "blender_amount");
            require(d.harvest().blender_xp() >= 0, "blender_xp must be nonnegative");
            require(
                    d.harvest().actor_use().equals(PassiveUses.HARVEST),
                    "harvest actor_use must be " + PassiveUses.HARVEST);
            positive(d.feeding_slurry().cost(), "feeding_slurry.cost");
        } else
            require(
                    d.harvest() == null
                            && d.culture_slurry() == null
                            && d.feeding_slurry() == null
                            && d.juvenile_growth_ticks() == 0,
                    "Passive lifecycle fields require feed");
        var processes = new HashSet<String>();
        for (var out : d.secondary_outputs()) {
            id(out.process());
            require(
                    processes.add(out.process()) && !out.process().equals(PassiveProcessCompiler.GROWTH),
                    "Duplicate/reserved process ID: " + out.process());
            positive(out.amount(), "output.amount");
            positive(out.interval_ticks(), "interval_ticks");
            use(out.actor_use());
            require(out.requires_stage() != null, "requires_stage must be ADULT, JUVENILE or ANY");
            fluid(out.fluid());
            ProductBuffers.key(out.buffer());
            if (out.container() != null) {
                item(out.container().input());
                item(out.container().output());
                positive(out.container().amount(), "container.amount");
                require(out.container().actor_use().equals(PassiveUses.SECONDARY), "Unsupported container actor use");
            }
        }
        resources(d, processes);
        var interactionIds = new HashSet<String>();
        for (var interaction : d.toolInteractions()) {
            interaction.validate();
            require(interactionIds.add(interaction.id()), "Duplicate interaction ID: " + interaction.id());
            require(
                    !processes.contains(interaction.id())
                            && !Set.of(PassiveProcessCompiler.GROWTH, PassiveProcessCompiler.HARVEST)
                                    .contains(interaction.id()),
                    "Reserved interaction state ID: " + interaction.id());
            ToolInteractionExecutorRegistry.require(ResourceLocation.parse(interaction.executor()))
                    .validateDefinition(d, interaction);
        }
    }

    private static void fluid(String value) {
        id(value);
        require(BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(value)), "Unknown fluid: " + value);
        require(!value.equals("minecraft:empty"), "Empty fluid cannot be stored");
    }

    private static void resources(PassiveMobDefinition d, Set<String> processes) {
        require(
                d.buffers().keySet().stream()
                                .map(ResourceLocation::parse)
                                .distinct()
                                .count()
                        == d.buffers().size(),
                "Duplicate canonical buffer ID");
        var buffers = d.bufferDefinitions();
        d.buffers().forEach((key, b) -> id(key));
        buffers.forEach((key, b) -> {
            positive(b.limit(), "buffer.capacity");
            require(!b.fluids().isEmpty(), "buffer.fluids cannot be empty");
            b.selector();
        });
        for (var out : d.secondary_outputs()) compatible(buffers, ProductBuffers.key(out.buffer()), out.fluid());
        for (var process : d.processes()) {
            id(process.id());
            require(
                    processes.add(process.id())
                            && !Set.of(PassiveProcessCompiler.GROWTH, PassiveProcessCompiler.HARVEST)
                                    .contains(process.id()),
                    "Duplicate/reserved process ID: " + process.id());
            positive(process.duration_ticks(), "duration_ticks");
            use(process.actor_use());
            require(!process.outputs().isEmpty(), "process.outputs cannot be empty");
            for (var in : process.inputs()) {
                require(in.type() != null, "input.type required");
                switch (in.type()) {
                    case "item" -> {
                        positive(in.count(), "input.count");
                        require(in.items() != null && !in.items().isEmpty(), "input.items required");
                        ResourceSelectors.items(in.items());
                    }
                    case "fluid" -> {
                        positive(in.amount(), "input.amount");
                        require(in.fluids() != null && !in.fluids().isEmpty(), "input.fluids required");
                        ResourceSelectors.fluids(in.fluids());
                    }
                    case "xp" -> positive(in.amount(), "input.amount");
                    default -> throw new IllegalArgumentException("Unsupported input type: " + in.type());
                }
            }
            for (var out : process.outputs()) {
                require(out.type() != null, "output.type required");
                switch (out.type()) {
                    case "item" -> {
                        item(out.item());
                        positive(out.count(), "output.count");
                        require(!out.item().equals("minecraft:air"), "Air item cannot be output");
                    }
                    case "fluid" -> {
                        id(out.buffer());
                        fluid(out.fluid());
                        positive(out.amount(), "output.amount");
                        compatible(buffers, ResourceLocation.parse(out.buffer()), out.fluid());
                    }
                    case "xp" -> positive(out.amount(), "output.amount");
                    default -> throw new IllegalArgumentException("Unsupported output type: " + out.type());
                }
            }
        }
    }

    private static void compatible(
            Map<ResourceLocation, ProductBufferDefinition> buffers, ResourceLocation id, String fluid) {
        var definition = buffers.get(id);
        require(definition != null, "Undefined buffer: " + id);
        var stack = new net.neoforged.neoforge.fluids.FluidStack(
                BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluid)), 1);
        // Tags are syntactically validated here; live membership is checked by storage at execution time.
        require(
                definition.fluids().stream().anyMatch(s -> s.startsWith("#") || s.equals(fluid)),
                "Buffer/fluid incompatibility: " + id + " / " + fluid);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }

    private PassiveMobDefinitionRegistry() {}
}
