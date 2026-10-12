package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;

/** Loot context only: neither specimen nor FakePlayer is added, ticked, killed or persisted. */
public final class VanillaEntityLoot {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("97b0dfb8-b26e-40d8-aee2-fc93817d52d0"), "[MobVats]");
    public record Result(List<ItemStack> items, int xp) {
        public Result { items = items.stream().map(ItemStack::copy).toList(); }
    }
    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.canPerformAction(ItemAbilities.SWORD_SWEEP));
    }
    public static Result roll(ServerLevel level, ResourceLocation identity, Vec3 position, ItemStack weapon, long seed) {
        return roll(level,identity,position,weapon,seed,new net.minecraft.nbt.CompoundTag());
    }
    public static Result roll(ServerLevel level, ResourceLocation identity, Vec3 position, ItemStack weapon, long seed, net.minecraft.nbt.CompoundTag state) {
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(identity))throw new IllegalArgumentException("Unknown loot specimen: "+identity);
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return new Result(List.of(), 0);
        var entity = BuiltInRegistries.ENTITY_TYPE.get(identity).create(level);
        var victim = entity instanceof Mob mob ? mob : null;
        if (victim == null) throw new IllegalStateException("Entity loot context could not be created");
        net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.apply(victim,state);
        victim.setPos(position); victim.setNoAi(true);
        var attacker = new FakePlayer(level, PROFILE);
        attacker.setPos(position); attacker.getAbilities().instabuild = false;
        attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        // Fire Aspect's vanilla loot consequence is the victim's burning predicate.
        if (weapon.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT)) > 0)
            victim.setRemainingFireTicks(100);
        var source = level.damageSources().playerAttack(attacker);
        var params = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, victim)
            .withParameter(LootContextParams.ORIGIN, position)
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .withParameter(LootContextParams.ATTACKING_ENTITY, attacker)
            .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, attacker)
            .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, attacker)
            .withLuck(attacker.getLuck()).create(LootContextParamSets.ENTITY);
        var table = level.getServer().reloadableRegistries().getLootTable(victim.getLootTable());
        var items = table.getRandomItems(params, seed);
        int xp = EventHooks.getExperienceDrop(victim, attacker, victim.getExperienceReward(level, attacker));
        return new Result(items, Math.max(0, xp));
    }
    public static ItemStack damageWeapon(ServerLevel level, ResourceLocation identity, ItemStack weapon) {
        var result = weapon.copy();
        var entity = BuiltInRegistries.ENTITY_TYPE.get(identity).create(level);
        var victim = entity instanceof Mob mob ? mob : null;
        var attacker = new FakePlayer(level, PROFILE);
        attacker.getAbilities().instabuild = false;
        attacker.setItemInHand(InteractionHand.MAIN_HAND, result);
        // Item combat hooks use ItemStack.hurtAndBreak for vanilla swords, including
        // NeoForge damageItem, Unbreaking and the unbreakable component.
        if (victim != null && result.hurtEnemy(victim, attacker))
            result.postHurtEnemy(victim, attacker);
        return result;
    }
    public static Mob specimen(ServerLevel level,ResourceLocation identity,net.minecraft.nbt.CompoundTag state){var entity=BuiltInRegistries.ENTITY_TYPE.get(identity).create(level);if(!(entity instanceof Mob mob))throw new IllegalArgumentException("Unsupported combat specimen "+identity);net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.apply(mob,state);mob.setNoAi(true);return mob;}
    public static float health(ServerLevel level,ResourceLocation identity,net.minecraft.nbt.CompoundTag state){return specimen(level,identity,state).getMaxHealth();}
    /** Full-strength weapon hit, with damage attributes and deterministic enchantment effects only. */
    public static float damage(ServerLevel level,ResourceLocation identity,ItemStack weapon,net.minecraft.nbt.CompoundTag state){
        var victim=specimen(level,identity,state);var attacker=new FakePlayer(level,PROFILE);attacker.setItemInHand(InteractionHand.MAIN_HAND,weapon.copy());
        var attribute=attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        weapon.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND,(holder,modifier)->{if(holder.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))attribute.addOrUpdateTransientModifier(modifier);});
        var source=level.damageSources().playerAttack(attacker);float base=(float)attribute.getValue();
        float enchanted=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,weapon,victim,source,base);
        return Math.max(0,enchanted+weapon.getItem().getAttackDamageBonus(victim,enchanted,source));
    }
    public static ItemStack damageShears(ServerLevel level,ItemStack stack){var result=stack.copy();var player=new FakePlayer(level,PROFILE);player.setItemInHand(InteractionHand.MAIN_HAND,result);result.hurtAndBreak(1,player,net.minecraft.world.entity.EquipmentSlot.MAINHAND);return result;}
    private VanillaEntityLoot() {}
}
