package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Tests the existing authoritative lookup used by the chunk model; no second connectivity system. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class ConduitArtLifecycleGameTests {
    private static void restoration(GameTestHelper h, boolean mixer) {
        var origin = new BlockPos(1, 1, 1);
        BlockPos controllerPos;
        if (mixer) {
            MachineFixtures.mixerRig((p, s) -> h.setBlock(p, s), origin);
            controllerPos = origin.offset(0, 1, 2);
        } else {
            VatGameTests.build(h, 5);
            ((net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity)
                            h.getBlockEntity(new BlockPos(3, 3, 3)))
                    .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
            controllerPos = origin.offset(1, 1, 0);
        }
        var conduit = origin.offset(0, 2, 2);
        var state = ConduitContent.state(
                ConduitResource.ITEM, ConduitCladding.GLASS, mixer ? ConduitShell.MIXER : ConduitShell.VAT);
        h.setBlock(conduit, state);
        h.runAtTickTime(35, () -> {
            var c = ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit));
            h.assertTrue(c != null, "Reload fixture not assembled");
            if (mixer)
                ((net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity)
                                h.getBlockEntity(controllerPos))
                        .onChunkUnloaded();
            else
                ((net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatControllerEntity)
                                h.getBlockEntity(controllerPos))
                        .onChunkUnloaded();
            h.assertTrue(
                    ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit)) == null,
                    "Paused controller still suppresses preview");
            h.assertTrue(
                    c.conduitGeometry().snapshot().cells().isEmpty(), "Paused controller retained formed geometry");
            h.getBlockEntity(controllerPos).clearRemoved();
        });
        h.runAtTickTime(
                75,
                () -> h.succeedWhen(() -> {
                    var c = ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit));
                    h.assertTrue(c != null, "Restored controller failed formed ownership");
                    h.assertTrue(
                            c.conduitGeometry().snapshot().cells().stream()
                                    .anyMatch(cell -> cell.position().equals(h.absolutePos(conduit))),
                            "Restoration lost conduit geometry");
                    h.assertTrue(h.getBlockState(conduit).equals(state), "Restoration replaced conduit");
                }));
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void vatChunkLifecycleRestoresConduitOwnership(GameTestHelper h) {
        restoration(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void mixerChunkLifecycleRestoresConduitOwnership(GameTestHelper h) {
        restoration(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void previewOwnershipTracksAssemblyAndDisassembly(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity)
                        h.getBlockEntity(new BlockPos(3, 3, 3)))
                .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
        var conduit = new BlockPos(1, 2, 3);
        var gap = new BlockPos(1, 2, 2);
        var preview = ConduitContent.state(ConduitResource.FLUID, ConduitCladding.GLASS)
                .setValue(ConduitBlock.MODE, ConduitMode.TAP);
        h.setBlock(conduit, preview);
        var wall = h.getBlockState(gap);
        h.setBlock(gap, Blocks.AIR);
        h.runAtTickTime(20, () -> {
            h.assertTrue(
                    ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit)) == null,
                    "Incomplete machine suppressed construction preview");
            h.setBlock(gap, wall);
        });
        h.runAtTickTime(55, () -> {
            var controller = ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit));
            h.assertTrue(controller != null, "Assembled machine did not suppress construction preview");
            h.assertTrue(
                    controller.conduitGeometry().snapshot().cells().stream()
                            .anyMatch(c -> c.position().equals(h.absolutePos(conduit))),
                    "Controller did not own formed conduit");
            var snapshot = controller.conduitGeometry().snapshot();
            h.assertTrue(
                    ConduitGeometry.Snapshot.load(snapshot.save()).equals(snapshot),
                    "Geometry changed during restoration");
            h.assertTrue(
                    h.getBlockState(conduit).equals(preview), "Assembly replaced block or added visual blockstate");
            h.setBlock(gap, Blocks.AIR);
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(
                    ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit)) == null,
                    "Disassembly did not restore preview");
            h.assertTrue(h.getBlockState(conduit).equals(preview), "Disassembly replaced conduit");
            h.setBlock(gap, wall);
        });
        h.runAtTickTime(
                135,
                () -> h.succeedWhen(() -> {
                    h.assertTrue(
                            ConduitStructure.containing(h.getLevel(), h.absolutePos(conduit)) != null,
                            "Reassembly did not recover formed ownership");
                    h.assertTrue(h.getBlockState(conduit).equals(preview), "Reassembly changed conduit blockstate");
                }));
    }
}
