package net.impactvector.mobvats.gamecontent.multiblock.vat.part;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class VatPortBlock extends VatPartBlock implements it.zerono.mods.zerocore.lib.block.INeighborChangeListener.Notifier {
    public VatPortBlock(VatPartType type) { super(type); registerDefaultState(defaultBlockState().setValue(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT,false)); }
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var part=level.getBlockEntity(pos);
        if(part instanceof AbstractVatPortEntity configured && configured.configure(player,InteractionHand.MAIN_HAND))return InteractionResult.sidedSuccess(level.isClientSide);
        if (part instanceof VatToolPortEntity tool) return tool.interact(player,InteractionHand.MAIN_HAND);
        if (part instanceof AbstractVatPortEntity port) {
            if (player instanceof ServerPlayer server) server.openMenu(port,pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state,level,pos,player,hit);
    }
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(level.getBlockEntity(pos) instanceof AbstractVatPortEntity configured && configured.configure(player,hand))return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if(level.getBlockEntity(pos) instanceof VatFluidPortEntity fluid && fluid.interact(player,hand))return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof VatToolPortEntity tool) {
            tool.interact(player,hand); return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Direction retains the existing menu interaction.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT);
    }
    @Override protected java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var entity=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        var drops=super.getDrops(state,builder);
        for(var stack:drops)if(stack.is(asItem()))net.impactvector.mobvats.gamecontent.item.EndpointState.capture(stack,entity);
        return drops;
    }
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,BlockPos pos,BlockState state) {
        return net.impactvector.mobvats.gamecontent.item.EndpointState.capture(new ItemStack(this),level.getBlockEntity(pos));
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide)net.impactvector.mobvats.gamecontent.item.EndpointState.restore(stack,level.getBlockEntity(pos));
    }
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            var part=level.getBlockEntity(pos);
            var stack=part instanceof VatItemPortEntity item ? item.staging().getStackInSlot(0).copy()
                : part instanceof VatToolPortEntity tool ? tool.heldItem() : ItemStack.EMPTY;
            if (!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);
            if(part instanceof VatToolPortEntity tool)for(var result:tool.results().snapshot())net.minecraft.world.Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,result);
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
