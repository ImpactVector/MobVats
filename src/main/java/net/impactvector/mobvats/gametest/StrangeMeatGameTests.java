package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class StrangeMeatGameTests {
    @GameTest(template = "empty", timeoutTicks = 10)
    public static void worldPumpRoundTripPreservesRegisteredIdentity(GameTestHelper h) {
        var pos = new BlockPos(3, 3, 3);
        // Enclose the placement cell so the pump's bounded search has one destination.
        for (var direction : Direction.values()) h.setBlock(pos.relative(direction), Blocks.STONE);
        var tank = new FluidTank(2000);
        tank.fill(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 1000), FluidAction.EXECUTE);
        var endpoint = new net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps.Endpoint() {
            public boolean accepts(FluidStack s) {
                return s.is(ProductFluids.STRANGE_MEAT.get());
            }

            public void status(String s) {}
        };
        h.assertTrue(
                net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps.push(
                                h.getLevel(), h.absolutePos(pos), tank, endpoint)
                        && tank.isEmpty(),
                "Pump did not place Strange Meat");
        h.assertTrue(
                h.getBlockState(pos).getFluidState().getType() == ProductFluids.STRANGE_MEAT.get(),
                "Push Pump converted fluid identity");
        h.assertTrue(
                net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps.pull(
                                h.getLevel(), h.absolutePos(pos), tank, endpoint)
                        && tank.getFluid().is(ProductFluids.STRANGE_MEAT.get())
                        && tank.getFluidAmount() == 1000
                        && h.getBlockState(pos).isAir(),
                "Pull Pump converted fluid identity or lost volume");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void standardTransfersAndBufferPersistencePreserveIdentity(GameTestHelper h) {
        var fluid = ProductFluids.STRANGE_MEAT.get();
        var stack = new FluidStack(fluid, 1000);
        stack.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(
                        List.of(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:meat", 1))));
        var from = new BlockPos(2, 2, 2);
        var to = new BlockPos(6, 2, 2);
        h.setBlock(from, FluidBufferContent.block());
        h.setBlock(to, FluidBufferContent.block());
        var a = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(from), Direction.UP);
        var b = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(to), Direction.UP);
        h.assertTrue(a != null && b != null, "Missing standard buffer capabilities");
        h.assertTrue(
                a.fill(stack, FluidAction.SIMULATE) == 1000
                        && a.getFluidInTank(0).isEmpty(),
                "Simulated fill mutated buffer");
        h.assertTrue(a.fill(stack, FluidAction.EXECUTE) == 1000, "Strange Meat rejected by buffer");
        var transfer = FluidUtil.tryFluidTransfer(b, a, 1000, false);
        h.assertTrue(
                FluidStack.isSameFluidSameComponents(transfer, stack)
                        && b.getFluidInTank(0).isEmpty()
                        && a.getFluidInTank(0).getAmount() == 1000,
                "Transfer simulation changed identity/storage");
        transfer = FluidUtil.tryFluidTransfer(b, a, 1000, true);
        h.assertTrue(
                FluidStack.isSameFluidSameComponents(transfer, stack)
                        && a.getFluidInTank(0).isEmpty()
                        && b.getFluidInTank(0).getAmount() == 1000,
                "Standard transfer changed identity/storage");
        var ordinary = new FluidTank(2000);
        ordinary.fill(new FluidStack(ProductFluids.MEAT.get(), 1000), FluidAction.EXECUTE);
        h.assertTrue(
                FluidUtil.tryFluidTransfer(ordinary, b, 1000, true).isEmpty(), "Mixed Strange Meat with edible Meat");
        h.assertTrue(
                b.drain(new FluidStack(ProductFluids.MEAT.get(), 1000), FluidAction.EXECUTE)
                        .isEmpty(),
                "Meat request drained Strange Meat");
        var registries = h.getLevel().registryAccess();
        var restored = FluidStack.parse(registries, b.getFluidInTank(0).save(registries))
                .orElseThrow();
        h.assertTrue(
                FluidStack.isSameFluidSameComponents(restored, stack), "FluidStack codec changed identity/components");
        var buffer = (FluidBufferEntity) h.getBlockEntity(to);
        var saved = buffer.saveWithFullMetadata(registries);
        buffer.tank.setFluid(FluidStack.EMPTY);
        buffer.loadWithComponents(saved, registries);
        h.assertTrue(
                FluidStack.isSameFluidSameComponents(buffer.tank.getFluid(), stack)
                        && buffer.tank.getFluidAmount() == 1000,
                "Buffer persistence changed fluid");
        // Deliberately remove optional composition: identity alone still prevents edible equivalence.
        restored.remove(CompoundFluids.COMPOSITION.get());
        h.assertTrue(
                restored.is(fluid) && !restored.is(ProductFluids.MEAT.get()) && !CompoundFluids.isCompound(restored),
                "Metadata loss converted Strange Meat to ordinary Meat");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void worldFlowRemainsStrangeMeat(GameTestHelper h) {
        var origin = new BlockPos(5, 3, 5);
        for (int x = 2; x <= 8; x++) for (int z = 2; z <= 8; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        var source = ProductFluids.STRANGE_MEAT.get();
        h.setBlock(origin, source.defaultFluidState().createLegacyBlock());
        h.runAfterDelay(30, () -> {
            var flowing = h.getLevel().getFluidState(h.absolutePos(origin.east()));
            h.assertTrue(
                    !flowing.isEmpty()
                            && !flowing.isSource()
                            && flowing.getType() == source.getFlowing()
                            && source.isSame(flowing.getType())
                            && !ProductFluids.MEAT.get().isSame(flowing.getType()),
                    "World flow lost Strange Meat identity");
            h.succeed();
        });
    }

    private static JsonObject resource(String path) throws Exception {
        try (var input = StrangeMeatGameTests.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void creativeLocalizationAndProceduralPresentation(GameTestHelper h) throws Exception {
        var fluid = ProductFluids.STRANGE_MEAT.get();
        h.assertTrue(
                fluid.getFluidType() == ProductFluids.STRANGE_MEAT_TYPE.get()
                        && fluid.getFluidType() != ProductFluids.MEAT_TYPE.get(),
                "Shared FluidType identity");
        var lang = resource("assets/mobvats/lang/en_us.json");
        h.assertTrue(
                lang.get(fluid.getFluidType().getDescriptionId()).getAsString().equals("Strange Meat")
                        && lang.get(ProductFluids.STRANGE_MEAT_BUCKET.get().getDescriptionId())
                                .getAsString()
                                .equals("Strange Meat Bucket"),
                "Missing Strange Meat localization");
        var model = resource("assets/mobvats/models/item/strange_meat_bucket.json");
        h.assertTrue(
                model.get("fluid").getAsString().equals("mobvats:strange_meat")
                        && model.get("loader").getAsString().equals("neoforge:fluid_container"),
                "Wrong bucket presentation");
        resource("assets/mobvats/blockstates/strange_meat.json");
        try (var input = StrangeMeatGameTests.class
                .getClassLoader()
                .getResourceAsStream("assets/mobvats/textures/block/procedural/master/strange_meat.png")) {
            var image = ImageIO.read(Objects.requireNonNull(input));
            h.assertTrue(image.getWidth() == 16 && image.getHeight() == 16, "Wrong texture dimensions");
            var palette = new HashSet<Integer>();
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++) {
                    int pixel = image.getRGB(x, y);
                    h.assertTrue((pixel >>> 24) == 255, "Strange Meat must be opaque");
                    palette.add(pixel);
                }
            h.assertTrue(
                    palette.equals(Set.of(0xff69463e, 0xff80564d, 0xff9b7366, 0xff565744, 0xff422f2b)),
                    "Rotten flesh palette changed");
        }
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.parse("mobvats:mobvats"));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        h.assertTrue(
                tab.getDisplayItems().stream()
                                .filter(s -> s.is(ProductFluids.STRANGE_MEAT_BUCKET.get()))
                                .count()
                        == 1,
                "Strange Meat bucket missing/duplicated in Creative");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void explicitBlenderOutputConfigurationLoadsWithoutChangingDefaults(GameTestHelper h)
            throws Exception {
        Files.createDirectories(Path.of("build"));
        var dir = Files.createTempDirectory(Path.of("build"), "strange-meat-config-");
        var cow = PassiveMobDefinitionRegistry.JSON
                .toJsonTree(PassiveDefaults.data().get("minecraft:cow"))
                .getAsJsonObject();
        cow.getAsJsonObject("harvest").addProperty("blender", "mobvats:strange_meat");
        var root = new JsonObject();
        root.add("minecraft:cow", cow);
        MobProfileFixtures.write(dir, root);
        var loaded = PassiveMobDefinitionRegistry.load(dir, s -> {});
        h.assertTrue(
                loaded.errors().isEmpty()
                        && loaded.definitions()
                                .get(ResourceLocation.parse("minecraft:cow"))
                                .harvest()
                                .blender()
                                .equals("mobvats:strange_meat"),
                "Explicit Strange Meat output rejected: " + loaded.errors());
        for (var d : PassiveDefaults.data().values()) {
            if (d.lifecycle())
                h.assertTrue(
                        d.harvest().blender().equals(d.hostile() ? "mobvats:strange_meat" : "mobvats:meat"),
                        "Classified Blender default changed");
            else h.assertTrue(d.harvest() == null, "New hostile production was introduced");
        }
        h.succeed();
    }

    private StrangeMeatGameTests() {}
}
