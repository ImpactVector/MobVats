package net.impactvector.mobvats.gametest;

import java.util.function.BiConsumer;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Geometry shared by automated tests and the disposable manual world. Positions are sink-relative. */
final class MachineFixtures {
    static final int MINIMUM_VAT_WIDTH=3, LARGER_VAT_WIDTH=5;
    static final int MINIMUM_MIXER_WIDTH=3, MINIMUM_MIXER_HEIGHT=3, MINIMUM_MIXER_SHAFT=2;
    static void grassInterior(BiConsumer<BlockPos,BlockState> put,BlockPos o) {
        put.accept(o.offset(1,1,1),Blocks.GRASS_BLOCK.defaultBlockState());
    }
    /** Operator/test specimens may occupy a different footprint from the historical cow. */
    static void specimen(BiConsumer<BlockPos,BlockState> put,BlockPos head,net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry geometry) {
        var cells=geometry.occupiedCells(head);
        if(!cells.contains(head.below()))put.accept(head.below(),Blocks.AIR.defaultBlockState());
        for(var cell:cells)if(!cell.equals(head))put.accept(cell,Content.state(VatPartType.Flesh));
        for(var corner:geometry.harnessCorners(head,(int)geometry.occupiedBounds(head).maxY))
            put.accept(corner,Content.state(VatPartType.Harness));
    }
    static void vat(BiConsumer<BlockPos, BlockState> put, BlockPos o, int width, int depth, int height) {
        for (int x=0;x<width;x++) for (int y=0;y<height;y++) for (int z=0;z<depth;z++) {
            boolean shell=x==0 || x==width-1 || z==0 || z==depth-1 || y==0 || y==height-1;
            put.accept(o.offset(x,y,z), shell ? Content.state(y==0 || y==height-1 ? VatPartType.Casing : VatPartType.Glass) : Blocks.AIR.defaultBlockState());
        }
        put.accept(o.offset(1,1,0),Content.state(VatPartType.Controller));
        put.accept(o.offset(width/2,height-1,depth/2),Content.state(VatPartType.Harness));
        put.accept(o.offset(width/2,0,depth/2),Content.state(VatPartType.Sump));
        put.accept(o.offset(width/2,height-2,depth/2),Content.state(VatPartType.Head));
        put.accept(o.offset(width/2,height-3,depth/2),Content.state(VatPartType.Flesh));
    }

    static BlockPos mixer(BiConsumer<BlockPos, BlockState> put, BlockPos o, int width, int height, int depth, int shaftLength, int controllerZ, boolean glass) {
        for(int x=0;x<width;x++) for(int y=0;y<height;y++) for(int z=0;z<depth;z++) {
            boolean shell=x==0 || x==width-1 || y==0 || y==height-1 || z==0 || z==depth-1;
            boolean window=glass && y>0 && y<height-1 && ((x==0 || x==width-1) && z>0 && z<depth-1 || (z==0 || z==depth-1) && x>0 && x<width-1);
            put.accept(o.offset(x,y,z),shell ? MixerContent.state(window ? MixerPartType.Glass : MixerPartType.Casing) : Blocks.AIR.defaultBlockState());
        }
        put.accept(o.offset(0,1,controllerZ),MixerContent.state(MixerPartType.Controller));
        var top=o.offset(width/2,height-1,depth/2);
        put.accept(top,MixerContent.state(MixerPartType.Impeller));
        for(int d=1;d<=shaftLength;d++) put.accept(top.below(d),MixerContent.state(MixerPartType.Shaft));
        return top;
    }

    static void mixerRig(BiConsumer<BlockPos, BlockState> put, BlockPos o) {
        mixer(put,o,5,4,5,2,2,true);
        put.accept(o.offset(2,1,0),MixerContent.state(MixerPartType.FluidPort));
        put.accept(o.offset(4,1,2),MixerContent.state(MixerPartType.FluidPort).setValue(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT,true));
        put.accept(o.offset(2,1,4),MixerContent.state(MixerPartType.ItemPort));
    }

    enum MixerFault { MISSING_IMPELLER, OFF_CENTER_IMPELLER, SHAFT_GAP, OFF_AXIS_SHAFT, DISCONNECTED_SHAFT }

    static void mixerFault(BiConsumer<BlockPos, BlockState> put, BlockPos o, MixerFault fault) {
        var top=o.offset(2,3,2);
        switch(fault) {
            case MISSING_IMPELLER -> put.accept(top,MixerContent.state(MixerPartType.Casing));
            case OFF_CENTER_IMPELLER -> {
                put.accept(top,MixerContent.state(MixerPartType.Casing));
                put.accept(top.east(),MixerContent.state(MixerPartType.Impeller));
                put.accept(top.below(),Blocks.AIR.defaultBlockState());
                put.accept(top.below(2),Blocks.AIR.defaultBlockState());
                put.accept(top.east().below(),MixerContent.state(MixerPartType.Shaft));
            }
            case SHAFT_GAP -> put.accept(top.below(),Blocks.AIR.defaultBlockState());
            case OFF_AXIS_SHAFT -> put.accept(top.below().east(),MixerContent.state(MixerPartType.Shaft));
            case DISCONNECTED_SHAFT -> put.accept(o.offset(3,2,3),MixerContent.state(MixerPartType.Shaft));
        }
    }
}
