package net.impactvector.mobvats.gamecontent.item;

import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Shapeless mode conversion, carrying exact-fluid configuration between Pump variants. */
public final class BoundarySwitchRecipe extends ShapelessRecipe {
    public BoundarySwitchRecipe(ShapelessRecipe recipe) {
        super(recipe.getGroup(), recipe.category(), recipe.getResultItem(null), recipe.getIngredients());
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        var result = super.assemble(input, registries);
        for (var stack : input.items())
            if (!stack.isEmpty()) {
                var state = EndpointState.read(stack);
                // Item and fluid selectors have different registries; a Void category switch resets its selector.
                if (result.is(net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.block(
                                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.PullPump)
                                .asItem())
                        || result.is(net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.block(
                                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.PushPump)
                                .asItem())
                        || result.is(
                                Content.block(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.PullPump)
                                        .asItem())
                        || result.is(
                                Content.block(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.PushPump)
                                        .asItem())) EndpointState.write(result, state);
            }
        return result;
    }

    public RecipeSerializer<?> getSerializer() {
        return Content.BOUNDARY_SWITCH.get();
    }
}
