package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.*;
import net.impactvector.mobvats.gamecontent.mob.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class GenericCaptureGameTests {
    private static Path directory() throws Exception {
        Files.createDirectories(Path.of("build"));
        return Files.createTempDirectory(Path.of("build"), "slice9-");
    }

    private static PassiveMobDefinitionRegistry.Loaded snapshot() {
        return new PassiveMobDefinitionRegistry.Loaded(PassiveMobDefinitionRegistry.definitions(), Map.of(), List.of());
    }

    private static JsonObject definition(String entity) {
        var p = new JsonObject();
        p.addProperty("entity", entity);
        p.add(
                "capture",
                JsonParser.parseString(
                        "{\"enabled\":true,\"cost\":{\"items\":[\"minecraft:glass_bottle\"],\"count\":1}}"));
        p.add("processes", new JsonArray());
        return p;
    }

    private static PassiveMobDefinitionRegistry.Loaded load(String id, JsonObject definition) throws Exception {
        var dir = directory();
        var root = new JsonObject();
        root.add(id, definition);
        Files.writeString(dir.resolve("90_override.json"), root.toString());
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        if (!loaded.errors().isEmpty())
            throw new IllegalStateException(loaded.errors().toString());
        return loaded;
    }

    private static Player player(GameTestHelper h, boolean creative, int bottles) {
        var p = h.makeMockPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        p.getAbilities().instabuild = creative;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.SYRINGE.get()));
        if (bottles > 0) p.getInventory().setItem(1, new ItemStack(Items.GLASS_BOTTLE, bottles));
        return p;
    }

    private static ItemStack essence(Player p) {
        return p.getInventory().items.stream()
                .filter(s -> s.is(Content.ESSENCE.get()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static int bottles(Player p) {
        return p.getInventory().countItem(Items.GLASS_BOTTLE);
    }

    private static InteractionResult capture(Player p, LivingEntity target) {
        return Content.SYRINGE.get().interactLivingEntity(p.getMainHandItem(), p, target, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fullCatalogMatchesInstantiatedVanillaMobsAndDefaultGates(GameTestHelper h) throws Exception {
        var dir = directory();
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        var expected = new HashSet<ResourceLocation>();
        for (var type : BuiltInRegistries.ENTITY_TYPE)
            if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                    && type.create(h.getLevel()) instanceof Mob)
                expected.add(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        h.assertTrue(
                loaded.errors().isEmpty() && loaded.definitions().keySet().equals(expected),
                "Catalog differs from all vanilla Mob types: " + loaded.errors() + " expected=" + expected.size()
                        + " actual=" + loaded.definitions().size());
        for (var name : List.of(
                "cow",
                "sheep",
                "pig",
                "chicken",
                "axolotl",
                "cod",
                "allay",
                "villager",
                "zombie",
                "skeleton",
                "creeper"))
            h.assertTrue(
                    loaded.definitions()
                            .get(ResourceLocation.withDefaultNamespace(name))
                            .capture()
                            .enabled(),
                    "Passive capture disabled: " + name);
        for (var name : List.of("wither", "ender_dragon", "warden", "elder_guardian"))
            h.assertTrue(
                    !loaded.definitions()
                            .get(ResourceLocation.withDefaultNamespace(name))
                            .capture()
                            .enabled(),
                    "Progression accidentally enabled: " + name);
        var text = Files.readString(dir.resolve("_defaults.json"));
        h.assertTrue(
                JsonParser.parseString(text).getAsJsonObject().size() == expected.size(),
                "Duplicate/missing generated entries");
        h.assertTrue(
                loaded.definitions()
                                        .get(ResourceLocation.withDefaultNamespace("sniffer"))
                                        .juvenile_growth_ticks()
                                == 48000
                        && !loaded.definitions()
                                .get(ResourceLocation.withDefaultNamespace("turtle"))
                                .lifecycle(),
                "Special reproduction/growth defaults incorrect");
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "SLICE9 CATALOG: {} vanilla mobs; {} lifecycle; {} enabled; {} gated",
                        expected.size(),
                        loaded.definitions().values().stream()
                                .filter(PassiveMobDefinition::lifecycle)
                                .count(),
                        loaded.definitions().values().stream()
                                .filter(d -> d.capture().enabled())
                                .count(),
                        loaded.definitions().values().stream()
                                .filter(d -> !d.capture().enabled())
                                .count());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void cowSheepAndAquaticCaptureThroughSyringe(GameTestHelper h) throws Exception {
        var original = snapshot();
        try {
            PassiveMobDefinitionRegistry.publish(PassiveMobDefinitionRegistry.load(directory(), s -> {}));
            for (var type : List.of(EntityType.COW, EntityType.SHEEP, EntityType.COD)) {
                var p = player(h, false, 2);
                var target = (LivingEntity) type.create(h.getLevel());
                h.assertTrue(
                        capture(p, target) == InteractionResult.CONSUME && bottles(p) == 1,
                        "Generic capture failed: " + type);
                var saved = CapturedSpecimen.load(
                        essence(p).get(DataComponents.CUSTOM_DATA).copyTag(), Direction.NORTH);
                h.assertTrue(
                        saved.identity().equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))
                                && saved.geometry().equals(SimulacrumGeometry.capture(target)),
                        "Identity/geometry not persisted");
            }
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void disabledDeadBabyWrongToolAndInsufficientCostsConsumeNothing(GameTestHelper h) {
        var p = player(h, false, 2);
        var zombie = EntityType.WITHER.create(h.getLevel());
        h.assertTrue(
                capture(p, zombie) == InteractionResult.FAIL
                        && bottles(p) == 2
                        && essence(p).isEmpty(),
                "Disabled capture consumed cost");
        var cow = EntityType.COW.create(h.getLevel());
        cow.setBaby(true);
        h.assertTrue(capture(p, cow) == InteractionResult.FAIL && bottles(p) == 2, "Baby cost consumed");
        cow.setBaby(false);
        cow.setHealth(0);
        h.assertTrue(capture(p, cow) == InteractionResult.FAIL && bottles(p) == 2, "Dead capture consumed");
        cow.setHealth(10);
        h.assertTrue(CaptureTransaction.eligibility(new ItemStack(Items.STICK), cow) != null, "Tool gate ignored");
        p = player(h, false, 0);
        h.assertTrue(capture(p, cow) == InteractionResult.FAIL && essence(p).isEmpty(), "Unpaid capture succeeded");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void costAcrossSlotsHealthAndFullInventoryAreAtomic(GameTestHelper h) throws Exception {
        var original = snapshot();
        try {
            var d = definition("minecraft:sheep");
            d.getAsJsonObject("capture").addProperty("health_cost", 3);
            d.getAsJsonObject("capture").getAsJsonObject("cost").addProperty("count", 3);
            PassiveMobDefinitionRegistry.publish(load("minecraft:sheep", d));
            var p = player(h, false, 2);
            p.getInventory().setItem(2, new ItemStack(Items.GLASS_BOTTLE, 2));
            p.setHealth(20);
            h.assertTrue(
                    capture(p, EntityType.SHEEP.create(h.getLevel())) == InteractionResult.CONSUME
                            && bottles(p) == 1
                            && p.getHealth() == 17
                            && !essence(p).isEmpty(),
                    "Multi-slot/health transaction failed");
            p = player(h, false, 4);
            p.setHealth(3);
            h.assertTrue(
                    capture(p, EntityType.SHEEP.create(h.getLevel())) == InteractionResult.FAIL && bottles(p) == 4,
                    "Nonlethal health precondition failed");
            p = player(h, false, 4);
            p.setHealth(20);
            for (int i = 2; i < p.getInventory().items.size(); i++)
                p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            h.assertTrue(
                    capture(p, EntityType.SHEEP.create(h.getLevel())) == InteractionResult.FAIL
                            && bottles(p) == 4
                            && p.getHealth() == 20,
                    "Full inventory partly committed costs");
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void invalidGeometryAndMissingProfileConsumeNothing(GameTestHelper h) {
        var p = player(h, false, 2);
        var cow = EntityType.COW.create(h.getLevel());
        cow.setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
        h.assertTrue(
                capture(p, cow) == InteractionResult.FAIL
                        && bottles(p) == 2
                        && essence(p).isEmpty(),
                "Invalid geometry consumed cost");
        h.assertTrue(
                capture(p, CaptureTestEntity.TYPE.create(h.getLevel())) == InteractionResult.FAIL && bottles(p) == 2,
                "Missing fixture profile consumed cost");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void creativeCaptureBypassesCostsAndHealth(GameTestHelper h) {
        var p = player(h, true, 0);
        h.assertTrue(
                capture(p, EntityType.COW.create(h.getLevel())) == InteractionResult.CONSUME
                        && !essence(p).isEmpty()
                        && bottles(p) == 0,
                "Creative capture requires costs");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void moddedFixtureAndHostileOverrideCaptureWithoutLifecycle(GameTestHelper h) throws Exception {
        var original = snapshot();
        try {
            for (var id : List.of(CaptureTestEntity.ID, ResourceLocation.withDefaultNamespace("zombie"))) {
                var d = definition(id.toString());
                d.add(
                        "processes",
                        JsonParser.parseString(
                                "[{\"id\":\"pack:gold\",\"duration_ticks\":600,\"inputs\":[{\"type\":\"item\",\"items\":[\"#c:ingots/iron\"],\"count\":1}],\"outputs\":[{\"type\":\"item\",\"item\":\"minecraft:gold_ingot\",\"count\":4}]}]"));
                var loaded = load(id.toString(), d);
                PassiveMobDefinitionRegistry.publish(loaded);
                var p = player(h, false, 2);
                var target =
                        (LivingEntity) BuiltInRegistries.ENTITY_TYPE.get(id).create(h.getLevel());
                h.assertTrue(
                        capture(p, target) == InteractionResult.CONSUME
                                && ItemEssence.identity(essence(p)).equals(id)
                                && !loaded.definitions().get(id).lifecycle()
                                && MobProcessRegistry.DEFAULT
                                                .resolve(id)
                                                .definitions()
                                                .size()
                                        == 3,
                        "Custom capture/fixed-process override required species code");
                var encoded = new VatHeadEntity(BlockPos.ZERO, Content.state(VatPartType.Head));
                ItemEssence.encode(essence(p), encoded);
                h.assertTrue(
                        encoded.getIdentity().equals(id)
                                && encoded.geometry()
                                        .equals(CapturedSpecimen.load(
                                                        essence(p)
                                                                .get(DataComponents.CUSTOM_DATA)
                                                                .copyTag(),
                                                        Direction.NORTH)
                                                .geometry()
                                                .withFacing(Direction.NORTH)),
                        "Configured non-Cow Essence did not preserve geometry with placed Head facing");
                if (id.equals(CaptureTestEntity.ID)) {
                    var geometry = CapturedSpecimen.load(
                                    essence(p).get(DataComponents.CUSTOM_DATA).copyTag(), Direction.NORTH)
                            .geometry();
                    h.assertTrue(
                            geometry.width() == 3
                                    && geometry.headOffset().getX() == 1
                                    && geometry.headOffset().getZ() == 0,
                            "Wide fixture lost 7C Head heuristic");
                }
            }
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void variantEssenceHeadPersistenceAndLootContextState(GameTestHelper h) throws Exception {
        var original = snapshot();
        try {
            PassiveMobDefinitionRegistry.publish(PassiveMobDefinitionRegistry.load(directory(), s -> {}));
            var sheep = EntityType.SHEEP.create(h.getLevel());
            sheep.setColor(DyeColor.BLUE);
            sheep.setSheared(true);
            var p = player(h, false, 1);
            h.assertTrue(capture(p, sheep) == InteractionResult.CONSUME, "Variant capture failed");
            var e = essence(p);
            var data = e.get(DataComponents.CUSTOM_DATA).copyTag();
            var saved = CapturedSpecimen.load(data, Direction.NORTH);
            h.assertTrue(
                    saved.state().getByte("Color") == DyeColor.BLUE.getId()
                            && saved.state().getBoolean("Sheared")
                            && !saved.state().contains("Health")
                            && !saved.state().contains("UUID"),
                    "Variant/transient subset incorrect");
            var fresh = EntityType.SHEEP.create(h.getLevel());
            CapturedSpecimen.apply(fresh, saved.state());
            h.assertTrue(
                    fresh.getColor() == DyeColor.BLUE && fresh.isSheared(),
                    "Renderer/loot state reconstruction failed");
            var pos = new BlockPos(2, 2, 2);
            h.setBlock(pos, Content.state(VatPartType.Head));
            var head = (VatHeadEntity) h.getBlockEntity(pos);
            ItemEssence.encode(e, head);
            var persisted = head.saveWithoutMetadata(h.getLevel().registryAccess());
            var restored = new VatHeadEntity(head.getBlockPos(), head.getBlockState());
            restored.loadWithComponents(persisted, h.getLevel().registryAccess());
            h.assertTrue(
                    restored.getIdentity().equals(saved.identity())
                            && restored.geometry().equals(saved.geometry().withFacing(Direction.NORTH))
                            && restored.specimenState().equals(saved.state()),
                    "Encoded Head lost generic specimen or placed facing");
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void geometryOverrideAndReloadPreserveCapturedHeadAndDisableFutureCapture(GameTestHelper h)
            throws Exception {
        var original = snapshot();
        try {
            var id = "minecraft:pig";
            var d = definition(id);
            d.add("simulacrum", JsonParser.parseString("{\"dimensions\":[3,4,2],\"head_offset\":[1,2,0]}"));
            PassiveMobDefinitionRegistry.publish(load(id, d));
            var p = player(h, false, 2);
            capture(p, EntityType.PIG.create(h.getLevel()));
            var e = essence(p);
            var g = CapturedSpecimen.load(e.get(DataComponents.CUSTOM_DATA).copyTag(), Direction.NORTH)
                    .geometry();
            h.assertTrue(g.width() == 3 && g.height() == 4, "Capture ignored geometry override");
            var pos = new BlockPos(2, 2, 2);
            h.setBlock(pos, Content.state(VatPartType.Head));
            var head = (VatHeadEntity) h.getBlockEntity(pos);
            ItemEssence.encode(e, head);
            d.getAsJsonObject("capture").addProperty("enabled", false);
            d.remove("simulacrum");
            PassiveMobDefinitionRegistry.publish(load(id, d));
            h.assertTrue(
                    head.geometry().equals(g.withFacing(Direction.NORTH))
                            && capture(p, EntityType.PIG.create(h.getLevel())) == InteractionResult.FAIL
                            && bottles(p) == 1,
                    "Reload changed stored geometry or failed gate");
            PassiveMobDefinitionRegistry.publish(
                    new PassiveMobDefinitionRegistry.Loaded(Map.of(), Map.of(), List.of()));
            h.assertTrue(
                    head.getIdentity().toString().equals(id)
                            && head.geometry().equals(g.withFacing(Direction.NORTH))
                            && MobProcessRegistry.DEFAULT.resolve(head.getIdentity()) == null,
                    "Missing definition destroyed specimen");
            var empty = new VatHeadEntity(BlockPos.ZERO, Content.state(VatPartType.Head));
            boolean rejected = false;
            try {
                ItemEssence.encode(e, empty);
            } catch (IllegalArgumentException error) {
                rejected = error.getMessage().contains("Definition missing");
            }
            h.assertTrue(
                    rejected
                            && empty.getIdentity() == null
                            && ItemEssence.identity(e).toString().equals(id),
                    "Missing definition silently became Cow");
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void legacyCowEssenceMigratesWithoutRecalculatingHistoricalGeometry(GameTestHelper h) {
        var legacy = new CompoundTag();
        legacy.putString("entity", "minecraft:cow");
        var essence = new ItemStack(Content.ESSENCE.get());
        essence.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        var head = new VatHeadEntity(BlockPos.ZERO, Content.state(VatPartType.Head));
        ItemEssence.encode(essence, head);
        h.assertTrue(
                head.getIdentity().equals(ItemEssence.COW)
                        && head.geometry().equals(SimulacrumGeometry.legacy(ItemEssence.COW, Direction.NORTH)),
                "Legacy Cow Essence migration failed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void vanillaVariantsAndNamesSurviveCaptureWithoutTransientEntityData(GameTestHelper h)
            throws Exception {
        var original = snapshot();
        try {
            PassiveMobDefinitionRegistry.publish(PassiveMobDefinitionRegistry.load(directory(), s -> {}));
            var fixtures = Map.of(
                    "mooshroom",
                    "Type=brown",
                    "rabbit",
                    "RabbitType=5",
                    "frog",
                    "variant=minecraft:cold",
                    "horse",
                    "Variant=259",
                    "tropical_fish",
                    "Variant=65536",
                    "goat",
                    "IsScreamingGoat=true");
            for (var entry : fixtures.entrySet()) {
                var state = new CompoundTag();
                var pair = entry.getValue().split("=", 2);
                if (pair[1].equals("true")) state.putBoolean(pair[0], true);
                else if (pair[1].matches("[0-9]+")) state.putInt(pair[0], Integer.parseInt(pair[1]));
                else state.putString(pair[0], pair[1]);
                var entity = (LivingEntity) BuiltInRegistries.ENTITY_TYPE
                        .get(ResourceLocation.withDefaultNamespace(entry.getKey()))
                        .create(h.getLevel());
                CapturedSpecimen.apply(entity, state);
                entity.setCustomName(net.minecraft.network.chat.Component.literal("Named specimen"));
                var p = player(h, false, 1);
                h.assertTrue(
                        capture(p, entity) == InteractionResult.CONSUME, "Variant capture failed: " + entry.getKey());
                var saved = CapturedSpecimen.load(
                        essence(p).get(DataComponents.CUSTOM_DATA).copyTag(), Direction.NORTH);
                h.assertTrue(
                        saved.state().get(pair[0]).equals(state.get(pair[0]))
                                && saved.state().contains("CustomName")
                                && !saved.state().contains("Pos")
                                && !saved.state().contains("Brain"),
                        "Variant/name not preserved: " + entry.getKey());
                var fresh = (LivingEntity) entity.getType().create(h.getLevel());
                CapturedSpecimen.apply(fresh, saved.state());
                h.assertTrue(
                        fresh.getCustomName() != null
                                && fresh.getCustomName().getString().equals("Named specimen"),
                        "Name reconstruction failed");
            }
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void explicitJuvenileCaptureKeepsStageAndCreativeHealthExemption(GameTestHelper h) throws Exception {
        var original = snapshot();
        try {
            var d = definition("minecraft:cow");
            d.getAsJsonObject("capture").addProperty("adult_only", false);
            d.getAsJsonObject("capture").addProperty("health_cost", 4);
            PassiveMobDefinitionRegistry.publish(load("minecraft:cow", d));
            var baby = EntityType.COW.create(h.getLevel());
            baby.setBaby(true);
            var p = player(h, true, 2);
            p.setHealth(2);
            h.assertTrue(
                    capture(p, baby) == InteractionResult.CONSUME && p.getHealth() == 2 && bottles(p) == 2,
                    "Creative health/item exemption failed");
            var head = new VatHeadEntity(BlockPos.ZERO, Content.state(VatPartType.Head));
            ItemEssence.encode(essence(p), head);
            h.assertTrue(
                    head.lifeStage() == LifeStage.JUVENILE
                            && head.specimenState().getBoolean("Baby"),
                    "Explicit captured baby lost its presentation stage");
        } finally {
            PassiveMobDefinitionRegistry.publish(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void invalidCaptureConfigurationAndUnknownEntityRejectWholeSibling(GameTestHelper h)
            throws Exception {
        var dir = directory();
        var base = definition("minecraft:pig");
        var root = new JsonObject();
        root.add("minecraft:pig", base);
        base.getAsJsonObject("capture").getAsJsonObject("cost").addProperty("count", 0);
        Files.writeString(dir.resolve("10_cost.json"), root.toString());
        base.getAsJsonObject("capture").getAsJsonObject("cost").addProperty("count", 1);
        base.getAsJsonObject("capture").addProperty("tool", "#Bad Tag!");
        Files.writeString(dir.resolve("20_tool.json"), root.toString());
        root = new JsonObject();
        root.add("missing:entity", definition("missing:entity"));
        Files.writeString(dir.resolve("30_entity.json"), root.toString());
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().size() == 3
                        && loaded.errors().stream().anyMatch(s -> s.contains("capture.cost.count"))
                        && loaded.errors().stream().anyMatch(s -> s.contains("Invalid selector ID"))
                        && loaded.errors().stream().anyMatch(s -> s.contains("Unknown entity"))
                        && loaded.sources()
                                .get(ResourceLocation.withDefaultNamespace("pig"))
                                .equals("_defaults.json"),
                "Invalid capture schema/ID poorly diagnosed or partly applied");
        h.succeed();
    }
}
