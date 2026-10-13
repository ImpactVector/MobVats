package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import it.zerono.mods.zerocore.lib.data.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class ActiveBoundaryGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), P = O.offset(4, 1, 2), Q = P.east();

    private static void machine(GameTestHelper h, BlockPos o, boolean mixer) {
        if (mixer) {
            MachineFixtures.mixerRig(h::setBlock, o);
            Slice11Fixtures.mixerStorage(h, o);
            // Leave the west face center available for direct adjacency.
            h.setBlock(o.offset(0, 1, 2), MixerContent.state(MixerPartType.Casing));
            h.setBlock(o.offset(0, 1, 1), MixerContent.state(MixerPartType.Controller));
        } else {
            MachineFixtures.vat(h::setBlock, o, 5, 5, 4);
            ((VatHeadEntity) h.getBlockEntity(o.offset(2, 2, 2))).encode(ItemEssence.COW);
            Slice11Fixtures.vatStorage(h, o, 5, 5);
        }
    }

    private static ActiveBoundaryPort port(GameTestHelper h, BlockPos p) {
        return (ActiveBoundaryPort) h.getBlockEntity(p);
    }

    private static PhysicalFluidStorage storage(ActiveBoundaryPort p) {
        return p instanceof VatFluidPortEntity v
                ? v.getMultiblockController().orElseThrow().storage()
                : ((MixerFluidPortEntity) p)
                        .getMultiblockController()
                        .orElseThrow()
                        .storage();
    }

    private static IFluidHandler external(GameTestHelper h, BlockPos p) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p), Direction.EAST);
    }

    private static String status(ActiveBoundaryPort p) {
        var c = p instanceof VatFluidPortEntity v
                ? v.getMultiblockController().orElseThrow()
                : ((MixerFluidPortEntity) p).getMultiblockController().orElseThrow();
        return "assembled=" + c.isAssembled() + " bounds=" + c.getBoundingBox() + " error="
                + c.getLastError().map(e -> e.getChatMessage().getString()) + " storage="
                + storage(p).diagnostics();
    }

    private static int amount(ActiveBoundaryPort p, FluidStack sample) {
        return storage(p).bulkConnected(p.getBlockPos()).stream()
                .filter(t -> FluidStack.isSameFluidSameComponents(t.getFluid(), sample))
                .mapToInt(t -> t.getFluidAmount())
                .sum();
    }

    private static FluidStack sample(boolean mixer, int n) {
        var s = new FluidStack(mixer ? ProductFluids.CULTURE_SLURRY.get() : ProductFluids.WASTE.get(), n);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Boundary identity"));
        if (mixer)
            s.set(
                    net.impactvector.mobvats.gamecontent.fluid.compound.CompoundFluids.COMPOSITION.get(),
                    new net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition(java.util.List.of(
                            new net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Entry(
                                    net.impactvector.mobvats.gamecontent.fluid.compound.CompoundComposition.Kind.FLUID,
                                    "minecraft:water",
                                    1000))));
        return s;
    }

    static void pair(GameTestHelper h, boolean mixer) {
        machine(h, O, mixer);
        machine(h, O.offset(5, 0, 0), !mixer);
        h.setBlock(P, mixer ? MixerContent.state(MixerPartType.FluidPort) : Content.state(VatPartType.FluidPort));
        h.setBlock(Q, mixer ? Content.state(VatPartType.FluidPort) : MixerContent.state(MixerPartType.FluidPort));
        port(h, P).setIoDirection(IoDirection.Output);
        port(h, P).setIoMode(IoMode.None);
        port(h, Q).setIoMode(IoMode.None);
    }

    private static void disabledTransfer(GameTestHelper h, boolean mixer) {
        pair(h, mixer);
        h.runAtTickTime(35, () -> {
            var a = port(h, P);
            var b = port(h, Q);
            if (a instanceof VatFluidPortEntity v)
                v.getMultiblockController().orElseThrow().setMachineActive(false);
            else
                ((MixerFluidPortEntity) a)
                        .getMultiblockController()
                        .orElseThrow()
                        .setMachineActive(false);
            if (b instanceof VatFluidPortEntity v)
                v.getMultiblockController().orElseThrow().setMachineActive(false);
            else
                ((MixerFluidPortEntity) b)
                        .getMultiblockController()
                        .orElseThrow()
                        .setMachineActive(false);
            var s = sample(mixer, 1000);
            h.assertTrue(
                    storage(a).destination(a.getBlockPos(), true, x -> true).fill(s, EXECUTE) == 1000,
                    "Disabled storage blocked fill");
            a.setIoMode(IoMode.Both);
            b.setIoMode(IoMode.Passive);
            h.assertTrue(
                    external(h, Q).fill(s.copyWithAmount(1), SIMULATE) == 1,
                    "Disable removed passive capability access");
        });
        h.runAtTickTime(55, () -> {
            var s = sample(mixer, 1000);
            h.assertTrue(
                    amount(port(h, P), s) == 0 && amount(port(h, Q), s) == 1000,
                    "Disabled controller stopped scheduled active I/O or stripped components");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void disabledMixerStillTransfersToDisabledVat(GameTestHelper h) {
        disabledTransfer(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void disabledVatStillTransfersToDisabledMixer(GameTestHelper h) {
        disabledTransfer(h, false);
    }

    private static void direct(GameTestHelper h, boolean mixer, IoMode output, IoMode input) {
        pair(h, mixer);
        h.runAtTickTime(35, () -> {
            var a = port(h, P);
            var b = port(h, Q);
            var s = sample(mixer, 2500);
            int filled =
                    storage(a).destination(a.getBlockPos(), true, x -> true).fill(s, EXECUTE);
            h.assertTrue(
                    filled == 2500,
                    "Source storage did not fill: " + filled + " tanks="
                            + storage(a).bulkConnected(a.getBlockPos()).size() + " contents="
                            + storage(a).diagnostics());
            a.setIoMode(output);
            b.setIoMode(input);
            h.assertTrue(
                    a.transferBoundary(1000) == (output.isActive() ? 1000 : 0), "Push policy failed: " + status(b));
            int pulled = b.transferBoundary(1000);
            h.assertTrue(
                    pulled == (output.isActive() ? 0 : 1000),
                    "Direct push ownership/pull policy failed: " + pulled + " " + status(b));
            h.assertTrue(
                    amount(a, s) == 1500 && amount(b, s) == 1000, "Transfer lost, duplicated or stripped components");
            a.setIoMode(IoMode.None);
            b.setIoMode(IoMode.None);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mixerActivePushToPassiveVat(GameTestHelper h) {
        direct(h, true, IoMode.Active, IoMode.Passive);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void vatActivePushToBothMixer(GameTestHelper h) {
        direct(h, false, IoMode.Active, IoMode.Both);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mixerPassiveOutputPulledByActiveVat(GameTestHelper h) {
        direct(h, true, IoMode.Passive, IoMode.Active);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void vatPassiveOutputPulledByBothMixer(GameTestHelper h) {
        direct(h, false, IoMode.Passive, IoMode.Both);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mixerBothToVatBothSingleOwner(GameTestHelper h) {
        direct(h, true, IoMode.Both, IoMode.Both);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void vatBothToMixerBothSingleOwner(GameTestHelper h) {
        direct(h, false, IoMode.Both, IoMode.Both);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void controllersTransferDirectlyWithoutTransport(GameTestHelper h) {
        pair(h, true);
        var s = sample(true, 2500);
        h.runAtTickTime(35, () -> {
            var a = port(h, P);
            int filled =
                    storage(a).destination(a.getBlockPos(), true, x -> true).fill(s, EXECUTE);
            h.assertTrue(
                    filled == 2500,
                    "Source fill failed: " + filled + " tanks="
                            + storage(a).bulkConnected(a.getBlockPos()).size());
            a.setIoMode(IoMode.Both);
            port(h, Q).setIoMode(IoMode.Both);
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(
                    amount(port(h, P), s) == 0 && amount(port(h, Q), s) == 2500,
                    "Controllers failed direct transport conservation: source=" + amount(port(h, P), s)
                            + " destination=" + amount(port(h, Q), s) + " " + status(port(h, P)) + " "
                            + status(port(h, Q)));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void fluidModesFiltersSimulationFullAndDirection(GameTestHelper h) {
        pair(h, true);
        h.runAtTickTime(35, () -> {
            var a = port(h, P);
            var b = port(h, Q);
            var s = sample(true, 2500);
            storage(a).destination(a.getBlockPos(), true, x -> true).fill(s, EXECUTE);
            var source = external(h, P);
            var dest = external(h, Q);
            for (var mode : IoMode.values()) {
                a.setIoMode(mode);
                b.setIoMode(mode);
                h.assertTrue(
                        source.drain(s, SIMULATE).getAmount() == (mode.isPassive() ? 2500 : 0),
                        "Passive output policy");
                h.assertTrue(
                        dest.fill(s, SIMULATE) == (mode.isPassive() ? 2500 : 0),
                        "Passive input policy: " + mode + " " + status(b));
                h.assertTrue(
                        amount(a, s) == 2500 && amount(b, s) == 0,
                        "Simulation mutated storage: source=" + amount(a, s) + " dest=" + amount(b, s));
                if (!mode.isActive())
                    h.assertTrue(
                            a.transferBoundary(1000) == 0 && b.transferBoundary(1000) == 0,
                            "Inactive mode transferred");
            }
            a.setIoMode(IoMode.Both);
            b.setIoMode(IoMode.Both);
            a.filter("mobvats:feeding_slurry");
            h.assertTrue(a.transferBoundary(1000) == 0, "Output filter bypass");
            a.filter("");
            b.filter("mobvats:feeding_slurry");
            h.assertTrue(a.transferBoundary(1000) == 0, "Input filter bypass");
            b.filter("");
            // Fill all reachable destination capacity; no execution may consume the source.
            dest.fill(s.copyWithAmount(Integer.MAX_VALUE), EXECUTE);
            int before = amount(b, s);
            h.assertTrue(
                    a.transferBoundary(1000) == 0 && amount(a, s) == 2500 && amount(b, s) == before,
                    "Full destination lost source");
            storage(b).handler(b.getBlockPos(), false, x -> true).drain(s.copyWithAmount(Integer.MAX_VALUE), EXECUTE);
            a.setIoDirection(IoDirection.Input);
            h.assertTrue(a.transferBoundary(1000) == 0, "Direction change retained output delegate");
            a.setIoDirection(IoDirection.Output);
            b.setIoMode(IoMode.None);
            h.assertTrue(a.transferBoundary(1000) == 0, "None accepted transfer");
            b.setIoMode(IoMode.Passive);
            h.assertTrue(a.transferBoundary(1000) == 1000, "Both failed active output after changes");
            a.setIoMode(IoMode.None);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void fluidNeighborRemovalReplacementAndBreakRepair(GameTestHelper h) {
        machine(h, O, false);
        h.setBlock(P, Content.state(VatPartType.FluidPort));
        var p = port(h, P);
        p.setIoMode(IoMode.None);
        p.setIoDirection(IoDirection.Output);
        var s = sample(false, 2500);
        h.runAtTickTime(35, () -> {
            storage(p).destination(p.getBlockPos(), true, x -> true).fill(s, EXECUTE);
            p.setIoMode(IoMode.Active);
            h.setBlock(
                    Q,
                    net.impactvector.mobvats.gamecontent.creative.CreativeContent.block(
                            net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.FLUID_SINK));
            h.assertTrue(p.transferBoundary(500) == 500, "Sink did not connect");
            h.setBlock(Q, Blocks.AIR);
            h.assertTrue(p.transferBoundary(500) == 0 && amount(p, s) == 2000, "Removed sink retained transfer");
            h.setBlock(
                    Q,
                    net.impactvector.mobvats.gamecontent.creative.CreativeContent.block(
                            net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.FLUID_SINK));
            h.assertTrue(p.transferBoundary(500) == 500, "Replacement did not reconnect");
            h.setBlock(Q, Blocks.AIR);
            h.setBlock(O.offset(1, 3, 2), Blocks.AIR);
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(p.transferBoundary(500) == 0, "Broken machine transferred");
            h.setBlock(O.offset(1, 3, 2), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(95, () -> {
            h.setBlock(
                    Q,
                    net.impactvector.mobvats.gamecontent.creative.CreativeContent.block(
                            net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.FLUID_SINK));
            h.assertTrue(p.transferBoundary(500) == 500, "Repair did not restore active transfer");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void itemModesFiltersCapacityComponentsAndExternalExtraction(GameTestHelper h) {
        machine(h, O, false);
        h.setBlock(P, Content.state(VatPartType.ItemPort));
        h.setBlock(Q, Blocks.CHEST);
        var p = (VatItemPortEntity) h.getBlockEntity(P);
        p.setIoDirection(IoDirection.Output);
        p.setIoMode(IoMode.None);
        h.runAtTickTime(35, () -> {
            var s = new ItemStack(Items.WHEAT, 20);
            s.set(DataComponents.CUSTOM_NAME, Component.literal("Staged identity"));
            p.staging().setStackInSlot(0, s.copy());
            var dest = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(Q), Direction.WEST);
            var external = p.externalItems();
            for (var mode : IoMode.values()) {
                p.setIoMode(mode);
                h.assertTrue(
                        external.extractItem(0, 1, true).getCount() == (mode.isPassive() ? 1 : 0),
                        "Passive item extraction policy");
                if (!mode.isActive()) h.assertTrue(p.transferBoundary(0) == 0, "Inactive item mode ejected");
            }
            p.setIoMode(IoMode.Both);
            p.filter("minecraft:coal");
            h.assertTrue(p.transferBoundary(0) == 0, "Item filter bypass");
            p.filter("");
            for (int i = 0; i < dest.getSlots(); i++) dest.insertItem(i, new ItemStack(Items.STONE, 64), false);
            h.assertTrue(
                    p.transferBoundary(0) == 0 && p.staging().getStackInSlot(0).getCount() == 20,
                    "Full inventory lost items");
            dest.extractItem(0, 64, false);
            dest.insertItem(0, s.copyWithCount(60), false);
            h.assertTrue(
                    p.transferBoundary(0) == 4 && p.staging().getStackInSlot(0).getCount() == 16,
                    "Partial insertion did not retain remainder");
            h.assertTrue(ItemStack.isSameItemSameComponents(dest.getStackInSlot(0), s), "Item components stripped");
            var pulled = external.extractItem(0, 3, false);
            h.assertTrue(
                    pulled.getCount() == 3
                            && p.transferBoundary(0) == 0
                            && p.staging().getStackInSlot(0).getCount() == 13,
                    "External pull/ejection duplicated items");
            dest.extractItem(1, 64, false);
            p.setIoMode(IoMode.Active);
            h.assertTrue(p.transferBoundary(0) == 13, "Active item output failed");
            p.setIoDirection(IoDirection.Input);
            p.setIoMode(IoMode.Both);
            h.assertTrue(
                    external.insertItem(0, new ItemStack(Items.WHEAT, 2), false).isEmpty(),
                    "Item input rejected external insertion");
            h.assertTrue(
                    p.transferBoundary(0) == 0 && p.staging().getStackInSlot(0).getCount() == 2,
                    "Item input actively pulled/ejected");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void controllerEjectionAndRealHopperPullConserveItems(GameTestHelper h) {
        machine(h, O, false);
        var pos = O.offset(2, 0, 3);
        h.setBlock(pos, Content.state(VatPartType.ItemPort));
        h.setBlock(pos.below(), Blocks.HOPPER);
        var p = (VatItemPortEntity) h.getBlockEntity(pos);
        p.setIoDirection(IoDirection.Output);
        p.setIoMode(IoMode.None);
        h.runAtTickTime(35, () -> {
            var hopper = h.getLevel()
                    .getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos.below()), Direction.UP);
            for (int slot = 0; slot < hopper.getSlots(); slot++)
                hopper.insertItem(slot, new ItemStack(Items.WHEAT, slot == 0 ? 60 : 64), false);
            p.staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 64));
            p.setIoMode(IoMode.Both);
        });
        h.runAtTickTime(50, () -> {
            var hopper = h.getLevel()
                    .getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos.below()), Direction.UP);
            int total = p.staging().getStackInSlot(0).getCount();
            for (int slot = 0; slot < hopper.getSlots(); slot++)
                total += hopper.getStackInSlot(slot).getCount();
            h.assertTrue(
                    total == 380 && p.staging().getStackInSlot(0).getCount() == 60,
                    "Controller push/hopper pull did not conserve staged output");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void mixerItemEjectionUsesFilteredOutputStaging(GameTestHelper h) {
        machine(h, O, true);
        h.setBlock(P, MixerContent.state(MixerPartType.ItemPort));
        h.setBlock(Q, Blocks.CHEST);
        var p = (MixerItemPortEntity) h.getBlockEntity(P);
        p.setIoDirection(IoDirection.Output);
        p.setIoMode(IoMode.None);
        h.runAtTickTime(35, () -> {
            var c = p.getMultiblockController().orElseThrow();
            var s = new ItemStack(Items.WHEAT, 12);
            s.set(DataComponents.CUSTOM_NAME, Component.literal("Mixer output"));
            var staged = new net.neoforged.neoforge.items.ItemStackHandler(4);
            staged.setStackInSlot(0, s.copy());
            var tag = c.state().save(h.getLevel().registryAccess());
            tag.put("producedItems", staged.serializeNBT(h.getLevel().registryAccess()));
            c.state().load(tag, h.getLevel().registryAccess());
            p.setIoMode(IoMode.Passive);
            h.assertTrue(
                    p.items().extractItem(0, 1, true).getCount() == 1 && p.transferBoundary(0) == 0,
                    "Passive Mixer output policy failed");
            p.setIoMode(IoMode.Both);
            p.filter("minecraft:coal");
            h.assertTrue(p.transferBoundary(0) == 0, "Mixer output bypassed fuel-only filter");
            p.filter("");
            h.assertTrue(p.items().extractItem(0, 2, false).getCount() == 2, "Both passive extraction failed");
            h.assertTrue(
                    p.transferBoundary(0) == 10
                            && c.itemsForPort(p).getStackInSlot(0).isEmpty(),
                    "Mixer output did not eject its remainder");
            var dest = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(Q), Direction.WEST);
            h.assertTrue(
                    dest.getStackInSlot(0).getCount() == 10
                            && ItemStack.isSameItemSameComponents(dest.getStackInSlot(0), s),
                    "Mixer ejection changed components/count");
            p.setIoDirection(IoDirection.Input);
            h.assertTrue(
                    p.items()
                            .insertItem(0, new ItemStack(Items.CARROT, 2), false)
                            .isEmpty(),
                    "Mixer item input rejected insertion");
            h.assertTrue(p.transferBoundary(0) == 0, "Mixer item input actively pulled");
            h.succeed();
        });
    }
}
