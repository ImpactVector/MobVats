package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatTier;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MobProfileSchemaGameTests {
    private static final ResourceLocation COW = ResourceLocation.parse("minecraft:cow");

    private static Path directory() throws Exception {
        Files.createDirectories(Path.of("build"));
        return Files.createTempDirectory(Path.of("build"), "mob-schema-");
    }

    private static JsonObject cow() {
        return PassiveMobDefinitionRegistry.JSON
                .toJsonTree(PassiveDefaults.data().get(COW.toString()))
                .getAsJsonObject();
    }

    private static Path write(Path dir, JsonObject document) throws Exception {
        var file = MobProfileFixtures.file(dir, document.get("entity").getAsString());
        Files.createDirectories(file.getParent());
        Files.writeString(file, document.toString());
        return file;
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void upgradePreservesAuthoredAndUnknownDataWithExactBackup(GameTestHelper h) throws Exception {
        var dir = directory();
        var document = cow();
        document.addProperty("schema_version", 1);
        document.remove("minimum_vat_tier");
        document.addProperty("juvenile_growth_ticks", 73);
        document.add(
                "editor_metadata", JsonParser.parseString("{\"owner\":\"operator\",\"future\":{\"values\":[1,true]}}"));
        var file = write(dir, document);
        var before = Files.readAllBytes(file);
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().isEmpty()
                        && loaded.definitions().get(COW).juvenile_growth_ticks() == 73
                        && loaded.definitions().get(COW).minimum_vat_tier() == 1,
                "Upgrade lost authored data");
        var upgraded = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        h.assertTrue(
                upgraded.get("schema_version").getAsInt() == MobProfileSchema.CURRENT_VERSION
                        && upgraded.get("editor_metadata").equals(document.get("editor_metadata")),
                "Unknown data lost");
        try (var files = Files.list(file.getParent())) {
            var backups = files.filter(p -> p.getFileName().toString().startsWith("cow.json.v1.")
                            && p.toString().endsWith(".bak"))
                    .toList();
            h.assertTrue(
                    backups.size() == 1 && Arrays.equals(before, Files.readAllBytes(backups.getFirst())),
                    "Original backup differs");
        }
        var current = Files.readAllBytes(file);
        PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(Arrays.equals(current, Files.readAllBytes(file)), "Current schema was rewritten on reload");
        document.addProperty("minimum_vat_tier", 3);
        h.assertTrue(
                MobProfileSchema.upgrade(document).get("minimum_vat_tier").getAsInt() == 3,
                "Migration reset an authored tier");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void invalidAndFutureSchemasRemainUntouchedDisabledAndUnbacked(GameTestHelper h) throws Exception {
        for (int scenario = 0; scenario < 7; scenario++) {
            var dir = directory();
            var document = cow();
            switch (scenario) {
                case 0 -> document.addProperty("schema_version", MobProfileSchema.CURRENT_VERSION + 1);
                case 1 -> document.addProperty("schema_version", 1.5);
                case 2 -> document.remove("schema_version");
                case 3 -> document.addProperty("minimum_vat_tier", 4);
                case 4 -> {
                    document.addProperty("schema_version", 1);
                    document.remove("minimum_vat_tier");
                    document.addProperty("juvenile_growth_ticks", -1);
                }
                case 5 -> document.addProperty("minimum_vat_tier", "2");
                case 6 -> document.addProperty("minimum_vat_tier", 0);
            }
            var file = write(dir, document);
            var before = Files.readAllBytes(file);
            var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
            h.assertTrue(
                    !loaded.definitions().containsKey(COW)
                            && loaded.errors().stream().anyMatch(e -> e.contains("minecraft/cow.json"))
                            && Arrays.equals(before, Files.readAllBytes(file)),
                    "Invalid schema changed or loaded: " + scenario);
            try (var files = Files.list(file.getParent())) {
                h.assertTrue(files.noneMatch(p -> p.toString().endsWith(".bak")), "Invalid file upgraded");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void duplicateAndUnavailableFilesRetainOriginals(GameTestHelper h) throws Exception {
        var dir = directory();
        var document = cow();
        document.addProperty("schema_version", 1);
        document.remove("minimum_vat_tier");
        var file = write(dir, document);
        var before = Files.readString(file);
        Files.writeString(dir.resolve("alias.json"), document.toString());
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                !loaded.definitions().containsKey(COW)
                        && loaded.errors().stream().anyMatch(e -> e.contains("Duplicate entity"))
                        && before.equals(Files.readString(file)),
                "Duplicate entity accepted or migrated");
        var unavailable = cow();
        unavailable.addProperty("entity", "missing_mod:creatures/nested_mob");
        var missing = write(dir, unavailable);
        var missingBefore = Files.readAllBytes(missing);
        loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().stream()
                                .anyMatch(e -> e.contains("missing_mod/creatures/nested_mob.json")
                                        && e.contains("Unknown entity"))
                        && Arrays.equals(missingBefore, Files.readAllBytes(missing)),
                "Missing mod definition removed or rewritten");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void pathsAndCompatibilityPolicyAreIndependentOfCaptureAndTierData(GameTestHelper h)
            throws Exception {
        var dir = directory();
        h.assertTrue(
                MobProfileFixtures.file(dir, "somepack:creatures/cow").endsWith("somepack/creatures/cow.json"),
                "Nested path not preserved");
        for (var id : List.of("somepack:../cow", "somepack:creatures/../../cow", "somepack:a//b", "somepack:con")) {
            boolean rejected = false;
            try {
                MobProfileFixtures.file(dir, id);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            h.assertTrue(rejected, "Unsafe path accepted: " + id);
        }
        var document = cow();
        document.addProperty("minimum_vat_tier", 3);
        write(dir, document);
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.definitions().get(COW).minimum_vat_tier() == 3
                        && MobEligibility.allows(COW, VatTier.T1)
                        && !MobEligibility.allows(ResourceLocation.parse("minecraft:zombie"), VatTier.T1)
                        && MobEligibility.allows(ResourceLocation.parse("minecraft:zombie"), VatTier.T2),
                "Slice 1 changed tier enforcement");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void catalogPublishesCompiledProfilesAndDefinitionsTogether(GameTestHelper h) throws Exception {
        var original = new PassiveMobDefinitionRegistry.Loaded(
                PassiveMobDefinitionRegistry.definitions(), Map.of(), List.of());
        var revision = MobProcessRegistry.DEFAULT.revision();
        try {
            var dir = directory();
            var old = PassiveMobDefinitionRegistry.definitions();
            var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
            h.assertTrue(
                    MobProcessRegistry.DEFAULT.revision() == revision
                            && PassiveMobDefinitionRegistry.definitions() == old,
                    "Load published a partial catalog");
            PassiveMobDefinitionRegistry.publish(loaded);
            h.assertTrue(
                    MobProcessRegistry.DEFAULT.revision() == revision + 1
                            && PassiveMobDefinitionRegistry.definitions().equals(loaded.definitions())
                            && MobProcessRegistry.DEFAULT.resolve(COW) != null,
                    "Catalog publication incomplete");
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    private MobProfileSchemaGameTests() {}
}
