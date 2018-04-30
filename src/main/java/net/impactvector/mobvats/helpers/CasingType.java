package net.impactvector.mobvats.helpers;

import it.zerono.mods.zerocore.api.multiblock.rectangular.PartPosition;
import net.minecraft.util.IStringSerializable;

public enum CasingType implements IStringSerializable {

    Single,
    Wall,
    FrameEW,
    FrameSN,
    FrameUD,
    Corner;

    CasingType() {

        this._name = this.name().toLowerCase();
    }

    public static CasingType from(PartPosition position) {

        if (position.isFace())
            return CasingType.Wall;

        switch (position) {

            case FrameCorner:
                return CasingType.Corner;

            case FrameEastWest:
                return CasingType.FrameEW;

            case FrameSouthNorth:
                return CasingType.FrameSN;

            case FrameUpDown:
                return CasingType.FrameUD;

            default:
                return CasingType.Single;
        }
    }

    @Override
    public String toString() {

        return this._name;
    }

    @Override
    public String getName() {

        return this._name;
    }

    private final String _name;
}
