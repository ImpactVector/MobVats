package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class VatBoundaryBlock extends VatPartBlock {
    public VatBoundaryBlock(VatPartType type) {
        super(type);
    }

    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return level.getBlockEntity(pos) instanceof VatBoundaryEntity e
                        && e.configure(player, InteractionHand.MAIN_HAND)
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return level.getBlockEntity(pos) instanceof VatBoundaryEntity e && e.configure(player, hand)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
