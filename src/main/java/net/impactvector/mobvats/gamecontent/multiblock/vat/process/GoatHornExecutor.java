package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Vanilla horn generation on an isolated goat; the acquired ram target is retained. */
public final class GoatHornExecutor extends EnvironmentalToolExecutor {
    @Override
    public boolean supports(Level level, ResourceLocation identity, ItemStack held) {
        return identity.equals(EntityType.GOAT.builtInRegistryHolder().key().location())
                && held.getItem() instanceof BlockItem block
                && block.getBlock().defaultBlockState().is(BlockTags.SNAPS_GOAT_HORN);
    }

    @Override
    protected boolean ready(LivingEntity specimen) {
        return specimen instanceof Goat goat && !goat.isBaby() && (goat.hasLeftHorn() || goat.hasRightHorn());
    }

    @Override
    protected EntityItemInteractionExecutor.Plan plan(
            ProcessContext c, LivingEntity specimen, InteractionLevel level, ItemStack held) {
        if (!((Goat) specimen).dropHorn()) return null;
        return new EntityItemInteractionExecutor.Plan(
                CapturedSpecimen.capture(specimen).state(), held, level.drops);
    }
}
