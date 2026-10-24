package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.*;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Two copies of the existing geometry/storage fixtures; all positions remain helper-relative. */
final class PairedMachineFixtures {
    enum Family {
        VAT,
        MIXER
    }

    record Machine(Family family, VatTier tier) {
        Machine(Family family) {
            this(family, VatTier.T1);
        }

        BlockState port(boolean item) {
            var state = family == Family.MIXER
                    ? MixerContent.state(item ? MixerPartType.ItemPort : MixerPartType.FluidPort)
                    : Content.state(item ? VatPartType.ItemPort : VatPartType.FluidPort);
            return state.setValue(
                    CladdingMaterial.PROPERTY,
                    family == Family.MIXER ? CladdingMaterial.MIXER_T1_CASING : tier.cladding(false));
        }
    }

    record Port(boolean item, int y, int z, IoDirection direction, IoMode mode, CladdingMaterial cladding) {
        static Port fluid(boolean output) {
            return new Port(false, 1, 2, output ? IoDirection.Output : IoDirection.Input, IoMode.None, null);
        }

        static Port item(boolean output) {
            return new Port(true, 1, 2, output ? IoDirection.Output : IoDirection.Input, IoMode.None, null);
        }
    }

    final GameTestHelper h;
    final Machine a, b;
    final int gap;
    final BlockPos left, right, output, input, middle;
    final BlockState intermediate;
    final Port pa, pb;

    PairedMachineFixtures(GameTestHelper h, Machine a, Machine b, int gap, BlockState intermediate, Port pa, Port pb) {
        this(h, a, b, gap, intermediate, pa, pb, new BlockPos(1, 1, 1));
    }

    PairedMachineFixtures(
            GameTestHelper h,
            Machine a,
            Machine b,
            int gap,
            BlockState intermediate,
            Port pa,
            Port pb,
            BlockPos origin) {
        if (gap < 0) throw new IllegalArgumentException("Negative face separation");
        this.h = h;
        this.a = a;
        this.b = b;
        this.gap = gap;
        this.intermediate = intermediate;
        this.pa = pa;
        this.pb = pb;
        left = origin;
        right = left.east(5 + gap);
        output = left.offset(4, pa.y(), pa.z());
        input = right.offset(0, pb.y(), pb.z());
        middle = output.east();
    }

    static PairedMachineFixtures fluid(GameTestHelper h, Family a, Family b, int gap, BlockState block) {
        return new PairedMachineFixtures(
                h, new Machine(a), new Machine(b), gap, block, Port.fluid(true), Port.fluid(false));
    }

    void build() {
        buildMachine(true);
        buildMachine(false);
        intermediates();
    }

    void intermediates() {
        intermediates(java.util.Collections.nCopies(gap, intermediate));
    }

    void intermediates(java.util.List<BlockState> blocks) {
        if (blocks.size() != gap) throw new IllegalArgumentException("One state required per gap cell");
        for (int n = 0; n < gap; n++) h.setBlock(middle.east(n), blocks.get(n));
    }

    void buildMachine(boolean first) {
        var spec = first ? a : b;
        var o = first ? left : right;
        if (spec.family() == Family.VAT) {
            MachineFixtures.vat(h::setBlock, o, 5, 5, 4);
            ((VatHeadEntity) h.getBlockEntity(o.offset(2, 2, 2))).encode(ItemEssence.COW);
            Slice11Fixtures.vatStorage(h, o, 5, 5);
            // Adapt the historical T1 fixture without introducing a second geometry builder.
            for (var p : BlockPos.betweenClosed(o, o.offset(4, 3, 4))) {
                var state = h.getBlockState(p);
                for (var type : new VatPartType[] {VatPartType.Casing, VatPartType.Glass, VatPartType.Controller})
                    if (state.is(Content.block(type)))
                        state = Content.block(spec.tier(), type).defaultBlockState();
                if (state.hasProperty(CladdingMaterial.PROPERTY)
                        && CladdingMaterial.installed(state).shell() == ConduitShell.VAT)
                    state = state.setValue(
                            CladdingMaterial.PROPERTY,
                            spec.tier()
                                    .cladding(CladdingMaterial.installed(state).cladding() == ConduitCladding.GLASS));
                h.setBlock(p, state);
            }
        } else {
            MachineFixtures.mixerRig(h::setBlock, o);
            Slice11Fixtures.mixerStorage(h, o);
            h.setBlock(o.offset(0, 1, 2), MixerContent.state(MixerPartType.Casing));
            h.setBlock(o.offset(0, 1, 1), MixerContent.state(MixerPartType.Controller));
        }
        var config = first ? pa : pb;
        var p = first ? output : input;
        var state = spec.port(config.item());
        if (config.cladding() != null) state = state.setValue(CladdingMaterial.PROPERTY, config.cladding());
        h.setBlock(p, state);
        port(first).setIoDirection(config.direction());
        port(first).setIoMode(config.mode());
    }

