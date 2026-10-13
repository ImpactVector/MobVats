package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Controller-owned event counters. The recent window measures assembled machine ticks only. */
public final class MachineMetrics {
    public static final int WINDOW_TICKS = 200;
    public static final String MILK = "milkMb",
            ITEMS = "routedItems",
            XP = "xp",
            WHEAT = "wheat",
            HARVEST_ITEMS = "harvestItems",
            HARVESTS = "harvests",
            DURABILITY = "durability";
    public static final String MEAT = "meatMb", WASTE = "wasteMb", BLENDER_HARVESTS = "blenderHarvests";

    private static final class Counter {
        long total, recent;
        final long[] buckets = new long[WINDOW_TICKS];
    }

    private final Map<String, Counter> counters = new TreeMap<>();
    private int cursor, elapsed;

    public void tick() {
        cursor = (cursor + 1) % WINDOW_TICKS;
        elapsed = Math.min(WINDOW_TICKS, elapsed + 1);
        for (var counter : counters.values()) {
            counter.recent -= counter.buckets[cursor];
            counter.buckets[cursor] = 0;
        }
    }

    public void record(String key, long amount) {
        if (amount <= 0) return;
        var counter = counters.computeIfAbsent(key, k -> new Counter());
        counter.total = sum(counter.total, amount);
        counter.recent = sum(counter.recent, amount);
        counter.buckets[cursor] = sum(counter.buckets[cursor], amount);
    }

    private static long sum(long a, long b) {
        return b > Long.MAX_VALUE - a ? Long.MAX_VALUE : a + b;
    }

    public long total(String key) {
        var c = counters.get(key);
        return c == null ? 0 : c.total;
    }

    public double perSecond(String key) {
        var c = counters.get(key);
        return c == null ? 0 : c.recent * 20.0 / Math.max(1, elapsed);
    }

    public double ratio(String numerator, String denominator) {
        return total(denominator) == 0 ? Double.NaN : (double) total(numerator) / total(denominator);
    }

    public int elapsedTicks() {
        return elapsed;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        counters.forEach((key, c) -> tag.putLong(key, c.total));
        return tag;
    }
    /** Lifetime totals persist; recent samples reset instead of inventing an offline rate. */
    public void load(CompoundTag tag) {
        counters.clear();
        cursor = elapsed = 0;
        for (var key : tag.getAllKeys()) {
            var c = new Counter();
            c.total = Math.max(0, tag.getLong(key));
            counters.put(key, c);
        }
    }

    public void assimilate(MachineMetrics source) {
        source.counters.forEach((key, c) -> {
            var target = counters.computeIfAbsent(key, k -> new Counter());
            target.total = sum(target.total, c.total);
        });
    }
}
