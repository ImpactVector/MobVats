package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.nbt.*;

/** Per-specimen/action fractional mB; plans join the biological output transaction. */
public final class FoulingRemainders {
    private record Entry(String configuration, double remainder) {}

    private final Map<String, Entry> values = new TreeMap<>();

    public static double multiplier(double happiness, double maximum) {
        if (!Double.isFinite(happiness) || !Double.isFinite(maximum) || maximum < 1)
            throw new IllegalArgumentException("Invalid fouling multiplier");
        return 1 + (maximum - 1) * Math.clamp((2 - happiness) / 1.5, 0, 1);
    }

    public record Plan(int amount, ProcessTransaction.Step step) {}

    public Plan plan(String action, String configuration, int base, double happiness, double maximum) {
        if (base < 0) throw new IllegalArgumentException("Negative fouling");
        String key = configuration + "/" + base + "/" + maximum;
        var before = values.get(action);
        double carry = before != null && before.configuration().equals(key) ? before.remainder() : 0;
        double total = base * multiplier(happiness, maximum) + carry;
        double whole = Math.floor(total + 1e-10);
        // Medium capacity is int-sized. Discard unavailable whole mB without creating debt,
        // even for large valid base/maximum overrides; retain only the fractional part.
        int amount = (int) Math.min(Integer.MAX_VALUE, whole);
        var after = new Entry(key, Double.isFinite(total) ? Math.max(0, total - whole) : 0);
        return new Plan(
                amount,
                new ProcessTransaction.Step(
                        values,
                        () -> Objects.equals(values.get(action), before),
                        () -> {
                            values.put(action, after);
                            return true;
                        },
                        () -> {
                            if (before == null) values.remove(action);
                            else values.put(action, before);
                        }));
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        values.forEach((action, value) -> {
            var entry = new CompoundTag();
            entry.putString("configuration", value.configuration());
            entry.putDouble("remainder", value.remainder());
            tag.put(action, entry);
        });
        return tag;
    }

    public void load(CompoundTag tag) {
        values.clear();
        for (var action : tag.getAllKeys()) {
            var entry = tag.getCompound(action);
            double carry = entry.getDouble("remainder");
            if (Double.isFinite(carry) && carry >= 0 && carry < 1)
                values.put(action, new Entry(entry.getString("configuration"), carry));
        }
    }
}
