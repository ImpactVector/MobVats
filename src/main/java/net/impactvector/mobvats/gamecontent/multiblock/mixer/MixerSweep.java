package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Structure-time selection of a uniform polygon. No per-device capacity deductions. */
public final class MixerSweep {
    public record Obstruction(BlockPos owner, String kind, AABB bounds) {}

    public static final class Interference extends IllegalArgumentException {
        private final Obstruction obstruction;

        public Interference(Obstruction obstruction) {
            super(obstruction.kind() + " @ " + obstruction.owner().toShortString() + " " + obstruction.bounds());
            this.obstruction = obstruction;
        }

        public Obstruction obstruction() {
            return obstruction;
        }
    }

    public static List<Obstruction> occupied(
            Level level, BlockPos min, BlockPos max, ConduitGeometry.Geometry conduits) {
        var occupied = new ArrayList<Obstruction>();
        for (var volume : FluidBufferGeometry.compile(level, min, max))
            occupied.add(new Obstruction(volume.owner(), "buffer " + volume.kind(), volume.bounds()));
        for (var volume : conduits.occupied())
            occupied.add(new Obstruction(volume.owner(), "conduit " + volume.material(), volume.bounds()));
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(pos);
            var block = state.getBlock();
            if (MixerPlacement.interior(pos, min, max)
                    && !state.isAir()
                    && !(block instanceof ConduitBlock)
                    && !(block instanceof FluidBufferBlock)
                    && !state.is(MixerContent.block(MixerPartType.Shaft)))
                occupied.add(new Obstruction(pos.immutable(), "interior block", new AABB(pos)));
        }
        return List.copyOf(occupied);
    }

    public static MixerAssembly compile(
            Level level, BlockPos min, BlockPos max, BlockPos impeller, int depth, ConduitGeometry.Geometry conduits) {
        int diameter = Math.min(max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1);
        int floorInset = impeller.getY() - depth == min.getY() ? MixerVesselGeometry.SHELL_DEPTH_PIXELS : 0;
        return select(impeller, depth, diameter, floorInset, occupied(level, min, max, conduits));
    }
    /** Shared compiler for snapshots and charts. Occupied boxes are in world coordinates. */
    public static MixerAssembly compileOccupied(BlockPos impeller, int depth, int diameter, Collection<AABB> occupied) {
        return select(
                impeller,
                depth,
                diameter,
                0,
                occupied.stream()
                        .map(b -> new Obstruction(BlockPos.containing(b.getCenter()), "device", b))
                        .toList());
    }

    public static boolean intersects(
            MixerVesselGeometry vessel, BlockPos impeller, int depth, AABB worldBox, boolean safety) {
        return intersects(vessel, impeller, depth, 0, worldBox, safety);
    }

    public static boolean intersects(
            MixerVesselGeometry vessel, BlockPos impeller, int depth, int floorInset, AABB worldBox, boolean safety) {
        if (worldBox.maxY <= impeller.getY() - depth + floorInset / 16.0 || worldBox.minY >= impeller.getY())
            return false;
        var relative = worldBox.move(-impeller.getX() - .5, 0, -impeller.getZ() - .5);
        return vessel.intersects(
                safety
                        ? relative.inflate(
                                MixerVesselGeometry.SAFETY_CLEARANCE, 0, MixerVesselGeometry.SAFETY_CLEARANCE)
                        : relative);
    }

    public static MixerAssembly select(
            BlockPos impeller, int depth, int diameter, int floorInset, List<Obstruction> occupied) {
        Obstruction last = null;
        for (int reduction = 0; ; reduction++) {
            var candidate = MixerVesselGeometry.reduced(diameter, reduction);
            if (candidate.isEmpty()) throw new Interference(Objects.requireNonNull(last));
            last = null;
            for (var device : occupied)
                if (intersects(candidate.get(), impeller, depth, floorInset, device.bounds(), true)) {
                    last = device;
                    break;
                }
            if (last == null) return new MixerAssembly(impeller, depth, diameter, reduction, floorInset);
        }
    }

    private MixerSweep() {}
}
