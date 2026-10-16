package net.impactvector.mobvats.gamecontent.product;

import java.util.List;
import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Standalone products; fluid forming is provided by the Extruder. */
public final class ProductContent {
    public static final DeferredItem<Item> RAW_MEAT =
            Content.ITEMS.registerSimpleItem("raw_meat", new Item.Properties().food(Foods.BEEF));
    public static final DeferredItem<Item> COOKED_MEAT =
            Content.ITEMS.registerSimpleItem("cooked_meat", new Item.Properties().food(Foods.COOKED_BEEF));
    // Inherit the platform's complete useOn path, including growth hooks and particles.
    public static final DeferredItem<BoneMealItem> FERTILIZER =
            Content.ITEMS.register("fertilizer", () -> new BoneMealItem(new Item.Properties()));
    public static final DeferredBlock<Block> MEAT_BLOCK = Content.BLOCKS.registerSimpleBlock(
            "meat_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.6F)
                    .sound(SoundType.SLIME_BLOCK));
    public static final DeferredBlock<Block> WASTE_BLOCK = Content.BLOCKS.registerSimpleBlock(
            "waste_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.6F)
                    .sound(SoundType.MUD));

    static {
        Content.ITEMS.registerSimpleBlockItem(MEAT_BLOCK);
        Content.ITEMS.registerSimpleBlockItem(WASTE_BLOCK);
    }

    public static void initialize() {}

    public static List<Item> items() {
        return List.of(
                RAW_MEAT.get(),
                COOKED_MEAT.get(),
                FERTILIZER.get(),
                MEAT_BLOCK.get().asItem(),
                WASTE_BLOCK.get().asItem());
    }

    private ProductContent() {}
}
