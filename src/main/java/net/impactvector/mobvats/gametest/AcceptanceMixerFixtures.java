package net.impactvector.mobvats.gametest;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Full-height acceptance enclosure; storage stays outside the circular mixing volume. */
final class AcceptanceMixerFixtures {
    static final int HEIGHT=4, SHAFT_LENGTH=HEIGHT-1;
    private static final int[][] CORNERS={{0,0},{4,0},{0,4},{4,4}};

    static void build(ServerLevel level,BlockPos o,List<String> filters){
        if(filters.size()!=12)throw new IllegalArgumentException("One filter required per edge buffer");
        MachineFixtures.mixer(level::setBlockAndUpdate,o,5,HEIGHT,5,SHAFT_LENGTH,2,true);
        // Glass ceiling and floor retain a clear view of the vessel. Machinery stays solid.
        for(int x=0;x<5;x++)for(int y=0;y<HEIGHT;y++)for(int z=0;z<5;z++)
            if((x==0 || x==4 || y==0 || y==HEIGHT-1 || z==0 || z==4)
                && !(x==2 && z==2) && !(x==0 && y==1 && z==2))
                level.setBlockAndUpdate(o.offset(x,y,z),MixerContent.state(MixerPartType.Glass));
        level.setBlockAndUpdate(o.offset(2,1,0),MixerContent.state(MixerPartType.FluidPort));
        level.setBlockAndUpdate(o.offset(4,1,2),MixerContent.state(MixerPartType.FluidPort).setValue(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT,true));
        level.setBlockAndUpdate(o.offset(2,1,4),MixerContent.state(MixerPartType.ItemPort));
        // A single roof header joins four buffer risers above every mixing level.
        for(int x=0;x<5;x++)pipe(level,o.offset(x,HEIGHT-1,3));
        for(int x:new int[]{0,4})for(int z=1;z<3;z++)pipe(level,o.offset(x,HEIGHT-1,z));
        // Each corner stack needs just one adjacent outside-wall riser.
        for(int y=0;y<HEIGHT-1;y++){
            for(int x:new int[]{0,4})for(int z:new int[]{1,3})pipe(level,o.offset(x,y,z));
        }
        // Join the north Fluid Input without routing through a buffer endpoint.
        for(int y=1;y<HEIGHT;y++)pipe(level,o.offset(3,y,0));
        pipe(level,o.offset(4,HEIGHT-1,0));
        int index=0;
        for(var corner:CORNERS)for(int y=0;y<HEIGHT-1;y++){
            var pos=o.offset(corner[0],y,corner[1]);
            level.setBlockAndUpdate(pos,FluidBufferContent.state(ConduitCladding.GLASS,ConduitShell.MIXER));
            ((FluidBufferEntity)level.getBlockEntity(pos)).filter(filters.get(index++));
        }
    }
    static void build(ServerLevel level,BlockPos o){
        build(level,o,List.of("minecraft:water","minecraft:water","minecraft:water",
            "mobvats:waste","mobvats:waste","mobvats:waste",
            "mobvats:culture_slurry","mobvats:feeding_slurry","mobvats:feeding_slurry",
            "mobvats:experience","mobvats:culture_slurry","mobvats:feeding_slurry"));
    }
    /** The culture annex replaces both side taps with boundary ports. One upper tap restores service. */
    static void connectControllerAbove(ServerLevel level,BlockPos o){pipe(level,o.offset(0,2,2));}
    private static void pipe(ServerLevel level,BlockPos pos){
        level.setBlockAndUpdate(pos,ConduitContent.state(ConduitResource.FLUID,ConduitCladding.GLASS,ConduitShell.MIXER).setValue(ConduitBlock.MODE,ConduitMode.TAP));
    }
    private AcceptanceMixerFixtures(){}
}
