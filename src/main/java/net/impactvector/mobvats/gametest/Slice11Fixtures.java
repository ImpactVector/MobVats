package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;

/** Explicit physical storage added to historical runtime fixtures. Geometry-only fixtures stay untouched. */
final class Slice11Fixtures {
    static void vatStorage(GameTestHelper h, BlockPos o, int width, int depth) {
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++)
                if (x == 0 || z == 0 || x == width - 1 || z == depth - 1) {
                    var pos = o.offset(x, 2, z);
                    var part = h.getBlockEntity(pos);
                    if (part instanceof VatCasingEntity || part instanceof VatGlassEntity)
                        h.setBlock(
                                pos,
                                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
                }
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++)
                for (int y : new int[] {0, 3}) {
                    var pos = o.offset(x, y, z);
                    var part = h.getBlockEntity(pos);
                    if (part instanceof VatCasingEntity || part instanceof VatGlassEntity)
                        h.setBlock(
                                pos,
                                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
                }
        for (int y : new int[] {1, 2})
            h.setBlock(
                    o.offset(0, y, 1),
                    ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        var fluids = List.of(
                "minecraft:milk",
                "mobvats:meat",
                "mobvats:waste",
                "mobvats:culture_slurry",
                "mobvats:feeding_slurry",
                "mobvats:experience",
                "minecraft:water",
                "minecraft:lava");
        var positions = List.of(
                o.offset(1, 3, 1),
                o.offset(width - 2, 3, 1),
                o.offset(1, 3, depth - 2),
                o.offset(width - 2, 3, depth - 2),
                o.offset(0, 3, 0),
                o.offset(width - 1, 3, depth - 1),
                o.offset(0, 3, depth - 1),
                o.offset(width - 1, 3, 0));
        for (int i = 0; i < positions.size(); i++) {
            h.setBlock(positions.get(i), FluidBufferContent.state(ConduitCladding.CASING));
            ((FluidBufferEntity) h.getBlockEntity(positions.get(i))).filter(fluids.get(i));
        }
    }

    static void feedFrom(GameTestHelper h, BlockPos input, BlockPos actor) {
        h.setBlock(actor, Content.state(VatPartType.FeedPort));
    }

    static void resourceIo(GameTestHelper h, BlockPos o) {
        for (var p :
                List.of(o.offset(2, 0, 0), o.offset(2, 0, 1), o.offset(3, 0, 1), o.offset(4, 0, 1), o.offset(4, 0, 2)))
            h.setBlock(
                    p,
                    ConduitContent.state(ConduitResource.ITEM, ConduitCladding.CASING)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        for (var p : List.of(o.offset(3, 0, 2), o.offset(2, 0, 3))) {
            h.setBlock(p, Content.state(VatPartType.ItemPort));
            ((VatItemPortEntity) h.getBlockEntity(p))
                    .setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        }
    }

    static void mixerStorage(GameTestHelper h, BlockPos o) {
        // Runtime regressions use canonical peripheral storage; preserve the historical
        // level-based dense fixture below for compatibility diagnostics.
        var filters = List.of(
                "minecraft:water",
                "minecraft:water",
                "mobvats:waste",
                "mobvats:waste",
                "mobvats:culture_slurry",
                "mobvats:culture_slurry",
                "mobvats:feeding_slurry",
                "mobvats:feeding_slurry",
                "mobvats:experience");
        int index = 0;
        for (var corner : List.of(new BlockPos(0, 0, 0), new BlockPos(4, 0, 0), new BlockPos(0, 0, 4)))
            for (int y = 0; y < 3; y++) {
                var p = o.offset(corner).above(y);
                h.setBlock(p, FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
                ((FluidBufferEntity) h.getBlockEntity(p)).filter(filters.get(index++));
            }
        for (int y = 0; y < 3; y++)
            for (int x : new int[] {0, 4}) for (int z : new int[] {1, 3}) mixerPipe(h, o.offset(x, y, z));
        // Roof perimeter remains connected when any cardinal drive is installed.
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++) if (x == 0 || x == 4 || z == 0 || z == 4) mixerPipe(h, o.offset(x, 3, z));
        for (int y = 0; y < 4; y++) mixerPipe(h, o.offset(3, y, 0));
    }

    private static void mixerPipe(GameTestHelper h, BlockPos p) {
        h.setBlock(
                p,
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING, ConduitShell.MIXER)
                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
    }

    static void mixerStorage(net.minecraft.server.level.ServerLevel level, BlockPos o) {
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                level.setBlockAndUpdate(
                        o.offset(x, 0, z),
                        ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING, ConduitShell.MIXER)
                                .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        int n = 0;
        for (var fluid : List.of(
                "minecraft:water",
                "mobvats:waste",
                "mobvats:culture_slurry",
                "mobvats:feeding_slurry",
                "mobvats:experience")) {
            int count = fluid.equals("mobvats:experience") ? 1 : 2;
            for (int i = 0; i < count; i++) {
                var p = n == 4 ? o.offset(1, 2, 1) : o.offset(1 + n / 3, 1, 1 + n % 3);
                level.setBlockAndUpdate(p, FluidBufferContent.state(ConduitCladding.BARE));
                ((FluidBufferEntity) level.getBlockEntity(p)).filter(fluid);
                n++;
            }
        }
    }
}
