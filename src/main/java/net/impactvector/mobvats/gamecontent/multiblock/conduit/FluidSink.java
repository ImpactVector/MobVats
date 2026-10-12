package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;

/** Explicit terminal disposal, separate from stores and normal production routing. */
public interface FluidSink {
    BlockPos getBlockPos();
    boolean isRemoved();
    boolean accepts(FluidStack stack);
    void consumed();
    default void consumed(int amount){if(amount>0)consumed();}
}
