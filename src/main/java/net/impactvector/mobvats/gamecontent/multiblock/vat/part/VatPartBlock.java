package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.lib.block.multiblock.MultiblockPartBlock;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class VatPartBlock extends MultiblockPartBlock<MultiblockVat, VatPartType> {
    public VatPartBlock(VatPartType type) {
        super(MultiblockPartProperties.create(type, properties(type)));
    }

    @Override
    protected java.util.List<ItemStack> getDrops(
            BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var entity = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        var drops = super.getDrops(state, builder);
        for (var stack : drops)
            if (stack.is(asItem())) net.impactvector.mobvats.gamecontent.item.EndpointState.capture(stack, entity);
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return net.impactvector.mobvats.gamecontent.item.EndpointState.capture(
                new ItemStack(this), level.getBlockEntity(pos));
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.entity.LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide)
            net.impactvector.mobvats.gamecontent.item.EndpointState.restore(stack, level.getBlockEntity(pos));
    }

    private static BlockBehaviour.Properties properties(VatPartType type) {
        var p = BlockBehaviour.Properties.of().strength(2.5f);
        return net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.supported(type)
                        || type == VatPartType.Glass
                        || type == VatPartType.Head
                        || type == VatPartType.Flesh
                ? p.noOcclusion()
                : p;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof VatControllerEntity controller) {
            if (player.isCrouching()) return super.useWithoutItem(state, level, pos, player, hit);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                serverPlayer.openMenu(controller, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.is(Items.REDSTONE_TORCH)
                && player.isCrouching()
                && level.getBlockEntity(pos) instanceof VatControllerEntity controller) {
            if (!level.isClientSide)
                controller
                        .getMultiblockController()
                        .filter(MultiblockVat::isAssembled)
                        .ifPresent(c -> {
                            c.toggleMachineActive();
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.literal(
                                            c.isMachineActive() ? "Vat enabled" : "Vat disabled"),
                                    true);
                        });
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Content.ESSENCE.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
