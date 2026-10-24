package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MobTierDiscoveryGameTests {
    private static final ResourceLocation COW = ResourceLocation.parse("minecraft:cow");
    private static final BlockPos O = new BlockPos(1, 1, 1), HEAD = O.offset(2, 2, 2);

    private static Path directory() throws Exception {
        Files.createDirectories(Path.of("build"));
        return Files.createTempDirectory(Path.of("build"), "mob-discovery-");
    }

    private static ResourceLocation fixture(String name) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(CaptureTestEntity.DISCOVERY.get(name));
    }

    @GameTest(batch = "mobDiscovery", template = "empty", timeoutTicks = 10)
    public static void discoversRegisteredMobsUsingActualBiomeAffinitiesAndBossTags(GameTestHelper h) throws Exception {
        var registry = BuiltInRegistries.ENTITY_TYPE;
        var tags = new HashMap<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>>();
        registry.getTags()
                .forEach(pair ->
                        tags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var dir = directory();
        var diagnostics = new ArrayList<String>();
        try {
            var changed = new HashMap<>(tags);
            var bossTag = net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES;
            var bosses = new ArrayList<>(tags.getOrDefault(bossTag, List.of()));
            bosses.add(registry.wrapAsHolder(CaptureTestEntity.DISCOVERY.get("boss")));
            changed.put(bossTag, bosses);
            var unknown =
                    TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("testpack:unclassified_ability"));
            changed.put(unknown, List.of(registry.wrapAsHolder(CaptureTestEntity.DISCOVERY.get("ambiguous"))));
            registry.bindTags(changed);
            h.assertTrue(
                    net.impactvector.mobvats.gamecontent.item.CaptureTransaction.boss(
                            CaptureTestEntity.DISCOVERY.get("boss")),
                    "Explicit boss capture policy does not recognize tagged modded bosses");
            MobProfileDiscovery.discover(h.getLevel().getServer(), dir, diagnostics::add);
        } finally {
            registry.bindTags(tags);
        }
        var loaded = PassiveMobDefinitionRegistry.load(dir, diagnostics::add);
        h.assertTrue(loaded.errors().isEmpty(), "Generated catalog failed validation: " + loaded.errors());
        h.assertTrue(
                loaded.definitions().get(fixture("passive")).minimum_vat_tier() == 1
                        && loaded.definitions().get(CaptureTestEntity.ID).minimum_vat_tier() == 2
                        && loaded.definitions().get(fixture("nether")).minimum_vat_tier() == 3
                        && loaded.definitions().get(fixture("end")).minimum_vat_tier() == 3
                        && loaded.definitions().get(fixture("ambiguous")).minimum_vat_tier() == 3,
                "Registered biome/category classification incorrect: " + diagnostics);
        h.assertTrue(
                !Files.exists(MobProfileFixtures.file(dir, fixture("boss").toString()))
                        && !loaded.definitions().containsKey(fixture("boss"))
                        && diagnostics.stream().anyMatch(s -> s.contains("boss excluded"))
                        && diagnostics.stream()
                                .anyMatch(s -> s.contains("unclassified tag=testpack:unclassified_ability")),
                "Boss or unknown-tag diagnostics missing");
        for (var name : List.of("passive", "nether", "end", "ambiguous")) {
            var definition = loaded.definitions().get(fixture(name));
            h.assertTrue(
                    !definition.lifecycle()
                            && definition.processes().isEmpty()
                            && definition.toolInteractions().isEmpty()
                            && definition.schema_version() == MobProfileSchema.CURRENT_VERSION,
                    "Discovery invented unsupported lifecycle or Tool behavior");
        }
        h.assertTrue(
                loaded.sources().get(fixture("nether")).contains("biome spawn affinity=nether"),
                "Inspection lost classification provenance");
        h.succeed();
    }

    @GameTest(batch = "mobDiscovery", template = "empty", timeoutTicks = 10)
    public static void refreshPreservesAuthoredMalformedAndAbsentModFilesAndCreatesNewProfiles(GameTestHelper h)
            throws Exception {
        var dir = directory();
        MobProfileDiscovery.discover(h.getLevel().getServer(), dir, s -> {});
        var passive = MobProfileFixtures.file(dir, fixture("passive").toString());
        var document = JsonParser.parseString(Files.readString(passive)).getAsJsonObject();
        document.addProperty("minimum_vat_tier", 3);
        document.getAsJsonObject("capture").addProperty("enabled", false);
        Files.writeString(passive, document.toString());
        var authored = Files.readAllBytes(passive);
        var nether = MobProfileFixtures.file(dir, fixture("nether").toString());
        Files.writeString(nether, "{malformed");
        var missing = MobProfileFixtures.file(dir, "absent_mod:creatures/pet");
        Files.createDirectories(missing.getParent());
        Files.writeString(
                missing, "{\"schema_version\":2,\"entity\":\"absent_mod:creatures/pet\",\"minimum_vat_tier\":3}");
        var missingBefore = Files.readAllBytes(missing);
        var end = MobProfileFixtures.file(dir, fixture("end").toString());
        Files.delete(end);
        MobProfileDiscovery.discover(h.getLevel().getServer(), dir, s -> {});
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                Arrays.equals(authored, Files.readAllBytes(passive))
                        && loaded.definitions().get(fixture("passive")).minimum_vat_tier() == 3
                        && !loaded.definitions()
                                .get(fixture("passive"))
                                .capture()
                                .enabled()
                        && Files.readString(nether).equals("{malformed")
                        && !loaded.definitions().containsKey(fixture("nether"))
                        && Arrays.equals(missingBefore, Files.readAllBytes(missing))
                        && Files.isRegularFile(end)
                        && loaded.definitions().get(fixture("end")).minimum_vat_tier() == 3,
                "Refresh overwrote authored/invalid/unavailable data or failed to generate a new species");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void conservativeClassificationAndConfiguredTierMatrix(GameTestHelper h) {
        var other = ResourceLocation.parse("example:unknown");
        h.assertTrue(
                MobTierClassification.classify(other, MobCategory.MONSTER, Set.of(), Set.of())
                                .ambiguous()
                        && MobTierClassification.classify(other, MobCategory.MONSTER, Set.of(), Set.of())
                                        .tier()
                                == 3
                        && MobTierClassification.classify(
                                                other,
                                                MobCategory.CREATURE,
                                                Set.of("mobvats:teleporting_mobs"),
                                                Set.of("overworld"))
                                        .tier()
                                == 3
                        && MobTierClassification.classify(
                                        other, MobCategory.MONSTER, Set.of("c:bosses"), Set.of("overworld"))
                                .excluded()
                        && MobTierClassification.classify(
                                                ResourceLocation.parse("minecraft:enderman"),
                                                MobCategory.MONSTER,
                                                Set.of(),
                                                Set.of("overworld"))
                                        .tier()
                                == 3,
                "Containment exceptions or conservative fallback incorrect");
        var original = new PassiveMobDefinitionRegistry.Loaded(
                PassiveMobDefinitionRegistry.definitions(), Map.of(), List.of());
        try {
            for (int required = 1; required <= 3; required++) {
                var changed = new HashMap<>(original.definitions());
                changed.put(COW, PassiveDefaults.data().get(COW.toString()).withMinimumVatTier(required));
                PassiveMobDefinitionRegistry.publish(
                        new PassiveMobDefinitionRegistry.Loaded(changed, Map.of(), List.of()));
                for (var tier : VatTier.values())
                    h.assertTrue(
                            MobEligibility.allows(COW, tier) == (tier.ordinal() + 1 >= required),
                            "Tier matrix ignores the profile: required=" + required + " actual=" + tier);
            }
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    private static void liveReload(GameTestHelper h, VatTier tier) throws Exception {
        MachineFixtures.vat(
                (pos, state) -> {
                    for (var type : List.of(VatPartType.Casing, VatPartType.Glass, VatPartType.Controller))
                        if (state.is(Content.block(type))) {
                            h.setBlock(pos, Content.block(tier, type).defaultBlockState());
                            return;
                        }
                    h.setBlock(
                            pos,
                            state.hasProperty(CladdingMaterial.PROPERTY)
                                    ? state.setValue(CladdingMaterial.PROPERTY, tier.cladding(false))
                                    : state);
                },
                O,
                5,
                5,
                4);
        var head = (VatHeadEntity) h.getBlockEntity(HEAD);
        head.encode(COW);
        var dir = directory();
        var file = MobProfileFixtures.file(dir, COW.toString());
        var original = new PassiveMobDefinitionRegistry.Loaded(
                PassiveMobDefinitionRegistry.definitions(), Map.of(), List.of());
        h.runAtTickTime(30, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            try {
                h.assertTrue(vat.isAssembled(), "Initial Vat did not form at " + tier);
                var identity = head.specimenId();
                var geometry = head.geometry();
                var saved = head.saveWithFullMetadata(h.getLevel().registryAccess());
                var restored = new VatHeadEntity(BlockPos.ZERO, Content.state(VatPartType.Head));
                restored.loadWithComponents(saved, h.getLevel().registryAccess());
                h.assertTrue(
                        restored.specimenId().equals(identity)
                                && restored.getIdentity().equals(COW),
                        "Specimen persistence changed identity before reload");
                var profile = PassiveDefaults.data().get(COW.toString());
                MobProfileFixtures.write(
                        dir,
                        PassiveMobDefinitionRegistry.JSON
                                .toJsonTree(Map.of(COW.toString(), profile.withMinimumVatTier(tier.ordinal() + 2)))
                                .getAsJsonObject());
                var blocked = PassiveMobDefinitionRegistry.load(dir, s -> {});
                PassiveMobDefinitionRegistry.publish(blocked);
                MultiblockVat.profilesReloaded(h.getLevel().getServer());
                h.assertTrue(
                        !vat.isAssembled()
                                && head.specimenId().equals(identity)
                                && head.geometry().equals(geometry),
                        "Reload grandfathered an invalid assembly or replaced its specimen at " + tier);
                MobProfileFixtures.write(
                        dir,
                        PassiveMobDefinitionRegistry.JSON
                                .toJsonTree(Map.of(COW.toString(), profile.withMinimumVatTier(tier.ordinal() + 1)))
                                .getAsJsonObject());
                var repaired = PassiveMobDefinitionRegistry.load(dir, s -> {});
                PassiveMobDefinitionRegistry.publish(repaired);
                MultiblockVat.profilesReloaded(h.getLevel().getServer());
                h.assertTrue(
                        vat.isAssembled()
                                && head.specimenId().equals(identity)
                                && head.geometry().equals(geometry)
                                && MobEligibility.allows(restored.getIdentity(), tier),
                        "Reload did not reform the existing specimen at " + tier + ": " + vat.getLastError());
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                PassiveMobDefinitionRegistry.publish(original);
                MultiblockVat.profilesReloaded(h.getLevel().getServer());
            }
            h.succeed();
        });
    }

    @GameTest(batch = "mobTierReload", template = "empty", timeoutTicks = 80)
    public static void copperVatRevalidatesAlreadyEncodedSpecimenOnProfileReload(GameTestHelper h) throws Exception {
        liveReload(h, VatTier.T1);
    }

    @GameTest(batch = "mobTierReload", template = "empty", timeoutTicks = 80)
    public static void ironVatRevalidatesAlreadyEncodedSpecimenOnProfileReload(GameTestHelper h) throws Exception {
        liveReload(h, VatTier.T2);
    }

    @GameTest(batch = "mobTierReload", template = "empty", timeoutTicks = 80)
    public static void bloodIronVatRejectsBadFileAndRecoversExistingSpecimen(GameTestHelper h) throws Exception {
        liveReload(h, VatTier.T3);
    }

    private MobTierDiscoveryGameTests() {}
}
