package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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
public final class MixerBlockerGameTests {
    private static final BlockPos FIRST = new BlockPos(2, 2, 1), SECOND = new BlockPos(2, 3, 1);

    private static MixerBlockerEntity blocker(GameTestHelper h, BlockPos pos) {
        return (MixerBlockerEntity) h.getBlockEntity(pos);
    }

    private static void setup(GameTestHelper h, Consumer<MultiblockMixer> test) {
        MixerGameTests.rig(h);
        h.setBlock(FIRST, MixerContent.state(MixerPartType.Blocker));
        h.setBlock(SECOND, MixerContent.state(MixerPartType.Blocker));
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(new BlockPos(1, 2, 3)))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(c.isAssembled(), "Blockers prevented formation: " + c.getLastError());
            c.setMachineActive(false);
            test.accept(c);
            h.succeed();
        });
    }

    private static JsonObject recipe(int wheat, String outputs) {
        return JsonParser.parseString("""
                {"batch_ticks":20,"strategies":[{"id":"default","items":[{"selector":"minecraft:wheat","amount":%d}]}],"outputs":%s}
                """.formatted(wheat, outputs)).getAsJsonObject();
    }

    private static JsonObject itemRecipe(int wheat, String item) {
        return recipe(wheat, "{\"items\":[{\"item\":\"minecraft:" + item + "\",\"amount\":1}]}");
    }

    private static void recipes(Map<String, JsonObject> values, Runnable test) {
        var before = MixerRecipeRegistry.snapshot();
        var root = new JsonObject();
        var entries = new JsonObject();
        values.forEach(entries::add);
        root.add("recipes", entries);
        try {
            MixerRecipeRegistry.publish(MixerRecipeRegistry.parse(root));
            test.run();
        } finally {
            MixerRecipeRegistry.publish(before);
        }
    }

    private static void fill(MixerState state) {
        state.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 32), false);
    }

    private static void iron(GameTestHelper h, BlockPos pos) {
        blocker(h, pos).sample(MixerBlockerSample.sampled(new ItemStack(Items.IRON_INGOT)));
    }

    private static FluidStack slurry(int wheat, int volume) {
        var stack = new FluidStack(ProductFluids.FEEDING_SLURRY.get(), volume);
        stack.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:wheat", wheat))));
        return stack;
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void registeredShellPartHasNoStorage(GameTestHelper h) {
        setup(h, c -> {
            h.assertTrue(MixerContent.block(MixerPartType.Blocker).asItem() != Items.AIR, "Missing block item");
            h.assertTrue(
                    h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(FIRST), null) == null
                            && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FIRST), null)
                                    == null,
                    "Blocker exposes storage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void emptyBlockersKeepConsumptionAndIdRanking(GameTestHelper h) {
        setup(
                h,
                c -> recipes(
                        Map.of(
                                "test:z",
                                itemRecipe(4, "iron_ingot"),
                                "test:a",
                                itemRecipe(4, "gold_ingot"),
                                "test:small",
                                itemRecipe(1, "diamond")),
                        () -> {
                            fill(c.state());
                            h.assertTrue(
                                    c.state().select().orElseThrow().recipe().equals("test:a"), "Ranking changed");
                            h.assertTrue(
                                    c.diagnosticReport().getString().contains("No Blocker filters configured"),
                                    "Missing diagnostic");
                        }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void matchingItemExcludesWithoutConsumptionOrReservation(GameTestHelper h) {
        setup(
                h,
                c -> recipes(Map.of("test:iron", itemRecipe(4, "iron_ingot")), () -> {
                    fill(c.state());
                    iron(h, FIRST);
                    var before = c.state().save(h.getLevel().registryAccess());
                    var definition = MixerRecipeRegistry.recipe("test:iron");
                    h.assertTrue(
                            c.state().select().isEmpty()
                                    && before.equals(c.state().save(h.getLevel().registryAccess())),
                            "Selection mutated state");
                    h.assertTrue(
                            c.state().prepare(definition).isEmpty()
                                    && !c.state().commit(definition)
                                    && c.state().plantCount() == 32,
                            "Blocked plan consumed or reserved resources");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void highestRankedBlockedFallsBack(GameTestHelper h) {
        setup(
                h,
                c -> recipes(
                        Map.of("test:high", itemRecipe(4, "iron_ingot"), "test:low", itemRecipe(1, "gold_ingot")),
                        () -> {
                            fill(c.state());
                            iron(h, FIRST);
                            var selected = c.state().select().orElseThrow();
                            h.assertTrue(
                                    selected.recipe().equals("test:low")
                                            && selected.commit()
                                            && c.state().plantCount() == 31,
                                    "Fallback failed");
                        }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void allBlockedStaysIdleWithPrivateDiagnostic(GameTestHelper h) {
        setup(
                h,
                c -> recipes(Map.of("test:undiscovered", itemRecipe(4, "iron_ingot")), () -> {
                    fill(c.state());
                    iron(h, FIRST);
                    c.state().cadence();
                    h.assertTrue(
                            c.state().progress() == 0
                                    && c.state().recipe().isEmpty()
                                    && c.state().plantCount() == 32,
                            "Blocked batch started");
                    h.assertTrue(
                            c.state().blocked().contains("All compatible recipes blocked")
                                    && !c.state().blocked().contains("undiscovered")
                                    && !c.state().blocked().contains("iron"),
                            "Diagnostic leaks product");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void multipleBlockersExcludeUnion(GameTestHelper h) {
        setup(
                h,
                c -> recipes(
                        Map.of(
                                "test:iron",
                                itemRecipe(4, "iron_ingot"),
                                "test:gold",
                                itemRecipe(2, "gold_ingot"),
                                "test:diamond",
                                itemRecipe(1, "diamond")),
                        () -> {
                            fill(c.state());
                            iron(h, FIRST);
                            blocker(h, SECOND).sample(MixerBlockerSample.sampled(new ItemStack(Items.GOLD_INGOT)));
                            h.assertTrue(
                                    c.state().select().orElseThrow().recipe().equals("test:diamond"),
                                    "Blockers did not combine as union");
                        }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void anyOfMultipleOutputsBlocksEntireRecipe(GameTestHelper h) {
        setup(
                h,
                c -> recipes(Map.of("test:multi", recipe(4, """
                {"items":[{"item":"minecraft:gold_ingot","amount":1},{"item":"minecraft:iron_ingot","amount":1}],
                 "fluids":[{"fluid":"minecraft:water","amount":100}]}
                """)), () -> {
                    fill(c.state());
                    iron(h, FIRST);
                    h.assertTrue(c.state().select().isEmpty(), "Secondary item ignored");
                    blocker(h, FIRST).sample(MixerBlockerSample.sampled(new FluidStack(Fluids.WATER, 1)));
                    h.assertTrue(
                            c.state().select().isEmpty() && c.state().plantCount() == 32, "Secondary fluid ignored");
                }));
    }

    @GameTest(template = "empty")
    public static void fluidQuantityDoesNotChangeIdentity(GameTestHelper h) {
        h.assertTrue(MixerBlockerSample.sampled(slurry(4, 1)).matches(slurry(4, 8000)), "Volume entered identity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void distinctFormulasAndRegisteredFluidsRemainDistinct(GameTestHelper h) {
        var filter = MixerBlockerSample.sampled(slurry(4, 1000));
        var other = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000);
        other.set(CompoundFluids.COMPOSITION.get(), slurry(4, 1).get(CompoundFluids.COMPOSITION.get()));
        h.assertTrue(
                !filter.matches(slurry(8, 1000))
                        && !filter.matches(other)
                        && !filter.matches(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 1000)),
                "Distinct products collapsed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void normalizedFormulaMergesEntriesAndCanonicalSelectors(GameTestHelper h) {
        var stack = slurry(4, 1000);
        stack.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(List.of(
                        new CompoundComposition.Entry(
                                CompoundComposition.Kind.ITEM, "{\"item\":\"minecraft:wheat\"}", 2),
                        new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:wheat", 2))));
        h.assertTrue(MixerBlockerSample.sampled(stack).matches(slurry(4, 1)), "Normalized formula mismatch");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidDisplayMetadataIgnoredInBothDirections(GameTestHelper h) {
        var named = slurry(4, 1000);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("presentation"));
        h.assertTrue(
                MixerBlockerSample.sampled(named).matches(slurry(4, 2))
                        && MixerBlockerSample.sampled(slurry(4, 2)).matches(named),
                "Display name entered identity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void itemComponentsRemainExactAndQuantityIgnored(GameTestHelper h) {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("exact"));
        var sample = MixerBlockerSample.sampled(named);
        h.assertTrue(
                sample.matches(named.copyWithCount(12)) && !sample.matches(new ItemStack(Items.IRON_INGOT)),
                "Item exactness changed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void actualDerivedCompositionControlsEligibility(GameTestHelper h) {
        setup(
                h,
                c -> recipes(
                        Map.of(
                                "test:basic",
                                recipe(4, "{\"fluids\":[{\"fluid\":\"mobvats:feeding_slurry\",\"amount\":1000}]}"),
                                "test:enhanced",
                                recipe(8, "{\"fluids\":[{\"fluid\":\"mobvats:feeding_slurry\",\"amount\":500}]}")),
                        () -> {
                            fill(c.state());
                            blocker(h, FIRST).sample(MixerBlockerSample.sampled(slurry(8, 1)));
                            h.assertTrue(
                                    c.state().select().orElseThrow().recipe().equals("test:basic"),
                                    "Derived formula not predicted");
                            blocker(h, FIRST).sample(MixerBlockerSample.sampled(slurry(4, 999)));
                            h.assertTrue(
                                    c.state().select().orElseThrow().recipe().equals("test:enhanced"),
                                    "Slurry type blocked all formulas");
                        }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sampleInteractionAssignsFluidOrItemAndClears(GameTestHelper h) {
        setup(h, c -> {
            var player = h.makeMockServerPlayerInLevel();
            player.setShiftKeyDown(true);
            var pos = h.absolutePos(FIRST);
            var state = h.getLevel().getBlockState(pos);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
            for (var held : List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.WATER_BUCKET))) {
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                state.useItemOn(held, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                h.assertTrue(
                        held.getCount() == 1
                                && (held.is(Items.WATER_BUCKET)
                                        ? blocker(h, FIRST).sample().matches(new FluidStack(Fluids.WATER, 5))
                                        : blocker(h, FIRST).sample().matches(new ItemStack(Items.IRON_INGOT))),
                        "Sample assignment failed");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            state.useWithoutItem(h.getLevel(), player, hit);
            h.assertTrue(blocker(h, FIRST).sample().equals(MixerBlockerSample.EMPTY), "Clear failed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void savesUpdateTagsAndPortableStateKeepSamples(GameTestHelper h) {
        setup(h, c -> {
            blocker(h, FIRST).sample(MixerBlockerSample.sampled(slurry(4, 12)));
            for (var sample :
                    List.of(blocker(h, FIRST).sample(), MixerBlockerSample.sampled(new ItemStack(Items.IRON_INGOT)))) {
                var original = blocker(h, FIRST);
                original.sample(sample);
                var registries = h.getLevel().registryAccess();
                var restored = new MixerBlockerEntity(original.getBlockPos(), original.getBlockState());
                restored.loadWithComponents(original.saveWithFullMetadata(registries), registries);
                h.assertTrue(restored.sample().equals(sample), "Save/load lost sample");
                restored.loadWithComponents(original.getUpdateTag(registries), registries);
                h.assertTrue(restored.sample().equals(sample), "Update tag lost sample");
                var portable = EndpointState.capture(
                        new ItemStack(original.getBlockState().getBlock()), original);
                original.sample(MixerBlockerSample.EMPTY);
                EndpointState.restore(portable, original);
                h.assertTrue(original.sample().equals(sample), "Portable state lost sample");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void changedSampleInvalidatesPreparedSelection(GameTestHelper h) {
        setup(
                h,
                c -> recipes(Map.of("test:iron", itemRecipe(4, "iron_ingot")), () -> {
                    fill(c.state());
                    var reservation = c.state().select().orElseThrow();
                    iron(h, FIRST);
                    h.assertTrue(!reservation.commit() && c.state().plantCount() == 32, "Stale selection consumed");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void inFlightFinishesAfterEditAndReloadThenNextBatchBlocked(GameTestHelper h) {
        setup(
                h,
                c -> recipes(Map.of("test:iron", itemRecipe(4, "iron_ingot")), () -> {
                    fill(c.state());
                    c.state().cadence();
                    h.assertTrue(c.state().progress() == 10, "Batch not started");
                    iron(h, FIRST);
                    c.state()
                            .load(
                                    c.state().save(h.getLevel().registryAccess()),
                                    h.getLevel().registryAccess());
                    c.state().cadence();
                    h.assertTrue(c.state().plantCount() == 28 && c.state().progress() == 0, "In-flight changed");
                    c.state().cadence();
                    h.assertTrue(
                            c.state().plantCount() == 28 && c.state().recipe().isEmpty(), "Next batch ignored edit");
                    blocker(h, FIRST).sample(MixerBlockerSample.EMPTY);
                    c.state().cadence();
                    h.assertTrue(c.state().progress() == 10, "Clearing did not refresh eligibility");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void metersStillConstrainUnblockedCandidates(GameTestHelper h) {
        setup(
                h,
                c -> recipes(
                        Map.of("test:iron", itemRecipe(4, "iron_ingot"), "test:gold", itemRecipe(1, "gold_ingot")),
                        () -> {
                            fill(c.state());
                            iron(h, FIRST);
                            // Use a physical meter so controller refresh follows the ordinary production path.
                            var pos = new BlockPos(2, 2, 5);
                            var meter = (AbstractMixerMeterEntity) h.getBlockEntity(pos);
                            meter.filter("minecraft:wheat");
                            meter.quantity("4");
                            h.assertTrue(c.state().select().isEmpty(), "Blocker bypassed exact meter");
                            meter.quantity("1");
                            h.assertTrue(
                                    c.state().select().orElseThrow().recipe().equals("test:gold"),
                                    "Meter edit not applied");
                        }));
        h.setBlock(new BlockPos(2, 2, 5), MixerContent.state(MixerPartType.ItemMeter));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void backpressureRemainsEffectiveWithBlockers(GameTestHelper h) {
        setup(
                h,
                c -> recipes(Map.of("test:iron", itemRecipe(4, "iron_ingot")), () -> {
                    fill(c.state());
                    // Restore a full bounded output escrow to exercise ordinary backpressure.
                    var definition = MixerRecipeRegistry.recipe("test:iron");
                    var saved = c.state().save(h.getLevel().registryAccess());
                    var outputs = new net.neoforged.neoforge.items.ItemStackHandler(4);
                    for (int i = 0; i < 4; i++) outputs.setStackInSlot(i, new ItemStack(Items.IRON_INGOT, 64));
                    saved.put("producedItems", outputs.serializeNBT(h.getLevel().registryAccess()));
                    c.state().load(saved, h.getLevel().registryAccess());
                    h.assertTrue(
                            !c.state().canCommit(definition) && c.state().plantCount() == 32, "Backpressure bypassed");
                    iron(h, FIRST);
                    h.assertTrue(c.state().select().isEmpty(), "Blocked full output accepted");
                    blocker(h, FIRST).sample(MixerBlockerSample.EMPTY);
                    h.assertTrue(c.state().select().isEmpty(), "Clearing bypassed full output");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void disassemblyReformationKeepsIndependentSamples(GameTestHelper h) {
        MixerGameTests.rig(h);
        h.setBlock(FIRST, MixerContent.state(MixerPartType.Blocker));
        h.setBlock(SECOND, MixerContent.state(MixerPartType.Blocker));
        iron(h, FIRST);
        blocker(h, SECOND).sample(MixerBlockerSample.sampled(slurry(8, 1000)));
        h.runAtTickTime(35, () -> h.setBlock(new BlockPos(3, 4, 3), Blocks.AIR));
        h.runAtTickTime(65, () -> {
            h.assertTrue(!blocker(h, FIRST).isMachineAssembled(), "Mixer remained formed");
            h.setBlock(new BlockPos(3, 4, 3), MixerContent.state(MixerPartType.Impeller));
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(
                    blocker(h, FIRST).isMachineAssembled()
                            && blocker(h, SECOND).isMachineAssembled()
                            && blocker(h, FIRST).sample().matches(new ItemStack(Items.IRON_INGOT))
                            && blocker(h, SECOND).sample().matches(slurry(8, 1)),
                    "Reformation lost sample");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void transformedOutputUsesActualAllocatedSource(GameTestHelper h) {
        var definition = JsonParser.parseString("""
            {"batch_ticks":20,"strategies":[{"id":"filter","items":[{"selector":"minecraft:charcoal","amount":1,"record_in_composition":false}],
              "fluids":[{"selector":[{"fluid":"mobvats:culture_slurry"},{"fluid":"mobvats:component_fluid"}],"amount":1000}]}],
             "outputs":{"fluids":[{"fluid":"mobvats:culture_slurry","amount":1000,
               "transform":{"source_input":0,"remove":[{"kind":"FLUID","selector":"mobvats:waste"}],"restore_ordinary":true}}]}}
            """).getAsJsonObject();
        recipes(Map.of("test:transform", definition), () -> {
            var clean = new FluidStack(Fluids.WATER, 1000);
            clean.set(DataComponents.CUSTOM_NAME, Component.literal("Presentation"));
            var dirty = CultureFouling.dirty(clean, 100).copyWithAmount(1000);
            var state = new MixerState(() -> {});
            state.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
            state.fluids(true).fill(dirty, FluidAction.EXECUTE);
            state.blockerSource(() -> List.of(MixerBlockerSample.sampled(new FluidStack(Fluids.WATER, 1))));
            var before = state.save(h.getLevel().registryAccess());
            h.assertTrue(
                    state.select().isEmpty()
                            && before.equals(state.save(h.getLevel().registryAccess())),
                    "Transform prediction mutated or ignored actual source");
            state.blockerSource(() -> List.of(MixerBlockerSample.sampled(slurry(4, 1))));
            h.assertTrue(
                    state.select().orElseThrow().commit(),
                    "Unrelated compound sample blocked restored ordinary product");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void complexityWinsAmongRemainingCandidates(GameTestHelper h) {
        var complex = itemRecipe(1, "gold_ingot");
        complex.getAsJsonArray("strategies")
                .get(0)
                .getAsJsonObject()
                .add("fluids", JsonParser.parseString("[{\"selector\":\"minecraft:water\",\"amount\":100}]"));
        recipes(
                Map.of(
                        "test:blocked",
                        itemRecipe(8, "iron_ingot"),
                        "test:z_complex",
                        complex,
                        "test:a_large",
                        itemRecipe(4, "diamond")),
                () -> {
                    var state = new MixerState(() -> {});
                    fill(state);
                    state.fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
                    state.blockerSource(() -> List.of(MixerBlockerSample.sampled(new ItemStack(Items.IRON_INGOT))));
                    h.assertTrue(
                            state.select().orElseThrow().recipe().equals("test:z_complex"),
                            "Complexity ranking changed");
                });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidBackpressureAndContentsRemainIntact(GameTestHelper h) {
        recipes(
                Map.of("test:water", recipe(1, "{\"fluids\":[{\"fluid\":\"minecraft:water\",\"amount\":1000}]}")),
                () -> {
                    var state = new MixerState(() -> {});
                    fill(state);
                    // An isolated fixture uses the same bounded fluid planner as physical controllers.
                    var full = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(SlurryBalance.CAPACITY);
                    full.setFluid(new FluidStack(Fluids.WATER, full.getCapacity()));
                    var saved = state.save(h.getLevel().registryAccess());
                    var tanks = new net.minecraft.nbt.ListTag();
                    tanks.add(full.writeToNBT(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag()));
                    saved.put("escrow", tanks);
                    state.load(saved, h.getLevel().registryAccess());
                    state.blockerSource(() -> List.of(MixerBlockerSample.sampled(new ItemStack(Items.IRON_INGOT))));
                    var before = state.save(h.getLevel().registryAccess());
                    h.assertTrue(
                            state.select().isEmpty()
                                    && before.equals(state.save(h.getLevel().registryAccess())),
                            "Fluid backpressure changed buffers");
                });
        h.succeed();
    }
}
