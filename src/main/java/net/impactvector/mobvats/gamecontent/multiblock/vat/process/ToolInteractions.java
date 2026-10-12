package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Definition selection and routing; independent of item categories and executor mechanics. */
public final class ToolInteractions {
    public static List<ToolInteractionDefinition> ordered(PassiveMobDefinition d) {
        return d.toolInteractions().stream().sorted(Comparator.comparingInt(ToolInteractionDefinition::priority).reversed().thenComparing(ToolInteractionDefinition::id)).toList();
    }
    public static List<String> uses(Level level,ResourceLocation identity,ItemStack stack) {
        var d=PassiveMobDefinitionRegistry.resolve(identity);
        return d==null || stack.isEmpty()?List.of():ordered(d).stream().filter(i->i.accepts(stack)
            && ToolInteractionExecutorRegistry.require(ResourceLocation.parse(i.executor())).supports(level,identity,stack))
            .map(ToolInteractionDefinition::actor_use).distinct().toList();
    }
    public static ProcessResult acquire(ProcessContext c,PassiveMobDefinition d) {
        boolean changed=false;
        for(var route:c.loading()) {
            var tool=c.tool(route);
            if(tool==null || !tool.heldItem().isEmpty() || !tool.results().isEmpty())continue;
            var strategies=new ArrayList<net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.Strategy>();
            for(var i:ordered(d)) {
                var executor=ToolInteractionExecutorRegistry.require(ResourceLocation.parse(i.executor()));
                if(i.requires_stage().accepts(c.state().stage()) && executor.readyToAcquire(c,i,route))
                    strategies.add(new net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.Strategy(i.id(),
                        net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.item(s->executor.acceptsAcquisition(c,i,s) && executor.supports(c.level(),c.identity(),s),1)));
            }
            // Older persisted ports named their single strategy "tool". Preserve that filter/order.
            if(tool.demandConfiguration().options().stream().anyMatch(o->o.id().equals("tool")))
                strategies.add(new net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.Strategy("tool",
                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle.item(s->strategies.stream().filter(p->!p.id().equals("tool")).anyMatch(p->p.inputs().items().getFirst().selector().test(s)),1)));
            changed|=ProcessTools.acquire(c,route,strategies);
        }
        return ProcessResult.committed(changed);
    }
    /** The process operation owns its persisted scheduling metadata. Failed plans leave it unchanged. */
    public static ProcessResult execute(ProcessContext c,ProcessState state,ToolInteractionDefinition definition,ToolInteractionExecutor executor) {
        var key="interactionCooldown:"+definition.id();
        boolean cooldown=executor.appliesInteractionCooldown(c);
        if(cooldown && state.data.contains(key) && c.level().getGameTime()-state.data.getLong(key)<definition.cooldown_ticks())
            return ProcessResult.of(ProcessResult.Reason.RUNNING);
        if(executor.requiresHeldInput(c,state) && c.routes(definition.actor_use()).stream().map(c::tool).filter(Objects::nonNull)
            .noneMatch(tool->definition.accepts(tool.heldItem()) && executor.supports(c.level(),c.identity(),tool.heldItem())))
            return ProcessResult.of(ProcessResult.Reason.MISSING_TOOL);
        var result=executor.execute(c,state,definition);
        if(cooldown && result.reason()==ProcessResult.Reason.COMMITTED && result.changed())state.data.putLong(key,c.level().getGameTime());
        return result;
    }
    private ToolInteractions() {}
}
