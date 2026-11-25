package net.impactvector.mobvats.gamecontent.journal;

import java.util.*;
import java.util.function.*;

/** Owns each value until eviction/reload; disposal is synchronous on the caller's thread. */
public final class JournalCache<K, V> implements AutoCloseable {
    private final int limit;
    private final Consumer<V> dispose;
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, 0.75f, true);

    public JournalCache(int limit, Consumer<V> dispose) {
        if (limit < 1) throw new IllegalArgumentException("Cache limit");
        this.limit = limit;
        this.dispose = dispose;
    }

    public V get(K key, Function<K, V> factory) {
        V value = values.get(key);
        if (value != null) return value;
        value = factory.apply(key);
        values.put(key, value);
        if (values.size() > limit) {
            var i = values.entrySet().iterator();
            var oldest = i.next();
            i.remove();
            dispose.accept(oldest.getValue());
        }
        return value;
    }

    public int size() {
        return values.size();
    }

    @Override
    public void close() {
        values.values().forEach(dispose);
        values.clear();
    }
}
