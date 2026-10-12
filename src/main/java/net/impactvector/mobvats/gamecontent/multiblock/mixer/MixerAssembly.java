package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** One uniform, obstruction-free vessel selected at formation. Capacity cannot encode local cutouts. */
public record MixerAssembly(BlockPos impeller,int shaftLength,int diameter,int radiusReduction,int floorInsetPixels) {
    public MixerAssembly(BlockPos impeller,int shaftLength,int diameter,int radiusReduction){this(impeller,shaftLength,diameter,radiusReduction,0);}
    public MixerAssembly {
        impeller=impeller.immutable();
        if(shaftLength<1 || shaftLength>8)throw new IllegalArgumentException("Mixer Shaft depth must be 1 through 8");
        MixerVesselGeometry.forDiameter(diameter,radiusReduction);
        if(floorInsetPixels!=0 && floorInsetPixels!=MixerVesselGeometry.SHELL_DEPTH_PIXELS)throw new IllegalArgumentException("Invalid floor clearance");
    }
    public MixerVesselGeometry vessel(){return MixerVesselGeometry.forDiameter(diameter,radiusReduction);}
    public BlockPos bottom(){return impeller.below(shaftLength);}
    /** Pixel-grid extent, rounded up; never use this as a physical radius. */
    public int rotorRadiusPixels(){return vessel().gridRadiusPixels();}
    public double rotorRadius(){return vessel().faceRadius();}
    /** Rendering/liquid diagnostic only; never used for processing capacity. */
    public long geometricSweptVoxels(){return vessel().sweptColumns()*(shaftLength*16L-floorInsetPixels);}
    public double vesselBottom(){return -shaftLength+floorInsetPixels/16.0;}
    public double geometricSweptVolume(){return geometricSweptVoxels()/4096.0;}
    public int effectiveDiameter(){return diameter-2*radiusReduction;}
    /** Approximate footprint times installed mixing depth, independent of artwork and floor inset. */
    public int slotCount(){int width=effectiveDiameter()-1;return width*width*shaftLength/8;}
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putLong("impeller",impeller.asLong());tag.putInt("depth",shaftLength);
        tag.putInt("geometryVersion",3);tag.putInt("diameter",diameter);tag.putInt("radiusReduction",radiusReduction);
        tag.putInt("floorInsetPixels",floorInsetPixels);
        return tag;
    }
    public static MixerAssembly load(CompoundTag tag){
        // Circular/obstructed legacy caches require the normal structure-time rebuild.
        if(tag.getInt("geometryVersion")!=3)return null;
        int depth=tag.getInt("depth"),diameter=tag.getInt("diameter"),reduction=tag.getInt("radiusReduction");
        if(depth<1 || depth>8 || diameter<3 || diameter>9 || diameter%2==0 || reduction<0 || reduction>3
            || MixerVesselGeometry.reduced(diameter,reduction).isEmpty())return null;
        int inset=tag.getInt("floorInsetPixels");
        if(inset!=0 && inset!=MixerVesselGeometry.SHELL_DEPTH_PIXELS)return null;
        return new MixerAssembly(BlockPos.of(tag.getLong("impeller")),depth,diameter,reduction,inset);
    }
}
