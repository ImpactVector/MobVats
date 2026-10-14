package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import it.zerono.mods.zerocore.lib.data.geometry.CuboidBoundingBox;
import it.zerono.mods.zerocore.lib.multiblock.*;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import java.util.*;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Structure-time bridge and bounded change notifications; never called by resource processing. */
@EventBusSubscriber(modid = MobVats.MOD_ID)
public final class ConduitStructure {
    private static final Set<ConduitCuboidController<?>> CONTROLLERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ConduitCuboidController<?>, Boolean> DIRTY = new WeakHashMap<>();
    private static final Set<net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer>
            MIXER_GEOMETRY_DIRTY = Collections.newSetFromMap(new WeakHashMap<>());

    @SuppressWarnings("unchecked")
    static <C extends IMultiblockController<C>> IMultiblockRegistry<C> registry() {
        // ZeroCore exposes this sided service without its controller type parameter.
        return (IMultiblockRegistry<C>)
                it.zerono.mods.zerocore.lib.multiblock.registry.MultiblockRegistry.INSTANCE.get();
    }

    public static synchronized void register(ConduitCuboidController<?> controller) {
        CONTROLLERS.add(controller);
    }

    private static synchronized List<ConduitCuboidController<?>> controllers() {
        return List.copyOf(CONTROLLERS);
    }

    private static synchronized void enqueue(ConduitCuboidController<?> controller, boolean structural) {
        DIRTY.merge(controller, structural, (a, b) -> a || b);
    }

