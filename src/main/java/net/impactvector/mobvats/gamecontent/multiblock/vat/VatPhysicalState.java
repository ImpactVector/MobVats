package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;

/** Compact rejected-route reasons; no rejected assignments or alternative endpoint arrays retained. */
public record VatPhysicalState(ConduitGeometry.Geometry geometry, List<Blocked> blocked, Set<BlockPos> inactiveHeads) {
    public static final VatPhysicalState EMPTY = new VatPhysicalState(ConduitGeometry.Geometry.EMPTY, List.of());

    public enum Kind {
        HARNESS,
        TOOL,
        FEED,
        BLENDER
    }

    public record Blocked(Kind kind, BlockPos part, BlockPos head, RouteIntersection.Blocker blocker) {
        public String description() {
            return (kind == Kind.HARNESS
                            ? "Strap"
                            : kind == Kind.TOOL ? "Tool Port" : kind == Kind.FEED ? "Feed route" : "Blender route")
                    + " @ " + part.toShortString() + " blocked by "
                    + (blocker.resource() == ConduitResource.ITEM ? "Item" : "Fluid")
                    + " Conduit @ " + blocker.position().toShortString() + " -> head @ " + head.toShortString();
        }
    }

    public VatPhysicalState(ConduitGeometry.Geometry geometry, List<Blocked> blocked) {
        this(
                geometry,
                blocked,
                blocked.stream()
                        .filter(b -> b.kind() == Kind.HARNESS)
                        .map(Blocked::head)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    public VatPhysicalState {
        blocked = List.copyOf(blocked);
        inactiveHeads = Set.copyOf(inactiveHeads);
    }

    public static VatPhysicalState harnesses(List<MobAssembly> assemblies, ConduitGeometry.Geometry geometry) {
        var blocked = new java.util.ArrayList<Blocked>();
        for (var assembly : assemblies) {
            var body = assembly.attachmentBounds();
            var curves = HarnessStraps.curves(
                    assembly.harnessPositions(),
                    assembly.occupiedBounds(),
                    body,
                    net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition.forEntity(
                            assembly.head().getIdentity()));
            for (int i = 0; i < curves.size(); i++) {
                var curve = curves.get(i);
                var hit = RouteIntersection.first(PhysicalRoute.cable(curve, HarnessStraps.THICKNESS), geometry, null);
                if (hit.isPresent()) {
                    var source =
                            assembly.harnessPositions().get(assembly.harnesses().size() == 1 ? 0 : i);
                    blocked.add(new Blocked(
                            Kind.HARNESS, source, assembly.head().getBlockPos().immutable(), hit.get()));
                    break;
                }
            }
        }
        return new VatPhysicalState(geometry, blocked);
    }

    public boolean operational(BlockPos head) {
        return !inactiveHeads.contains(head);
    }

    public boolean blockedPart(BlockPos part, BlockPos head) {
        return blocked.stream().anyMatch(b -> b.part().equals(part) && b.head().equals(head));
    }
}
