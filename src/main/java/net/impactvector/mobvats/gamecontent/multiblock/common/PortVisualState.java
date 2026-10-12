package net.impactvector.mobvats.gamecontent.multiblock.common;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Render-only projection of persisted endpoint direction; no capability or transfer policy. */
public final class PortVisualState {
    public static final BooleanProperty OUTPUT = BooleanProperty.create("port_output");
    private PortVisualState() {}
    public static void refresh(BlockEntity entity, ConfigurableEndpoint endpoint) {
        var level = entity.getLevel();
        if (level == null || level.isClientSide) return;
        var state = level.getBlockState(entity.getBlockPos());
        if (state.hasProperty(OUTPUT) && state.getValue(OUTPUT) != endpoint.getIoDirection().isOutput())
            level.setBlock(entity.getBlockPos(), state.setValue(OUTPUT, endpoint.getIoDirection().isOutput()), 3);
    }
}
