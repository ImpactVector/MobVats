package net.impactvector.mobvats.gamecontent.item;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
public final class ItemBoundary extends BlockItem {
    public ItemBoundary(Block block){super(block,new Properties());}
    public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);String filter=EndpointState.read(stack).getString("filter");tooltip.add(Component.translatable("tooltip.mobvats.endpoint_filter",filter.isEmpty()?"â€”":filter));}
}
