package net.impactvector.mobvats.gamecontent.item;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingInteraction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Sneaking normally bypasses block interaction; casing items explicitly install before placement. */
public final class CladdingBlockItem extends BlockItem {
    public CladdingBlockItem(Block block){super(block,new Properties());}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();
        if(player!=null&&player.isShiftKeyDown()){
            var result=CladdingInteraction.apply(context.getItemInHand(),context.getLevel().getBlockState(context.getClickedPos()),context.getLevel(),context.getClickedPos(),player);
            if(result==ItemInteractionResult.FAIL)return InteractionResult.FAIL;
            if(result.consumesAction())return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
}
