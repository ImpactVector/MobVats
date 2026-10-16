package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity;
import it.zerono.mods.zerocore.lib.client.model.data.multiblock.CuboidPartVariantsModelData;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition;
import it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public abstract class AbstractVatEntity extends AbstractMultiblockEntity<MultiblockVat> {
    public boolean redstoneInhibited() {
        return net.impactvector.mobvats.gamecontent.multiblock.common.RedstoneControl.inhibited(this);
    }

    private void diagnostic(String event) {
        net.impactvector.mobvats.gamecontent.multiblock.common.SyncDiagnostics.record(
                "MultiblockVat", event, this, level == null ? Long.MIN_VALUE : level.getGameTime());
    }

    @Override
    public void setChanged() {
        diagnostic("partDirty");
        super.setChanged();
    }

    @Override
    public void notifyBlockUpdate(BlockState oldState, BlockState newState) {
        diagnostic("partUpdate");
        super.notifyBlockUpdate(oldState, newState);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        diagnostic("packetConstructed");
        return super.getUpdatePacket();
    }

    private net.minecraft.nbt.CompoundTag retainedMechanicalState = new net.minecraft.nbt.CompoundTag();
    private final net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging service =
            new net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging(this::setChanged);

    public net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging service() {
        return service;
    }

    @Override
    public net.minecraft.nbt.CompoundTag syncDataTo(
            net.minecraft.nbt.CompoundTag data,
            net.minecraft.core.HolderLookup.Provider registries,
            SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        data.put("retainedMechanicalState", retainedMechanicalState.copy());
        data.put("serviceStaging", service.save(registries));
        return data;
    }

    @Override
    public void syncDataFrom(
            net.minecraft.nbt.CompoundTag data,
            net.minecraft.core.HolderLookup.Provider registries,
            SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        retainedMechanicalState = data.getCompound("retainedMechanicalState").copy();
        service.load(data.getCompound("serviceStaging"), registries);
    }

    private final VatPartType type;

    protected AbstractVatEntity(VatPartType type, BlockPos pos, BlockState state) {
        super(Content.entityType(type), pos, state);
        this.type = type;
    }

    public VatPartType getPartType() {
        return type;
    }

    public VatWallPanelState getWallPanelState() {
        if (!type.usesWallPanel()) return VatWallPanelState.UNFORMED;
        var shell = getMultiblockController()
                .filter(MultiblockVat::isAssembled)
                .map(vat -> VatCasingState.from(
                        getBlockPos(),
                        vat.getBoundingBox().getMin(),
                        vat.getBoundingBox().getMax()))
                .orElse(VatCasingState.UNFORMED);
        if (!shell.formed() && isMachineAssembled()) {
            // Interior service endpoints retain the same thin panels and strap underside
            // as shell endpoints, with deterministic mounting planes.
            if (type == VatPartType.Harness)
                shell = new VatCasingState(1 << net.minecraft.core.Direction.UP.ordinal(), 0);
            else if (type == VatPartType.Sump)
                shell = new VatCasingState(1 << net.minecraft.core.Direction.DOWN.ordinal(), 0);
        }
        if (!shell.formed() || level == null) return VatWallPanelState.UNFORMED;
        int edges = 0;
        for (var face : net.minecraft.core.Direction.values())
            if (level.getBlockEntity(getBlockPos().relative(face)) instanceof VatGlassEntity)
                edges |= 1 << face.ordinal();
        return new VatWallPanelState(shell, edges);
    }

    @Override
    public java.util.Set<MultiblockVat> attachToNeighbors(
            java.util.function.Function<
                            it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<MultiblockVat>,
                            java.util.Set<MultiblockVat>>
                    ignored) {
        // ZC's default registry callback checks six physical BE neighbors. Include passive structural bridges.
        var controllers = new java.util.HashSet<MultiblockVat>();
        for (var neighbor : getNeighboringParts())
            if (!neighbor.isPartInvalid() && neighbor.getControllerType() == getControllerType())
                neighbor.getMultiblockController().ifPresent(controllers::add);
        var current = getMultiblockController().filter(controller -> controller.containsPart(this));
        if (current.isPresent()) {
            // Requeued passive bridges must retain their original controller in the merge set.
            // Do not attach an already-owned part twice or transfer it before assimilation.
            controllers.add(current.get());
            return controllers;
        }
        return super.attachToNeighbors(part -> controllers);
    }

    @Override
    public java.util.List<it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<MultiblockVat>> getNeighboringParts() {
        var found = new java.util.LinkedHashSet<>(
                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure.neighbors(
                        level, getBlockPos(), MultiblockVat.class, super.getNeighboringParts()));
        // Semantic interior parts can be suspended in medium. Axial visibility bridges
        // registration only; it never claims substrate or associates a service endpoint.
        // Stop at the first non-medium cell so a shell cannot bridge to another vat.
        if (level != null)
            for (var face : net.minecraft.core.Direction.values())
                for (int distance = 1; distance <= 15; distance++) {
                    var p = getBlockPos().relative(face, distance);
                    if (!level.hasChunkAt(p)) break;
                    if (level.getBlockEntity(p) instanceof AbstractVatEntity other) {
                        if (semantic(type) || semantic(other.type)) found.add(other);
                        break;
                    }
                    if (!SpecimenGeometry.medium(level, p)) break;
                }
        return java.util.List.copyOf(found);
    }

    private static boolean semantic(VatPartType type) {
        return type == VatPartType.Head
                || type == VatPartType.Flesh
                || type == VatPartType.Harness
                || type == VatPartType.Sump
                || type == VatPartType.FillValve
                || type == VatPartType.DrainValve
                || type == VatPartType.ItemVoid
                || type == VatPartType.FluidVoid;
    }

    @Override
    public Class<MultiblockVat> getControllerType() {
        return MultiblockVat.class;
    }

    @Override
    public MultiblockVat createController() {
        return new MultiblockVat(getPartWorldOrFail());
    }

    @Override
    public void onMachineActivated() {}

    @Override
    public void onMachineDeactivated() {}

    @Override
    public boolean isGoodForPosition(PartPosition position, IMultiblockValidator validator) {
        boolean materialAllowed = true;
        if (getBlockState().getBlock()
                instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock) {
            var material =
                    net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.installed(getBlockState());
            materialAllowed = position == PartPosition.Interior
                            || material == net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.NONE
                    ? position == PartPosition.Interior
                    : material.shell() == net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitShell.VAT
                            && (material.cladding()
                                                    == net.impactvector.mobvats.gamecontent.multiblock.conduit
                                                            .ConduitCladding.GLASS
                                            ? VatPartType.Glass
                                            : VatPartType.Casing)
                                    .allows(position, getOutwardFacings().countFacesIf(true) == 3);
        }
        if (materialAllowed && type.allows(position, getOutwardFacings().countFacesIf(true) == 3)) return true;
        validator.setLastError(
                getBlockPos(),
                "multiblock.mobvats.invalid_position",
                type.getSerializedName(),
                position.getSerializedName());
        return false;
    }

    @Override
    protected ModelData getUpdatedModelData() {
        // XR2 turbine pattern: construction at index 0, hidden model at index 1.
        var data = CuboidPartVariantsModelData.from(
                type.ordinal(),
                isMachineAssembled() && (type == VatPartType.Head || type == VatPartType.Flesh) ? 1 : 0,
                getOutwardFacings());
        return type.usesWallPanel()
                ? data.derive()
                        .with(VatWallPanelState.PROPERTY, getWallPanelState())
                        .build()
                : data;
    }
}