    ActiveBoundaryPort port(boolean first) {
        return (ActiveBoundaryPort) h.getBlockEntity(first ? output : input);
    }

    ConduitCuboidController<?> controller(boolean first) {
        var spec = first ? a : b;
        var o = first ? left : right;
        var pos = spec.family() == Family.VAT ? o.offset(1, 1, 0) : o.offset(0, 1, 1);
        return (ConduitCuboidController<?>) ((IMultiblockPart<?>) h.getBlockEntity(pos))
                .getMultiblockController()
                .orElseThrow(() -> new IllegalStateException("No ZeroCore controller: " + spec + " at "
                        + h.absolutePos(pos) + " gameTime=" + h.getLevel().getGameTime()));
    }

    PhysicalFluidStorage storage(boolean first) {
        var c = controller(first);
        return c instanceof MultiblockVat v ? v.storage() : ((MultiblockMixer) c).storage();
    }

    int amount(boolean first, FluidStack sample) {
        return storage(first).clusters().stream()
                .map(PhysicalFluidStorage.Cluster::tank)
                .filter(t -> FluidStack.isSameFluidSameComponents(t.getFluid(), sample))
                .mapToInt(t -> t.getFluidAmount())
                .sum();
    }

    int seed(boolean first, FluidStack sample) {
        return storage(first)
                .destination(h.absolutePos(first ? output : input), true, f -> true)
                .fill(sample, IFluidHandler.FluidAction.EXECUTE);
    }

    IFluidHandler bufferCapability() {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(middle), Direction.WEST);
    }

    void independent() {
        var ca = controller(true);
        var cb = controller(false);
        var detail = diagnostic();
        h.assertTrue(ca != cb && ca.isAssembled() && cb.isAssembled(), "Isolation: " + detail);
        h.assertTrue(
                ca.getBoundingBox().getMin().equals(h.absolutePos(left))
                        && ca.getBoundingBox().getMax().equals(h.absolutePos(left.offset(4, 3, 4)))
                        && cb.getBoundingBox().getMin().equals(h.absolutePos(right))
                        && cb.getBoundingBox().getMax().equals(h.absolutePos(right.offset(4, 3, 4))),
                "Discovery crossed envelope: " + detail);
        for (var first : new boolean[] {true, false}) {
            var o = first ? left : right;
            for (var p : BlockPos.betweenClosed(o, o.offset(4, 3, 4)))
                if (h.getLevel().getBlockEntity(h.absolutePos(p)) instanceof IMultiblockPart<?> part)
                    h.assertTrue(
                            part.getMultiblockController().orElse(null) == (first ? ca : cb),
                            "Ambiguous part ownership at " + p + ": " + detail);
        }
        if (gap > 0)
            for (int n = 0; n < gap; n++)
                h.assertTrue(
                        ConduitStructure.containing(h.getLevel(), h.absolutePos(middle.east(n))) == null,
                        "Intermediate claimed: " + detail);
    }

    String diagnostic() {
        var ca = controller(true);
        var cb = controller(false);
        return a + " -> " + b + " gap=" + gap + " intermediate=" + intermediate
                + " actualIntermediate="
                + java.util.stream.IntStream.range(0, gap)
                        .mapToObj(n -> h.getBlockState(middle.east(n)))
                        .toList()
                + " A=" + ca.isAssembled() + "/" + ca.getBoundingBox() + "/"
                + ca.getLastError().map(e -> e.getChatMessage().getString())
                + " B=" + cb.isAssembled() + "/" + cb.getBoundingBox() + "/"
                + cb.getLastError().map(e -> e.getChatMessage().getString())
                + " sameController=" + (ca == cb) + " fluidCapability=" + (gap > 0 && bufferCapability() != null)
                + " itemCapability="
                + (gap > 0
                        && h.getLevel()
                                        .getCapability(
                                                Capabilities.ItemHandler.BLOCK, h.absolutePos(middle), Direction.WEST)
                                != null)
                + " intermediateOwner="
                + (gap > 0 ? ConduitStructure.containing(h.getLevel(), h.absolutePos(middle)) : "none");
    }

    String fluidDiagnostic(FluidStack sample) {
        return "A=" + amount(true, sample) + " Buffer="
                + (bufferCapability() == null ? "absent" : bufferCapability().getFluidInTank(0)) + " B="
                + amount(false, sample) + " identity=" + sample + " " + diagnostic();
    }

    void report(String result) {
        com.mojang.logging.LogUtils.getLogger().info("INTER_MACHINE {} {}", result, diagnostic());
    }

    /** Successful fixtures must not keep controllers/active ports ticking through later batches. */
    void clear() {
        for (var p : BlockPos.betweenClosed(left, right.offset(4, 3, 4)))
            h.getLevel().removeBlock(h.absolutePos(p), false);
    }
}
