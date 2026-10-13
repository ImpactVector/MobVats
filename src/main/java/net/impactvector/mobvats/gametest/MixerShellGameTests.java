package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatGlassState;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerShellGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), C = O.offset(0, 1, 2), TOP = O.offset(2, 4, 2);

    private static MultiblockMixer controller(GameTestHelper h, BlockPos pos) {
        return ((MixerControllerEntity) h.getBlockEntity(pos))
                .getMultiblockController()
                .orElseThrow();
    }

    private static MultiblockMixer formed(GameTestHelper h) {
        var c = controller(h, C);
        h.assertTrue(
                c.isAssembled(),
                "Shell rejected: "
                        + c.getLastError().map(e -> e.getChatMessage().getString()));
        return c;
    }

    private static List<BlockPos> shell() {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++)
                    if (x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4) cells.add(O.offset(x, y, z));
        return cells;
    }

    private static void rig(GameTestHelper h, MixerPartType drive) {
        MachineFixtures.mixer(h::setBlock, O, 5, 5, 5, 3, 2, false);
        for (var pos : shell())
            if (!pos.equals(C) && !pos.equals(TOP)) h.setBlock(pos, MixerContent.state(MixerPartType.Glass));
        h.setBlock(TOP.west(), MixerContent.state(drive));
    }

    private static void solid(GameTestHelper h, BlockPos pos, MixerPartType type) {
        var state = h.getBlockState(pos);
        var absolute = h.absolutePos(pos);
        h.assertTrue(MixerPlacement.fullMachinery(type), "Machinery would receive a thin formed panel model");
        h.assertTrue(
                state.canOcclude()
                        && Shapes.block().equals(state.getShape(h.getLevel(), absolute))
                        && Shapes.block().equals(state.getCollisionShape(h.getLevel(), absolute)),
                "Machinery lost full solid footprint: " + type);
        h.assertTrue(((AbstractMixerEntity) h.getBlockEntity(pos)).isMachineAssembled(), "Machinery not assembled");
    }

    private static void glass(GameTestHelper h, MixerPartType drive) {
        rig(h, drive);
        h.runAtTickTime(40, () -> {
            var c = formed(h);
            h.assertTrue(c.drive().isPresent(), "Glass shell lost coupled drive");
            solid(h, TOP, MixerPartType.Impeller);
            solid(h, TOP.west(), drive);
            if (h.getBlockEntity(TOP.west()) instanceof MixerFeDriveEntity motor)
                motor.energy().receiveEnergy(MixerFeDriveEntity.CAPACITY, false);
            if (h.getBlockEntity(TOP.west()) instanceof MixerCombustionDriveEntity motor)
                motor.fuel().insertItem(0, new ItemStack(Items.COAL, 8), false);
            MixerCombustionGameTests.ticks(c, c.spinUpTicks() + 10);
            h.assertTrue(
                    c.operatingState() == MixerOperatingState.RUNNING,
                    "Glass enclosure did not support running machinery");
            solid(h, TOP, MixerPartType.Impeller);
            solid(h, TOP.west(), drive);
            var min = h.absolutePos(O);
            var max = h.absolutePos(O.offset(4, 4, 4));
            for (var pos : shell()) {
                var state = VatGlassState.mixer(h.absolutePos(pos), min, max);
                int expected = (pos.getX() == O.getX() || pos.getX() == O.getX() + 4 ? 1 : 0)
                        + (pos.getY() == O.getY() || pos.getY() == O.getY() + 4 ? 1 : 0)
                        + (pos.getZ() == O.getZ() || pos.getZ() == O.getZ() + 4 ? 1 : 0);
                h.assertTrue(
                        Arrays.stream(Direction.values()).filter(state::hasFace).count() == expected,
                        "Glass omitted shell face/edge/corner plane");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void entirelyGlassCreativeMixer(GameTestHelper h) {
        glass(h, MixerPartType.Drive);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void entirelyGlassFeMixer(GameTestHelper h) {
        glass(h, MixerPartType.FeDrive);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void entirelyGlassCombustionMixer(GameTestHelper h) {
        glass(h, MixerPartType.CombustionDrive);
    }

    private static void clad(GameTestHelper h, ConduitCladding cladding, boolean buffer) {
        rig(h, MixerPartType.Drive);
        int index = 0;
        for (var pos : shell())
            if (!pos.equals(C) && !pos.equals(TOP) && !pos.equals(TOP.west())) {
                var block = buffer
                        ? FluidBufferContent.state(cladding, ConduitShell.MIXER)
                        : ConduitContent.state(ConduitResource.values()[index++ % 2], cladding, ConduitShell.MIXER);
                h.setBlock(pos, block);
            }
        h.runAtTickTime(40, () -> {
            var c = controller(h, C);
            h.assertTrue(
                    c.getBoundingBox().getMin().equals(h.absolutePos(O))
                            && c.getBoundingBox().getMax().equals(h.absolutePos(O.offset(4, 4, 4))),
                    "Passive shell changed envelope");
            for (var pos : shell())
                if (!pos.equals(C) && !pos.equals(TOP) && !pos.equals(TOP.west()))
                    h.assertTrue(
                            ConduitStructure.allows(
                                    h.getLevel(),
                                    h.absolutePos(pos),
                                    c.getBoundingBox().getMin(),
                                    c.getBoundingBox().getMax(),
                                    true),
                            "Clad shell cell rejected: " + pos);
            if (c.isAssembled()) {
                var a = c.assembly().orElseThrow();
                h.assertTrue(a.radiusReduction() > 0, "Packed shell did not shrink vessel");
                for (var v : MixerSweep.occupied(
                        h.getLevel(),
                        c.getBoundingBox().getMin(),
                        c.getBoundingBox().getMax(),
                        c.conduitGeometry()))
                    h.assertTrue(
                            !MixerSweep.intersects(
                                    a.vessel(), a.impeller(), a.shaftLength(), a.floorInsetPixels(), v.bounds(), true),
                            "Packed shell retained an intersection");
            } else
                h.assertTrue(
                        c.getLastError()
                                .orElseThrow()
                                .getChatMessage()
                                .getString()
                                .contains("intersects"),
                        "Packed shell failed for an unrelated reason");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void glassCladConduitsInEveryShellCell(GameTestHelper h) {
        clad(h, ConduitCladding.GLASS, false);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void casingCladConduitsInEveryShellCell(GameTestHelper h) {
        clad(h, ConduitCladding.CASING, false);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void glassCladBuffersInEveryShellCell(GameTestHelper h) {
        clad(h, ConduitCladding.GLASS, true);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void casingCladBuffersInEveryShellCell(GameTestHelper h) {
        clad(h, ConduitCladding.CASING, true);
    }

    @GameTest(template = "empty", timeoutTicks = 480)
    public static void interiorCladdingRejectsAndBareGeometryRecovers(GameTestHelper h) {
        rig(h, MixerPartType.Drive);
        var pos = TOP.below().east();
        final long[] baseline = {0};
        h.runAtTickTime(
                35, () -> baseline[0] = formed(h).assembly().orElseThrow().geometricSweptVoxels());
        var blocks = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
        for (var cladding : List.of(ConduitCladding.CASING, ConduitCladding.GLASS)) {
            for (var resource : ConduitResource.values())
                blocks.add(ConduitContent.state(resource, cladding, ConduitShell.MIXER));
            blocks.add(FluidBufferContent.state(cladding, ConduitShell.MIXER));
        }
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            int tick = 40 + i * 40;
            h.runAtTickTime(tick, () -> h.setBlock(pos, block));
            h.runAtTickTime(
                    tick + 30,
                    () -> h.assertTrue(!controller(h, C).isAssembled(), "Interior cladding accepted: " + block));
        }
        h.runAtTickTime(285, () -> h.setBlock(pos, ConduitContent.state(ConduitResource.ITEM, ConduitCladding.BARE)));
        h.runAtTickTime(325, () -> {
            var c = controller(h, C);
            h.assertTrue(!c.isAssembled() && c.slotCount() == 0, "Bare item conduit left a blocked vessel");
            h.setBlock(pos, ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE));
        });
        h.runAtTickTime(365, () -> {
            h.assertTrue(!controller(h, C).isAssembled(), "Bare fluid conduit intersection ignored");
            h.setBlock(pos, FluidBufferContent.state(ConduitCladding.BARE));
        });
        h.runAtTickTime(405, () -> {
            h.assertTrue(!controller(h, C).isAssembled(), "Bare buffer intersection ignored");
            h.setBlock(pos, Blocks.AIR);
        });
        h.runAtTickTime(445, () -> {
            h.assertTrue(
                    formed(h).assembly().orElseThrow().geometricSweptVoxels() == baseline[0],
                    "Removing interference failed recovery");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void vatCladdingCannotReplaceMixerCorner(GameTestHelper h) {
        rig(h, MixerPartType.Drive);
        var blocks = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
        for (var cladding : List.of(ConduitCladding.CASING, ConduitCladding.GLASS)) {
            for (var resource : ConduitResource.values())
                blocks.add(ConduitContent.state(resource, cladding, ConduitShell.VAT));
            blocks.add(FluidBufferContent.state(cladding, ConduitShell.VAT));
        }
        h.runAtTickTime(35, () -> formed(h));
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            int tick = 40 + i * 40;
            h.runAtTickTime(tick, () -> h.setBlock(O, block));
            h.runAtTickTime(
                    tick + 30,
                    () -> h.assertTrue(!controller(h, C).isAssembled(), "Vat cladding accepted in Mixer: " + block));
        }
        h.runAtTickTime(280, () -> h.setBlock(O, MixerContent.state(MixerPartType.Glass)));
        h.runAtTickTime(315, () -> {
            formed(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void infrastructurePolicyAgreesForEveryCell(GameTestHelper h) {
        var min = h.absolutePos(O);
        var max = h.absolutePos(O.offset(4, 4, 4));
        for (var family : List.of(ConduitShell.VAT, ConduitShell.MIXER))
            for (var cladding : List.of(ConduitCladding.CASING, ConduitCladding.GLASS)) {
                var blocks = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
                for (var resource : ConduitResource.values())
                    blocks.add(ConduitContent.state(resource, cladding, family));
                blocks.add(FluidBufferContent.state(cladding, family));
                policy(h, blocks, cladding, family, min, max);
            }
        policy(
                h,
                List.of(
                        ConduitContent.state(ConduitResource.ITEM, ConduitCladding.BARE),
                        ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE),
                        FluidBufferContent.state(ConduitCladding.BARE)),
                ConduitCladding.BARE,
                ConduitShell.SHARED,
                min,
                max);
        h.succeed();
    }

    private static void policy(
            GameTestHelper h,
            List<net.minecraft.world.level.block.state.BlockState> blocks,
            ConduitCladding cladding,
            ConduitShell family,
            BlockPos min,
            BlockPos max) {
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++) {
                    var pos = O.offset(x, y, z);
                    var absolute = h.absolutePos(pos);
                    boolean inside = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
                    boolean expected =
                            cladding == ConduitCladding.BARE ? inside : family == ConduitShell.MIXER && !inside;
                    h.assertTrue(
                            MixerPlacement.allows(cladding, family, absolute, min, max) == expected,
                            "Placement policy disagrees at " + pos);
                    for (var block : blocks) {
                        h.setBlock(pos, block);
                        h.assertTrue(
                                ConduitStructure.allows(h.getLevel(), absolute, min, max, true) == expected,
                                "Structure policy disagrees for " + block + " at " + pos);
                    }
                }
        h.assertTrue(
                !MixerPlacement.allows(cladding, family, min.west(), min, max),
                "Infrastructure permitted outside envelope");
    }

    @GameTest(template = "empty", timeoutTicks = 440)
    public static void bareInfrastructureCannotReplaceShell(GameTestHelper h) {
        rig(h, MixerPartType.Drive);
        var cells = List.of(O.offset(0, 2, 2), O.offset(0, 2, 0), O);
        var blocks = List.of(
                ConduitContent.state(ConduitResource.ITEM, ConduitCladding.BARE),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE),
                FluidBufferContent.state(ConduitCladding.BARE));
        h.runAtTickTime(35, () -> formed(h));
        int index = 0;
        for (var pos : cells)
            for (var block : blocks) {
                int tick = 40 + index++ * 40;
                h.runAtTickTime(tick, () -> h.setBlock(pos, block));
                h.runAtTickTime(tick + 30, () -> {
                    h.assertTrue(
                            !controller(h, C).isAssembled(), "Bare infrastructure supplied shell structure at " + pos);
                    h.setBlock(pos, MixerContent.state(MixerPartType.Glass));
                });
            }
        h.runAtTickTime(435, () -> {
            formed(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exteriorMixerCladdingCannotMergeFormedMixers(GameTestHelper h) {
        rig(h, MixerPartType.Drive);
        var second = O.offset(6, 0, 0);
        var c2 = second.offset(0, 1, 2);
        MachineFixtures.mixer(h::setBlock, second, 5, 5, 5, 3, 2, true);
        h.runAtTickTime(35, () -> {
            formed(h);
            h.assertTrue(controller(h, c2).isAssembled(), "Second Mixer failed baseline");
            for (int x = 4; x <= 6; x++)
                h.setBlock(
                        O.offset(x, 2, 2),
                        ConduitContent.state(ConduitResource.FLUID, ConduitCladding.GLASS, ConduitShell.MIXER));
        });
        h.runAtTickTime(75, () -> {
            var a = formed(h);
            var b = controller(h, c2);
            h.assertTrue(b.isAssembled() && a != b, "Exterior passive bridge merged Mixers");
            h.assertTrue(
                    a.getBoundingBox().getMaxX()
                                    == h.absolutePos(O.offset(4, 4, 4)).getX()
                            && b.getBoundingBox().getMinX()
                                    == h.absolutePos(second).getX(),
                    "Cladding extended neighboring envelopes");
            h.succeed();
        });
    }
}
