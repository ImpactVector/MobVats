package net.impactvector.mobvats.gamecontent.fluid.compound;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.*;

/** Bucket contents preserve the entire FluidStack. World placement deliberately produces a family-only spill. */
public final class CompoundBucketItem extends ComponentPreservingBucketItem {
    public CompoundBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    public Component getName(ItemStack stack) {
        var fluid = contents(stack);
        var name = MixerRecipeRegistry.name(fluid, true);
        return name == null ? super.getName(stack) : name;
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        appendComposition(contents(stack), tooltip);
        tooltip.add(Component.translatable("tooltip.mobvats.compound_spill"));
    }

    public static void appendComposition(FluidStack stack, List<Component> tooltip) {
        var composition = stack.get(CompoundFluids.COMPOSITION.get());
        if (composition == null) {
            if (CompoundFluids.isCompound(stack))
                tooltip.add(Component.translatable("tooltip.mobvats.composition_unknown"));
            return;
        }
        tooltip.add(Component.translatable("tooltip.mobvats.composition"));
        for (var entry : composition.entries())
            tooltip.add(Component.literal(label(entry) + "  " + entry.amount()
                    + (entry.kind() == CompoundComposition.Kind.FLUID ? " mB" : " items")));
    }

    private static String label(CompoundComposition.Entry entry) {
        var selector = com.google.gson.JsonParser.parseString(entry.selector());
        if (selector.isJsonObject()) {
            var object = selector.getAsJsonObject();
            if (object.has("tag")) return "#" + object.get("tag").getAsString();
            String key = entry.kind() == CompoundComposition.Kind.ITEM ? "item" : "fluid";
            if (object.has(key)) {
                var id = net.minecraft.resources.ResourceLocation.parse(
                        object.get(key).getAsString());
                if (entry.kind() == CompoundComposition.Kind.ITEM)
                    return net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .get(id)
                            .getDescription()
                            .getString();
                return net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .get(id)
                        .getFluidType()
                        .getDescription()
                        .getString();
            }
        }
        return entry.selector();
    }
}
