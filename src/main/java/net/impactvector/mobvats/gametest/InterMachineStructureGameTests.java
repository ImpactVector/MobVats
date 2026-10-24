package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gametest.PairedMachineFixtures.Family.*;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatTier;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class InterMachineStructureGameTests {
    private static void cladding(GameTestHelper h, PairedMachineFixtures.Family family, VatTier tier, int kind) {
        var spec = new PairedMachineFixtures.Machine(family, tier);
        var f = new PairedMachineFixtures(
                h,
                spec,
                spec,
                1,
                Blocks.AIR.defaultBlockState(),
                PairedMachineFixtures.Port.fluid(true),
                PairedMachineFixtures.Port.fluid(false));
        f.build();
        var material = family == MIXER ? CladdingMaterial.MIXER_T1_CASING : tier.cladding(false);
        var valid = switch (kind) {
            case 0 -> FluidBufferContent.state(ConduitCladding.BARE).setValue(CladdingMaterial.PROPERTY, material);
            case 1 ->
                ConduitContent.state(ConduitResource.ITEM, ConduitCladding.BARE)
                        .setValue(CladdingMaterial.PROPERTY, material);
            case 2 ->
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                        .setValue(CladdingMaterial.PROPERTY, material);
            default -> spec.port(false);
        };
        h.setBlock(f.output, valid.setValue(CladdingMaterial.PROPERTY, CladdingMaterial.NONE));
        h.runAtTickTime(40, () -> {
            var c = f.controller(true);
            h.assertTrue(
                    !c.isAssembled()
                            && c.getLastError().isPresent()
                            && f.controller(false).isAssembled(),
                    "Unclad shell accepted or neighbor affected: " + f.diagnostic());
            h.assertTrue(
                    c != f.controller(false)
                            && c.getBoundingBox().getMax().equals(h.absolutePos(f.left.offset(4, 3, 4))),
                    "Discovery escaped even though validation failed: " + f.diagnostic());
            f.report("PASS bare shell rejected kind=" + kind + " validation=" + c.getLastError());
            h.setBlock(f.output, valid);
        });
        h.runAtTickTime(80, () -> {
            f.independent();
            f.report("PASS compatible cladding kind=" + kind);
            var wrong = family == MIXER
                    ? CladdingMaterial.VAT_T1_CASING
                    : (tier == VatTier.T2 ? VatTier.T3 : VatTier.T2).cladding(false);
            h.setBlock(f.output, valid.setValue(CladdingMaterial.PROPERTY, wrong));
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    !f.controller(true).isAssembled() && f.controller(false).isAssembled(),
                    "Wrong tier/family cladding accepted: " + f.diagnostic());
            f.report("PASS wrong tier/family cladding rejected kind=" + kind);
            h.setBlock(f.output, valid);
            h.setBlock(f.left.offset(1, 1, 3), FluidBufferContent.state(ConduitCladding.BARE));
        });
        h.runAtTickTime(160, () -> {
            f.independent();
            f.report("PASS bare interior Buffer and structural Controller");
            f.clear();
            h.succeed();
        });
    }

    private static void reload(
            GameTestHelper h, PairedMachineFixtures.Family a, PairedMachineFixtures.Family b, int kind) {
        int id = (a.ordinal() * 2 + b.ordinal()) * 5 + kind;
        // Entire pair fits one distant chunk, outside GameTest's forced arena tickets.
        // Separate ticket/dependency radii: reloading one pair cannot keep another test's chunk resident.
        var remote = new BlockPos(25000001 + id * 512, 80, 25000001);
        // These tests have Rotation.NONE. Minecraft's relativePos applies an extra 180-degree transform.
        var origin = remote.subtract(h.absolutePos(BlockPos.ZERO));
        h.assertTrue(h.absolutePos(origin).equals(remote), "Remote fixture coordinate conversion failed");
        var cp = new net.minecraft.world.level.ChunkPos(remote);
        var f = new PairedMachineFixtures(
                h,
                new PairedMachineFixtures.Machine(a),
                new PairedMachineFixtures.Machine(b),
                1,
                InterMachineLogisticsGameTests.gap(kind),
                PairedMachineFixtures.Port.fluid(true),
                PairedMachineFixtures.Port.fluid(false),
                origin);
        var level = h.getLevel();
        level.setChunkForced(cp.x, cp.z, true);
        for (var p : BlockPos.betweenClosed(remote, remote.offset(10, 3, 4))) level.removeBlock(p, false);
        h.runAtTickTime(40, f::build);
        var old = new LevelChunk[1];
        var retained = new net.neoforged.neoforge.fluids.capability.IFluidHandler[1];
        var previous = new ConduitCuboidController<?>[2];
        var roofs = new net.minecraft.world.level.block.state.BlockState[2];
        var sample = InterMachineLogisticsGameTests.fluid(true, 1500);
        h.runAtTickTime(100, () -> {
            f.independent();
            previous[0] = f.controller(true);
            previous[1] = f.controller(false);
            if (kind == 2)
                h.assertTrue(
                        f.bufferCapability()
                                        .fill(
                                                sample,
                                                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                                                        .EXECUTE)
                                == 1500,
                        "Reload contents setup failed");
            old[0] = level.getChunkAt(remote);
            retained[0] = f.bufferCapability();
            level.setChunkForced(cp.x, cp.z, false);
        });
        h.runAtTickTime(180, () -> {
            var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(cp.toLong());
            h.assertTrue(
                    holder != null
                            && holder.getTicketLevel() == 33
                            && level.getChunkSource().getChunkNow(cp.x, cp.z) == old[0],
                    "Assembled chunk-retention baseline changed; re-evaluate native unload: " + f.diagnostic());
            f.independent();
            f.report(
                    "BUG assembled pair retains level-33 holder after forced ticket release; safe unload requires shell disassembly");
            level.setChunkForced(cp.x, cp.z, true);
            roofs[0] = h.getBlockState(f.left.offset(2, 3, 0));
            roofs[1] = h.getBlockState(f.right.offset(2, 3, 0));
            h.setBlock(f.left.offset(2, 3, 0), Blocks.AIR);
            h.setBlock(f.right.offset(2, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(220, () -> {
            h.assertTrue(
                    !f.controller(true).isAssembled() && !f.controller(false).isAssembled(),
                    "Native unload fixture did not quiesce");
            level.setChunkForced(cp.x, cp.z, false);
        });
        h.startSequence()
                .thenIdle(230)
                .thenWaitUntil(() -> {
                    var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(cp.toLong());
                    h.assertTrue(
                            holder == null || holder.getTicketLevel() == 34 && holder.getGenerationRefCount() == 0,
                            "Waiting for settled dependency holder: "
                                    + (holder == null
                                            ? "absent"
                                            : holder.getTicketLevel() + " generationRefs="
                                                    + holder.getGenerationRefCount()));
                    if (holder != null)
                        try {
                            ServerChunkEviction.request(level, cp);
                        } catch (ReflectiveOperationException error) {
                            throw new IllegalStateException(error);
                        }
                })
                .thenWaitUntil(() -> {
                    try {
                        h.assertTrue(
                                level.getChunkSource().getChunkNow(cp.x, cp.z) == null
                                        && !ServerChunkEviction.loaded(old[0])
                                        && ServerChunkEviction.absent(level, cp),
                                "Waiting for actual paired-machine chunk eviction");
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                })
                .thenExecute(() -> {
                    if (retained[0] != null)
                        h.assertTrue(
                                retained[0]
                                        .drain(
                                                1,
                                                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                                                        .EXECUTE)
                                        .isEmpty(),
                                "Retained Buffer capability accessed evicted chunk");
                    level.setChunkForced(cp.x, cp.z, true);
                    h.assertTrue(level.getChunkAt(remote) != old[0], "Chunk instance not replaced");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(
                            f.controller(true) != previous[0] && f.controller(false) != previous[1],
                            "Reload reused old controller objects");
                    if (retained[0] != null)
                        h.assertTrue(
                                retained[0]
                                        .drain(
                                                1,
                                                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                                                        .EXECUTE)
                                        .isEmpty(),
                                "Reload revived old Buffer capability");
                    if (kind == 2)
                        h.assertTrue(
                                net.neoforged.neoforge.fluids.FluidStack.matches(
                                        f.bufferCapability().getFluidInTank(0), sample),
                                "Native save/reload lost Buffer identity/components");
                    h.assertTrue(
                            h.getBlockState(f.middle).equals(InterMachineLogisticsGameTests.gap(kind)),
                            "Reload changed intermediate block/cladding/mode: " + f.diagnostic());
                    h.setBlock(f.left.offset(2, 3, 0), roofs[0]);
                    h.setBlock(f.right.offset(2, 3, 0), roofs[1]);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    f.independent();
                    f.report("PASS actual chunk unload/save/reload after shell quiescence and repair");
                    for (var p : BlockPos.betweenClosed(remote, remote.offset(10, 3, 4))) level.removeBlock(p, false);
                    level.setChunkForced(cp.x, cp.z, false);
                })
                .thenSucceed();
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVatVat(GameTestHelper h) {
        reload(h, VAT, VAT, 2);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMixerMixer(GameTestHelper h) {
        reload(h, MIXER, MIXER, 2);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVatMixer(GameTestHelper h) {
        reload(h, VAT, MIXER, 2);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMixerVat(GameTestHelper h) {
        reload(h, MIXER, VAT, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT1Kind0(GameTestHelper h) {
        cladding(h, VAT, VatTier.T1, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT1Kind1(GameTestHelper h) {
        cladding(h, VAT, VatTier.T1, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT1Kind2(GameTestHelper h) {
        cladding(h, VAT, VatTier.T1, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT1Kind3(GameTestHelper h) {
        cladding(h, VAT, VatTier.T1, 3);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT2Kind0(GameTestHelper h) {
        cladding(h, VAT, VatTier.T2, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT2Kind1(GameTestHelper h) {
        cladding(h, VAT, VatTier.T2, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT2Kind2(GameTestHelper h) {
        cladding(h, VAT, VatTier.T2, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT2Kind3(GameTestHelper h) {
        cladding(h, VAT, VatTier.T2, 3);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT3Kind0(GameTestHelper h) {
        cladding(h, VAT, VatTier.T3, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT3Kind1(GameTestHelper h) {
        cladding(h, VAT, VatTier.T3, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT3Kind2(GameTestHelper h) {
        cladding(h, VAT, VatTier.T3, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingVATT3Kind3(GameTestHelper h) {
        cladding(h, VAT, VatTier.T3, 3);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingMIXERT1Kind0(GameTestHelper h) {
        cladding(h, MIXER, VatTier.T1, 0);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingMIXERT1Kind1(GameTestHelper h) {
        cladding(h, MIXER, VatTier.T1, 1);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingMIXERT1Kind2(GameTestHelper h) {
        cladding(h, MIXER, VatTier.T1, 2);
    }

    @GameTest(batch = "interMachineIsolation", template = "empty", timeoutTicks = 190)
    public static void claddingMIXERT1Kind3(GameTestHelper h) {
        cladding(h, MIXER, VatTier.T1, 3);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATVATGap0(GameTestHelper h) {
        reload(h, VAT, VAT, 0);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATVATGap1(GameTestHelper h) {
        reload(h, VAT, VAT, 1);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATVATGap3(GameTestHelper h) {
        reload(h, VAT, VAT, 3);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATVATGap4(GameTestHelper h) {
        reload(h, VAT, VAT, 4);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATMIXERGap0(GameTestHelper h) {
        reload(h, VAT, MIXER, 0);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATMIXERGap1(GameTestHelper h) {
        reload(h, VAT, MIXER, 1);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATMIXERGap3(GameTestHelper h) {
        reload(h, VAT, MIXER, 3);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadVATMIXERGap4(GameTestHelper h) {
        reload(h, VAT, MIXER, 4);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERVATGap0(GameTestHelper h) {
        reload(h, MIXER, VAT, 0);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERVATGap1(GameTestHelper h) {
        reload(h, MIXER, VAT, 1);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERVATGap3(GameTestHelper h) {
        reload(h, MIXER, VAT, 3);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERVATGap4(GameTestHelper h) {
        reload(h, MIXER, VAT, 4);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERMIXERGap0(GameTestHelper h) {
        reload(h, MIXER, MIXER, 0);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERMIXERGap1(GameTestHelper h) {
        reload(h, MIXER, MIXER, 1);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERMIXERGap3(GameTestHelper h) {
        reload(h, MIXER, MIXER, 3);
    }

    @GameTest(batch = "interMachineDiscovery", template = "empty", timeoutTicks = 1000)
    public static void reloadMIXERMIXERGap4(GameTestHelper h) {
        reload(h, MIXER, MIXER, 4);
    }
}
