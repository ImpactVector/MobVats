package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class AcceptanceMixerGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1);

    private static void clearanceReport(GameTestHelper h, MultiblockMixer c, String name) {
        var a = c.assembly().orElseThrow();
        var volumes = MixerSweep.occupied(
                h.getLevel(), h.absolutePos(O), h.absolutePos(O.offset(4, 3, 4)), c.conduitGeometry());
        var conflicts = new java.util.ArrayList<String>();
        for (var v : volumes) {
            h.assertTrue(
                    !MixerSweep.intersects(
                            a.vessel(), a.impeller(), a.shaftLength(), a.floorInsetPixels(), v.bounds(), true),
                    "Acceptance device intersects selected vessel: " + v);
            if (MixerSweep.intersects(
                    MixerVesselGeometry.forDiameter(5),
                    a.impeller(),
                    a.shaftLength(),
                    a.floorInsetPixels(),
                    v.bounds(),
                    true)) conflicts.add(v.owner().subtract(h.absolutePos(O)).toShortString() + " " + v.kind());
        }
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("mixer-" + name + "-clearance.txt"),
                    c.capacityDiagnostics() + "\nCanonical conflicts (relative to enclosure origin):\n"
                            + String.join("\n", conflicts) + "\nSelected vessel intersections: 0\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void fullHeightAcceptanceMixer(GameTestHelper h) {
        AcceptanceMixerFixtures.build(h.getLevel(), h.absolutePos(O));
        h.setBlock(O.offset(1, 3, 2), MixerContent.state(MixerPartType.Drive));
        h.runAtTickTime(40, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(c.isAssembled(), "Acceptance enclosure rejected: " + c.getLastError());
            var a = c.assembly().orElseThrow();
            h.assertTrue(
                    a.shaftLength() == 3
                            && h.getBlockState(O.offset(2, 0, 2)).is(MixerContent.block(MixerPartType.Shaft)),
                    "Bottom mixing level omitted");
            var shorter = MixerSweep.compile(
                    h.getLevel(),
                    h.absolutePos(O),
                    h.absolutePos(O.offset(4, 3, 4)),
                    h.absolutePos(O.offset(2, 3, 2)),
                    2,
                    c.conduitGeometry());
            h.assertTrue(a.slotCount() > shorter.slotCount(), "Extended shaft added no usable bottom volume");
            int buffers = 0;
            for (int x = 0; x < 5; x++)
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 5; z++) {
                        var pos = O.offset(x, y, z);
                        if (h.getLevel().getBlockEntity(h.absolutePos(pos)) instanceof FluidBufferEntity) {
                            buffers++;
                            h.assertTrue(
                                    (x == 0 || x == 4) && (z == 0 || z == 4) && y < 3,
                                    "Buffer intrudes into mixing volume or ceiling");
                            var block = (FluidBufferBlock) h.getBlockState(pos).getBlock();
                            h.assertTrue(
                                    CladdingMaterial.installed(h.getBlockState(pos))
                                                            .shell()
                                                    == ConduitShell.MIXER
                                            && CladdingMaterial.installed(h.getBlockState(pos))
                                                            .cladding()
                                                    == ConduitCladding.GLASS,
                                    "Buffer lost integrated Mixer cladding");
                        }
                    }
            h.assertTrue(buffers == 12, "Missing vertical edge storage");
            h.assertTrue(
                    c.storage().bulkConnected(h.absolutePos(O.offset(0, 1, 2))).stream()
                                    .mapToInt(t -> t.getCapacity())
                                    .sum()
                            == 48000,
                    "Some edge buffers cannot reach Controller");
            h.assertTrue(
                    c.storage().bulkConnected(h.absolutePos(O.offset(2, 1, 0))).stream()
                                    .mapToInt(t -> t.getCapacity())
                                    .sum()
                            == 48000,
                    "Some edge buffers cannot reach Fluid Input");
            for (int y = 0; y < 3; y++)
                for (var pos : java.util.List.of(O.offset(0, y, 1), O.offset(4, y, 3))) {
                    var block = (ConduitBlock) h.getBlockState(pos).getBlock();
                    h.assertTrue(
                            CladdingMaterial.installed(h.getBlockState(pos)).cladding() == ConduitCladding.GLASS
                                    && CladdingMaterial.installed(h.getBlockState(pos))
                                                    .shell()
                                            == ConduitShell.MIXER,
                            "Wall riser lost shell cladding");
                    h.assertTrue(
                            c.conduitGeometry().snapshot().cells().stream()
                                    .anyMatch(cell -> cell.position().equals(h.absolutePos(pos))),
                            "Wall riser geometry missing");
                }
            h.assertTrue(
                    c.conduitGeometry().snapshot().cells().size() <= 25,
                    "Acceptance fixture contains unnecessary conduit runs");
            clearanceReport(h, c, "acceptance");
            for (int x = 1; x < 4; x++)
                for (int y = 0; y < 3; y++)
                    for (int z = 1; z < 4; z++)
                        if (x != 2 || z != 2)
                            h.assertTrue(
                                    y == 0
                                            ? h.getBlockState(O.offset(x, y, z))
                                                    .is(MixerContent.block(MixerPartType.Glass))
                                            : h.getBlockState(O.offset(x, y, z)).isAir(),
                                    "Infrastructure intrudes into mixing space");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void cultureAnnexPortsRetainControllerStorage(GameTestHelper h) {
        AcceptanceMixerFixtures.build(
                h.getLevel(),
                h.absolutePos(O),
                java.util.List.of(
                        "minecraft:water",
                        "minecraft:water",
                        "minecraft:water",
                        "mobvats:experience",
                        "mobvats:experience",
                        "mobvats:experience",
                        "mobvats:culture_slurry",
                        "mobvats:culture_slurry",
                        "mobvats:culture_slurry",
                        "mobvats:culture_slurry",
                        "mobvats:culture_slurry",
                        "mobvats:culture_slurry"));
        h.setBlock(O.offset(0, 1, 1), MixerContent.state(MixerPartType.FluidPort));
        h.setBlock(O.offset(0, 1, 3), MixerContent.state(MixerPartType.FluidPort));
        h.setBlock(O.offset(4, 1, 3), MixerContent.state(MixerPartType.FluidVoid));
        AcceptanceMixerFixtures.connectControllerAbove(h.getLevel(), h.absolutePos(O));
        h.runAtTickTime(40, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(c.isAssembled(), "Culture annex did not form");
            h.assertTrue(
                    c.storage().bulkConnected(h.absolutePos(O.offset(0, 1, 2))).stream()
                                    .mapToInt(t -> t.getCapacity())
                                    .sum()
                            == 48000,
                    "Annex ports isolated Controller from storage");
            h.assertTrue(
                    c.storage()
                            .connectedEndpoints(
                                    h.absolutePos(O.offset(0, 1, 2)),
                                    h.absolutePos(O.offset(0, 1, 1)),
                                    ConduitResource.FLUID),
                    "Annex XP input cannot reach Controller");
            clearanceReport(h, c, "annex");
            h.succeed();
        });
    }
}