    private static synchronized Map<ConduitCuboidController<?>, Boolean> takeDirty(Level level) {
        var result = new LinkedHashMap<ConduitCuboidController<?>, Boolean>();
        for (var iterator = DIRTY.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (entry.getKey().getWorld() == level) {
                result.put(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
        return result;
    }

    public static ConduitCuboidController<?> containing(Level level, BlockPos pos) {
        for (var controller : controllers())
            if (controller.getWorld() == level
                    && controller.isAssembled()
                    && controller.getBoundingBox().contains(pos)) return controller;
        return null;
    }

    public static boolean allows(Level level, BlockPos pos, BlockPos min, BlockPos max, boolean mixer) {
        var state = level.getBlockState(pos);
        var block = state.getBlock();
        var cladding = block instanceof ConduitBlock conduit
                ? conduit.cladding(state)
                : block instanceof FluidBufferBlock buffer ? buffer.cladding(state) : null;
        if (cladding == null) return false;
        if (mixer)
            return net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPlacement.allows(
                    cladding, shell(state), pos, min, max);
        boolean x = pos.getX() == min.getX() || pos.getX() == max.getX(),
                y = pos.getY() == min.getY() || pos.getY() == max.getY(),
                z = pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
        int faces = (x ? 1 : 0) + (y ? 1 : 0) + (z ? 1 : 0);
        if (cladding == ConduitCladding.BARE) return faces == 0;
        if (shell(state) != ConduitShell.VAT) return false;
        PartPosition position = faces == 0
                ? PartPosition.Interior
                : faces >= 2
                        ? (y ? PartPosition.FrameEastWest : PartPosition.FrameUpDown)
                        : y
                                ? (pos.getY() == min.getY() ? PartPosition.BottomFace : PartPosition.TopFace)
                                : x ? PartPosition.WestFace : PartPosition.NorthFace;
        return (cladding == ConduitCladding.CASING ? VatPartType.Casing : VatPartType.Glass)
                .allows(position, faces == 3);
    }

    private static ConduitShell shell(net.minecraft.world.level.block.state.BlockState state) {
        return CladdingMaterial.installed(state).shell();
    }

    private static boolean continuation(Level level, BlockPos pos, ConduitShell family) {
        var state = level.getBlockState(pos);
        return (state.getBlock() instanceof ConduitBlock || state.getBlock() instanceof FluidBufferBlock)
                && family != ConduitShell.SHARED
                && shell(state) == family;
    }

    private static List<CuboidBoundingBox> envelopes(Level level) {
        var result = new ArrayList<CuboidBoundingBox>();
        for (var controller : controllers())
            if (controller.getWorld() == level && !controller.isEmpty()) {
                var bounds = controller.structuralEnvelope();
                if (bounds != null) result.add(bounds);
            }
        return result;
    }

    private static boolean sameEnvelope(List<CuboidBoundingBox> envelopes, BlockPos origin, BlockPos target) {
        for (var bounds : envelopes) if (bounds.contains(origin) != bounds.contains(target)) return false;
        return true;
    }

    public static int count(Level level, CuboidBoundingBox bounds, ConduitShell family) {
        if (bounds.isEmpty() || bounds.getLengthX() > 16 || bounds.getLengthY() > 16 || bounds.getLengthZ() > 16)
            return 0;
        int count = 0;
        for (var pos : bounds) if (continuation(level, pos, family)) ++count;
        return count;
    }

    public static CuboidBoundingBox bounds(
            Level level, CuboidBoundingBox base, Collection<? extends IMultiblockPart<?>> parts) {
        if (base.isEmpty()) return CuboidBoundingBox.EMPTY;
        var result = new CuboidBoundingBox(base.getMin(), base.getMax());
        var family = parts.stream()
                .findFirst()
                .map(p -> ConduitShell.forController(p.getControllerType()))
                .orElse(ConduitShell.SHARED);
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        for (var part : parts)
            for (var face : Direction.values())
                queue.add(part.getWorldPosition().relative(face));
        var origin = parts.stream()
                .findFirst()
                .map(IMultiblockPart::getWorldPosition)
                .orElse(base.getMin());
        var envelopes = envelopes(level);
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            if (!visited.add(pos) || !level.hasChunkAt(pos)) continue;
            if (!sameEnvelope(envelopes, origin, pos)) continue;
            if (!continuation(level, pos, family)) continue;
            result = result.add(pos);
            // Oversize geometry fails normal ZC dimensions; never walk an unlimited exterior run.
            if (result.getLengthX() > 16 || result.getLengthY() > 16 || result.getLengthZ() > 16) return result;
            for (var face : Direction.values()) queue.add(pos.relative(face));
        }
        return result;
    }

    public static <C extends IMultiblockController<C>> List<IMultiblockPart<C>> neighbors(
            Level level, BlockPos origin, Class<C> type, List<IMultiblockPart<C>> direct) {
        if (level == null) return direct;
        var envelopes = envelopes(level);
        var found = new LinkedHashSet<IMultiblockPart<C>>();
        for (var part : direct) if (sameEnvelope(envelopes, origin, part.getWorldPosition())) found.add(part);
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var family = ConduitShell.forController(type);
        for (var face : Direction.values()) queue.add(origin.relative(face));
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            if (!visited.add(pos)
                    || Math.abs(pos.getX() - origin.getX()) > 15
                    || Math.abs(pos.getY() - origin.getY()) > 15
                    || Math.abs(pos.getZ() - origin.getZ()) > 15
                    || !level.hasChunkAt(pos)) continue;
            if (!sameEnvelope(envelopes, origin, pos)) continue;
            if (continuation(level, pos, family)) {
                for (var face : Direction.values()) queue.add(pos.relative(face));
            } else if (!pos.equals(origin)
                    && level.getBlockEntity(pos) instanceof IMultiblockPart<?> part
                    && part.getControllerType() == type) {
                @SuppressWarnings("unchecked")
                var typed = (IMultiblockPart<C>) part;
                found.add(typed);
            }
        }
        return List.copyOf(found);
    }

    public static void changed(Level level, BlockPos pos, boolean structural) {
        for (var c : controllers())
            if (c.getWorld() == level && !c.isEmpty() && !c.isPaused()) {
                var box = c.getBoundingBox();
                if (pos.getX() < box.getMinX() - 1
                        || pos.getX() > box.getMaxX() + 1
                        || pos.getY() < box.getMinY() - 1
                        || pos.getY() > box.getMaxY() + 1
                        || pos.getZ() < box.getMinZ() - 1
                        || pos.getZ() > box.getMaxZ() + 1) continue;
                enqueue(c, structural);
            }
    }
    /** Identity-dependent buffer meshes affect Mixer formation, without rebuilding unrelated Vat routing. */
    public static synchronized void mixerGeometryChanged(Level level, BlockPos pos) {
        for (var c : controllers())
            if (c instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer mixer
                    && c.getWorld() == level
                    && !c.isEmpty()
                    && !c.isPaused()
                    && c.getBoundingBox().contains(pos)) MIXER_GEOMETRY_DIRTY.add(mixer);
    }

    private static synchronized List<net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer>
            takeMixerGeometryDirty(Level level) {
        var result = new ArrayList<net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer>();
        for (var iterator = MIXER_GEOMETRY_DIRTY.iterator(); iterator.hasNext(); ) {
            var c = iterator.next();
            if (c.getWorld() == level) {
                result.add(c);
                iterator.remove();
            }
        }
        return result;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(LevelTickEvent.Pre event) {
        // Client and integrated-server worlds can enqueue concurrently; controller work stays on its level thread.
        for (var entry : takeDirty(event.getLevel()).entrySet())
            if (!entry.getKey().isEmpty()) entry.getKey().conduitChanged(entry.getValue());
        for (var mixer : takeMixerGeometryDirty(event.getLevel())) mixer.recheckPhysicalAssembly();
    }

    @SubscribeEvent
    public static void chunkUnloaded(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        chunkAvailabilityChanged(event, true);
    }

    @SubscribeEvent
    public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        chunkAvailabilityChanged(event, false);
    }

    private static void chunkAvailabilityChanged(
            net.neoforged.neoforge.event.level.ChunkEvent event, boolean unloading) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var chunk = event.getChunk().getPos();
        // Only inspect cached bounds here. Unload events must never fetch blocks or chunks.
        for (var controller : controllers()) {
            var bounds = controller.structuralEnvelope();
            if (controller.getWorld() == level
                    && (unloading || !controller.isAssembled())
                    && bounds != null
                    && chunk.x >= (bounds.getMinX() >> 4)
                    && chunk.x <= (bounds.getMaxX() >> 4)
                    && chunk.z >= (bounds.getMinZ() >> 4)
                    && chunk.z <= (bounds.getMaxZ() >> 4)) enqueue(controller, true);
        }
    }

    private ConduitStructure() {}
}
