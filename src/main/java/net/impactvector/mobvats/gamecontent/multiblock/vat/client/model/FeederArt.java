package net.impactvector.mobvats.gamecontent.multiblock.vat.client.model;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import org.joml.*;

/** Decorative reel/spindle connections only. Never supplied to occupied geometry or routing. */
public final class FeederArt {
    public static final double RADIUS=ConduitGeometry.DIAMETER*.5/2;
    public static final double BODY_WIDTH=10.0/16;
    private static final Comparator<ConduitGeometry.Fitting> ORDER=Comparator.<ConduitGeometry.Fitting>comparingInt(f->f.volumes().getFirst().resource()==ConduitResource.FLUID?0:1)
        .thenComparing(ConduitGeometry.Fitting::conduit,ConduitTopology.POSITION);
    public record Terminal(EndpointGeometry endpoint,Direction inlet,List<AABB> bridge) {
        public Terminal {bridge=List.copyOf(bridge);}
    }
    public static Optional<Direction> inlet(ConduitGeometry.Geometry geometry,BlockPos port) {
        return geometry.endpointFittings().stream().filter(f->f.endpoint().kind()==EndpointGeometry.Kind.FEED&&f.endpoint().position().equals(port))
            .min(ORDER).map(FeederArt::inlet);
    }
    private static Direction inlet(ConduitGeometry.Fitting fitting) {
        var delta=fitting.conduit().subtract(fitting.endpoint().position());
        return Direction.getNearest(delta.getX(),delta.getY(),delta.getZ());
    }
    public static Vec3 spindleAttachment(EndpointGeometry endpoint,Direction inlet) {
        return Vec3.atCenterOf(endpoint.position()).add(Vec3.atLowerCornerOf(inlet.getNormal()).scale(BODY_WIDTH/2));
    }
    public static List<Terminal> terminals(ConduitGeometry.Geometry geometry) {
        var selected=new TreeMap<BlockPos,List<ConduitGeometry.Fitting>>(ConduitTopology.POSITION);
        geometry.endpointFittings().stream().filter(f->f.endpoint().kind()==EndpointGeometry.Kind.FEED)
            .sorted(ORDER)
            .forEach(f->selected.computeIfAbsent(f.endpoint().position(),k->new ArrayList<>()).add(f));
        return selected.values().stream().map(fittings->{
            var branches=new LinkedHashSet<AABB>();
            var inlet=inlet(fittings.getFirst());
            fittings.forEach(f->branches.addAll(bridge(f.endpoint(),inlet)));
            return new Terminal(fittings.getFirst().endpoint(),inlet,List.copyOf(branches));
        }).toList();
    }
    private static List<AABB> bridge(EndpointGeometry endpoint,Direction inlet) {
        var result=new ArrayList<AABB>();var cursor=endpoint.serviceConnectionAnchor();
        var target=spindleAttachment(endpoint,inlet);
        // The last leg runs along the spindle axis into the centered 10px reel.
        var axes=new ArrayList<Direction.Axis>();
        for(var axis:Direction.Axis.values())if(axis!=inlet.getAxis())axes.add(axis);
        axes.add(inlet.getAxis());
        for(var axis:axes) {
            var next=switch(axis) {case X->new Vec3(target.x,cursor.y,cursor.z);case Y->new Vec3(cursor.x,target.y,cursor.z);case Z->new Vec3(cursor.x,cursor.y,target.z);};
            if(next.distanceToSqr(cursor)>1e-12)result.add(new AABB(cursor,next).inflate(axis==Direction.Axis.X?0:RADIUS,axis==Direction.Axis.Y?0:RADIUS,axis==Direction.Axis.Z?0:RADIUS));
            cursor=next;
        }
        return List.copyOf(result);
    }
    public static BakedQuad orientInlet(BakedQuad quad,Direction inlet) {
        if(inlet==Direction.EAST)return quad;
        var rotation=new Quaternionf().rotationTo(new Vector3f(1,0,0),new Vector3f(inlet.getStepX(),inlet.getStepY(),inlet.getStepZ()));
        var vertices=quad.getVertices();int stride=vertices.length/4;var points=new Vector3f[4];
        for(int v=0;v<4;v++)points[v]=rotation.transform(new Vector3f(Float.intBitsToFloat(vertices[v*stride]),Float.intBitsToFloat(vertices[v*stride+1]),Float.intBitsToFloat(vertices[v*stride+2])).sub(.5f,.5f,.5f)).add(.5f,.5f,.5f);
        return FixtureModel.withPoints(quad,points);
    }
    private FeederArt() {}
}
