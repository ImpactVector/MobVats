package net.impactvector.mobvats.gamecontent.multiblock.mixer;
import it.zerono.mods.zerocore.lib.block.multiblock.IMultiblockPartType;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public enum MixerPartType implements IMultiblockPartType {
    Controller,Casing,Glass,FluidPort,ItemPort,Intake,Impeller,Shaft,Drive,CombustionDrive,FeDrive,FluidVoid,PullPump,PushPump;
    public String getSerializedName(){return this==Drive?"creative_drive":"mixer_"+name().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(java.util.Locale.ROOT);}
    public String getTranslationKey(){var shared=net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.shared(this);return "block.mobvats."+(shared==null?getSerializedName():net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.id(shared));}
    public AbstractMixerEntity createTileEntity(BlockState state,BlockPos pos){return switch(this){
        case Controller -> new MixerControllerEntity(pos,state);
        case Casing -> new MixerCasingEntity(pos,state);
        case Glass -> new MixerGlassEntity(pos,state);
        case FluidPort -> new MixerFluidPortEntity(this,pos,state);
        case ItemPort -> new MixerItemPortEntity(this,pos,state);
        case Intake -> new MixerIntakeEntity(pos,state);
        case Impeller -> new MixerImpellerEntity(pos,state);
        case Shaft -> new MixerShaftEntity(pos,state);
        case Drive -> new MixerCreativeDriveEntity(pos,state);
        case CombustionDrive -> new MixerCombustionDriveEntity(pos,state);
        case FeDrive -> new MixerFeDriveEntity(pos,state);
        case FluidVoid -> new MixerFluidVoidEntity(pos,state);
        case PullPump,PushPump -> new MixerPumpEntity(this,pos,state);
    };}
    public boolean allows(PartPosition pos){
        return MixerPlacement.allows(this,pos);
    }
}
