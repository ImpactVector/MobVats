package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Renderer-owned specimen states. Weak keys supplement explicit client lifecycle retirement. */
public final class VatVisualCache<K, V> {
    private final WeakHashMap<K, V> states = new WeakHashMap<>();
    private WeakReference<Object> world = new WeakReference<>(null);

    public void world(Object current) {
        if (world.get() != current) {
            clear();
            world = new WeakReference<>(current);
        }
    }

    public V get(K head) {
        return states.get(head);
    }

    public void put(K head, V state) {
        states.put(head, state);
    }

    public void remove(K head) {
        states.remove(head);
    }

    public void retire(Predicate<K> invalid) {
        states.keySet().removeIf(invalid);
    }

    public int size() {
        return states.size();
    }

    public long count(java.util.function.Predicate<V> predicate) {
        return states.values().stream().filter(predicate).count();
    }

    public void retire(java.util.function.BiPredicate<K, V> invalid) {
        states.entrySet().removeIf(entry -> invalid.test(entry.getKey(), entry.getValue()));
    }

    public void clear() {
        states.clear();
        world.clear();
    }
}
