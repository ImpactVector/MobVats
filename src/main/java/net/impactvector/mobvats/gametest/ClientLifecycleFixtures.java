package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Dedicated opt-in client acceptance fixtures; never shipped. */
public final class ClientLifecycleFixtures {
    public static void setup(ServerLevel level) {
        var origin = new BlockPos(-6, -60, 0);
        MachineFixtures.mixer(level::setBlockAndUpdate, origin, 5, 4, 5, 2, 2, true);
        level.setBlockAndUpdate(
                origin.offset(0, 2, 0), FluidBufferContent.state(ConduitCladding.GLASS, ConduitShell.MIXER));
        var buffer = (FluidBufferEntity) level.getBlockEntity(origin.offset(0, 2, 0));
        buffer.tank.setFluid(new FluidStack(Fluids.WATER, 2000));
        level.setBlockAndUpdate(
                origin.offset(0, 2, 1),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.GLASS, ConduitShell.MIXER)
                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
    }

    private ClientLifecycleFixtures() {}
}
