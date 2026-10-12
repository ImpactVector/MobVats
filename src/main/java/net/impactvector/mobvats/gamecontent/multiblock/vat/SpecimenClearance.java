package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Comfort-shell observations for happiness. Cosmetic service volumes do not block cell credit. */
public record SpecimenClearance(Set<BlockPos> shell, Set<BlockPos> solidIntrusions, List<AABB> serviceGeometry,
        List<AABB> neighboringBodies) {
    public SpecimenClearance(Set<BlockPos> shell,Set<BlockPos> solids,List<AABB> services){this(shell,solids,services,List.of());}
    public SpecimenClearance {shell=Set.copyOf(shell);solidIntrusions=Set.copyOf(solidIntrusions);serviceGeometry=List.copyOf(serviceGeometry);neighboringBodies=List.copyOf(neighboringBodies);}
    /** One intervening cell is shared by both shells; two cells permit independent shells. */
    public Set<BlockPos> sharedShell(SpecimenClearance other){var result=new HashSet<>(shell);result.retainAll(other.shell);return Set.copyOf(result);}
    public double creditedFraction(Collection<SpecimenClearance> specimens){
        if(shell.isEmpty())return 0;
        double credit=0;
        for(var p:shell){
            if(solidIntrusions.contains(p) || neighboringBodies.stream().anyMatch(body->body.intersects(new AABB(p))))continue;
            long claims=specimens.stream().filter(c->c.shell.contains(p)).count();
            credit+=1.0/Math.max(1,claims);
        }
        return credit/shell.size();
    }
    public static SpecimenClearance inspect(Level world,SimulacrumGeometry g,BlockPos head){
        return inspect(world,g,head,List.of());
    }
    public static SpecimenClearance inspect(Level world,SimulacrumGeometry g,BlockPos head,List<MobAssembly> specimens){
        var shell=g.comfortShell(head);var solid=new HashSet<BlockPos>();var services=new ArrayList<AABB>();
        for(var p:shell){
            if(world.getBlockState(p).getBlock() instanceof ConduitBlock){
                solid.add(p);
                var controller=ConduitStructure.containing(world,p);
                if(controller!=null)for(var box:controller.conduitGeometry().occupied())if(box.owner().equals(p))services.add(box.bounds());
            }else if(!SpecimenGeometry.medium(world,p))solid.add(p);
        }
        var outer=g.occupiedBounds(head).inflate(1);
        var bodies=specimens.stream().filter(a->!a.head().getBlockPos().equals(head)).map(MobAssembly::bodyBounds)
            .filter(body->body.intersects(outer)).toList();
        return new SpecimenClearance(shell,solid,services,bodies);
    }
}
