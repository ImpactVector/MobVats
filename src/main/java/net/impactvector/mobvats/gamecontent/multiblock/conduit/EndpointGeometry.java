package net.impactvector.mobvats.gamecontent.multiblock.conduit;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatCasingState;
/** Fixed service mounts and mechanism origins. Coordinates are world-space, in blocks. */
public record EndpointGeometry(BlockPos position,Kind kind,Vec3 serviceConnectionAnchor,Optional<Vec3> interactionOrigin,Direction inward) {
    public enum Kind { PASSIVE, TOOL, FEED, BLENDER }
    public static final double PANEL_DEPTH=3.0/16, SERVICE_OFFSET=6.0/16;
    public static EndpointGeometry from(BlockPos pos,BlockPos min,BlockPos max,BlockPos conduit,Kind kind){
        var shell=VatCasingState.from(pos,min,max);
        if(shell.outwardFaces()==0){var center=Vec3.atCenterOf(pos);var delta=conduit.subtract(pos);var direction=Direction.getNearest(delta.getX(),delta.getY(),delta.getZ());return new EndpointGeometry(pos.immutable(),kind,center.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(.3)),kind==Kind.PASSIVE?Optional.empty():Optional.of(center),direction.getOpposite());}
        if(Integer.bitCount(shell.outwardFaces())!=1)return null;
        var outward=java.util.Arrays.stream(Direction.values()).filter(shell::outward).findFirst().orElseThrow();var inward=outward.getOpposite();
        var normal=Vec3.atLowerCornerOf(inward.getNormal());
        var interaction=Vec3.atCenterOf(pos).add(normal.scale(-.5+PANEL_DEPTH+.01));
        var offset=Vec3.atLowerCornerOf(conduit.subtract(pos));
        offset=offset.subtract(normal.scale(offset.dot(normal)));
        if(offset.lengthSqr()==0){var axis=VatCasingState.uAxis(outward);offset=axis==Direction.Axis.X?new Vec3(1,0,0):new Vec3(0,0,1);}
        var service=Vec3.atCenterOf(pos).add(normal.scale(-.5+PANEL_DEPTH+1.0/64)).add(offset.normalize().scale(SERVICE_OFFSET));
        return new EndpointGeometry(pos.immutable(),kind,service,kind==Kind.PASSIVE?Optional.empty():Optional.of(interaction),inward);
    }
    public Optional<AABB> interactionCorridor(){
        return interactionOrigin.map(start->{var end=start.add(Vec3.atLowerCornerOf(inward.getNormal()));return new AABB(start,end).inflate(.075);});
    }
}
