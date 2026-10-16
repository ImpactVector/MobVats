package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class CowProcessGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos HEAD = ORIGIN.offset(2, 2, 2);
    private static final BlockPos TOOL = ORIGIN.offset(2, 1, 4);
    private static final BlockPos INPUT = ORIGIN.offset(1, 1, 4);
    private static final BlockPos OUTPUT_A = ORIGIN.offset(3, 1, 4);
    private static final BlockPos OUTPUT_B = ORIGIN.offset(2, 2, 4);
    private static final BlockPos FEED = ORIGIN.offset(2, 1, 0);
    private static final BlockPos FLUID = ORIGIN.offset(0, 1, 2);
    private static final BlockPos BOTTOM_A = ORIGIN.offset(1, 0, 2);
    private static final BlockPos BOTTOM_B = ORIGIN.offset(3, 0, 2);

    private static void redstoneController(GameTestHelper h, boolean indirect) {
        rig(h, true);
        var power = ORIGIN.offset(1, 1, -1);
        h.runAtTickTime(30, () -> {
            seed(h, "GROWING", 123, 0, 0, 0, ItemStack.EMPTY);
            tool(h).setInteractionStart(h.getLevel().getGameTime());
            MixerRedstoneGameTests.power(h, power, indirect);
            var c = vat(h);
            h.assertTrue(c.isMachineActive() && !c.processingEnabled(), "Controller power not derived");
            item(h, INPUT).externalItems().insertItem(0, new ItemStack(Items.IRON_SWORD), false);
            h.assertTrue(!item(h, INPUT).staging().getStackInSlot(0).isEmpty(), "Controller power blocked Item Port");
            var saved = save(h);
            c.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
        });
        h.runAtTickTime(50, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.processEngine().state(h.absolutePos(HEAD)).growth().elapsed == 123,
                    "Powered/reloaded Vat advanced biological progress");
            h.assertTrue(
                    !tool(h).available() && h.getLevel().getGameTime() - tool(h).interactionStart() <= 1,
                    "Absolute tool cooldown expired during controller inhibition");
            MixerRedstoneGameTests.unpower(h, power);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(
                    vat(h).isMachineActive()
                            && vat(h).processEngine().state(h.absolutePos(HEAD)).growth().elapsed > 123
                            && vat(h).processEngine().state(h.absolutePos(HEAD)).growth().elapsed <= 133,
                    "Biology failed to resume or replayed inhibited ticks");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void redstoneControllerDirectPreservesProgressAndTransport(GameTestHelper h) {
        redstoneController(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void redstoneControllerIndirectReloadAndCooldown(GameTestHelper h) {
        redstoneController(h, true);
    }

    private static void redstoneActor(GameTestHelper h, boolean blender, boolean indirect) {
        rig(h, true);
        if (blender) h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        else tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        var p = blender ? BLENDER : TOOL;
        var power = blender ? p.east() : p.south();
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            seed(h, "READY", 1200, 0, 0, 0, ItemStack.EMPTY);
            var routes = c.getTopology().actors();
            var before = tool(h).heldItem();
            MixerRedstoneGameTests.power(h, power, indirect);
            for (int i = 0; i < 100; i++) c.processEngine().tick();
            h.assertTrue(
                    c.metrics().total(MachineMetrics.BLENDER_HARVESTS) == 0
                            && tool(h).heldItem().getDamageValue() == before.getDamageValue()
                            && c.getTopology().actors().equals(routes)
                            && c.processEngine().state(h.absolutePos(HEAD)).stage() == LifeStage.ADULT,
                    "Inhibited actor performed work, lost assignment, or changed held resources");
            h.assertTrue(
                    c.processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.MILK).elapsed > 0,
                    "Individual actor power stopped independent biological operation");
            MixerRedstoneGameTests.unpower(h, power);
            for (int i = 0; i < 10; i++) c.processEngine().tick();
            h.assertTrue(
                    blender
                            ? c.metrics().total(MachineMetrics.BLENDER_HARVESTS) == 1
                            : tool(h).heldItem().getDamageValue() > before.getDamageValue(),
                    "Actor failed to resume");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneToolDirectPauseAndIndependentBiology(GameTestHelper h) {
        redstoneActor(h, false, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneToolIndirectPauseAndResume(GameTestHelper h) {
        redstoneActor(h, false, true);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneBlenderDirectPauseAndResume(GameTestHelper h) {
        redstoneActor(h, true, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneBlenderIndirectPauseAndResume(GameTestHelper h) {
        redstoneActor(h, true, true);
    }

    private static void redstoneToolCooldown(GameTestHelper h, boolean indirect) {
        rig(h, true);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        var p = TOOL.south();
        h.runAtTickTime(30, () -> {
            seed(h, "GROWING", 123, 0, 0, 0, ItemStack.EMPTY);
            tool(h).setInteractionStart(h.getLevel().getGameTime());
            var state = vat(h).processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.HARVEST);
            state.data.putLong(
                    "interactionCooldown:mobvats:weapon_harvest", h.getLevel().getGameTime() - 5);
            state.data.putLong("interactionAt", h.getLevel().getGameTime() - 5);
            MixerRedstoneGameTests.power(h, p, indirect);
        });
        h.runAtTickTime(40, () -> {
            var saved = save(h);
            vat(h).syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
        });
        h.runAtTickTime(50, () -> {
            var state = vat(h).processEngine().state(h.absolutePos(HEAD));
            h.assertTrue(
                    !tool(h).available() && state.growth().elapsed > 123,
                    "Tool inhibition expired its cooldown or stopped unrelated biology");
            var process = state.process(PassiveProcessCompiler.HARVEST);
            h.assertTrue(
                    h.getLevel().getGameTime() - process.data.getLong("interactionAt") <= 6
                            && h.getLevel().getGameTime()
                                            - process.data.getLong("interactionCooldown:mobvats:weapon_harvest")
                                    <= 6,
                    "Persisted process cooldowns advanced during tool inhibition/reload");
            MixerRedstoneGameTests.unpower(h, p);
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(tool(h).available(), "Tool cooldown did not resume");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void redstoneToolDirectCooldownReload(GameTestHelper h) {
        redstoneToolCooldown(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void redstoneToolIndirectCooldownReload(GameTestHelper h) {
        redstoneToolCooldown(h, true);
    }

    private static void redstoneFeed(GameTestHelper h, boolean indirect) {
        rig(h, true);
        h.setBlock(FEED.south(), Blocks.AIR);
        h.setBlock(FEED, Content.state(VatPartType.FeedPort));
        h.setBlock(FEED.east(), Content.state(VatPartType.ItemPort));
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            seed(h, "READY", 1200, 0, 0, 0, ItemStack.EMPTY);
            item(h, FEED.east()).staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
            MixerRedstoneGameTests.power(h, FEED.north(), indirect);
            for (int i = 0; i < 20; i++) c.processEngine().tick();
            h.assertTrue(
                    tool(h).heldItem().getDamageValue() == 0
                            && item(h, FEED.east()).staging().getStackInSlot(0).getCount() == 1,
                    "Inhibited Feed Port fed replacement operation");
            MixerRedstoneGameTests.unpower(h, FEED.north());
            for (int i = 0; i < 10; i++) c.processEngine().tick();
            h.assertTrue(tool(h).heldItem().getDamageValue() > 0, "Feed Port failed to resume");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneFeedDirectSuspension(GameTestHelper h) {
        redstoneFeed(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneFeedIndirectSuspension(GameTestHelper h) {
        redstoneFeed(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redstoneItemInputCannotBeConsumedInternally(GameTestHelper h) {
        rig(h, true);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            seed(h, "READY", 1200, 0, 0, 0, ItemStack.EMPTY);
            MixerRedstoneGameTests.power(h, FEED.north(), true);
            h.assertTrue(
                    !((VatFeedPortEntity) h.getBlockEntity(FEED.south())).redstoneInhibited(),
                    "Fixture also inhibited Feed actor");
            for (int i = 0; i < 20; i++) c.processEngine().tick();
            h.assertTrue(
                    item(h, FEED).staging().getStackInSlot(0).getCount() == 1
                            && tool(h).heldItem().getDamageValue() == 0,
                    "Process consumed an inhibited Item Port's resources");
            MixerRedstoneGameTests.unpower(h, FEED.north());
            for (int i = 0; i < 10; i++) c.processEngine().tick();
            h.assertTrue(tool(h).heldItem().getDamageValue() > 0, "Input port did not resume internal supply");
            h.succeed();
        });
    }

    private static void genericOutput(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, Content.state(VatPartType.ItemPort));
        item(h, pos).setIoDirection(IoDirection.Output);
    }

    private static void rig(GameTestHelper h, boolean outputs) {
        VatGameTests.build(h, 5);
        for (var pos : List.of(INPUT, FEED)) h.setBlock(pos, Content.state(VatPartType.ItemPort));
        h.setBlock(TOOL, Content.state(VatPartType.ToolPort));
        h.setBlock(FLUID, Content.state(VatPartType.FluidPort));
        ((VatFluidPortEntity) h.getBlockEntity(FLUID)).setIoDirection(IoDirection.Output);
        if (outputs)
            for (var pos : List.of(OUTPUT_A, OUTPUT_B)) {
                h.setBlock(pos, Content.state(VatPartType.ItemPort));
                item(h, pos).setIoDirection(IoDirection.Output);
            }
        ((VatHeadEntity) h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        Slice11Fixtures.vatStorage(h, ORIGIN, 5, 5);
        Slice11Fixtures.feedFrom(h, FEED, FEED.south());
    }

    private static MultiblockVat vat(GameTestHelper h) {
        return ((VatHeadEntity) h.getBlockEntity(HEAD))
                .getMultiblockController()
                .orElseThrow();
    }

    private static VatItemPortEntity item(GameTestHelper h, BlockPos pos) {
        return (VatItemPortEntity) h.getBlockEntity(pos);
    }

    private static VatToolPortEntity tool(GameTestHelper h) {
        return (VatToolPortEntity) h.getBlockEntity(TOOL);
    }

    private static void connectBottomTool(GameTestHelper h) {
        for (var p : List.of(ORIGIN.offset(2, 0, 4), ORIGIN.offset(1, 0, 4), ORIGIN.offset(1, 0, 3)))
            h.setBlock(
                    p,
                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.state(
                                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM,
                                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.CASING)
                            .setValue(
                                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock.MODE,
                                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitMode.TAP));
        // The right bottom output is also connected explicitly when present.
        if (h.getLevel().getBlockEntity(h.absolutePos(BOTTOM_B)) instanceof VatItemPortEntity)
            for (var p : List.of(ORIGIN.offset(3, 0, 4), ORIGIN.offset(3, 0, 3)))
                h.setBlock(
                        p,
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.state(
                                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.ITEM,
                                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.CASING)
                                .setValue(
                                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock.MODE,
                                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitMode.TAP));
    }

    private static void noSpawnedProductionEntities(GameTestHelper h) {
        var area = new AABB(
                Vec3.atLowerCornerOf(h.absolutePos(ORIGIN)),
                Vec3.atLowerCornerOf(h.absolutePos(ORIGIN.offset(5, 4, 5))));
        h.assertTrue(
                h.getLevel().getEntities(EntityType.COW, area, e -> true).isEmpty(),
                "Production inserted a Cow into the level");
        h.assertTrue(
                h.getLevel()
                        .getEntities(EntityType.EXPERIENCE_ORB, area, e -> true)
                        .isEmpty(),
                "Production emitted XP orbs");
        h.assertTrue(
                h.getLevel().getEntities(EntityType.ITEM, area, e -> true).isEmpty(),
                "Production dropped unrouted items");
    }

    private static CompoundTag save(GameTestHelper h) {
        return vat(h).syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
    }

    private static void seed(
            GameTestHelper h, String cycle, int elapsed, int milkTicks, int milk, long xp, ItemStack pendingWeapon) {
        var data = save(h);
        vat(h).processFluids()
                .buffer(ProductBuffers.MILK)
                .setFluid(milk > 0 ? new FluidStack(NeoForgeMod.MILK.get(), milk) : FluidStack.EMPTY);
        var xpTank = vat(h).storage()
                .select(
                        h.absolutePos(ORIGIN.offset(2, 0, 2)),
                        net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.stack(Math.max(1, xp)));
        if (xpTank != null)
            xpTank.setFluid(
                    xp > 0 ? net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.stack(xp) : FluidStack.EMPTY);
        var tank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(CowBalance.FLUID_CAPACITY);

        data.getCompound("products")
                .getCompound("buffers")
                .getCompound("mobvats:milk")
                .put("tank", tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
        data.putLong("legacyXpMigration", 0);
        data.remove("processXp");
        var specimen = new SpecimenProcessState();
        specimen.stage(cycle.equals("GROWING") ? LifeStage.JUVENILE : LifeStage.ADULT);
        specimen.growth().phase = cycle.equals("GROWING") ? ProcessState.Phase.RUNNING : ProcessState.Phase.IDLE;
        specimen.growth().elapsed = cycle.equals("GROWING") ? elapsed : 0;
        var timer = specimen.process(PassiveProcessCompiler.MILK);
        timer.elapsed = milkTicks;
        timer.phase = milkTicks >= 1200 ? ProcessState.Phase.READY : ProcessState.Phase.RUNNING;
        if (!pendingWeapon.isEmpty()) {
            var result = twoDropLoot(h, pendingWeapon);
            var prepared = specimen.process(PassiveProcessCompiler.HARVEST);
            prepared.pending = new ProcessState.PreparedOutput(result.items(), result.xp());
            prepared.pendingTool = pendingWeapon.copy();
        }
        var states = new ListTag();
        var entry = new CompoundTag();
        entry.putLong("head", h.absolutePos(HEAD).asLong());
        entry.put("processes", specimen.save(h.getLevel().registryAccess()));
        states.add(entry);
        data.put("specimenProcesses", states);
        vat(h).syncDataFrom(data, h.getLevel().registryAccess(), SyncReason.FullSync);
        vat(h).processFluids().bind(vat(h).storage());
        if (cycle.equals("READY")
                && h.getBlockEntity(FEED) instanceof VatItemPortEntity feed
                && feed.staging().getStackInSlot(0).isEmpty())
            feed.staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
        else if (cycle.equals("READY") && !(h.getBlockEntity(FEED) instanceof VatItemPortEntity))
            vat(h).fluidHandler(IoDirection.Input)
                    .fill(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 250), FluidAction.EXECUTE);
    }

    private static CowHarvestLoot.Result twoDropLoot(GameTestHelper h, ItemStack sword) {
        for (long seed = 1; seed < 100; seed++) {
            var result = CowHarvestLoot.roll(h.getLevel(), Vec3.atCenterOf(h.absolutePos(HEAD)), sword, seed);
            if (result.items().stream().anyMatch(s -> s.is(Items.LEATHER))
                    && result.items().stream().anyMatch(s -> s.is(Items.BEEF))) return result;
        }
        throw new IllegalStateException("Vanilla cow loot did not produce leather and beef in 99 rolls");
    }

    private static void seedFixedDrops(GameTestHelper h, List<ItemStack> drops) {
        seed(h, "READY", 1200, 0, 0, 0, tool(h).heldItem());
        var state = vat(h).processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.HARVEST);
        state.pending = new ProcessState.PreparedOutput(drops, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void genericBottomOutputAndHopper(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        genericOutput(h, BOTTOM_A);
        connectBottomTool(h);
        h.setBlock(BOTTOM_A.below(), Blocks.HOPPER);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            h.assertTrue(
                    vat(h).isAssembled()
                            && vat(h).getTopology().reservedItemOutputs().contains(h.absolutePos(BOTTOM_A))
                            && h.absolutePos(BOTTOM_A).distManhattan(h.absolutePos(TOOL)) > 1,
                    "Bottom output still requires adjacency to harvest actor");
            seedFixedDrops(h, List.of(new ItemStack(Items.BEEF, 3), new ItemStack(Items.LEATHER)));
        });
        h.runAtTickTime(130, () -> {
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(BOTTOM_A.below());
            int beef = 0, leather = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); ++slot) {
                var stack = hopper.getItem(slot);
                if (stack.is(Items.BEEF)) beef += stack.getCount();
                if (stack.is(Items.LEATHER)) leather += stack.getCount();
            }
            h.assertTrue(
                    beef == 3
                            && leather == 1
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).isEmpty()
                            && tool(h).results().isEmpty(),
                    "Bottom hopper did not extract both buffered kill products without loss/duplication");
            h.assertTrue(
                    vat(h).getExperienceMb() == 40
                            && tool(h).heldItem().getDamageValue() == 1
                            && vat(h).metrics().total(MachineMetrics.ITEMS) == 4,
                    "Bottom drain commit/metrics incorrect");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void harvestToolLoadsWithoutAnyItemOutputs(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        item(h, INPUT).staging().setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> seed(h, "READY", 1200, 0, 0, 0, new ItemStack(Items.IRON_SWORD)));
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    vat(h).getTopology().itemOutputs().isEmpty()
                            && tool(h).heldItem().is(Items.IRON_SWORD)
                            && tool(h).heldItem().getDamageValue() == 1
                            && item(h, INPUT).staging().getStackInSlot(0).isEmpty()
                            && tool(h).results().stackCount() == 2
                            && vat(h).getExperienceMb() > 0,
                    "Harvest acquisition/commit still requires an adjacent or generic item output");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 95)
    public static void deterministicGenericOutputsAndReservations(GameTestHelper h) {
        rig(h, false);
        genericOutput(h, BOTTOM_B);
        genericOutput(h, BOTTOM_A);
        connectBottomTool(h);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            var topology = vat(h).getTopology();
            h.assertTrue(
                    topology.reservedItemOutputs()
                            .containsAll(List.of(h.absolutePos(BOTTOM_A), h.absolutePos(BOTTOM_B))),
                    "Compiler did not partition outputs in deterministic position order");
            seedFixedDrops(h, List.of(new ItemStack(Items.BEEF, 3), new ItemStack(Items.LEATHER)));
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(
                    item(h, BOTTOM_A).staging().getStackInSlot(0).is(Items.BEEF)
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).getCount() == 3
                            && item(h, BOTTOM_B).staging().getStackInSlot(0).is(Items.LEATHER)
                            && tool(h).results().isEmpty(),
                    "Buffered drops did not drain in stack order then sorted port order");
            h.assertTrue(tool(h).results().isEmpty(), "Routed local batch remained buffered");
            // Empty but physically valid Tool loading relationships keep their local output reservation.
            tool(h).setHeldItem(ItemStack.EMPTY);
            h.assertTrue(
                    vat(h).getTopology().reservedItemOutputs().contains(h.absolutePos(BOTTOM_A)),
                    "Empty tool incorrectly released its loading workflow outputs");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 190)
    public static void partialStackDrainConservesBuffer(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        genericOutput(h, BOTTOM_A);
        connectBottomTool(h);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        item(h, BOTTOM_A).staging().setStackInSlot(0, new ItemStack(Items.BEEF, 63));
        h.runAtTickTime(
                30, () -> seedFixedDrops(h, List.of(new ItemStack(Items.BEEF, 3), new ItemStack(Items.LEATHER))));
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    item(h, BOTTOM_A).staging().getStackInSlot(0).getCount() == 64
                            && tool(h).results().stackCount() == 2
                            && tool(h).results().itemCount() == 3
                            && vat(h).metrics().total(MachineMetrics.ITEMS) == 1,
                    "Partial capacity lost buffer remainder or inflated drain metric");
            var copy = tool(h).results().snapshot();
            copy.getFirst().setCount(0);
            h.assertTrue(tool(h).results().itemCount() == 3, "Observer snapshot mutated authoritative drops");
            item(h, BOTTOM_A).externalItems().extractItem(0, 64, false);
        });
        h.runAtTickTime(115, () -> {
            h.assertTrue(
                    item(h, BOTTOM_A).staging().getStackInSlot(0).is(Items.BEEF)
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).getCount() == 2
                            && tool(h).results().stackCount() == 1
                            && tool(h).results().itemCount() == 1,
                    "Remaining partial beef/leather was not preserved");
            item(h, BOTTOM_A).externalItems().extractItem(0, 64, false);
        });
        h.runAtTickTime(160, () -> {
            h.assertTrue(
                    tool(h).results().isEmpty()
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).is(Items.LEATHER)
                            && vat(h).metrics().total(MachineMetrics.ITEMS) == 4
                            && vat(h).metrics().total(MachineMetrics.HARVEST_ITEMS) == 4,
                    "Drained count does not equal committed drops");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void killBufferRejectsSecondDepositAndPreservesAssimilation(GameTestHelper h) {
        var a = new KillOutputBuffer();
        var b = new KillOutputBuffer();
        h.assertTrue(
                !a.store(java.util.Arrays.asList(new ItemStack(Items.BEEF), null)) && a.isEmpty(),
                "Invalid deposit partially committed");
        h.assertTrue(
                a.store(List.of(new ItemStack(Items.BEEF, 3), new ItemStack(Items.LEATHER)))
                        && !a.store(List.of(new ItemStack(Items.BEEF)))
                        && a.itemCount() == 4,
                "Backlog accepted or overwrote another kill");
        b.store(List.of(new ItemStack(Items.LEATHER, 2)));
        a.assimilate(b);
        b.clear();
        var restored = new KillOutputBuffer();
        restored.load(a.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(
                restored.itemCount() == 6 && restored.stackCount() == 3 && b.isEmpty(),
                "Assimilation/reload lost committed drops");
        h.succeed();
    }

    private static void fixtureField(MultiblockVat vat, String name, Object value) {
        try {
            var field = MultiblockVat.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(vat, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Headless controller component fixture failed", e);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void globalBackpressureSkipsSecondSpecimenEligibility(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            tool(h).results().store(List.of(new ItemStack(Items.BEEF, 3), new ItemStack(Items.LEATHER)));
            item(h, FEED).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(
                    vat(h).killOutputItemCount() == 4
                            && vat(h).getExperienceMb() == 0
                            && tool(h).heldItem().getDamageValue() == 0
                            && item(h, FEED).staging().getStackInSlot(0).getCount() == 2
                            && vat(h).processEngine()
                                            .state(h.absolutePos(HEAD))
                                            .process(PassiveProcessCompiler.HARVEST)
                                            .pending
                                    == null,
                    "Shared backpressure consumed feed or prepared/committed a new harvest");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 380)
    public static void bufferCultureDoesNotAccelerateAndSlurryStillReplacesWheat(GameTestHelper h) {
        slurryFeeding(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 380)
    public static void componentBearingMixerSlurryFeedsWithoutConsumingWheat(GameTestHelper h) {
        slurryFeeding(h, true);
    }

    private static void slurryFeeding(GameTestHelper h, boolean mixerProduct) {
        rig(h, false);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            seed(h, "IDLE", 0, 0, 0, 0, ItemStack.EMPTY);
            ((VatFluidPortEntity) h.getBlockEntity(FLUID)).setIoDirection(IoDirection.Input);
            var in = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FLUID), Direction.WEST);
            h.assertTrue(
                    in.fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 1000), FluidAction.EXECUTE) == 1000
                            && in.fill(feedingSample(mixerProduct), FluidAction.EXECUTE) == 250,
                    "Vat rejected slurries");
            item(h, FEED).staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
        });
        h.runAtTickTime(355, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.milkAmount() == 0
                            && c.cowState(h.absolutePos(HEAD)).replacement().equals("GROWING")
                            && c.cowState(h.absolutePos(HEAD)).replacementTicks() >= 100
                            && c.cowState(h.absolutePos(HEAD)).replacementTicks() < 200,
                    "Stored buffer culture changed dry recovery or juvenile milk pause");
            h.assertTrue(
                    c.feedingAmount() == 0
                            && c.metrics().total("feedingConsumed") == 250
                            && c.metrics().total(MachineMetrics.WHEAT) == 0
                            && item(h, FEED).staging().getStackInSlot(0).getCount() == 1,
                    "Slurry cycle also consumed wheat");
            h.assertTrue(
                    c.cultureAmount() == 1000
                            && c.metrics().total("cultureConsumed") == 0
                            && c.medium().isEmpty()
                            && c.feedSource().equals("feeding slurry"),
                    "Vat-level culture consumption or feed status wrong");
            h.assertTrue(
                    c.statusSnapshot().pages().get(1).contains("Culture frequency:")
                            && c.statusSnapshot().pages().get(1).contains("Feeding Slurry consumed"),
                    "Support observability absent");
            h.succeed();
        });
    }

    private static FluidStack feedingSample(boolean mixerProduct) {
        if (!mixerProduct) return new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 250);
        var d = net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe(
                "feeding_slurry");
        return d.result(
                        d.strategies().getFirst(),
                        d.fluidOutputs().getFirst(),
                        java.util.List.of(
                                java.util.List.of(
                                        new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000)),
                                java.util.List.of(new FluidStack(ProductFluids.WASTE.get(), 250))))
                .copyWithAmount(250);
    }

    @GameTest(template = "empty", timeoutTicks = 95)
    public static void dryFractionalGrowthPersistsWithoutCultureCredits(GameTestHelper h) {
        rig(h, false);
        h.runAtTickTime(30, () -> {
            seed(h, "GROWING", 0, 0, 0, 0, ItemStack.EMPTY);
            ((VatFluidPortEntity) h.getBlockEntity(FLUID)).setIoDirection(IoDirection.Input);
            h.getLevel()
                    .getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FLUID), Direction.WEST)
                    .fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 10), FluidAction.EXECUTE);
        });
        h.runAtTickTime(35, () -> {
            var saved = save(h);
            h.assertTrue(
                    !saved.contains("cultureCredit") && vat(h).cultureAmount() == 10, "Culture budget not paid/shared");
            vat(h).syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
        });
        final int[] milk = {0};
        h.runAtTickTime(55, () -> {
            milk[0] = vat(h).cowState(h.absolutePos(HEAD)).replacementTicks();
            h.assertTrue(
                    vat(h).frequencyMultiplier(h.absolutePos(HEAD)) == 0.5
                            && vat(h).metrics().total("cultureConsumed") == 0,
                    "Reload double-consumed culture or did not expire credit");
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    vat(h).cowState(h.absolutePos(HEAD)).replacementTicks() - milk[0] == 10,
                    "Exhausted culture still accelerated juvenile growth");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void fiveVatFluidsPersistAndExportIndependently(GameTestHelper h) {
        rig(h, false);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            c.produceMilk();
            c.produceMeat();
            c.produceWaste();
            ((VatFluidPortEntity) h.getBlockEntity(FLUID)).setIoDirection(IoDirection.Input);
            var in = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FLUID), Direction.WEST);
            in.fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 500), FluidAction.EXECUTE);
            in.fill(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 500), FluidAction.EXECUTE);
            var saved = save(h);
            c.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    c.cultureAmount() == 500
                            && c.feedingAmount() == 500
                            && c.milkAmount() == 1000
                            && c.meatAmount() == 1000
                            && c.wasteAmount() == 250,
                    "Five logical fluids lost/mixed on reload");
            ((VatFluidPortEntity) h.getBlockEntity(FLUID)).setIoDirection(IoDirection.Output);
            var out = productOutput(h);
            h.assertTrue(
                    out.getTanks() >= 8
                            && out.drain(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 123), FluidAction.SIMULATE)
                                            .getAmount()
                                    == 123
                            && c.cultureAmount() == 500,
                    "Explicit slurry simulation mutated buffer");
            out.drain(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 123), FluidAction.EXECUTE);
            h.assertTrue(
                    c.cultureAmount() == 377
                            && c.feedingAmount() == 500
                            && out.drain(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 50), FluidAction.EXECUTE)
                                    .is(ProductFluids.FEEDING_SLURRY.get())
                            && out.drain(new FluidStack(ProductFluids.MEAT.get(), 50), FluidAction.EXECUTE)
                                    .is(ProductFluids.MEAT.get()),
                    "Expanded round-robin/exact-fluid behavior wrong");
            h.succeed();
        });
    }

    private static final BlockPos BLENDER = ORIGIN.offset(4, 1, 2);
    private static final BlockPos UNUSED_BOTTOM = ORIGIN.offset(1, 0, 1);

    private static void seedWasteTimer(GameTestHelper h, int ticks) {
        var state = vat(h).processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.WASTE);
        state.elapsed = ticks;
        state.phase = ticks >= 1200 ? ProcessState.Phase.READY : ProcessState.Phase.RUNNING;
    }

    private static net.neoforged.neoforge.fluids.capability.IFluidHandler productOutput(GameTestHelper h) {
        var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FLUID), Direction.WEST);
        h.assertTrue(handler != null, "Missing product fluid capability");
        return handler;
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void blenderPriorityMeatCommit(GameTestHelper h) {
        rig(h, true);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        var besideBlender = BLENDER.north();
        genericOutput(h, besideBlender);
        h.runAtTickTime(30, () -> {
            h.assertTrue(vat(h).isAssembled(), "Blender prevented assembly");
            var routes = vat(h).getTopology().actors();
            h.assertTrue(
                    routes.stream()
                                    .anyMatch(a -> a.actor().equals(h.absolutePos(BLENDER))
                                            && a.use().equals(PassiveUses.BLEND))
                            && routes.stream()
                                    .anyMatch(a -> a.actor().equals(h.absolutePos(TOOL))
                                            && a.use().equals(CowBalance.HARVEST_USE)),
                    "Compiler retained rejected Sword or failed Blender priority");
            h.assertTrue(
                    vat(h).getTopology().genericItemOutputs().contains(h.absolutePos(besideBlender))
                            && routes.stream()
                                    .filter(a -> a.actor().equals(h.absolutePos(BLENDER)))
                                    .allMatch(a -> a.itemOutputs().isEmpty()),
                    "Fluid-only Blender incorrectly reserved generic Item Output");
            seed(h, "READY", 1200, 0, 0, 7, ItemStack.EMPTY);
        });
        h.runAtTickTime(55, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.meatAmount() == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(HEAD)))
                            && c.cowState(h.absolutePos(HEAD)).replacement().equals("GROWING")
                            && c.cowState(h.absolutePos(HEAD)).pendingItemCount() == 0,
                    "Blender failed to consume the shared READY replacement");
            h.assertTrue(
                    c.killOutputs().isEmpty()
                            && c.getExperienceMb() == 140
                            && tool(h).heldItem().getDamageValue() == 0
                            && c.metrics().total(MachineMetrics.BLENDER_HARVESTS) == 1
                            && c.metrics().total(MachineMetrics.HARVESTS) == 0,
                    "Blender emitted item loot/XP/durability or duplicate Sword harvest");
            var pages = c.statusSnapshot().pages();
            h.assertTrue(
                    pages.get(1).contains("Harvest actor: Blender")
                            && pages.get(3).contains("Blender @")
                            && pages.get(3).contains("Assigned / mobvats:blend"),
                    "Selected/unassigned actor diagnostics incorrect");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 95)
    public static void blenderCapacityAndItemBufferIndependence(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        h.runAtTickTime(30, () -> {
            for (int i = 0; i < 4; i++) h.assertTrue(vat(h).produceMeat(), "Failed to prepare full meat tank");
            ((VatBlenderEntity) h.getBlockEntity(BLENDER))
                    .service()
                    .tank(new FluidStack(ProductFluids.MEAT.get(), 1))
                    .fill(new FluidStack(ProductFluids.MEAT.get(), 2000), FluidAction.EXECUTE);
            tool(h).results().store(List.of(new ItemStack(Items.BEEF, 3)));
            seed(h, "READY", 1200, 1200, 0, 5, ItemStack.EMPTY);
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(
                    vat(h).cowState(h.absolutePos(HEAD)).replacement().equals("IDLE")
                            && vat(h).getExperienceMb() == 100
                            && vat(h).metrics().total(MachineMetrics.BLENDER_HARVESTS) == 0,
                    "Full MEAT tank partially committed Blender");
            h.assertTrue(
                    vat(h).milkAmount() == (int) Math.floor(1000 * vat(h).yieldMultiplier(h.absolutePos(HEAD)))
                            && vat(h).statusSnapshot().pages().get(3).contains("MEAT buffer lacks"),
                    "Milk blocked by MEAT or capacity diagnostic absent");
            h.assertTrue(
                    productOutput(h)
                                    .drain(new FluidStack(ProductFluids.MEAT.get(), 2000), FluidAction.EXECUTE)
                                    .getAmount()
                            == 2000,
                    "MEAT did not export");
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(
                    vat(h).meatAmount() == 4000
                            && vat(h).cowState(h.absolutePos(HEAD))
                                    .replacement()
                                    .equals("GROWING")
                            && tool(h).results().itemCount() == 3
                            && vat(h).getExperienceMb() == 100
                            && tool(h).heldItem().getDamageValue() == 0,
                    "Item backlog unnecessarily blocked Blender or was consumed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void productFluidArbitrationAndPersistence(GameTestHelper h) {
        rig(h, false);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            c.produceMilk();
            c.produceMeat();
            c.produceWaste();
            var out = productOutput(h);
            h.assertTrue(
                    out.getTanks() >= 8
                            && out.fill(new FluidStack(ProductFluids.MEAT.get(), 100), FluidAction.EXECUTE) == 0,
                    "Physical output policy incorrect");
            var before = save(h);
            c.syncDataFrom(before, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    c.milkAmount() == 1000 && c.meatAmount() == 1000 && c.wasteAmount() == 250,
                    "Controller reload altered member-owned contents");
            for (var fluid : List.of(NeoForgeMod.MILK.get(), ProductFluids.MEAT.get(), ProductFluids.WASTE.get())) {
                var wanted = new FluidStack(fluid, 125);
                h.assertTrue(
                        out.drain(wanted, FluidAction.SIMULATE).getAmount() == 125
                                && out.drain(wanted, FluidAction.EXECUTE).getAmount() == 125,
                        "Exact simulation/export failed");
            }
            h.assertTrue(
                    c.milkAmount() == 875
                            && c.meatAmount() == 875
                            && c.wasteAmount() == 125
                            && out.drain(0, FluidAction.EXECUTE).isEmpty(),
                    "Partial exports mixed fluids");
            var port = (VatFluidPortEntity) h.getBlockEntity(FLUID);
            port.setIoDirection(IoDirection.Input);
            port.filter("#minecraft:water");
            var input = port.externalFluids();
            h.assertTrue(
                    input.fill(new FluidStack(ProductFluids.MEAT.get(), 500), FluidAction.EXECUTE) == 0
                            && input.fill(
                                            new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 500),
                                            FluidAction.EXECUTE)
                                    == 500,
                    "Endpoint selector failed");
            var saved = port.saveWithFullMetadata(h.getLevel().registryAccess());
            port.filter("");
            port.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(port.filter().equals("#minecraft:water"), "Port filter lost on reload");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void sumpOwnsWasteWithoutAuxiliaryActor(GameTestHelper h) {
        rig(h, false);
        h.runAtTickTime(30, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.getTopology().actors().stream()
                            .filter(r -> r.use().equals(CowBalance.DRAIN_USE))
                            .allMatch(r -> h.getLevel().getBlockEntity(r.actor()) instanceof VatSumpEntity),
                    "Stale auxiliary actor claim");
            seedWasteTimer(h, CowBalance.WASTE_INTERVAL);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(
                    vat(h).wasteAmount() == (int) Math.floor(250 * vat(h).yieldMultiplier(h.absolutePos(HEAD))),
                    "Sump failed collection");
            var saved = save(h);
            vat(h).syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    vat(h).wasteAmount() == (int) Math.floor(250 * vat(h).yieldMultiplier(h.absolutePos(HEAD))),
                    "Reload lost waste");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void sumpFullBufferRetriesWithoutLoss(GameTestHelper h) {
        rig(h, false);
        h.setBlock(BOTTOM_A, Content.state(VatPartType.Casing));
        h.runAtTickTime(30, () -> {
            for (int i = 0; i < 16; i++) vat(h).produceWaste();
            vat(h).getAssemblies()
                    .getFirst()
                    .drain()
                    .service()
                    .tank(new FluidStack(ProductFluids.WASTE.get(), 1))
                    .fill(new FluidStack(ProductFluids.WASTE.get(), 1000), FluidAction.EXECUTE);
            seedWasteTimer(h, CowBalance.WASTE_INTERVAL);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(
                    vat(h).wasteAmount() == 4000
                            && vat(h).metrics().total(MachineMetrics.WASTE) == 4000
                            && vat(h).statusSnapshot().pages().get(3).contains("Sump blocked"),
                    "Full waste tank lost/duplicated yield");
            var saved = save(h);
            h.assertTrue(
                    vat(h).processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.WASTE).elapsed
                            == 1200,
                    "Full tank reset waste timer");
            productOutput(h)
                    .drain(
                            new FluidStack(ProductFluids.WASTE.get(), (int)
                                    Math.floor(250 * vat(h).yieldMultiplier(h.absolutePos(HEAD)))),
                            FluidAction.EXECUTE);
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    vat(h).wasteAmount() == 4000
                            && vat(h).metrics().total(MachineMetrics.WASTE)
                                    == 4000 + (int) Math.floor(250 * vat(h).yieldMultiplier(h.absolutePos(HEAD))),
                    "Sump did not retry exactly once after export");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 95)
    public static void blenderReachAndSwordFallback(GameTestHelper h) {
        rig(h, false);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        var tooHigh = BLENDER.above();
        h.setBlock(tooHigh, Content.state(VatPartType.Blender));
        h.runAtTickTime(30, () -> {
            h.assertTrue(
                    vat(h).getTopology().actors().stream()
                                    .anyMatch(a -> a.actor().equals(h.absolutePos(TOOL))
                                            && a.use().equals(CowBalance.HARVEST_USE))
                            && vat(h).getTopology().actors().stream()
                                    .noneMatch(a -> a.actor().equals(h.absolutePos(tooHigh))),
                    "Unreachable Blender displaced Sword");
            h.setBlock(tooHigh, Content.state(VatPartType.Casing));
            h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(
                    vat(h).getTopology().actors().stream()
                            .anyMatch(a -> a.actor().equals(h.absolutePos(TOOL))),
                    "Independent kill route disappeared");
            h.setBlock(BLENDER, Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    vat(h).getTopology().actors().stream()
                            .anyMatch(a -> a.actor().equals(h.absolutePos(TOOL))),
                    "Removing Blender failed to recompile Sword fallback");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void productAssimilationPreservesOverflow(GameTestHelper h) {
        var a = new ProductBuffers(new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000), () -> {});
        var b = new ProductBuffers(new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000), () -> {});
        for (int i = 0; i < 4; i++) {
            a.produceMeat();
            b.produceMeat();
        }
        a.assimilate(b);
        b.clear();
        h.assertTrue(
                a.meatAmount() == 8000 && b.meatAmount() == 0 && !a.produceMeat(),
                "Assimilation discarded committed fluid or allowed over-capacity production");
        var saved = a.save(h.getLevel().registryAccess());
        a.load(saved, h.getLevel().registryAccess());
        h.assertTrue(
                a.drain(new FluidStack(ProductFluids.MEAT.get(), 8000), FluidAction.EXECUTE)
                                        .getAmount()
                                == 8000
                        && a.meatAmount() == 0,
                "Overflow failed persistence/export conservation");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1350)
    public static void physicalFeedBlenderAndSumpCycle(GameTestHelper h) {
        rig(h, false);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.setBlock(BLENDER, Content.state(VatPartType.Blender));
        h.setBlock(BOTTOM_A, Content.state(VatPartType.Casing));
        item(h, FEED).staging().setStackInSlot(0, new ItemStack(Items.WHEAT));
        h.runAtTickTime(
                35,
                () -> h.assertTrue(
                        vat(h).cowState(h.absolutePos(HEAD)).replacement().equals("GROWING"),
                        "Physical feed did not start shared replacement"));
        h.runAtTickTime(1300, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.meatAmount() == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(HEAD)))
                            && c.wasteAmount() == (int) Math.floor(250 * c.yieldMultiplier(h.absolutePos(HEAD)))
                            && c.milkAmount() == 0
                            && c.cowState(h.absolutePos(HEAD)).replacement().equals("GROWING")
                            && c.killOutputs().isEmpty()
                            && c.metrics().total(MachineMetrics.WHEAT) == 1
                            && c.metrics().total(MachineMetrics.BLENDER_HARVESTS) == 1
                            && c.metrics().ratio(MachineMetrics.MEAT, MachineMetrics.WHEAT)
                                    == (int) Math.floor(1000 * c.yieldMultiplier(h.absolutePos(HEAD)))
                            && c.getExperienceMb() == 0,
                    "End-to-end Blender/Sump/milk yield or shared cycle incorrect");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void rollingMetricsWindowAndPersistence(GameTestHelper h) {
        var m = new MachineMetrics();
        for (int i = 0; i < 200; ++i) m.tick();
        m.record(MachineMetrics.MILK, 1000);
        m.record(MachineMetrics.HARVEST_ITEMS, 6);
        m.record(MachineMetrics.WHEAT, 2);
        m.record(MachineMetrics.XP, 4);
        m.record(MachineMetrics.DURABILITY, 1);
        m.record(MachineMetrics.HARVESTS, 2);
        h.assertTrue(
                m.perSecond(MachineMetrics.MILK) == 100
                        && m.ratio(MachineMetrics.HARVEST_ITEMS, MachineMetrics.WHEAT) == 3
                        && m.ratio(MachineMetrics.XP, MachineMetrics.WHEAT) == 2
                        && m.ratio(MachineMetrics.DURABILITY, MachineMetrics.HARVESTS) == .5,
                "Metrics rates/ratios do not reflect committed counters");
        var saved = m.save();
        for (int i = 0; i < 199; ++i) m.tick();
        h.assertTrue(m.perSecond(MachineMetrics.MILK) == 100, "Rolling sample expired early");
        m.tick();
        h.assertTrue(
                m.perSecond(MachineMetrics.MILK) == 0 && m.total(MachineMetrics.MILK) == 1000,
                "Rolling sample did not expire at 200 ticks");
        m.load(saved);
        h.assertTrue(
                m.total(MachineMetrics.MILK) == 1000 && m.perSecond(MachineMetrics.MILK) == 0 && m.elapsedTicks() == 0,
                "Reload must preserve totals and clear recent rates");
        var other = new MachineMetrics();
        other.record(MachineMetrics.MILK, 500);
        m.assimilate(other);
        h.assertTrue(
                m.total(MachineMetrics.MILK) == 1500 && m.perSecond(MachineMetrics.MILK) == 0,
                "Assimilation must merge totals without importing stale rate samples");
        h.assertTrue(
                Double.isNaN(new MachineMetrics().ratio(MachineMetrics.XP, MachineMetrics.WHEAT)),
                "Unsampled ratio should be undefined");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void committedProductionMetricsAndArmSignal(GameTestHelper h) {
        rig(h, true);
        item(h, INPUT).endpoint().filter("minecraft:iron_sword");
        var sword = new ItemStack(Items.IRON_SWORD);
        tool(h).setHeldItem(sword);
        // This assertion measures buffered production. Block exports explicitly instead
        // of depending on whether the assertion falls before the next routing cadence.
        for (var pos : List.of(OUTPUT_A, OUTPUT_B))
            item(h, pos).staging().setStackInSlot(0, new ItemStack(Items.STONE, 64));

        h.runAtTickTime(30, () -> {
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.WHEAT) == 0,
                    "Controller did not record actual wheat consumption");
            seed(h, "READY", 1200, 1200, 0, 0, sword);
        });
        h.runAtTickTime(45, () -> {
            var m = vat(h).metrics();
            int items = (int) tool(h).results().itemCount();
            h.assertTrue(
                    m.total(MachineMetrics.MILK) == (int) Math.floor(1000 * vat(h).yieldMultiplier(h.absolutePos(HEAD)))
                            && m.perSecond(MachineMetrics.MILK) > 0,
                    "Milk commit was not recorded");
            h.assertTrue(
                    m.total(MachineMetrics.ITEMS) == 0
                            && m.total(MachineMetrics.HARVEST_ITEMS) == items
                            && m.perSecond(MachineMetrics.HARVEST_ITEMS) > 0,
                    "Buffered harvest commit/drain metrics were conflated: routed=" + m.total(MachineMetrics.ITEMS)
                            + " harvested=" + m.total(MachineMetrics.HARVEST_ITEMS) + " buffered=" + items + " rate="
                            + m.perSecond(MachineMetrics.HARVEST_ITEMS));
            h.assertTrue(
                    m.total(MachineMetrics.XP) * net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.ratio()
                                    == vat(h).getExperienceMb()
                            && m.perSecond(MachineMetrics.XP) > 0
                            && m.total(MachineMetrics.HARVESTS) == 1
                            && m.ratio(MachineMetrics.HARVEST_ITEMS, MachineMetrics.WHEAT) == items
                            && m.ratio(MachineMetrics.DURABILITY, MachineMetrics.HARVESTS) == 1,
                    "XP/efficiency counters differ from process commit");
            h.assertTrue(
                    VatToolPortEntity.extension(
                                    tool(h).interactionStart(), h.getLevel().getGameTime())
                            > 0,
                    "Committed interaction signal unavailable for arm animation");
            var saved = save(h);
            vat(h).syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.HARVESTS) == 1
                            && vat(h).metrics().perSecond(MachineMetrics.ITEMS) == 0,
                    "Controller reload metrics behavior incorrect");
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    VatToolPortEntity.extension(
                                    tool(h).interactionStart(), h.getLevel().getGameTime())
                            == 0,
                    "Arm signal failed to retract after cosmetic pulse");
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.HARVESTS) == 1, "Presentation changed harvest timing/count");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void presentationAssignmentAndFeedChanges(GameTestHelper h) {
        rig(h, true);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(HEAD);
            var visual = head.presentation();
            h.assertTrue(
                    visual.tools().size() == 1
                            && visual.tools().getFirst().assigned()
                            && visual.feeds().size() == 1,
                    "Controller did not publish selected tool/feed anchors");
            var arm = visual.tools().getFirst();
            var body = EntityType.COW
                    .getDimensions()
                    .makeBoundingBox(vat(h).getAssemblies().getFirst().renderPosition());
            h.assertTrue(
                    body.contains(arm.target()) && arm.inward() == Direction.NORTH,
                    "Arm does not terminate inside body along compiled cardinal direction");
            var specimen = vat(h).getAssemblies().getFirst().renderPosition();
            var facing = head.getSpecimenFacing();
            var expectedHead =
                    specimen.add(facing.getStepX() * 11.0 / 16, 1.501 - 4.0 / 16, facing.getStepZ() * 11.0 / 16);
            h.assertTrue(
                    visual.feeds().getFirst().target().distanceToSqr(expectedHead) < 1e-10,
                    "Feed tube did not terminate at the vanilla cow head-box center for the placed facing");
            double expected = h.absolutePos(TOOL).getZ() + 13.0 / 16 - .01;
            h.assertTrue(
                    Math.abs(arm.base().z - expected) < .00001,
                    "Mechanism base does not match three-pixel shell inner surface");
            h.assertTrue(
                    VatPresentation.load(visual.save()).equals(visual), "Static presentation NBT did not roundtrip");
            long revision = head.presentationRevision();
            var sword = tool(h).heldItem();
            sword.setDamageValue(3);
            tool(h).setHeldItem(sword);
            h.assertTrue(
                    head.presentationRevision() == revision,
                    "Unchanged anchors were republished for a durability update");
            item(h, FEED).setIoDirection(IoDirection.Output);
            h.assertTrue(
                    !head.presentation().feeds().isEmpty(),
                    "Fluid-capable Feed route disappeared with Item Input change");
            h.setBlock(FEED.south(), Blocks.AIR);
            h.setBlock(FEED, Content.state(VatPartType.ToolPort));
            ((VatToolPortEntity) h.getBlockEntity(FEED)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        });
        h.runAtTickTime(60, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(HEAD);
            var old = head.presentation().tools().stream()
                    .filter(l -> l.port().equals(h.absolutePos(TOOL)))
                    .findFirst()
                    .orElseThrow();
            h.assertTrue(
                    old.assigned() && head.presentation().feeds().isEmpty(),
                    "Independent Tool or removed Feed relationship incorrect");
            var text = vat(h).statusSnapshot().pages().get(3);
            h.assertTrue(text.contains("Assigned"), "Menu snapshot did not reflect changed selected assignments");
            h.setBlock(ORIGIN.offset(0, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(
                    ((VatHeadEntity) h.getBlockEntity(HEAD)).presentation().equals(VatPresentation.EMPTY),
                    "Broken vat retained presentation anchors");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void compactStatusAndActualHeldItemSync(GameTestHelper h) {
        rig(h, true);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(7);
        sword.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Mounted specimen tool"));
        sword.enchant(
                h.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.LOOTING),
                2);
        tool(h).setHeldItem(sword);
        for (var pos : List.of(OUTPUT_A, OUTPUT_B))
            item(h, pos).staging().setStackInSlot(0, new ItemStack(Items.STONE, 64));
        h.runAtTickTime(30, () -> seed(h, "READY", 1200, 0, 0, 5, sword));
        h.runAtTickTime(65, () -> {
            h.assertTrue(
                    tool(h).results().stackCount() == 2
                            && vat(h).cowState(h.absolutePos(HEAD)).pendingItemCount() == 0,
                    "Kill did not commit to server-only buffer");
            var compact = vat(h).syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            h.assertTrue(
                    compact.contains("cowStatus")
                            && compact.getLong("experienceMb") == vat(h).getExperienceMb()
                            && compact.getInt("killOutputStacks") == 2
                            && !compact.contains("killOutputs")
                            && !compact.contains("cowProcesses")
                            && !compact.toString().contains("pendingItems")
                            && !compact.toString().contains("pendingWeapon")
                            && !compact.toString().contains("itemContacts")
                            && !compact.contains("metrics"),
                    "Client status included server-only process/graph objects");
            var status = vat(h).statusSnapshot();
            h.assertTrue(
                    status.pages().size() == 4 && status.pages().get(3).contains("local output"),
                    "Buffered/global pause diagnostic omitted");
            tool(h).setInteractionStart(h.getLevel().getGameTime());
            var portTag =
                    tool(h).syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            var decoded = new VatToolPortEntity(tool(h).getBlockPos(), tool(h).getBlockState());
            decoded.syncDataFrom(portTag, h.getLevel().registryAccess(), SyncReason.NetworkUpdate);
            h.assertTrue(
                    ItemStack.matches(decoded.heldItem(), tool(h).heldItem())
                            && decoded.heldItem().hasFoil()
                            && decoded.interactionStart() == tool(h).interactionStart(),
                    "Actual held components/glint or interaction timestamp failed sync");
            var persisted = tool(h).syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    persisted.contains("interactionStart"),
                    "Cosmetic interaction pulse should not persist across reload");
            h.assertTrue(
                    net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDetail.FULL
                                    .rendersMechanisms()
                            && net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDetail.REDUCED
                                    .rendersMechanisms()
                            && !net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDetail
                                    .MINIMAL
                                    .rendersMechanisms()
                            && !net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDetail.CULLED
                                    .rendersMechanisms(),
                    "New mechanisms bypass render detail permissions");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 75)
    public static void controllerMenuProviderAndTypedSync(GameTestHelper h) {
        rig(h, true);
        h.runAtTickTime(30, () -> {
            var pos = ORIGIN.offset(1, 1, 0);
            var part = (VatControllerEntity) h.getBlockEntity(pos);
            var player = h.makeMockPlayer(GameType.CREATIVE);
            player.setPos(Vec3.atCenterOf(h.absolutePos(pos)));
            var created = part.createMenu(1, player.getInventory(), player);
            h.assertTrue(
                    created instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu
                            && created.getType() == Content.CONTROLLER_MENU.get(),
                    "Controller provider did not create the registered menu");
            var menu = (net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu) created;
            h.assertTrue(
                    menu.stillValid(player) && menu.slots.isEmpty() && !menu.clickMenuButton(player, 0),
                    "Controller observer has writable controls or invalid lifetime");
            var client = new it.zerono.mods.zerocore.lib.item.inventory.container.ModContainer(
                    true,
                    200,
                    it.zerono.mods.zerocore.lib.item.inventory.container.ContainerFactory.EMPTY,
                    Content.CONTROLLER_MENU.get(),
                    menu.containerId,
                    player.getInventory());
            var server = new it.zerono.mods.zerocore.lib.item.inventory.container.ModContainer(
                    false,
                    200,
                    it.zerono.mods.zerocore.lib.item.inventory.container.ContainerFactory.EMPTY,
                    Content.CONTROLLER_MENU.get(),
                    menu.containerId,
                    player.getInventory());
            final String[] received = {""};
            var source = it.zerono.mods.zerocore.lib.item.inventory.container.data.StringData.of(
                    server, 32767, () -> vat(h).statusSnapshot().pages().get(1));
            var target = it.zerono.mods.zerocore.lib.item.inventory.container.data.StringData.of(
                    client, 32767, () -> "", value -> received[0] = value);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                source.getChangedValue().accept(buffer);
                target.updateFrom(target.getValueFrom(buffer));
            } finally {
                buffer.release();
            }
            h.assertTrue(
                    received[0].contains("mobvats:milk:")
                            && received[0].contains("Stage:")
                            && received[0].contains("XP fluid:"),
                    "ZeroCore menu status codec/setter did not deliver presentation data");
            player.setPos(player.position().add(20, 0, 0));
            h.assertTrue(!menu.stillValid(player), "Distant player retained controller menu");
            menu.removed(player);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2560)
    public static void passiveMilkFluidAndDrain(GameTestHelper h) {
        rig(h, false);
        h.runAtTickTime(30, () -> {
            h.assertTrue(vat(h).isAssembled() && vat(h).milkAmount() == 0, "Passive milk appeared before its interval");
            h.assertTrue(
                    vat(h).getTopology().actors().stream().anyMatch(c -> c.use().equals(PassiveUses.FEED)),
                    "Direct feed route missing");
        });
        h.runAtTickTime(2440, () -> {
            h.assertTrue(
                    vat(h).milkAmount() == (int) Math.floor(1000 * vat(h).yieldMultiplier(h.absolutePos(HEAD))),
                    "Cow did not passively accumulate 1000 mB after 1200 active ticks");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            h.useBlock(ORIGIN.offset(2, 0, 2), player);
            h.assertTrue(
                    player.getMainHandItem().is(Items.BUCKET)
                            && vat(h).milkAmount()
                                    == (int) Math.floor(1000 * vat(h).yieldMultiplier(h.absolutePos(HEAD))),
                    "Drain extracted milk");
            var handler =
                    h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(FLUID), Direction.WEST);
            h.assertTrue(
                    handler != null
                            && handler.drain(new FluidStack(NeoForgeMod.MILK.get(), 1000), FluidAction.SIMULATE)
                                    .is(NeoForgeMod.MILK.get())
                            && handler.fill(new FluidStack(NeoForgeMod.MILK.get(), 1000), FluidAction.EXECUTE) == 0
                            && handler.drain(1000, FluidAction.EXECUTE).getAmount() == 1000
                            && vat(h).milkAmount()
                                    == (int) Math.floor(1000 * vat(h).yieldMultiplier(h.absolutePos(HEAD))) - 1000,
                    "Fluid Output milk delegation/policy failed");
            h.assertTrue(
                    h.getLevel()
                                    .getCapability(
                                            Capabilities.FluidHandler.BLOCK,
                                            h.absolutePos(ORIGIN.offset(2, 0, 2)),
                                            Direction.DOWN)
                            == null,
                    "Drain exposed a fluid capability");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 4960)
    public static void minimumManualMilkBucket(GameTestHelper h) {
        VatGameTests.build(h, 3);
        var pos = ORIGIN.offset(1, 1, 2);
        h.setBlock(pos, Content.state(VatPartType.ToolPort));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        h.useBlock(pos, player);
        var head = (VatHeadEntity) h.getBlockEntity(ORIGIN.offset(1, 2, 1));
        head.encode(ItemEssence.COW);
        h.runAtTickTime(4840, () -> {
            var port = (VatToolPortEntity) h.getBlockEntity(pos);
            var c = head.getMultiblockController().orElseThrow();
            h.assertTrue(
                    c.isAssembled()
                            && port.results().snapshot().stream().anyMatch(s -> s.is(Items.MILK_BUCKET))
                            && c.milkAmount() == 0,
                    "Manual minimum-vat bucket process failed");
            h.assertTrue(
                    c.getTopology().itemInputs().isEmpty()
                            && c.getTopology().itemOutputs().isEmpty(),
                    "Manual vat unexpectedly needs IO ports");
        });
        h.runAtTickTime(4880, () -> {
            var port = (VatToolPortEntity) h.getBlockEntity(pos);
            h.assertTrue(
                    port.results().snapshot().stream().anyMatch(s -> s.is(Items.MILK_BUCKET)),
                    "Blocked bucket retries lost the resident result");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.useBlock(pos, player);
            h.assertTrue(
                    port.heldItem().isEmpty() && player.getInventory().countItem(Items.MILK_BUCKET) == 1,
                    "Manual milk bucket retrieval lost or duplicated result");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void automatedMilkBlockedOutputAndRetries(GameTestHelper h) {
        rig(h, true);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:bucket"); /* Exclude production from this tool-only Store when testing output backpressure. */
        h.runAtTickTime(30, () -> {
            seed(h, "IDLE", 0, 0, 3000, 0, ItemStack.EMPTY);
            item(h, INPUT).staging().setStackInSlot(0, new ItemStack(Items.BUCKET, 3));
            item(h, OUTPUT_B).staging().setStackInSlot(0, new ItemStack(Items.STONE, 64));
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(
                    item(h, OUTPUT_A).staging().getStackInSlot(0).is(Items.MILK_BUCKET)
                            && tool(h).results().snapshot().stream().anyMatch(s -> s.is(Items.MILK_BUCKET))
                            && item(h, INPUT).staging().getStackInSlot(0).getCount() == 1
                            && vat(h).milkAmount() == 1000,
                    "Input -> Tool -> Output or blocked resident bucket failed");
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    tool(h).results().snapshot().stream().anyMatch(s -> s.is(Items.MILK_BUCKET))
                            && vat(h).milkAmount() == 1000
                            && item(h, INPUT).staging().getStackInSlot(0).getCount() == 1,
                    "Retries duplicated bucket or consumed more milk");
            h.assertTrue(
                    vat(h).metrics().total(MachineMetrics.ITEMS) == 1,
                    "Blocked bucket retries inflated routed-item counters");
            h.assertTrue(
                    item(h, OUTPUT_A).externalItems().extractItem(0, 1, false).is(Items.MILK_BUCKET),
                    "Automated extraction failed");
        });
        h.runAtTickTime(180, () -> {
            h.assertTrue(
                    item(h, OUTPUT_A).staging().getStackInSlot(0).is(Items.MILK_BUCKET)
                            && tool(h).results().snapshot().stream().anyMatch(s -> s.is(Items.MILK_BUCKET))
                            && item(h, INPUT).staging().getStackInSlot(0).isEmpty()
                            && vat(h).milkAmount() == 0,
                    "Completed bucket did not resume routing safely");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 4960)
    public static void physicalFeedingReplacementHarvestBuffered(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        item(h, FEED).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(65, () -> {
            h.assertTrue(
                    vat(h).cowState(h.absolutePos(HEAD)).replacement().equals("GROWING")
                            && item(h, FEED).staging().getStackInSlot(0).getCount() == 1,
                    "Direct wheat feed did not begin one replacement cycle");
        });
        h.runAtTickTime(1240, () -> {
            h.assertTrue(
                    vat(h).cowState(h.absolutePos(HEAD)).replacement().equals("GROWING")
                            && item(h, FEED).staging().getStackInSlot(0).getCount() == 1
                            && vat(h).getExperienceMb() > 0
                            && tool(h).heldItem().getDamageValue() == 2
                            && !tool(h).results().isEmpty(),
                    "Physical feeding/harvest did not progress into safely buffered output");
            h.assertTrue(vat(h).milkAmount() == 0, "Juvenile milk must pause");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void irrelevantItemsAndNonFeedInputs(GameTestHelper h) {
        rig(h, true);
        item(h, INPUT).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        item(h, FEED).staging().setStackInSlot(0, new ItemStack(Items.CARROT, 4));
        h.runAtTickTime(60, () -> {
            h.assertTrue(
                    vat(h).cowState(h.absolutePos(HEAD)).replacement().equals("IDLE")
                            && tool(h).heldItem().isEmpty()
                            && item(h, INPUT).staging().getStackInSlot(0).getCount() == 4
                            && item(h, FEED).staging().getStackInSlot(0).getCount() == 4,
                    "Controller shuffled irrelevant tools or fed globally from a non-contact input");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void swordHarvestBufferedPartialDrainAndReusable(GameTestHelper h) {
        rig(h, false);
        item(h, INPUT)
                .endpoint()
                .filter(
                        "minecraft:iron_sword"); /* Exclude production from this tool-only Store when testing output backpressure. */
        genericOutput(h, BOTTOM_A);
        connectBottomTool(h);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        item(h, BOTTOM_A).staging().setStackInSlot(0, new ItemStack(Items.BEEF, 63));
        h.runAtTickTime(
                30, () -> seedFixedDrops(h, List.of(new ItemStack(Items.BEEF, 3), new ItemStack(Items.LEATHER))));
        h.runAtTickTime(75, () -> {
            h.assertTrue(
                    tool(h).results().itemCount() == 3
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).getCount() == 64
                            && vat(h).getExperienceMb() == 40,
                    "Prepared lethal batch did not preserve partial capacity");
            var saved = tool(h).saveWithFullMetadata(h.getLevel().registryAccess());
            tool(h).loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(tool(h).results().itemCount() == 3, "Tool member reload lost batch");
            item(h, BOTTOM_A).staging().setStackInSlot(0, ItemStack.EMPTY);
        });
        h.runAtTickTime(110, () -> {
            h.assertTrue(
                    tool(h).results().itemCount() == 1
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).getCount() == 2,
                    "Partial beef drain lost wool/skin remainder");
            item(h, BOTTOM_A).staging().setStackInSlot(0, ItemStack.EMPTY);
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(
                    tool(h).results().isEmpty()
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).is(Items.LEATHER),
                    "Final local stack failed to drain");
            seedFixedDrops(h, List.of(new ItemStack(Items.BEEF, 2)));
            item(h, BOTTOM_A).staging().setStackInSlot(0, ItemStack.EMPTY);
        });
        h.runAtTickTime(210, () -> {
            h.assertTrue(
                    tool(h).heldItem().getDamageValue() == 2
                            && vat(h).getExperienceMb() == 40
                            && vat(h).metrics().total(MachineMetrics.HARVESTS) == 2
                            && item(h, BOTTOM_A).staging().getStackInSlot(0).is(Items.BEEF),
                    "Tool failed to reuse after complete batch drain");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void swordBreakageCommit(GameTestHelper h) {
        rig(h, true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage() - 1);
        tool(h).setHeldItem(sword);
        h.runAtTickTime(30, () -> seed(h, "READY", CowBalance.REPLACEMENT_INTERVAL, 0, 0, 0, sword));
        h.runAtTickTime(80, () -> {
            h.assertTrue(
                    tool(h).heldItem().isEmpty()
                            && vat(h).getExperienceMb() > 0
                            && vat(h).cowState(h.absolutePos(HEAD))
                                    .replacement()
                                    .equals("GROWING"),
                    "Broken sword was not cleared after a successful harvest");
            h.assertTrue(
                    !tool(h).results().isEmpty()
                            || !item(h, OUTPUT_A).staging().getStackInSlot(0).isEmpty()
                            || !item(h, OUTPUT_B).staging().getStackInSlot(0).isEmpty(),
                    "Breaking sword lost the committed loot");
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    tool(h).heldItem().isEmpty() && vat(h).getExperienceMb() <= 60,
                    "Breakage retry duplicated harvest");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void cowProcessPersistenceAndPause(GameTestHelper h) {
        rig(h, false);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(4);
        tool(h).setHeldItem(sword);
        final CompoundTag[] saved = new CompoundTag[1];
        final int[] ticks = new int[1];
        h.runAtTickTime(30, () -> {
            seed(h, "GROWING", 400, 800, 2000, 7, ItemStack.EMPTY);
            saved[0] = save(h);
            var ref = vat(h).getReferenceCoord().orElseThrow();
            var refSave = h.getLevel()
                    .getBlockEntity(ref)
                    .saveWithFullMetadata(h.getLevel().registryAccess());
            h.assertTrue(
                    refSave.toString().contains("specimenProcesses")
                            && refSave.toString().contains("legacyXpMigration"),
                    "ZeroCore reference-coordinate NBT omitted controller processes");
            var portSave = tool(h).saveWithFullMetadata(h.getLevel().registryAccess());
            tool(h).loadWithComponents(portSave, h.getLevel().registryAccess());
            vat(h).syncDataFrom(saved[0], h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(
                    vat(h).milkAmount() == 2000
                            && vat(h).getExperienceMb() == 140
                            && vat(h).cowState(h.absolutePos(HEAD)).replacementTicks() == 400
                            && vat(h).cowState(h.absolutePos(HEAD)).milkTicks() == 800
                            && tool(h).heldItem().getDamageValue() == 4,
                    "Controller/Tool NBT lost process state");
            h.setBlock(ORIGIN.offset(0, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(!vat(h).isAssembled(), "Broken vat stayed assembled");
            ticks[0] = vat(h).cowState(h.absolutePos(HEAD)).replacementTicks();
        });
        h.runAtTickTime(80, () -> {
            var milk = ((net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity)
                            h.getBlockEntity(ORIGIN.offset(1, 3, 1)))
                    .tank;
            h.assertTrue(
                    vat(h).cowState(h.absolutePos(HEAD)).replacementTicks() == ticks[0]
                            && milk.getFluidAmount() == 2000
                            && vat(h).storage().clusters().isEmpty(),
                    "Unformed vat advanced production or lost physical milk");
            h.setBlock(ORIGIN.offset(0, 3, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(110, () -> {
            h.assertTrue(
                    vat(h).isAssembled()
                            && vat(h).cowState(h.absolutePos(HEAD)).replacementTicks() > ticks[0]
                            && vat(h).getExperienceMb() == 140
                            && tool(h).heldItem().getDamageValue() == 4,
                    "Reformation lost controller state or failed to resume");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void emptyConnectionsAndIndependentClaims(GameTestHelper h) {
        rig(h, true);
        var north = FEED;
        h.setBlock(FEED.south(), Blocks.AIR);
        h.setBlock(north, Content.state(VatPartType.ToolPort));
        var other = (VatToolPortEntity) h.getBlockEntity(north);
        tool(h).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30, () -> {
            var t = vat(h).getTopology();
            h.assertTrue(
                    t.actors().stream().anyMatch(r -> r.actor().equals(h.absolutePos(TOOL)))
                            && t.toolLoading().stream().anyMatch(r -> r.actor().equals(h.absolutePos(north))),
                    "Empty loading workflow displaced mounted Tool");
            other.setHeldItem(new ItemStack(Items.IRON_SWORD));
            h.assertTrue(
                    vat(h).getTopology().actors().stream()
                                    .filter(r -> r.use().equals(PassiveUses.HARVEST))
                                    .count()
                            == 2,
                    "Multiple Tool routes were collapsed");
            tool(h).results().store(List.of(new ItemStack(Items.LEATHER, 2)));
            other.setHeldItem(ItemStack.EMPTY);
            h.assertTrue(
                    vat(h).getTopology().toolLoading().stream()
                                    .anyMatch(r -> r.actor().equals(h.absolutePos(north)))
                            && tool(h).results().itemCount() == 2,
                    "Tool removal changed another local batch");
            noSpawnedProductionEntities(h);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cowLootEnchantmentsAndDurability(GameTestHelper h) {
        var position = Vec3.atCenterOf(h.absolutePos(ORIGIN));
        var plain = new ItemStack(Items.IRON_SWORD);
        var enchanted = plain.copy();
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        enchanted.enchant(registry.getOrThrow(Enchantments.LOOTING), 3);
        enchanted.enchant(registry.getOrThrow(Enchantments.FIRE_ASPECT), 2);
        int base = 0, looting = 0;
        for (long seed = 1; seed <= 64; seed++) {
            var normal = CowHarvestLoot.roll(h.getLevel(), position, plain, seed);
            var enhanced = CowHarvestLoot.roll(h.getLevel(), position, enchanted, seed);
            base += normal.items().stream().mapToInt(ItemStack::getCount).sum();
            looting += enhanced.items().stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(
                    enhanced.items().stream().noneMatch(s -> s.is(Items.BEEF))
                            && enhanced.items().stream().anyMatch(s -> s.is(Items.COOKED_BEEF)),
                    "Fire Aspect did not use vanilla cooked-beef loot semantics");
            h.assertTrue(normal.xp() >= 1 && normal.xp() <= 3, "Cow XP did not follow vanilla reward range");
        }
        h.assertTrue(looting > base, "Looting did not affect vanilla cow loot table outputs");
        var unbreakable = plain.copy();
        unbreakable.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        h.assertTrue(
                CowHarvestLoot.damageWeapon(h.getLevel(), unbreakable).getDamageValue() == 0,
                "Unbreakable sword was damaged");
        var unbreaking = plain.copy();
        unbreaking.enchant(registry.getOrThrow(Enchantments.UNBREAKING), 3);
        for (int i = 0; i < 64; i++) unbreaking = CowHarvestLoot.damageWeapon(h.getLevel(), unbreaking);
        h.assertTrue(unbreaking.getDamageValue() < 64, "Unbreaking was bypassed by harvest durability");
        h.assertTrue(
                CowHarvestLoot.damageWeapon(h.getLevel(), plain).getDamageValue() == 1,
                "Vanilla sword combat damage was not one durability");
        noSpawnedProductionEntities(h);
        h.succeed();
    }
}
