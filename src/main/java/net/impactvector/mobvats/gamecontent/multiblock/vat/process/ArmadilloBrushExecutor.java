package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;

/** Vanilla scute contract plus copied 16-point brush damage, which needs a ServerLevel. */
public final class ArmadilloBrushExecutor extends EnvironmentalToolExecutor {
    @Override public boolean supports(Level level,ResourceLocation identity,ItemStack held) {
        return identity.equals(EntityType.ARMADILLO.builtInRegistryHolder().key().location()) && held.canPerformAction(ItemAbilities.BRUSH_BRUSH);
    }
    @Override protected boolean ready(LivingEntity specimen) {return specimen instanceof Armadillo armadillo && !armadillo.isBaby();}
    @Override protected EntityItemInteractionExecutor.Plan plan(ProcessContext c,LivingEntity specimen,InteractionLevel level,ItemStack held) {
        if(!((Armadillo)specimen).brushOffScute())return null;
        held.hurtAndBreak(16,c.level(),(net.minecraft.server.level.ServerPlayer)null,item->{});
        return new EntityItemInteractionExecutor.Plan(CapturedSpecimen.capture(specimen).state(),held,level.drops);
    }
}
