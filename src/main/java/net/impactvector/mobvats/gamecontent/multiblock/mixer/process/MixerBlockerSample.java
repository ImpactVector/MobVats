package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import com.google.gson.JsonParser;
import java.util.Objects;
import net.impactvector.mobvats.gamecontent.fluid.compound.CompoundFluids;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** One acquired product, encoded with the existing stack codecs; no selectors, tags or recipe IDs. */
public record MixerBlockerSample(String item, String fluid) {
    public static final MixerBlockerSample EMPTY = new MixerBlockerSample("", "");

    public MixerBlockerSample {
        if (!item.isEmpty() && !fluid.isEmpty()) throw new IllegalArgumentException("One sample required");
        if (!item.isEmpty())
            ItemStack.CODEC
                    .parse(ResourceSelectors.ops(), JsonParser.parseString(item))
                    .getOrThrow();
        if (!fluid.isEmpty())
            FluidStack.CODEC
                    .parse(ResourceSelectors.ops(), JsonParser.parseString(fluid))
                    .getOrThrow();
    }

    public static MixerBlockerSample sampled(ItemStack stack) {
        if (stack.isEmpty()) return EMPTY;
        return new MixerBlockerSample(
                ItemStack.CODEC
                        .encodeStart(ResourceSelectors.ops(), stack.copyWithCount(1))
                        .getOrThrow()
                        .toString(),
                "");
    }

    public static MixerBlockerSample sampled(FluidStack stack) {
        if (stack.isEmpty()) return EMPTY;
        // The composition component is the current MobVats mechanical fluid metadata.
        // Rebuild the stack to exclude custom names, lore and unrelated presentation components.
        var product = new FluidStack(stack.getFluid(), 1);
        var composition = stack.get(CompoundFluids.COMPOSITION.get());
        if (composition != null) product.set(CompoundFluids.COMPOSITION.get(), composition);
        return new MixerBlockerSample(
                "",
                FluidStack.CODEC
                        .encodeStart(ResourceSelectors.ops(), product)
                        .getOrThrow()
                        .toString());
    }

    public boolean matches(ItemStack output) {
        return !item.isEmpty()
                && !output.isEmpty()
                && ItemStack.isSameItemSameComponents(
                        ItemStack.CODEC
                                .parse(ResourceSelectors.ops(), JsonParser.parseString(item))
                                .getOrThrow(),
                        output);
    }

    public boolean matches(FluidStack output) {
        if (fluid.isEmpty() || output.isEmpty()) return false;
        var sample = FluidStack.CODEC
                .parse(ResourceSelectors.ops(), JsonParser.parseString(fluid))
                .getOrThrow();
        return sample.is(output.getFluid())
                && Objects.equals(
                        sample.get(CompoundFluids.COMPOSITION.get()), output.get(CompoundFluids.COMPOSITION.get()));
    }
}
