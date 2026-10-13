package net.impactvector.mobvats.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Uses the same builders as the dedicated area; normal server ticks perform every transfer. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class CreativeProductionGameTests {
    private static void run(GameTestHelper h, String recipe, boolean vat) {
        var rig = CreativeProductionFixtures.mixer(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 2)), recipe, vat);
        h.onEachTick(rig::observe);
        h.runAtTickTime(4700, () -> {
            h.assertTrue(rig.verify().startsWith("PASS:"), rig.diagnostics());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void feeding(GameTestHelper h) {
        run(h, "feeding_slurry", false);
    }

    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void cultureToVat(GameTestHelper h) {
        run(h, "culture_slurry", true);
    }

    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void generalFiltration(GameTestHelper h) {
        run(h, "mobvats:filter_general", false);
    }

    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void lavaFiltration(GameTestHelper h) {
        run(h, "mobvats:filter_lava", false);
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void waterVatStopsAtCapacity(GameTestHelper h) {
        var o = new BlockPos(2, 2, 2);
        var absolute = h.absolutePos(o);
        var sample = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1);
        CreativeProductionFixtures.vat(h.getLevel(), absolute, sample);
        var head = (net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity)
                h.getBlockEntity(o.offset(2, 2, 1));
        // First establish empty storage without a connected source.
        h.runAtTickTime(40, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(
                    vat.isAssembled(),
                    "Water Vat unformed: "
                            + vat.getLastError()
                                    .map(e -> e.getChatMessage().getString())
                                    .orElse(""));
            h.assertTrue(vat.medium().isEmpty(), "Disconnected inlet invented medium");
            CreativeProductionFixtures.source(
                    h.getLevel(), absolute.offset(-1, 1, 1), sample, net.minecraft.world.item.ItemStack.EMPTY);
        });
        h.runAtTickTime(70, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(
                    vat.medium().getFluidAmount() > 0
                            && vat.medium().getFluidAmount() < vat.medium().getCapacity(),
                    "Medium volume did not increase gradually through Fill Valve");
        });
        final int[] retained = {0};
        h.runAtTickTime(600, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(
                    vat.medium().getFluidAmount() == vat.medium().getCapacity()
                            && !vat.medium().overCapacity(),
                    "Water Vat did not stop at capacity");
            h.assertTrue(
                    net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(
                            vat.medium().cleanPrototype(), sample),
                    "Water composition changed");
            retained[0] = vat.storage().clusters().stream()
                    .mapToInt(c -> c.tank().getFluidAmount())
                    .sum();
            var inlet = (net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatFluidPortEntity)
                    h.getBlockEntity(o.offset(0, 1, 1));
            inlet.setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.None);
        });
        h.runAtTickTime(750, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(
                    vat.medium().getFluidAmount() == vat.medium().getCapacity()
                            && retained[0]
                                    == vat.storage().clusters().stream()
                                            .mapToInt(c -> c.tank().getFluidAmount())
                                            .sum(),
                    "Full Fill Valve continued consuming storage or duplicated medium");
            h.succeed();
        });
    }
}
