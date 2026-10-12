package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
/** Canonical semantic uses. Legacy configuration values normalize at their boundary. */
public final class PassiveUses {
    public static final String HARVEST="mobvats:kill", SECONDARY="mobvats:tool", DRAIN="mobvats:drain", BLEND="mobvats:blend", SHEAR="mobvats:secondary", FEED="mobvats:feed";
    public static String canonical(String use) { return switch(use) {
        case "cowHarvest" -> HARVEST; case "cowMilk" -> SECONDARY; case "cowDrain" -> DRAIN; default -> use;
    }; }
    public static final int SWORD_PRIORITY=100, BLENDER_PRIORITY=200;
    private PassiveUses() {}
}
