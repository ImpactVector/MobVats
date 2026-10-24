package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import net.impactvector.mobvats.MobVats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;

/** Standard automation access to the same filtered cluster used by manual containers. */
@EventBusSubscriber(modid = MobVats.MOD_ID)
public final class FluidBufferCapabilities {
    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                FluidBufferContent.ENTITY.get(),
                (entity, side) -> new IFluidHandler() {
                    private IFluidHandler current() {
                        var level = entity.getLevel();
                        var pos = entity.getBlockPos();
                        return level != null
                                        && entity.routingAvailable()
                                        && level.hasChunkAt(pos)
                                        && net.impactvector.mobvats.gamecontent.multiblock.common.LoadedWorld
                                                        .blockEntity(level, pos)
                                                == entity
                                ? entity.interactionCluster().tank()
                                : EmptyFluidHandler.INSTANCE;
                    }

                    public int getTanks() {
                        return current().getTanks();
                    }

                    public FluidStack getFluidInTank(int tank) {
                        return current().getFluidInTank(tank).copy();
                    }

                    public int getTankCapacity(int tank) {
                        return current().getTankCapacity(tank);
                    }

                    public boolean isFluidValid(int tank, FluidStack stack) {
                        return current().isFluidValid(tank, stack);
                    }

                    public int fill(FluidStack stack, FluidAction action) {
                        return current().fill(stack, action);
                    }

                    public FluidStack drain(FluidStack stack, FluidAction action) {
                        return current().drain(stack, action);
                    }

                    public FluidStack drain(int amount, FluidAction action) {
                        return current().drain(amount, action);
                    }
                });
    }

    private FluidBufferCapabilities() {}
}
