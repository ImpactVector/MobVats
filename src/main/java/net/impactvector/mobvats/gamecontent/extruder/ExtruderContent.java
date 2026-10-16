package net.impactvector.mobvats.gamecontent.extruder;

import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.*;

@EventBusSubscriber(modid = MobVats.MOD_ID)
public final class ExtruderContent {
    public static final DeferredBlock<ExtruderBlock> BLOCK = Content.BLOCKS.register("extruder", ExtruderBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtruderEntity>> ENTITY =
            Content.ENTITIES.register(
                    "extruder",
                    () -> BlockEntityType.Builder.of(ExtruderEntity::new, BLOCK.get())
                            .build(null));

    public static void initialize() {
        Content.ITEMS.registerSimpleBlockItem(BLOCK);
    }

    @SubscribeEvent
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ENTITY.get(),
                (entity, side) -> side == Direction.DOWN ? entity.output() : null);
    }

    private ExtruderContent() {}
}
