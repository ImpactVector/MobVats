package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import net.minecraft.util.StringRepresentable;

public enum ConduitMode implements StringRepresentable {
    TRUNK,
    TAP;

    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
