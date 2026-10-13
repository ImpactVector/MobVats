package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.Optional;
import net.minecraft.core.BlockPos;

/** Compile-time physical tests only. No world queries, renderer dependencies, or validity mutations. */
public final class RouteIntersection {
    public record Blocker(BlockPos position, ConduitResource resource, boolean fitting) {}

    public static Optional<Blocker> first(
            PhysicalRoute route, ConduitGeometry.Geometry geometry, BlockPos ownEndpoint) {
        // Stable snapshot/volume ordering determines a reproducible first blocker.
        for (var volume : geometry.pipeSegments())
            if (intersects(route, volume)) return Optional.of(new Blocker(volume.owner(), volume.resource(), false));
        for (var fitting : geometry.endpointFittings()) {
            if (fitting.endpoint().position().equals(ownEndpoint)) continue;
            for (var volume : fitting.volumes())
                if (intersects(route, volume)) return Optional.of(new Blocker(volume.owner(), volume.resource(), true));
        }
        return Optional.empty();
    }

    public static boolean intersects(PhysicalRoute route, ConduitGeometry.Volume volume) {
        var box = volume.bounds().inflate(route.radius());
        for (int i = 1; i < route.points().size(); i++) {
            var start = route.points().get(i - 1);
            var end = route.points().get(i);
            if (box.contains(start) || box.contains(end) || box.clip(start, end).isPresent()) return true;
        }
        return false;
    }

    private RouteIntersection() {}
}
