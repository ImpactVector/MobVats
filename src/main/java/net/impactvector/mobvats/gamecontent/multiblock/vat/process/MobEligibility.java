package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

/** Species containment is controlled by the loaded profile, independently of capture and activation. */
public final class MobEligibility {
    public static boolean legacyHostile(ResourceLocation id) {
        return id != null
                && BuiltInRegistries.ENTITY_TYPE
                        .getOptional(id)
                        .map(type -> type.getCategory() == MobCategory.MONSTER)
                        .orElse(false);
    }

    public static boolean allows(ResourceLocation id, VatTier tier) {
        var definition = id == null ? null : PassiveMobDefinitionRegistry.resolve(id);
        return definition != null && tier.ordinal() + 1 >= definition.minimum_vat_tier();
    }

    public static String requirement(ResourceLocation id, VatTier tier) {
        var definition = id == null ? null : PassiveMobDefinitionRegistry.resolve(id);
        return definition == null
                ? "No valid loaded mob profile: " + id
                : id + " requires minimum Vat tier T" + definition.minimum_vat_tier() + "; current tier " + tier;
    }

    private MobEligibility() {}
}
