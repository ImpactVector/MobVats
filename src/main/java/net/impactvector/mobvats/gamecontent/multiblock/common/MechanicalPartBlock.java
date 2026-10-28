package net.impactvector.mobvats.gamecontent.multiblock.common;

import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

/** One registered function, with existing ZeroCore part adapters selected by installed material. */
public class MechanicalPartBlock extends VatPartBlock
        implements it.zerono.mods.zerocore.lib.block.INeighborChangeListener.Notifier {
    public MechanicalPartBlock(VatPartType type) {
        super(type);
        registerDefaultState(defaultBlockState()
                .setValue(CladdingMaterial.PROPERTY, CladdingMaterial.NONE)
                .setValue(PortVisualState.OUTPUT, false));
    }

    public static MechanicalPartBlock create(VatPartType type) {
        return switch (type) {
            case ItemPort, FluidPort, ToolPort, FeedPort, Blender, PullPump, PushPump, FillValve, DrainValve ->
                new DirectionalMechanicalPartBlock(type);
            default -> new MechanicalPartBlock(type);
        };
    }

    public static boolean supported(VatPartType type) {
        return switch (type) {
            case ItemPort,
                    Harness,
                    Sump,
                    FluidPort,
                    ToolPort,
                    FeedPort,
                    Blender,
                    PullPump,
                    PushPump,
                    FillValve,
                    DrainValve,
                    ItemVoid,
                    FluidVoid -> true;
            default -> false;
        };
    }

    public static VatPartType shared(MixerPartType type) {
        return switch (type) {
            case ItemPort -> VatPartType.ItemPort;
            case FluidPort -> VatPartType.FluidPort;
            case PullPump -> VatPartType.PullPump;
            case PushPump -> VatPartType.PushPump;
            case FluidVoid -> VatPartType.FluidVoid;
            default -> null;
        };
    }

    public static MixerPartType mixer(VatPartType type) {
        return switch (type) {
            case ItemPort -> MixerPartType.ItemPort;
            case FluidPort -> MixerPartType.FluidPort;
            case PullPump -> MixerPartType.PullPump;
            case PushPump -> MixerPartType.PushPump;
            case FluidVoid -> MixerPartType.FluidVoid;
            default -> null;
        };
    }

    public static String id(VatPartType type) {
        return supported(type) && type != VatPartType.Harness && type != VatPartType.Sump
                ? type.getSerializedName().substring(4)
                : type.getSerializedName();
    }

    public boolean accepts(CladdingMaterial material) {
        return material.shell() != ConduitShell.MIXER || mixer(getPartType()) != null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CladdingMaterial.PROPERTY, PortVisualState.OUTPUT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        var type = mixer(getPartType());
        return type != null && CladdingMaterial.installed(state).shell() == ConduitShell.MIXER
                ? type.createTileEntity(state, pos)
                : getPartType().createTileEntity(state, pos);
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
        var cladding = CladdingInteraction.apply(stack, state, level, pos, player);
        if (cladding != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return cladding;
        var entity = level.getBlockEntity(pos);
        if (entity instanceof VatBoundaryEntity e && e.configure(player, hand)
                || entity instanceof MixerPumpEntity e2 && e2.configure(player, hand)
                || entity instanceof MixerFluidVoidEntity e3 && e3.configure(player, hand))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (entity instanceof ConfigurableEndpoint port
                && port.endpoint()
                        .configure(
                                player,
                                hand,
                                entity instanceof AbstractVatEntity v
                                        ? v.isMachineAssembled()
                                        : ((AbstractMixerEntity) entity).isMachineAssembled()))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (entity instanceof VatFluidPortEntity fluid && fluid.interact(player, hand))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (entity instanceof MixerFluidPortEntity fluid
                && fluid.isMachineAssembled()
                && FluidContainerInteraction.interact(
                        player, hand, fluid.fluids(), fluid.getIoDirection().isInput()))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (entity instanceof VatToolPortEntity tool) {
            tool.interact(player, hand);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var result = useItemOn(ItemStack.EMPTY, state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) return InteractionResult.sidedSuccess(level.isClientSide);
        var entity = level.getBlockEntity(pos);
        if (entity instanceof VatToolPortEntity tool) return tool.interact(player, InteractionHand.MAIN_HAND);
        if (entity instanceof net.minecraft.world.MenuProvider menu) {
            if (player instanceof net.minecraft.server.level.ServerPlayer server) server.openMenu(menu, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (state.is(old.getBlock())
                && CladdingMaterial.installed(state).shell()
                        != CladdingMaterial.installed(old).shell()) refreshAdapter(level, pos, state);
        ConduitStructure.changed(level, pos, true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!state.equals(next)) ConduitStructure.changed(level, pos, true);
    }
    /** Palette/state edits preserve the adapter. Only an explicit family change changes ownership type. */
    public void refreshAdapter(Level level, BlockPos pos, BlockState state) {
        var old = level.getBlockEntity(pos);
        boolean mixer = CladdingMaterial.installed(state).shell() == ConduitShell.MIXER;
        if (old == null || mixer == (old instanceof AbstractMixerEntity)) return;
        var retained = MechanicalState.capture(old);
        level.removeBlockEntity(pos);
        var next = newBlockEntity(pos, state);
        level.setBlockEntity(next);
        MechanicalState.restore(next, retained);
        level.invalidateCapabilities(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
