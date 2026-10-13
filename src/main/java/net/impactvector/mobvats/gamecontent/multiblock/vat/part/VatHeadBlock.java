package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Placement orientation controls the Face artwork and formed specimen, never the substrate scan. */
public final class VatHeadBlock extends VatConstructionBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public VatHeadBlock() {
        super(VatPartType.Head);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(
            net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, BlockState state) {
        var stack = new net.minecraft.world.item.ItemStack(this);
        if (level.getBlockEntity(pos) instanceof VatHeadEntity head && head.getIdentity() != null) {
            var saved = head.saveWithoutMetadata(level.registryAccess());
            var tag = saved.getCompound("zcvase_payload");
            // Clone specimen data, not controller ownership, presentation or process identity.
            var specimen = new net.minecraft.nbt.CompoundTag();
            for (var key : java.util.List.of("entity", "geometry", "specimen_version", "specimen_state", "lifeStage"))
                if (tag.contains(key)) specimen.put(key, tag.get(key).copy());
            var itemData = new net.minecraft.nbt.CompoundTag();
            itemData.putInt("zcvase_version", saved.getInt("zcvase_version"));
            itemData.put("zcvase_payload", specimen);
            itemData.putString(
                    "id",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                            .getKey(head.getType())
                            .toString());
            stack.set(
                    net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                    net.minecraft.world.item.component.CustomData.of(itemData));
        }
        return stack;
    }

    @Override
    public void setPlacedBy(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            BlockState state,
            net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof VatHeadEntity head)
            head.rotateGeometry(state.getValue(FACING));
    }

    @Override
    protected void onPlace(
            BlockState state,
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            BlockState previous,
            boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (!level.isClientSide && previous.is(this) && previous.getValue(FACING) != state.getValue(FACING))
            level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(
            BlockState state,
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.util.RandomSource random) {
        // Defer until vanilla has updated the retained block entity's state.
        if (level.getBlockEntity(pos) instanceof VatHeadEntity head) {
            head.rotateGeometry(state.getValue(FACING));
            head.getMultiblockController().ifPresent(controller -> controller.specimenStateChanged(head));
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
