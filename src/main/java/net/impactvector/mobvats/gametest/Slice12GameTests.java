package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.*;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class Slice12GameTests {
    private static CompoundComposition formula(String fluid) {
        return new CompoundComposition(List.of(
                new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, fluid, 1000),
                new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "#mobvats:plant_biomass", 8)));
    }

    private static FluidStack compound(Fluid family, int amount, CompoundComposition composition) {
        var s = new FluidStack(family, amount);
        s.set(CompoundFluids.COMPOSITION.get(), composition);
        return s;
    }

    private static FluidBufferEntity buffer(GameTestHelper h, BlockPos p) {
        h.setBlock(p, FluidBufferContent.state(ConduitCladding.BARE));
        return (FluidBufferEntity) h.getBlockEntity(p);
    }

    private static void same(GameTestHelper h, FluidStack a, FluidStack b, String message) {
        h.assertTrue(FluidStack.isSameFluidSameComponents(a, b), message);
    }

    private static void rejects(GameTestHelper h, Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        h.fail(message);
    }

    private static void scoped(MixerRecipeRegistry.Loaded loaded, Runnable run) {
        var before = MixerRecipeRegistry.snapshot();
        try {
            MixerRecipeRegistry.publish(loaded);
            run.run();
        } finally {
            MixerRecipeRegistry.publish(before);
        }
    }

    private static MixerRecipeRegistry.Loaded definition(String json) {
        return MixerRecipeRegistry.parse(JsonParser.parseString(json));
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void configLoadsFilesAndRejectsInvalidDefinitions(GameTestHelper h) throws Exception {
        var directory = Files.createTempDirectory(Path.of("build"), "slice12-config-");
        Files.writeString(directory.resolve("extra.json"), """
            {"recipes":{"example:lava_diamonds":{"fluids":[{"selector":"minecraft:lava","amount":100}],"items":[{"selector":"minecraft:diamond","amount":2}],"output":"mobvats:component_fluid","output_amount":100,"batch_ticks":7}}}
            """);
        Files.writeString(directory.resolve("invalid.json"), """
            {"recipes":{"bad":{"fluids":[{"selector":"minecraft:water","amount":0}],"output":"mobvats:meat","output_amount":1,"batch_ticks":1}}}
            """);
        var loaded = MixerRecipeRegistry.load(directory, s -> {});
        h.assertTrue(
                loaded.recipes().size() == 6
                        && loaded.recipes()
                                .keySet()
                                .containsAll(List.of(
                                        "culture_slurry",
                                        "feeding_slurry",
                                        "mobvats:filter_general",
                                        "mobvats:filter_lava",
                                        "example:lava_diamonds"))
                        && loaded.errors().size() == 1,
                "Config loading was not extensible/file atomic");
        Files.writeString(directory.resolve("duplicate.json"), "{\"recipes\":{},\"recipes\":{}}");
        h.assertTrue(
                MixerRecipeRegistry.load(directory, s -> {}).errors().size() == 2, "Duplicate keys silently accepted");
        for (var amount : List.of("0", "-1", "1.5", "2147483648"))
            rejects(
                    h,
                    () -> definition("{\"recipes\":{\"bad\":{\"fluids\":[{\"selector\":\"minecraft:water\",\"amount\":"
                            + amount + "}],\"output\":\"mobvats:meat\",\"output_amount\":1,\"batch_ticks\":1}}}"),
                    "Invalid amount accepted");
        rejects(h, () -> new MixerRecipeDefinition.FluidInput("missing:fluid", 1), "Unknown fluid accepted");
        rejects(h, () -> new MixerRecipeDefinition.ItemInput("missing:item", 1), "Unknown item accepted");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void compositionCanonicalIdentityAndAmountIndependence(GameTestHelper h) {
        var a = formula("#minecraft:water");
        var entries = new ArrayList<CompoundComposition.Entry>();
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "#mobvats:plant_biomass", 4));
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "#minecraft:water", 1000));
        entries.add(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "#mobvats:plant_biomass", 4));
        var b = new CompoundComposition(entries);
        h.assertTrue(a.equals(b), "Ordering/duplicates changed identity");
        var small = compound(ProductFluids.CULTURE_SLURRY.get(), 100, a);
        var large = compound(ProductFluids.CULTURE_SLURRY.get(), 10000, b);
        same(h, small, large, "Formula scaled with volume");
        same(h, small, large.copyWithAmount(10), "Split/copy changed formula");
        var tank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(20000);
        h.assertTrue(
                tank.fill(small, FluidAction.EXECUTE) == 100 && tank.fill(large, FluidAction.EXECUTE) == 10000,
                "Same formula did not merge");
        h.assertTrue(
                tank.fill(
                                compound(ProductFluids.CULTURE_SLURRY.get(), 10, formula("minecraft:lava")),
                                FluidAction.EXECUTE)
                        == 0,
                "Unlike formulas merged");
        h.assertTrue(
                tank.fill(compound(ProductFluids.FEEDING_SLURRY.get(), 10, a), FluidAction.EXECUTE) == 0,
                "Different families merged");
        h.assertTrue(!a.equals(formula("minecraft:water")), "Exact/tag distinction lost");
        rejects(
                h,
                () -> new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:wheat", 0),
                "Zero formula accepted");
        rejects(
                h,
                () -> new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", -1),
                "Negative formula accepted");
        var encoded = CompoundComposition.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        h.assertTrue(
                a.equals(CompoundComposition.CODEC
                        .parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow()),
                "Formula codec roundtrip failed");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void fullStackPersistenceAndNetworkRetainAllComponents(GameTestHelper h) {
        var original = compound(ProductFluids.COMPONENT_FLUID.get(), 1500, formula("minecraft:water"));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Other fluid component"));
        var saved = original.save(h.getLevel().registryAccess());
        var restored = FluidStack.parse(h.getLevel().registryAccess(), saved).orElseThrow();
        same(h, original, restored, "Persistence lost components");
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            FluidStack.STREAM_CODEC.encode(buf, original);
            var synced = FluidStack.STREAM_CODEC.decode(buf);
            h.assertTrue(FluidStack.matches(original, synced), "Network lost fluid components/amount");
        } finally {
            buf.release();
        }
        var names = MixerRecipeRegistry.namesJson();
        var nameBuf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            MixerDefinitionEvents.Names.CODEC.encode(nameBuf, new MixerDefinitionEvents.Names(names));
            h.assertTrue(
                    MixerDefinitionEvents.Names.CODEC.decode(nameBuf).json().equals(names),
                    "Names packet roundtrip failed");
        } finally {
            nameBuf.release();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void authoredTagAndExactSelectorsSurviveCrafting(GameTestHelper h) {
        var loaded = definition("""
            {"recipes":{"test:tagged":{"fluids":[{"selector":"#minecraft:water","amount":1000}],"items":[{"selector":"#mobvats:plant_biomass","amount":8}],"output":"mobvats:feeding_slurry","output_amount":1000,"batch_ticks":1},"test:exact":{"items":[{"selector":"minecraft:diamond","amount":1}],"output":"mobvats:component_fluid","output_amount":1000,"batch_ticks":1}}}
            """);
        scoped(loaded, () -> {
            FluidStack first = FluidStack.EMPTY;
            for (var inputs : List.of(
                    List.of(new ItemStack(Items.CARROT, 8)),
                    List.of(new ItemStack(Items.POTATO, 8)),
                    List.of(new ItemStack(Items.CARROT, 4), new ItemStack(Items.POTATO, 4)))) {
                var s = new MixerState(() -> {});
                s.fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
                for (int i = 0; i < inputs.size(); i++) s.itemInput().insertItem(i, inputs.get(i), false);
                h.assertTrue(s.commit(loaded.recipes().get("test:tagged")), "Tagged recipe failed");
                var result = s.fluids(false).drain(1000, FluidAction.SIMULATE);
                h.assertTrue(
                        result.get(CompoundFluids.COMPOSITION.get()).equals(formula("#minecraft:water")),
                        "Concrete tag member substituted");
                if (!first.isEmpty()) same(h, first, result, "Different tag members changed identity");
                first = result;
            }
            var s = new MixerState(() -> {});
            h.assertTrue(
                    s.itemInput()
                            .insertItem(0, new ItemStack(Items.DIAMOND), false)
                            .isEmpty(),
                    "Nonplant configured item rejected");
            h.assertTrue(s.commit(loaded.recipes().get("test:exact")), "Exact item recipe failed");
            var e = s.fluids(false)
                    .drain(1000, FluidAction.SIMULATE)
                    .get(CompoundFluids.COMPOSITION.get())
                    .entries()
                    .getFirst();
            h.assertTrue(
                    e.selector()
                            .equals(CompoundComposition.canonicalSelector(
                                    CompoundComposition.Kind.ITEM, "minecraft:diamond")),
                    "Exact identity lost");
        });
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void componentAwareNativeIngredientFormsLoad(GameTestHelper h) {
        var loaded = definition("""
            {"recipes":{"test:components":{"items":[{"selector":{"type":"neoforge:components","items":"minecraft:diamond","components":{"minecraft:custom_name":"Named"}},"amount":1}],"fluids":[{"selector":{"type":"neoforge:components","fluids":"mobvats:experience","components":{"minecraft:custom_name":"Named"}},"amount":20}],"output":"mobvats:component_fluid","output_amount":1000,"batch_ticks":1}}}
            """);
        var recipe = loaded.recipes().get("test:components");
        var item = new ItemStack(Items.DIAMOND);
        var fluid = new FluidStack(ProductFluids.EXPERIENCE.get(), 20);
        h.assertTrue(
                !recipe.items().getFirst().selector().test(item)
                        && !recipe.fluids().getFirst().selector().test(fluid),
                "Component selector ignored required data");
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Named"));
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Named"));
        h.assertTrue(
                recipe.items().getFirst().selector().test(item)
                        && recipe.fluids().getFirst().selector().test(fluid),
                "Native component ingredients not supported");
        scoped(loaded, () -> {
            var s = new MixerState(() -> {});
            s.itemInput().insertItem(0, item, false);
            s.fluids(true).fill(fluid, FluidAction.EXECUTE);
            h.assertTrue(s.commit(recipe), "Component-aware recipe failed");
            h.assertTrue(
                    s
                            .fluids(false)
                            .drain(1000, FluidAction.SIMULATE)
                            .get(CompoundFluids.COMPOSITION.get())
                            .entries()
                            .stream()
                            .allMatch(e -> e.selector().contains("neoforge:components")),
                    "Custom authored selector identity lost");
        });
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void overlappingSelectorsAllocateWithoutDoubleConsumption(GameTestHelper h) {
        var loaded = definition("""
            {"recipes":{"test:overlap":{"items":[{"selector":"#mobvats:plant_biomass","amount":1},{"selector":"minecraft:carrot","amount":1}],"fluids":[{"selector":"#minecraft:water","amount":100},{"selector":"minecraft:water","amount":100}],"output":"mobvats:meat","output_amount":10,"batch_ticks":1}}}
            """);
        scoped(loaded, () -> {
            var s = new MixerState(() -> {});
            s.itemInput().insertItem(0, new ItemStack(Items.CARROT), false);
            s.itemInput().insertItem(1, new ItemStack(Items.POTATO), false);
            s.fluids(true).fill(new FluidStack(Fluids.WATER, 200), FluidAction.EXECUTE);
            h.assertTrue(
                    s.commit(loaded.recipes().get("test:overlap")) && s.plantCount() == 0 && s.amount(0) == 0,
                    "Overlapping selectors starved exact ingredient/double consumed");
        });
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void genericPhysicalMixerAndOrdinaryFluidIngredients(GameTestHelper h) {
        var loaded = definition("""
            {"recipes":{"test:ordinary":{"fluids":[{"selector":"minecraft:lava","amount":100},{"selector":"mobvats:meat","amount":10},{"selector":"mobvats:waste","amount":20},{"selector":"mobvats:experience","amount":30}],"items":[{"selector":"minecraft:diamond","amount":2}],"output":"mobvats:component_fluid","output_amount":100,"batch_ticks":1}}}
            """);
        var owner = new BlockPos(3, 2, 3);
        var members = new ArrayList<FluidBufferEntity>();
        int i = 0;
        for (var fluid : List.of(
                Fluids.LAVA, ProductFluids.MEAT.get(), ProductFluids.WASTE.get(), ProductFluids.EXPERIENCE.get())) {
            var member = buffer(h, owner.relative(Direction.values()[i++]));
            member.filter(net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(fluid)
                    .toString());
            member.tank.fill(new FluidStack(fluid, 100), FluidAction.EXECUTE);
            h.assertTrue(
                    !CompoundFluids.isCompound(member.tank.getFluid())
                            && member.tank.getFluid().get(CompoundFluids.COMPOSITION.get()) == null,
                    "Ordinary fluid converted");
            members.add(member);
        }
        var storage = new PhysicalFluidStorage();
        storage.compile(members, ConduitTopology.EMPTY);
        var service = new ServiceStaging(() -> {});
        storage.services(Map.of(h.absolutePos(owner), service));
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            state.bind(storage, h.absolutePos(owner));
            state.itemInput().insertItem(0, new ItemStack(Items.DIAMOND, 2), false);
            h.assertTrue(
                    state.commit(loaded.recipes().get("test:ordinary")),
                    "Generic physical recipe failed: " + state.blocked());
            var result = service.tanks().getFirst().getFluid();
            h.assertTrue(
                    result.is(ProductFluids.COMPONENT_FLUID.get())
                            && result.get(CompoundFluids.COMPOSITION.get())
                                            .entries()
                                            .size()
                                    == 5,
                    "Arbitrary output/ordinary composition ingredients lost");
            h.assertTrue(
                    state.save(h.getLevel().registryAccess())
                            .getList("escrow", 10)
                            .isEmpty(),
                    "Physical fluid duplicated into controller storage");
        });
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void compoundInputsFlattenAndDuplicateEntriesCombine(GameTestHelper h) {
        var loaded = definition("""
            {"recipes":{"test:transitive":{"fluids":[{"selector":"mobvats:component_fluid","amount":500},{"selector":"minecraft:water","amount":100}],"output":"mobvats:culture_slurry","output_amount":1000,"batch_ticks":1}}}
            """);
        var p = new BlockPos(3, 2, 3);
        var a = buffer(h, p.west());
        var b = buffer(h, p.east());
        var water = buffer(h, p.north());
        var formula = formula("minecraft:water");
        a.tank.fill(compound(ProductFluids.COMPONENT_FLUID.get(), 200, formula), FluidAction.EXECUTE);
        b.tank.fill(compound(ProductFluids.COMPONENT_FLUID.get(), 300, formula), FluidAction.EXECUTE);
        water.tank.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(a, b, water), ConduitTopology.EMPTY);
        var service = new ServiceStaging(() -> {});
        storage.services(Map.of(h.absolutePos(p), service));
        scoped(loaded, () -> {
            var s = new MixerState(() -> {});
            s.bind(storage, h.absolutePos(p));
            h.assertTrue(
                    s.commit(loaded.recipes().get("test:transitive")),
                    "Compound input processing failed: " + s.blocked());
            var result = s.fluids(false).drain(1000, FluidAction.SIMULATE).get(CompoundFluids.COMPOSITION.get());
            h.assertTrue(
                    result.entries().size() == 2
                            && result.entries().stream()
                                            .filter(e -> e.kind() == CompoundComposition.Kind.FLUID)
                                            .findFirst()
                                            .orElseThrow()
                                            .amount()
                                    == 1100,
                    "Formula was nested, scaled, or duplicated by physical split");
            h.assertTrue(
                    result.entries().stream().noneMatch(e -> e.selector().contains("component_fluid")),
                    "Nested family persisted");
        });
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void sampledFiltersBuffersAndResourceViewsRetainExactIdentity(GameTestHelper h) {
        var p = new BlockPos(3, 2, 3);
        var a = buffer(h, p.west());
        var b = buffer(h, p.east());
        var original = compound(ProductFluids.CULTURE_SLURRY.get(), 500, formula("minecraft:water"));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Full identity"));
        var other = compound(ProductFluids.CULTURE_SLURRY.get(), 500, formula("minecraft:lava"));
        a.filter(EndpointState.fluidId(original));
        h.assertTrue(
                a.accepts(original.copyWithAmount(10000)) && !a.accepts(other),
                "Sampled filter lost components or included amount");
        a.tank.fill(original, FluidAction.EXECUTE);
        b.tank.fill(other, FluidAction.EXECUTE);
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(a, b), ConduitTopology.EMPTY);
        var view = new ResourceFluidTank(storage, h.absolutePos(p), ProductFluids.CULTURE_SLURRY::get);
        h.assertTrue(view.getFluidAmount() == 500, "View aggregated unlike compositions");
        var sample = view.getFluid();
        var drained = view.drain(100, FluidAction.EXECUTE);
        same(h, sample, drained, "View reconstructed transfer from registry Fluid");
        h.assertTrue(drained.getAmount() == 100, "View transfer amount wrong");
        var saved = a.saveWithFullMetadata(h.getLevel().registryAccess());
        a.tank.setFluid(FluidStack.EMPTY);
        a.loadWithComponents(saved, h.getLevel().registryAccess());
        same(h, original, a.tank.getFluid(), "Fluid Buffer save/reload lost components");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void actualConduitTransferAndServiceStoragePreserveFullStack(GameTestHelper h) {
        var start = new BlockPos(1, 2, 2);
        var end = new BlockPos(5, 2, 2);
        var a = buffer(h, start);
        var b = buffer(h, end);
        for (int x = 2; x <= 4; x++)
            h.setBlock(
                    new BlockPos(x, 2, 2),
                    ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        var original = compound(ProductFluids.COMPONENT_FLUID.get(), 1000, formula("minecraft:water"));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Transport metadata"));
        a.tank.fill(original, FluidAction.EXECUTE);
        var endpoint = h.absolutePos(new BlockPos(3, 3, 2));
        var endpoints = List.of(
                new ConduitTopology.Endpoint(
                        a.getBlockPos(),
                        ConduitResource.FLUID,
                        ConduitTopology.Role.PRODUCER,
                        ConduitTopology.Role.CONSUMER),
                new ConduitTopology.Endpoint(
                        b.getBlockPos(),
                        ConduitResource.FLUID,
                        ConduitTopology.Role.PRODUCER,
                        ConduitTopology.Role.CONSUMER),
                new ConduitTopology.Endpoint(
                        endpoint, ConduitResource.FLUID, ConduitTopology.Role.PRODUCER, ConduitTopology.Role.CONSUMER));
        var topology = ConduitTopology.compile(
                h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)), h.absolutePos(new BlockPos(6, 4, 4)), endpoints);
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(a, b), topology);
        var extracted =
                storage.handler(endpoint, false, s -> true).drain(original.copyWithAmount(1000), FluidAction.EXECUTE);
        h.assertTrue(extracted.getAmount() == 1000, "Conduit routing failed");
        same(h, original, extracted, "Conduit drain lost components");
        h.assertTrue(b.tank.fill(extracted, FluidAction.EXECUTE) == 1000, "Destination rejected full identity");
        same(h, original, b.tank.getFluid(), "Conduit transfer changed components");
        var stage = new ServiceStaging(() -> {});
        var tank = stage.tank(original);
        tank.fill(extracted, FluidAction.EXECUTE);
        h.assertTrue(
                tank.fill(
                                compound(ProductFluids.COMPONENT_FLUID.get(), 100, formula("minecraft:lava")),
                                FluidAction.EXECUTE)
                        == 0,
                "Service silently merged unlike formulas");
        var saved = stage.save(h.getLevel().registryAccess());
        var restored = new ServiceStaging(() -> {});
        restored.load(saved, h.getLevel().registryAccess());
        same(h, original, restored.tanks().getFirst().getFluid(), "Endpoint save/reload lost components");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void allCompoundBucketsRoundTripAndSpillsKeepOnlyFamily(GameTestHelper h) {
        int i = 0;
        for (var family : List.of(
                ProductFluids.CULTURE_SLURRY.get(),
                ProductFluids.FEEDING_SLURRY.get(),
                ProductFluids.COMPONENT_FLUID.get())) {
            var original = compound(family, 1000, formula("minecraft:water"));
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Contained metadata"));
            var source = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
            source.fill(original, FluidAction.EXECUTE);
            var fill = FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET), source, 1000, null, true);
            h.assertTrue(fill.isSuccess(), "Compound bucket fill failed");
            var bucket = fill.getResult();
            same(h, original, FluidUtil.getFluidContained(bucket).orElseThrow(), "Bucket lost full components");
            var destination = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
            h.assertTrue(
                    FluidUtil.tryEmptyContainer(bucket, destination, 1000, null, true)
                            .isSuccess(),
                    "Bucket empty failed");
            same(h, original, destination.getFluid(), "Bucket roundtrip lost components");
            var worldPos = h.absolutePos(new BlockPos(2 + i++ * 2, 2, 3));
            var item = (BucketItem) bucket.getItem();
            h.assertTrue(
                    item.emptyContents(null, h.getLevel(), worldPos, null, bucket),
                    "Compound bucket world placement failed");
            var state = h.getLevel().getBlockState(worldPos);
            h.assertTrue(
                    state.getBlock() instanceof LiquidBlock
                            && state.getFluidState().getType() == family,
                    "World spill lost fluid family");
            var recovered = ((LiquidBlock) state.getBlock()).pickupBlock(null, h.getLevel(), worldPos, state);
            var collected = FluidUtil.getFluidContained(recovered).orElseThrow();
            h.assertTrue(
                    collected.is(family) && collected.getComponents().isEmpty(),
                    "World pickup falsely restored formula/data components");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 70)
    public static void formedFluidPortContainerRoundTripPreservesComposition(GameTestHelper h) {
        VatGameTests.build(h, 5);
        var head = new BlockPos(3, 3, 3);
        ((VatHeadEntity) h.getBlockEntity(head)).encode(ResourceLocation.withDefaultNamespace("cow"));
        var portPos = new BlockPos(1, 2, 3);
        h.setBlock(portPos, Content.state(VatPartType.FluidPort));
        var port = (VatFluidPortEntity) h.getBlockEntity(portPos);
        port.setIoDirection(IoDirection.Input);
        Slice11Fixtures.vatStorage(h, new BlockPos(1, 1, 1), 5, 5);
        h.runAtTickTime(35, () -> {
            h.assertTrue(port.isMachineAssembled(), "Fluid Port fixture did not assemble");
            var original = compound(ProductFluids.CULTURE_SLURRY.get(), 1000, formula("minecraft:water"));
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Port metadata"));
            var bucket = FluidUtil.getFilledBucket(original);
            var player = h.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
            h.assertTrue(
                    port.interact(player, InteractionHand.MAIN_HAND)
                            && player.getMainHandItem().is(Items.BUCKET),
                    "Fluid Port input failed");
            port.setIoDirection(IoDirection.Output);
            h.assertTrue(port.interact(player, InteractionHand.MAIN_HAND), "Fluid Port output failed");
            same(
                    h,
                    original,
                    FluidUtil.getFluidContained(player.getMainHandItem()).orElseThrow(),
                    "Fluid Port container roundtrip stripped components");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void legacySlurriesAndMixerEscrowSurviveWithoutInventingFormulas(GameTestHelper h) {
        var tag = new CompoundTag();
        var families = List.of(ProductFluids.CULTURE_SLURRY.get(), ProductFluids.FEEDING_SLURRY.get());
        for (int i = 0; i < families.size(); i++) {
            var tank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(8000);
            tank.setFluid(new FluidStack(families.get(i), 5000));
            tag.put("tank" + (i + 2), tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
        }
        var p = new BlockPos(3, 2, 3);
        var a = buffer(h, p.west());
        var b = buffer(h, p.east());
        a.filter("mobvats:culture_slurry");
        b.filter("mobvats:feeding_slurry");
        var storage = new PhysicalFluidStorage();
        storage.compile(List.of(a, b), ConduitTopology.EMPTY);
        var state = new MixerState(() -> {});
        state.load(tag, h.getLevel().registryAccess());
        state.bind(storage, h.absolutePos(p));
        h.assertTrue(
                a.tank.getFluidAmount() == 4000 && b.tank.getFluidAmount() == 4000,
                "Existing slurry IDs/resources changed");
        var saved = state.save(h.getLevel().registryAccess());
        var restored = new MixerState(() -> {});
        restored.load(saved, h.getLevel().registryAccess());
        restored.bind(storage, h.absolutePos(p));
        a.tank.drain(1000, FluidAction.EXECUTE);
        b.tank.drain(1000, FluidAction.EXECUTE);
        restored.cadence();
        h.assertTrue(
                a.tank.getFluidAmount() == 4000
                        && b.tank.getFluidAmount() == 4000
                        && restored.save(h.getLevel().registryAccess())
                                .getList("escrow", 10)
                                .isEmpty(),
                "Escrow erased/duplicated during reload");
        h.assertTrue(a.tank.getFluid().get(CompoundFluids.COMPOSITION.get()) == null, "Legacy formula invented");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void ordinaryProductBucketsPreserveUnrelatedComponents(GameTestHelper h) {
        for (var family :
                List.of(ProductFluids.MEAT.get(), ProductFluids.WASTE.get(), ProductFluids.EXPERIENCE.get())) {
            var original = new FluidStack(family, 1000);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Ordinary metadata"));
            var bucket = FluidUtil.getFilledBucket(original);
            same(
                    h,
                    original,
                    FluidUtil.getFluidContained(bucket).orElseThrow(),
                    "Ordinary product bucket stripped components");
            var tank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
            h.assertTrue(
                    FluidUtil.tryEmptyContainer(bucket, tank, 1000, null, true).isSuccess(),
                    "Ordinary product container failed");
            same(h, original, tank.getFluid(), "Ordinary product transfer lost components");
            h.assertTrue(
                    !CompoundFluids.isCompound(tank.getFluid())
                            && tank.getFluid().get(CompoundFluids.COMPOSITION.get()) == null,
                    "Ordinary product became compound");
        }
        var water = new FluidStack(Fluids.WATER, 1000);
        water.set(DataComponents.CUSTOM_NAME, Component.literal("Unsupported vanilla bucket metadata"));
        var source = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        source.fill(water, FluidAction.EXECUTE);
        h.assertTrue(
                !FluidContainerInteraction.preservesFill(new ItemStack(Items.BUCKET), source, water, 1000)
                        && source.getFluidAmount() == 1000,
                "Lossy external bucket accepted or source mutated during preflight");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void unknownCompositionBlocksDerivedRecipeButExplicitRecoveryWorks(GameTestHelper h) {
        var loaded = definition("""
            {"recipes":{"test:derived":{"fluids":[{"selector":"mobvats:component_fluid","amount":1000}],"output":"mobvats:culture_slurry","output_amount":1000,"batch_ticks":1},"test:recovery":{"fluids":[{"selector":"mobvats:component_fluid","amount":1000}],"output":"mobvats:culture_slurry","output_amount":1000,"batch_ticks":1,"composition":[{"kind":"FLUID","selector":"minecraft:water","amount":1000}]}}}
            """);
        scoped(loaded, () -> {
            var state = new MixerState(() -> {});
            state.fluids(true).fill(new FluidStack(ProductFluids.COMPONENT_FLUID.get(), 1000), FluidAction.EXECUTE);
            h.assertTrue(
                    !state.commit(loaded.recipes().get("test:derived"))
                            && state.blocked().contains("unknown composition"),
                    "Unknown spill formula invented");
            h.assertTrue(
                    java.util.stream.IntStream.range(0, state.fluids(true).getTanks())
                                    .mapToObj(state.fluids(true)::getFluidInTank)
                                    .mapToInt(FluidStack::getAmount)
                                    .sum()
                            == 1000,
                    "Rejected recipe consumed input");
            h.assertTrue(state.commit(loaded.recipes().get("test:recovery")), "Explicit configured recovery failed");
            var output = state.fluids(false).drain(1000, FluidAction.SIMULATE);
            h.assertTrue(
                    output.get(CompoundFluids.COMPOSITION.get()).entries().size() == 1,
                    "Explicit recovery ignored formula");
        });
        rejects(
                h,
                () -> MixerRecipeRegistry.parseComposition(JsonParser.parseString(
                                "[{\"kind\":\"FLUID\",\"selector\":\"mobvats:component_fluid\",\"amount\":1000}]")
                        .getAsJsonArray()),
                "Nested compound family accepted in explicit composition");
        rejects(
                h,
                () -> MixerRecipeRegistry.parseComposition(JsonParser.parseString(
                                "[{\"kind\":\"FLUID\",\"selector\":{\"type\":\"neoforge:components\",\"fluids\":\"mobvats:component_fluid\",\"components\":{}},\"amount\":1000}]")
                        .getAsJsonArray()),
                "Native compound family selector accepted in explicit composition");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void pumpsRejectLossyCompoundPlacementAndCollectOrdinarySources(GameTestHelper h) {
        var pumpPos = new BlockPos(3, 2, 3);
        h.setBlock(pumpPos, Content.state(VatPartType.PushPump));
        var pump = (VatBoundaryEntity) h.getBlockEntity(pumpPos);
        var root = h.absolutePos(pumpPos.west());
        // Bound the search to one exterior source cell so the test does not spill outside its fixture.
        h.setBlock(pumpPos.west().below(), Blocks.STONE);
        h.setBlock(pumpPos.west().west(), Blocks.STONE);
        h.setBlock(pumpPos.west().north(), Blocks.STONE);
        h.setBlock(pumpPos.west().south(), Blocks.STONE);
        var original = compound(ProductFluids.CULTURE_SLURRY.get(), 1000, formula("minecraft:water"));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Pump metadata"));
        var other = compound(ProductFluids.CULTURE_SLURRY.get(), 1000, formula("minecraft:lava"));
        var a = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        var b = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        a.fill(original, FluidAction.EXECUTE);
        b.fill(other, FluidAction.EXECUTE);
        pump.filter(EndpointState.fluidId(original));
        h.assertTrue(
                !net.impactvector.mobvats.gamecontent.multiblock.vat.BoundaryDevices.push(
                        h.getLevel(), root, PhysicalFluidStorage.handler(List.of(a, b), false, pump::accepts), pump),
                "Pump placed compound lossily");
        same(h, original, a.getFluid(), "Rejected placement changed selected identity");
        same(h, other, b.getFluid(), "Pump drained wrong composition");
        h.assertTrue(h.getLevel().getBlockState(root).isAir(), "Rejected placement changed world");
        pump.filter("");
        h.getLevel()
                .setBlock(
                        root,
                        ProductFluids.CULTURE_SLURRY.get().defaultFluidState().createLegacyBlock(),
                        3);
        var collected = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        h.assertTrue(
                net.impactvector.mobvats.gamecontent.multiblock.vat.BoundaryDevices.pull(
                                h.getLevel(), root, collected, pump)
                        && collected.getFluid().getComponents().isEmpty()
                        && collected.getFluid().is(ProductFluids.CULTURE_SLURRY.get()),
                "Ordinary world pickup invented composition");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice12", timeoutTicks = 10)
    public static void friendlyNamesAreConfiguredAndNeverPartOfFluidIdentity(GameTestHelper h) {
        var composition = formula("minecraft:water");
        var entry = CompoundComposition.CODEC
                .encodeStart(JsonOps.INSTANCE, composition)
                .getOrThrow();
        var name = new MixerRecipeRegistry.Name(
                ResourceLocation.parse("mobvats:culture_slurry"), composition, "Configured Medium", "");
        var saved = MixerRecipeRegistry.snapshot();
        try {
            MixerRecipeRegistry.publish(new MixerRecipeRegistry.Loaded(saved.recipes(), List.of(name), List.of()));
            MixerRecipeRegistry.clientNames(MixerRecipeRegistry.namesJson());
            var stack = compound(ProductFluids.CULTURE_SLURRY.get(), 100, composition);
            var before = stack.copy();
            h.assertTrue(stack.getHoverName().getString().equals("Configured Medium"), "Configured name not displayed");
            var tooltip = new ArrayList<Component>();
            CompoundBucketItem.appendComposition(stack, tooltip);
            h.assertTrue(tooltip.size() == 3, "Composition tooltip missing");
            MixerRecipeRegistry.publish(saved);
            same(h, before, stack, "Display name changed resource identity");
            h.assertTrue(
                    CompoundComposition.CODEC
                            .encodeStart(JsonOps.INSTANCE, composition)
                            .getOrThrow()
                            .equals(entry),
                    "Name changed serialized composition");
        } finally {
            MixerRecipeRegistry.publish(saved);
        }
        h.succeed();
    }
}
