package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.*;

/** Survival Player interaction dispatch (NeoForge event, entity, then item) on detached objects. */
public class EntityItemInteractionExecutor implements ToolInteractionExecutor {
    private record Dispatch(net.minecraft.world.InteractionResult result, boolean itemConsumed) {}
    /** Mirrors Player.interactOn's public survival contracts, retaining which hook consumed
     * the action so canceled events/entity-handled actions cannot falsely damage an unused tool. */
    private static Dispatch interact(Player player, LivingEntity specimen) {
        var hand = InteractionHand.MAIN_HAND;
        var held = player.getItemInHand(hand);
        var before = held.copy();
        var canceled = CommonHooks.onInteractEntity(player, specimen, hand);
        if (canceled != null) return new Dispatch(canceled, false);
        var result = specimen.interact(player, hand);
        if (result.consumesAction()) {
            if (held.isEmpty()) net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem(player, before, hand);
            return new Dispatch(result, false);
        }
        if (held.isEmpty()) return new Dispatch(net.minecraft.world.InteractionResult.PASS, false);
        result = held.interactLivingEntity(player, specimen, hand);
        if (result.consumesAction()) {
            player.level()
                    .gameEvent(
                            net.minecraft.world.level.gameevent.GameEvent.ENTITY_INTERACT,
                            specimen.position(),
                            net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
            if (held.isEmpty()) {
                net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem(player, before, hand);
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        return new Dispatch(result, result.consumesAction());
    }

    public record Plan(CompoundTag specimen, ItemStack held, List<ItemStack> drops) {
        public Plan {
            specimen = specimen.copy();
            held = held.copy();
            drops = drops.stream().map(ItemStack::copy).toList();
        }

        @Override
        public CompoundTag specimen() {
            return specimen.copy();
        }

        @Override
        public ItemStack held() {
            return held.copy();
        }

        @Override
        public List<ItemStack> drops() {
            return drops.stream().map(ItemStack::copy).toList();
        }
    }

    @Override
    public boolean supports(Level level, ResourceLocation identity, ItemStack stack) {
        // Arbitrary damageable item hooks frequently require ServerLevel; use a dedicated adapter.
        // Explicit authored interactions may be supplied entirely by an EntityInteract listener.
        return !stack.isDamageableItem() || stack.canPerformAction(ItemAbilities.SHEARS_HARVEST);
    }

    public static Plan plan(
            ServerLevel source,
            java.util.function.Function<Level, LivingEntity> factory,
            ItemStack held,
            Vec3 position) {
        var level = new InteractionLevel(source);
        try {
            var specimen = factory.apply(level);
            if (specimen == null) return null;
            specimen.setPos(position);
            var player =
                    new Player(
                            level,
                            BlockPos.containing(position),
                            0,
                            new GameProfile(
                                    UUID.fromString("20a969db-728b-4fb4-b744-979f75814e67"), "[MobVats Interaction]")) {
                        public boolean isSpectator() {
                            return false;
                        }

                        public boolean isCreative() {
                            return false;
                        }
                    };
            player.setPos(position);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            if (held.isDamageableItem() && !held.canPerformAction(ItemAbilities.SHEARS_HARVEST)) return null;
            var before = new CompoundTag();
            specimen.saveWithoutId(before);
            var dispatch = interact(player, specimen);
            if (level.rejected || !dispatch.result().consumesAction() || specimen.isRemoved()) return null;
            var after = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
            // LivingEntity damage APIs only charge durability in a ServerLevel. Preserve the
            // public one-point shears contract with damageItem and enchantment hooks on a copy.
            if (dispatch.itemConsumed()
                    && held.canPerformAction(ItemAbilities.SHEARS_HARVEST)
                    && ItemStack.matches(held, after)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, after);
                after.hurtAndBreak(
                        1,
                        source,
                        player,
                        item -> player.onEquippedItemBroken(item, net.minecraft.world.entity.EquipmentSlot.MAINHAND));
                if (after.isEmpty()) {
                    net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem(
                            player, held.copy(), InteractionHand.MAIN_HAND);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
                after = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
            }
            var drops = new ArrayList<>(level.drops);
            for (var stack : player.getInventory().items)
                if (!stack.isEmpty() && stack != player.getItemInHand(InteractionHand.MAIN_HAND))
                    drops.add(stack.copy());
            for (var stack : player.getInventory().offhand) if (!stack.isEmpty()) drops.add(stack.copy());
            for (var stack : player.getInventory().armor) if (!stack.isEmpty()) drops.add(stack.copy());
            if (after.getCount() > 1) {
                drops.add(after.copyWithCount(after.getCount() - 1));
                after.setCount(1);
            }
            var changed = new CompoundTag();
            specimen.saveWithoutId(changed);
            if (level.rejected
                    || specimen.isRemoved()
                    || !CapturedSpecimen.preservesInteractionChanges(before, changed)) return null;
            return new Plan(CapturedSpecimen.capture(specimen).state(), after, drops);
        } catch (UnsupportedOperationException | ClassCastException exception) {
            return null; // Environmental/ServerLevel-only interactions require a registered adapter.
        }
    }

    @Override
    public ProcessResult execute(ProcessContext c, ProcessState state, ToolInteractionDefinition definition) {
        for (var route : c.routes(definition.actor_use())) {
            var tool = c.tool(route);
            if (tool == null || !tool.available() || !definition.accepts(tool.heldItem())) continue;
            if (!tool.results().isEmpty()) return ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
            if (state.data.contains("interactionAt")
                    && c.level().getGameTime() - state.data.getLong("interactionAt") < definition.cooldown_ticks())
                continue;
            var before = c.assembly().head().specimenState();
            var plan = plan(
                    c.level(),
                    level -> {
                        var specimen = (LivingEntity)
                                BuiltInRegistries.ENTITY_TYPE.get(c.identity()).create(level);
                        CapturedSpecimen.apply(specimen, before);
                        return specimen;
                    },
                    tool.heldItem(),
                    c.assembly().renderPosition());
            if (plan == null) continue;
            var yield = c.yieldPlan();
            var drops = yield.items(definition.id(), plan.drops());
            var transaction = new ProcessTransaction()
                    .add(ProcessOutput.buffered(tool.results(), drops))
                    .add(ProcessTransaction.tool(tool, plan.held()))
                    .add(specimenStep(c.assembly().head(), before, plan.specimen()))
                    .add(yield.step());
            var old = state.data.copy();
            var updated = old.copy();
            updated.putLong("interactionAt", c.level().getGameTime());
            transaction.add(new ProcessTransaction.Step(
                    state,
                    () -> state.data.equals(old),
                    () -> {
                        state.data = updated;
                        return true;
                    },
                    () -> state.data = old));
            if (!transaction.commit()) return ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
            c.controller().toolInteracted(tool, c.head());
            return ProcessResult.committed(true);
        }
        return ProcessResult.of(ProcessResult.Reason.MISSING_TOOL);
    }

    public static ProcessTransaction.Step specimenStep(
            net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity head,
            CompoundTag before,
            CompoundTag after) {
        var saved = before.copy();
        var result = after.copy();
        var identity = head.getIdentity();
        var specimenId = head.specimenId();
        var stage = head.lifeStage();
        return new ProcessTransaction.Step(
                head,
                () -> !head.isRemoved()
                        && Objects.equals(identity, head.getIdentity())
                        && specimenId.equals(head.specimenId())
                        && stage == head.lifeStage()
                        && head.specimenState().equals(saved),
                () -> {
                    head.updateSpecimenState(result);
                    return true;
                },
                () -> head.updateSpecimenState(saved));
    }
}
