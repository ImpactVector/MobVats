package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gametest.PairedMachineFixtures.Family.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import it.zerono.mods.zerocore.lib.data.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatItemPortEntity;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;

/** Regression assertions and explicitly named GAP characterizations share the normal green suite. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class InterMachineLogisticsGameTests {
    static BlockState gap(int kind) {
        return switch (kind) {
            case 1 -> Blocks.CHEST.defaultBlockState();
            case 2 -> FluidBufferContent.state(ConduitCladding.BARE);
            case 3 -> ConduitContent.state(ConduitResource.ITEM, ConduitCladding.BARE);
            case 4 -> ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE);
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static void isolation(
            GameTestHelper h, PairedMachineFixtures.Family a, PairedMachineFixtures.Family b, int kind, int order) {
        var f = PairedMachineFixtures.fluid(h, a, b, 1, gap(kind));
        if (order == 0) f.build();
        else {
            f.buildMachine(order == 1);
            f.intermediates();
            h.runAtTickTime(40, () -> {
                h.assertTrue(f.controller(order == 1).isAssembled(), "First machine did not form");
                f.buildMachine(order != 1);
            });
        }
        var shell = f.left.offset(2, 3, 0);
        var saved = new BlockState[1];
        h.runAtTickTime(80, () -> {
            f.independent();
            f.report("PASS formation order=" + order);
            saved[0] = h.getBlockState(shell);
            h.setBlock(shell, Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    !f.controller(true).isAssembled() && f.controller(false).isAssembled(),
                    "Disassembly crossed gap: " + f.diagnostic());
            h.setBlock(shell, saved[0]);
            h.setBlock(f.middle, Blocks.AIR);
        });
        h.runAtTickTime(160, () -> {
            f.independent();
            f.intermediates();
        });
        h.runAtTickTime(200, () -> {
            f.independent();
            f.report("PASS shell and intermediate repair");
            f.clear();
            h.succeed();
        });
    }

    private static void adjacent(GameTestHelper h, PairedMachineFixtures.Family family) {
        var f = PairedMachineFixtures.fluid(h, family, family, 0, gap(0));
        f.build();
        h.runAtTickTime(60, () -> {
            f.report("EXPLORATORY touching same family");
            h.assertTrue(
                    f.controller(true) == f.controller(false)
                            && !f.controller(true).isAssembled(),
                    "Touching baseline changed; inspect discovery before updating expectation: " + f.diagnostic());
            f.clear();
            h.succeed();
        });
    }

    private static void adjacentOrdered(GameTestHelper h, PairedMachineFixtures.Family family, boolean aFirst) {
        var f = PairedMachineFixtures.fluid(h, family, family, 0, gap(0));
        f.buildMachine(aFirst);
        h.runAtTickTime(40, () -> {
            h.assertTrue(f.controller(aFirst).isAssembled(), "First touching fixture failed to form");
            f.buildMachine(!aFirst);
        });
        h.runAtTickTime(80, () -> {
            f.report("EXPLORATORY touching ordered AFirst=" + aFirst);
            f.independent();
            h.setBlock(f.left.offset(2, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    !f.controller(true).isAssembled() && f.controller(false).isAssembled(),
                    "Touching neighbor affected by disassembly: " + f.diagnostic());
            h.setBlock(
                    f.left.offset(2, 3, 0),
                    ConduitContent.state(
                                    ConduitResource.FLUID,
                                    ConduitCladding.CASING,
                                    family == VAT ? ConduitShell.VAT : ConduitShell.MIXER)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        });
        h.runAtTickTime(160, () -> {
            f.independent();
            f.report("PASS touching ordered shell repair");
            f.clear();
            h.succeed();
        });
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 190)
    public static void touchingVatsAFirst(GameTestHelper h) {
        adjacentOrdered(h, VAT, true);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 190)
    public static void touchingVatsBFirst(GameTestHelper h) {
        adjacentOrdered(h, VAT, false);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 190)
    public static void touchingMixersAFirst(GameTestHelper h) {
        adjacentOrdered(h, MIXER, true);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 190)
    public static void touchingMixersBFirst(GameTestHelper h) {
        adjacentOrdered(h, MIXER, false);
    }

    static FluidStack fluid(boolean compound, int n) {
        var s = new FluidStack(compound ? ProductFluids.CULTURE_SLURRY.get() : ProductFluids.STRANGE_MEAT.get(), n);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Inter-machine identity"));
        if (compound)
            s.set(
                    CompoundFluids.COMPOSITION.get(),
                    new CompoundComposition(java.util.List.of(
                            new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 1000),
                            new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:waste", 250))));
        return s;
    }

    private static void standalone(GameTestHelper h, boolean compound) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, gap(2));
        var e = (FluidBufferEntity) h.getBlockEntity(p);
        var cap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p), Direction.WEST);
        var s = fluid(compound, 2000);
        h.assertTrue(cap != null && cap.getTankCapacity(0) == 4000, "Standalone capability/capacity missing");
        h.assertTrue(cap.fill(s, SIMULATE) == 2000 && e.tank.isEmpty(), "Fill simulation mutated buffer");
        e.filter("minecraft:water");
        h.assertTrue(cap.fill(s, EXECUTE) == 0, "Filter admitted incompatible fluid");
        e.filter("");
        h.assertTrue(cap.fill(s, EXECUTE) == 2000, "Standalone insertion failed");
        h.assertTrue(
                FluidStack.matches(cap.drain(s, SIMULATE), s) && e.tank.getFluidAmount() == 2000,
                "Drain simulation changed contents/components");
        var different = s.copy();
        different.set(DataComponents.CUSTOM_NAME, Component.literal("Other identity"));
        h.assertTrue(cap.drain(different, EXECUTE).isEmpty(), "Typed drain ignored components");
        e.filter(compound ? "mobvats:culture_slurry" : "mobvats:strange_meat");
        var saved = e.saveWithoutMetadata(h.getLevel().registryAccess());
        e.onChunkUnloaded();
        h.assertTrue(cap.fill(s, EXECUTE) == 0 && cap.drain(s, EXECUTE).isEmpty(), "Unloaded retained capability live");
        e.loadWithComponents(saved, h.getLevel().registryAccess());
        e.onLoad();
        h.assertTrue(FluidStack.matches(cap.drain(s, EXECUTE), s), "Save/load changed fluid components");
        h.assertTrue(cap.fill(s.copyWithAmount(5000), EXECUTE) == 4000, "Capacity/backpressure failed");
        h.setBlock(p, Blocks.AIR);
        h.assertTrue(cap.fill(s, EXECUTE) == 0 && cap.drain(s, EXECUTE).isEmpty(), "Removed retained capability live");
        h.setBlock(p, gap(2));
        ((FluidBufferEntity) h.getBlockEntity(p))
                .loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(((FluidBufferEntity) h.getBlockEntity(p)).filter().equals(e.filter()), "Persisted filter lost");
        var fresh = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p), Direction.EAST);
        h.assertTrue(
                FluidStack.matches(fresh.drain(s, SIMULATE), s)
                        && cap.drain(s, EXECUTE).isEmpty(),
                "Replacement restored old authority or lost persisted components");
        h.succeed();
    }

    private static void handoff(GameTestHelper h, PairedMachineFixtures.Family a, PairedMachineFixtures.Family b) {
        var f = PairedMachineFixtures.fluid(h, a, b, 1, gap(2));
        f.build();
        var s = fluid(a == MIXER || b == MIXER, 2500);
        h.runAtTickTime(40, () -> {
            f.independent();
            // The production rigs pre-filter physical cells; select this test's identity explicitly.
            for (var first : new boolean[] {true, false})
                for (var cluster : f.storage(first).clusters()) for (var member : cluster.members()) member.filter("");
            h.assertTrue(
                    b != MIXER
                            || net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                    .acceptsFluid(s),
                    "Handoff sample is not an enabled Mixer recipe input");
            h.assertTrue(f.seed(true, s) == 2500, "Source initial contents unavailable: " + f.diagnostic());
            f.port(true).filter("minecraft:water");
            f.port(true).setIoMode(IoMode.Active);
        });
        h.runAtTickTime(65, () -> {
            f.independent();
            h.assertTrue(
                    f.amount(true, s) == 2500
                            && f.bufferCapability().getFluidInTank(0).isEmpty(),
                    "Output filter bypass");
            f.port(true).filter("");
        });
        h.runAtTickTime(90, () -> {
            f.independent();
            h.assertTrue(
                    f.amount(true, s) == 0
                            && f.bufferCapability().getFluidInTank(0).getAmount() == 2500,
                    "Scheduled A -> Buffer boundary failed: " + f.diagnostic());
            f.report("PASS fluid A=2500->0 Buffer=0->2500 B=0 direction=A->Buffer");
            f.port(false).filter("minecraft:water");
            f.port(false).setIoMode(IoMode.Active);
        });
        h.runAtTickTime(115, () -> {
            h.assertTrue(
                    f.amount(false, s) == 0
                            && f.bufferCapability().getFluidInTank(0).getAmount() == 2500,
                    "Active input filter bypass");
            f.port(false).filter("");
        });
        h.runAtTickTime(140, () -> {
            f.independent();
            h.assertTrue(
                    f.amount(true, s) == 0
                            && f.amount(false, s) == 2500
                            && f.bufferCapability().getFluidInTank(0).isEmpty(),
                    "Buffer -> B conservation/components: " + f.fluidDiagnostic(s));
            f.report("PASS fluid A=0 Buffer=2500->0 B=0->2500 direction=Buffer->B");
            f.port(false).setIoMode(IoMode.None);
            h.assertTrue(f.bufferCapability().fill(s.copyWithAmount(4000), EXECUTE) == 4000, "Buffer full setup");
            h.assertTrue(f.seed(true, s) == 2500, "Backpressure source setup");
        });
        h.runAtTickTime(165, () -> {
            f.independent();
            h.assertTrue(
                    f.amount(true, s) == 2500
                            && f.bufferCapability().getFluidInTank(0).getAmount() == 4000,
                    "Full Buffer lost fluid");
            f.port(true).setIoMode(IoMode.None);
            f.seed(false, s.copyWithAmount(Integer.MAX_VALUE));
            f.port(false).setIoMode(IoMode.Active);
        });
        h.runAtTickTime(190, () -> {
            f.independent();
            h.assertTrue(
                    f.bufferCapability().getFluidInTank(0).getAmount() == 4000 && f.amount(true, s) == 2500,
                    "Full destination drained Buffer");
            f.report("PASS full Buffer and destination backpressure");
            f.clear();
            h.succeed();
        });
    }

    private static void mixerRecipeGate(GameTestHelper h, boolean compound) {
        var f = PairedMachineFixtures.fluid(h, VAT, MIXER, 1, gap(2));
        f.build();
        var s = fluid(compound, 1000);
        if (compound)
            s.set(
                    CompoundFluids.COMPOSITION.get(),
                    new CompoundComposition(java.util.List.of(
                            new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 1000))));
        h.runAtTickTime(40, () -> {
            f.independent();
            h.assertTrue(
                    !net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.acceptsFluid(s),
                    "Recipe rejection baseline changed; revise characterization");
            for (var first : new boolean[] {true, false})
                for (var cluster : f.storage(first).clusters()) for (var member : cluster.members()) member.filter("");
            h.assertTrue(f.seed(true, s) == 1000, "Recipe-gate source setup");
            f.port(true).setIoMode(IoMode.Active);
            f.port(false).setIoMode(IoMode.Both);
        });
        h.runAtTickTime(100, () -> {
            f.independent();
            var input =
                    h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(f.input), Direction.WEST);
            h.assertTrue(
                    input != null
                            && input.fill(s, SIMULATE) == 0
                            && f.amount(true, s) == 0
                            && f.amount(false, s) == 0
                            && FluidStack.matches(f.bufferCapability().getFluidInTank(0), s),
                    "Mixer recipe gate changed or lost fluid: " + f.fluidDiagnostic(s));
            f.report(
                    "GAP unrestricted Mixer fluid input: A=1000->0 Buffer=0->1000 B=0 boundary=MixerRecipeRegistry.acceptsFluid compound="
                            + compound);
            f.clear();
            h.succeed();
        });
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 130)
    public static void gapStrangeMeatMixerRecipeInput(GameTestHelper h) {
        mixerRecipeGate(h, false);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 130)
    public static void gapWaterOnlyCultureMixerRecipeInput(GameTestHelper h) {
        mixerRecipeGate(h, true);
    }

    private static IItemHandler items(PairedMachineFixtures f, boolean first) {
        var p = f.h.getBlockEntity(first ? f.output : f.input);
        return p instanceof VatItemPortEntity v
                ? v.externalItems()
                : ((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerItemPortEntity) p).items();
    }

    private static int count(IItemHandler handler) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++)
            n += handler.getStackInSlot(i).getCount();
        return n;
    }

    private static IItemHandler internalItems(PairedMachineFixtures f, boolean first) {
        var p = f.h.getBlockEntity(first ? f.output : f.input);
        return p instanceof VatItemPortEntity v
                ? v.staging()
                : ((MultiblockMixer) f.controller(first))
                        .itemsForPort(
                                (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerItemPortEntity) p);
    }

    private static void itemHandoffGap(
            GameTestHelper h, PairedMachineFixtures.Family a, PairedMachineFixtures.Family b) {
        var f = new PairedMachineFixtures(
                h,
                new PairedMachineFixtures.Machine(a),
                new PairedMachineFixtures.Machine(b),
                1,
                gap(1),
                PairedMachineFixtures.Port.item(true),
                PairedMachineFixtures.Port.item(false));
        f.build();
        var s = new ItemStack(Items.WHEAT, 20);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Chest identity"));
        var chest = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(f.middle), Direction.WEST);
        h.runAtTickTime(40, () -> {
            f.independent();
            if (a == VAT)
                ((VatItemPortEntity) h.getBlockEntity(f.output)).staging().setStackInSlot(0, s.copy());
            else {
                var c = (MultiblockMixer) f.controller(true);
                var staged = new ItemStackHandler(4);
                staged.setStackInSlot(0, s.copy());
                var tag = c.state().save(h.getLevel().registryAccess());
                tag.put("producedItems", staged.serializeNBT(h.getLevel().registryAccess()));
                c.state().load(tag, h.getLevel().registryAccess());
            }
            for (int i = 0; i < chest.getSlots(); i++) chest.insertItem(i, new ItemStack(Items.STONE, 64), false);
            f.port(true).setIoMode(IoMode.Active);
            f.port(false).setIoMode(IoMode.Active);
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(
                    count(chest) == 27 * 64 && count(internalItems(f, true)) == 20, "Full chest lost staged output");
            f.port(true).filter("minecraft:coal");
            for (int i = 0; i < chest.getSlots(); i++) chest.extractItem(i, 64, false);
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(count(chest) == 0, "Output item filter bypass");
            f.port(true).filter("");
        });
        h.runAtTickTime(115, () -> {
            f.independent();
            h.assertTrue(
                    count(chest) == 20
                            && count(internalItems(f, true)) == 0
                            && count(internalItems(f, false)) == 0
                            && ItemStack.isSameItemSameComponents(chest.getStackInSlot(0), s),
                    "Scheduled item output/components failed: " + f.diagnostic());
            // Active extraction is deliberately absent. This is a GAP characterization, not a success assertion for
            // pulling.
            f.report("GAP active item extraction: A=20->0 Chest=0->20 B=0 direction=A->Chest only");
            f.port(false).setIoMode(IoMode.Both);
            f.port(false).filter("minecraft:coal");
            h.assertTrue(items(f, false).insertItem(0, s, true).getCount() == 20, "Input filter bypass");
            f.port(false).filter("");
            var carried = chest.extractItem(0, 20, false);
            var hopper = f.middle;
            h.setBlock(
                    hopper,
                    Blocks.HOPPER
                            .defaultBlockState()
                            .setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.EAST));
            var hopperItems =
                    h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(hopper), Direction.UP);
            h.assertTrue(hopperItems.insertItem(0, carried, false).isEmpty(), "Hopper setup failed");
        });
        h.runAtTickTime(300, () -> {
            f.independent();
            h.assertTrue(
                    count(items(f, false)) == 20
                            && ItemStack.isSameItemSameComponents(
                                    items(f, false).getStackInSlot(0), s),
                    "Real hopper insertion failed: " + f.diagnostic());
            f.report("PASS hopper->passive input B=20 identity preserved");
            f.clear();
            h.succeed();
        });
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 80)
    public static void touchingVatsDiscoveryBaseline(GameTestHelper h) {
        adjacent(h, VAT);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 80)
    public static void touchingMixersDiscoveryBaseline(GameTestHelper h) {
        adjacent(h, MIXER);
    }

    @GameTest(template = "empty")
    public static void standaloneStrangeMeatCapability(GameTestHelper h) {
        standalone(h, false);
    }

    @GameTest(template = "empty")
    public static void standaloneCompoundCultureCapability(GameTestHelper h) {
        standalone(h, true);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 220)
    public static void vatBufferVatScheduled(GameTestHelper h) {
        handoff(h, VAT, VAT);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 220)
    public static void mixerBufferMixerScheduled(GameTestHelper h) {
        handoff(h, MIXER, MIXER);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 220)
    public static void vatBufferMixerScheduled(GameTestHelper h) {
        handoff(h, VAT, MIXER);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 220)
    public static void mixerBufferVatScheduled(GameTestHelper h) {
        handoff(h, MIXER, VAT);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 330)
    public static void gapVatChestVatActiveExtraction(GameTestHelper h) {
        itemHandoffGap(h, VAT, VAT);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 330)
    public static void gapMixerChestMixerActiveExtraction(GameTestHelper h) {
        itemHandoffGap(h, MIXER, MIXER);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 330)
    public static void gapVatChestMixerActiveExtraction(GameTestHelper h) {
        itemHandoffGap(h, VAT, MIXER);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 330)
    public static void gapMixerChestVatActiveExtraction(GameTestHelper h) {
        itemHandoffGap(h, MIXER, VAT);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap0Order0(GameTestHelper h) {
        isolation(h, VAT, VAT, 0, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap0Order1(GameTestHelper h) {
        isolation(h, VAT, VAT, 0, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap0Order2(GameTestHelper h) {
        isolation(h, VAT, VAT, 0, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap1Order0(GameTestHelper h) {
        isolation(h, VAT, VAT, 1, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap1Order1(GameTestHelper h) {
        isolation(h, VAT, VAT, 1, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap1Order2(GameTestHelper h) {
        isolation(h, VAT, VAT, 1, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap2Order0(GameTestHelper h) {
        isolation(h, VAT, VAT, 2, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap2Order1(GameTestHelper h) {
        isolation(h, VAT, VAT, 2, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap2Order2(GameTestHelper h) {
        isolation(h, VAT, VAT, 2, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap3Order0(GameTestHelper h) {
        isolation(h, VAT, VAT, 3, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap3Order1(GameTestHelper h) {
        isolation(h, VAT, VAT, 3, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap3Order2(GameTestHelper h) {
        isolation(h, VAT, VAT, 3, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap4Order0(GameTestHelper h) {
        isolation(h, VAT, VAT, 4, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap4Order1(GameTestHelper h) {
        isolation(h, VAT, VAT, 4, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATVATGap4Order2(GameTestHelper h) {
        isolation(h, VAT, VAT, 4, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap0Order0(GameTestHelper h) {
        isolation(h, VAT, MIXER, 0, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap0Order1(GameTestHelper h) {
        isolation(h, VAT, MIXER, 0, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap0Order2(GameTestHelper h) {
        isolation(h, VAT, MIXER, 0, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap1Order0(GameTestHelper h) {
        isolation(h, VAT, MIXER, 1, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap1Order1(GameTestHelper h) {
        isolation(h, VAT, MIXER, 1, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap1Order2(GameTestHelper h) {
        isolation(h, VAT, MIXER, 1, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap2Order0(GameTestHelper h) {
        isolation(h, VAT, MIXER, 2, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap2Order1(GameTestHelper h) {
        isolation(h, VAT, MIXER, 2, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap2Order2(GameTestHelper h) {
        isolation(h, VAT, MIXER, 2, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap3Order0(GameTestHelper h) {
        isolation(h, VAT, MIXER, 3, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap3Order1(GameTestHelper h) {
        isolation(h, VAT, MIXER, 3, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap3Order2(GameTestHelper h) {
        isolation(h, VAT, MIXER, 3, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap4Order0(GameTestHelper h) {
        isolation(h, VAT, MIXER, 4, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap4Order1(GameTestHelper h) {
        isolation(h, VAT, MIXER, 4, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationVATMIXERGap4Order2(GameTestHelper h) {
        isolation(h, VAT, MIXER, 4, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap0Order0(GameTestHelper h) {
        isolation(h, MIXER, VAT, 0, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap0Order1(GameTestHelper h) {
        isolation(h, MIXER, VAT, 0, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap0Order2(GameTestHelper h) {
        isolation(h, MIXER, VAT, 0, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap1Order0(GameTestHelper h) {
        isolation(h, MIXER, VAT, 1, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap1Order1(GameTestHelper h) {
        isolation(h, MIXER, VAT, 1, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap1Order2(GameTestHelper h) {
        isolation(h, MIXER, VAT, 1, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap2Order0(GameTestHelper h) {
        isolation(h, MIXER, VAT, 2, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap2Order1(GameTestHelper h) {
        isolation(h, MIXER, VAT, 2, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap2Order2(GameTestHelper h) {
        isolation(h, MIXER, VAT, 2, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap3Order0(GameTestHelper h) {
        isolation(h, MIXER, VAT, 3, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap3Order1(GameTestHelper h) {
        isolation(h, MIXER, VAT, 3, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap3Order2(GameTestHelper h) {
        isolation(h, MIXER, VAT, 3, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap4Order0(GameTestHelper h) {
        isolation(h, MIXER, VAT, 4, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap4Order1(GameTestHelper h) {
        isolation(h, MIXER, VAT, 4, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERVATGap4Order2(GameTestHelper h) {
        isolation(h, MIXER, VAT, 4, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap0Order0(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 0, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap0Order1(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 0, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap0Order2(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 0, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap1Order0(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 1, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap1Order1(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 1, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap1Order2(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 1, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap2Order0(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 2, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap2Order1(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 2, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap2Order2(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 2, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap3Order0(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 3, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap3Order1(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 3, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap3Order2(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 3, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap4Order0(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 4, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap4Order1(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 4, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 230)
    public static void isolationMIXERMIXERGap4Order2(GameTestHelper h) {
        isolation(h, MIXER, MIXER, 4, 2);
    }
}
