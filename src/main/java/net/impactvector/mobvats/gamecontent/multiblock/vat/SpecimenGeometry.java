package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Structural resolution only. No traversal through Flesh and no clearance-based rejection. */
public final class SpecimenGeometry {
    public static final int MAX_HARNESS_GAP=4,MAX_DRAIN_GAP=4,DRAIN_HORIZONTAL_RANGE=2;
    public record Claim(BlockPos head,SimulacrumGeometry geometry) {}
    public static Map<BlockPos,BlockPos> ownership(List<Claim> claims){
        var owners=new LinkedHashMap<BlockPos,BlockPos>();
        for(var claim:claims)for(var cell:claim.geometry().occupiedCells(claim.head()))
            if(owners.putIfAbsent(cell,claim.head())!=null)throw new IllegalArgumentException("Overlapping specimen cell @ "+cell.toShortString());
        return Collections.unmodifiableMap(owners);
    }
    public static boolean medium(Level world,BlockPos p){
        var state=world.getBlockState(p);
        var fluid=state.getFluidState();
        return state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
    }
    public static void substrate(Level world,Claim claim){
        for(var p:claim.geometry().occupiedCells(claim.head())){
            var part=world.getBlockEntity(p);
            if(p.equals(claim.head())?!(part instanceof VatHeadEntity h && h.getIdentity()!=null && claim.geometry().equals(h.geometry())):!(part instanceof VatFleshEntity))
                throw new IllegalArgumentException("Required "+(p.equals(claim.head())?"encoded Head":"Flesh")+" @ "+p.toShortString());
        }
    }
    public static MobAssembly associate(Level world,VatHeadEntity head,List<VatHarnessEntity> harnesses,List<VatSumpEntity> drains){
        var g=head.geometry();var bounds=g.occupiedBounds(head.getBlockPos());
        var planes=new ArrayList<List<VatHarnessEntity>>();
        for(int plane=(int)bounds.maxY;plane<=bounds.maxY+MAX_HARNESS_GAP;plane++){
            var corners=g.harnessCorners(head.getBlockPos(),plane);var matching=harnesses.stream().filter(h->corners.contains(h.getBlockPos())).toList();
            if(matching.size()==corners.size())planes.add(matching);
        }
        if(planes.size()!=1)throw new IllegalArgumentException("Expected one complete Harness corner plane; found "+planes.size());
        var candidates=drains.stream().filter(d->{var p=d.getBlockPos();return p.getY()<bounds.minY && p.getY()>=bounds.minY-1-MAX_DRAIN_GAP
            && p.getX()>=bounds.minX-DRAIN_HORIZONTAL_RANGE && p.getX()<bounds.maxX+DRAIN_HORIZONTAL_RANGE
            && p.getZ()>=bounds.minZ-DRAIN_HORIZONTAL_RANGE && p.getZ()<bounds.maxZ+DRAIN_HORIZONTAL_RANGE;}).toList();
        if(candidates.size()!=1)throw new IllegalArgumentException("Expected exactly one Sump below specimen; found "+candidates.size());
        return new MobAssembly(planes.getFirst(),head,candidates.getFirst());
    }
    /** A Sump selects the nearest eligible footprint, then nearest vertical gap. Equal distance is ambiguous. */
    public static List<MobAssembly> associateAll(Level world,List<VatHeadEntity> heads,List<VatHarnessEntity> harnesses,List<VatSumpEntity> drains){
        var sorted=heads.stream().sorted(Comparator.comparing(VatHeadEntity::getBlockPos,VatTopologyCompiler.POSITION)).toList();
        var selected=new HashMap<BlockPos,List<VatSumpEntity>>();
        for(var drain:drains){
            VatHeadEntity owner=null;int best=Integer.MAX_VALUE,bestVertical=Integer.MAX_VALUE;boolean tied=false;
            for(var head:sorted){
                var b=head.geometry().occupiedBounds(head.getBlockPos());var p=drain.getBlockPos();
                if(p.getY()>=b.minY || p.getY()<b.minY-1-MAX_DRAIN_GAP)continue;
                int dx=Math.max(0,Math.max((int)b.minX-p.getX(),p.getX()-(int)b.maxX+1));
                int dz=Math.max(0,Math.max((int)b.minZ-p.getZ(),p.getZ()-(int)b.maxZ+1));
                if(dx>DRAIN_HORIZONTAL_RANGE || dz>DRAIN_HORIZONTAL_RANGE)continue;
                int distance=dx+dz;
                int vertical=(int)b.minY-p.getY();
                if(distance<best || distance==best && vertical<bestVertical){owner=head;best=distance;bestVertical=vertical;tied=false;}
                else if(distance==best && vertical==bestVertical)tied=true;
            }
            if(owner==null || tied)throw new IllegalArgumentException("Unassociated or ambiguous Sump @ "+drain.getBlockPos().toShortString());
            selected.computeIfAbsent(owner.getBlockPos(),p->new ArrayList<>()).add(drain);
        }
        var result=new ArrayList<MobAssembly>();
        for(var head:sorted){
            try{
                var assembly=associate(world,head,harnesses,selected.getOrDefault(head.getBlockPos(),List.of()));
                result.add(assembly);
            }catch(IllegalArgumentException error){throw new IllegalArgumentException("Head @ "+head.getBlockPos().toShortString()+": "+error.getMessage());}
        }
        supportOwnership(result);
        if(result.stream().mapToInt(a->a.harnesses().size()).sum()!=harnesses.size())throw new IllegalArgumentException("Unassociated Harness");
        return List.copyOf(result);
    }
    public static void supportOwnership(List<MobAssembly> assemblies){
        var harnesses=new HashSet<BlockPos>();var drains=new HashSet<BlockPos>();
        for(var assembly:assemblies){
            for(var harness:assembly.harnesses())if(!harnesses.add(harness.getBlockPos()))
                throw new IllegalArgumentException("Harness shared by specimens @ "+harness.getBlockPos().toShortString());
            if(!drains.add(assembly.drain().getBlockPos()))throw new IllegalArgumentException("Sump shared by specimens @ "+assembly.drain().getBlockPos().toShortString());
        }
    }
    private SpecimenGeometry(){}
}
