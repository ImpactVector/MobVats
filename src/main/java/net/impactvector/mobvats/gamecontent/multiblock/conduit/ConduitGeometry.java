package net.impactvector.mobvats.gamecontent.multiblock.conduit;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
/** Renderer-independent occupied volumes, shared by rendering and Mixer vessel clearance. */
public final class ConduitGeometry {
    public static final double DIAMETER=6.0/16, RADIUS=DIAMETER/2, CLEARANCE=1.0/64;
    public enum Material { BODY, COLLAR, MARKER }
    public record Attachment(BlockPos endpoint,EndpointGeometry.Kind kind){}
    public record Cell(BlockPos position,ConduitResource resource,ConduitCladding cladding,ConduitMode mode,int connections,List<Attachment> attachments){public Cell{attachments=List.copyOf(attachments);}}
    public record Snapshot(BlockPos min,BlockPos max,List<Cell> cells){
        public static final Snapshot EMPTY=new Snapshot(BlockPos.ZERO,BlockPos.ZERO,List.of());
        public Snapshot{cells=List.copyOf(cells);}
        public CompoundTag save(){
            var tag=new CompoundTag();tag.putLong("min",min.asLong());tag.putLong("max",max.asLong());var list=new ListTag();
            for(var cell:cells){var c=new CompoundTag();c.putLong("pos",cell.position().asLong());c.putInt("resource",cell.resource().ordinal());c.putInt("cladding",cell.cladding().ordinal());c.putInt("mode",cell.mode().ordinal());c.putInt("connections",cell.connections());var attached=new ListTag();for(var a:cell.attachments()){var e=new CompoundTag();e.putLong("pos",a.endpoint().asLong());e.putInt("kind",a.kind().ordinal());attached.add(e);}c.put("endpoints",attached);list.add(c);}tag.put("cells",list);return tag;
        }
        public static Snapshot load(CompoundTag tag){
            var cells=new ArrayList<Cell>();var list=tag.getList("cells",Tag.TAG_COMPOUND);
            for(int i=0;i<Math.min(4096,list.size());i++){var c=list.getCompound(i);var attached=new ArrayList<Attachment>();var entries=c.getList("endpoints",Tag.TAG_COMPOUND);for(int j=0;j<Math.min(6,entries.size());j++){var e=entries.getCompound(j);attached.add(new Attachment(BlockPos.of(e.getLong("pos")),EndpointGeometry.Kind.values()[Math.clamp(e.getInt("kind"),0,4)]));}
                cells.add(new Cell(BlockPos.of(c.getLong("pos")),ConduitResource.values()[Math.clamp(c.getInt("resource"),0,1)],ConduitCladding.values()[Math.clamp(c.getInt("cladding"),0,2)],ConduitMode.values()[Math.clamp(c.getInt("mode"),0,1)],c.getInt("connections")&63,attached));}
            return new Snapshot(BlockPos.of(tag.getLong("min")),BlockPos.of(tag.getLong("max")),cells);
        }
    }
    public record Volume(BlockPos owner,ConduitResource resource,Material material,AABB bounds){}
    public record Fitting(BlockPos conduit,EndpointGeometry endpoint,List<Volume> volumes){public Fitting{volumes=List.copyOf(volumes);}}
    /** Identity-keyed immutable cache entry: renderer lookup must not hash every volume each frame. */
    public static final class Geometry {
        public static final Geometry EMPTY=new Geometry(Snapshot.EMPTY,List.of(),List.of());
        private final Snapshot snapshot;
        private final List<Volume> pipeSegments;
        private final List<Fitting> endpointFittings;
        private final List<Volume> occupied;
        public Geometry(Snapshot snapshot,List<Volume> pipeSegments,List<Fitting> endpointFittings){
            this.snapshot=snapshot;this.pipeSegments=List.copyOf(pipeSegments);this.endpointFittings=List.copyOf(endpointFittings);
            var all=new ArrayList<>(pipeSegments);endpointFittings.forEach(f->all.addAll(f.volumes()));occupied=List.copyOf(all);
        }
        public Snapshot snapshot(){return snapshot;}
        public List<Volume> pipeSegments(){return pipeSegments;}
        public List<Fitting> endpointFittings(){return endpointFittings;}
        public List<Volume> occupied(){return occupied;}
    }
    public static Snapshot compile(Level level,BlockPos min,BlockPos max,ConduitTopology topology){
        var compatible=new EnumMap<ConduitResource,Set<BlockPos>>(ConduitResource.class);for(var resource:ConduitResource.values())compatible.put(resource,new HashSet<>());
        for(var component:topology.components()){var set=compatible.get(component.resource());set.addAll(component.positions());}
        var cells=new ArrayList<Cell>();
        for(var pos:BlockPos.betweenClosed(min,max))if(level.getBlockState(pos).getBlock() instanceof ConduitBlock block){
            int connections=0;var attachments=new ArrayList<Attachment>();var mode=level.getBlockState(pos).getValue(ConduitBlock.MODE);
            for(var direction:Direction.values()){
                var neighbor=pos.relative(direction);if(!ConduitTopology.contains(neighbor,min,max))continue;
                if(level.getBlockState(neighbor).getBlock() instanceof ConduitBlock other && other.resource()==block.resource())connections|=1<<direction.ordinal();
                else if(mode==ConduitMode.TAP && compatible.get(block.resource()).contains(neighbor)){
                    var part=level.getBlockEntity(neighbor);var kind=part instanceof VatToolPortEntity?EndpointGeometry.Kind.TOOL:part instanceof VatBlenderEntity?EndpointGeometry.Kind.BLENDER:part instanceof VatFeedPortEntity?EndpointGeometry.Kind.FEED:EndpointGeometry.Kind.PASSIVE;
                    attachments.add(new Attachment(neighbor.immutable(),kind));
                }
            }
            attachments.sort(Comparator.comparing(Attachment::endpoint,ConduitTopology.POSITION));cells.add(new Cell(pos.immutable(),block.resource(),block.cladding(level.getBlockState(pos)),mode,connections,attachments));
        }
        cells.sort(Comparator.comparing(Cell::position,ConduitTopology.POSITION));return new Snapshot(min.immutable(),max.immutable(),cells);
    }
    public static Vec3 center(Cell cell,BlockPos min,BlockPos max){
        // Shell position owns the pipe offset; cladding changes panels, never the pipe body.
        var center=Vec3.atCenterOf(cell.position());
        var shell=VatCasingState.from(cell.position(),min,max);
        double inset=EndpointGeometry.PANEL_DEPTH+4.0/16+CLEARANCE;
        for(var direction:Direction.values())if(shell.outward(direction))center=center.add(Vec3.atLowerCornerOf(direction.getOpposite().getNormal()).scale(inset-.5));
        return center;
    }
    private static AABB box(Vec3 center,double radius){return new AABB(center,center).inflate(radius);}
    private static void volume(List<Volume> list,Cell cell,Material material,AABB bounds){list.add(new Volume(cell.position(),cell.resource(),material,bounds));}
    private static void rod(List<Volume> list,Cell cell,Vec3 from,Vec3 to,double radius,Material material){
        if(from.distanceToSqr(to)<1e-12)return;
        var axis=Math.abs(to.x-from.x)>1e-6?Direction.Axis.X:Math.abs(to.y-from.y)>1e-6?Direction.Axis.Y:Direction.Axis.Z;
        var bounds=new AABB(from,to);volume(list,cell,material,bounds.inflate(axis==Direction.Axis.X?0:radius,axis==Direction.Axis.Y?0:radius,axis==Direction.Axis.Z?0:radius));
    }
    private static Vec3 coordinate(Vec3 v,Direction.Axis axis,double value){return switch(axis){case X->new Vec3(value,v.y,v.z);case Y->new Vec3(v.x,value,v.z);case Z->new Vec3(v.x,v.y,value);};}
    private static double coordinate(Vec3 v,Direction.Axis axis){return switch(axis){case X->v.x;case Y->v.y;case Z->v.z;};}
    private static void path(List<Volume> list,Cell cell,Vec3 from,Vec3 to,List<Direction.Axis> axes,double radius,Material material){
        var cursor=from;
        for(var axis:axes){var next=coordinate(cursor,axis,coordinate(to,axis));rod(list,cell,cursor,next,radius,material);if(next.distanceToSqr(cursor)>1e-12 && !next.equals(to))volume(list,cell,material,box(next,radius+1.0/1024));cursor=next;}
    }
    private static void collar(List<Volume> list,Cell cell,Vec3 center,Direction.Axis axis){
        double r=4.0/16,half=1.0/32;
        if(cell.resource()==ConduitResource.ITEM){volume(list,cell,Material.COLLAR,box(center,r).deflate(axis==Direction.Axis.X?r-half:0,axis==Direction.Axis.Y?r-half:0,axis==Direction.Axis.Z?r-half:0));}
        else for(double offset:new double[]{-1.0/32,1.0/32}){
            var point=coordinate(center,axis,coordinate(center,axis)+offset);
            // Partition the rounded-square cuff into three disjoint rectangles; no overlapping cap faces.
            var transverse=Arrays.stream(Direction.Axis.values()).filter(d->d!=axis).toList();
            var u=transverse.get(0);var v=transverse.get(1);
            var middle=box(point,r).deflate(axis==Direction.Axis.X?r-half/2:0,axis==Direction.Axis.Y?r-half/2:0,axis==Direction.Axis.Z?r-half/2:0);
            middle=middle.deflate(v==Direction.Axis.X?1.0/16:0,v==Direction.Axis.Y?1.0/16:0,v==Direction.Axis.Z?1.0/16:0);
            volume(list,cell,Material.COLLAR,middle);
            for(double sign:new double[]{-1,1}){
                var stripCenter=coordinate(point,v,coordinate(point,v)+sign*(r-1.0/32));
                var strip=box(stripCenter,r).deflate(axis==Direction.Axis.X?r-half/2:0,axis==Direction.Axis.Y?r-half/2:0,axis==Direction.Axis.Z?r-half/2:0);
                strip=strip.deflate(u==Direction.Axis.X?1.0/16:0,u==Direction.Axis.Y?1.0/16:0,u==Direction.Axis.Z?1.0/16:0);
                strip=strip.deflate(v==Direction.Axis.X?r-1.0/32:0,v==Direction.Axis.Y?r-1.0/32:0,v==Direction.Axis.Z?r-1.0/32:0);
                volume(list,cell,Material.COLLAR,strip);
            }
        }
    }
    public static Geometry bake(Snapshot snapshot){
        var lookup=new HashMap<BlockPos,Cell>();snapshot.cells().forEach(c->lookup.put(c.position(),c));var pipes=new ArrayList<Volume>();var fittings=new ArrayList<Fitting>();
        for(var cell:snapshot.cells()){
            var start=center(cell,snapshot.min(),snapshot.max());volume(pipes,cell,Material.BODY,box(start,RADIUS));
            if(cell.mode()==ConduitMode.TAP)collar(pipes,cell,start,Direction.Axis.Y);
            for(var direction:Direction.values())if((cell.connections()&1<<direction.ordinal())!=0){
                var neighbor=lookup.get(cell.position().relative(direction));if(neighbor==null || neighbor.resource()!=cell.resource() || ConduitTopology.POSITION.compare(cell.position(),neighbor.position())>=0)continue;
                var end=center(neighbor,snapshot.min(),snapshot.max());double mid=(coordinate(start,direction.getAxis())+coordinate(end,direction.getAxis()))/2;
                var a=coordinate(start,direction.getAxis(),mid);var b=coordinate(end,direction.getAxis(),mid);
                rod(pipes,cell,start,a,RADIUS-1.0/1024,Material.BODY);path(pipes,cell,a,b,List.of(Direction.Axis.X,Direction.Axis.Y,Direction.Axis.Z),RADIUS-1.0/1024,Material.BODY);rod(pipes,cell,b,end,RADIUS-1.0/1024,Material.BODY);
                if(!a.equals(b)){volume(pipes,cell,Material.BODY,box(a,RADIUS));volume(pipes,cell,Material.BODY,box(b,RADIUS));}
                collar(pipes,cell,start.lerp(a,.5),direction.getAxis());
            }
            if(cell.mode()==ConduitMode.TAP)for(var attachment:cell.attachments()){
                var endpoint=EndpointGeometry.from(attachment.endpoint(),snapshot.min(),snapshot.max(),cell.position(),attachment.kind());if(endpoint==null)continue;
                var anchor=endpoint.serviceConnectionAnchor();var normal=endpoint.inward().getAxis();var axes=new ArrayList<Direction.Axis>();for(var axis:Direction.Axis.values())if(axis!=normal)axes.add(axis);axes.add(normal);
                var volumes=new ArrayList<Volume>();
                var branchStart=start;
                // An inward-adjacent pipe meets the side of the hub, outside the actor's centered corridor.
                if(cell.position().subtract(attachment.endpoint()).get(normal)!=0){
                    var tangent=anchor.subtract(Vec3.atCenterOf(attachment.endpoint()));
                    tangent=tangent.subtract(Vec3.atLowerCornerOf(endpoint.inward().getNormal()).scale(tangent.dot(Vec3.atLowerCornerOf(endpoint.inward().getNormal()))));
                    branchStart=start.add(tangent.normalize().scale(RADIUS));
                }
                path(volumes,cell,branchStart,anchor,axes,1.5/16,Material.BODY);
                // Last leg terminates exactly at the backside anchor; the small cuff stays on the chamber side.
                var collarCenter=anchor.add(Vec3.atLowerCornerOf(endpoint.inward().getNormal()).scale(1.0/32));
                volume(volumes,cell,Material.COLLAR,box(collarCenter,2.5/16).deflate(normal==Direction.Axis.X?2.0/16:0,normal==Direction.Axis.Y?2.0/16:0,normal==Direction.Axis.Z?2.0/16:0));
                fittings.add(new Fitting(cell.position(),endpoint,volumes));
            }
        }
        return new Geometry(snapshot,pipes,fittings);
    }
    private ConduitGeometry(){}
}
