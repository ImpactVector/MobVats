package net.impactvector.mobvats.gamecontent.item;

import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Server-thread transaction: validate specimen and plan all costs/output before mutating inventory. */
public final class CaptureTransaction {
    public record Result(boolean success, String diagnostic) {}

    public static boolean boss(EntityType<?> type) {
        return Set.of("minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian", "minecraft:warden")
                .contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    public static String eligibility(ItemStack tool, LivingEntity target) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        var definition = PassiveMobDefinitionRegistry.resolve(id);
        if (definition == null) return "No loaded definition: " + id;
        var p = definition.capture();
        if (!p.enabled()) return "Capture disabled: " + id + " / " + p.status();
        if (p.requires_alive() && !target.isAlive()) return "Capture requires a living target";
        if (p.adult_only() && target.isBaby()) return "Capture requires an adult specimen";
        if (!p.allow_hostile()
                && (target instanceof net.minecraft.world.entity.monster.Enemy
                        || target.getType().getCategory() == MobCategory.MONSTER)) return "Capture hostile gate: " + id;
        if (!p.allow_boss() && boss(target.getType())) return "Capture boss gate: " + id;
        if (!ResourceSelectors.items(List.of(p.tool())).test(tool)) return "Capture requires tool: " + p.tool();
        return null;
    }

    public static Result capture(ItemStack tool, Player player, LivingEntity target) {
        if (player.level().isClientSide) return new Result(false, "Capture transaction requires server");
        var reason = eligibility(tool, target);
        if (reason != null) return new Result(false, reason);
        var definition = PassiveMobDefinitionRegistry.resolve(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
        final CapturedSpecimen specimen;
        try {
            specimen = CapturedSpecimen.capture(target);
        } catch (IllegalArgumentException error) {
            return new Result(false, error.getMessage());
        }
        var policy = definition.capture();
        var inventory = player.getInventory();
        var before = new ArrayList<ItemStack>();
        var after = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            before.add(inventory.getItem(i).copy());
            after.add(inventory.getItem(i).copy());
        }
        boolean creative = player.getAbilities().instabuild;
        float health = player.getHealth();
        if (!creative && policy.health_cost() > 0 && health <= policy.health_cost())
            return new Result(false, "Insufficient health for capture");
        if (!creative && policy.cost() != null) {
            var cost = policy.cost();
            int remaining = cost.count();
            var selector = ResourceSelectors.items(cost.items());
            for (var stack : after)
                if (selector.test(stack)) {
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    remaining -= taken;
                    if (remaining == 0) break;
                }
            if (remaining > 0)
                return new Result(false, "Capture cost requires " + cost.count() + " of " + cost.items());
        }
        var essence = new ItemStack(Content.ESSENCE.get());
        essence.set(DataComponents.CUSTOM_DATA, CustomData.of(specimen.save()));
        for (int i = 0; i < inventory.items.size() && !essence.isEmpty(); i++) {
            var stack = after.get(i);
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, essence)
                    && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(1);
                essence.shrink(1);
            }
        }
        for (int i = 0; i < inventory.items.size() && !essence.isEmpty(); i++)
            if (after.get(i).isEmpty()) {
                after.set(i, essence.copy());
                essence.setCount(0);
            }
        if (!essence.isEmpty()) return new Result(false, "Inventory full; capture requires space for Essence");
        var transaction = new ProcessTransaction()
                .add(new ProcessTransaction.Step(
                        inventory,
                        () -> {
                            for (int i = 0; i < before.size(); i++)
                                if (!ItemStack.matches(before.get(i), inventory.getItem(i))) return false;
                            return PassiveMobDefinitionRegistry.resolve(specimen.identity()) == definition
                                    && eligibility(tool, target) == null;
                        },
                        () -> {
                            for (int i = 0; i < after.size(); i++)
                                inventory.setItem(i, after.get(i).copy());
                            inventory.setChanged();
                            return true;
                        },
                        () -> {
                            for (int i = 0; i < before.size(); i++)
                                inventory.setItem(i, before.get(i).copy());
                            inventory.setChanged();
                        }));
        if (!creative && policy.health_cost() > 0)
            transaction.add(new ProcessTransaction.Step(
                    player,
                    () -> player.getHealth() == health,
                    () -> {
                        player.setHealth(health - policy.health_cost());
                        return true;
                    },
                    () -> player.setHealth(health)));
        return transaction.commit()
                ? new Result(true, "Captured " + specimen.identity())
                : new Result(false, "Capture changed before commit");
    }

    private CaptureTransaction() {}
}
