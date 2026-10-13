package net.impactvector.mobvats.gamecontent.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class ItemEndpoint extends BlockItem {
    private final boolean buffer;

    public ItemEndpoint(Block block, boolean buffer) {
        super(block, new Properties());
        this.buffer = buffer;
    }

    @Override
    public Component getName(ItemStack stack) {
        var state = EndpointState.read(stack);
        var filter = state.getString("filter");
        return buffer && !filter.isEmpty()
                ? Component.translatable("item.mobvats.filtered_buffer", fluidName(filter))
                : super.getName(stack);
    }

    private static Component fluidName(String filter) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(filter);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id))
            return Component.literal(filter);
        return new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id), 1)
                .getHoverName();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var state = EndpointState.read(stack);
        var filter = state.getString("filter");
        tooltip.add(Component.translatable("tooltip.mobvats.endpoint_filter", filter.isEmpty() ? "â€”" : filter));
        if (buffer) {
            var tank = state.getCompound("tank");
            tooltip.add(Component.translatable(
                    "tooltip.mobvats.buffer_contents", tank.getCompound("Fluid").getInt("amount"), 4000));
        } else
            tooltip.add(Component.translatable(
                    "tooltip.mobvats.endpoint_direction", state.getBoolean("output") ? "Output" : "Input"));
    }
}
