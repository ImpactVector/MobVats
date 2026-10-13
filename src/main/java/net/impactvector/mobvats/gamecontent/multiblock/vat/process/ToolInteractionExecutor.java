package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Public code adapter. Planning must not mutate live resources; commit/undo are executor-owned. */
@FunctionalInterface
public interface ToolInteractionExecutor {
    ProcessResult execute(ProcessContext context, ProcessState state, ToolInteractionDefinition definition);

    default String stateId(ToolInteractionDefinition definition) {
        return definition.id();
    }

    default boolean appliesInteractionCooldown(ProcessContext context) {
        return true;
    }

    default boolean requiresHeldInput(ProcessContext context, ProcessState state) {
        return true;
    }

    default void validateDefinition(PassiveMobDefinition owner, ToolInteractionDefinition definition) {}

    default boolean supports(Level level, ResourceLocation specimen, ItemStack held) {
        return true;
    }

    default boolean acceptsAcquisition(
            ProcessContext context, ToolInteractionDefinition definition, ItemStack candidate) {
        return definition.accepts(candidate);
    }

    default boolean readyToAcquire(
            ProcessContext context,
            ToolInteractionDefinition definition,
            net.impactvector.mobvats.gamecontent.multiblock.vat.VatTopology.Assignment route) {
        return true;
    }
}
