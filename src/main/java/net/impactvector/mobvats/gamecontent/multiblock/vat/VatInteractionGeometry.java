package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.impactvector.mobvats.gamecontent.mob.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.EntityType;

/** Shared static origins, targets, sag and thickness for presentation and route obstruction. */
public final class VatInteractionGeometry {
    public static final double ARM_RADIUS=.075, FEED_WIDTH=.09, FEED_SAG=.08;
    public static final int FEED_SEGMENTS=12;
    public static VatPresentation.Link link(BlockPos min,BlockPos max,MobAssembly assembly,BlockPos port,boolean assigned,boolean feed){
        var shell=VatCasingState.from(port,min,max);
        if(Integer.bitCount(shell.outwardFaces())!=1 || shell.outward(Direction.UP) || shell.outward(Direction.DOWN)){
            var base=net.minecraft.world.phys.Vec3.atCenterOf(port);var body=assembly.attachmentBounds();
            var aim=feed?MobRenderDefinition.forEntity(assembly.head().getIdentity()).headAnchor(body,assembly.head().getSpecimenFacing(),assembly.head().lifeStage()):body.getCenter();
            var travel=aim.subtract(base).normalize();var target=feed?aim:body.clip(base,aim).orElse(aim).add(travel.scale(.12));
            return new VatPresentation.Link(port.immutable(),base,target,Direction.getNearest(travel.x,travel.y,travel.z),assigned);
        }
        var endpoint=EndpointGeometry.from(port,min,max,port.relative(java.util.Arrays.stream(Direction.values()).filter(shell::outward).findFirst().orElseThrow().getOpposite()),feed?EndpointGeometry.Kind.FEED:EndpointGeometry.Kind.TOOL);
        var direction=net.minecraft.world.phys.Vec3.atLowerCornerOf(endpoint.inward().getNormal());
        var base=endpoint.interactionOrigin().orElseThrow();var target=base.add(direction.scale(.28));
        if(assigned){
            var body=assembly.attachmentBounds();
            if(feed)target=MobRenderDefinition.forEntity(assembly.head().getIdentity()).headAnchor(body,assembly.head().getSpecimenFacing(),assembly.head().lifeStage());
            else{
                // High ports can miss a calf on a horizontal ray. Aim at its body anchor
                // rather than leaving a disconnected adult-height arm in the chamber.
                var aim=assembly.head().lifeStage()==net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.JUVENILE
                    ? MobRenderDefinition.forEntity(assembly.head().getIdentity()).bodyAnchor(body) : base.add(direction.scale(32));
                var travel=aim.subtract(base).normalize();
                var hit=body.clip(base,aim);
                if(hit.isPresent())target=hit.get().add(travel.scale(.12));
                else if(body.contains(base))target=base.add(travel.scale(.12));
            }
        }
        return new VatPresentation.Link(port.immutable(),base,target,endpoint.inward(),assigned);
    }
    public static CableCurve feedCurve(VatPresentation.Link link){return CableCurve.between(link.base(),link.target(),FEED_SAG,FEED_SEGMENTS);}
    public static PhysicalRoute route(VatPresentation.Link link,boolean feed){return feed?PhysicalRoute.cable(feedCurve(link),FEED_WIDTH):PhysicalRoute.line(link.base(),link.target(),ARM_RADIUS);}
    private VatInteractionGeometry(){}
}
