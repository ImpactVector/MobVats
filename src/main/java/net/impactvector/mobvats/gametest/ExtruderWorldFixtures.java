package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.extruder.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.product.ProductContent;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/** Development-world extruder demonstrations and decorative meat/waste castles. */
final class ExtruderWorldFixtures {
    private static final int Y = -60;
    private static final BlockPos DEMOS = new BlockPos(-24, Y, 0);

    static void build(ServerLevel level) {
        // Four independent, deterministic rear-buffer -> Extruder -> barrel examples.
        demo(level, DEMOS, true, false, "MEAT", "PORTION");
        demo(level, DEMOS.offset(0, 0, 5), true, true, "MEAT", "BLOCK");
        demo(level, DEMOS.offset(0, 0, 10), false, false, "WASTE", "FERTILIZER");
        demo(level, DEMOS.offset(0, 0, 15), false, true, "WASTE", "BLOCK");
        castle(level, new BlockPos(-53, Y, 30), true);
        castle(level, new BlockPos(-82, Y, 30), false);
    }

    private static void demo(ServerLevel level, BlockPos pos, boolean meat, boolean block, String fluid, String form) {
        var source = pos.west();
        level.setBlockAndUpdate(source, FluidBufferContent.block().defaultBlockState());
        var buffer = (FluidBufferEntity) level.getBlockEntity(source);
        buffer.tank.fill(new FluidStack(meat ? ProductFluids.MEAT.get() : ProductFluids.WASTE.get(), 4000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        level.setBlockAndUpdate(pos.below(), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(pos, ExtruderContent.BLOCK.get().defaultBlockState()
                .setValue(ExtruderBlock.FACING, Direction.EAST)
                .setValue(ExtruderBlock.FORM, block ? ExtruderBlock.Form.BLOCK : ExtruderBlock.Form.PORTION));
        ManualTestWorld.sign(level, pos.offset(-2, 1, -2), "EXTRUDER", fluid, form, "Barrel below");
    }

    private static void castle(ServerLevel level, BlockPos center, boolean meat) {
        var wall = meat ? ProductContent.MEAT_BLOCK.get() : ProductContent.WASTE_BLOCK.get();
        var fluid = meat ? ProductFluids.MEAT.get() : ProductFluids.WASTE.get();
        // Stone-lined two-block moat, with an outer retaining wall to prevent spreading.
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                var floor = center.offset(dx, -1, dz);
                level.setBlockAndUpdate(floor, Blocks.STONE_BRICKS.defaultBlockState());
                int radius = Math.max(Math.abs(dx), Math.abs(dz));
                if (radius == 9 || radius == 6) {
                    level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.STONE_BRICKS.defaultBlockState());
                } else if (radius >= 7) {
                    level.setBlockAndUpdate(center.offset(dx, 0, dz),
                            fluid.defaultFluidState().createLegacyBlock());
                } else {
                    level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        // Hollow keep with corner towers and crenellated parapet.
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                boolean wallCell = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                boolean tower = Math.abs(dx) >= 4 && Math.abs(dz) >= 4;
                for (int dy = 1; dy <= (tower ? 7 : 5); dy++) {
                    boolean battlement = dy == 5 || dy == 7;
                    boolean crenel = ((dx + dz) & 1) == 0;
                    boolean present = wallCell && (!battlement || crenel || tower);
                    level.setBlockAndUpdate(center.offset(dx, dy, dz),
                            present ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        // Raised approach over the northern moat and an opening into the courtyard.
        for (int dz = -9; dz <= -5; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                level.setBlockAndUpdate(center.offset(dx, 1, dz), Blocks.OAK_PLANKS.defaultBlockState());
                if (dz == -5)
                    for (int dy = 2; dy <= 3; dy++)
                        level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        ManualTestWorld.sign(level, center.offset(0, 2, -10),
                meat ? "MEAT CASTLE" : "WASTE CASTLE", "Fluid moat", "Block fortress", "Extruder showcase");
    }

    static void verify(ServerLevel level, StringBuilder manifest) {
        for (int i = 0; i < 4; i++) {
            var pos = DEMOS.offset(0, 0, i * 5);
            if (!(level.getBlockEntity(pos) instanceof ExtruderEntity))
                throw new IllegalStateException("Missing Extruder demo at " + pos);
            if (!(level.getBlockEntity(pos.below()) instanceof BarrelBlockEntity barrel))
                throw new IllegalStateException("Missing Extruder barrel at " + pos);
            boolean produced = false;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++)
                produced |= !barrel.getItem(slot).isEmpty();
            if (!produced)
                throw new IllegalStateException("Extruder demo did not produce into barrel at " + pos);
        }
        manifest.append("\\nExtruder demonstrations: 4 rear-buffer-fed units, all produced into barrels.\\n");
        manifest.append("Meat Castle: (-53, -60, 30); Waste Castle: (-82, -60, 30).\\n");
    }

    private ExtruderWorldFixtures() {}
}
