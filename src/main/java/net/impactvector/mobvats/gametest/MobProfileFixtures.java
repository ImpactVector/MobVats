package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.nio.file.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.resources.ResourceLocation;

/** Test documents use the production file contract, never bundle/override semantics. */
final class MobProfileFixtures {
    static void write(Path directory, JsonObject catalog) throws Exception {
        for (var entry : catalog.entrySet()) {
            var file = PassiveMobDefinitionRegistry.path(directory, ResourceLocation.parse(entry.getKey()));
            Files.createDirectories(file.getParent());
            var document = entry.getValue().getAsJsonObject().deepCopy();
            if (!document.has("schema_version"))
                document.addProperty("schema_version", MobProfileSchema.CURRENT_VERSION);
            if (!document.has("minimum_vat_tier")) document.addProperty("minimum_vat_tier", 1);
            Files.writeString(file, document.toString());
        }
    }

    static Path file(Path directory, String entity) {
        return PassiveMobDefinitionRegistry.path(directory, ResourceLocation.parse(entity));
    }

    private MobProfileFixtures() {}
}
