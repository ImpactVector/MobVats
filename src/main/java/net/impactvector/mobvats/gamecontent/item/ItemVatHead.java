package net.impactvector.mobvats.gamecontent.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class ItemVatHead extends BlockItem {
    public ItemVatHead(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return SpecimenPresentation.displayName(stack, true, super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        SpecimenPresentation.appendTooltip(stack, true, tooltip, flag);
    }
}
