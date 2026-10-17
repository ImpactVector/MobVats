package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class WorldRedstoneControlGameTests {
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void operatorLeverLeavesFeedHopperRunning(GameTestHelper h) {
        var o = new BlockPos(4, 1, 4);
        MachineFixtures.vat(h::setBlock, o, 5, 5, 4);
        h.setBlock(o.offset(1, 1, 0), Content.state(VatPartType.Glass));
        var c = o.offset(0, 1, 1);
        h.setBlock(c, Content.state(VatPartType.Controller));
        var head = (VatHeadEntity) h.getBlockEntity(o.offset(2, 2, 2));
        head.encode(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"));
        MachineFixtures.specimen(h::setBlock, o.offset(2, 2, 2), head.geometry());
        var hopper = o.offset(2, 1, -1);
        h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        h.runAtTickTime(35, () -> {
            var controller = ((VatControllerEntity) h.getBlockEntity(c))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(controller.isAssembled() && controller.processingEnabled(), "Unpowered Vat not operational");
            var lever = WorldRedstoneControls.install(
                    h.getLevel(), new StringBuilder(), "operator", h.absolutePos(c), Direction.WEST);
            h.assertTrue(h.getBlockState(c.west()).isAir(), "Controller GUI face is obstructed");
            h.assertTrue(lever.getY() == h.absolutePos(c).getY(), "Lever must sit beside the controller");
            h.assertTrue(
                    h.getLevel().getBlockState(lever.east()).is(Content.block(VatPartType.Casing)),
                    "Lever is not mounted on the Vat wall");
            h.assertTrue(
                    controller.isMachineActive() && !controller.processingEnabled(),
                    "Lever must preserve operator preference");
            h.assertTrue(
                    h.getBlockState(hopper).getValue(HopperBlock.ENABLED), "Feed hopper locked by controller lever");
            toggle(h, lever);
            h.assertTrue(controller.processingEnabled(), "Lever OFF did not resume Vat");
            toggle(h, lever);
            h.assertTrue(!controller.processingEnabled(), "Lever ON did not pause Vat");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void mixerLeverLeavesPortUnpowered(GameTestHelper h) {
        var o = new BlockPos(4, 1, 4);
        MachineFixtures.mixer(h::setBlock, o, 5, 4, 5, 3, 2, true);
        var c = o.offset(0, 1, 2);
        var port = o.offset(2, 1, 0);
        h.setBlock(port, MixerContent.state(MixerPartType.FluidPort));
        h.runAtTickTime(35, () -> {
            var controller = ((MixerControllerEntity) h.getBlockEntity(c))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(controller.isAssembled() && controller.processingEnabled(), "Unpowered Mixer not operational");
            var lever = WorldRedstoneControls.install(
                    h.getLevel(), new StringBuilder(), "mixer", h.absolutePos(c), Direction.WEST);
            h.assertTrue(h.getBlockState(c.west()).isAir(), "Controller GUI face is obstructed");
            h.assertTrue(lever.getY() == h.absolutePos(c).getY(), "Lever must sit beside the controller");
            h.assertTrue(
                    h.getLevel().getBlockState(lever.east()).is(MixerContent.block(MixerPartType.Casing)),
                    "Lever is not mounted on the Mixer wall");
            h.assertTrue(controller.isMachineActive() && !controller.processingEnabled(), "Mixer not paused");
            h.assertTrue(!h.getLevel().hasNeighborSignal(h.absolutePos(port)), "Port received controller power");
            toggle(h, lever);
            h.assertTrue(controller.processingEnabled(), "Lever OFF did not resume Mixer");
            h.succeed();
        });
    }

    private static void toggle(GameTestHelper h, BlockPos pos) {
        ((LeverBlock) Blocks.LEVER).pull(h.getLevel().getBlockState(pos), h.getLevel(), pos, null);
    }
}
