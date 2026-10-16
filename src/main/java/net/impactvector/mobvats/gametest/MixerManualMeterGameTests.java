package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.menu.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerManualMeterGameTests {
    private static final BlockPos ITEM = new BlockPos(2, 2, 1), FLUID = new BlockPos(2, 3, 1);

    private static AbstractMixerMeterEntity meter(GameTestHelper h, BlockPos pos) {
        return (AbstractMixerMeterEntity) h.getBlockEntity(pos);
    }

    private static void setup(GameTestHelper h, boolean item, boolean fluid, Consumer<MultiblockMixer> test) {
        MixerGameTests.rig(h);
        if (item) h.setBlock(ITEM, MixerContent.state(MixerPartType.ItemMeter));
        if (fluid) h.setBlock(FLUID, MixerContent.state(MixerPartType.FluidMeter));
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(new BlockPos(1, 2, 3)))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(c.isAssembled(), "Meters prevented formation: " + c.getLastError());
            c.setMachineActive(false);
            test.accept(c);
            h.succeed();
        });
    }

    private static JsonObject recipe(int items, int water) {
        return JsonParser.parseString("""
            {"batch_ticks":20,"strategies":[{"id":"default","items":%s,"fluids":%s}],
             "outputs":{"items":[{"item":"minecraft:iron_ingot","amount":1}]}}
            """.formatted(
                                items == 0 ? "[]" : "[{\"selector\":\"minecraft:wheat\",\"amount\":" + items + "}]",
                                water == 0 ? "[]" : "[{\"selector\":\"minecraft:water\",\"amount\":" + water + "}]"))
                .getAsJsonObject();
    }

    private static void recipes(Map<String, JsonObject> recipes, Runnable test) {
        var before = MixerRecipeRegistry.snapshot();
        var root = new JsonObject();
        var values = new JsonObject();
        recipes.forEach(values::add);
        root.add("recipes", values);
        try {
            MixerRecipeRegistry.publish(MixerRecipeRegistry.parse(root));
            test.run();
        } finally {
            MixerRecipeRegistry.publish(before);
        }
    }

    private static void fill(MixerState s) {
        s.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 32), false);
        s.fluids(true).fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
    }

    private static void selected(GameTestHelper h, MixerState s, String id) {
        h.assertTrue(s.select().orElseThrow().recipe().equals(id), "Unexpected recipe selection: " + id);
    }

    private static void configured(GameTestHelper h, BlockPos pos, String filter, String amount) {
        meter(h, pos).filter(filter);
        h.assertTrue(meter(h, pos).quantity(amount), "Valid quantity rejected");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void itemMeterIsRegisteredShellControlWithoutStorage(GameTestHelper h) {
        setup(h, true, false, c -> {
            h.assertTrue(
                    meter(h, ITEM) instanceof MixerItemMeterEntity
                            && MixerContent.block(MixerPartType.ItemMeter).asItem() != Items.AIR,
                    "Item Meter registration failed");
            h.assertTrue(
                    h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(ITEM), null) == null
                            && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(ITEM), null)
                                    == null,
                    "Meter exposed storage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fluidMeterIsRegisteredShellControlWithoutStorage(GameTestHelper h) {
        setup(h, false, true, c -> {
            h.assertTrue(
                    meter(h, FLUID) instanceof MixerFluidMeterEntity
                            && MixerContent.block(MixerPartType.FluidMeter).asItem() != Items.AIR,
                    "Fluid Meter registration failed");
            h.assertTrue(
                    h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(FLUID), null) == null
                            && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FLUID), null)
                                    == null,
                    "Meter exposed storage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void unmeteredMixerKeepsConsumptionAndIdPriority(GameTestHelper h) {
        setup(
                h,
                false,
                false,
                c -> recipes(Map.of("test:z", recipe(4, 0), "test:a", recipe(4, 0), "test:small", recipe(1, 0)), () -> {
                    fill(c.state());
                    selected(h, c.state(), "test:a");
                    h.assertTrue(
                            c.state().select().orElseThrow().commit()
                                    && c.state().plantCount() == 28,
                            "Unmetered consumption changed");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void physicalItemMeterConstrainsNextSelection(GameTestHelper h) {
        setup(
                h,
                true,
                false,
                c -> recipes(Map.of("test:one", recipe(1, 0), "test:four", recipe(4, 0)), () -> {
                    fill(c.state());
                    configured(h, ITEM, "minecraft:wheat", "1");
                    selected(h, c.state(), "test:one");
                    meter(h, ITEM).quantity("4");
                    selected(h, c.state(), "test:four");
                    h.assertTrue(
                            c.state().select().orElseThrow().commit()
                                    && c.state().plantCount() == 28,
                            "Item meter altered consumption");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void physicalFluidMeterConstrainsNextSelection(GameTestHelper h) {
        setup(
                h,
                false,
                true,
                c -> recipes(Map.of("test:small", recipe(0, 500), "test:large", recipe(0, 1000)), () -> {
                    fill(c.state());
                    configured(h, FLUID, "minecraft:water", "500");
                    selected(h, c.state(), "test:small");
                    meter(h, FLUID).quantity("1000");
                    selected(h, c.state(), "test:large");
                    h.assertTrue(
                            c.state().select().orElseThrow().commit()
                                    && c.state().amount(0) == 3000,
                            "Fluid meter altered consumption");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void physicalMetersUseAndAndFailedEligibilityConsumesNothing(GameTestHelper h) {
        setup(
                h,
                true,
                true,
                c -> recipes(Map.of("test:mixed", recipe(4, 1000)), () -> {
                    fill(c.state());
                    configured(h, ITEM, "minecraft:wheat", "4");
                    configured(h, FLUID, "minecraft:water", "500");
                    var before = c.state().save(h.getLevel().registryAccess());
                    h.assertTrue(
                            c.state().select().isEmpty()
                                    && before.equals(c.state().save(h.getLevel().registryAccess())),
                            "Failed AND selection mutated state");
                    meter(h, FLUID).quantity("1000");
                    selected(h, c.state(), "test:mixed");
                    meter(h, ITEM).quantity("3");
                    h.assertTrue(c.state().select().isEmpty(), "Fluid match bypassed item mismatch");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void incompleteAndCorruptMetersBlockFormedMixerWithDiagnostics(GameTestHelper h) {
        setup(
                h,
                true,
                true,
                c -> recipes(Map.of("test:mixed", recipe(4, 1000)), () -> {
                    fill(c.state());
                    configured(h, ITEM, "minecraft:wheat", "4");
                    for (var filter : List.of("", "minecraft:wheat", "#minecraft:water", "missing:fluid", "=invalid")) {
                        var tag = new CompoundTag();
                        tag.putString("meterFilter", filter);
                        tag.putInt("meterQuantity", 1000);
                        meter(h, FLUID).loadConfiguration(tag);
                        h.assertTrue(
                                c.state().select().isEmpty()
                                        && c.diagnosticReport().getString().contains("Next batch blocked"),
                                "Invalid installed filter disappeared");
                    }
                    var tag = new CompoundTag();
                    tag.putString("meterFilter", "minecraft:water");
                    for (int amount : new int[] {0, -1}) {
                        tag.putInt("meterQuantity", amount);
                        meter(h, FLUID).loadConfiguration(tag);
                        h.assertTrue(c.state().select().isEmpty(), "Invalid saved quantity accepted");
                    }
                    tag.putLong("meterQuantity", 4294967297L);
                    meter(h, FLUID).loadConfiguration(tag);
                    h.assertTrue(c.state().select().isEmpty(), "Saved long was truncated into a valid quantity");
                    tag.putDouble("meterQuantity", 1.5);
                    meter(h, FLUID).loadConfiguration(tag);
                    h.assertTrue(c.state().select().isEmpty(), "Saved fraction was truncated into a valid quantity");
                    h.assertTrue(
                            c.isAssembled()
                                    && c.state().plantCount() == 32
                                    && c.state().amount(0) == 4000,
                            "Invalid meter broke formation or consumed resources");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void serverMenuRejectsInvalidQuantitiesAndOutOfRangePlayers(GameTestHelper h) {
        setup(h, true, false, c -> {
            var player = h.makeMockServerPlayerInLevel();
            player.setPos(Vec3.atCenterOf(h.absolutePos(ITEM)));
            var menu = (MixerMeterMenu) meter(h, ITEM).createMenu(7, player.getInventory(), player);
            h.assertTrue(menu.getType() == Content.METER_MENU.get() && menu.slots.isEmpty(), "Wrong meter menu");
            for (var value : List.of("0", "-1", "", "1.5", "abc", "2147483648", "99999999999999999999", " 4", "+4"))
                h.assertTrue(
                        !menu.applyQuantity(player, value) && meter(h, ITEM).quantity() == 1,
                        "Server accepted invalid quantity: " + value);
            h.assertTrue(
                    menu.applyQuantity(player, "2147483647") && menu.quantity() == Integer.MAX_VALUE,
                    "Menu truncated positive int");
            var reopened = new MixerMeterMenu(8, player.getInventory(), meter(h, ITEM));
            h.assertTrue(reopened.quantity() == Integer.MAX_VALUE, "Reopened menu lost stored value");
            player.setPos(Vec3.atCenterOf(h.absolutePos(ITEM)).add(20, 0, 0));
            h.assertTrue(!menu.applyQuantity(player, "2"), "Remote player configured meter");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sampleInteractionAssignsAndClearsWithoutConsumingSamples(GameTestHelper h) {
        setup(h, true, true, c -> {
            var player = h.makeMockServerPlayerInLevel();
            player.setShiftKeyDown(true);
            for (var pos : List.of(ITEM, FLUID)) {
                var held = new ItemStack(pos.equals(ITEM) ? Items.WHEAT : Items.WATER_BUCKET);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                var absolute = h.absolutePos(pos);
                var state = h.getLevel().getBlockState(absolute);
                var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
                state.useItemOn(held, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                h.assertTrue(
                        meter(h, pos).filter().equals(pos.equals(ITEM) ? "minecraft:wheat" : "minecraft:water")
                                && held.getCount() == 1,
                        "Sample interaction failed");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                state.useWithoutItem(h.getLevel(), player, hit);
                h.assertTrue(meter(h, pos).filter().isEmpty(), "Empty-hand clearing failed");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void metersPersistThroughNewEntitiesAndPortableState(GameTestHelper h) {
        setup(h, true, true, c -> {
            configured(h, ITEM, "minecraft:wheat", "4");
            configured(h, FLUID, "minecraft:water", "1000");
            for (var pos : List.of(ITEM, FLUID)) {
                var original = meter(h, pos);
                var registries = h.getLevel().registryAccess();
                var saved = original.saveWithFullMetadata(registries);
                var replacement =
                        original.getPartType().createTileEntity(original.getBlockState(), original.getBlockPos());
                replacement.loadWithComponents(saved, registries);
                var restored = (AbstractMixerMeterEntity) replacement;
                h.assertTrue(
                        restored.configuration().equals(original.configuration()),
                        "New entity load lost meter configuration");
                var portable = EndpointState.capture(
                        new ItemStack(original.getBlockState().getBlock()), original);
                original.filter("");
                original.quantity("9");
                EndpointState.restore(portable, original);
                h.assertTrue(
                        restored.configuration().equals(original.configuration()),
                        "Drop/pick/place state lost meter configuration");
                var packetTag = original.getUpdateTag(registries);
                restored.loadWithComponents(packetTag, registries);
                h.assertTrue(
                        restored.configuration().equals(original.configuration()),
                        "Client update tag lost configuration");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void activeBatchFinishesAndNextBatchUsesChangedMeters(GameTestHelper h) {
        setup(
                h,
                true,
                true,
                c -> recipes(Map.of("test:one", recipe(1, 1000), "test:four", recipe(4, 1000)), () -> {
                    fill(c.state());
                    configured(h, ITEM, "minecraft:wheat", "4");
                    configured(h, FLUID, "minecraft:water", "1000");
                    c.state().cadence();
                    h.assertTrue(
                            c.state().recipe().equals("test:four") && c.state().progress() == 10,
                            "Batch failed to start");
                    meter(h, ITEM).quantity("1");
                    meter(h, FLUID).filter("");
                    var saved = c.state().save(h.getLevel().registryAccess());
                    c.state().load(saved, h.getLevel().registryAccess());
                    c.state().cadence();
                    h.assertTrue(
                            c.state().plantCount() == 28 && c.state().progress() == 0,
                            "Meter edit changed in-flight batch after reload");
                    c.state().cadence();
                    h.assertTrue(
                            c.state().progress() == 0 && c.state().plantCount() == 28,
                            "Incomplete next batch processed");
                    meter(h, FLUID).filter("minecraft:water");
                    c.state().cadence();
                    h.assertTrue(c.state().recipe().equals("test:one"), "Next batch ignored changed item meter");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void physicalMeterKeepsComplexityPriority(GameTestHelper h) {
        setup(h, true, false, c -> {
            var complex = recipe(4, 1000);
            complex.addProperty("priority", -999);
            recipes(Map.of("test:z_complex", complex, "test:a_simple", recipe(4, 0)), () -> {
                fill(c.state());
                configured(h, ITEM, "minecraft:wheat", "4");
                selected(h, c.state(), "test:z_complex");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void changingPhysicalMetersInvalidatesUncommittedSelection(GameTestHelper h) {
        setup(
                h,
                true,
                false,
                c -> recipes(Map.of("test:four", recipe(4, 0)), () -> {
                    fill(c.state());
                    configured(h, ITEM, "minecraft:wheat", "4");
                    var reservation = c.state().select().orElseThrow();
                    meter(h, ITEM).quantity("3");
                    h.assertTrue(
                            !reservation.commit() && c.state().plantCount() == 32,
                            "Stale selection consumed resources");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void disassemblyAndReformationKeepIndependentMeterState(GameTestHelper h) {
        MixerGameTests.rig(h);
        h.setBlock(ITEM, MixerContent.state(MixerPartType.ItemMeter));
        h.setBlock(FLUID, MixerContent.state(MixerPartType.FluidMeter));
        configured(h, ITEM, "minecraft:wheat", "4");
        configured(h, FLUID, "minecraft:water", "1000");
        h.runAtTickTime(35, () -> h.setBlock(new BlockPos(3, 4, 3), Blocks.AIR));
        h.runAtTickTime(65, () -> {
            h.assertTrue(!meter(h, ITEM).isMachineAssembled(), "Missing impeller left Mixer assembled");
            h.setBlock(new BlockPos(3, 4, 3), MixerContent.state(MixerPartType.Impeller));
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(
                    meter(h, ITEM).isMachineAssembled()
                            && meter(h, FLUID).isMachineAssembled()
                            && meter(h, ITEM).quantity() == 4
                            && meter(h, FLUID).quantity() == 1000
                            && meter(h, ITEM).filter().equals("minecraft:wheat")
                            && meter(h, FLUID).filter().equals("minecraft:water"),
                    "Reformation lost independent controls");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sampledFluidComponentsRemainExactAfterPersistence(GameTestHelper h) {
        setup(h, false, true, c -> {
            var sample = new FluidStack(Fluids.WATER, 1000);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("meter sample"));
            var m = meter(h, FLUID);
            m.filter(EndpointState.fluidId(sample));
            var saved = m.saveWithFullMetadata(h.getLevel().registryAccess());
            m.filter("");
            m.loadWithComponents(saved, h.getLevel().registryAccess());
            var requirement = new FluidMeterRequirement(m.filter(), m.quantity());
            h.assertTrue(
                    requirement.matches(sample.copyWithAmount(2000))
                            && !requirement.matches(new FluidStack(Fluids.WATER, 1000)),
                    "Persisted fluid filter lost exact components");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void runningBatchKeepsItsMeterConstrainedStrategyAfterEditAndReload(GameTestHelper h) {
        setup(h, true, false, c -> {
            var definition = recipe(4, 0);
            var alternative = recipe(8, 0).getAsJsonArray("strategies").get(0).getAsJsonObject();
            alternative.addProperty("id", "larger");
            definition.getAsJsonArray("strategies").add(alternative);
            recipes(Map.of("test:strategies", definition), () -> {
                fill(c.state());
                configured(h, ITEM, "minecraft:wheat", "4");
                c.state().cadence();
                meter(h, ITEM).quantity("8");
                var saved = c.state().save(h.getLevel().registryAccess());
                c.state().load(saved, h.getLevel().registryAccess());
                c.state().cadence();
                h.assertTrue(c.state().plantCount() == 28, "Edited meter changed active strategy consumption");
                c.state().cadence();
                c.state().cadence();
                h.assertTrue(c.state().plantCount() == 20, "Subsequent batch did not use edited strategy");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void severalPhysicalMetersRequireDistinctIngredientSlots(GameTestHelper h) {
        var extra = new BlockPos(2, 2, 5);
        setup(
                h,
                true,
                true,
                c -> recipes(Map.of("test:mixed", recipe(4, 1000)), () -> {
                    fill(c.state());
                    configured(h, ITEM, "minecraft:wheat", "4");
                    configured(h, FLUID, "minecraft:water", "1000");
                    configured(h, extra, "minecraft:wheat", "4");
                    h.assertTrue(
                            c.state().select().isEmpty() && c.state().plantCount() == 32,
                            "Duplicate physical meters reused a slot");
                    var twoSlots = recipe(4, 1000);
                    twoSlots.getAsJsonArray("strategies")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("items")
                            .add(recipe(4, 0)
                                    .getAsJsonArray("strategies")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonArray("items")
                                    .get(0));
                    recipes(Map.of("test:two_slots", twoSlots), () -> {
                        h.assertTrue(
                                c.state().select().orElseThrow().commit()
                                        && c.state().plantCount() == 24
                                        && c.state().amount(0) == 3000,
                                "Three physical meters did not combine over distinct slots");
                    });
                }));
        h.setBlock(extra, MixerContent.state(MixerPartType.ItemMeter));
    }
}
