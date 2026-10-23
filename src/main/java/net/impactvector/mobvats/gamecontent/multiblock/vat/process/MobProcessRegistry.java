package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Species lookup is performed on topology rebuild, never across unrelated definitions each tick. */
public final class MobProcessRegistry {
    public static final class Profile {
        private final List<ProcessDefinition> definitions, tick, cadence;
        private final Map<ResourceLocation, ProductBufferDefinition> buffers;
        private String foulingConfiguration = "";

        public String foulingConfiguration() {
            return foulingConfiguration;
        }

        Profile foulingConfiguration(PassiveMobDefinition definition) {
            // Canonical JSON is independent of map iteration order across JVMs/reloads.
            foulingConfiguration = com.google.common.hash.Hashing.sha256()
                    .hashString(
                            canonical(PassiveMobDefinitionRegistry.JSON.toJsonTree(definition))
                                    .toString(),
                            java.nio.charset.StandardCharsets.UTF_8)
                    .toString();
            return this;
        }

        private static com.google.gson.JsonElement canonical(com.google.gson.JsonElement value) {
            if (value.isJsonObject()) {
                var sorted = new com.google.gson.JsonObject();
                new TreeSet<>(value.getAsJsonObject().keySet())
                        .forEach(key -> sorted.add(
                                key, canonical(value.getAsJsonObject().get(key))));
                return sorted;
            }
            if (value.isJsonArray()) {
                var array = new com.google.gson.JsonArray();
                value.getAsJsonArray().forEach(element -> array.add(canonical(element)));
                return array;
            }
            return value;
        }

        public Map<ResourceLocation, ProductBufferDefinition> buffers() {
            return buffers;
        }

        public Profile(List<ProcessDefinition> definitions) {
            this(definitions, null);
        }

        public Profile(List<ProcessDefinition> definitions, Map<ResourceLocation, ProductBufferDefinition> buffers) {
            this.buffers = buffers == null ? null : Map.copyOf(buffers);
            this.definitions = definitions.stream()
                    .sorted(Comparator.comparing(ProcessDefinition::id))
                    .toList();
            if (this.definitions.stream().map(ProcessDefinition::id).distinct().count() != definitions.size())
                throw new IllegalArgumentException("Duplicate process id");
            tick = this.definitions.stream()
                    .filter(d -> d.stage() == ProcessDefinition.Stage.TICK)
                    .toList();
            cadence = this.definitions.stream()
                    .filter(d -> d.stage() == ProcessDefinition.Stage.CADENCE)
                    .toList();
        }

        public List<ProcessDefinition> definitions() {
            return definitions;
        }

        public List<ProcessDefinition> definitions(ProcessDefinition.Stage stage) {
            return stage == ProcessDefinition.Stage.TICK ? tick : cadence;
        }
    }

    private final Map<ResourceLocation, Profile> profiles = new HashMap<>();
    private boolean frozen;

    public void register(ResourceLocation mob, Profile profile) {
        if (frozen) throw new IllegalStateException("Frozen registry");
        if (profiles.putIfAbsent(mob, profile) != null) throw new IllegalArgumentException("Duplicate mob");
    }

    public MobProcessRegistry freeze() {
        frozen = true;
        return this;
    }

    public Profile resolve(ResourceLocation mob) {
        return profiles.get(mob);
    }

    private long revision;

    public long revision() {
        return revision;
    }

    void replace(Map<ResourceLocation, Profile> loaded) {
        profiles.clear();
        profiles.putAll(loaded);
        ++revision;
    }

    public static final MobProcessRegistry DEFAULT = new MobProcessRegistry().freeze();
}
