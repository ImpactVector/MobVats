package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;

/** Authored data. Executors own item consumption, replacement and damage semantics. */
public record ToolInteractionDefinition(String id, String executor, List<String> items, List<String> abilities,
        String actor_use, StageRequirement requires_stage, int cooldown_ticks, int priority,
        String output_policy, String source_process, int duration_ticks, List<String> block_items) {
    public ToolInteractionDefinition(String id,String executor,List<String> items,List<String> abilities,String actor_use,
            StageRequirement requires_stage,int cooldown_ticks,int priority,String output_policy,String source_process) {
        this(id,executor,items,abilities,actor_use,requires_stage,cooldown_ticks,priority,output_policy,source_process,0,List.of());
    }
    public ToolInteractionDefinition {
        items=items==null?List.of():List.copyOf(items);
        abilities=abilities==null?List.of():List.copyOf(abilities);
        block_items=block_items==null?List.of():List.copyOf(block_items);
        actor_use=actor_use==null?id:PassiveUses.canonical(actor_use);
        requires_stage=requires_stage==null?StageRequirement.ANY:requires_stage;
        output_policy=output_policy==null?"retain":output_policy;
    }
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (!items.isEmpty() && ResourceSelectors.items(items).test(stack)
            || !block_items.isEmpty() && ResourceSelectors.blockItems(block_items).test(stack)
            || !abilities.isEmpty() && abilities.stream().allMatch(a->stack.canPerformAction(net.neoforged.neoforge.common.ItemAbility.get(a))));
    }
    public void validate() {
        if(ResourceLocation.tryParse(id)==null || ResourceLocation.tryParse(executor)==null)
            throw new IllegalArgumentException("Invalid interaction/executor ID: "+id+" / "+executor);
        ToolInteractionExecutorRegistry.require(ResourceLocation.parse(executor));
        if(items.isEmpty() && abilities.isEmpty() && block_items.isEmpty())throw new IllegalArgumentException("Interaction requires an item selector or ability: "+id);
        ResourceSelectors.items(items);
        ResourceSelectors.blockItems(block_items);
        if(actor_use.isEmpty() || cooldown_ticks<0 || duration_ticks<0 || !output_policy.equals("retain"))
            throw new IllegalArgumentException("Invalid interaction use, cooldown or output_policy: "+id);
    }
}
