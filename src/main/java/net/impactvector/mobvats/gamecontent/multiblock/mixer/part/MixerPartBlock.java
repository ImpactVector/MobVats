package net.impactvector.mobvats.gamecontent.multiblock.mixer.part;

import it.zerono.mods.zerocore.lib.block.multiblock.MultiblockPartBlock;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class MixerPartBlock extends MultiblockPartBlock<MultiblockMixer, MixerPartType>
        implements it.zerono.mods.zerocore.lib.block.INeighborChangeListener.Notifier {
    private final MixerPartType type;

    public MixerPartBlock(MixerPartType type) {
        super(MultiblockPartProperties.create(
                type,
                type == MixerPartType.Glass || type == MixerPartType.Shaft
                        ? BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()
                        : BlockBehaviour.Properties.of().strength(2.5f)));
        this.type = type;
        registerDefaultState(defaultBlockState()
                .setValue(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT, false));
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state,
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        double half = MixerVesselGeometry.SHAFT_WIDTH_PIXELS / 2.0;
        return type == MixerPartType.Shaft
                ? net.minecraft.world.level.block.Block.box(8 - half, 0, 8 - half, 8 + half, 16, 8 + half)
                : super.getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<
                            net.minecraft.world.level.block.Block, BlockState>
                    builder) {
        super.createBlockStateDefinition(builder);
        builder.add(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT);
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
                && level.getBlockEntity(pos) instanceof MixerControllerEntity controller) {
            if (!level.isClientSide)
                controller
                        .getMultiblockController()
                        .filter(MultiblockMixer::isAssembled)
                        .ifPresent(c -> {
                            c.toggleMachineActive();
                            player.displayClientMessage(
                                    Component.literal(c.isMachineActive() ? "Mixer enabled" : "Mixer disabled"), true);
                        });
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof MixerBlockerEntity blocker && blocker.configure(player, hand))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof AbstractMixerMeterEntity meter && meter.configure(player, hand))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof MixerPumpEntity pump && pump.configure(player, hand))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof MixerFluidVoidEntity sink && sink.configure(player, hand))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof MixerCombustionDriveEntity drive
                && MixerCombustionDriveEntity.fuelTicks(stack) > 0) {
            if (!level.isClientSide) {
                var remaining = drive.fuel().insertItem(0, stack.copy(), false);
                if (!player.getAbilities().instabuild) player.setItemInHand(hand, remaining);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof MixerPortEntity port) {
            if (port.configure(player, hand)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
            if (port instanceof MixerFluidPortEntity
                    && port.isMachineAssembled()
                    && net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidContainerInteraction.interact(
                            player, hand, port.fluids(), port.getIoDirection().isInput()))
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.WATER_BUCKET)
                && level.getBlockEntity(pos) instanceof AbstractMixerEntity part
                && part.getPartType() == MixerPartType.Controller) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            var mixer = part.getMultiblockController().filter(MultiblockMixer::isAssembled);
            if (mixer.isPresent() && mixer.get().waterBucket(player, hand)) return ItemInteractionResult.SUCCESS;
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MixerBlockerEntity blocker
                && blocker.configure(player, InteractionHand.MAIN_HAND))
            return InteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof AbstractMixerMeterEntity meter) {
            if (meter.configure(player, InteractionHand.MAIN_HAND))
                return InteractionResult.sidedSuccess(level.isClientSide);
            if (player instanceof ServerPlayer server) server.openMenu(meter, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof MixerPumpEntity pump
                && pump.configure(player, InteractionHand.MAIN_HAND))
            return InteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof MixerFluidVoidEntity sink
                && sink.configure(player, InteractionHand.MAIN_HAND))
            return InteractionResult.sidedSuccess(level.isClientSide);
        if (level.getBlockEntity(pos) instanceof MixerPortEntity port) {
            if (port.configure(player, InteractionHand.MAIN_HAND))
                return InteractionResult.sidedSuccess(level.isClientSide);
            if (player instanceof ServerPlayer server) server.openMenu(port, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof AbstractMixerEntity part) {
            var controller = part.getMultiblockController();
            if (controller.isPresent()) {
                var c = controller.get();
                if (player.isCrouching()
                        && part.getPartType() == MixerPartType.Controller
                        && player instanceof ServerPlayer server) c.depositPlayerXp(server);
                player.displayClientMessage(c.diagnosticReport(), false);
            } else player.displayClientMessage(Component.literal("Mixer unformed"), false);
        }
        return InteractionResult.CONSUME;
    }
}
