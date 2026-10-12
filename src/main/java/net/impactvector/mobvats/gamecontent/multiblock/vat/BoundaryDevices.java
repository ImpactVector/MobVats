package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Explicit world/medium crossings. No boundary operation is provided by storage or conduits. */
public final class BoundaryDevices {
    public static final int BUCKET=WorldFluidPumps.BUCKET, SEARCH_RADIUS=WorldFluidPumps.SEARCH_RADIUS, SEARCH_LIMIT=WorldFluidPumps.SEARCH_LIMIT;
    public static Direction outward(BlockPos p,BlockPos min,BlockPos max){return WorldFluidPumps.outward(p,min,max);}
    public static void tick(MultiblockVat vat,Collection<AbstractVatEntity> parts){
        tickOrdered(vat,parts.stream().filter(VatBoundaryEntity.class::isInstance).map(VatBoundaryEntity.class::cast).sorted(Comparator.comparing(VatBoundaryEntity::getBlockPos,ConduitTopology.POSITION)).toList());
    }
    public static void tickOrdered(MultiblockVat vat,List<VatBoundaryEntity> devices){
        if(vat.getWorld().getGameTime()%WorldFluidPumps.CADENCE!=0)return;
        var level=vat.getWorld();var min=vat.getBoundingBox().getMin();var max=vat.getBoundingBox().getMax();
        for(var e:devices){
            if(e.isRemoved() || !e.isMachineAssembled())continue;
            var input=vat.storage().destination(e.getBlockPos(),true,e::accepts);
            var output=vat.storage().handler(e.getBlockPos(),false,e::accepts);
            var face=outward(e.getBlockPos(),min,max);
            switch(e.getPartType()){
                case PullPump -> {if(face!=null)pull(level,e.getBlockPos().relative(face),input,e);}
                case PushPump -> {if(face!=null)push(level,e.getBlockPos().relative(face),output,e);}
                case FillValve -> fill(vat.medium(),vat.storage().handler(e.getBlockPos(),false,s->true),e);
                case DrainValve -> drain(vat.medium(),input,e);
                default -> {}
            }
        }
    }
    public static boolean infiniteWater(Level level,BlockPos p){return WorldFluidPumps.infiniteWater(level,p);}
    public static boolean pull(Level level,BlockPos root,IFluidHandler destination,VatBoundaryEntity e){return WorldFluidPumps.pull(level,root,destination,e);}
    public static BlockState source(FluidStack stack){return WorldFluidPumps.source(stack);}
    public static BlockPos placement(Level level,BlockPos root,Fluid fluid){return WorldFluidPumps.placement(level,root,fluid);}
    public static boolean push(Level level,BlockPos root,IFluidHandler bus,VatBoundaryEntity e){return WorldFluidPumps.push(level,root,bus,e);}
    public static boolean fill(VatMediumStorage medium,IFluidHandler bus,VatBoundaryEntity e){
        int tanks=bus.getTanks(),stage=tanks==0?0:1;
        String failure=tanks==0?"No connected supply":"No fluid available";
        FluidStack incoming=FluidStack.EMPTY;
        for(int i=0;i<tanks;i++){
            var stack=bus.getFluidInTank(i);if(stack.isEmpty())continue;
            if(stage<2){stage=2;failure="Filter mismatch";incoming=stack;}
            if(!e.accepts(stack))continue;
            var rejection=medium.fillRejection(stack);
            if(stage<3){stage=3;incoming=stack;failure=switch(rejection){
                case CONFLICT -> "Migration/reconnection conflict";
                case CAPACITY -> "Medium at capacity";
                case INCOMPATIBLE -> "Incompatible existing medium";
                default -> "Transfer blocked or unavailable";
            };}
            if(rejection!=VatMediumStorage.FillRejection.NONE)continue;
            stage=4;incoming=stack;failure="Transfer blocked or unavailable";
            int amount=medium.fill(stack.copyWithAmount(Math.min(BUCKET,stack.getAmount())),SIMULATE);
            if(amount==0)continue;
            amount=Math.min(amount,bus.drain(stack.copyWithAmount(amount),SIMULATE).getAmount());if(amount==0)continue;
            var request=stack.copyWithAmount(amount);
            if(bus.drain(request,EXECUTE).getAmount()!=amount)throw new IllegalStateException("Valve source changed during commit");
            if(medium.fill(request,EXECUTE)!=amount)throw new IllegalStateException("Valve medium changed during commit");
            e.fillDiagnostic("Filling normally",medium,request,amount);return true;
        }e.fillDiagnostic(failure,medium,incoming,0);return false;
    }
    public static boolean drain(VatMediumStorage medium,IFluidHandler destination,VatBoundaryEntity e){
        var request=medium.drain(BUCKET,SIMULATE);
        if(request.isEmpty() || !e.accepts(request)){e.status("Medium empty or filter blocked");return false;}
        int amount=destination.fill(request,SIMULATE);if(amount==0){e.status("Medium output blocked");return false;}
        request.setAmount(amount);
        if(destination.fill(request,EXECUTE)!=amount)throw new IllegalStateException("Valve destination changed during commit");
        medium.drain(amount,EXECUTE);e.status("Drained medium "+amount+" mB");return true;
    }
    private BoundaryDevices(){}
}
