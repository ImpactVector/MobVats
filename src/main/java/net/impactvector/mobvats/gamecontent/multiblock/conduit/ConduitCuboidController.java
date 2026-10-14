package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import it.zerono.mods.zerocore.lib.data.geometry.CuboidBoundingBox;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.AbstractCuboidMultiblockController;
import net.minecraft.world.level.Level;

/** Supplies ZC structural connectivity/bounds for ordinary blocks without inventing block entities. */
public abstract class ConduitCuboidController<C extends ConduitCuboidController<C>>
        extends AbstractCuboidMultiblockController<C> {
    private void diagnostic(String event) {
        net.impactvector.mobvats.gamecontent.multiblock.common.SyncDiagnostics.record(
                getClass().getSimpleName(), event, this, getWorld().getGameTime());
    }

    @Override
    protected void markReferenceCoordDirty() {
        diagnostic("dirty");
        super.markReferenceCoordDirty();
    }

    @Override
    protected void markReferenceCoordForUpdate() {
        diagnostic("referenceUpdate");
        super.markReferenceCoordForUpdate();
    }

    @Override
    protected void markMultiblockForRenderUpdate() {
        diagnostic("render");
        super.markMultiblockForRenderUpdate();
    }

    private volatile CuboidBoundingBox conduitBounds;
    private volatile CuboidBoundingBox structuralEnvelope;
    /** Last validated envelope fences passive connectivity even during dirty revalidation. */
    public CuboidBoundingBox structuralEnvelope() {
        return structuralEnvelope;
    }

    private volatile ConduitGeometry.Geometry conduitGeometry = ConduitGeometry.Geometry.EMPTY;

    public ConduitGeometry.Geometry conduitGeometry() {
        return conduitGeometry;
    }

    protected void compileConduitGeometry(ConduitTopology topology) {
        setConduitGeometry(ConduitGeometry.compile(
                getWorld(), getBoundingBox().getMin(), getBoundingBox().getMax(), topology));
        FluidBufferPresentation.structure(
                getWorld(), getBoundingBox().getMin(), getBoundingBox().getMax());
    }

    protected void clearConduitGeometry() {
        // Lifecycle clearing can run during chunk unload. Do not fetch/notify the reference chunk here.
        // Assembly-state updates already hide the coordinated renderer and refresh the shell.
        conduitGeometry = ConduitGeometry.Geometry.EMPTY;
        if (structuralEnvelope != null)
            FluidBufferPresentation.structure(getWorld(), structuralEnvelope.getMin(), structuralEnvelope.getMax());
    }

    private void setConduitGeometry(ConduitGeometry.Snapshot snapshot) {
        if (conduitGeometry.snapshot().equals(snapshot)) return;
        adoptConduitGeometry(ConduitGeometry.bake(snapshot));
    }

    protected void adoptConduitGeometry(ConduitGeometry.Geometry geometry) {
        if (conduitGeometry.snapshot().equals(geometry.snapshot())) return;
        conduitGeometry = geometry;
        if (getWorld().isClientSide) forceStructureUpdate(getWorld());
        else markReferenceCoordForUpdate();
    }

    @Override
    public net.minecraft.nbt.CompoundTag syncDataTo(
            net.minecraft.nbt.CompoundTag data,
            net.minecraft.core.HolderLookup.Provider registries,
            SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        diagnostic(reason == SyncReason.FullSync ? "fullSerialization" : "networkSerialization");
        if (reason == SyncReason.NetworkUpdate)
            data.put("conduitGeometry", conduitGeometry.snapshot().save());
        return data;
    }

    @Override
    public void syncDataFrom(
            net.minecraft.nbt.CompoundTag data,
            net.minecraft.core.HolderLookup.Provider registries,
            SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        if (getWorld().isClientSide && data.contains("conduitGeometry"))
            setConduitGeometry(ConduitGeometry.Snapshot.load(data.getCompound("conduitGeometry")));
    }

    @Override
    protected void onMachineAssembled() {
        super.onMachineAssembled();
        structuralEnvelope = getBoundingBox();
        FluidBufferPresentation.structure(getWorld(), structuralEnvelope.getMin(), structuralEnvelope.getMax());
        if (getWorld().isClientSide) forceStructureUpdate(getWorld());
    }

    protected ConduitCuboidController(Level level) {
        super(level);
        ConduitStructure.register(this);
    }

    @Override
    protected boolean isMachineWhole(it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator validator) {
        // Passive shell cells have no ZC part to pause the machine during chunk eviction.
        // Validate availability before ZC enumerates world cells, using the last complete envelope.
        var bounds = structuralEnvelope;
        if (!getWorld().isClientSide && bounds != null && !getWorld().hasChunksAt(bounds.getMin(), bounds.getMax())) {
            validator.setLastError("multiblock.mobvats.unloaded_structure");
            return false;
        }
        return super.isMachineWhole(validator);
    }

    public void invalidateConduitBounds() {
        conduitBounds = null;
    }

    public CuboidBoundingBox getBoundingBox() {
        if (conduitBounds == null)
            conduitBounds = ConduitStructure.bounds(getWorld(), super.buildBoundingBox(), getConnectedParts());
        return conduitBounds;
    }

    protected CuboidBoundingBox buildBoundingBox() {
        return getBoundingBox();
    }

    protected int getMinimumNumberOfPartsForAssembledMachine() {
        // Preserve the original 34 structural cells, counting passive blocks as well as BEs.
        var bounds = getBoundingBox();
        var shell = getConnectedParts().stream()
                .findFirst()
                .map(p -> ConduitShell.forController(p.getControllerType()))
                .orElse(ConduitShell.SHARED);
        return Math.max(1, 34 - ConduitStructure.count(getWorld(), bounds, shell));
    }

    public void conduitChanged(boolean structural) {
        // A pre-pause notification may still be queued at the next level tick.
        if (isPaused()) return;
        diagnostic("structureDelivery");
        if (!structural && isAssembled()) {
            rebuildConduitTopology();
            return;
        }
        invalidateConduitBounds();
        if (structural) {
            _shouldCheckForDisconnections = true;
            // A passive block can join previously separate controllers without adding a BE.
            // Feed those new connections into ZC's normal merge phase before validation.
            for (var part : getConnectedParts())
                if (part.getNeighboringParts().stream()
                        .anyMatch(neighbor -> !neighbor.isPartInvalid()
                                && neighbor.getMultiblockController()
                                        .filter(controller -> controller != castSelf())
                                        .isPresent()))
                    ConduitStructure.<C>registry().onPartAdded(part);
        }
        ConduitStructure.<C>registry().addDirtyController(castSelf());
        invalidateConduitTopology();
    }

    @Override
    protected void visitLoadedNeighboringParts(it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<C> root) {
        var queue = new java.util.ArrayDeque<it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<C>>();
        root.setVisited();
        queue.add(root);
        while (!queue.isEmpty())
            for (var neighbor : queue.removeFirst().getNeighboringParts())
                if (containsPart(neighbor) && neighbor.isNotVisited() && !neighbor.isPartInvalid()) {
                    neighbor.setVisited();
                    queue.add(neighbor);
                }
    }

    protected abstract void rebuildConduitTopology();

    protected abstract void invalidateConduitTopology();
}
