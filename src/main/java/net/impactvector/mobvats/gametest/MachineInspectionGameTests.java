package net.impactvector.mobvats.gametest;

import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.MachineInspection;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** Integration-independent acceptance: these tests run on the Jade-free GameTest server. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MachineInspectionGameTests {
    private static String inspect(GameTestHelper h, BlockPos pos) {
        return String.join(
                "\n",
                MachineInspection.lines(h.getLevel(), h.absolutePos(pos)).stream()
                        .map(Component::getString)
                        .toList());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void buffersAndPortsExposeActualLocalContentsWithoutMutation(GameTestHelper h) {
        var bufferPos = new BlockPos(1, 1, 1);
        h.setBlock(bufferPos, FluidBufferContent.state(ConduitCladding.BARE));
        var buffer = (FluidBufferEntity) h.getBlockEntity(bufferPos);
        buffer.filter("minecraft:water");
        buffer.tank.fill(new FluidStack(Fluids.WATER, 2750), FluidAction.EXECUTE);
        var text = inspect(h, bufferPos);
        h.assertTrue(
                text.contains("2750 / 4000 mB") && text.contains("minecraft:water"),
                "Buffer contents/filter missing: " + text);
        h.assertTrue(buffer.tank.getFluidAmount() == 2750, "Inspection mutated buffer");
        var portPos = bufferPos.east();
        h.setBlock(portPos, Content.state(VatPartType.ItemPort));
        var port = (VatItemPortEntity) h.getBlockEntity(portPos);
        port.staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 32));
        text = inspect(h, portPos);
        h.assertTrue(
                text.contains("Direction: Input") && text.contains("Wheat x32") && text.contains("99 items / 1 slots"),
                "Local port contents missing: " + text);
        h.assertTrue(port.staging().getStackInSlot(0).getCount() == 32, "Inspection mutated items");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void compoundsReuseExistingCompositionPresentation(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, FluidBufferContent.state(ConduitCladding.BARE));
        var buffer = (FluidBufferEntity) h.getBlockEntity(pos);
        var stack =
                new FluidStack(net.impactvector.mobvats.gamecontent.fluid.ProductFluids.COMPONENT_FLUID.get(), 1000);
        stack.set(
                CompoundFluids.COMPOSITION.get(),
                new CompoundComposition(
                        List.of(new CompoundComposition.Entry(CompoundComposition.Kind.ITEM, "minecraft:wheat", 32))));
        buffer.tank.fill(stack, FluidAction.EXECUTE);
        var expected = new java.util.ArrayList<Component>();
        CompoundBucketItem.appendComposition(stack, expected);
        var text = inspect(h, pos);
        for (var line : expected)
            h.assertTrue(
                    text.contains(line.getString()), "Compound presentation differs from existing display: " + text);
        h.assertTrue(FluidStack.isSameFluidSameComponents(stack, buffer.tank.getFluid()), "Composition mutated");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void controllersUseFormationErrorsAndLeaveGlassQuiet(GameTestHelper h) {
        MixerGameTests.rig(h);
        var controller = new BlockPos(1, 2, 3);
        h.setBlock(new BlockPos(3, 3, 3), Blocks.AIR);
        h.runAtTickTime(35, () -> {
            var mixer = ((MixerControllerEntity) h.getBlockEntity(controller))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(!mixer.isAssembled(), "Invalid fixture formed");
            var reason = mixer.getLastError().orElseThrow().getChatMessage().getString();
            var text = inspect(h, controller);
            h.assertTrue(
                    text.equals("Mixer: Unformed\nReason: " + reason),
                    "Formation presentation differs from validator: " + text);
            h.assertTrue(inspect(h, new BlockPos(3, 3, 1)).isEmpty(), "Glass leaked machine state");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void conduitSummaryUsesCompiledComponentAndToolUsesAssignedSpecimen(GameTestHelper h) {
        ConduitGameTests.routedRig(h);
        BlockPos conduit = new BlockPos(3, 1, 5), toolPos = new BlockPos(3, 2, 5), headPos = new BlockPos(3, 3, 3);
        h.runAtTickTime(35, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(headPos);
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(vat.isAssembled(), "Routed fixture unformed");
            var topology = vat.getTopology().conduits();
            var component = topology.componentAt(h.absolutePos(conduit)).orElseThrow();
            var text = inspect(h, conduit);
            for (var role : ConduitTopology.Role.values()) {
                var label = switch (role) {
                    case PRODUCER -> "Producers";
                    case CONSUMER -> "Consumers";
                    case STORE -> "Stores";
                };
                h.assertTrue(
                        text.contains(label + ": " + component.with(role).size()), "Wrong component count: " + text);
            }
            var tool = (VatToolPortEntity) h.getBlockEntity(toolPos);
            tool.setHeldItem(new ItemStack(Items.BUCKET));
            vat.rebuildTopology();
            text = inspect(h, toolPos);
            h.assertTrue(
                    text.contains("Target: Cow @ " + h.absolutePos(headPos).toShortString())
                            && text.contains("Tool/Input: Bucket")
                            && !text.contains("Operation: none"),
                    "Tool definition/assignment missing: " + text);
            h.assertTrue(
                    topology == vat.getTopology().conduits()
                            || vat.getTopology()
                                    .conduits()
                                    .componentAt(h.absolutePos(conduit))
                                    .isPresent(),
                    "Inspection invalidated topology");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void burningFuelPresentationSurvivesReloadAfterFuelConsumption(GameTestHelper h) {
        var origin = new BlockPos(1, 1, 1);
        var top = MachineFixtures.mixer(h::setBlock, origin, 5, 3, 5, 1, 1, false);
        var pos = top.east();
        h.setBlock(pos, MixerContent.state(MixerPartType.CombustionDrive));
        h.runAtTickTime(35, () -> {
            var drive = (MixerCombustionDriveEntity) h.getBlockEntity(pos);
            var mixer = drive.getMultiblockController().orElseThrow();
            drive.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            h.assertTrue(drive.driveTick(mixer), "Could not ignite");
            var saved = drive.saveFuel(h.getLevel().registryAccess());
            drive.loadFuel(saved, h.getLevel().registryAccess());
            var text = inspect(h, pos);
            h.assertTrue(
                    text.contains("Fuel: Coal") && text.contains("Burn: 1599 / 1600"),
                    "Consumed fuel lost inspection state: " + text);
            h.assertTrue(
                    drive.fuel().getStackInSlot(0).isEmpty() && drive.burnTicks() == 1599,
                    "Inspection changed burning state");
            h.succeed();
        });
    }
}
