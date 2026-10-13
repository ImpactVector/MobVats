package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import it.zerono.mods.zerocore.lib.item.inventory.handler.TileEntityItemStackHandler;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class VatToolPortEntity extends AbstractVatEntity implements VatActor, ResourceEndpoint {
    private final DemandConfiguration demand = new DemandConfiguration(this::setChanged);

    public DemandConfiguration demandConfiguration() {
        return demand;
    }

    public java.util.Set<ConduitTopology.Role> roles(ConduitResource resource) {
        return java.util.Set.of(ConduitTopology.Role.PRODUCER, ConduitTopology.Role.CONSUMER);
    }

    public net.neoforged.neoforge.items.IItemHandlerModifiable items(ConduitTopology.Role role) {
        return role == ConduitTopology.Role.PRODUCER ? results.resources() : null;
    }

    public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(
            ConduitTopology.Role role) {
        return role == ConduitTopology.Role.PRODUCER ? service().tanks() : java.util.List.of();
    }

    public boolean routingAvailable() {
        return !isRemoved();
    }

    private long interactionStart = Long.MIN_VALUE;
    private BlockPos interactionHead;
    private net.minecraft.world.phys.Vec3 previousAim, currentAim;

    public net.minecraft.world.phys.Vec3 previousAim() {
        return previousAim;
    }

    private final ItemResultBuffer results = new ItemResultBuffer(this::setChanged);

    public ItemResultBuffer results() {
        return results;
    }

    public BlockPos interactionHead() {
        return interactionHead;
    }

    public void selectTarget(BlockPos head) {
        previousAim = currentAim;
        interactionHead = head.immutable();
        getMultiblockController()
                .ifPresent(v -> v.getAssemblies().stream()
                        .filter(a -> a.head().getBlockPos().equals(head))
                        .findFirst()
                        .ifPresent(a -> currentAim = VatInteractionGeometry.link(
                                        v.getBoundingBox().getMin(),
                                        v.getBoundingBox().getMax(),
                                        a,
                                        getBlockPos(),
                                        true,
                                        false)
                                .target()));
        setChanged();
        notifyBlockUpdate();
    }

    public boolean available() {
        return level != null
                && (interactionStart == Long.MIN_VALUE || level.getGameTime() - interactionStart >= INTERACTION_TICKS);
    }

    public boolean reaches(
            net.minecraft.world.level.Level level,
            BlockPos actor,
            BlockPos min,
            BlockPos max,
            MobAssembly target,
            java.util.List<MobAssembly> all) {
        return VatTopologyCompiler.radialTargets(level, actor, target, all, 32, true);
    }

    public static final int INTERACTION_TICKS = 20;

    public long interactionStart() {
        return interactionStart;
    }
    /** Controller sets the post-commit epoch; available() uses it for the actor cooldown. */
    public void setInteractionStart(long start) {
        interactionStart = start;
        setChanged();
        notifyBlockUpdate();
    }

    public static double extension(long start, double now) {
        if (start == Long.MIN_VALUE || now < start) return 0;
        double age = now - start;
        if (age >= INTERACTION_TICKS) return 0;
        return age < 6 ? age / 6 : age < 10 ? 1 : (INTERACTION_TICKS - age) / 10;
    }

    private final TileEntityItemStackHandler held = new TileEntityItemStackHandler(this, 1) {
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public VatToolPortEntity(BlockPos pos, BlockState state) {
        super(VatPartType.ToolPort, pos, state);
        // TileEntityItemStackHandler already dirties its owner before delivering this callback.
        held.setContentsChangedListener(slot -> {
            notifyBlockUpdate();
            if (level != null && !level.isClientSide)
                getMultiblockController().ifPresent(MultiblockVat::rebuildTopology);
        });
    }

    public ItemStack heldItem() {
        return held.getStackInSlot(0).copy();
    }

    public boolean usesLocalItemPorts() {
        return true;
    }

    public int priority() {
        return PassiveUses.SWORD_PRIORITY;
    }

    public String useKey() {
        return PassiveUses.SECONDARY;
    }

    public boolean compatible(MobAssembly assembly) {
        return !heldItem().isEmpty();
    }

    public java.util.List<String> uses(MobAssembly assembly) {
        return ToolInteractions.uses(level, assembly.head().getIdentity(), heldItem());
    }
    /** State mutation endpoint; only the controller performs automated transfers. */
    public void setHeldItem(ItemStack stack) {
        held.setStackInSlot(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public String assignmentStatus() {
        var controller =
                getMultiblockController().filter(MultiblockVat::isAssembled).orElse(null);
        if (controller == null) return "Unassigned -> vat is not formed";
        var topology = controller.getTopology();
        var reason = topology.physical().blocked().stream()
                .filter(b -> b.part().equals(getBlockPos()))
                .findFirst();
        if (reason.isPresent()) return reason.get().description();
        if (!topology.physical().inactiveHeads().isEmpty())
            return "Unassigned -> simulacrum inactive: harness obstruction";
        return java.util.stream.Stream.concat(topology.actors().stream(), topology.toolLoading().stream())
                        .anyMatch(a -> a.actor().equals(getBlockPos()))
                ? "Assigned -> Specimen #1"
                : "Unassigned -> no available/valid simulacrum";
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var incoming = player.getItemInHand(hand);
        if (incoming.isEmpty() && !results.isEmpty()) {
            for (var result : results.snapshot()) if (!player.getInventory().add(result)) player.drop(result, false);
            results.clear();
            return InteractionResult.CONSUME;
        }
        var old = held.extractItem(0, 1, false);
        if (!incoming.isEmpty()) {
            held.setStackInSlot(0, incoming.copyWithCount(1));
            if (!player.getAbilities().instabuild) incoming.shrink(1);
        }
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(assignmentStatus()), true);
        return InteractionResult.CONSUME;
    }

    private static net.minecraft.world.phys.Vec3 readAim(CompoundTag tag, String key) {
        if (!tag.contains(key + "X")) return null;
        var aim = new net.minecraft.world.phys.Vec3(
                tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
        return Double.isFinite(aim.lengthSqr()) ? aim : null;
    }

    private static void writeAim(CompoundTag tag, String key, net.minecraft.world.phys.Vec3 aim) {
        if (aim != null) {
            tag.putDouble(key + "X", aim.x);
            tag.putDouble(key + "Y", aim.y);
            tag.putDouble(key + "Z", aim.z);
        }
    }

    public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        demand.load(data.getCompound("resourceDemand"));
        interactionStart = data.contains("interactionStart") ? data.getLong("interactionStart") : Long.MIN_VALUE;
        interactionHead = data.contains("interactionHead") ? BlockPos.of(data.getLong("interactionHead")) : null;
        previousAim = readAim(data, "previousAim");
        currentAim = readAim(data, "currentAim");
        held.deserializeNBT(registries, data.getCompound("held"));
        results.load(data.getList("results", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }

    public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        data.put("resourceDemand", demand.save());
        writeAim(data, "previousAim", previousAim);
        writeAim(data, "currentAim", currentAim);
        data.put("held", held.serializeNBT(registries));
        data.put("results", results.save(registries));
        data.putLong("interactionStart", interactionStart);
        if (interactionHead != null) data.putLong("interactionHead", interactionHead.asLong());
        return data;
    }
}
