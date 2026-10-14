package net.impactvector.mobvats.render;

import java.util.HashMap;
import java.util.Map;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatVisualCache;

/** Direct ownership assertions: no GC requests, timeouts, game bootstrap or client launch. */
public final class VatVisualCacheTest {
    private static final class World {
        final Map<Integer, Head> loaded = new HashMap<>();
    }

    private static final class Head {
        final World level;
        final int chunk;
        long revision;

        Head(World level, int chunk, long revision) {
            this.level = level;
            this.chunk = chunk;
            this.revision = revision;
        }
    }

    private record Specimen(World level) {}

    private record Visual(Specimen specimen) {}

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--baseline")) {
            var original = new java.util.WeakHashMap<Head, Visual>();
            var oldWorld = new World();
            var head = new Head(oldWorld, 1, 0);
            oldWorld.loaded.put(1, head);
            original.put(head, new Visual(new Specimen(oldWorld)));
            // Original renderer has no chunk/world retirement callback. A value directly reaches its key.
            require(
                    original.values().iterator().next().specimen.level.loaded.get(1) == head,
                    "Original cache graph does not reproduce indirect key retention");
            World currentWorld = oldWorld;
            currentWorld = new World();
            require(original.get(head).specimen.level != currentWorld, "Baseline harness did not switch worlds");
            require(
                    original.isEmpty(),
                    "Original weak cache retains a specimen after world retirement without another render call");
            return;
        }
        var cache = new VatVisualCache<Head, Visual>();
        var world = new World();
        cache.world(world);
        var first = new Head(world, 1, 0);
        world.loaded.put(1, first);
        var state = new Visual(new Specimen(world));
        cache.put(first, state);
        require(
                cache.get(first) == state
                        && cache.get(first).specimen.level.loaded.get(1) == first,
                "Harness must contain the original value -> Mob -> Level -> Head retention path");
        cache.world(world);
        require(cache.get(first) == state, "Stable world rebuilt a cached visual");
        first.revision++;
        cache.retire((head, visual) -> head.revision != 0);
        require(cache.size() == 0, "Specimen revision retained obsolete visual");
        cache.put(first, state);

        var replacement = new Head(world, 1, 1);
        world.loaded.put(1, replacement);
        cache.retire(head -> world.loaded.get(head.chunk) != head);
        require(cache.size() == 0 && cache.get(first) == null, "Head replacement retained old specimen");
        cache.put(replacement, new Visual(new Specimen(world)));
        var second = new Head(world, 2, 0);
        world.loaded.put(2, second);
        var secondState = new Visual(new Specimen(world));
        cache.put(second, secondState);
        world.loaded.remove(1);
        cache.retire(head -> head.chunk == 1);
        require(cache.size() == 1 && cache.get(second) == secondState, "Chunk retirement affected another chunk");
        var reloaded = new Head(world, 1, 1);
        world.loaded.put(1, reloaded);
        cache.put(reloaded, new Visual(new Specimen(world)));
        require(cache.get(reloaded) != state && cache.get(replacement) == null, "Reload reused retired state");

        cache.world(new World());
        require(cache.size() == 0 && cache.get(second) == null, "World replacement retained a previous Level");
        cache.world(world);
        cache.put(second, secondState);
        cache.clear();
        require(cache.size() == 0, "Resource reload retained specimen objects");
        cache.world(world);
        cache.put(second, secondState);
        cache.world(null);
        require(cache.size() == 0, "Disconnect retained specimen objects");
        System.out.println("Vat visual cache lifecycle checks passed");
    }
}
