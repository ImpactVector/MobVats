package net.impactvector.mobvats.gamecontent.creative;

import com.mojang.serialization.MapCodec;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CreativeDeviceBlock extends BaseEntityBlock {
    private final CreativeDeviceType type;
    public CreativeDeviceBlock(CreativeDeviceType type){super(Properties.of().strength(2.5f));this.type=type;}
    public CreativeDeviceType type(){return type;}
    protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new CreativeDeviceBlock(type));}
    public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CreativeEndpointEntity(pos,state);}
    protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.getBlockEntity(pos) instanceof CreativeEndpointEntity endpoint){endpoint.interact(player,hand);return ItemInteractionResult.sidedSuccess(level.isClientSide);}
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.getBlockEntity(pos) instanceof CreativeEndpointEntity endpoint){endpoint.interact(player,InteractionHand.MAIN_HAND);return InteractionResult.sidedSuccess(level.isClientSide);}
        return InteractionResult.PASS;
    }
    protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){
        var drops=super.getDrops(state,builder);var entity=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        for(var stack:drops)if(stack.is(asItem()))EndpointState.capture(stack,entity);return drops;
    }
    public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return EndpointState.capture(new ItemStack(this),level.getBlockEntity(pos));}
    public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide)EndpointState.restore(stack,level.getBlockEntity(pos));}
}
