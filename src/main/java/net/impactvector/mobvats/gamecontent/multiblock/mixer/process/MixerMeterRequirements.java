package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Immutable, transient batch constraints. No acquisition, recipe ranking or persistence is performed here. */
public record MixerMeterRequirements(
        List<ItemMeterRequirement> itemRequirements, List<FluidMeterRequirement> fluidRequirements) {
    public static final MixerMeterRequirements NONE = new MixerMeterRequirements(List.of(), List.of());

    public MixerMeterRequirements {
        itemRequirements = List.copyOf(itemRequirements);
        fluidRequirements = List.copyOf(fluidRequirements);
    }

    /** Each meter owns a whole ingredient slot, even when that slot draws from several physical stacks. */
    public boolean matches(
            MixerRecipeDefinition.Strategy strategy, List<List<ItemStack>> items, List<List<FluidStack>> fluids) {
        return assign(itemRequirements.size(), strategy.items().size(), (m, i) -> {
                    var meter = itemRequirements.get(m);
                    return meter.exactCount() == strategy.items().get(i).amount()
                            && !items.get(i).isEmpty()
                            && items.get(i).stream().allMatch(meter::matches);
                })
                && assign(fluidRequirements.size(), strategy.fluids().size(), (m, i) -> {
                    var meter = fluidRequirements.get(m);
                    return meter.exactAmountMb() == strategy.fluids().get(i).amount()
                            && !fluids.get(i).isEmpty()
                            && fluids.get(i).stream().allMatch(meter::matches);
                });
    }

    private static boolean assign(int meters, int slots, BiPredicate<Integer, Integer> matches) {
        if (meters > slots) return false;
        var owners = new int[slots];
        Arrays.fill(owners, -1);
        for (int m = 0; m < meters; m++) if (!assign(m, owners, new boolean[slots], matches)) return false;
        return true;
    }

    // Augmenting paths allow overlapping filters to exchange slots rather than depending on meter order.
    private static boolean assign(int meter, int[] owners, boolean[] visited, BiPredicate<Integer, Integer> matches) {
        for (int i = 0; i < owners.length; i++) {
            if (visited[i] || !matches.test(meter, i)) continue;
            visited[i] = true;
            if (owners[i] < 0 || assign(owners[i], owners, visited, matches)) {
                owners[i] = meter;
                return true;
            }
        }
        return false;
    }
}
