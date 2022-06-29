package net.impactvector.mobvats.block;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.block.custom.ModFluidBlock;
import net.impactvector.mobvats.fluid.ModFluids;
import net.impactvector.mobvats.item.ModItemGroup;
import net.minecraft.block.Block;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModBlocks {

    public static final Block MEAT_BLOCK = registerBlock("meat_block",
            new Block(FabricBlockSettings.of(Material.ORGANIC_PRODUCT).strength(4f)),
            ModItemGroup.MEAT);

    public static final Block MEAT_FLUID_BLOCK = registerBlockWithoutBlockItem("meat_fluid_block",
            new ModFluidBlock(ModFluids.MEAT_STILL, FabricBlockSettings.of(Material.WATER)
                    .noCollision().dropsNothing()), ModItemGroup.MEAT);
    public static final Block NUTRIENT_FLUID_BLOCK = registerBlockWithoutBlockItem("nutrient_fluid_block",
            new ModFluidBlock(ModFluids.NUTRIENT_STILL, FabricBlockSettings.of(Material.WATER)
                    .noCollision().nonOpaque().dropsNothing()), ModItemGroup.MEAT);


    private static Block registerBlockWithoutBlockItem(String name, Block block, ItemGroup group) {
        return Registry.register(Registry.BLOCK, new Identifier(MobVats.MOD_ID, name), block);
    }

    private static Block registerBlock(String name, Block block, ItemGroup group) {
        registerBlockItem(name, block, group);
        return Registry.register(Registry.BLOCK, new Identifier(MobVats.MOD_ID, name), block);
    }

    private static Item registerBlockItem(String name, Block block, ItemGroup group) {
        return Registry.register(Registry.ITEM, new Identifier(MobVats.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings().group(group)));
    }

    public static void registerModBlocks() {
        MobVats.LOGGER.info("Registering ModBlocks for " + MobVats.MOD_ID);
    }
}
