package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.function.Predicate;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatTopology;
import net.minecraft.world.item.ItemStack;

/** Reusable acquisition and copied tool transformation; compatible use remains definition-owned. */
public final class ProcessTools {
    public static boolean acquire(
            ProcessContext context, VatTopology.Assignment route, Predicate<ItemStack> predicate) {
        var tool = context.tool(route);
        if (tool == null || !tool.heldItem().isEmpty()) return false;
        var definition = new net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.Strategy(
                "tool", net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.item(predicate, 1));
        return acquire(context, route, java.util.List.of(definition));
    }

    public static boolean acquire(
            ProcessContext context,
            VatTopology.Assignment route,
            java.util.List<net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.Strategy>
                    definitions) {
        var tool = context.tool(route);
        if (tool == null || !tool.heldItem().isEmpty()) return false;
        var acquisition = context.controller()
                .resourceRouting()
                .prepare(tool, tool.demandConfiguration().plan(definitions))
                .orElse(null);
        if (acquisition == null) return false;
        var held = acquisition.items().getFirst();
        return acquisition.commit(() -> tool.setHeldItem(held), () -> tool.setHeldItem(ItemStack.EMPTY));
    }

    public static ItemStack transform(ItemStack tool, java.util.function.UnaryOperator<ItemStack> strategy) {
        return strategy.apply(tool.copy());
    }

    private ProcessTools() {}
}
