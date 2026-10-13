package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;

/** Authoritative Mixer cell policy; physical sweep geometry is compiled only after validation. */
public final class MixerPlacement {
    public static boolean shell(PartPosition position) {
        return position != PartPosition.Interior && position != PartPosition.Unknown;
    }

    public static boolean interior(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() > min.getX()
                && pos.getX() < max.getX()
                && pos.getY() > min.getY()
                && pos.getY() < max.getY()
                && pos.getZ() > min.getZ()
                && pos.getZ() < max.getZ();
    }

    public static boolean allows(MixerPartType type, PartPosition position) {
        boolean side = position == PartPosition.NorthFace
                || position == PartPosition.SouthFace
                || position == PartPosition.EastFace
                || position == PartPosition.WestFace;
        return switch (type) {
            case Casing, Glass -> shell(position);
            case Controller, Intake -> side;
            case Impeller, Drive, CombustionDrive, FeDrive -> position == PartPosition.TopFace;
            case Shaft -> position == PartPosition.Interior || position == PartPosition.BottomFace;
            default -> side || position == PartPosition.BottomFace || position == PartPosition.TopFace;
        };
    }

    public static boolean allows(
            ConduitCladding cladding, ConduitShell family, BlockPos pos, BlockPos min, BlockPos max) {
        if (!ConduitTopology.contains(pos, min, max)) return false;
        boolean inside = interior(pos, min, max);
        return cladding == ConduitCladding.BARE ? inside : family == ConduitShell.MIXER && !inside;
    }

    public static boolean fullMachinery(MixerPartType type) {
        return type == MixerPartType.Impeller
                || type == MixerPartType.Drive
                || type == MixerPartType.CombustionDrive
                || type == MixerPartType.FeDrive;
    }

    private MixerPlacement() {}
}
