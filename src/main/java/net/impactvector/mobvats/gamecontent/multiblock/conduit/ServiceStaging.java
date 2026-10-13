package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Bounded operation allowance per fluid, persisted by the owning endpoint and portable item. */
public final class ServiceStaging {
    public static final int CAPACITY = 1000;
    private final NavigableMap<ResourceLocation, FluidTank> tanks =
            new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
    private final Runnable changed;

    public ServiceStaging(Runnable changed) {
        this.changed = changed;
    }

    public FluidTank tank(FluidStack output) {
        if (output.isEmpty()) throw new IllegalArgumentException("Empty output");
        var id = BuiltInRegistries.FLUID.getKey(output.getFluid());
        return tanks.computeIfAbsent(
                id, key -> new FluidTank(
                        CAPACITY,
                        s -> BuiltInRegistries.FLUID.getKey(s.getFluid()).equals(key)) {
                    protected void onContentsChanged() {
                        changed.run();
                    }
                });
    }

    public List<FluidTank> tanks() {
        return List.copyOf(tanks.values());
    }
    /** Unpublished service tanks keep operation simulation free of storage mutations. */
    public final class Pending {
        private final Map<ResourceLocation, FluidTank> before = new TreeMap<>(tanks);
        private final Map<ResourceLocation, FluidTank> created =
                new TreeMap<>(Comparator.comparing(ResourceLocation::toString));

        public FluidTank tank(FluidStack output) {
            var id = BuiltInRegistries.FLUID.getKey(output.getFluid());
            var existing = before.get(id);
            if (existing != null) return existing;
            return created.computeIfAbsent(
                    id, key -> new FluidTank(
                            CAPACITY,
                            s -> BuiltInRegistries.FLUID.getKey(s.getFluid()).equals(key)) {
                        protected void onContentsChanged() {
                            changed.run();
                        }
                    });
        }

        public boolean valid() {
            return before.equals(tanks);
        }

        public void publish() {
            tanks.putAll(created);
        }
    }

    public Pending prepare() {
        return new Pending();
    }

    public List<String> diagnostics() {
        return tanks.entrySet().stream()
                .map(e -> "Service: " + e.getValue().getFluidAmount() + "/"
                        + e.getValue().getCapacity() + " mB " + e.getKey())
                .toList();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tanks.forEach((id, t) -> {
            var saved = t.writeToNBT(registries, new CompoundTag());
            saved.putInt("capacity", t.getCapacity());
            tag.put(id.toString(), saved);
        });
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        tanks.clear();
        for (var name : new TreeSet<>(tag.getAllKeys())) {
            var id = ResourceLocation.parse(name);
            var fluid = BuiltInRegistries.FLUID.get(id);
            var tank = tank(new FluidStack(fluid, 1));
            tank.readFromNBT(registries, tag.getCompound(name));
            tank.setCapacity(Math.max(CAPACITY, tag.getCompound(name).getInt("capacity")));
            // Authored service state is bounded. Never reinterpret old controller escrow as service state.
            if (tank.getFluidAmount() > tank.getCapacity())
                throw new IllegalArgumentException("Service staging overflow: " + id);
        }
    }
}
