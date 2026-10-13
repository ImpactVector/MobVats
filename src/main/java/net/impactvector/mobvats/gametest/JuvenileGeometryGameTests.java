package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class JuvenileGeometryGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            HEAD = O.offset(2, 2, 2),
            FEED = O.offset(2, 1, 0),
            TOOL = O.offset(2, 1, 4);

    private static MultiblockVat vat(GameTestHelper h) {
        return ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void calfBodyAndHeadUseSeparateVanillaTransforms(GameTestHelper h) {
        var position = new Vec3(10, 20, 30);
        var adult = MobRenderDefinition.attachmentBounds(ItemEssence.COW, position, LifeStage.ADULT);
        var baby = MobRenderDefinition.attachmentBounds(ItemEssence.COW, position, LifeStage.JUVENILE);
        h.assertTrue(
                Math.abs(baby.getXsize() * 2 - adult.getXsize()) < 1e-6
                        && Math.abs(baby.getYsize() * 2 - adult.getYsize()) < 1e-6
                        && baby.minY == adult.minY,
                "Calf body did not halve around the same feet position");
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var anchor = MobRenderDefinition.forEntity(ItemEssence.COW).headAnchor(baby, facing, LifeStage.JUVENILE);
            var expected = position.add(facing.getStepX() * 7.0 / 16, 1.501 - 14.0 / 16, facing.getStepZ() * 7.0 / 16);
            h.assertTrue(
                    anchor.distanceToSqr(expected) < 1e-12,
                    "Calf head center ignored CowModel juvenile translation: " + facing);
        }
        var harness = BlockPos.containing(position).above(3);
        var adultStraps = HarnessStraps.curves(harness, adult, MobRenderDefinition.DEFAULT);
        var babyStraps = HarnessStraps.curves(harness, baby, MobRenderDefinition.DEFAULT);
        for (int i = 0; i < 4; i++)
            h.assertTrue(
                    adultStraps
                                    .get(i)
                                    .points()
                                    .getFirst()
                                    .equals(babyStraps.get(i).points().getFirst())
                            && baby.contains(babyStraps.get(i).points().getLast())
                            && babyStraps.get(i).points().getLast().y
                                    < adultStraps.get(i).points().getLast().y,
                    "Calf straps moved the mount or retained adult endpoint");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void controllerRebuildsJuvenileEndpointsOnReplacementReloadAndMaturity(GameTestHelper h) {
        VatGameTests.build(h, 5);
        h.setBlock(FEED, Content.state(VatPartType.ItemPort));
        h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity) h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        Slice11Fixtures.vatStorage(h, O, 5, 5);
        Slice11Fixtures.feedFrom(h, FEED, FEED.south());
        final VatPresentation[] adult = {null};
        final long[] revision = {0};
        h.runAtTickTime(30, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(HEAD);
            adult[0] = head.presentation();
            revision[0] = head.presentationRevision();
            h.assertTrue(
                    adult[0].tools().getFirst().assigned() && !adult[0].feeds().isEmpty(), "Adult endpoints missing");
            ((VatItemPortEntity) h.getBlockEntity(FEED)).staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
        });
        h.runAtTickTime(75, () -> {
            var c = vat(h);
            var head = (VatHeadEntity) h.getBlockEntity(HEAD);
            var assembly = c.getAssemblies().getFirst();
            var baby = head.presentation();
            h.assertTrue(
                    head.lifeStage() == LifeStage.JUVENILE && head.presentationRevision() > revision[0],
                    "Replacement did not publish stage-adjusted presentation");
            var arm = baby.tools().getFirst();
            h.assertTrue(
                    arm.assigned()
                            && assembly.attachmentBounds().contains(arm.target())
                            && arm.target().y < adult[0].tools().getFirst().target().y
                            && arm.base().equals(adult[0].tools().getFirst().base()),
                    "High port did not aim down into calf body from its original mount");
            var expected = assembly.renderPosition()
                    .add(
                            head.getSpecimenFacing().getStepX() * 7.0 / 16,
                            1.501 - 14.0 / 16,
                            head.getSpecimenFacing().getStepZ() * 7.0 / 16);
            h.assertTrue(
                    baby.feeds().getFirst().target().distanceToSqr(expected) < 1e-12
                            && VatPresentation.load(baby.save()).equals(baby),
                    "Calf feed center or presentation transport incorrect");
            h.assertTrue(
                    c.getTopology().actors().stream().anyMatch(r -> r.actor().equals(h.absolutePos(TOOL))),
                    "Calf size lost structurally reachable actor");
            var saved = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            head.setLifeStage(LifeStage.ADULT);
            c.rebuildTopology();
            c.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            c.processEngine().tick();
            h.assertTrue(
                    head.lifeStage() == LifeStage.JUVENILE
                            && head.presentation().equals(baby),
                    "Reload reused stale adult endpoints");
            c.processEngine().state(h.absolutePos(HEAD)).growth().elapsed = 23999;
        });
        h.runAtTickTime(100, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(HEAD);
            h.assertTrue(
                    head.lifeStage() == LifeStage.ADULT && head.presentation().equals(adult[0]),
                    "Maturity did not restore adult connection geometry");
            h.succeed();
        });
    }
}
