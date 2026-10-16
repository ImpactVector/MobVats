package net.impactvector.mobvats.gamecontent.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

/** Versioned portable endpoint state; excludes controller ownership and ordinary recipe port staging. */
public final class EndpointState {
    public static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getCompound("mobvats_endpoint");
    }

    public static void write(ItemStack stack, CompoundTag state) {
        var data =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        state.putInt("version", 1);
        data.put("mobvats_endpoint", state);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String fluidId(FluidStack stack) {
        if (stack.getComponents().isEmpty())
            return BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
        // Sampled component-bearing resources pin the entire identity, never the volume.
        return "="
                + FluidStack.CODEC
                        .encodeStart(
                                net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors.ops(),
                                stack.copyWithAmount(1))
                        .getOrThrow();
    }

    public static ItemStack capture(ItemStack stack, net.minecraft.world.level.block.entity.BlockEntity entity) {
        var state = new CompoundTag();
        if (entity != null
                && entity.getBlockState().getBlock()
                        instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock) {
            state.put(
                    "functionState",
                    net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalState.capture(entity));
        }
        if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerMeterEntity meter) {
            state.put("meterConfiguration", meter.configuration());
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFeDriveEntity drive) {
            state.put("feDrive", drive.saveEnergy());
        } else if (entity
                instanceof
                net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerCombustionDriveEntity drive) {
            state.put("combustionFuel", drive.saveFuel(entity.getLevel().registryAccess()));
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.creative.CreativeEndpointEntity endpoint) {
            state.put(
                    "creativeConfiguration",
                    endpoint.configuration(entity.getLevel().registryAccess()));
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity buffer) {
            state.putString("filter", buffer.filter());
            state.put("tank", buffer.tank.writeToNBT(entity.getLevel().registryAccess(), new CompoundTag()));
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPumpEntity boundary) {
            state.putString("filter", boundary.filter());
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFluidVoidEntity boundary) {
            state.putString("filter", boundary.filter());
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryEntity boundary) {
            state.putString("filter", boundary.filter());
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.common.ConfigurableEndpoint port) {
            if (port.endpoint().resource()
                    == net.impactvector.mobvats.gamecontent.multiblock.common.EndpointConfiguration.Resource.NONE) {
                if (state.contains("functionState")) write(stack, state);
                return stack;
            }
            port.endpoint().capture(state);
            if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPortEntity input)
                state.put("fuelStore", input.saveStore(entity.getLevel().registryAccess()));
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity part
                && part.service().tanks().stream().anyMatch(t -> !t.isEmpty())) {
            state.put("serviceStaging", part.service().save(entity.getLevel().registryAccess()));
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity part
                && part.service().tanks().stream().anyMatch(t -> !t.isEmpty())) {
            state.put("serviceStaging", part.service().save(entity.getLevel().registryAccess()));
        } else if (!state.contains("functionState")) return stack;
        write(stack, state);
        return stack;
    }

    public static void restore(ItemStack stack, net.minecraft.world.level.block.entity.BlockEntity entity) {
        var state = read(stack);
        if (state.getInt("version") != 1) return;
        if (state.contains("functionState")) {
            net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalState.restore(
                    entity, state.getCompound("functionState"));
            return;
        }
        if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerMeterEntity meter
                && state.contains("meterConfiguration"))
            meter.loadConfiguration(state.getCompound("meterConfiguration"));
        if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFeDriveEntity drive
                && state.contains("feDrive")) drive.loadEnergy(state.getCompound("feDrive"));
        if (entity
                        instanceof
                        net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerCombustionDriveEntity drive
                && state.contains("combustionFuel"))
            drive.loadFuel(
                    state.getCompound("combustionFuel"), entity.getLevel().registryAccess());
        if (entity instanceof net.impactvector.mobvats.gamecontent.creative.CreativeEndpointEntity endpoint) {
            endpoint.configuration(
                    state.getCompound("creativeConfiguration"),
                    entity.getLevel().registryAccess());
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity buffer) {
            buffer.tank.readFromNBT(entity.getLevel().registryAccess(), state.getCompound("tank"));
            buffer.filter(state.getString("filter"));
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPumpEntity boundary) {
            boundary.filter(state.getString("filter"));
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFluidVoidEntity boundary) {
            boundary.filter(state.getString("filter"));
        } else if (entity
                instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryEntity boundary) {
            boundary.filter(state.getString("filter"));
        } else if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.common.ConfigurableEndpoint port) {
            port.endpoint().restore(state);
            if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPortEntity input
                    && state.contains("fuelStore"))
                input.loadStore(
                        state.getCompound("fuelStore"), entity.getLevel().registryAccess());
        }

        if (state.contains("serviceStaging")) {
            if (entity instanceof net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity part)
                part.service()
                        .load(
                                state.getCompound("serviceStaging"),
                                entity.getLevel().registryAccess());
            else if (entity
                    instanceof net.impactvector.mobvats.gamecontent.multiblock.mixer.part.AbstractMixerEntity part)
                part.service()
                        .load(
                                state.getCompound("serviceStaging"),
                                entity.getLevel().registryAccess());
        }
        entity.setChanged();
    }

    private EndpointState() {}
}
