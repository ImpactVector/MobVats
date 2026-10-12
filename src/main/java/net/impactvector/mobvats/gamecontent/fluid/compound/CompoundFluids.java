package net.impactvector.mobvats.gamecontent.fluid.compound;

import java.util.*;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.*;

public final class CompoundFluids {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MobVats.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundComposition>> COMPOSITION = COMPONENTS.register("composition",
        () -> DataComponentType.<CompoundComposition>builder().persistent(CompoundComposition.CODEC).networkSynchronized(CompoundComposition.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> BUCKET_CONTENTS = COMPONENTS.register("compound_bucket_contents",
        () -> DataComponentType.<net.neoforged.neoforge.fluids.SimpleFluidContent>builder().persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC).networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC).build());
    public static void register(IEventBus bus) { COMPONENTS.register(bus); }
    public static boolean isCompound(FluidStack stack) {
        return stack.is(ProductFluids.CULTURE_SLURRY.get()) || stack.is(ProductFluids.FEEDING_SLURRY.get()) || stack.is(ProductFluids.COMPONENT_FLUID.get());
    }
    /** One authored formula per distinct compound identity in each input, regardless of consumed volume.
     * Splitting a physical stack across buffers does not duplicate its formula. No ratios or rounding.
     */
    public static void appendInput(List<CompoundComposition.Entry> result, String selector, int amount, List<FluidStack> consumed) {
        var compound = consumed.stream().filter(CompoundFluids::isCompound).toList();
        if (compound.isEmpty()) { result.add(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, selector, amount)); return; }
        if (compound.size()!=consumed.size()) throw new IllegalArgumentException("A fluid input cannot mix ordinary and compound identities; use separate inputs");
        var identities = new ArrayList<FluidStack>();
        for (var stack : compound) {
            if (identities.stream().anyMatch(s -> FluidStack.isSameFluidSameComponents(s, stack))) continue;
            var formula = stack.get(COMPOSITION.get());
            if (formula==null) throw new IllegalArgumentException("Compound input has unknown composition (legacy fluid or world spill); use an explicit composition recipe");
            result.addAll(formula.entries()); identities.add(stack.copy());
        }
    }
    private CompoundFluids() {}
}
