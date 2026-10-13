package net.impactvector.mobvats.gamecontent.multiblock.common;

import java.util.*;

/** A role subset of ZeroCore's parts, with stable ordering between attachment events.
 * Controllers retire it on pause/break and rebuild it from ZeroCore on assembly.
 * It owns no resources, topology, or independent ticking. */
public final class AttachedPartCollection<T> {
    private final Class<T> type;
    private final Comparator<? super T> order;
    private final Set<T> members = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<T> ordered = List.of();
    private boolean dirty;

    public AttachedPartCollection(Class<T> type, Comparator<? super T> order) {
        this.type = type;
        this.order = order;
    }

    public void add(Object part) {
        if (type.isInstance(part) && members.add(type.cast(part))) dirty = true;
    }

    public void remove(Object part) {
        if (members.remove(part)) {
            ordered = List.of();
            dirty = true;
        }
    }

    public void clear() {
        members.clear();
        ordered = List.of();
        dirty = false;
    }

    public void rebuild(Collection<?> parts) {
        clear();
        parts.forEach(this::add);
    }

    public List<T> parts() {
        if (dirty) {
            ordered = members.stream().sorted(order).toList();
            dirty = false;
        }
        return ordered;
    }
}
