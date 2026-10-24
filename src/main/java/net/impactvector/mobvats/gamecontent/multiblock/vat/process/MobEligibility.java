package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

/** Single containment policy. Enforcement of the configured tier is deferred to slice 2. */
public final class MobEligibility {
    public static boolean legacyHostile(ResourceLocation id) {
        return id != null
                && BuiltInRegistries.ENTITY_TYPE
                        .getOptional(id)
                        .map(type -> type.getCategory() == MobCategory.MONSTER)
                        .orElse(false);
    }

    public static boolean allows(ResourceLocation id, VatTier tier) {
        // Temporary compatibility gate; minimum_vat_tier remains data only in slice 1.
        return tier != VatTier.T1 || !legacyHostile(id);
    }

    private MobEligibility() {}
}
