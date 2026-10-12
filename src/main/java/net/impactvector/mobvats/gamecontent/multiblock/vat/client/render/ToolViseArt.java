package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Decorative coordinates only, expressed in the existing arm's local frame. */
public final class ToolViseArt {
    public static final double WIDTH=7, HEIGHT=5, DEPTH=5, JAW_GAP=.45;
    // Native centered item coordinates, before the existing mechanical Y rotation.
    // Generated-item hilt (3,3), lower-left handles (5,5), upper-left rim (5,11),
    // and reversed syringe rear edge (16,8), in bottom-left pixel coordinates.
    public static final double GRIP_X=-3.0/16, GRIP_Y=-3.0/16;
    public static final double BUCKET_GRIP_X=-3.0/16, BUCKET_GRIP_Y=3.0/16;
    public static final double SWORD_GRIP_X=-5.0/16, SWORD_GRIP_Y=-5.0/16;
    public static final double REVERSE_GRIP_X=.5, REVERSE_GRIP_Y=0;
    public static Vec3 nativeGrip(boolean sword,boolean reverse,boolean bucket) {
        return reverse?new Vec3(REVERSE_GRIP_X,REVERSE_GRIP_Y,0):
            sword?new Vec3(SWORD_GRIP_X,SWORD_GRIP_Y,0):
            bucket?new Vec3(BUCKET_GRIP_X,BUCKET_GRIP_Y,0):new Vec3(GRIP_X,GRIP_Y,0);
    }
    public static Vec3 center(Vector3f scale,double length,boolean sword,boolean reverse) {
        return center(scale,length,sword,reverse,false);
    }
    public static Vec3 center(Vector3f scale,double length,boolean sword,boolean reverse,boolean bucket) {
        double itemY=sword?scale.y()*5.0/16:0;
        double itemZ=length+(sword?scale.x()*5.0/16:Math.abs(scale.x())/2);
        var grip=nativeGrip(sword,reverse,bucket);
        return new Vec3(0,itemY+scale.y()*grip.y,itemZ+(reverse?-1:1)*scale.x()*grip.x);
    }
    private ToolViseArt() {}
}
