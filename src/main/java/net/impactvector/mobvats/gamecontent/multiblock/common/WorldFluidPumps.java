package net.impactvector.mobvats.gamecontent.multiblock.common;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Shared world-source mechanics. Controllers own lifecycle, scheduling and private storage. */
public final class WorldFluidPumps {
    public interface Endpoint { boolean accepts(FluidStack stack); void status(String value); }
    public static final int CADENCE=10;
    public static final int BUCKET=1000, SEARCH_RADIUS=16, SEARCH_LIMIT=4096;
    private static final Direction[] SEARCH={Direction.DOWN,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.UP};
    public static Direction outward(BlockPos p,BlockPos min,BlockPos max){
        if(p.getX()==min.getX())return Direction.WEST;if(p.getX()==max.getX())return Direction.EAST;
        if(p.getZ()==min.getZ())return Direction.NORTH;if(p.getZ()==max.getZ())return Direction.SOUTH;
        if(p.getY()==min.getY())return Direction.DOWN;if(p.getY()==max.getY())return Direction.UP;return null;
    }
    public static boolean infiniteWater(Level level,BlockPos p){
        var fluid=level.getFluidState(p);
        if(!(level instanceof ServerLevel server) || !fluid.is(Fluids.WATER) || !fluid.isSource() || !server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WATER_SOURCE_CONVERSION))return false;
        int count=0;for(var face:Direction.Plane.HORIZONTAL){var other=level.getFluidState(p.relative(face));if(other.is(Fluids.WATER)&&other.isSource())++count;}
        var below=level.getBlockState(p.below());return count>=2 && (below.isSolid() || below.getFluidState().is(Fluids.WATER)&&below.getFluidState().isSource());
    }
    public static boolean pull(Level level,BlockPos root,IFluidHandler destination,Endpoint e){
        if(!level.hasChunkAt(root)){e.status("Adjacent source unloaded");return false;}var state=level.getBlockState(root);var fluid=state.getFluidState();
        if(!(state.getBlock() instanceof LiquidBlock)||!fluid.isSource()){e.status("No adjacent source");return false;}
        var request=new FluidStack(fluid.getType(),BUCKET);
        if(!e.accepts(request)||destination.fill(request,SIMULATE)!=BUCKET){e.status("Filter or destination blocked");return false;}
        if(!infiniteWater(level,root) && !level.setBlock(root,Blocks.AIR.defaultBlockState(),3)){e.status("Source removal blocked");return false;}
        if(destination.fill(request,EXECUTE)!=BUCKET)throw new IllegalStateException("Private destination changed during pump commit");
        e.status("Pulled 1000 mB");return true;
    }
    public static BlockState source(FluidStack stack){
        if(stack.isEmpty() || !stack.getComponents().isEmpty())return null;var fluid=stack.getFluid();
        var state=fluid.defaultFluidState();var block=state.createLegacyBlock();
        return state.isSource() && block.getBlock() instanceof LiquidBlock?block:null;
    }
    private static boolean traversable(Level level,BlockPos p,Fluid fluid){
        if(!level.hasChunkAt(p)||level.isOutsideBuildHeight(p))return false;var state=level.getBlockState(p);
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().getType().isSame(fluid) || state.canBeReplaced() && level.getBlockEntity(p)==null && state.getFluidState().isEmpty();
    }
    private static boolean placeable(Level level,BlockPos p,Fluid fluid){return traversable(level,p,fluid) && !level.getFluidState(p).isSource();}
    /** Bounded loaded-cell search, sorted by depth, root distance, then x/z. */
    public static BlockPos placement(Level level,BlockPos root,Fluid fluid){
        if(!traversable(level,root,fluid))return null;
        var visited=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();var choices=new ArrayList<BlockPos>();queue.add(root);visited.add(root);
        while(!queue.isEmpty() && visited.size()<=SEARCH_LIMIT){var p=queue.remove();if(placeable(level,p,fluid))choices.add(p);
            for(var face:SEARCH){var next=p.relative(face);if(next.getY()>root.getY() || Math.abs(next.getX()-root.getX())>SEARCH_RADIUS || Math.abs(next.getZ()-root.getZ())>SEARCH_RADIUS || root.getY()-next.getY()>SEARCH_RADIUS || visited.contains(next) || !traversable(level,next,fluid))continue;visited.add(next);queue.add(next);}
        }
        // Refuse truncated searches instead of choosing a result from an incomplete cavity.
        if(visited.size()>SEARCH_LIMIT)return null;
        return choices.stream().min(Comparator.<BlockPos>comparingInt(p->p.getY()).thenComparingInt(p->p.distManhattan(root)).thenComparing(ConduitTopology.POSITION)).orElse(null);
    }
    public static boolean push(Level level,BlockPos root,IFluidHandler bus,Endpoint e){
        e.status("Source empty, insufficient or filter blocked");
        for(int i=0;i<bus.getTanks();i++){var candidate=bus.getFluidInTank(i);if(candidate.isEmpty()||!e.accepts(candidate))continue;var request=candidate.copyWithAmount(BUCKET);
            var state=source(request);if(state==null || level.dimensionType().ultraWarm() && request.is(Fluids.WATER)){e.status("Unsupported world placement");continue;}
            if(bus.drain(request,SIMULATE).getAmount()!=BUCKET)continue;
            var target=placement(level,root,request.getFluid());if(target==null){e.status("Placement blocked");continue;}
            // World mutation must succeed before spending private bus contents.
            if(!level.setBlock(target,state,3)){e.status("Placement blocked");return false;}
            if(bus.drain(request,EXECUTE).getAmount()!=BUCKET)throw new IllegalStateException("Private source changed during pump commit");
            e.status("Placed 1000 mB");return true;
        }return false;
    }
    private WorldFluidPumps(){}
}
