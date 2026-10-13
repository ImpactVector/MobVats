package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.common.MachineInspection;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatFillDiagnosticGameTests {
    private static final BlockPos O = new BlockPos(2, 2, 2), P = new BlockPos(3, 3, 3);

    private static FluidStack water(int n) {
        return new FluidStack(Fluids.WATER, n);
    }

    private static VatBoundaryEntity valve(GameTestHelper h) {
        h.setBlock(P, Content.state(VatPartType.FillValve));
        return (VatBoundaryEntity) h.getBlockEntity(P);
    }

    private static VatMediumStorage medium() {
        var m = new VatMediumStorage(() -> {});
        m.setCapacity(1500);
        return m;
    }

    private static FluidTank supply(FluidStack stack) {
        var t = new FluidTank(10000);
        t.fill(stack, EXECUTE);
        return t;
    }

    private static boolean fill(VatMediumStorage m, FluidTank t, VatBoundaryEntity e) {
        return BoundaryDevices.fill(m, PhysicalFluidStorage.handler(List.of(t), false, s -> true), e);
    }

    private static void status(GameTestHelper h, VatBoundaryEntity e, String expected) {
        h.assertTrue(e.status().equals(expected), "Expected " + expected + ", got " + e.status());
    }

    @GameTest(template = "empty")
    public static void diagnosticPrecedenceAndConservation(GameTestHelper h) {
        var e = valve(h);
        var m = medium();
        m.fill(water(1500), EXECUTE);
        BoundaryDevices.fill(m, PhysicalFluidStorage.handler(List.of(), false, s -> true), e);
        status(h, e, "No connected supply");
        var t = supply(FluidStack.EMPTY);
        fill(m, t, e);
        status(h, e, "No fluid available");
        t.fill(water(2000), EXECUTE);
        e.filter("minecraft:lava");
        fill(m, t, e);
        status(h, e, "Filter mismatch");
        e.filter("minecraft:water");
        fill(m, t, e);
        status(h, e, "Medium at capacity");
        h.assertTrue(t.getFluidAmount() == 2000 && m.getFluidAmount() == 1500, "Failed operations changed resources");
        m.drain(1500, EXECUTE);
        fill(m, t, e);
        status(h, e, "Filling normally");
        h.assertTrue(
                m.getFluidAmount() == 1000
                        && t.getFluidAmount() == 1000
                        && e.fillDetails().contains("1000 mB / operation"),
                "Accepted amount or accounting incorrect");
        fill(m, t, e);
        h.assertTrue(m.getFluidAmount() == 1500 && t.getFluidAmount() == 500, "Partial capacity lost overflow");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void conflictAndIncompatibleDiagnostics(GameTestHelper h) {
        var e = valve(h);
        var m = medium();
        m.fill(water(1000), EXECUTE);
        var lava = supply(new FluidStack(Fluids.LAVA, 1000));
        fill(m, lava, e);
        status(h, e, "Incompatible existing medium");
        var other = medium();
        other.fill(new FluidStack(Fluids.LAVA, 500), EXECUTE);
        m.assimilate(other);
        var t = supply(water(1000));
        fill(m, t, e);
        status(h, e, "Migration/reconnection conflict");
        var saved = m.save(h.getLevel().registryAccess());
        var restored = medium();
        restored.load(saved, h.getLevel().registryAccess());
        fill(restored, t, e);
        status(h, e, "Migration/reconnection conflict");
        var migration = medium();
        var tag = migration.save(h.getLevel().registryAccess());
        tag.putString("migrationDiagnostic", "Legacy source conflict");
        migration.load(tag, h.getLevel().registryAccess());
        fill(migration, t, e);
        status(h, e, "Migration/reconnection conflict");
        h.assertTrue(
                m.getFluidAmount() == 1000
                        && restored.getFluidAmount() == 1000
                        && t.getFluidAmount() == 1000
                        && lava.getFluidAmount() == 1000,
                "Rejection changed medium or source");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedDrainAndUsableCandidatePrecedence(GameTestHelper h) {
        var e = valve(h);
        var m = medium();
        var blocked = new FluidTank(1000) {
            @Override
            public FluidStack drain(FluidStack request, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
        blocked.fill(water(1000), EXECUTE);
        fill(m, blocked, e);
        status(h, e, "Transfer blocked or unavailable");
        var lava = supply(new FluidStack(Fluids.LAVA, 1000));
        var usable = supply(water(1000));
        e.filter("minecraft:water");
        BoundaryDevices.fill(m, PhysicalFluidStorage.handler(List.of(lava, blocked, usable), false, s -> true), e);
        status(h, e, "Filling normally");
        h.assertTrue(
                m.getFluidAmount() == 1000
                        && blocked.getFluidAmount() == 1000
                        && lava.getFluidAmount() == 1000
                        && usable.isEmpty(),
                "Fallback transfer lost or duplicated fluid");
        h.succeed();
    }

    private static FluidStack mixedCulture(GameTestHelper h) {
        var state = new MixerState(() -> {});
        var recipe = MixerRecipeRegistry.recipe("culture_slurry");
        var strategy = recipe.strategies().stream()
                .filter(MixerRecipeDefinition.Strategy::enabled)
                .findFirst()
                .orElseThrow();
        for (var demand : strategy.fluids()) {
            var sample = List.of(water(1), new FluidStack(ProductFluids.EXPERIENCE.get(), 1)).stream()
                    .filter(demand.selector())
                    .findFirst()
                    .orElseThrow();
            state.fluids(true).fill(sample.copyWithAmount(demand.amount()), EXECUTE);
        }
        h.assertTrue(state.commit(recipe), "Real Mixer recipe could not produce culture");
        var product = state.fluids(false).drain(recipe.outputAmount(), EXECUTE);
        h.assertTrue(
                product.is(ProductFluids.CULTURE_SLURRY.get()) && product.getAmount() == recipe.outputAmount(),
                "Mixer output missing real compound culture");
        return product;
    }

    private static void routed(GameTestHelper h, FluidStack sample) {
        CreativeProductionFixtures.vat(h.getLevel(), h.absolutePos(O), sample);
        var head = (VatHeadEntity) h.getBlockEntity(O.offset(2, 2, 1));
        h.runAtTickTime(35, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(vat.isAssembled() && vat.medium().isEmpty(), "Cow Vat not formed/empty");
            vat.setMachineActive(false);
            var port = (VatFluidPortEntity) h.getBlockEntity(O.offset(0, 1, 1));
            port.setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Passive);
            h.assertTrue(
                    port.externalFluids().fill(sample, EXECUTE) == sample.getAmount(),
                    "Finite supply did not reach normal inlet/buffer");
            var valve = (VatBoundaryEntity) h.getBlockEntity(O.offset(1, 3, 1));
            h.assertTrue(
                    vat.storage().connected(valve.getBlockPos()).stream()
                                    .mapToInt(FluidTank::getFluidAmount)
                                    .sum()
                            == sample.getAmount(),
                    "TAP route did not expose finite supply to valve");
        });
        h.runAtTickTime(80, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            var m = vat.medium();
            h.assertTrue(
                    m.getFluidAmount() == sample.getAmount()
                            && FluidStack.isSameFluidSameComponents(m.cleanPrototype(), sample),
                    "Scheduled Fill Valve changed volume/identity/composition");
            h.assertTrue(
                    vat.storage().clusters().stream()
                                    .mapToInt(c -> c.tank().getFluidAmount())
                                    .sum()
                            == 0,
                    "Finite supply lost or duplicated");
            var cow = ResourceLocation.withDefaultNamespace("cow");
            h.assertTrue(
                    Math.abs(m.frequency(cow) - CultureProfiles.multiplier(cow, sample)) < 1e-12,
                    "Cow frequency differs from authoritative profile resolution");
            int dirty = m.foul(100);
            h.assertTrue(
                    dirty == 100 && m.cleanAmount() == sample.getAmount() - 100 && m.dirtyAmount() == 100,
                    "Clean/dirty accounting changed");
            var restored = medium();
            restored.setCapacity(m.getCapacity());
            restored.load(m.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(
                    restored.matches(m.snapshot()) && restored.frequency(cow) == m.frequency(cow),
                    "Save/load lost identity, composition, fractions or frequency");
            h.assertTrue(
                    m.fill(new FluidStack(Fluids.LAVA, 1000), EXECUTE) == 0 && restored.matches(m.snapshot()),
                    "Incompatible fluid changed medium");
            var valve = (VatBoundaryEntity) h.getBlockEntity(O.offset(1, 3, 1));
            long builds = vat.storage().bulkSnapshotBuilds();
            var before = m.save(h.getLevel().registryAccess());
            for (int i = 0; i < 10; i++) MachineInspection.lines(h.getLevel(), valve.getBlockPos());
            h.assertTrue(
                    builds == vat.storage().bulkSnapshotBuilds()
                            && before.equals(m.save(h.getLevel().registryAccess())),
                    "Tooltip traversed routes or changed resources");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void finiteWaterThroughCowVatRoute(GameTestHelper h) {
        routed(h, water(2500));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mixerProducedCultureThroughCowVatRoute(GameTestHelper h) {
        routed(h, mixedCulture(h));
    }
}
