package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Preflight a complete container operation before standard NeoForge transformation/stowing. */
public final class FluidContainerInteraction {
    public static boolean interact(Player player, InteractionHand hand, IFluidHandler network, boolean input) {
        var held = player.getItemInHand(hand);
        var capability = FluidUtil.getFluidHandler(held.copyWithCount(1));
        if (capability.isEmpty()) return false;
        if (player.level().isClientSide) return true;
        var container = capability.get();
        int amount;
        IFluidHandler transfer = network;
        if (input) {
            var offered = container.drain(Integer.MAX_VALUE, SIMULATE);
            amount = offered.getAmount();
            if (amount == 0 || network.fill(offered, SIMULATE) != amount) return true;
        } else {
            var candidates = new java.util.ArrayList<FluidStack>();
            for (int i = 0; i < network.getTanks(); i++) {
                var s = network.getFluidInTank(i);
                if (!s.isEmpty()) candidates.add(s);
            }
            candidates.sort(java.util.Comparator.comparing(s -> net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(s.getFluid())
                    .toString()));
            FluidStack selected = FluidStack.EMPTY;
            amount = 0;
            for (var available : candidates) {
                int intended = container.fill(available.copyWithAmount(Integer.MAX_VALUE), SIMULATE);
                if (intended > 0
                        && network.drain(available.copyWithAmount(intended), SIMULATE)
                                        .getAmount()
                                == intended
                        && preservesFill(held, sourceFor(network, available), available, intended)) {
                    selected = available;
                    amount = intended;
                    break;
                }
            }
            if (selected.isEmpty()) return true;
            transfer = sourceFor(network, selected.copy());
        }
        var inventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        var result = input
                ? FluidUtil.tryEmptyContainerAndStow(held, network, inventory, amount, player, true)
                : FluidUtil.tryFillContainerAndStow(held, transfer, inventory, amount, player, true);
        if (result.isSuccess()) player.setItemInHand(hand, result.getResult());
        return true; // Rejected containers must not fall through to world bucket placement.
    }
    /** Inspect the transformed copy before spending source fluid; reject third-party containers that discard components. */
    public static boolean preservesFill(
            net.minecraft.world.item.ItemStack held, IFluidHandler source, FluidStack identity, int amount) {
        var trial = FluidUtil.tryFillContainer(held, source, amount, null, false);
        return trial.isSuccess()
                && FluidUtil.getFluidContained(trial.getResult())
                        .filter(s -> FluidStack.isSameFluidSameComponents(s, identity))
                        .isPresent();
    }

    private static IFluidHandler sourceFor(IFluidHandler network, FluidStack selected) {
        return new IFluidHandler() {
            public int getTanks() {
                return network.getTanks();
            }

            public FluidStack getFluidInTank(int i) {
                var s = network.getFluidInTank(i);
                return FluidStack.isSameFluidSameComponents(s, selected) ? s : FluidStack.EMPTY;
            }

            public int getTankCapacity(int i) {
                return network.getTankCapacity(i);
            }

            public boolean isFluidValid(int i, FluidStack s) {
                return false;
            }

            public int fill(FluidStack s, FluidAction a) {
                return 0;
            }

            public FluidStack drain(FluidStack s, FluidAction a) {
                return FluidStack.isSameFluidSameComponents(s, selected) ? network.drain(s, a) : FluidStack.EMPTY;
            }

            public FluidStack drain(int n, FluidAction a) {
                return network.drain(selected.copyWithAmount(n), a);
            }
        };
    }

    private FluidContainerInteraction() {}
}
