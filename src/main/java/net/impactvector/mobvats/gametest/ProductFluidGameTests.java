package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeDefinition;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Standard platform containers only; no machine interaction is involved. */
@GameTestHolder(MobVats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProductFluidGameTests {
    @GameTest(template = "empty", batch = "fluid_buckets", timeoutTicks = 10)
    public static void meatBucket(GameTestHelper h) {
        checkBucket(h, "meat", ProductFluids.MEAT.get(), ProductFluids.MEAT_BUCKET.get());
    }

    @GameTest(template = "empty", batch = "fluid_buckets", timeoutTicks = 10)
    public static void wasteBucket(GameTestHelper h) {
        checkBucket(h, "waste", ProductFluids.WASTE.get(), ProductFluids.WASTE_BUCKET.get());
    }

    @GameTest(template = "empty", batch = "fluid_buckets", timeoutTicks = 10)
    public static void cultureSlurryBucket(GameTestHelper h) {
        checkBucket(h, "culture_slurry", ProductFluids.CULTURE_SLURRY.get(), ProductFluids.CULTURE_SLURRY_BUCKET.get());
    }

    @GameTest(template = "empty", batch = "fluid_buckets", timeoutTicks = 10)
    public static void feedingSlurryBucket(GameTestHelper h) {
        checkBucket(h, "feeding_slurry", ProductFluids.FEEDING_SLURRY.get(), ProductFluids.FEEDING_SLURRY_BUCKET.get());
    }

    @GameTest(template = "empty", batch = "fluid_buckets", timeoutTicks = 10)
    public static void experienceBucket(GameTestHelper h) {
        checkBucket(h, "experience", ProductFluids.EXPERIENCE.get(), ProductFluids.EXPERIENCE_BUCKET.get());
    }

    private static void contents(GameTestHelper h, ItemStack stack, Fluid fluid, String message) {
        var contents = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        h.assertTrue(contents.is(fluid) && contents.getAmount() == 1000 && stack.getCount() == 1, message);
    }

    private static void checkBucket(GameTestHelper h, String name, Fluid fluid, BucketItem bucket) {
        var id = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, name);
        h.assertTrue(BuiltInRegistries.FLUID.getKey(fluid).equals(id)
            && BuiltInRegistries.FLUID.get(id) == fluid, "Source ID changed: " + name);
        h.assertTrue(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, name + "_bucket")) == bucket
            && bucket.content == fluid && fluid.getBucket() == bucket, "Bucket registration mismatch: " + name);
        var flowing = ((net.neoforged.neoforge.fluids.BaseFlowingFluid) fluid).getFlowing();
        h.assertTrue(BuiltInRegistries.FLUID.getKey(flowing).equals(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "flowing_" + name))
            && flowing.getBucket() == bucket, "Flowing ID/bucket changed: " + name);

        ItemStack carried = new ItemStack(bucket);
        h.assertTrue(carried.getMaxStackSize() == 1 && carried.getCraftingRemainingItem().is(Items.BUCKET),
            "Filled bucket stack/remainder semantics changed: " + name);
        contents(h, carried, fluid, "Missing standard fluid capability: " + name);
        var restored = ItemStack.parse(h.getLevel().registryAccess(), carried.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(ItemStack.isSameItemSameComponents(carried, restored), "ItemStack save/load mismatch: " + name);
        contents(h, restored, fluid, "Saved bucket lost fluid identity: " + name);

        var handler = FluidUtil.getFluidHandler(restored).orElseThrow();
        h.assertTrue(handler.getTanks() == 1 && handler.getTankCapacity(0) == 1000
            && handler.drain(999, FluidAction.EXECUTE).isEmpty(), "Partial bucket drain accepted: " + name);
        for (var other : ProductFluids.sources()) {
            if (other == fluid) continue;
            h.assertTrue(handler.drain(new FluidStack(other, 1000), FluidAction.EXECUTE).isEmpty()
                && handler.fill(new FluidStack(other, 1000), FluidAction.EXECUTE) == 0,
                "Filled bucket accepted a different fluid: " + name);
            var wrongTank = new FluidTank(2000);
            wrongTank.fill(new FluidStack(other, 1000), FluidAction.EXECUTE);
            h.assertTrue(!FluidUtil.tryEmptyContainer(carried, wrongTank, 1000, null, true).isSuccess()
                && wrongTank.getFluid().is(other) && wrongTank.getFluidAmount() == 1000,
                "Bucket contaminated a different-fluid tank: " + name);
        }
        contents(h, handler.getContainer(), fluid, "Rejected transfers mutated bucket: " + name);
        var undersized = new FluidTank(999);
        h.assertTrue(!FluidUtil.tryEmptyContainer(carried, undersized, 1000, null, true).isSuccess()
            && undersized.isEmpty(), "Partial tank capacity lost bucket contents: " + name);

        var tank = new FluidTank(2000);
        var simulatedEmpty = FluidUtil.tryEmptyContainer(carried, tank, 2000, null, false);
        h.assertTrue(simulatedEmpty.isSuccess() && simulatedEmpty.getResult().is(Items.BUCKET) && tank.isEmpty(),
            "Drain simulation mutated tank: " + name);
        contents(h, carried, fluid, "Drain simulation mutated input: " + name);
        var emptied = FluidUtil.tryEmptyContainer(carried, tank, 2000, null, true);
        h.assertTrue(emptied.isSuccess() && emptied.getResult().is(Items.BUCKET)
            && tank.getFluidAmount() == 1000 && tank.getFluid().is(fluid), "Generic drain not exactly 1000 mB: " + name);
        // FluidUtil returns the replacement container; callers must retain it.
        carried = emptied.getResult();
        h.assertTrue(!FluidUtil.tryEmptyContainer(carried, tank, 1000, null, true).isSuccess()
            && tank.getFluidAmount() == 1000, "Empty container duplicated fluid: " + name);
        h.assertTrue(!FluidUtil.tryFillContainer(carried, tank, 999, null, true).isSuccess()
            && tank.getFluidAmount() == 1000, "Partial fill consumed tank contents: " + name);
        var simulatedFill = FluidUtil.tryFillContainer(carried, tank, 2000, null, false);
        h.assertTrue(simulatedFill.isSuccess() && tank.getFluidAmount() == 1000 && carried.is(Items.BUCKET),
            "Fill simulation mutated source/container: " + name);
        contents(h, simulatedFill.getResult(), fluid, "Fill simulation selected wrong bucket: " + name);
        var filled = FluidUtil.tryFillContainer(carried, tank, 2000, null, true);
        h.assertTrue(filled.isSuccess() && tank.isEmpty(), "Generic refill lost fluid: " + name);
        carried = filled.getResult();
        h.assertTrue(carried.is(bucket), "Generic refill returned wrong bucket: " + name);
        contents(h, carried, fluid, "Round trip lost or duplicated fluid: " + name);
        h.assertTrue(!FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET), tank, 1000, null, true).isSuccess(),
            "Empty source duplicated a bucket: " + name);

        // BucketItem requires a real legacy block: prove source placement and vanilla pickup.
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos.below(), Blocks.STONE);
        var worldPos = h.absolutePos(pos);
        h.assertTrue(bucket.emptyContents(null, h.getLevel(), worldPos, null, carried), "Bucket placement failed: " + name);
        var state = h.getLevel().getBlockState(worldPos);
        h.assertTrue(state.getBlock() instanceof LiquidBlock && state.getFluidState().isSource()
            && state.getFluidState().getType() == fluid, "Placement lost fluid identity: " + name);
        h.assertTrue(!fluid.getFluidType().canConvertToSource(state.getFluidState(), h.getLevel(), worldPos),
            "Unexpected source generation: " + name);
        var pickedUp = ((LiquidBlock) state.getBlock()).pickupBlock(null, h.getLevel(), worldPos, state);
        h.assertTrue(pickedUp.is(bucket) && h.getLevel().getBlockState(worldPos).isAir(), "Source pickup mismatch: " + name);
        contents(h, pickedUp, fluid, "Picked-up source changed fluid: " + name);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fluid_buckets", timeoutTicks = 10)
    public static void processSelectorsAndMixerKeepOriginalFluidIds(GameTestHelper h) {
        h.assertTrue(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe("culture_slurry").output().get() == ProductFluids.CULTURE_SLURRY.get()
            && net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe("feeding_slurry").output().get() == ProductFluids.FEEDING_SLURRY.get(),
            "Mixer recipe fluid identities changed");
        var names = java.util.List.of("meat", "waste", "culture_slurry", "feeding_slurry", "experience");
        for (int i = 0; i < names.size(); i++) {
            var selector = ResourceSelectors.fluids(java.util.List.of("mobvats:" + names.get(i)));
            var matching = ProductFluids.sources().get(i);
            for (var fluid : ProductFluids.sources())
                h.assertTrue(selector.test(new FluidStack(fluid, 1000)) == (fluid == matching),
                    "Existing process selector changed: " + names.get(i));
        }
        h.succeed();
    }

    private ProductFluidGameTests() {}
}
