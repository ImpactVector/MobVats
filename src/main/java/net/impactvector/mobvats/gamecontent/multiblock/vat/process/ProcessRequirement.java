package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
/** Null means eligible. Requirements never consume or mutate inputs. */
@FunctionalInterface public interface ProcessRequirement {
    ProcessResult.Reason evaluate(ProcessContext context,ProcessState state);
    static ProcessRequirement stage(StageRequirement required){return condition(c->required.accepts(c.state().stage()),ProcessResult.Reason.CONDITION_FAILED);}
    static ProcessRequirement condition(Predicate<ProcessContext> predicate,ProcessResult.Reason reason){return (c,s)->predicate.test(c)?null:reason;}
    static ProcessRequirement state(Predicate<ProcessState> predicate){return(c,s)->predicate.test(s)?null:ProcessResult.Reason.CONDITION_FAILED;}
    static ProcessRequirement phase(ProcessState.Phase expected){return(c,s)->s.phase==expected?null:switch(s.phase){case RUNNING->ProcessResult.Reason.RUNNING;case READY->ProcessResult.Reason.READY;case BLOCKED->ProcessResult.Reason.OUTPUT_BLOCKED;case IDLE->ProcessResult.Reason.IDLE;};}
    static ProcessRequirement actor(String use){return condition(c->!c.routes(use).isEmpty(),ProcessResult.Reason.MISSING_ACTOR);}
    static ProcessRequirement item(Predicate<ItemStack> predicate,int amount){if(amount<1)throw new IllegalArgumentException("amount");return condition(c->c.itemInputs().stream().anyMatch(p->predicate.test(p.staging().getStackInSlot(0)) && p.staging().getStackInSlot(0).getCount()>=amount),ProcessResult.Reason.WAITING_INPUT);}
    static ProcessRequirement actorItem(String use,Predicate<ItemStack> predicate,int amount){if(amount<1)throw new IllegalArgumentException("amount");return condition(c->c.routes(use).stream().flatMap(r->r.itemInputs().stream()).map(p->c.itemPort(p,true)).anyMatch(p->p!=null && predicate.test(p.staging().getStackInSlot(0)) && p.staging().getStackInSlot(0).getCount()>=amount),ProcessResult.Reason.WAITING_INPUT);}
    static ProcessRequirement fluid(Fluid fluid,int amount){if(amount<1)throw new IllegalArgumentException("amount");return condition(c->c.fluidAmount(fluid)>=amount,ProcessResult.Reason.WAITING_FLUID);}
    static ProcessRequirement experienceMb(long amount){if(amount<0)throw new IllegalArgumentException("amount");return condition(c->c.engine().experienceMb()>=amount,ProcessResult.Reason.WAITING_XP);}
    static ProcessRequirement tool(String use,Predicate<ItemStack> predicate){return condition(c->c.routes(use).stream().anyMatch(r->c.tool(r)!=null && predicate.test(c.tool(r).heldItem())),ProcessResult.Reason.MISSING_TOOL);}
}
