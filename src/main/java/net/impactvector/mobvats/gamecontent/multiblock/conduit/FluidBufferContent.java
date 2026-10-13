package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.*;

public final class FluidBufferContent {
    private static final Map<String, DeferredBlock<Block>> BLOCKS = new LinkedHashMap<>();

    static {
        var block = Content.BLOCKS.<Block>register("fluid_buffer", FluidBufferBlock::new);
        BLOCKS.put("fluid_buffer", block);
        Content.ITEMS.register(
                "fluid_buffer", () -> new net.impactvector.mobvats.gamecontent.item.ItemEndpoint(block.get(), true));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidBufferEntity>> ENTITY =
            Content.ENTITIES.register(
                    "fluid_buffer",
                    () -> BlockEntityType.Builder.of(
                                    FluidBufferEntity::new,
                                    BLOCKS.values().stream()
                                            .map(DeferredBlock::get)
                                            .toArray(Block[]::new))
                            .build(null));

    public static void initialize() {}

    public static Block block() {
        return BLOCKS.get("fluid_buffer").get();
    }

    public static net.minecraft.world.level.block.state.BlockState state(ConduitCladding clad) {
        return state(clad, ConduitShell.VAT);
    }

    public static net.minecraft.world.level.block.state.BlockState state(ConduitCladding clad, ConduitShell shell) {
        return block().defaultBlockState().setValue(CladdingMaterial.PROPERTY, CladdingMaterial.of(clad, shell));
    }

    public static java.util.Collection<Block> blocks() {
        return BLOCKS.values().stream().map(DeferredBlock::get).toList();
    }
}
