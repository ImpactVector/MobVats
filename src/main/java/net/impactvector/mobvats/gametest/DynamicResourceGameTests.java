package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class DynamicResourceGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("somepack:water"),
            ICHOR = ResourceLocation.parse("somepack:ichor");
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            FEED = O.offset(2, 1, 0),
            FEED2 = O.offset(0, 2, 2);

    private static ProductBufferDefinition definition(String fluid, int capacity, boolean input) {
        return new ProductBufferDefinition(List.of(fluid), capacity, input);
    }

    private static ProductBuffers buffers() {
        var b = new ProductBuffers(() -> {});
        b.configure(Map.of(
                WATER,
                definition("minecraft:water", 4000, true),
                ICHOR,
                definition("mobvats:culture_slurry", 1000, false)));
        return b;
    }

    private static boolean produce(ProductBuffers b, ResourceLocation id, FluidStack stack) {
        var step = ProcessOutput.fluid(b.buffer(id), stack);
        return step != null && new ProcessTransaction().add(step).commit();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void arbitraryBufferFluidAndExactExport(GameTestHelper h) {
        var b = buffers();
        h.assertTrue(
                produce(b, ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 250)),
                "Arbitrary keyed output failed");
        var request = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 200);
        h.assertTrue(
                b.drain(request, FluidAction.SIMULATE).getAmount() == 200
                        && b.buffer(ICHOR).getFluidAmount() == 250,
                "Simulated explicit export mutated storage");
        h.assertTrue(
                b.drain(request, FluidAction.EXECUTE).getAmount() == 200
                        && b.buffer(ICHOR).getFluidAmount() == 50,
                "Explicit export did not resolve custom tank");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void moreThanFiveBuffersHaveStableRoundRobinAndPersistedCursor(GameTestHelper h) {
        var b = new ProductBuffers(() -> {});
        var defs = new TreeMap<ResourceLocation, ProductBufferDefinition>(
                Comparator.comparing(ResourceLocation::toString));
        for (int i = 0; i < 7; i++)
            defs.put(
                    ResourceLocation.parse("a:buffer_" + i),
                    definition(i % 2 == 0 ? "minecraft:water" : "minecraft:lava", 4000, false));
        b.configure(defs);
        for (var entry : defs.entrySet())
            produce(
                    b,
                    entry.getKey(),
                    new FluidStack(
                            entry.getValue().fluids().getFirst().equals("minecraft:water") ? Fluids.WATER : Fluids.LAVA,
                            100));
        h.assertTrue(
                b.getTanks() > 5
                        && b.drain(1, FluidAction.SIMULATE).is(Fluids.WATER)
                        && b.drain(1, FluidAction.EXECUTE).is(Fluids.WATER),
                "Stable sorted arbitration failed");
        var restored = new ProductBuffers(() -> {});
        restored.load(b.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(
                restored.drain(1, FluidAction.EXECUTE).is(Fluids.LAVA)
                        && restored.drain(1, FluidAction.EXECUTE).is(Fluids.WATER),
                "Keyed cursor did not survive load");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void keyedPersistencePreservesMetadataAndAmount(GameTestHelper h) {
        var b = buffers();
        b.inputHandler().fill(new FluidStack(Fluids.WATER, 725), FluidAction.EXECUTE);
        produce(b, ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 250));
        var saved = b.save(h.getLevel().registryAccess());
        var copy = new ProductBuffers(() -> {});
        copy.load(saved, h.getLevel().registryAccess());
        h.assertTrue(
                saved.getCompound("buffers").contains(ICHOR.toString())
                        && !saved.contains("meat")
                        && copy.buffer(ICHOR).getCapacity() == 1000
                        && copy.buffer(ICHOR).getFluidAmount() == 250
                        && copy.buffer(WATER).getFluidAmount() == 725,
                "Keyed state/metadata lost");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void legacyFiveStoresMigrateAndWriteOnlyDynamicState(GameTestHelper h) {
        var old = new CompoundTag();
        var products = new CompoundTag();
        var definitions = ProductBuffers.builtins();
        definitions.forEach((id, d) -> {
            var tank = new FluidTank(4000);
            tank.setFluid(new FluidStack(
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.get(
                            ResourceLocation.parse(d.fluids().getFirst())),
                    123));
            if (id.equals(ProductBuffers.MILK))
                old.put("fluidBuffer", tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
            else {
                String name = id.equals(ProductBuffers.CULTURE)
                        ? "culture"
                        : id.equals(ProductBuffers.FEEDING) ? "feeding" : id.getPath();
                products.put(name, tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
            }
        });
        old.put("products", products);
        var vat = new MultiblockVat(h.getLevel());
        vat.syncDataFrom(old, h.getLevel().registryAccess(), SyncReason.FullSync);
        var members = new ArrayList<net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity>();
        int index = 0;
        for (var d : definitions.values()) {
            var pos = new BlockPos(1 + index++ * 2, 1, 1);
            h.setBlock(
                    pos,
                    net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.state(
                            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.BARE));
            var member =
                    (net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity) h.getBlockEntity(pos);
            member.filter(d.fluids().getFirst());
            members.add(member);
        }
        vat.storage().compile(members, net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.EMPTY);
        vat.processFluids().bind(vat.storage());
        h.assertTrue(
                members.stream().allMatch(e -> e.tank.getFluidAmount() == 123),
                "Legacy migration did not conserve contents in physical buffers");
        var saved = vat.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                !saved.contains("fluidBuffer") && saved.getCompound("products").contains("buffers"),
                "New save retained fixed fields");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void assimilationOverflowAndFluidConflictNeverVoidContents(GameTestHelper h) {
        var orphan = buffers();
        orphan.inputHandler().fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        orphan.configure(Map.of());
        var receiver = new ProductBuffers(() -> {});
        receiver.configure(Map.of());
        receiver.assimilate(orphan);
        h.assertTrue(
                !receiver.compatible(WATER)
                        && receiver.inputHandler().fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0
                        && receiver.drain(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE)
                                        .getAmount()
                                == 100,
                "Assimilation reactivated a removed buffer");
        var a = buffers();
        var b = buffers();
        produce(a, ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000));
        produce(b, ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000));
        a.assimilate(b);
        h.assertTrue(
                a.buffer(ICHOR).getFluidAmount() == 2000
                        && !produce(a, ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1)),
                "Overflow lost or production escaped capacity");
        var incompatible = new ProductBuffers(() -> {});
        incompatible.configure(Map.of(ICHOR, definition("minecraft:lava", 1000, false)));
        produce(incompatible, ICHOR, new FluidStack(Fluids.LAVA, 300));
        a.assimilate(incompatible);
        h.assertTrue(
                a.diagnostics().stream().anyMatch(s -> s.contains("Conflicting"))
                        && a.drain(new FluidStack(Fluids.LAVA, 300), FluidAction.EXECUTE)
                                        .getAmount()
                                == 300
                        && a.drain(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 2000), FluidAction.EXECUTE)
                                        .getAmount()
                                == 2000,
                "Conflicting fluid was lost or mixed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void incompatibleReloadAndRemovedBufferRemainExportable(GameTestHelper h) {
        var b = buffers();
        b.inputHandler().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        b.configure(Map.of(WATER, definition("minecraft:lava", 100, false)));
        h.assertTrue(
                b.buffer(WATER).getFluidAmount() == 1000
                        && b.buffer(WATER).getCapacity() == 100
                        && !b.compatible(WATER)
                        && !b.diagnostics().isEmpty()
                        && !produce(b, WATER, new FluidStack(Fluids.LAVA, 1)),
                "Incompatible reload destroyed fluid or allowed production");
        var copy = new ProductBuffers(() -> {});
        copy.load(b.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        copy.configure(Map.of());
        h.assertTrue(
                copy.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE)
                                        .getAmount()
                                == 1000
                        && !copy.compatible(WATER),
                "Removed buffer contents not preserved/exportable");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void capacityReductionWaitsForExportAndInputDirectionIsGeneric(GameTestHelper h) {
        var b = buffers();
        h.assertTrue(
                b.inputHandler().fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE) == 3000
                        && b.inputHandler()
                                        .fill(
                                                new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 100),
                                                FluidAction.EXECUTE)
                                == 0,
                "Direction metadata ignored");
        b.configure(Map.of(WATER, definition("minecraft:water", 1000, true)));
        h.assertTrue(
                b.buffer(WATER).getFluidAmount() == 3000
                        && b.inputHandler().fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0,
                "Capacity reduction destroyed fluid or accepted overflow");
        b.drain(new FluidStack(Fluids.WATER, 2500), FluidAction.EXECUTE);
        h.assertTrue(produce(b, WATER, new FluidStack(Fluids.WATER, 500)), "Production failed to resume after export");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void itemSelectorsExactVanillaCommonTagAndAlternatives(GameTestHelper h) {
        h.assertTrue(
                ResourceSelectors.items(List.of("minecraft:iron_ingot")).test(new ItemStack(Items.IRON_INGOT)),
                "Exact selector failed");
        h.assertTrue(
                ResourceSelectors.items(List.of("#minecraft:logs")).test(new ItemStack(Items.OAK_LOG)),
                "Vanilla tag failed");
        h.assertTrue(
                ResourceSelectors.items(List.of("#c:ingots/iron")).test(new ItemStack(Items.IRON_INGOT)),
                "Common tag failed");
        var alternatives = ResourceSelectors.items(List.of("#minecraft:logs", "minecraft:gold_ingot"));
        h.assertTrue(
                alternatives.test(new ItemStack(Items.OAK_LOG))
                        && alternatives.test(new ItemStack(Items.GOLD_INGOT))
                        && !alternatives.test(new ItemStack(Items.STONE)),
                "Alternative list failed");
        h.assertTrue(
                new PassiveMobDefinition.Feed(List.of("#minecraft:logs"), 1).accepts(new ItemStack(Items.OAK_LOG)),
                "Feed did not use shared selector");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fluidSelectorsExactTagsAndArbitraryRegisteredFluid(GameTestHelper h) {
        h.assertTrue(
                ResourceSelectors.fluids(List.of("minecraft:lava")).test(new FluidStack(Fluids.LAVA, 1)),
                "Exact fluid failed");
        h.assertTrue(
                ResourceSelectors.fluids(List.of("#minecraft:water")).test(new FluidStack(Fluids.WATER, 1)),
                "Fluid tag failed");
        h.assertTrue(
                ResourceSelectors.fluids(List.of("#minecraft:water", "mobvats:culture_slurry"))
                        .test(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1)),
                "Mod fluid alternative failed");
        h.succeed();
    }

    private static JsonObject fixture(String entity) {
        return JsonParser.parseString("""
            {"entity":"%s","buffers":{
              "somepack:water":{"fluids":["#minecraft:water"],"input":true},
              "somepack:ichor":{"fluids":["mobvats:culture_slurry"],"capacity":1000}
             },"processes":[{"id":"somepack:gold","duration_ticks":6,
               "inputs":[{"type":"item","items":["#c:ingots/iron"],"count":2},
                         {"type":"fluid","fluids":["#minecraft:water"],"amount":500},
                         {"type":"xp","amount":60}],
               "outputs":[{"type":"item","item":"minecraft:gold_ingot","count":4},
                          {"type":"fluid","buffer":"somepack:ichor","fluid":"mobvats:culture_slurry","amount":250},
                          {"type":"xp","amount":240}]}]}
            """.formatted(entity)).getAsJsonObject();
    }

    private static Path directory() throws Exception {
        Files.createDirectories(Path.of("build"));
        return Files.createTempDirectory(Path.of("build"), "slice8-");
    }

    private static void write(Path dir, String name, JsonObject profile, String entity) throws Exception {
        var root = new JsonObject();
        root.add(entity, profile);
        MobProfileFixtures.write(dir, root);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void hostileProfileWithoutLifecycleLoadsAndCompilesPureData(GameTestHelper h) throws Exception {
        var dir = directory();
        write(dir, "fixture.json", fixture("minecraft:zombie"), "minecraft:zombie");
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        var d = loaded.definitions().get(ResourceLocation.parse("minecraft:zombie"));
        h.assertTrue(
                loaded.errors().isEmpty()
                        && d != null
                        && !d.lifecycle()
                        && d.bufferDefinitions().get(WATER).limit() == 4000
                        && PassiveProcessCompiler.compile(d).definitions().size() == 3,
                "Generic hostile profile requires species Java or passive lifecycle");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void invalidResourceDefinitionsRejectWholeFileWithSpecificDiagnostics(GameTestHelper h)
            throws Exception {
        String[] reasons = {
            "Unknown item",
            "Invalid selector ID",
            "Unknown fluid",
            "Undefined buffer",
            "incompatibility",
            "positive",
            "Duplicate/reserved",
            "Unsupported input",
            "Unsupported output",
            "unknown field"
        };

        for (int i = 0; i < reasons.length; i++) {
            var dir = directory();
            var p = fixture("minecraft:zombie");
            var process = p.getAsJsonArray("processes").get(0).getAsJsonObject();
            var input = process.getAsJsonArray("inputs");
            var output = process.getAsJsonArray("outputs");
            switch (i) {
                case 0 ->
                    input.get(0).getAsJsonObject().getAsJsonArray("items").set(0, new JsonPrimitive("missing:item"));
                case 1 -> input.get(0).getAsJsonObject().getAsJsonArray("items").set(0, new JsonPrimitive("#Bad Tag!"));
                case 2 -> output.get(1).getAsJsonObject().addProperty("fluid", "missing:fluid");
                case 3 -> output.get(1).getAsJsonObject().addProperty("buffer", "missing:buffer");
                case 4 -> output.get(1).getAsJsonObject().addProperty("fluid", "minecraft:lava");
                case 5 ->
                    p.getAsJsonObject("buffers")
                            .getAsJsonObject("somepack:ichor")
                            .addProperty("capacity", 0);
                case 6 -> p.getAsJsonArray("processes").add(process.deepCopy());
                case 7 -> input.get(0).getAsJsonObject().addProperty("type", "script");
                case 8 -> output.get(0).getAsJsonObject().addProperty("type", "script");
                case 9 -> process.addProperty("strategy", "produce_gold");
            }
            write(dir, "invalid_" + i + ".json", p, "minecraft:zombie");
            var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
            final int n = i;
            h.assertTrue(
                    !loaded.definitions().containsKey(ResourceLocation.parse("minecraft:zombie"))
                            && loaded.errors().stream()
                                    .anyMatch(s -> s.contains("minecraft/zombie.json") && s.contains(reasons[n])),
                    "Missing diagnostic: " + reasons[i] + " / " + loaded.errors());
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void duplicateJsonBuffersAreRejectedRatherThanSilentlyOverwritten(GameTestHelper h) throws Exception {
        var dir = directory();
        var text =
                "{\"minecraft:zombie\":{\"entity\":\"minecraft:zombie\",\"buffers\":{\"pack:one\":{\"fluids\":[\"minecraft:water\"]},\"pack:one\":{\"fluids\":[\"minecraft:lava\"]}}}}";
        Files.writeString(dir.resolve("duplicate.json"), text);
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().stream().anyMatch(s -> s.contains("Duplicate definition/field: pack:one")),
                "Duplicate buffer silently overwritten");
        h.succeed();
    }

    private static void rig(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        h.setBlock(FEED, Content.state(VatPartType.ItemPort));
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        Slice11Fixtures.resourceIo(h, O);
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
    }

    private static PassiveMobDefinition loadedFixture(GameTestHelper h) throws Exception {
        var dir = directory();
        write(dir, "fixture.json", fixture("minecraft:cow"), "minecraft:cow");
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(loaded.errors().isEmpty(), "Fixture invalid: " + loaded.errors());
        return loaded.definitions().get(ItemEssence.COW);
    }

    private static ProcessContext context(
            MultiblockVat vat, VatProcessEngine engine, MobProcessRegistry.Profile profile) {
        return new ProcessContext(
                engine,
                vat.getAssemblies().getFirst(),
                engine.state(vat.getAssemblies().getFirst().head().getBlockPos()),
                profile);
    }

    private static long outputCount(GameTestHelper h) {
        return outputStacks(h).stream().mapToLong(ItemStack::getCount).sum();
    }

    private static List<ItemStack> outputStacks(GameTestHelper h) {
        return List.of(O.offset(3, 0, 2), O.offset(2, 0, 3)).stream()
                .map(p -> ((VatItemPortEntity) h.getBlockEntity(p)).staging().getStackInSlot(0))
                .filter(p -> !p.isEmpty())
                .toList();
    }

    private static VatProcessEngine engine(MultiblockVat vat, PassiveMobDefinition d) {
        var registry = new MobProcessRegistry();
        registry.register(d.identity(), PassiveProcessCompiler.compile(d));
        var engine = new VatProcessEngine(vat, registry.freeze());
        engine.assembled();
        return engine;
    }

    private static void inputs(GameTestHelper h, VatProcessEngine engine) {
        ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        vat(h).processFluids().inputHandler().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        engine.addExperienceMb(60);
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void configuredTimedMixedOutputsCommitItemsFluidXpAtomically(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            inputs(h, engine);
            for (int i = 0; i < 10; i++) engine.tick();
            h.assertTrue(
                    (outputCount(h) == 0) && engine.experienceMb() == 60, "Start consumed input or produced early");
            for (int i = 0; i < 5; i++) engine.tick();
            h.assertTrue((outputCount(h) == 0), "Duration completed early");
            for (int i = 0; i < 5; i++) engine.tick();
            h.assertTrue(
                    outputCount(h) == 4
                            && outputStacks(h).getFirst().is(Items.GOLD_INGOT)
                            && vat.processFluids().buffer(ICHOR).getFluidAmount() == 250
                            && vat.processFluids().buffer(WATER).getFluidAmount() == 500
                            && engine.experienceMb() == 240
                            && ((VatItemPortEntity) h.getBlockEntity(FEED))
                                            .staging()
                                            .getStackInSlot(0)
                                            .getCount()
                                    == 1,
                    "Configured mixed transaction incorrect");
            h.assertTrue(vat.statusSnapshot().pages().get(1).contains("somepack:ichor"), "Dynamic diagnostics missing");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void capacityFailureAndLateBackpressureConsumeNoInputs(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            inputs(h, engine);
            var runtime = new ResourceProcessRuntime(d.processes().getFirst());
            var c = context(vat, engine, PassiveProcessCompiler.compile(d));
            produce(vat.processFluids(), ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 4000));
            var service = vat.getAssemblies()
                    .getFirst()
                    .drain()
                    .service()
                    .tank(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1));
            service.fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000), FluidAction.EXECUTE);
            h.assertTrue(
                    runtime.plan(c).problem() == ProcessResult.Reason.OUTPUT_BLOCKED
                            && engine.experienceMb() == 60
                            && vat.processFluids().buffer(WATER).getFluidAmount() == 1000
                            && ((VatItemPortEntity) h.getBlockEntity(FEED))
                                            .staging()
                                            .getStackInSlot(0)
                                            .getCount()
                                    == 3,
                    "Blocked plan consumed inputs");
            vat.processFluids().drain(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 4000), FluidAction.EXECUTE);
            for (int i = 0; i < 10; i++) engine.tick();
            produce(vat.processFluids(), ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 4000));
            for (int i = 0; i < 10; i++) engine.tick();
            h.assertTrue(
                    engine.experienceMb() == 60
                            && vat.processFluids().buffer(WATER).getFluidAmount() == 1000
                            && (outputCount(h) == 0),
                    "Late capacity failure partially committed");
            vat.processFluids().drain(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 4000), FluidAction.EXECUTE);
            for (int i = 0; i < 10; i++) engine.tick();
            h.assertTrue(
                    engine.experienceMb() == 240 && outputCount(h) == 4,
                    "Ready transaction did not recover after export");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void repeatedInputsAndSameBufferTransformUseOneCombinedStep(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            inputs(h, engine);
            var c = context(vat, engine, PassiveProcessCompiler.compile(d));
            var process = new PassiveMobDefinition.ResourceProcess(
                    "pack:combined",
                    1,
                    null,
                    null,
                    List.of(
                            new PassiveMobDefinition.ResourceInput("item", List.of("minecraft:iron_ingot"), null, 2, 0),
                            new PassiveMobDefinition.ResourceInput("item", List.of("#c:ingots/iron"), null, 2, 0)),
                    List.of(new PassiveMobDefinition.ResourceOutput("xp", null, null, null, 0, 20)));
            h.assertTrue(
                    new ResourceProcessRuntime(process).plan(c).problem() == ProcessResult.Reason.WAITING_INPUT,
                    "Repeated inputs double-spent the same slot");
            process = new PassiveMobDefinition.ResourceProcess(
                    "pack:combined",
                    1,
                    null,
                    null,
                    List.of(
                            new PassiveMobDefinition.ResourceInput("fluid", null, List.of("minecraft:water"), 0, 500),
                            new PassiveMobDefinition.ResourceInput("fluid", null, List.of("#minecraft:water"), 0, 100)),
                    List.of(
                            new PassiveMobDefinition.ResourceOutput(
                                    "fluid", WATER.toString(), "minecraft:water", null, 0, 200),
                            new PassiveMobDefinition.ResourceOutput("xp", null, null, null, 0, 20)));
            var plan = new ResourceProcessRuntime(process).plan(c);
            h.assertTrue(
                    plan.problem() == null
                            && plan.transaction().commit()
                            && vat.processFluids().buffer(WATER).getFluidAmount() == 600
                            && engine.experienceMb() == 80,
                    "Combined input/output changes overwrote or duplicated a resource");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void staleResourcePlanAndItemBacklogPreventPartialCommit(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            inputs(h, engine);
            var runtime = new ResourceProcessRuntime(d.processes().getFirst());
            var c = context(vat, engine, PassiveProcessCompiler.compile(d));
            var plan = runtime.plan(c);
            ((VatItemPortEntity) h.getBlockEntity(FEED))
                    .staging()
                    .setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
            h.assertTrue(
                    !plan.transaction().commit()
                            && engine.experienceMb() == 60
                            && vat.processFluids().buffer(WATER).getFluidAmount() == 1000
                            && vat.processFluids().buffer(ICHOR).isEmpty(),
                    "Stale plan partly committed");
            for (var p : List.of(O.offset(3, 0, 2), O.offset(2, 0, 3)))
                ((VatItemPortEntity) h.getBlockEntity(p)).staging().setStackInSlot(0, new ItemStack(Items.STONE, 64));
            h.assertTrue(
                    runtime.plan(c).problem() == ProcessResult.Reason.OUTPUT_BLOCKED && engine.experienceMb() == 60,
                    "Item backlog failed to block mixed transaction");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void exactModFluidInputAndFixedFluidOnlyOutputNeedNoStrategy(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            var c = context(vat, engine, PassiveProcessCompiler.compile(d));
            produce(vat.processFluids(), ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 500));
            var process = new PassiveMobDefinition.ResourceProcess(
                    "pack:fluid",
                    1,
                    null,
                    null,
                    List.of(new PassiveMobDefinition.ResourceInput(
                            "fluid", null, List.of("mobvats:culture_slurry"), 0, 250)),
                    List.of(new PassiveMobDefinition.ResourceOutput(
                            "fluid", WATER.toString(), "minecraft:water", null, 0, 125)));
            var plan = new ResourceProcessRuntime(process).plan(c);
            h.assertTrue(
                    plan.problem() == null
                            && plan.transaction().commit()
                            && vat.processFluids().buffer(ICHOR).getFluidAmount() == 250
                            && vat.processFluids().buffer(WATER).getFluidAmount() == 125,
                    "Arbitrary registered fluid transform failed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void liveProfileCapacityAndSelectorReloadPreserveCommittedFluid(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            engine(vat, d);
            produce(vat.processFluids(), ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 500));
            var defs = new HashMap<>(d.buffers());
            defs.put(ICHOR.toString(), definition("minecraft:lava", 100, false));
            var changed =
                    new PassiveMobDefinition(d.entity(), null, 0, List.of(), null, null, null, null, defs, List.of());
            engine(vat, changed);
            h.assertTrue(
                    vat.storage().clusters().stream()
                                            .filter(c -> c.tank().getFluid().is(ProductFluids.CULTURE_SLURRY.get()))
                                            .mapToInt(c -> c.tank().getFluidAmount())
                                            .sum()
                                    == 500
                            && vat.processFluids()
                                            .drain(
                                                    new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 500),
                                                    FluidAction.EXECUTE)
                                            .getAmount()
                                    == 500,
                    "Live profile reload lost committed fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void cachedItemAndFluidTagsObserveRebinding(GameTestHelper h) {
        var itemRegistry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var fluidRegistry = net.minecraft.core.registries.BuiltInRegistries.FLUID;
        var itemTag = net.minecraft.tags.ItemTags.LOGS;
        var fluidTag = net.minecraft.tags.FluidTags.WATER;
        var items = new HashMap<net.minecraft.tags.TagKey<Item>, List<Holder<Item>>>();
        itemRegistry
                .getTags()
                .forEach(pair ->
                        items.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var fluids = new HashMap<
                net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>,
                List<Holder<net.minecraft.world.level.material.Fluid>>>();
        fluidRegistry
                .getTags()
                .forEach(pair ->
                        fluids.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var itemSelector = ResourceSelectors.items(List.of("#minecraft:logs"));
        var fluidSelector = ResourceSelectors.fluids(List.of("#minecraft:water"));
        try {
            var changedItems = new HashMap<>(items);
            changedItems.put(itemTag, List.of(itemRegistry.wrapAsHolder(Items.DIAMOND)));
            itemRegistry.bindTags(changedItems);
            var changedFluids = new HashMap<>(fluids);
            changedFluids.put(fluidTag, List.of(fluidRegistry.wrapAsHolder(Fluids.LAVA)));
            fluidRegistry.bindTags(changedFluids);
            h.assertTrue(
                    itemSelector.test(new ItemStack(Items.DIAMOND))
                            && !itemSelector.test(new ItemStack(Items.OAK_LOG))
                            && fluidSelector.test(new FluidStack(Fluids.LAVA, 1))
                            && !fluidSelector.test(new FluidStack(Fluids.WATER, 1)),
                    "Cached selectors retained stale tag membership");
        } finally {
            itemRegistry.bindTags(items);
            fluidRegistry.bindTags(fluids);
        }
        h.assertTrue(
                itemSelector.test(new ItemStack(Items.OAK_LOG)) && fluidSelector.test(new FluidStack(Fluids.WATER, 1)),
                "Tag restoration failed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void genericPortsAcceptAndExportAnArbitraryProfileBuffer(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        var position = O.offset(0, 1, 2);
        h.setBlock(position, Content.state(VatPartType.FluidPort));
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            engine(vat, d);
            var port = (VatFluidPortEntity) h.getBlockEntity(position);
            port.setIoDirection(IoDirection.Input);
            var input = h.getLevel()
                    .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            h.absolutePos(position),
                            Direction.WEST);
            h.assertTrue(
                    input != null && input.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE) == 500,
                    "Generic input port rejected declared arbitrary buffer");
            produce(vat.processFluids(), ICHOR, new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 250));
            port.setIoDirection(IoDirection.Output);
            var output = h.getLevel()
                    .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            h.absolutePos(position),
                            Direction.WEST);
            h.assertTrue(
                    output != null
                            && output.getTanks() >= 8
                            && output.drain(
                                                    new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 250),
                                                    FluidAction.EXECUTE)
                                            .getAmount()
                                    == 250
                            && output.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0,
                    "Dynamic output port/policy failed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void arbitraryEntityIdentityRunsConfiguredTransformsWithoutSpeciesCode(GameTestHelper h)
            throws Exception {
        var dir = directory();
        write(dir, "axolotl.json", fixture("minecraft:axolotl"), "minecraft:axolotl");
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        var d = loaded.definitions().get(ResourceLocation.parse("minecraft:axolotl"));
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var head = (VatHeadEntity) h.getBlockEntity(HEAD);
            var original = head.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            var tag = original.copy();
            tag.putString("entity", "minecraft:axolotl");
            try {
                // Identity transport retains the preformed geometry/topology; all processing uses the new ID.
                head.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
                var engine = engine(vat, d);
                inputs(h, engine);
                for (int i = 0; i < 20; i++) engine.tick();
                h.assertTrue(
                        outputCount(h) == 4
                                && vat.processFluids().buffer(ICHOR).getFluidAmount() == 250
                                && engine.experienceMb() == 240
                                && head.getIdentity().equals(d.identity()),
                        "Arbitrary configured entity failed generic scheduling/resources");
            } finally {
                head.syncDataFrom(original, h.getLevel().registryAccess(), SyncReason.FullSync);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void itemConsumptionSpansResolvedInputsWithoutDoubleSpending(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        var other = O.offset(4, 1, 2);
        h.setBlock(other, Content.state(VatPartType.ItemPort));
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            inputs(h, engine);
            ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            ((VatItemPortEntity) h.getBlockEntity(other)).staging().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            for (int i = 0; i < 20; i++) engine.tick();
            h.assertTrue(
                    outputCount(h) == 4
                            && ((VatItemPortEntity) h.getBlockEntity(FEED))
                                    .staging()
                                    .getStackInSlot(0)
                                    .isEmpty()
                            && ((VatItemPortEntity) h.getBlockEntity(other))
                                    .staging()
                                    .getStackInSlot(0)
                                    .isEmpty(),
                    "Consumption did not span eligible input endpoints");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void itemOnlyAndXpOnlyOutputsRemainOrdinaryTransactions(GameTestHelper h) throws Exception {
        var d = loadedFixture(h);
        rig(h);
        h.runAtTickTime(30, () -> {
            var vat = vat(h);
            var engine = engine(vat, d);
            var c = context(vat, engine, PassiveProcessCompiler.compile(d));
            var xp = new PassiveMobDefinition.ResourceProcess(
                    "pack:xp",
                    1,
                    null,
                    null,
                    List.of(),
                    List.of(new PassiveMobDefinition.ResourceOutput("xp", null, null, null, 0, 251)));
            var plan = new ResourceProcessRuntime(xp).plan(c);
            h.assertTrue(
                    plan.transaction().commit()
                            && engine.experienceMb() == 251
                            && plan.metrics().get("experienceProducedMb") == 251,
                    "XP-only fixed transform rounded mB or metrics");
            var item = new PassiveMobDefinition.ResourceProcess(
                    "pack:items",
                    1,
                    null,
                    null,
                    List.of(new PassiveMobDefinition.ResourceInput("xp", null, null, 0, 60)),
                    List.of(new PassiveMobDefinition.ResourceOutput("item", null, null, "minecraft:diamond", 65, 0)));
            plan = new ResourceProcessRuntime(item).plan(c);
            h.assertTrue(
                    plan.transaction().commit()
                            && engine.experienceMb() == 191
                            && outputCount(h) == 65
                            && outputStacks(h).size() == 2
                            && outputStacks(h).stream().allMatch(s -> s.is(Items.DIAMOND) && s.getCount() <= 64),
                    "Item-only output did not split/store fixed stacks or retain XP mB");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void unknownTagsDiagnoseWithoutFreezingFutureMembership(GameTestHelper h) throws Exception {
        var dir = directory();
        var profile = fixture("minecraft:zombie");
        profile.getAsJsonArray("processes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("inputs")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("items")
                .set(0, new JsonPrimitive("#somepack:future_input"));
        write(dir, "unknown_tag.json", profile, "minecraft:zombie");
        var messages = new ArrayList<String>();
        var loaded = PassiveMobDefinitionRegistry.load(dir, messages::add);
        h.assertTrue(
                loaded.errors().isEmpty()
                        && messages.stream()
                                .anyMatch(s -> s.contains("Unknown or empty item tag: #somepack:future_input")),
                "Missing tag was undiagnosed or treated as permanent membership failure");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void mixerSharesExactItemAndTaggedFluidIngredients(GameTestHelper h) {
        var state = new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerState(() -> {});
        state.fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        state.itemInput().insertItem(0, new ItemStack(Items.WHEAT, 2), false);
        var recipe = new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeDefinition(
                "test_shared",
                List.of(
                        new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeDefinition
                                .FluidInput(List.of("#minecraft:water"), 500)),
                List.of(
                        new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeDefinition
                                .ItemInput(List.of("minecraft:wheat"), 2)),
                ProductFluids.FEEDING_SLURRY,
                250,
                10,
                null);
        h.assertTrue(
                state.commit(recipe) && state.amount(0) == 500 && state.amount(3) == 250 && state.plantCount() == 0,
                "Mixer did not consume shared exact/tag selectors");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void invalidStageAndCanonicalDuplicateBufferIdsRejectFile(GameTestHelper h) throws Exception {
        var dir = directory();
        var profile = fixture("minecraft:zombie");
        profile.getAsJsonArray("processes").get(0).getAsJsonObject().addProperty("requires_stage", "BOGUS");
        write(dir, "stage.json", profile, "minecraft:zombie");
        var stageLoaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                stageLoaded.errors().stream().anyMatch(s -> s.contains("unsupported value: BOGUS"))
                        && !stageLoaded.definitions().containsKey(ResourceLocation.parse("minecraft:zombie")),
                "Invalid stage escaped validation");
        profile = fixture("minecraft:zombie");
        profile.getAsJsonObject("buffers").add("water", JsonParser.parseString("{\"fluids\":[\"minecraft:water\"]}"));
        profile.getAsJsonObject("buffers")
                .add("minecraft:water", JsonParser.parseString("{\"fluids\":[\"minecraft:water\"]}"));
        write(dir, "canonical.json", profile, "minecraft:zombie");
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().stream().anyMatch(s -> s.contains("Duplicate canonical buffer ID"))
                        && !loaded.definitions().containsKey(ResourceLocation.parse("minecraft:zombie")),
                "Invalid stage or canonical duplicate escaped validation");
        h.succeed();
    }
}
