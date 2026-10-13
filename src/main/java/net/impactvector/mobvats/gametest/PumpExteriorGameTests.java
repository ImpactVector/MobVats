package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.EndpointGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;

/** Headless assembly and persisted-entity restoration, paired with CPU mesh tests.
 * Entity replacement exercises saved data reconstruction; it is not a chunk-unload test. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class PumpExteriorGameTests {
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void vatPullInGlassWall(GameTestHelper h) {
        verify(h, false, false, true);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void vatPushInGlassWall(GameTestHelper h) {
        verify(h, false, true, true);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void vatPullInCasingWall(GameTestHelper h) {
        verify(h, false, false, false);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void vatPushInCasingWall(GameTestHelper h) {
        verify(h, false, true, false);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mixerPullInGlassWall(GameTestHelper h) {
        verify(h, true, false, true);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mixerPushInGlassWall(GameTestHelper h) {
        verify(h, true, true, true);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mixerPullInCasingWall(GameTestHelper h) {
        verify(h, true, false, false);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mixerPushInCasingWall(GameTestHelper h) {
        verify(h, true, true, false);
    }

    private static VatCasingState shell(BlockEntity entity) {
        if (entity instanceof AbstractVatEntity p)
            return p.getMultiblockController()
                    .filter(MultiblockVat::isAssembled)
                    .map(c -> VatCasingState.from(
                            p.getBlockPos(),
                            c.getBoundingBox().getMin(),
                            c.getBoundingBox().getMax()))
                    .orElse(VatCasingState.UNFORMED);
        var p = (AbstractMixerEntity) entity;
        return p.getMultiblockController()
                .filter(MultiblockMixer::isAssembled)
                .map(c -> VatCasingState.from(
                        p.getBlockPos(),
                        c.getBoundingBox().getMin(),
                        c.getBoundingBox().getMax()))
                .orElse(VatCasingState.UNFORMED);
    }

    private static void verify(GameTestHelper h, boolean mixer, boolean push, boolean glass) {
        var o = new BlockPos(1, 2, 1);
        var p = o.offset(4, 1, 2);
        var broken = o.offset(0, 0, 0);
        if (mixer) MachineFixtures.mixer(h::setBlock, o, 5, 4, 5, 2, 2, glass);
        else {
            MachineFixtures.vat(h::setBlock, o, 5, 5, 4);
            ((net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity)
                            h.getBlockEntity(o.offset(2, 2, 2)))
                    .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
            if (!glass)
                for (int x = 0; x < 5; x++)
                    for (int y = 1; y < 3; y++)
                        for (int z = 0; z < 5; z++)
                            if (h.getBlockState(o.offset(x, y, z)).is(Content.block(VatPartType.Glass)))
                                h.setBlock(o.offset(x, y, z), Content.state(VatPartType.Casing));
        }
        var block = mixer
                ? MixerContent.state(push ? MixerPartType.PushPump : MixerPartType.PullPump)
                : Content.state(push ? VatPartType.PushPump : VatPartType.PullPump);
        block = block.setValue(
                net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.PROPERTY,
                mixer
                        ? (glass
                                ? net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial
                                        .MIXER_T1_GLASS
                                : net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial
                                        .MIXER_T1_CASING)
                        : (glass
                                ? net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.VAT_T1_GLASS
                                : net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial
                                        .VAT_T1_CASING));
        final var installed = block;
        h.setBlock(p, installed);
        var collision = h.getBlockState(p)
                .getCollisionShape(h.getLevel(), h.absolutePos(p))
                .toAabbs();
        var endpoint = EndpointGeometry.from(
                h.absolutePos(p),
                h.absolutePos(o),
                h.absolutePos(o.offset(4, 3, 4)),
                h.absolutePos(p.west()),
                EndpointGeometry.Kind.PASSIVE);
        var saved = new CompoundTag[1];
        var original = new BlockEntity[1];
        h.runAtTickTime(35, () -> {
            original[0] = h.getBlockEntity(p);
            h.assertTrue(
                    shell(original[0]).outwardFaces() == 1 << Direction.EAST.ordinal(),
                    "Pump did not form on actual exterior face");
            saved[0] = original[0].saveWithFullMetadata(h.getLevel().registryAccess());
            h.setBlock(broken, Blocks.AIR);
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(!shell(h.getBlockEntity(p)).formed(), "Broken enclosure retained formed presentation");
            h.setBlock(p, Blocks.AIR);
            h.setBlock(p, installed);
            h.getBlockEntity(p).loadWithComponents(saved[0], h.getLevel().registryAccess());
            h.setBlock(broken, mixer ? MixerContent.state(MixerPartType.Casing) : Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(100, () -> {
            var restored = h.getBlockEntity(p);
            h.assertTrue(
                    restored != original[0] && shell(restored).outwardFaces() == 1 << Direction.EAST.ordinal(),
                    "Saved pump did not reconstruct formed facing");
            h.assertTrue(
                    h.getBlockState(p).is(installed.getBlock())
                            && restored.getBlockPos().equals(h.absolutePos(p)),
                    "Pump identity or position changed");
            h.assertTrue(
                    collision.equals(h.getBlockState(p)
                            .getCollisionShape(h.getLevel(), h.absolutePos(p))
                            .toAabbs()),
                    "Visual sleeve changed collision");
            h.assertTrue(
                    endpoint.equals(EndpointGeometry.from(
                            h.absolutePos(p),
                            h.absolutePos(o),
                            h.absolutePos(o.offset(4, 3, 4)),
                            h.absolutePos(p.west()),
                            EndpointGeometry.Kind.PASSIVE)),
                    "Service endpoint alignment changed");
            h.succeed();
        });
    }
}
