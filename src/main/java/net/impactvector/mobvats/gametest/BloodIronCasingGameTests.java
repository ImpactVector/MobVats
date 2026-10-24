package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

import java.nio.file.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class BloodIronCasingGameTests {
    private static final String RECIPE = "mobvats:blood_iron_vat_casing";

    private static Item casing(int tier) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse("mobvats:vat_casing_t" + tier));
    }

    private static MixerState filled(int casings, int sand, int fluid) {
        var state = new MixerState(() -> {});
        if (casings > 0) state.itemInput().insertItem(0, new ItemStack(casing(2), casings), false);
        if (sand > 0) state.itemInput().insertItem(1, new ItemStack(Items.SOUL_SAND, sand), false);
        if (fluid > 0) state.fluids(true).fill(new FluidStack(ProductFluids.STRANGE_MEAT.get(), fluid), EXECUTE);
        return state;
    }

    private static int outputs(MixerState state) {
        var output = state.itemPort(false, s -> s.is(casing(3)));
        int count = 0;
        for (int slot = 0; slot < output.getSlots(); slot++)
            count += output.getStackInSlot(slot).getCount();
        return count;
    }

    private static CompoundTag resources(GameTestHelper h, MixerState state) {
        var tag = state.save(h.getLevel().registryAccess());
        tag.remove("blocked");
        return tag;
    }

    private static void scoped(GameTestHelper h, java.util.function.Consumer<Path> action) throws Exception {
        var directory = Files.createTempDirectory("blood-iron-mixer-");
        var before = MixerRecipeRegistry.snapshot();
        try {
            var loaded = MixerRecipeRegistry.load(directory, s -> {});
            h.assertTrue(loaded.errors().isEmpty() && loaded.recipes().containsKey(RECIPE), "Bundled recipe missing");
            MixerRecipeRegistry.publish(loaded);
            action.accept(directory);
        } finally {
            MixerRecipeRegistry.publish(before);
            Files.deleteIfExists(directory.resolve("_defaults.json"));
            Files.delete(directory);
        }
    }

    @GameTest(template = "empty")
    public static void completesAt200TicksAcrossSaveAndConfigReload(GameTestHelper h) throws Exception {
        scoped(h, directory -> {
            var state = filled(4, 1, 100);
            for (int i = 0; i < 10; i++) state.cadence();
            h.assertTrue(state.progress() == 100 && outputs(state) == 0, "Batch completed before 200 ticks");
            var tag = state.save(h.getLevel().registryAccess());
            var reloaded = MixerRecipeRegistry.load(directory, s -> {});
            h.assertTrue(reloaded.errors().isEmpty(), "Config reload failed");
            MixerRecipeRegistry.publish(reloaded);
            var restored = new MixerState(() -> {});
            restored.load(tag, h.getLevel().registryAccess());
            for (int i = 0; i < 9; i++) restored.cadence();
            h.assertTrue(
                    restored.progress() == 190
                            && outputs(restored) == 0
                            && restored.itemInput().getStackInSlot(0).getCount() == 4
                            && restored.itemInput().getStackInSlot(1).getCount() == 1
                            && restored.internalFluids().getFirst().getAmount() == 100,
                    "Inputs consumed or output produced before tick 200");
            restored.cadence();
            h.assertTrue(
                    outputs(restored) == 4
                            && restored.itemInput().getStackInSlot(0).isEmpty()
                            && restored.itemInput().getStackInSlot(1).isEmpty()
                            && restored.internalFluids().isEmpty(),
                    "Tick 200 did not consume exact inputs and produce four T3 casings");
            var finished = new MixerState(() -> {});
            finished.load(
                    restored.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            finished.cadence();
            h.assertTrue(outputs(finished) == 4, "Reload lost or duplicated finished casings");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missingAndInsufficientInputsDoNotMutateResources(GameTestHelper h) throws Exception {
        scoped(h, directory -> {
            for (var amounts : new int[][] {{0, 1, 100}, {3, 1, 100}, {4, 0, 100}, {4, 1, 0}, {4, 1, 99}}) {
                var state = filled(amounts[0], amounts[1], amounts[2]);
                var before = resources(h, state);
                h.assertTrue(!state.commit(MixerRecipeRegistry.recipe(RECIPE)), "Incomplete inputs accepted");
                for (int i = 0; i < 20; i++) state.cadence();
                h.assertTrue(
                        before.equals(resources(h, state)) && outputs(state) == 0,
                        "Incomplete batch changed resources or progress");
            }
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void outputCapacityAndStaleReservationPreserveInputs(GameTestHelper h) throws Exception {
        scoped(h, directory -> {
            var state = filled(0, 0, 0);
            var recipe = MixerRecipeRegistry.recipe(RECIPE);
            for (int i = 0; i < 64; i++) {
                state.itemInput().insertItem(0, new ItemStack(casing(2), 4), false);
                state.itemInput().insertItem(1, new ItemStack(Items.SOUL_SAND), false);
                state.fluids(true).fill(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 100), EXECUTE);
                h.assertTrue(state.commit(recipe), "Output capacity fixture failed");
            }
            var output = state.itemPort(false, s -> true);
            output.extractItem(0, 3, false);
            state.itemInput().insertItem(0, new ItemStack(casing(2), 4), false);
            state.itemInput().insertItem(1, new ItemStack(Items.SOUL_SAND), false);
            state.fluids(true).fill(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 100), EXECUTE);
            var before = resources(h, state);
            h.assertTrue(
                    !state.commit(recipe) && before.equals(resources(h, state)) && outputs(state) == 253,
                    "Three free output spaces partially consumed or produced a batch");
            for (int i = 0; i < 20; i++) state.cadence();
            h.assertTrue(before.equals(resources(h, state)), "Blocked completion lost resources");
            output.extractItem(0, 1, false);
            var reservation = state.prepare(recipe).orElseThrow();
            state.itemInput().insertItem(1, new ItemStack(Items.SOUL_SAND), false);
            before = resources(h, state);
            h.assertTrue(
                    !reservation.commit() && before.equals(resources(h, state)), "Stale reservation mutated inputs");
            h.assertTrue(state.commit(recipe) && outputs(state) == 256, "Freed output capacity did not allow batch");
            h.assertTrue(
                    state.itemInput().getStackInSlot(0).isEmpty()
                            && state.itemInput().getStackInSlot(1).getCount() == 1
                            && state.internalFluids().isEmpty(),
                    "Successful batch consumed incorrect input amounts");
        });
        h.succeed();
    }
}
