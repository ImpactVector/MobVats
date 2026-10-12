package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Slice 2 physical contract. Storage clustering, not filter equality alone, owns bridges. */
public final class FluidBufferGeometry {
    public static final AABB BODY=new AABB(3.0/16,2.0/16,3.0/16,13.0/16,15.0/16,13.0/16);
    public enum Kind { BODY, SUPPORT, MOUNT, BRIDGE }
    public record Volume(BlockPos owner,Kind kind,AABB bounds) {}
    public static List<Volume> envelope(BlockPos pos,boolean supported,Set<Direction> mounts,Set<Direction> bridges) {
        var result=new ArrayList<Volume>();
        result.add(new Volume(pos.immutable(),Kind.BODY,BODY.move(pos)));
        if(supported)result.add(new Volume(pos.immutable(),Kind.SUPPORT,new AABB(6.0/16,0,6.0/16,10.0/16,2.0/16,10.0/16).move(pos)));
        for(var face:Direction.values()) {
            if(bridges.contains(face)) {
                // Each member owns its half of a continuous 10-pixel tank bridge.
                result.add(new Volume(pos.immutable(),Kind.BRIDGE,extension(face,3.0/16,13.0/16,2.0/16,15.0/16).move(pos)));
            } else if(mounts.contains(face)) {
                result.add(new Volume(pos.immutable(),Kind.MOUNT,extension(face,6.0/16,10.0/16,6.0/16,10.0/16).move(pos)));
            }
        }
        return List.copyOf(result);
    }
    private static AABB extension(Direction face,double lo,double hi,double bottom,double top) {
        double start=face.getAxisDirection()==Direction.AxisDirection.NEGATIVE?0:face.getAxis()==Direction.Axis.Y?BODY.maxY:BODY.maxX;
        double end=face.getAxisDirection()==Direction.AxisDirection.NEGATIVE?face.getAxis()==Direction.Axis.Y?BODY.minY:BODY.minX:1;
        return switch(face.getAxis()) {
            case X -> new AABB(start,bottom,lo,end,top,hi);
            case Z -> new AABB(lo,bottom,start,hi,top,end);
            case Y -> new AABB(lo,start,lo,hi,end,hi);
        };
    }
    public static List<Volume> compile(Level level,BlockPos min,BlockPos max) {
        var members=PhysicalFluidStorage.members(level,min,max);
        var storage=new PhysicalFluidStorage();storage.compile(members,ConduitTopology.EMPTY);
        var groups=new HashMap<BlockPos,Integer>();
        for(int i=0;i<storage.clusters().size();i++)for(var member:storage.clusters().get(i).members())groups.put(member.getBlockPos(),i);
        var result=new ArrayList<Volume>();
        for(var member:members) {
            var pos=member.getBlockPos();var mounts=EnumSet.noneOf(Direction.class);var bridges=EnumSet.noneOf(Direction.class);
            var block=(FluidBufferBlock)level.getBlockState(pos).getBlock();
            var shell=VatCasingState.from(pos,min,max);
            for(var face:Direction.values()) {
                var neighbor=pos.relative(face);
                if(groups.containsKey(neighbor) && groups.get(pos).equals(groups.get(neighbor)))bridges.add(face);
                else if(level.getBlockState(neighbor).is(MixerContent.block(MixerPartType.Casing))
                    || level.getBlockState(neighbor).is(MixerContent.block(MixerPartType.Glass))
                    || block.cladding(level.getBlockState(pos))!=ConduitCladding.BARE && shell.outward(face))mounts.add(face);
            }
            result.addAll(envelope(pos,!level.getBlockState(pos.below()).isAir(),mounts,bridges));
        }
        return List.copyOf(result);
    }
    private FluidBufferGeometry() {}
}
