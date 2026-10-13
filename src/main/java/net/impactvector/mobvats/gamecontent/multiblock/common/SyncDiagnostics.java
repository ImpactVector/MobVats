package net.impactvector.mobvats.gamecontent.multiblock.common;

import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLLoader;

/** Explicit development capture; inert outside a scope and in production. Owners retire on close. */
public final class SyncDiagnostics implements AutoCloseable {
    private static final ThreadLocal<SyncDiagnostics> CAPTURE = new ThreadLocal<>();
    private final Map<String, Long> counts = new HashMap<>();
    private final Map<String, Long> lastTick = new HashMap<>();
    private final java.util.Set<Object> owners = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static SyncDiagnostics capture(Object... owners) {
        if (FMLLoader.isProduction()) throw new IllegalStateException("Development diagnostics only");
        if (CAPTURE.get() != null) throw new IllegalStateException("Nested diagnostic capture");
        var capture = new SyncDiagnostics();
        java.util.Collections.addAll(capture.owners, owners);
        CAPTURE.set(capture);
        return capture;
    }

    public static void record(String machine, String event, Object owner, long tick) {
        var capture = CAPTURE.get();
        if (capture == null || !capture.owners.isEmpty() && !capture.owners.contains(owner)) return;
        var key = machine + "." + event;
        capture.counts.merge(key, 1L, Long::sum);
        var repeated = key + "." + System.identityHashCode(owner);
        var previous = capture.lastTick.put(repeated, tick);
        if (previous != null && previous == tick) capture.counts.merge(key + ".sameTick", 1L, Long::sum);
    }

    public long count(String key) {
        return counts.getOrDefault(key, 0L);
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(counts);
    }

    @Override
    public void close() {
        CAPTURE.remove();
        owners.clear();
    }

    private SyncDiagnostics() {}
}
