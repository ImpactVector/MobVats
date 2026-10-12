package net.impactvector.mobvats.gamecontent.mob;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Body-relative attachment metadata. Register optional entity overrides during mod setup. */
public record MobRenderDefinition(Vec3 bodyAnchorFraction, boolean vanillaCowHead) {
    public MobRenderDefinition(Vec3 fraction){this(fraction,false);}
    public static final MobRenderDefinition DEFAULT = new MobRenderDefinition(new Vec3(0.5, 0.55, 0.5));
    private static final Map<ResourceLocation, MobRenderDefinition> OVERRIDES = new ConcurrentHashMap<>();
    static { OVERRIDES.put(ResourceLocation.withDefaultNamespace("cow"),new MobRenderDefinition(new Vec3(.5,.55,.5),true)); }
    public MobRenderDefinition {
        if (bodyAnchorFraction == null || !inside(bodyAnchorFraction.x) || !inside(bodyAnchorFraction.y)
                || !inside(bodyAnchorFraction.z)) throw new IllegalArgumentException("Body anchor fractions must lie strictly inside (0,1)");
    }
    private static boolean inside(double value) { return Double.isFinite(value) && value > 0 && value < 1; }
    public Vec3 bodyAnchor(AABB body) {
        return new Vec3(body.minX + body.getXsize() * bodyAnchorFraction.x,
            body.minY + body.getYsize() * bodyAnchorFraction.y,
            body.minZ + body.getZsize() * bodyAnchorFraction.z);
    }
    /** Neutral adult CowModel head-box center, in world coordinates for the placed specimen facing.
     * CowModel pivot (0,4,-8) plus box center (0,0,-3), at 16 model pixels/block;
     * LivingEntityRenderer flips model Y/Z into world up/forward and lifts it 1.501 blocks.
     * Current specimens use the vanilla cow renderer; additional species need model-specific metadata.
     */
    public Vec3 headAnchor(AABB body, Direction facing) {
        return headAnchor(body,facing,net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.ADULT);
    }
    /** CowModel's juvenile head is NOT scaled: AgeableListModel translates it by
     * (0,10,4) model pixels before the renderer flips Y/Z. Its body alone scales 1/2.
     * Neutral anchors remain independent of gaze and never require a live entity. */
    public Vec3 headAnchor(AABB body,Direction facing,net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage stage) {
        if(!vanillaCowHead)return new Vec3((body.minX+body.maxX)/2+facing.getStepX()*body.getXsize()*.4,
            body.minY+body.getYsize()*.8,(body.minZ+body.maxZ)/2+facing.getStepZ()*body.getZsize()*.4);
        boolean juvenile=stage==net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.JUVENILE;
        double forward=(juvenile?7.0:11.0)/16,down=(juvenile?14.0:4.0)/16;
        return new Vec3((body.minX+body.maxX)/2+facing.getStepX()*forward,
            body.minY+1.501-down,(body.minZ+body.maxZ)/2+facing.getStepZ()*forward);
    }
    public static AABB attachmentBounds(ResourceLocation identity,Vec3 position,net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage stage) {
        var dimensions=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(identity).getDimensions();
        if(stage==net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.JUVENILE)dimensions=dimensions.scale(.5f);
        return dimensions.makeBoundingBox(position);
    }
    public static MobRenderDefinition forEntity(ResourceLocation identity) {
        return identity == null ? DEFAULT : OVERRIDES.getOrDefault(identity, DEFAULT);
    }
    public static void registerOverride(ResourceLocation identity, MobRenderDefinition definition) {
        OVERRIDES.put(java.util.Objects.requireNonNull(identity), java.util.Objects.requireNonNull(definition));
    }
}
