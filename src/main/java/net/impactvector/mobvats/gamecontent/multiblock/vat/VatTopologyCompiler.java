package net.impactvector.mobvats.gamecontent.multiblock.vat;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import net.minecraft.core.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
public final class VatTopologyCompiler {
    public static final Comparator<BlockPos> POSITION = Comparator.<BlockPos>comparingInt(p->p.getX()).thenComparingInt(p->p.getY()).thenComparingInt(p->p.getZ());
    public record Claim(BlockPos actor, BlockPos head, String use, int priority) {}
    public static List<Claim> claim(List<Claim> candidates) {
        var claimed = new HashSet<String>(); var result = new ArrayList<Claim>();
        candidates.stream().sorted(Comparator.comparingInt(Claim::priority).reversed().thenComparing(Claim::actor,POSITION).thenComparing(Claim::head,POSITION).thenComparing(Claim::use)).forEach(c->{
            if (claimed.add(c.head().toShortString()+":"+c.use())) { result.add(c); }
        });
        return List.copyOf(result);
    }
    public static VatTopology compile(Level level, BlockPos min, BlockPos max, List<MobAssembly> assemblies, Collection<AbstractVatEntity> ports) {
        var items = ports.stream().filter(p->p instanceof VatItemPortEntity).map(p->(VatItemPortEntity)p).toList();
        var fluids = ports.stream().filter(p->p instanceof VatFluidPortEntity).map(p->(VatFluidPortEntity)p).toList();
        var inputs = positions(items,IoDirection.Input); var outputs = positions(items,IoDirection.Output);
        var fluidInputs=positions(fluids,IoDirection.Input);var fluidOutputs=positions(fluids,IoDirection.Output);
        var candidates = new ArrayList<Claim>();var loadingClaims=new ArrayList<Claim>();
        for (var part:ports) for (var assembly:assemblies) {
            if(part instanceof VatToolPortEntity tool && tool.heldItem().isEmpty() && tool.reaches(level,part.getBlockPos(),min,max,assembly,assemblies))
                loadingClaims.add(new Claim(part.getBlockPos().immutable(),assembly.head().getBlockPos().immutable(),"toolLoading",0));
            if(part instanceof VatActor actor && actor.compatible(assembly) && actor.reaches(level,part.getBlockPos(),min,max,assembly,assemblies))
                for(var use:actor.uses(assembly))candidates.add(new Claim(part.getBlockPos().immutable(),assembly.head().getBlockPos().immutable(),use,actor.priority()));
        }
        // Structurally reachable actors expose service plumbing. Obstruction filters interaction claims below.
        var actorPositions=new HashSet<BlockPos>();candidates.forEach(c->actorPositions.add(c.actor()));loadingClaims.forEach(c->actorPositions.add(c.actor()));
        ports.stream().filter(VatActor.class::isInstance).forEach(p->actorPositions.add(p.getBlockPos()));
        var endpoints=new ArrayList<ConduitTopology.Endpoint>();
        inputs.forEach(p->endpoints.add(new ConduitTopology.Endpoint(p,ConduitResource.ITEM,ConduitTopology.Role.STORE)));
        outputs.forEach(p->endpoints.add(new ConduitTopology.Endpoint(p,ConduitResource.ITEM,ConduitTopology.Role.CONSUMER)));
        fluidInputs.forEach(p->endpoints.add(new ConduitTopology.Endpoint(p,ConduitResource.FLUID,ConduitTopology.Role.STORE)));
        fluidOutputs.forEach(p->endpoints.add(new ConduitTopology.Endpoint(p,ConduitResource.FLUID,ConduitTopology.Role.CONSUMER)));
        for(var pos:actorPositions)if(level.getBlockEntity(pos) instanceof VatActor actor){
            if(actor.usesLocalItemPorts())endpoints.add(new ConduitTopology.Endpoint(pos,ConduitResource.ITEM,ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER));
            if(actor.usesLocalFluidPorts())endpoints.add(new ConduitTopology.Endpoint(pos,ConduitResource.FLUID,ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER));
        }
        for(var part:ports)if(part instanceof VatBoundaryEntity e){
            var endpoint=new ConduitTopology.Endpoint(e.getBlockPos(),e.resource(),ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER);
            if(e.getPartType()==VatPartType.PullPump || e.getPartType()==VatPartType.PushPump){var face=BoundaryDevices.outward(e.getBlockPos(),min,max);if(face!=null)endpoint=endpoint.attached(face.getOpposite());}
            endpoints.add(endpoint);
        }
        for(var buffer:PhysicalFluidStorage.members(level,min,max))endpoints.add(new ConduitTopology.Endpoint(buffer.getBlockPos(),ConduitResource.FLUID,ConduitTopology.Role.STORE));
        for(var part:ports)if(part instanceof ResourceEndpoint endpoint){endpoints.removeIf(e->e.position().equals(endpoint.getBlockPos()));endpoints.addAll(endpoint.connections());}
        var conduits=ConduitTopology.compile(level,min,max,endpoints);
        var geometry=ConduitGeometry.bake(ConduitGeometry.compile(level,min,max,conduits));
        var harnessState=VatPhysicalState.harnesses(assemblies,geometry);
        var blocked=new ArrayList<>(harnessState.blocked());
        candidates.removeIf(c->!harnessState.operational(c.head()) || !actorClear(level,min,max,assemblies,c,geometry,blocked));
        loadingClaims.removeIf(c->!harnessState.operational(c.head()) || !actorClear(level,min,max,assemblies,c,geometry,blocked));
        var selected=new ArrayList<>(claim(candidates.stream().filter(c->!(level.getBlockEntity(c.actor()) instanceof VatSumpEntity) && !(level.getBlockEntity(c.actor()) instanceof VatFeedPortEntity)).toList()).stream().filter(c->!(level.getBlockEntity(c.actor()) instanceof VatToolPortEntity)).toList());
        candidates.stream().filter(c->level.getBlockEntity(c.actor()) instanceof VatSumpEntity).forEach(selected::add);
        candidates.stream().filter(c->level.getBlockEntity(c.actor()) instanceof VatFeedPortEntity).sorted(Comparator.comparing(Claim::actor,POSITION).thenComparing(Claim::head,POSITION)).forEach(selected::add);
        candidates.stream().filter(c->level.getBlockEntity(c.actor()) instanceof VatToolPortEntity).sorted(Comparator.comparing(Claim::actor,POSITION).thenComparing(Claim::head,POSITION).thenComparing(Claim::use)).forEach(selected::add);
        var active=selected.stream().map(c->assignment(level,c,conduits,inputs,outputs,fluidInputs,fluidOutputs)).toList();
        // Each empty Tool retains every independently reachable loading relationship.
        var loading=loadingClaims.stream().sorted(Comparator.comparing(Claim::actor,POSITION).thenComparing(Claim::head,POSITION))
            .map(c->assignment(level,c,conduits,inputs,outputs,fluidInputs,fluidOutputs)).toList();
        // All valid relationships reserve outputs, including loading and displaced actors.
        var reserved = new HashSet<BlockPos>();
        for(var c:candidates)reserved.addAll(assignment(level,c,conduits,inputs,outputs,fluidInputs,fluidOutputs).itemOutputs());
        for(var connection:loading)reserved.addAll(connection.itemOutputs());
        var contacts = List.<VatTopology.ItemContact>of(); // Legacy record API; generic logistics never feed.
        return new VatTopology(assemblies.stream().map(a->a.head().getBlockPos().immutable()).sorted(POSITION).toList(),inputs,outputs,
            positions(fluids,IoDirection.Input),positions(fluids,IoDirection.Output),active,List.copyOf(contacts),List.copyOf(loading),
            outputs.stream().filter(reserved::contains).toList(),outputs.stream().filter(p -> !reserved.contains(p)).toList(),conduits,new VatPhysicalState(geometry,blocked));
    }
    private static boolean actorClear(Level level,BlockPos min,BlockPos max,List<MobAssembly> assemblies,Claim claim,ConduitGeometry.Geometry geometry,List<VatPhysicalState.Blocked> blocked){
        var part=level.getBlockEntity(claim.actor());
        if(!(part instanceof VatToolPortEntity) && !(part instanceof VatBlenderEntity) && !(part instanceof VatFeedPortEntity))return true;
        var assembly=assemblies.stream().filter(a->a.head().getBlockPos().equals(claim.head())).findFirst().orElseThrow();
        boolean feed=part instanceof VatFeedPortEntity;
        var link=VatInteractionGeometry.link(min,max,assembly,claim.actor(),true,feed);
        var hit=RouteIntersection.first(VatInteractionGeometry.route(link,feed),geometry,claim.actor());
        if(hit.isEmpty())return true;
        var reason=new VatPhysicalState.Blocked(feed?VatPhysicalState.Kind.FEED:part instanceof VatBlenderEntity?VatPhysicalState.Kind.BLENDER:VatPhysicalState.Kind.TOOL,claim.actor(),claim.head(),hit.get());
        if(!blocked.contains(reason))blocked.add(reason);
        return false;
    }
    private static List<BlockPos> positions(List<? extends AbstractVatPortEntity> parts, IoDirection direction) {
        return parts.stream().filter(p->p.getIoDirection()==direction).map(p->p.getBlockPos().immutable()).sorted(POSITION).toList();
    }
    private static VatTopology.Assignment assignment(Level level,Claim claim,ConduitTopology conduits,List<BlockPos> inputs,List<BlockPos> outputs,List<BlockPos> fluidInputs,List<BlockPos> fluidOutputs){
        var actor=(VatActor)level.getBlockEntity(claim.actor());
        var items=actor.usesLocalItemPorts()?conduits.expand(claim.actor(),ConduitResource.ITEM,adjacent(inputs,claim.actor()),adjacent(outputs,claim.actor())):new ConduitTopology.Endpoints(List.of(),List.of());
        var fluids=actor.usesLocalFluidPorts()?conduits.expand(claim.actor(),ConduitResource.FLUID,adjacent(fluidInputs,claim.actor()),adjacent(fluidOutputs,claim.actor())):new ConduitTopology.Endpoints(List.of(),List.of());
        return new VatTopology.Assignment(claim.actor(),claim.head(),claim.use(),items.inputs().stream().filter(inputs::contains).toList(),items.outputs().stream().filter(outputs::contains).toList(),fluids.inputs().stream().filter(fluidInputs::contains).toList(),fluids.outputs().stream().filter(fluidOutputs::contains).toList());
    }
    private static List<BlockPos> adjacent(List<BlockPos> ports, BlockPos actor) { return ports.stream().filter(p->p.distManhattan(actor)==1).toList(); }
    /** Direction comes only from specimen geometry. A bus attachment never supplies facing. */
    public static boolean radialTargets(Level level,BlockPos actor,MobAssembly target,List<MobAssembly> all,double range,boolean cardinal){
        var start=Vec3.atCenterOf(actor);var body=target.bodyBounds();
        var nearest=new Vec3(Math.clamp(start.x,body.minX,body.maxX),Math.clamp(start.y,body.minY,body.maxY),Math.clamp(start.z,body.minZ,body.maxZ));
        if(start.distanceTo(nearest)>range || start.distanceToSqr(nearest)<1e-6)return false;
        if(cardinal && !((start.x>=body.minX && start.x<=body.maxX) || (start.z>=body.minZ && start.z<=body.maxZ)))return false;
        var aim=body.getCenter();var hit=body.clip(start,aim).orElse(aim);var delta=hit.subtract(start);double distance=delta.length();var direction=delta.normalize();
        for(var other:all)if(other!=target && other.bodyBounds().clip(start,hit).isPresent())return false;
        for(double step=.51;step<distance;step+=.1){var pos=BlockPos.containing(start.add(direction.scale(step)));if(pos.equals(actor))continue;
            if(target.occupiedBounds().contains(Vec3.atCenterOf(pos)))continue;
            if(level.getBlockState(pos).getBlock() instanceof ConduitBlock)continue;
            if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())return false;
        }return true;
    }
    public static boolean targets(Level level, BlockPos port, BlockPos min, BlockPos max, MobAssembly assembly, List<MobAssembly> all) {
        var shell = VatCasingState.from(port,min,max);
        if (Integer.bitCount(shell.outwardFaces()) != 1 || shell.outward(Direction.UP) || shell.outward(Direction.DOWN)) return false;
        var outward = Arrays.stream(Direction.values()).filter(shell::outward).findFirst().orElseThrow();
        var inward = Vec3.atLowerCornerOf(outward.getOpposite().getNormal());
        var start = Vec3.atCenterOf(port); var end = start.add(inward.scale(32));
        var bounds = assembly.bodyBounds();
        var hit = bounds.clip(start,end); if (hit.isEmpty()) return false;
        double distance = start.distanceTo(hit.get());
        // Another simulacrum occupies the ray before this target, even without a solid entity.
        for (var other:all) if (other != assembly) {
            var otherHit=other.bodyBounds().clip(start,end);
            if (otherHit.isPresent() && start.distanceTo(otherHit.get()) < distance) return false;
        }
        for (double step=.51; step<distance; step+=.1) {
            var p=BlockPos.containing(start.add(inward.scale(step)));
            if (assembly.occupiedBounds().contains(Vec3.atCenterOf(p))) continue;
            // Actual narrow pipe/route intersections are checked by the shared physical geometry.
            if(level.getBlockState(p).getBlock() instanceof net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock)continue;
            if (!level.getBlockState(p).getCollisionShape(level,p).isEmpty()) return false;
        }
        return true;
    }
    private VatTopologyCompiler() {}
}
