package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.*;

/** Runs only at server startup/refresh. Temporary entities are never spawned or captured. */
public final class MobProfileDiscovery {
    public static void discover(MinecraftServer server, Path directory, Consumer<String> diagnostic) {
        var affinities = new HashMap<EntityType<?>, Set<String>>();
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var biome : biomes.holders().toList()) {
            var regions = new HashSet<String>();
            if (biome.is(BiomeTags.IS_NETHER) || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_NETHER))
                regions.add("nether");
            if (biome.is(BiomeTags.IS_END) || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_END))
                regions.add("end");
            if (biome.is(BiomeTags.IS_OVERWORLD) || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_OVERWORLD))
                regions.add("overworld");
            if (regions.isEmpty()) regions.add("unclassified");
            for (var category : MobCategory.values())
                for (var spawn :
                        biome.value().getMobSettings().getMobs(category).unwrap())
                    affinities
                            .computeIfAbsent(spawn.type, ignored -> new HashSet<>())
                            .addAll(regions);
        }
        var vanilla = PassiveDefaults.data();
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            try {
                var file = PassiveMobDefinitionRegistry.path(directory, id);
                // Do not instantiate, infer or refresh an already authored species, even if invalid.
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
                var tags = type.builtInRegistryHolder()
                        .tags()
                        .map(tag -> tag.location().toString())
                        .collect(java.util.stream.Collectors.toSet());
                var classification = MobTierClassification.classify(
                        id, type.getCategory(), tags, affinities.getOrDefault(type, Set.of()));
                if (classification.excluded()) {
                    diagnostic.accept(id + ": " + String.join("; ", classification.reasons()));
                    continue;
                }
                Entity probe = null;
                try {
                    probe = type.create(server.overworld());
                    if (!(probe instanceof Mob)) continue;
                    var definition = vanilla.get(id.toString());
                    if (definition == null) {
                        var capture = new PassiveMobDefinition.Capture(
                                true,
                                "mobvats:syringe",
                                new PassiveMobDefinition.CaptureCost(List.of("minecraft:glass_bottle"), 1),
                                0,
                                true,
                                true,
                                false,
                                true,
                                true,
                                "generated representation only; lifecycle, special abilities and Tool interactions require explicit configuration");
                        definition = new PassiveMobDefinition(
                                id.toString(),
                                null,
                                0,
                                List.of(),
                                null,
                                null,
                                null,
                                new PassiveMobDefinition.Simulacrum(null, null),
                                Map.of(),
                                List.of(),
                                capture,
                                List.of());
                    }
                    var size = type.getDimensions();
                    net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.resolve(
                            size.width(),
                            size.height(),
                            size.width(),
                            net.minecraft.core.Direction.NORTH,
                            definition.simulacrum());
                    var document = PassiveMobDefinitionRegistry.JSON
                            .toJsonTree(definition.withMinimumVatTier(classification.tier()))
                            .getAsJsonObject();
                    document.add("discovery", PassiveMobDefinitionRegistry.JSON.toJsonTree(classification));
                    create(directory, id, document);
                    diagnostic.accept(id + ": generated T" + classification.tier()
                            + (classification.ambiguous() ? " (ambiguous; review required)" : "")
                            + "; " + String.join("; ", classification.reasons()));
                } finally {
                    if (probe != null) probe.discard();
                }
            } catch (Exception error) {
                diagnostic.accept(id + ": discovery unavailable; no profile replaced: " + error.getMessage());
            }
        }
    }

    private static void create(Path directory, net.minecraft.resources.ResourceLocation id, JsonObject document)
            throws java.io.IOException {
        var file = PassiveMobDefinitionRegistry.path(directory, id);
        var cursor = file;
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) throw new java.io.IOException("Linked profile path: " + cursor);
            cursor = cursor.getParent();
        }
        Files.createDirectories(file.getParent());
        try {
            Files.writeString(
                    file,
                    PassiveMobDefinitionRegistry.JSON.toJson(document) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
            // Another writer wins. Never replace it.
        }
    }

    private MobProfileDiscovery() {}
}
