package net.impactvector.mobvats.gamecontent.multiblock.mixer;

/** Rotor presentation driven by the synchronized operating state, independent of recipe progress. */
public final class MixerRotorAnimation {
    public static final int CYCLE_TICKS=200, STARTUP_TICKS=40;
    public static final double OPERATING_DEGREES_PER_TICK=360.0/CYCLE_TICKS;
    private double activeTicks,angle,previousAngle;
    public void reset(){activeTicks=0;angle=0;previousAngle=0;}
    public void tick(MixerOperatingState state,int startup,int required){
        previousAngle=angle;
        double speed=state==MixerOperatingState.RUNNING?1:state==MixerOperatingState.SPINNING_UP?Math.clamp((double)startup/Math.max(1,required),0,1):0;
        angle+=OPERATING_DEGREES_PER_TICK*speed;
        if(previousAngle>=360){previousAngle-=360;angle-=360;}
    }
    public void tick(boolean active){
        previousAngle=angle;
        if(active){angle+=travel(activeTicks+1)-travel(activeTicks);activeTicks++;}
        else activeTicks=0;
        if(previousAngle>=360){previousAngle-=360;angle-=360;}
    }
    public double angle(float partial){return previousAngle+(angle-previousAngle)*Math.clamp(partial,0,1);}
    public static double speed(double ticks){
        double t=Math.clamp(ticks/STARTUP_TICKS,0,1);
        return OPERATING_DEGREES_PER_TICK*t*t*(3-2*t);
    }
    public static double travel(double ticks){
        double t=Math.clamp(ticks/STARTUP_TICKS,0,1);
        return OPERATING_DEGREES_PER_TICK*(STARTUP_TICKS*(t*t*t-.5*t*t*t*t)+Math.max(0,ticks-STARTUP_TICKS));
    }
}
