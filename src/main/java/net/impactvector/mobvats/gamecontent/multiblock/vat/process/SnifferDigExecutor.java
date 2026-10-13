package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;

/** Uses vanilla Sniffer.dropSeed's GIFT loot table/context, without world spawning. */
public final class SnifferDigExecutor extends EnvironmentalToolExecutor {
    @Override
    public boolean supports(Level level, ResourceLocation identity, ItemStack held) {
        return identity.equals(EntityType.SNIFFER.builtInRegistryHolder().key().location())
                && held.getItem() instanceof BlockItem block
                && block.getBlock().defaultBlockState().is(BlockTags.SNIFFER_DIGGABLE_BLOCK);
    }

    @Override
    protected boolean ready(LivingEntity specimen) {
        return specimen instanceof Sniffer sniffer && !sniffer.isBaby();
    }

    @Override
    protected EntityItemInteractionExecutor.Plan plan(
            ProcessContext c, LivingEntity specimen, InteractionLevel level, ItemStack held) {
        var table = c.level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SNIFFER_DIGGING);
        var params = new LootParams.Builder(c.level())
                .withParameter(
                        LootContextParams.ORIGIN,
                        specimen.position().add(specimen.getForward().scale(2.25)))
                .withParameter(LootContextParams.THIS_ENTITY, specimen)
                .create(LootContextParamSets.GIFT);
        return new EntityItemInteractionExecutor.Plan(
                CapturedSpecimen.capture(specimen).state(), held, table.getRandomItems(params));
    }
}
