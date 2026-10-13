package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Physical storage endpoint, deliberately distinct from a conduit node. */
public final class FluidBufferBlock extends BaseEntityBlock
        implements net.impactvector.mobvats.gamecontent.multiblock.vat.VatDisplacement.Provider {
    @Override
    public int vatDisplacement(Level level, BlockPos pos) {
        return 1000;
    }

    public FluidBufferBlock() {
        super(Properties.of().strength(2.5f).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(CladdingMaterial.PROPERTY, CladdingMaterial.NONE));
    }

    public ConduitCladding cladding(BlockState state) {
        return CladdingMaterial.installed(state).cladding();
    }

    public ConduitShell shell(BlockState state) {
        return CladdingMaterial.installed(state).shell();
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new FluidBufferBlock());
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CladdingMaterial.PROPERTY);
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidBufferEntity(pos, state);
    }

    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moving) {
        super.neighborChanged(state, level, pos, block, neighbor, moving);
        FluidBufferPresentation.changed(level, pos);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        var cladding = CladdingInteraction.apply(stack, state, level, pos, player);
        if (cladding != net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return cladding;
        return level.getBlockEntity(pos) instanceof FluidBufferEntity buffer && buffer.interact(player, hand)
                ? net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide)
                : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(
            BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var entity = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        var drops = super.getDrops(state, builder);
        for (var stack : drops)
            if (stack.is(asItem())) net.impactvector.mobvats.gamecontent.item.EndpointState.capture(stack, entity);
        return drops;
    }

    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(
            net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return net.impactvector.mobvats.gamecontent.item.EndpointState.capture(
                new net.minecraft.world.item.ItemStack(this), level.getBlockEntity(pos));
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide)
            net.impactvector.mobvats.gamecontent.item.EndpointState.restore(stack, level.getBlockEntity(pos));
    }

    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        ConduitStructure.changed(level, pos, true);
        FluidBufferPresentation.changed(level, pos);
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        ConduitStructure.changed(level, pos, true);
        FluidBufferPresentation.changed(level, pos);
    }
}
