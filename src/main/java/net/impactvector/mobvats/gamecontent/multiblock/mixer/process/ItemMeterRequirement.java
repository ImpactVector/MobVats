package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import java.util.List;
import java.util.Objects;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.minecraft.world.item.ItemStack;

/** One sampled item identity and the exact consumption of one recipe ingredient slot. */
public record ItemMeterRequirement(String filter, int exactCount) {
    public ItemMeterRequirement {
        Objects.requireNonNull(filter);
        if (filter.startsWith("#") || exactCount <= 0)
            throw new IllegalArgumentException("Item meter requires one exact filter and a positive count");
        ResourceSelectors.items(List.of(filter));
    }

    public static ItemMeterRequirement sampled(ItemStack sample, int exactCount) {
        if (sample.isEmpty()) throw new IllegalArgumentException("Empty item sample");
        return new ItemMeterRequirement(EndpointState.itemId(sample), exactCount);
    }

    public boolean matches(ItemStack stack) {
        return ResourceSelectors.items(List.of(filter)).test(stack);
    }
}
