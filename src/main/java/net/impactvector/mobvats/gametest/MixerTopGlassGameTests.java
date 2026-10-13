package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerTopGlassGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), C = O.offset(0, 1, 1);

    private static BlockPos rig(GameTestHelper h, int width, int height, int shaft, MixerPartType drive) {
        var top = MachineFixtures.mixer(h::setBlock, O, width, height, width, shaft, 1, true);
        for (int x = 1; x < width - 1; x++)
            for (int z = 1; z < width - 1; z++)
                h.setBlock(O.offset(x, height - 1, z), MixerContent.state(MixerPartType.Glass));
        h.setBlock(top, MixerContent.state(MixerPartType.Impeller));
        if (drive != null) h.setBlock(top.west(), MixerContent.state(drive));
        return top;
    }

    private static MultiblockMixer controller(GameTestHelper h) {
        return ((MixerControllerEntity) h.getBlockEntity(C))
                .getMultiblockController()
                .orElseThrow();
    }

    private static void verify(GameTestHelper h, BlockPos top, int depth) {
        var c = controller(h);
        h.assertTrue(
                c.isAssembled(),
                "Top glass rejected: "
                        + c.getLastError().map(e -> e.getChatMessage().getString()));
        var a = c.assembly().orElseThrow();
        h.assertTrue(
                a.impeller().equals(h.absolutePos(top)) && a.shaftLength() == depth,
                "Top glass changed centered Impeller or Shaft validation");
        var descriptor = c.renderDescriptor().orElseThrow();
        var tag = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                MixerRenderDescriptor.from(MixerAssembly.load(tag.getCompound("mixerAssembly")))
                        .equals(descriptor),
                "Top glass changed saved assembly presentation");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void minimumMixerRemainsCompatible(GameTestHelper h) {
        var top = rig(h, 3, 3, 1, null);
        h.runAtTickTime(35, () -> {
            verify(h, top, 1);
            h.succeed();
        });
    }

    private static void driven(GameTestHelper h, MixerPartType drive) {
        var top = rig(h, 5, 5, 3, drive);
        h.runAtTickTime(35, () -> {
            verify(h, top, 3);
            h.assertTrue(controller(h).drive().isPresent(), "Glass surrounding adjacent Drive broke coupling");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glassAroundCreativeDriveForms(GameTestHelper h) {
        driven(h, MixerPartType.Drive);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glassAroundFeDriveForms(GameTestHelper h) {
        driven(h, MixerPartType.FeDrive);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glassAroundCombustionDriveForms(GameTestHelper h) {
        driven(h, MixerPartType.CombustionDrive);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void largeGlassRoofPreservesFullShaft(GameTestHelper h) {
        var top = rig(h, 7, 7, 5, MixerPartType.FeDrive);
        h.runAtTickTime(35, () -> {
            verify(h, top, 5);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void casingRoofCanBecomeGlassWithoutChangingAssembly(GameTestHelper h) {
        var top = MachineFixtures.mixer(h::setBlock, O, 5, 5, 5, 3, 1, false);
        final MixerAssembly[] original = {null};
        h.runAtTickTime(35, () -> {
            verify(h, top, 3);
            original[0] = controller(h).assembly().orElseThrow();
            for (int x = 1; x < 4; x++)
                for (int z = 1; z < 4; z++)
                    if (x != 2 || z != 2) h.setBlock(O.offset(x, 4, z), MixerContent.state(MixerPartType.Glass));
        });
        h.runAtTickTime(75, () -> {
            verify(h, top, 3);
            h.assertTrue(
                    controller(h).assembly().orElseThrow().equals(original[0]),
                    "Roof replacement changed existing saved geometry or capacity");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glassCladRoofConduitsAndBuffersFollowGlassRules(GameTestHelper h) {
        var top = rig(h, 5, 5, 1, MixerPartType.FeDrive);
        h.setBlock(top.north(), ConduitContent.state(ConduitResource.FLUID, ConduitCladding.GLASS, ConduitShell.MIXER));
        h.setBlock(top.south(), ConduitContent.state(ConduitResource.ITEM, ConduitCladding.GLASS, ConduitShell.MIXER));
        h.setBlock(top.east(), FluidBufferContent.state(ConduitCladding.GLASS, ConduitShell.MIXER));
        h.runAtTickTime(35, () -> {
            verify(h, top, 1);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void roofGlassStillRequiresCenteredImpeller(GameTestHelper h) {
        var top = rig(h, 5, 5, 3, null);
        h.setBlock(top, MixerContent.state(MixerPartType.Glass));
        h.runAtTickTime(35, () -> {
            h.assertTrue(!controller(h).isAssembled(), "Roof glass substituted for required Impeller");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void roofGlassStillRejectsUncoupledDrive(GameTestHelper h) {
        var top = rig(h, 7, 5, 1, null);
        h.setBlock(top.west(2), MixerContent.state(MixerPartType.FeDrive));
        h.runAtTickTime(35, () -> {
            h.assertTrue(!controller(h).isAssembled(), "Roof glass bypassed adjacent Drive requirement");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void glassPermitsEveryShellCategory(GameTestHelper h) {
        for (var pos : PartPosition.values()) {
            boolean expected = pos != PartPosition.Interior && pos != PartPosition.Unknown;
            h.assertTrue(MixerPartType.Glass.allows(pos) == expected, "Glass changed unrelated position rule: " + pos);
        }
        var min = h.absolutePos(O);
        var max = h.absolutePos(O.offset(4, 4, 4));
        for (var block : List.of(
                MixerContent.state(MixerPartType.Glass),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.GLASS, ConduitShell.MIXER))) {
            for (var cell : List.of(O.offset(0, 4, 2), O.offset(0, 4, 0), O.offset(2, 0, 2))) {
                h.setBlock(cell, block);
                if (block.getBlock() instanceof ConduitBlock)
                    h.assertTrue(
                            ConduitStructure.allows(h.getLevel(), h.absolutePos(cell), min, max, true),
                            "Glass conduit rejected frame, corner or bottom");
            }
        }
        h.succeed();
    }
}
