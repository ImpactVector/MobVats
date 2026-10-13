package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

/** Server-side transport/descriptor coverage. Does not claim GPU visibility or simulate chunk unload. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatRenderLifecycleGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoLoadedCowVatsKeepDescriptorsAcrossMediumAndHeadRestoration(GameTestHelper h) {
        var positions = List.of(new BlockPos(3, 3, 3), new BlockPos(9, 3, 3));
        for (var origin : List.of(new BlockPos(1, 1, 1), new BlockPos(7, 1, 1)))
            MachineFixtures.vat(h::setBlock, origin, 5, 5, 4);
        for (var pos : positions) ((VatHeadEntity) h.getBlockEntity(pos)).encode(ItemEssence.COW);
        h.runAtTickTime(35, () -> {
            for (var pos : positions) {
                var head = (VatHeadEntity) h.getBlockEntity(pos);
                var vat = head.getMultiblockController().orElseThrow();
                h.assertTrue(vat.isAssembled(), "Two cow Vats did not load together");
                var descriptor = VatRenderDescriptor.from(vat, head);
                for (var fluid : List.of(
                        net.neoforged.neoforge.fluids.FluidStack.EMPTY,
                        new net.neoforged.neoforge.fluids.FluidStack(
                                net.minecraft.world.level.material.Fluids.WATER, 1000),
                        new net.neoforged.neoforge.fluids.FluidStack(
                                net.impactvector.mobvats.gamecontent.fluid.ProductFluids.CULTURE_SLURRY.get(), 1000))) {
                    vat.medium().setFluid(fluid);
                    h.assertTrue(
                            descriptor.equals(VatRenderDescriptor.from(vat, head)),
                            "Empty/water/culture medium changed specimen descriptor");
                }
                var saved = head.saveWithFullMetadata(h.getLevel().registryAccess());
                h.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(pos, Content.state(VatPartType.Head));
                ((VatHeadEntity) h.getBlockEntity(pos))
                        .loadWithComponents(saved, h.getLevel().registryAccess());
            }
        });
        h.runAtTickTime(75, () -> {
            for (var pos : positions) {
                var head = (VatHeadEntity) h.getBlockEntity(pos);
                var vat = head.getMultiblockController().orElseThrow();
                h.assertTrue(
                        vat.isAssembled() && ItemEssence.COW.equals(head.getIdentity()) && head.geometry() != null,
                        "Head replacement/restoration failed to reassemble cow Vat");
                h.assertTrue(
                        VatRenderDescriptor.from(vat, head)
                                .occupiedBounds()
                                .contains(VatRenderDescriptor.from(vat, head).specimenPosition()),
                        "Restored descriptor lost specimen origin");
            }
            h.succeed();
        });
    }

    private static VatHeadEntity detached(BlockPos pos) {
        return new VatHeadEntity(pos, Content.state(VatPartType.Head));
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void twoCowHeadsRestoreInEitherTransportOrder(GameTestHelper h) {
        var lookup = h.getLevel().registryAccess();
        for (boolean presentationFirst : new boolean[] {false, true}) {
            var source = detached(new BlockPos(1, 2, 1));
            source.encode(ItemEssence.COW);
            source.setPresentation(new VatPresentation(List.of(), List.of(), true));
            var network = source.syncDataTo(new CompoundTag(), lookup, SyncReason.NetworkUpdate);
            var persisted = source.syncDataTo(new CompoundTag(), lookup, SyncReason.FullSync);
            h.assertTrue(!persisted.contains("presentation"), "Presentation leaked into saved Head state");
            var first = detached(new BlockPos(1, 2, 1));
            var second = detached(new BlockPos(8, 2, 1));
            for (var target : List.of(first, second)) {
                if (presentationFirst) {
                    var early = new CompoundTag();
                    early.put("presentation", source.presentation().save());
                    target.syncDataFrom(early, lookup, SyncReason.NetworkUpdate);
                }
                target.syncDataFrom(persisted, lookup, SyncReason.FullSync);
                target.syncDataFrom(network, lookup, SyncReason.NetworkUpdate);
                target.syncDataFrom(network.copy(), lookup, SyncReason.NetworkUpdate);
                h.assertTrue(
                        ItemEssence.COW.equals(target.getIdentity())
                                && source.geometry().equals(target.geometry())
                                && source.specimenId().equals(target.specimenId())
                                && source.specimenState().equals(target.specimenState())
                                && target.presentation().equals(source.presentation()),
                        "Cow transport order/repetition lost render dependencies");
            }
            h.assertTrue(first != second, "Distinct Heads reused one instance");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void specimenAndPresentationInvalidateIndependentRevisions(GameTestHelper h) {
        var head = detached(BlockPos.ZERO);
        head.encode(ItemEssence.COW);
        long render = head.getRenderRevision();
        head.setPresentation(new VatPresentation(List.of(), List.of(), true));
        long presentation = head.presentationRevision();
        h.assertTrue(head.getRenderRevision() == render, "Presentation invalidated specimen geometry");
        head.setPresentation(head.presentation());
        h.assertTrue(head.presentationRevision() == presentation, "Identical presentation changed revision");
        head.setLifeStage(LifeStage.JUVENILE);
        h.assertTrue(head.getRenderRevision() > render, "Life-stage change retained stale specimen");
        render = head.getRenderRevision();
        var state = new CompoundTag();
        state.putBoolean("Baby", true);
        head.updateSpecimenState(state);
        h.assertTrue(head.getRenderRevision() > render, "Captured state failed to invalidate specimen");
        render = head.getRenderRevision();
        head.updateSpecimenState(state.copy());
        h.assertTrue(head.getRenderRevision() == render, "Identical state invalidated specimen");
        var replacement = detached(BlockPos.ZERO);
        replacement.encode(ItemEssence.COW);
        h.assertTrue(
                !replacement.specimenId().equals(head.specimenId()), "Replacement retained previous specimen UUID");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void descriptorsSurviveSyncAndCullingWithoutOperationalPresentation(GameTestHelper h) {
        VatGameTests.build(h, 5);
        var head = (VatHeadEntity) h.getBlockEntity(new BlockPos(3, 3, 3));
        head.encode(ItemEssence.COW);
        h.runAtTickTime(35, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(vat.isAssembled(), "Encoded Vat failed assembly");
            head.setPresentation(VatPresentation.EMPTY);
            var before = VatRenderDescriptor.from(vat, head);
            var saved = head.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            head.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            var after = VatRenderDescriptor.from(vat, head);
            h.assertTrue(
                    before.equals(after) && !head.presentation().operational(),
                    "Non-operational topology or repeated sync lost descriptor");
            var camera = before.vatBounds().getCenter();
            for (var position : List.of(camera, camera.add(1000, 0, 0), camera)) {
                var detail = VatRenderDetail.select(
                        VatRenderDetail.distanceSquared(before.vatBounds(), position), 32, 64, 96);
                h.assertTrue(
                        detail == (position.equals(camera) ? VatRenderDetail.FULL : VatRenderDetail.CULLED),
                        "Distance culling/resumption failed");
                h.assertTrue(
                        after.equals(VatRenderDescriptor.from(vat, head)), "Distance selection mutated descriptor");
            }
            long revision = head.getRenderRevision();
            head.onPostMachineBroken();
            h.assertTrue(head.getRenderRevision() > revision, "Broken callback failed cache invalidation");
            revision = head.getRenderRevision();
            head.onPostMachineAssembled(vat);
            h.assertTrue(head.getRenderRevision() > revision, "Restoration callback failed cache invalidation");
            h.succeed();
        });
    }
}
