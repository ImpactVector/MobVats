package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerCombustionGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), C = O.offset(0, 1, 1);

    private static BlockPos rig(GameTestHelper h, int depth, MixerPartType motor) {
        boolean oneSlot = depth == 1;
        if (oneSlot) depth = 3;
        var top = MachineFixtures.mixer(h::setBlock, O, 5, depth + 2, 5, depth, 1, false);
        if (oneSlot)
            h.setBlock(O.offset(4, 2, 2), FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
        if (motor != null) h.setBlock(top.east(), MixerContent.state(motor));
        return top.east();
    }

    private static MultiblockMixer mixer(GameTestHelper h) {
        return ((MixerControllerEntity) h.getBlockEntity(C))
                .getMultiblockController()
                .orElseThrow();
    }
    // Simulate exact server ticks synchronously on a real assembled machine; no wall-clock races.
    static void ticks(MultiblockMixer c, int n) {
        try {
            var method = MultiblockMixer.class.getDeclaredMethod("updateServer");
            method.setAccessible(true);
            for (int i = 0; i < n; i++) method.invoke(c);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void reset(GameTestHelper h, MultiblockMixer c) {
        var tag = c.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        c.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
    }

    private static MixerCombustionDriveEntity motor(GameTestHelper h, BlockPos p) {
        return (MixerCombustionDriveEntity) h.getBlockEntity(p);
    }

    private static void state(GameTestHelper h, MultiblockMixer c, MixerOperatingState expected, int startup) {
        h.assertTrue(
                c.operatingState() == expected && c.startupTicks() == startup,
                "Expected " + expected + " / " + startup + ", got " + c.operatingState() + " / " + c.startupTicks());
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void noDriveNeverProcesses(GameTestHelper h) {
        MixerGameTests.rig(h);
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            c.state().fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            c.state().depositExperienceMb(250);
            ticks(c, 2000);
            h.assertTrue(
                    c.availableDrive() == 0
                            && c.state().progress() == 0
                            && c.state().amount(0) == 1000,
                    "Undriven Mixer processed");
            state(h, c, MixerOperatingState.STOPPED, 0);
            h.succeed();
        });
    }

    private static void startup(GameTestHelper h, int depth, boolean creative) {
        var p = rig(h, depth, creative ? MixerPartType.Drive : MixerPartType.CombustionDrive);
        h.runAtTickTime(35, () -> {
            var c = mixer(h);
            h.assertTrue(c.isAssembled(), "Fixture unformed");
            reset(h, c);
            int slots = c.slotCount();
            h.assertTrue(depth != 1 || slots == 1, "One-slot fixture incorrect");
            if (!creative) motor(h, p).fuel().insertItem(0, new ItemStack(Items.COAL, 8), false);
            int startup = slots * 200;
            ticks(c, startup - 1);
            state(h, c, MixerOperatingState.SPINNING_UP, startup - 1);
            ticks(c, 1);
            state(h, c, MixerOperatingState.RUNNING, startup);
            h.assertTrue(c.state().progress() == 0, "Startup advanced recipes");
            if (!creative) {
                var d = motor(h, p);
                int before = d.burnTicks() + d.fuel().getStackInSlot(0).getCount() * 1600;
                ticks(c, 200);
                int after = d.burnTicks() + d.fuel().getStackInSlot(0).getCount() * 1600;
                h.assertTrue(before - after == 200, "Operating cost scaled with slots");
                h.assertTrue(after == 8 * 1600 - startup - 200, "Startup cost not one fuel tick per tick");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void oneSlotCosts200AndOperationCosts200(GameTestHelper h) {
        startup(h, 1, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void manySlotsOnlyScaleStartup(GameTestHelper h) {
        startup(h, 3, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void eightSlotStartupAndOperatingCost(GameTestHelper h) {
        startup(h, 5, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void creativePaysFullStartup(GameTestHelper h) {
        startup(h, 2, true);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fuelsAndContainerRemainders(GameTestHelper h) {
        var p = rig(h, 1, MixerPartType.CombustionDrive);
        h.runAtTickTime(35, () -> {
            var d = motor(h, p);
            var c = mixer(h);
            h.assertTrue(
                    MixerCombustionDriveEntity.fuelTicks(new ItemStack(Items.COAL)) == 1600, "Coal baseline wrong");
            for (var item : new Item[] {Items.COAL, Items.CHARCOAL, Items.OAK_PLANKS, Items.STICK, Items.LAVA_BUCKET})
                h.assertTrue(d.fuel().isItemValid(0, new ItemStack(item)), "Furnace fuel rejected: " + item);
            h.assertTrue(
                    !d.fuel().insertItem(0, new ItemStack(Items.DIRT), false).isEmpty(), "Invalid fuel accepted");
            d.fuel().insertItem(0, new ItemStack(Items.LAVA_BUCKET), false);
            int burn = MixerCombustionDriveEntity.fuelTicks(new ItemStack(Items.LAVA_BUCKET));
            for (int i = 0; i < 20; i++) h.assertTrue(c.availableDrive() == 1, "Fuel query failed");
            h.assertTrue(d.burnTicks() == 0 && d.fuel().getStackInSlot(0).getCount() == 1, "Getter burned fuel");
            ticks(c, 1);
            h.assertTrue(
                    d.burnTicks() == burn - 1
                            && d.fuel().getStackInSlot(0).isEmpty()
                            && d.fuel().getStackInSlot(1).is(Items.BUCKET),
                    "Fuel remainder/value incorrect");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void exhaustionResetsAndRestartPaysFullStartup(GameTestHelper h) {
        var p = rig(h, 1, MixerPartType.CombustionDrive);
        h.runAtTickTime(35, () -> {
            var c = mixer(h);
            var d = motor(h, p);
            d.fuel().insertItem(0, new ItemStack(Items.STICK), false);
            ticks(c, 100);
            state(h, c, MixerOperatingState.SPINNING_UP, 100);
            ticks(c, 1);
            state(h, c, MixerOperatingState.STOPPED, 0);
            d.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            ticks(c, 199);
            state(h, c, MixerOperatingState.SPINNING_UP, 199);
            ticks(c, 1);
            state(h, c, MixerOperatingState.RUNNING, 200);
            ticks(c, 1400);
            state(h, c, MixerOperatingState.RUNNING, 200);
            ticks(c, 1);
            state(h, c, MixerOperatingState.STOPPED, 0);
            d.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            ticks(c, 199);
            state(h, c, MixerOperatingState.SPINNING_UP, 199);
            ticks(c, 1);
            state(h, c, MixerOperatingState.RUNNING, 200);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void conduitFuelCannotLeakToRecipeSlots(GameTestHelper h) {
        var p = rig(h, 2, MixerPartType.CombustionDrive);
        var port = p.east().below();
        h.setBlock(port, MixerContent.state(MixerPartType.ItemPort));
        for (var q :
                java.util.List.of(p.south(), p.east().south(), p.east().south().below()))
            h.setBlock(
                    q,
                    ConduitContent.state(ConduitResource.ITEM, ConduitCladding.CASING, ConduitShell.MIXER)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        h.runAtTickTime(35, () -> {
            var c = mixer(h);
            h.assertTrue(c.isAssembled(), "Conduit fixture unformed: " + c.getLastError());
            var handler =
                    h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(port), Direction.EAST);
            h.assertTrue(handler != null && handler.getSlots() == 6, "Topology did not route motor inventory");
            h.assertTrue(
                    handler.insertItem(0, new ItemStack(Items.CHARCOAL, 64), true)
                                    .isEmpty()
                            && motor(h, p).fuel().getStackInSlot(0).isEmpty(),
                    "Simulation mutated fuel");
            h.assertTrue(
                    handler.insertItem(0, new ItemStack(Items.CHARCOAL, 64), false)
                            .isEmpty(),
                    "Conduit fuel rejected");
            for (int i = 1; i < handler.getSlots(); i++)
                h.assertTrue(
                        !handler.insertItem(i, new ItemStack(Items.CHARCOAL), false)
                                .isEmpty(),
                        "Motor fuel leaked to recipe inventory");
            h.assertTrue(
                    c.state().plantCount() == 0
                            && ((MixerPortEntity) h.getBlockEntity(port))
                                            .fuelStore()
                                            .getStackInSlot(0)
                                            .getCount()
                                    == 64
                            && motor(h, p).fuel().getStackInSlot(0).isEmpty(),
                    "Fuel mixed with ingredients");
            ticks(c, 1);
            h.assertTrue(
                    ((MixerPortEntity) h.getBlockEntity(port))
                                            .fuelStore()
                                            .getStackInSlot(0)
                                            .getCount()
                                    == 63
                            && motor(h, p).burnTicks() > 0
                            && c.state().plantCount() == 0,
                    "Motor did not demand isolated fuel: port="
                            + ((MixerPortEntity) h.getBlockEntity(port))
                                    .fuelStore()
                                    .getStackInSlot(0)
                                    .getCount() + " burn=" + motor(h, p).burnTicks() + " slots=" + c.slotCount()
                            + " graph=" + c.conduitTopology().diagnostics());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void saveLoadAndPortableFuelConserve(GameTestHelper h) {
        var p = rig(h, 1, MixerPartType.CombustionDrive);
        h.runAtTickTime(35, () -> {
            var d = motor(h, p);
            var c = mixer(h);
            d.fuel().insertItem(0, new ItemStack(Items.COAL, 3), false);
            ticks(c, 77);
            var tag = d.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
            var portable = EndpointState.capture(new ItemStack(MixerContent.block(MixerPartType.CombustionDrive)), d);
            for (int i = 0; i < 3; i++) d.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
            h.assertTrue(d.burnTicks() == 1523 && d.fuel().getStackInSlot(0).getCount() == 2, "Reload duplicated fuel");
            h.setBlock(p, Blocks.AIR);
            h.setBlock(p, MixerContent.state(MixerPartType.CombustionDrive));
            EndpointState.restore(portable, h.getBlockEntity(p));
            var restored = motor(h, p);
            h.assertTrue(
                    restored.burnTicks() == 1523
                            && restored.fuel().getStackInSlot(0).getCount() == 2,
                    "Portable motor lost/duplicated fuel");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void breakReformPreservesFuelAndResetsStartup(GameTestHelper h) {
        var p = rig(h, 1, MixerPartType.CombustionDrive);
        int[] burn = {0};
        h.runAtTickTime(35, () -> {
            var c = mixer(h);
            var d = motor(h, p);
            d.fuel().insertItem(0, new ItemStack(Items.COAL, 2), false);
            ticks(c, 80);
            burn[0] = d.burnTicks();
            h.setBlock(O, Blocks.AIR);
        });
        h.runAtTickTime(65, () -> {
            var c = mixer(h);
            h.assertTrue(!c.isAssembled(), "Broken shell still formed");
            state(h, c, MixerOperatingState.STOPPED, 0);
            burn[0] = motor(h, p).burnTicks();
            ticks(c, 200);
            h.assertTrue(motor(h, p).burnTicks() == burn[0], "Unformed motor burned fuel");
            h.setBlock(O, MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(100, () -> {
            var c = mixer(h);
            h.assertTrue(c.isAssembled(), "Repair failed");
            h.assertTrue(
                    c.startupTicks() < 200 && c.operatingState() == MixerOperatingState.SPINNING_UP,
                    "Reform skipped startup");
            h.assertTrue(motor(h, p).fuel().getStackInSlot(0).getCount() == 1, "Reform duplicated staged fuel");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void recipeProgressSurvivesExhaustionAndRestart(GameTestHelper h) {
        MixerGameTests.rig(h);
        var p = O.offset(3, 3, 2);
        h.setBlock(p, MixerContent.state(MixerPartType.CombustionDrive));
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            var d = motor(h, p);
            reset(h, c);
            d.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            ticks(c, c.spinUpTicks());
            c.state().fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            c.state().depositExperienceMb(250);
            ticks(c, 30);
            int progress = c.state().progress();
            h.assertTrue(progress == 30, "Running recipe cadence wrong");
            var fuel = d.saveFuel(h.getLevel().registryAccess());
            fuel.putInt("burnTicks", 0);
            d.loadFuel(fuel, h.getLevel().registryAccess());
            ticks(c, 1);
            state(h, c, MixerOperatingState.STOPPED, 0);
            h.assertTrue(c.state().progress() == progress, "Stop erased recipe progress");
            d.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            ticks(c, c.spinUpTicks() - 1);
            h.assertTrue(
                    c.state().progress() == progress && c.operatingState() == MixerOperatingState.SPINNING_UP,
                    "Restart advanced recipe early");
            ticks(c, 1);
            ticks(c, 10);
            h.assertTrue(c.state().progress() == progress + 10, "Restart lost normal cadence/progress");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cookCycleConsumesOneEighthCoal(GameTestHelper h) {
        MixerGameTests.rig(h);
        var p = O.offset(3, 3, 2);
        h.setBlock(p, MixerContent.state(MixerPartType.CombustionDrive));
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(O.offset(0, 1, 2)))
                    .getMultiblockController()
                    .orElseThrow();
            var d = motor(h, p);
            reset(h, c);
            d.fuel().insertItem(0, new ItemStack(Items.COAL), false);
            ticks(c, c.spinUpTicks());
            int before = d.burnTicks();
            c.state().fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            c.state().depositExperienceMb(250);
            ticks(c, 199);
            h.assertTrue(
                    c.state().progress() == 190 && c.state().amount(2) == 0,
                    "Cook completed early: " + c.state().status(true));
            ticks(c, 1);
            h.assertTrue(
                    c.state().amount(2) == 1000 && before - d.burnTicks() == 200, "Cook cycle did not cost 1/8 coal");
            h.succeed();
        });
    }
}
