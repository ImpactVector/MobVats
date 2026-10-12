package net.impactvector.mobvats.gamecontent.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

public class ItemSyringe extends Item {
    public ItemSyringe(Properties p) { super(p); }
    @Override public InteractionResult interactLivingEntity(ItemStack syringe, Player player, LivingEntity target, InteractionHand hand) {
        if(player.level().isClientSide)return InteractionResult.SUCCESS;
        var result=CaptureTransaction.capture(syringe,player,target);
        if(!result.success()){player.displayClientMessage(Component.literal(result.diagnostic()),true);return InteractionResult.FAIL;}
        return InteractionResult.CONSUME;
    }
}
