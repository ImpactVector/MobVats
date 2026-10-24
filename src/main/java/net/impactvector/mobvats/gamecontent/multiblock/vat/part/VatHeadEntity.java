package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class VatHeadEntity extends AbstractVatEntity {
    // Legacy Head fields are retained only for old-world NBT compatibility.
    // Active timers and milk storage belong to MultiblockVat/VatProcessEngine.
    private ResourceLocation identity;
    private boolean lightningActivated;

    public boolean hostile() {
        return identity != null
                && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getOptional(identity)
                        .map(t -> t.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER)
                        .orElse(false);
    }

    public boolean lightningActivated() {
        return hostile() && lightningActivated;
    }

    public boolean activationReady() {
        return !hostile() || lightningActivated;
    }

    /** Only the verified server lightning integration calls this after structural validation. */
    public void activateFromLightning() {
        if (getLevel() == null || getLevel().isClientSide || !hostile() || lightningActivated) return;
        lightningActivated = true;
        ++renderRevision;
        setChanged();
        getMultiblockController().ifPresent(MultiblockVat::rebuildTopology);
        notifyBlockUpdate();
    }

    private void refreshSubstrateModels() {
        if (getLevel() == null || !getLevel().isClientSide || geometry == null) return;
        for (var pos : geometry.occupiedCells(getBlockPos())) {
            if (getLevel().getBlockEntity(pos) instanceof AbstractVatEntity part) part.requestModelDataUpdate();
            var state = getLevel().getBlockState(pos);
            getLevel().sendBlockUpdated(pos, state, state, 8);
        }
    }

    private java.util.UUID specimenId = java.util.UUID.randomUUID();

    public java.util.UUID specimenId() {
        return specimenId;
    }

    private CompoundTag specimenState = new CompoundTag();

    public CompoundTag specimenState() {
        return specimenState.copy();
    }

    public void updateSpecimenState(CompoundTag state) {
        var filtered = net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.filter(state);
        if (specimenState.equals(filtered)) return;
        specimenState = filtered;
        ++renderRevision;
        setChanged();
        notifyBlockUpdate();
    }

    private net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry geometry;
    private String geometryError;
    private CompoundTag invalidGeometry;

    public net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry geometry() {
        return geometry;
    }

    public String geometryError() {
        return geometryError;
    }

    private net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage lifeStage =
            net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.ADULT;

    public net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage lifeStage() {
        return lifeStage;
    }

    public void setLifeStage(net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage value) {
        if (lifeStage == value) return;
        lifeStage = value;
        ++renderRevision;
        if (getLevel() != null && !getLevel().isClientSide) notifyBlockUpdate();
    }

    private int milkTicks;
    private boolean milkReady;
    private long renderRevision, presentationRevision;
    private net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation presentation =
            net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation.EMPTY;

    public net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation presentation() {
        return presentation;
    }

    public long presentationRevision() {
        return presentationRevision;
    }
    /** Controller-authored transport only. The Head does not calculate routes or anchors. */
    public void setPresentation(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation value) {
        if (presentation.equals(value)) return;
        presentation = value;
        ++presentationRevision;
        if (getLevel() != null && !getLevel().isClientSide) notifyBlockUpdate();
    }

    public VatHeadEntity(BlockPos pos, BlockState state) {
        super(VatPartType.Head, pos, state);
    }

    public ResourceLocation getIdentity() {
        return identity;
    }

    @Deprecated
    public int getMilkTicks() {
        return milkTicks;
    }

    @Deprecated
    public boolean hasMilk() {
        return milkReady;
    }

    public long getRenderRevision() {
        return renderRevision;
    }

    public Direction getSpecimenFacing() {
        return geometry == null ? getBlockState().getValue(VatHeadBlock.FACING) : geometry.facing();
    }

    public float getSpecimenYaw() {
        return getSpecimenFacing().toYRot();
    }

    public void rotateGeometry(Direction facing) {
        if (geometry == null || geometry.facing() == facing) return;
        geometry = geometry.withFacing(facing);
        ++renderRevision;
        setChanged();
        notifyBlockUpdate();
        getMultiblockController().ifPresent(c -> c.checkIfMachineIsWhole());
    }

    @Override
    public void onPostMachineAssembled(MultiblockVat controller) {
        super.onPostMachineAssembled(controller);
        ++renderRevision;
    }

    @Override
    public void onPostMachineBroken() {
        super.onPostMachineBroken();
        // Client assembly can temporarily break while chunk data arrives. Only server topology clears anchors.
        if (getLevel() != null
                && !getLevel().isClientSide
                && !isRemoved()
                && getLevel().hasChunkAt(getBlockPos()))
            setPresentation(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation.EMPTY);
        ++renderRevision;
    }

    public void encode(ResourceLocation identity) {
        encode(
                identity,
                net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.legacy(
                        identity, getBlockState().getValue(VatHeadBlock.FACING)));
    }

    public void clearSpecimen() {
        identity = null;
        lightningActivated = false;
        specimenId = java.util.UUID.randomUUID();
        specimenState = new CompoundTag();
        geometry = null;
        geometryError = null;
        invalidGeometry = null;
        ++renderRevision;
        setChanged();
        notifyBlockUpdate();
        getMultiblockController().ifPresent(c -> c.checkIfMachineIsWhole());
    }

    public void encode(
            ResourceLocation identity, net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry resolved) {
        encode(identity, resolved, new CompoundTag());
    }

    public void encode(
            ResourceLocation identity,
            net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry resolved,
            CompoundTag state) {
        lightningActivated = false;
        specimenId = java.util.UUID.randomUUID();
        specimenState = net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.filter(state);
        lifeStage = specimenState.getBoolean("Baby")
                ? net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.JUVENILE
                : net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.ADULT;
        geometry = java.util.Objects.requireNonNull(resolved);
        geometryError = null;
        invalidGeometry = null;
        ++renderRevision;
        if (getLevel() != null && getBlockState().getValue(VatHeadBlock.FACING) != resolved.facing())
            getLevel().setBlock(getBlockPos(), getBlockState().setValue(VatHeadBlock.FACING, resolved.facing()), 3);
        if (!java.util.Objects.equals(this.identity, identity)) ++renderRevision;
        this.identity = identity;
        setChanged();
        notifyBlockUpdate();
        getMultiblockController().ifPresent(c -> c.checkIfMachineIsWhole());
    }

    @Override
    public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        if (reason == SyncReason.NetworkUpdate && data.contains("presentation"))
            setPresentation(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPresentation.load(
                    data.getCompound("presentation")));
        try {
            setLifeStage(net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage.valueOf(
                    data.getString("lifeStage")));
        } catch (IllegalArgumentException ignored) {
        }
        var previousIdentity = identity;
        boolean previousActivationReady = activationReady();
        if (data.hasUUID("specimenId")) specimenId = data.getUUID("specimenId");
        var previousGeometry = geometry;
        identity = data.getString("entity").isEmpty() ? null : ResourceLocation.tryParse(data.getString("entity"));
        lightningActivated = identity != null && data.getBoolean("lightningActivated");
        specimenState =
                net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.filter(data.getCompound("specimen_state"));
        geometry = null;
        geometryError = null;
        invalidGeometry = null;
        if (identity != null)
            try {
                geometry = data.contains("geometry")
                        ? net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.load(data.getCompound("geometry"))
                        : net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.legacy(
                                identity, getBlockState().getValue(VatHeadBlock.FACING));
                if (!data.contains("geometry")) setChanged();
            } catch (IllegalArgumentException error) {
                geometryError = error.getMessage();
                if (data.contains("geometry"))
                    invalidGeometry = data.getCompound("geometry").copy();
            }
        ++renderRevision;
        if (!java.util.Objects.equals(previousIdentity, identity)) ++renderRevision;
        milkTicks = Math.clamp(
                data.getInt("milkTicks"),
                0,
                net.impactvector.mobvats.gamecontent.multiblock.vat.process.CowBalance.MILK_INTERVAL - 1);
        milkReady = data.getBoolean("milkReady");
        if (previousActivationReady != activationReady()
                || !java.util.Objects.equals(previousIdentity, identity)
                || !java.util.Objects.equals(previousGeometry, geometry)) refreshSubstrateModels();
        // Encoding changes part data, not block identity: ask ZeroCore2 to revalidate after its sync arrives.
        if (getLevel() != null
                && getLevel().isClientSide
                && (!java.util.Objects.equals(previousIdentity, identity)
                        || !java.util.Objects.equals(previousGeometry, geometry))) {
            getMultiblockController().ifPresent(c -> c.checkIfMachineIsWhole());
        }
    }

    @Override
    public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        if (reason == SyncReason.NetworkUpdate) data.put("presentation", presentation.save());
        if (identity != null) data.putString("entity", identity.toString());
        data.putBoolean("lightningActivated", lightningActivated());
        data.putUUID("specimenId", specimenId);
        data.putInt("specimen_version", 1);
        data.put("specimen_state", specimenState.copy());
        if (geometry != null) data.put("geometry", geometry.save());
        else if (invalidGeometry != null) data.put("geometry", invalidGeometry.copy());
        data.putString("lifeStage", lifeStage.name());
        data.putInt("milkTicks", milkTicks);
        data.putBoolean("milkReady", milkReady);
        return data;
    }
}
