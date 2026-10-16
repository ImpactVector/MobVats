package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class MixerRedstoneGameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1), C = O.offset(0, 1, 2), D = O.offset(3, 3, 2);

    static void power(GameTestHelper h, BlockPos adjacent, boolean indirect) {
        h.setBlock(adjacent, indirect ? Blocks.STONE : Blocks.REDSTONE_BLOCK);
        if (indirect)
            h.setBlock(
                    adjacent.above(),
                    Blocks.LEVER
                            .defaultBlockState()
                            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                            .setValue(LeverBlock.POWERED, true));
    }

    static void unpower(GameTestHelper h, BlockPos adjacent) {
        h.setBlock(adjacent.above(), Blocks.AIR);
        h.setBlock(adjacent, Blocks.AIR);
    }

    private static void suspension(GameTestHelper h, MixerPartType type, boolean controller) {
        MixerGameTests.rig(h);
        h.setBlock(D, MixerContent.state(type));
        h.runAtTickTime(35, () -> {
            var c = ((MixerControllerEntity) h.getBlockEntity(C))
                    .getMultiblockController()
                    .orElseThrow();
            var drive = (AbstractMixerDriveEntity) h.getBlockEntity(D);
            var registries = h.getLevel().registryAccess();
            // Creative motors already execute during fixture assembly. Start every provider at the same epoch.
            c.syncDataFrom(
                    c.syncDataTo(new CompoundTag(), registries, SyncReason.FullSync), registries, SyncReason.FullSync);
            if (drive instanceof MixerFeDriveEntity fe) fe.energy().receiveEnergy(MixerFeDriveEntity.CAPACITY, false);
            if (drive instanceof MixerCombustionDriveEntity combustion)
                combustion.fuel().insertItem(0, new ItemStack(Items.COAL, 8), false);
            MixerCombustionGameTests.ticks(c, c.spinUpTicks());
            c.state().fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            c.state().depositExperienceMb(250);
            MixerCombustionGameTests.ticks(c, 37);
            h.assertTrue(c.state().progress() == 30, "Unpowered baseline did not progress");
            var adjacent = controller ? C.west() : D.above();
            for (boolean indirect : new boolean[] {false, true}) {
                var before = drive.syncDataTo(new CompoundTag(), registries, SyncReason.FullSync);
                int progress = c.state().progress(), startup = c.startupTicks();
                power(h, adjacent, indirect);
                h.assertTrue(h.getLevel().hasNeighborSignal(h.absolutePos(controller ? C : D)), "Missing power");
                MixerCombustionGameTests.ticks(c, 80);
                h.assertTrue(
                        c.isMachineActive() && c.state().progress() == progress && c.startupTicks() == startup,
                        "Signal changed preference, progress, or rotor epoch");
                h.assertTrue(
                        drive.syncDataTo(new CompoundTag(), registries, SyncReason.FullSync)
                                .equals(before),
                        "Inhibited drive consumed resources or changed state");
                drive.syncDataFrom(before, registries, SyncReason.FullSync);
                MixerCombustionGameTests.ticks(c, 20);
                h.assertTrue(c.state().progress() == progress, "Reload bypassed inhibition");
                if (controller) {
                    var input = h.getLevel()
                            .getCapability(
                                    Capabilities.FluidHandler.BLOCK, h.absolutePos(O.offset(2, 1, 0)), Direction.NORTH);
                    h.assertTrue(
                            input != null && input.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 100,
                            "Controller inhibition blocked connected resources");
                }
                unpower(h, adjacent);
                MixerCombustionGameTests.ticks(c, 3);
                h.assertTrue(c.state().progress() == progress + 10, "Pause lost cadence or resumed with catch-up");
                MixerCombustionGameTests.ticks(c, 7);
            }
            c.setMachineActive(false);
            power(h, adjacent, false);
            MixerCombustionGameTests.ticks(c, 10);
            unpower(h, adjacent);
            h.assertTrue(!c.isMachineActive(), "Redstone overwrote operator-disabled preference");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void controllerPauseResumeAndTransport(GameTestHelper h) {
        suspension(h, MixerPartType.FeDrive, true);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void feDrivePauseResume(GameTestHelper h) {
        suspension(h, MixerPartType.FeDrive, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void combustionDrivePauseResume(GameTestHelper h) {
        suspension(h, MixerPartType.CombustionDrive, false);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void creativeDrivePauseResume(GameTestHelper h) {
        suspension(h, MixerPartType.Drive, false);
    }
}
