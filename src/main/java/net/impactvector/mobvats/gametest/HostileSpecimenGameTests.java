package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** Actual lightning entity ticks, formed shells, portable items, and shared production transactions. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class HostileSpecimenGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            H = O.offset(1, 2, 2),
            SECOND = O.offset(4, 2, 2),
            PASSIVE = O.offset(7, 2, 2),
            BLENDER = O.offset(1, 1, 0),
            TOOL = O.offset(1, 1, 4),
            INPUT = O.offset(0, 2, 2);

    private static ResourceLocation id(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static VatHeadEntity head(GameTestHelper h, BlockPos p) {
        return (VatHeadEntity) h.getBlockEntity(p);
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return head(h, H).getMultiblockController().orElseThrow();
    }

    private static BlockState part(VatTier tier, VatPartType type) {
        var state = Content.state(type);
        return state.hasProperty(CladdingMaterial.PROPERTY)
                ? state.setValue(CladdingMaterial.PROPERTY, tier.cladding(false))
                : state;
    }

    private static void specimen(GameTestHelper h, VatTier tier, BlockPos p, String species) {
        h.setBlock(p, Content.state(VatPartType.Head));
        var head = head(h, p);
        head.encode(id(species));
        for (var cell : head.geometry().occupiedCells(h.absolutePos(p)))
            if (!cell.equals(h.absolutePos(p))) h.getLevel().setBlockAndUpdate(cell, Content.state(VatPartType.Flesh));
        h.setBlock(p.above(), part(tier, VatPartType.Harness));
        h.setBlock(p.below(2), part(tier, VatPartType.Sump));
    }

    private static void rig(GameTestHelper h, VatTier tier, boolean mixed) {
        int width = mixed ? 9 : 5;
        for (int x = 0; x < width; x++)
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < 5; z++)
                    h.setBlock(
                            O.offset(x, y, z),
                            x == 0 || x == width - 1 || y == 0 || y == 3 || z == 0 || z == 4
                                    ? Content.block(tier, VatPartType.Casing).defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
        h.setBlock(O.offset(2, 1, 0), Content.block(tier, VatPartType.Controller));
        specimen(h, tier, H, "zombie");
        if (mixed) {
            specimen(h, tier, SECOND, "skeleton");
            specimen(h, tier, PASSIVE, "cow");
        }
    }

    private static BlockPos rod(GameTestHelper h, BlockPos p) {
        var rod = p.above(2);
        h.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState());
        return rod;
    }

    private static void strike(GameTestHelper h, BlockPos rod, boolean channeling) {
        var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel());
        var p = h.absolutePos(rod);
        bolt.moveTo(p.getX() + .5, p.getY() + 1, p.getZ() + .5);
        if (channeling) bolt.setCause(h.makeMockServerPlayerInLevel());
        h.getLevel().addFreshEntity(bolt);
        bolt.tick();
        bolt.discard();
    }

    private static void formed(GameTestHelper h) {
        h.assertTrue(
                vat(h).isAssembled(),
                "Vat failed: "
                        + vat(h).getLastError().map(e -> e.getChatMessage().getString()));
    }

    private static void ticks(GameTestHelper h, int count) {
        for (int i = 0; i < count; i++) vat(h).processEngine().tick();
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 1000)
    public static void activatedHeadSurvivesActualChunkUnloadReload(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var r = rod(h, H);
        var remote = new BlockPos(22000008, 80, 22000008);
        var chunkPos = new net.minecraft.world.level.ChunkPos(remote);
        var originalChunk = new net.minecraft.world.level.chunk.LevelChunk[1];
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            var level = h.getLevel();
            level.setChunkForced(chunkPos.x, chunkPos.z, true);
            level.setBlockAndUpdate(remote, Content.state(VatPartType.Head));
            ((VatHeadEntity) level.getBlockEntity(remote))
                    .loadWithComponents(
                            head(h, H).saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            originalChunk[0] = level.getChunkAt(remote);
            level.setChunkForced(chunkPos.x, chunkPos.z, false);
        });
        h.startSequence()
                .thenIdle(50)
                .thenWaitUntil(() -> {
                    try {
                        h.assertTrue(
                                h.getLevel().getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) == null
                                        && !ServerChunkEviction.loaded(originalChunk[0])
                                        && ServerChunkEviction.absent(h.getLevel(), chunkPos),
                                "Waiting for native chunk save and complete unload");
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                })
                .thenExecute(() -> {
                    var level = h.getLevel();
                    h.assertTrue(level.getChunkAt(remote) != originalChunk[0], "Chunk was not reloaded from storage");
                    h.assertTrue(
                            level.getBlockEntity(remote) instanceof VatHeadEntity head && head.lightningActivated(),
                            "Actual chunk save/unload/reload lost activation");
                    level.removeBlock(remote, false);
                })
                .thenSucceed();
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 120)
    public static void activatedHeadMovesBetweenEligibleVatsThroughItemPlacement(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var other = O.offset(7, 0, 0);
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < 5; z++)
                    h.setBlock(
                            other.offset(x, y, z),
                            x == 0 || x == 4 || y == 0 || y == 3 || z == 0 || z == 4
                                    ? Content.block(VatTier.T2, VatPartType.Casing)
                                            .defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
        h.setBlock(other.offset(2, 1, 0), Content.block(VatTier.T2, VatPartType.Controller));
        var target = other.offset(1, 2, 2);
        specimen(h, VatTier.T2, target, "zombie");
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            h.assertTrue(head(h, target).isMachineAssembled(), "Destination Vat unformed");
            strike(h, r, false);
            var original = head(h, H);
            var item = Block.getDrops(original.getBlockState(), h.getLevel(), original.getBlockPos(), original)
                    .getFirst();
            h.setBlock(H, Blocks.AIR);
            h.setBlock(target, Blocks.AIR);
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
            var support = h.absolutePos(target.below());
            item.useOn(new net.minecraft.world.item.context.UseOnContext(
                    player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(support), Direction.UP, support, false)));
            h.assertTrue(head(h, target).lightningActivated(), "Item placement lost activation");
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(
                    head(h, target).isMachineAssembled() && head(h, target).hidesSpecimenSubstrate(),
                    "Moved Head did not activate in destination Vat");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void sneakEssenceReencodingResetsActivationWithoutOverwritingOnOrdinaryUse(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var r = rod(h, H);
        var outside = O.offset(7, 1, 1);
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            var original = head(h, H);
            var saved = original.saveWithFullMetadata(h.getLevel().registryAccess());
            h.setBlock(outside, Content.state(VatPartType.Head));
            var target = head(h, outside);
            target.loadWithComponents(saved, h.getLevel().registryAccess());
            var item = new ItemStack(Content.ESSENCE.get(), 2);
            item.set(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(
                            new net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen(
                                            id("zombie"), target.geometry(), new CompoundTag())
                                    .save()));
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
            var p = h.absolutePos(outside);
            var context = new net.minecraft.world.item.context.UseOnContext(
                    player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(p), Direction.UP, p, false));
            h.assertTrue(
                    item.useOn(context) == net.minecraft.world.InteractionResult.PASS
                            && target.lightningActivated()
                            && item.getCount() == 2,
                    "Ordinary Essence use overwrote an encoding");
            player.setShiftKeyDown(true);
            h.assertTrue(
                    item.useOn(context).consumesAction()
                            && !target.lightningActivated()
                            && item.getCount() == 1
                            && target.getIdentity().equals(id("zombie")),
                    "Sneak Essence did not reset encoding and consume exactly one Essence");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void dormantT2FormsWithVisibleFleshAndStatus(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        h.runAtTickTime(40, () -> {
            formed(h);
            h.assertTrue(
                    !head(h, H).activationReady()
                            && !head(h, H).hidesSpecimenSubstrate()
                            && !((AbstractVatEntity) h.getBlockEntity(H.below())).hidesSpecimenSubstrate(),
                    "Dormant substrate hidden");
            h.assertTrue(
                    vat(h).specimenSnapshots().getFirst().status().equals("Awaiting Lightning"),
                    "Dormant status missing");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void dormantT3Forms(GameTestHelper h) {
        rig(h, VatTier.T3, false);
        h.runAtTickTime(40, () -> {
            formed(h);
            h.assertTrue(!head(h, H).activationReady(), "T3 bypassed lightning");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void mixedPassiveProductionContinuesWithDormantAndChargedHostiles(GameTestHelper h) {
        rig(h, VatTier.T2, true);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            ticks(h, 2500);
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.MILK) > 0,
                    "Dormant hostile stopped unrelated passive milk production");
            h.assertTrue(
                    head(h, H).activationReady()
                            && !head(h, SECOND).activationReady()
                            && head(h, PASSIVE).activationReady()
                            && vat(h).isAssembled(),
                    "Mixed specimen state was not independent");
            var skeleton = vat(h).processEngine()
                    .presentationState(h.absolutePos(SECOND), head(h, SECOND).specimenId());
            h.assertTrue(
                    skeleton == null || skeleton.stage() == LifeStage.ADULT,
                    "Dormant skeleton entered production lifecycle");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void hostileT1AndLightningRejected(GameTestHelper h) {
        rig(h, VatTier.T1, false);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            strike(h, r, false);
            h.assertTrue(!vat(h).isAssembled() && !head(h, H).lightningActivated(), "T1 hostile accepted");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void incompleteFleshAndLightningRejected(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        h.setBlock(H.below(), Blocks.AIR);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            strike(h, r, false);
            h.assertTrue(!vat(h).isAssembled() && !head(h, H).lightningActivated(), "Incomplete geometry activated");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void wideHostileUsesCompleteGeometryAndOwnedCornerHarnesses(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        h.setBlock(H, Blocks.AIR);
        h.setBlock(H.below(), Blocks.AIR);
        h.setBlock(H.above(), Content.block(VatTier.T2, VatPartType.Casing));
        h.setBlock(H.below(2), Content.block(VatTier.T2, VatPartType.Casing));
        var p = H.east();
        h.setBlock(p, Content.state(VatPartType.Head));
        var head = head(h, p);
        head.encode(id("spider"));
        for (var cell : head.geometry().occupiedCells(h.absolutePos(p)))
            if (!cell.equals(head.getBlockPos()))
                h.getLevel().setBlockAndUpdate(cell, Content.state(VatPartType.Flesh));
        var corners = head.geometry().harnessCorners(p, O.getY() + 3);
        for (var corner : corners) h.setBlock(corner, part(VatTier.T2, VatPartType.Harness));
        h.setBlock(p.below(2), part(VatTier.T2, VatPartType.Sump));
        var r = corners.stream().min(VatTopologyCompiler.POSITION).orElseThrow().above();
        h.setBlock(r, Blocks.LIGHTNING_ROD);
        h.runAtTickTime(40, () -> {
            var vat = head.getMultiblockController().orElseThrow();
            h.assertTrue(
                    vat.isAssembled()
                            && corners.size() == 4
                            && vat.getAssemblies().getFirst().harnesses().size() == 4,
                    "Wide hostile lost required geometry or corner Harnesses: "
                            + vat.getLastError().map(e -> e.getChatMessage().getString()));
            strike(h, r, false);
            h.assertTrue(
                    head.lightningActivated() && head.hidesSpecimenSubstrate(),
                    "Owned corner rod did not activate wide hostile");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void misplacedHarnessRejected(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        h.setBlock(H.above(), Content.block(VatTier.T2, VatPartType.Casing));
        h.setBlock(H.above().east(), part(VatTier.T2, VatPartType.Harness));
        var r = rod(h, H.east());
        h.runAtTickTime(40, () -> {
            strike(h, r, false);
            h.assertTrue(!vat(h).isAssembled() && !head(h, H).lightningActivated(), "Wrong Harness accepted");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void independentLightningAndChannelingTransition(GameTestHelper h) {
        rig(h, VatTier.T2, true);
        var first = rod(h, H);
        var second = rod(h, SECOND);
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, first, false);
            h.assertTrue(
                    head(h, H).lightningActivated()
                            && !head(h, SECOND).lightningActivated()
                            && head(h, PASSIVE).activationReady(),
                    "Strike affected another specimen");
            h.assertTrue(
                    head(h, H).hidesSpecimenSubstrate()
                            && ((AbstractVatEntity) h.getBlockEntity(H.below())).hidesSpecimenSubstrate()
                            && !head(h, SECOND).hidesSpecimenSubstrate(),
                    "Independent rendering transition failed");
            long revision = head(h, H).getRenderRevision();
            strike(h, first, false);
            h.assertTrue(head(h, H).getRenderRevision() == revision, "Repeat strike accumulated activation");
            strike(h, second, true);
            h.assertTrue(head(h, SECOND).lightningActivated(), "Channeling-origin bolt failed");
            formed(h);
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void redstonePoweredStateAndUnrelatedLightningDoNotCharge(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            h.setBlock(r, h.getBlockState(r).setValue(LightningRodBlock.POWERED, true));
            h.setBlock(r.east(), Blocks.REDSTONE_BLOCK);
            strike(h, r.east(2), false);
            var other = r.east(3);
            h.setBlock(other, Blocks.LIGHTNING_ROD);
            strike(h, other, false);
            h.assertTrue(!head(h, H).lightningActivated(), "Power or another strike charged Head");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void sidewaysRodDoesNotCharge(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var r = rod(h, H);
        h.setBlock(r, h.getBlockState(r).setValue(LightningRodBlock.FACING, Direction.EAST));
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            h.assertTrue(!head(h, H).lightningActivated(), "Sideways rod charged");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void missingRoofAssociationDoesNotCharge(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var r = rod(h, H.east());
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            h.assertTrue(!head(h, H).lightningActivated(), "Roof casing conducted remote charging");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void brokenShellCannotUseStaleAssociation(GameTestHelper h) {
        rig(h, VatTier.T2, false);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            h.setBlock(O, Blocks.AIR);
            strike(h, r, false);
            h.assertTrue(!head(h, H).lightningActivated(), "Stale formed state bypassed shell validation");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 100)
    public static void headSaveNetworkPickupRelocationAndReset(GameTestHelper h) {
        rig(h, VatTier.T2, true);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            var head = head(h, H);
            var saved = head.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored = new VatHeadEntity(head.getBlockPos(), head.getBlockState());
            restored.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(restored.lightningActivated(), "Disk serialization lost activation");
            var packet = head.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            var remote = new VatHeadEntity(head.getBlockPos(), head.getBlockState());
            remote.syncDataFrom(packet, h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            h.assertTrue(
                    remote.activationReady() && remote.presentation().equals(head.presentation()),
                    "Network state lost activation/presentation");
            var drops = Block.getDrops(head.getBlockState(), h.getLevel(), head.getBlockPos(), head);
            h.assertTrue(drops.size() == 1, "Missing portable Head");
            h.setBlock(H, Blocks.AIR);
            h.setBlock(H, Content.state(VatPartType.Head));
            head(h, H).encode(id("zombie"));
            h.assertTrue(!head(h, H).lightningActivated(), "Controller retained activation after Head replacement");
            h.setBlock(SECOND, Blocks.AIR);
            h.setBlock(SECOND, Content.state(VatPartType.Head));
            var placed = head(h, SECOND);
            var item = drops.getFirst();
            var data = item.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                    .copyTag();
            placed.loadWithComponents(data, h.getLevel().registryAccess());
            h.assertTrue(placed.lightningActivated(), "Dropped Head lost activation on relocation");
        });
        h.runAtTickTime(80, () -> {
            formed(h);
            h.assertTrue(
                    head(h, SECOND).hidesSpecimenSubstrate() && !head(h, H).lightningActivated(),
                    "Relocation changed another Head");
            var charged =
                    head(h, SECOND).syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            head(h, SECOND).encode(id("zombie"));
            h.assertTrue(!head(h, SECOND).lightningActivated(), "Same-species encoding retained charge");
            head(h, SECOND).syncDataFrom(charged, h.getLevel().registryAccess(), SyncReason.FullSync);
            head(h, SECOND).clearSpecimen();
            h.assertTrue(
                    head(h, SECOND).getIdentity() == null && !head(h, SECOND).lightningActivated(),
                    "Clear retained charge");
            h.succeed();
        });
    }

    private static void production(GameTestHelper h, boolean blender, boolean passive) {
        rig(h, VatTier.T2, false);
        if (passive) head(h, H).encode(id("cow"));
        h.setBlock(O.offset(0, 1, 2), part(VatTier.T2, VatPartType.FeedPort));
        h.setBlock(INPUT, part(VatTier.T2, VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(INPUT))
                .staging()
                .setStackInSlot(0, new ItemStack(passive ? Items.WHEAT : Items.ROTTEN_FLESH, 8));
        h.setBlock(blender ? BLENDER : TOOL, part(VatTier.T2, blender ? VatPartType.Blender : VatPartType.ToolPort));
        if (!blender) ((VatToolPortEntity) h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.NETHERITE_SWORD));
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void dormantBlenderProducesNothingAndActivatedBlenderMakesStrangeMeat(GameTestHelper h) {
        production(h, true, false);
        h.setBlock(BLENDER.above(), part(VatTier.T2, VatPartType.FluidPort));
        ((VatFluidPortEntity) h.getBlockEntity(BLENDER.above()))
                .setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            ticks(h, 1200);
            var input = (VatItemPortEntity) h.getBlockEntity(INPUT);
            var blender = (VatBlenderEntity) h.getBlockEntity(BLENDER);
            h.assertTrue(
                    input.staging().getStackInSlot(0).getCount() == 8
                            && vat(h).metrics().total(MachineMetrics.BLENDER_HARVESTS) == 0,
                    "Dormant Blender consumed or produced");
            strike(h, r, false);
            ticks(h, 20);
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.BLENDER_HARVESTS) == 1
                            && blender.service()
                                            .tank(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 1))
                                            .getFluidAmount()
                                    > 0
                            && blender.service()
                                    .tank(new FluidStack(ProductFluids.MEAT.get(), 1))
                                    .isEmpty(),
                    "Hostile Blender did not output only Strange Meat");
            var output = ((VatFluidPortEntity) h.getBlockEntity(BLENDER.above())).externalFluids();
            int before = blender.service()
                    .tank(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 1))
                    .getFluidAmount();
            var simulated =
                    output.drain(new FluidStack(ProductFluids.STRANGE_MEAT.get(), before), FluidAction.SIMULATE);
            h.assertTrue(
                    simulated.is(ProductFluids.STRANGE_MEAT.get())
                            && simulated.getAmount() == before
                            && blender.service().tank(simulated).getFluidAmount() == before,
                    "Strange Meat fluid output simulation changed contents or identity");
            var drained = output.drain(simulated, FluidAction.EXECUTE);
            h.assertTrue(
                    FluidStack.isSameFluidSameComponents(simulated, drained)
                            && drained.getAmount() == before
                            && blender.service().tank(simulated).isEmpty(),
                    "Strange Meat did not reach normal fluid output");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void passiveBlenderStillMakesMeatWithoutLightning(GameTestHelper h) {
        production(h, true, true);
        h.runAtTickTime(40, () -> {
            formed(h);
            ticks(h, 20);
            var b = (VatBlenderEntity) h.getBlockEntity(BLENDER);
            h.assertTrue(
                    b.service()
                                            .tank(new FluidStack(ProductFluids.MEAT.get(), 1))
                                            .getFluidAmount()
                                    > 0
                            && b.service()
                                    .tank(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 1))
                                    .isEmpty(),
                    "Passive Blender output changed");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 160)
    public static void dormantSwordThenActivatedLootAndXp(GameTestHelper h) {
        production(h, false, false);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            ticks(h, 200);
            var tool = (VatToolPortEntity) h.getBlockEntity(TOOL);
            h.assertTrue(
                    tool.heldItem().getDamageValue() == 0
                            && tool.results().isEmpty()
                            && vat(h).metrics().total(MachineMetrics.XP) == 0,
                    "Dormant Sword acted");
            strike(h, r, false);
        });
        h.runAtTickTime(120, () -> {
            var tool = (VatToolPortEntity) h.getBlockEntity(TOOL);
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.HARVESTS) > 0
                            && vat(h).metrics().total(MachineMetrics.XP) > 0
                            && tool.heldItem().getDamageValue() > 0,
                    "Activated Sword failed loot/XP/durability: "
                            + vat(h).processEngine().status(h.absolutePos(H)));
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void unsupportedHostileBucketDoesNothing(GameTestHelper h) {
        production(h, false, false);
        ((VatToolPortEntity) h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.BUCKET));
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            ticks(h, 2000);
            var tool = (VatToolPortEntity) h.getBlockEntity(TOOL);
            h.assertTrue(
                    tool.heldItem().is(Items.BUCKET)
                            && tool.results().isEmpty()
                            && vat(h).metrics().total(MachineMetrics.MILK) == 0,
                    "Hostile produced passive output");
            h.succeed();
        });
    }

    @GameTest(batch = "hostileSpecimens", template = "empty", timeoutTicks = 80)
    public static void hostileBlenderBackpressureRetainsFeed(GameTestHelper h) {
        production(h, true, false);
        var r = rod(h, H);
        h.runAtTickTime(40, () -> {
            formed(h);
            strike(h, r, false);
            var b = (VatBlenderEntity) h.getBlockEntity(BLENDER);
            var tank = b.service().tank(new FluidStack(ProductFluids.STRANGE_MEAT.get(), 1));
            tank.fill(new FluidStack(ProductFluids.STRANGE_MEAT.get(), tank.getCapacity()), FluidAction.EXECUTE);
            ticks(h, 100);
            h.assertTrue(
                    ((VatItemPortEntity) h.getBlockEntity(INPUT))
                                            .staging()
                                            .getStackInSlot(0)
                                            .getCount()
                                    == 8
                            && vat(h).metrics().total(MachineMetrics.BLENDER_HARVESTS) == 0,
                    "Blocked transaction consumed feed");
            tank.drain(tank.getCapacity(), FluidAction.EXECUTE);
            ticks(h, 20);
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.BLENDER_HARVESTS) == 1,
                    "Unblocked transaction did not resume");
            h.succeed();
        });
    }
}
