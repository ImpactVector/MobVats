package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import it.zerono.mods.zerocore.lib.multiblock.*;
import it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.ActiveBoundaryPort;
import net.impactvector.mobvats.gamecontent.multiblock.common.AttachedPartCollection;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class MultiblockMixer
        extends net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCuboidController<MultiblockMixer>
        implements it.zerono.mods.zerocore.lib.IActivableMachine {
    private boolean active = true;

    public boolean processingEnabled() {
        return active && !controllerInhibited();
    }

    public boolean controllerInhibited() {
        return controllerParts.parts().stream()
                .anyMatch(net.impactvector.mobvats.gamecontent.multiblock.common.RedstoneControl::inhibited);
    }

    @Override
    public boolean isMachineActive() {
        return active;
    }

    @Override
    public void setMachineActive(boolean value) {
        if (active == value) return;
        active = value;
        if (!value) stopDrive(false);
        if (isPaused()) return;
        getConnectedParts()
                .forEach(value ? IMultiblockPart::onMachineActivated : IMultiblockPart::onMachineDeactivated);
        callOnLogicalServer(this::changed);
    }

    private final MixerState state = new MixerState(this::changed);
    private final AttachedPartCollection<MixerControllerEntity> controllerParts = new AttachedPartCollection<>(
            MixerControllerEntity.class,
            Comparator.comparing(MixerControllerEntity::getBlockPos, ConduitTopology.POSITION));
    private final AttachedPartCollection<AbstractMixerDriveEntity> attachedDrives = new AttachedPartCollection<>(
            AbstractMixerDriveEntity.class,
            Comparator.comparing(AbstractMixerDriveEntity::getBlockPos, ConduitTopology.POSITION));
    private final AttachedPartCollection<MixerPumpEntity> attachedPumps = new AttachedPartCollection<>(
            MixerPumpEntity.class, Comparator.comparing(MixerPumpEntity::getBlockPos, ConduitTopology.POSITION));
    private final AttachedPartCollection<ActiveBoundaryPort> activePorts = new AttachedPartCollection<>(
            ActiveBoundaryPort.class, Comparator.comparing(ActiveBoundaryPort::getBlockPos, ConduitTopology.POSITION));

    private void registerAttachedPart(Object part) {
        controllerParts.add(part);
        attachedDrives.add(part);
        attachedPumps.add(part);
        activePorts.add(part);
    }

    private void retireAttachedParts() {
        controllerParts.clear();
        attachedDrives.clear();
        attachedPumps.clear();
        activePorts.clear();
    }

    private void rebuildAttachedParts() {
        retireAttachedParts();
        getConnectedParts().forEach(this::registerAttachedPart);
    }

    private List<BlockPos> intakes = List.of();
    private int cadence;
    private MixerAssembly assembly;
    private MixerRenderDescriptor renderDescriptor;
    private FluidStack clientWorkingFluid = FluidStack.EMPTY;

    public FluidStack workingFluid() {
        if (!isAssembled()) return FluidStack.EMPTY;
        return getWorld().isClientSide ? clientWorkingFluid.copy() : state.workingFluid();
    }

    private final MixerRotorAnimation rotorAnimation = new MixerRotorAnimation();
    private MixerOperatingState operatingState = MixerOperatingState.STOPPED;
    private MixerDrive activeDrive;
    private int startupTicks, runningCadence;

    public MixerOperatingState operatingState() {
        return operatingState;
    }

    public int startupTicks() {
        return startupTicks;
    }

    public int spinUpTicks() {
        return slotCount() * 200;
    }

    private void stopDrive() {
        stopDrive(true);
    }

    private void stopDrive(boolean notify) {
        boolean update = operatingState != MixerOperatingState.STOPPED || startupTicks != 0;
        operatingState = MixerOperatingState.STOPPED;
        startupTicks = 0;
        runningCadence = 0;
        activeDrive = null;
        if (update && notify) markReferenceCoordForUpdate();
    }

    public MixerRotorAnimation rotorAnimation() {
        return rotorAnimation;
    }

    public Optional<MixerRenderDescriptor> renderDescriptor() {
        var current = assembly().orElse(null);
        if (current == null) {
            renderDescriptor = null;
            return Optional.empty();
        }
        if (renderDescriptor == null || renderDescriptor.assembly() != current)
            renderDescriptor = MixerRenderDescriptor.from(current);
        return Optional.of(renderDescriptor);
    }

    private void invalidateRotorPresentation() {
        renderDescriptor = null;
        rotorAnimation.reset();
    }

    public Optional<MixerAssembly> assembly() {
        return isAssembled() ? Optional.ofNullable(assembly) : Optional.empty();
    }

    private ConduitTopology conduitTopology = ConduitTopology.EMPTY;
    private final PhysicalFluidStorage storage = new PhysicalFluidStorage();

    public PhysicalFluidStorage storage() {
        return storage;
    }

    public ConduitTopology conduitTopology() {
        return conduitTopology;
    }

    @Override
    protected void rebuildConduitTopology() {
        compileConduits();
        recheckPhysicalAssembly();
    }

    public int slotCount() {
        return assembly().map(MixerAssembly::slotCount).orElse(0);
    }

    public String capacityDiagnostics() {
        var a = assembly().orElse(null);
        return "Shaft depth: " + (a == null ? 0 : a.shaftLength()) + "\nEffective diameter: "
                + (a == null ? 0 : a.effectiveDiameter())
                + "\nVessel sides: " + (a == null ? 0 : a.vessel().sides())
                + "\nFace radius: " + String.format(java.util.Locale.ROOT, "%.9f", a == null ? 0 : a.rotorRadius())
                + "\nRadius reduction: " + (a == null ? 0 : a.radiusReduction()) + " blocks"
                + "\nGeometric mixing volume: "
                + String.format(java.util.Locale.ROOT, "%.3f", a == null ? 0 : a.geometricSweptVolume())
                + " blocks³\nProcessing slots: " + slotCount();
    }

    public void recheckPhysicalAssembly() {
        if (assembly == null || !isAssembled()) return;
        try {
            var next = MixerSweep.compile(
                    getWorld(),
                    getBoundingBox().getMin(),
                    getBoundingBox().getMax(),
                    assembly.impeller(),
                    assembly.shaftLength(),
                    conduitGeometry());
            if (!next.equals(assembly)) {
                assembly = next;
                markReferenceCoordForUpdate();
            }
        } catch (MixerSweep.Interference interference) {
            // ZC owns disassembly; suppress stale capacity immediately and enqueue revalidation.
            assembly = null;
            stopDrive(false);
            markReferenceCoordForUpdate();
            ConduitStructure.changed(getWorld(), interference.obstruction().owner(), true);
        }
    }

    @Override
    protected void invalidateConduitTopology() {
        intakes = List.of();
        conduitTopology = ConduitTopology.EMPTY;
        storage.clear();
        routing = null;
    }

    public MultiblockMixer(Level world) {
        super(world);
        if (!world.isClientSide) state.bind(storage, BlockPos.ZERO);
        state.meterSource(this::installedMeters);
        state.blockerSource(() -> getConnectedParts().stream()
                .filter(MixerBlockerEntity.class::isInstance)
                .map(MixerBlockerEntity.class::cast)
                .map(MixerBlockerEntity::sample)
                .filter(s -> !s.equals(MixerBlockerSample.EMPTY))
                .toList());
    }

    /** Resolve installed controls at selection time, including invalid controls and post-load changes. */
    private MixerMeterRequirements installedMeters() {
        var items = new ArrayList<ItemMeterRequirement>();
        var fluids = new ArrayList<FluidMeterRequirement>();
        for (var part : getConnectedParts()) {
            if (!(part instanceof AbstractMixerMeterEntity meter)) continue;
            String problem = meter.problem();
            if (!problem.isEmpty()) throw new IllegalArgumentException(problem);
            if (meter.itemMeter()) items.add(new ItemMeterRequirement(meter.filter(), meter.quantity()));
            else fluids.add(new FluidMeterRequirement(meter.filter(), meter.quantity()));
        }
        return new MixerMeterRequirements(items, fluids);
    }

    public MixerState state() {
        return state;
    }

    private List<AbstractMixerDriveEntity> driveParts() {
        return attachedDrives.parts();
    }

    public Optional<MixerDrive> drive() {
        var a = assembly().orElse(null);
        var drives = driveParts();
        if (a == null || drives.size() != 1) return Optional.empty();
        var drive = drives.getFirst();
        return !drive.isRemoved()
                        && drive.getMultiblockController()
                                .filter(c -> c == this)
                                .isPresent()
                        && AbstractMixerDriveEntity.couples(drive.getBlockPos(), a.impeller())
                ? Optional.of(drive)
                : Optional.empty();
    }

    public double availableDrive() {
        if (!isAssembled()) return 0.0;
        return drive().map(d -> Math.clamp(d.availableDrive(this), 0.0, 1.0)).orElse(0.0);
    }

    public boolean hasFullDrive() {
        return availableDrive() >= 1.0;
    }

    private ResourceRouting routing;

    public ResourceRouting resourceRouting() {
        if (routing != null) return routing;
        var endpoints = new ArrayList<ResourceEndpoint>();
        for (var part : getConnectedParts()) if (part instanceof ResourceEndpoint endpoint) endpoints.add(endpoint);
        for (var cluster : storage.clusters())
            for (var member : cluster.members())
                endpoints.add(new ResourceEndpoint() {
                    public BlockPos getBlockPos() {
                        return member.getBlockPos();
                    }

                    public boolean routingAvailable() {
                        return cluster.members().stream().noneMatch(FluidBufferEntity::isRemoved);
                    }

                    public Set<ConduitTopology.Role> roles(ConduitResource resource) {
                        return resource == ConduitResource.FLUID ? Set.of(ConduitTopology.Role.STORE) : Set.of();
                    }

                    public List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(
                            ConduitTopology.Role role) {
                        return List.of(cluster.tank());
                    }
                });
        return routing = new ResourceRouting(conduitTopology, endpoints);
    }

    public net.neoforged.neoforge.items.IItemHandler itemsForPort(MixerPortEntity port) {
        var motor = drive().filter(MixerCombustionDriveEntity.class::isInstance)
                .map(MixerCombustionDriveEntity.class::cast)
                .filter(d -> conduitTopology
                        .expand(d.getBlockPos(), ConduitResource.ITEM, List.of(), List.of())
                        .inputs()
                        .contains(port.getBlockPos()))
                .orElse(null);
        boolean input = port.getIoDirection().isInput();
        var recipeItems = state.itemPort(
                input, s -> port.accepts(s) && (motor == null || MixerCombustionDriveEntity.fuelTicks(s) == 0));
        if (motor == null) return recipeItems;
        var fuel = port.fuelStore();
        return new net.neoforged.neoforge.items.IItemHandler() {
            public int getSlots() {
                return 2 + recipeItems.getSlots();
            }

            public ItemStack getStackInSlot(int slot) {
                return slot < 2 ? fuel.getStackInSlot(slot) : recipeItems.getStackInSlot(slot - 2);
            }

            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return slot < 2
                        ? (input && port.accepts(stack) ? fuel.insertItem(slot, stack, simulate) : stack)
                        : recipeItems.insertItem(slot - 2, stack, simulate);
            }

            public ItemStack extractItem(int slot, int n, boolean simulate) {
                return slot < 2 ? ItemStack.EMPTY : recipeItems.extractItem(slot - 2, n, simulate);
            }

            public int getSlotLimit(int slot) {
                return slot < 2 ? fuel.getSlotLimit(slot) : recipeItems.getSlotLimit(slot - 2);
            }

            public boolean isItemValid(int slot, ItemStack stack) {
                return slot < 2
                        ? input && port.accepts(stack) && fuel.isItemValid(slot, stack)
                        : recipeItems.isItemValid(slot - 2, stack);
            }
        };
    }

    public net.minecraft.network.chat.Component diagnosticReport() {
        if (!isAssembled()) {
            var report = net.minecraft.network.chat.Component.literal("Mixer unformed");
            getLastError()
                    .map(e -> e.getChatMessage())
                    .filter(reason -> !reason.getString().isBlank())
                    .ifPresent(reason -> report.append("\nReason: ").append(reason));
            return report;
        }
        return net.minecraft.network.chat.Component.literal(
                "Operator: " + (active ? "enabled" : "disabled") + "\n" + state.status(true, active) + "\nDrive: "
                        + operatingState + " | Startup: " + startupTicks + " / " + spinUpTicks() + "\n"
                        + capacityDiagnostics() + "\n" + String.join("\n", conduitTopology.diagnostics()));
    }
    // In this ZeroCore version dirty also calls notifyBlockUpdate; do not notify twice.
    private void changed() {
        markReferenceCoordDirty();
    }

    protected boolean isMachineWhole(IMultiblockValidator validator) {
        if (!super.isMachineWhole(validator)) return false;
        if (getConnectedParts().stream()
                        .filter(p -> p instanceof MixerControllerEntity)
                        .count()
                != 1) {
            validator.setLastError("multiblock.mobvats.mixer_controller");
            return false;
        }
        return validateAssembly(validator) != null;
    }

    private MixerAssembly validateAssembly(IMultiblockValidator validator) {
        var impellers = getConnectedParts().stream()
                .filter(MixerImpellerEntity.class::isInstance)
                .map(p -> ((MixerImpellerEntity) p).getBlockPos())
                .toList();
        if (impellers.size() != 1) {
            validator.setLastError("multiblock.mobvats.mixer_impeller_count");
            return null;
        }
        var anchor = impellers.getFirst();
        var min = getBoundingBox().getMin();
        var max = getBoundingBox().getMax();
        // An even horizontal dimension has no single centered block.
        if (anchor.getY() != max.getY()
                || 2L * anchor.getX() != (long) min.getX() + max.getX()
                || 2L * anchor.getZ() != (long) min.getZ() + max.getZ()) {
            validator.setLastError(anchor, "multiblock.mobvats.mixer_impeller_center");
            return null;
        }
        // Validation can fail repeatedly while paused. Use a temporary role view there;
        // retained references must remain retired until successful assembly/restoration.
        var drives = isAssembled()
                ? driveParts()
                : getConnectedParts().stream()
                        .filter(AbstractMixerDriveEntity.class::isInstance)
                        .map(AbstractMixerDriveEntity.class::cast)
                        .toList();
        if (drives.size() > 1) {
            validator.setLastError("multiblock.mobvats.mixer_drive_count");
            return null;
        }
        for (var drive : drives)
            if (!AbstractMixerDriveEntity.couples(drive.getBlockPos(), anchor)) {
                validator.setLastError(drive.getBlockPos(), "multiblock.mobvats.mixer_drive_position");
                return null;
            }
        int length = 0;
        for (var pos = anchor.below(); pos.getY() >= min.getY(); pos = pos.below()) {
            if (!(getWorld().getBlockEntity(pos) instanceof MixerShaftEntity shaft) || !containsPart(shaft)) break;
            length++;
        }
        if (length == 0) {
            validator.setLastError(anchor.below(), "multiblock.mobvats.mixer_shaft_required");
            return null;
        }
        // Inspect world cells too: a disconnected Shaft can belong to another ZC controller.
        for (var pos : BlockPos.betweenClosed(min, max))
            if (getWorld().getBlockState(pos).is(MixerContent.block(MixerPartType.Shaft))
                    && (pos.getX() != anchor.getX()
                            || pos.getZ() != anchor.getZ()
                            || pos.getY() >= anchor.getY()
                            || pos.getY() < anchor.getY() - length)) {
                validator.setLastError(pos, "multiblock.mobvats.mixer_shaft_disconnected");
                return null;
            }
        try {
            return MixerSweep.compile(
                    getWorld(),
                    min,
                    max,
                    anchor,
                    length,
                    ConduitGeometry.bake(ConduitGeometry.compile(getWorld(), min, max, buildConduitTopology())));
        } catch (MixerSweep.Interference interference) {
            var device = interference.obstruction();
            validator.setLastError(device.owner(), "multiblock.mobvats.mixer_clearance", device.kind());
            return null;
        }
    }

    protected void onMachineAssembled() {
        super.onMachineAssembled();
        rebuildAttachedParts();
        invalidateRotorPresentation();
        assembly = validateAssembly(this);
        intakes = getConnectedParts().stream()
                .filter(MixerIntakeEntity.class::isInstance)
                .map(p -> ((AbstractMixerEntity) p).getBlockPos().immutable())
                .sorted(java.util.Comparator.comparingLong(BlockPos::asLong))
                .toList();
        compileConduits();
        MixerStructureEvents.remember(this);
        markMultiblockForRenderUpdate();
    }

    private ConduitTopology buildConduitTopology() {
        var endpoints = new ArrayList<ConduitTopology.Endpoint>();
        for (var part : getConnectedParts()) {
            var p = (AbstractMixerEntity) part;
            var pos = p.getBlockPos().immutable();
            if (p instanceof AbstractMixerDriveEntity drive)
                for (var resource : drive.conduitResources())
                    endpoints.add(new ConduitTopology.Endpoint(
                            pos, resource, ConduitTopology.Role.PRODUCER, ConduitTopology.Role.CONSUMER));
            switch (p.getPartType()) {
                case Controller -> {
                    endpoints.add(new ConduitTopology.Endpoint(
                            pos, ConduitResource.ITEM, ConduitTopology.Role.PRODUCER, ConduitTopology.Role.CONSUMER));
                    endpoints.add(new ConduitTopology.Endpoint(
                            pos, ConduitResource.FLUID, ConduitTopology.Role.PRODUCER, ConduitTopology.Role.CONSUMER));
                }
                case Intake ->
                    endpoints.add(new ConduitTopology.Endpoint(
                            pos, ConduitResource.FLUID, ConduitTopology.Role.PRODUCER, ConduitTopology.Role.CONSUMER));
                case PullPump, PushPump -> {
                    var face = net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps.outward(
                            pos, getBoundingBox().getMin(), getBoundingBox().getMax());
                    if (face != null)
                        endpoints.add(new ConduitTopology.Endpoint(
                                        pos,
                                        ConduitResource.FLUID,
                                        p.getPartType() == MixerPartType.PullPump
                                                ? ConduitTopology.Role.PRODUCER
                                                : ConduitTopology.Role.CONSUMER)
                                .attached(face.getOpposite()));
                }
                case FluidVoid ->
                    endpoints.add(new ConduitTopology.Endpoint(pos, ConduitResource.FLUID, Set.of(), null));
                case ItemPort, FluidPort -> {
                    var port = (MixerPortEntity) p;
                    var resource = port.endpoint().resource()
                                    == net.impactvector.mobvats.gamecontent.multiblock.common.EndpointConfiguration
                                            .Resource.ITEM
                            ? ConduitResource.ITEM
                            : ConduitResource.FLUID;
                    endpoints.add(new ConduitTopology.Endpoint(
                            pos,
                            resource,
                            port.getIoDirection().isInput()
                                    ? ConduitTopology.Role.STORE
                                    : ConduitTopology.Role.CONSUMER));
                }
                default -> {}
            }
        }
        for (var buffer : PhysicalFluidStorage.members(
                getWorld(), getBoundingBox().getMin(), getBoundingBox().getMax()))
            endpoints.add(new ConduitTopology.Endpoint(
                    buffer.getBlockPos(), ConduitResource.FLUID, ConduitTopology.Role.STORE));
        for (var part : getConnectedParts())
            if (part instanceof ResourceEndpoint endpoint) {
                endpoints.removeIf(e -> e.position().equals(endpoint.getBlockPos()));
                endpoints.addAll(endpoint.connections());
            }
        return ConduitTopology.compile(
                getWorld(), getBoundingBox().getMin(), getBoundingBox().getMax(), endpoints);
    }

    public void rebuildTopology() {
        if (!isAssembled()) return;
        compileConduits();
        recheckPhysicalAssembly();
    }

    private void compileConduits() {
        if (getWorld().isClientSide) return;
        routing = null;
        conduitTopology = buildConduitTopology();
        storage.compile(
                PhysicalFluidStorage.members(
                        getWorld(), getBoundingBox().getMin(), getBoundingBox().getMax()),
                conduitTopology);
        storage.sinks(getConnectedParts().stream()
                .filter(MixerFluidVoidEntity.class::isInstance)
                .map(MixerFluidVoidEntity.class::cast)
                .sorted(Comparator.comparing(MixerFluidVoidEntity::getBlockPos, ConduitTopology.POSITION))
                .toList());
        getConnectedParts().stream()
                .filter(MixerControllerEntity.class::isInstance)
                .findFirst()
                .ifPresent(p -> {
                    var owner = (AbstractMixerEntity) p;
                    storage.services(Map.of(owner.getBlockPos(), owner.service()));
                    state.bind(storage, owner.getBlockPos());
                });
        compileConduitGeometry(conduitTopology);
        getConnectedParts().stream()
                .filter(MixerFluidPortEntity.class::isInstance)
                .forEach(p -> ((MixerFluidPortEntity) p).refreshRouting());
    }

    protected void onMachineRestored() {
        onMachineAssembled();
    }

    protected void onMachinePaused() {
        MixerStructureEvents.forget(this);
        // ZC pauses from LevelChunk.clearAllBlockEntities after the chunk leaves the loaded map.
        // Reference notifications fetch that chunk again and can make vanilla's unload queue spin.
        stopDrive(false);
        assembly = null;
        invalidateRotorPresentation();
        invalidateConduitTopology();
        clearConduitGeometry();
        retireAttachedParts();
    }

    protected void onMachineDisassembled() {
        stopDrive(false);
        assembly = null;
        invalidateRotorPresentation();
        invalidateConduitTopology();
        clearConduitGeometry();
        retireAttachedParts();
        if (getWorld().isClientSide) markMultiblockForRenderUpdate();
    }

    protected void onPartAdded(IMultiblockPart<MultiblockMixer> part) {
        if (!isPaused()) registerAttachedPart(part);
        invalidateConduitBounds();
        routing = null;
    }

    protected void onPartRemoved(IMultiblockPart<MultiblockMixer> part) {
        controllerParts.remove(part);
        attachedDrives.remove(part);
        attachedPumps.remove(part);
        activePorts.remove(part);
        if (part == activeDrive) stopDrive(false);
        invalidateConduitBounds();
        invalidateConduitTopology();
    }

    protected boolean updateServer() {
        if (!isAssembled()) return false;
        boolean storageChanged = storage.evacuate();
        state.activeTick();
        if (processingEnabled()) tickDrive();
        boolean pumpsChanged = MixerPumps.tickOrdered(this, attachedPumps.parts());
        boolean boundaryChanged = storageChanged
                | pumpsChanged
                | ActiveBoundaryPort.tickOrdered(activePorts.parts(), getWorld().getGameTime());
        if (++cadence < SlurryBalance.CADENCE) return boundaryChanged;
        cadence = 0;
        boolean intakeChanged = false;
        for (var intake : intakes) {
            Direction face = intake.getX() == getBoundingBox().getMin().getX()
                    ? Direction.WEST
                    : intake.getX() == getBoundingBox().getMax().getX()
                            ? Direction.EAST
                            : intake.getZ() == getBoundingBox().getMin().getZ() ? Direction.NORTH : Direction.SOUTH;
            var water = getWorld().getFluidState(intake.relative(face));
            if (water.is(FluidTags.WATER) && water.isSource())
                intakeChanged |= storage.handler(intake, true, s -> s.is(Fluids.WATER))
                                .fill(new FluidStack(Fluids.WATER, SlurryBalance.WATER_PER_INTAKE), FluidAction.EXECUTE)
                        > 0;
        }
        return boundaryChanged | intakeChanged;
    }

    private void tickDrive() {
        var motor = drive().orElse(null);
        // Redstone freezes the mechanical epoch instead of invoking stopDrive's reset path.
        if (motor instanceof AbstractMixerDriveEntity part && part.redstoneInhibited()) return;
        if (motor != activeDrive) {
            stopDrive();
            activeDrive = motor;
        }
        if (motor instanceof MixerCombustionDriveEntity combustion) combustion.refill(resourceRouting());
        if (slotCount() == 0 || motor == null || motor.availableDrive(this) < 1 || !motor.driveTick(this)) {
            stopDrive();
            return;
        }
        if (operatingState != MixerOperatingState.RUNNING) {
            var previous = operatingState;
            operatingState = MixerOperatingState.SPINNING_UP;
            if (++startupTicks >= spinUpTicks()) {
                operatingState = MixerOperatingState.RUNNING;
                runningCadence = 0;
            }
            // Publish transitions promptly; client presentation advances locally between corrections.
            if (previous != operatingState || startupTicks % 20 == 0) markReferenceCoordForUpdate();
            return;
        }
        if (++runningCadence >= SlurryBalance.CADENCE) {
            runningCadence = 0;
            state.cadence();
        }
    }

    protected void updateClient() {
        if (isAssembled() && assembly != null) {
            rotorAnimation.tick(operatingState, startupTicks, spinUpTicks());
            if (operatingState == MixerOperatingState.SPINNING_UP)
                startupTicks = Math.min(startupTicks + 1, spinUpTicks());
        }
    }

    protected void onAssimilate(IMultiblockController<MultiblockMixer> other) {
        if (other instanceof MultiblockMixer mixer) {
            active &= mixer.active;
            state.assimilate(mixer.state);
        }
    }

    protected void onAssimilated(IMultiblockController<MultiblockMixer> other) {
        MixerStructureEvents.forget(this);
        stopDrive(false);
        assembly = null;
        invalidateRotorPresentation();
        invalidateConduitTopology();
        clearConduitGeometry();
        retireAttachedParts();
        state.clear();
    }

    public boolean waterBucket(Player player, InteractionHand hand) {
        if (!isAssembled() || !player.getItemInHand(hand).is(Items.WATER_BUCKET)) return false;
        var water = new FluidStack(Fluids.WATER, 1000);
        var input = state.fluids(true);
        if (input.fill(water, FluidAction.SIMULATE) != 1000) return false;
        if (input.fill(water, FluidAction.EXECUTE) != 1000) return false;
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        return true;
    }

    public boolean depositPlayerXp(ServerPlayer player) {
        if (!isAssembled()) return false;
        int requiredMb = MixerRecipeRegistry.recipes().stream()
                .flatMap(r -> r.fluids().stream())
                .filter(i -> i.selector()
                        .test(new FluidStack(
                                net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get(), 1)))
                .mapToInt(i -> i.amount())
                .min()
                .orElse(0);
        int ratio = net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.ratio();
        int points = (int) Math.min((requiredMb + (long) ratio - 1) / ratio, player.totalExperience);
        // Vanilla points are indivisible. Keep the complete conversion in fluid storage,
        // including any remainder above the recipe's exact mB requirement.
        if (!state.depositExperienceMb((long) points * ratio)) return false;
        player.giveExperiencePoints(-points);
        return true;
    }

    protected int getMinimumXSize() {
        return 3;
    }

    protected int getMinimumYSize() {
        return 3;
    }

    protected int getMinimumZSize() {
        return 3;
    }

    protected int getMaximumXSize() {
        return 9;
    }

    protected int getMaximumYSize() {
        return 9;
    }

    protected int getMaximumZSize() {
        return 9;
    }

    @Override
    protected int getMinimumNumberOfPartsForAssembledMachine() {
        return Math.max(1, 26 - ConduitStructure.count(getWorld(), getBoundingBox(), ConduitShell.MIXER));
    }

    private boolean conduitAllowed(Level level, int x, int y, int z) {
        return net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure.allows(
                level,
                new BlockPos(x, y, z),
                getBoundingBox().getMin(),
                getBoundingBox().getMax(),
                true);
    }

    protected boolean isBlockGoodForFrame(Level w, int x, int y, int z, IMultiblockValidator v) {
        return conduitAllowed(w, x, y, z);
    }

    protected boolean isBlockGoodForTop(Level w, int x, int y, int z, IMultiblockValidator v) {
        return conduitAllowed(w, x, y, z);
    }

    protected boolean isBlockGoodForBottom(Level w, int x, int y, int z, IMultiblockValidator v) {
        return conduitAllowed(w, x, y, z);
    }

    protected boolean isBlockGoodForSides(Level w, int x, int y, int z, IMultiblockValidator v) {
        return conduitAllowed(w, x, y, z);
    }

    protected boolean isBlockGoodForInterior(Level w, int x, int y, int z, IMultiblockValidator v) {
        return w.getBlockState(new BlockPos(x, y, z)).isAir() || conduitAllowed(w, x, y, z);
    }

    public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        data.putBoolean("active", active);
        if (assembly != null) data.put("mixerAssembly", assembly.save());
        else data.remove("mixerAssembly");
        data.put("mixer", reason == SyncReason.NetworkUpdate ? state.presentation(registries) : state.save(registries));
        if (reason == SyncReason.NetworkUpdate) {
            var fluid = workingFluid();
            if (fluid.isEmpty()) data.remove("workingFluid");
            else data.put("workingFluid", fluid.save(registries));
        }
        data.putString("operatingState", operatingState.name());
        data.putInt("startupTicks", startupTicks);
        return data;
    }

    public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        active = !data.contains("active") || data.getBoolean("active");
        state.load(data.getCompound("mixer"), registries);
        if (getWorld().isClientSide) {
            clientWorkingFluid = FluidStack.parseOptional(registries, data.getCompound("workingFluid"));
            try {
                operatingState = MixerOperatingState.valueOf(data.getString("operatingState"));
            } catch (IllegalArgumentException e) {
                operatingState = MixerOperatingState.STOPPED;
            }
            startupTicks = Math.max(0, data.getInt("startupTicks"));
        } else stopDrive(false);
        var loaded = data.contains("mixerAssembly") ? MixerAssembly.load(data.getCompound("mixerAssembly")) : null;
        if (!Objects.equals(assembly, loaded)) {
            assembly = loaded;
            invalidateRotorPresentation();
        }
    }
}
