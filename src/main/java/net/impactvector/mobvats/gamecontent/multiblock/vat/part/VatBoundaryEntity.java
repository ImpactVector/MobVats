package net.impactvector.mobvats.gamecontent.multiblock.vat.part;

import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.*;

/** Stateless directional boundary; configuration and the cosmetic pulse are endpoint-owned. */
public final class VatBoundaryEntity extends AbstractVatEntity
        implements net.impactvector.mobvats.gamecontent.multiblock.common.WorldFluidPumps.Endpoint,
                net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidSink {
    private String filter = "", status = "Idle";
    private long flareUntil;
    private String fillDetails = "";

    public String fillDetails() {
        return fillDetails;
    }

    public void fillDiagnostic(String result, VatMediumStorage medium, FluidStack incoming, int accepted) {
        String previous = status, previousDetails = fillDetails;
        status(result);
        fillDetails = "Medium: " + fluidId(medium.cleanPrototype()) + " | " + medium.getFluidAmount() + " / "
                + medium.getCapacity() + " mB\nIncoming: " + fluidId(incoming) + "\nAccepted: " + accepted
                + " mB / operation";
        if (!previous.equals(status) || !previousDetails.equals(fillDetails)) notifyBlockUpdate();
    }

    private static String fluidId(FluidStack fluid) {
        return fluid.isEmpty()
                ? "none"
                : net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(fluid.getFluid())
                        .toString();
    }

    public VatBoundaryEntity(VatPartType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String filter() {
        return filter;
    }

    public String status() {
        return isVoid() ? (flaring() ? "Voiding" : "Idle") : status;
    }

    public void status(String value) {
        status = value;
    }

    public boolean itemVoid() {
        return getPartType() == VatPartType.ItemVoid;
    }

    public boolean isVoid() {
        return itemVoid() || getPartType() == VatPartType.FluidVoid;
    }

    public ConduitResource resource() {
        return itemVoid() ? ConduitResource.ITEM : ConduitResource.FLUID;
    }

    public boolean accepts(FluidStack stack) {
        return !itemVoid()
                && !stack.isEmpty()
                && (filter.isEmpty()
                        || ResourceSelectors.fluids(java.util.List.of(filter)).test(stack));
    }

    public boolean accepts(ItemStack stack) {
        return itemVoid()
                && !stack.isEmpty()
                && (filter.isEmpty()
                        || ResourceSelectors.items(java.util.List.of(filter)).test(stack));
    }

    public void filter(String value) {
        if (!value.isEmpty()) {
            if (itemVoid()) ResourceSelectors.items(java.util.List.of(value));
            else ResourceSelectors.fluids(java.util.List.of(value));
        }
        filter = value;
        setChanged();
        notifyBlockUpdate();
    }

    public boolean configure(Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return false;
        if (level.isClientSide) return true;
        if (isMachineAssembled()) {
            player.displayClientMessage(Component.translatable("message.mobvats.filter_locked"), true);
            return true;
        }
        var held = player.getItemInHand(hand);
        String value = "";
        if (!held.isEmpty()) {
            if (itemVoid()) value = EndpointState.itemId(held);
            else {
                var sample = FluidUtil.getFluidContained(held);
                if (sample.isEmpty()) return true;
                value = EndpointState.fluidId(sample.get());
            }
        }
        filter(value);
        player.displayClientMessage(
                value.isEmpty()
                        ? Component.translatable("message.mobvats.filter_cleared")
                        : Component.translatable("tooltip.mobvats.endpoint_filter", value),
                true);
        return true;
    }

    public void consumed() {
        if (level == null || level.isClientSide) return;
        flareUntil = level.getGameTime() + 8;
        status = "Voiding";
        notifyBlockUpdate();
    }

    public boolean flaring() {
        return level != null && level.getGameTime() < flareUntil;
    }

    public net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessTransaction.Step disposal() {
        long before = flareUntil;
        String previous = status;
        return new net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessTransaction.Step(
                this,
                () -> !isRemoved() && isMachineAssembled(),
                () -> {
                    consumed();
                    return true;
                },
                () -> {
                    flareUntil = before;
                    status = previous;
                    notifyBlockUpdate();
                });
    }

    public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data, registries, reason);
        data.putString("boundaryFilter", filter);
        data.putLong("voidFlareUntil", flareUntil);
        data.putString("boundaryStatus", status);
        data.putString("fillDetails", fillDetails);
        return data;
    }

    public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data, registries, reason);
        filter = data.getString("boundaryFilter");
        flareUntil = data.getLong("voidFlareUntil");
        status = data.contains("boundaryStatus") ? data.getString("boundaryStatus") : "Idle";
        fillDetails = data.getString("fillDetails");
    }
}
