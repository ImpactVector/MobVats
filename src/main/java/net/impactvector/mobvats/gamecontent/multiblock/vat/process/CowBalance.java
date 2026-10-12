package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

/** Provisional Slice 2 balance, in active server ticks and millibuckets. */
public final class CowBalance {
    public static final int PROCESS_CADENCE = 10;
    public static final int MILK_INTERVAL = 1200;
    public static final int MILK_PER_INTERVAL = 1000;
    public static final int FLUID_CAPACITY = 4000;
    public static final int WHEAT_COST = 1;
    public static final int REPLACEMENT_INTERVAL = 1200;
    public static final String MILK_USE = PassiveUses.SECONDARY;
    public static final String HARVEST_USE = PassiveUses.HARVEST;
    public static final int SWORD_PRIORITY = 100, BLENDER_PRIORITY = 200;
    public static final int MEAT_PER_HARVEST = 1000, BLENDER_XP = 0;
    public static final int WASTE_INTERVAL = 1200, WASTE_PER_INTERVAL = 250;
    public static final String DRAIN_USE = PassiveUses.DRAIN;
    private CowBalance() {}
}
