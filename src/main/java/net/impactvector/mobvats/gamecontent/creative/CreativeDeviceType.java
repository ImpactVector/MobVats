package net.impactvector.mobvats.gamecontent.creative;

public enum CreativeDeviceType {
    DRIVE,
    ITEM_SOURCE,
    FLUID_SOURCE,
    ITEM_SINK,
    FLUID_SINK;

    public String id() {
        return "creative_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean item() {
        return this == ITEM_SOURCE || this == ITEM_SINK;
    }

    public boolean source() {
        return this == ITEM_SOURCE || this == FLUID_SOURCE;
    }
}
