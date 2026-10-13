package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Inventory forms share a block, but carry their intended placement mode. */
public final class ConduitBlockItem extends BlockItem {
    private final ConduitMode mode;

    public ConduitBlockItem(Block block, ConduitMode mode) {
        super(block, new Item.Properties());
        this.mode = mode;
    }

    public ConduitMode mode() {
        return mode;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        var state = getBlock().getStateForPlacement(context);
        if (state == null) return null;
        state = state.setValue(ConduitBlock.MODE, mode);
        return canPlace(context, state) ? state : null;
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        // Keep Block.asItem() stable regardless of item registration order.
        if (mode == ConduitMode.TRUNK) super.registerBlocks(map, item);
    }

    @Override
    public String getDescriptionId() {
        return "item.mobvats." + ConduitContent.name(((ConduitBlock) getBlock()).resource())
                + (mode == ConduitMode.TAP ? "_tap" : "");
    }
}
