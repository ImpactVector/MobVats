package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.fluid.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessResult.Reason.*;
/** Last-mile consumption and reversible local output commits. */
final class PassiveOperations {
    private final ProcessContext c;private final PassiveMobDefinition d;private final MultiblockVat vat;
    PassiveOperations(ProcessContext c,PassiveMobDefinition d){this.c=c;this.d=d;vat=c.controller();}
    private record FeedPlan(ProcessTransaction.Step step,boolean slurry){}
    private FeedPlan feed(){
        if(d.feeding_slurry().supported())for(var route:c.routes(PassiveUses.FEED)){
            // Feeding consumes a configured fluid family. Preserve the selected tank's
            // components instead of asking exact-identity storage selection for a blank stack.
            var tank=vat.storage().connected(route.actor()).stream().filter(t->t.getFluid().is(ProductFluids.FEEDING_SLURRY.get()) && t.getFluidAmount()>=d.feeding_slurry().cost()).findFirst().orElse(null);
            if(tank!=null){var step=new ProcessInput.Liquid(ProductFluids.FEEDING_SLURRY.get(),d.feeding_slurry().cost()).plan(tank);if(step!=null)return new FeedPlan(step,true);}
        }
        var input=new ProcessInput.Item(d.feed()::accepts,d.feed().count());for(var source:c.itemInputs()){var step=input.plan(source.staging());if(step!=null)return new FeedPlan(step,false);}return null;
    }
    boolean hasFeed(){return feed()!=null;}
    ProcessResult acquire(){return ToolInteractions.acquire(c,d);}
    private ProcessTransaction.Step xp(long points,Object excluded){if(points==0)return null;var output=ExperienceFluid.stack(points);var tank=vat.storage().selectOutput(c.assembly().drain().getBlockPos(),output,excluded);return tank==null?null:ProcessOutput.fluid(tank,output);}
    ProcessResult replace(ProcessState state){return replace(state,false);}
    ProcessResult replaceBlender(ProcessState state){return replace(state,true);}
    private ProcessResult replace(ProcessState state,boolean onlyBlender){
        var plan=feed();if(plan==null)return ProcessResult.of(WAITING_INPUT);
        var routes=new ArrayList<>(c.routes(onlyBlender?PassiveUses.BLEND:PassiveUses.HARVEST));
        routes.sort(Comparator.<VatTopology.Assignment>comparingInt(r->((VatActor)c.actor(r)).priority()).reversed().thenComparing(VatTopology.Assignment::actor,VatTopologyCompiler.POSITION));
        if(state.pending!=null && !state.data.contains("killActor"))routes.removeIf(r->c.actor(r) instanceof VatBlenderEntity);
        if(state.pending!=null && state.data.contains("killActor")){
            var actor=BlockPos.of(state.data.getLong("killActor"));
            java.util.stream.Stream.concat(c.loading().stream(),c.routes(PassiveUses.HARVEST).stream()).filter(r->r.actor().equals(actor)).findFirst().ifPresent(r->{routes.clear();routes.add(r);});
        }
        for(var route:routes){
            boolean blender=c.actor(route) instanceof VatBlenderEntity;if(blender && d.harvest().blender().isEmpty() || !blender && d.harvest().sword().isEmpty())continue;
            var tool=c.tool(route);var transaction=new ProcessTransaction();long reward,count=0;Object reserved=null;var yield=c.yieldPlan();
            if(blender){
                var output=yield.fluid("blender",new FluidStack(ProductFluids.MEAT.get(),d.harvest().blender_amount()));
                if(!output.isEmpty()){var tank=vat.storage().selectOutput(route.actor(),output);var step=tank==null?null:ProcessOutput.fluid(tank,output);if(step==null)continue;transaction.add(step);reserved=step.resource();}
                transaction.add(yield.step());reward=yield.amount("blender/xp",d.harvest().blender_xp());count=output.getAmount();
            }else{
                if(tool==null)return ProcessResult.of(MISSING_TOOL);if(!tool.results().isEmpty())continue;
                if(state.pending==null){
                    if(!tool.available() || !c.engine().toolTurn(tool,PassiveUses.HARVEST,c) || !VanillaEntityLoot.isWeapon(tool.heldItem()))continue;
                    var weapon=tool.heldItem();float health=state.data.contains("killHealth")?state.data.getFloat("killHealth"):VanillaEntityLoot.health(c.level(),c.identity(),c.assembly().head().specimenState());
                    float remaining=Math.max(0,health-VanillaEntityLoot.damage(c.level(),c.identity(),weapon,c.assembly().head().specimenState()));
                    var damaged=VanillaEntityLoot.damageWeapon(c.level(),c.identity(),weapon);var before=state.data.copy();var after=before.copy();after.putFloat("killHealth",remaining);after.putLong("killActor",route.actor().asLong());
                    if(!new ProcessTransaction().add(ProcessTransaction.tool(tool,damaged)).add(new ProcessTransaction.Step(state,()->state.data.equals(before),()->{state.data=after;return true;},()->state.data=before)).commit())return ProcessResult.of(MISSING_TOOL);
                    vat.toolInteracted(tool,c.head());c.metrics().record(MachineMetrics.DURABILITY,Math.max(0,(damaged.isEmpty()?weapon.getMaxDamage():damaged.getDamageValue())-weapon.getDamageValue()));
                    if(remaining>0)return ProcessResult.committed(true);
                    var rolled=VanillaEntityLoot.roll(c.level(),c.identity(),c.assembly().renderPosition(),weapon,c.level().random.nextLong(),c.assembly().head().specimenState());state.pending=new ProcessState.PreparedOutput(yield.items("kill",rolled.items()),yield.amount("kill/xp",rolled.xp()));new ProcessTransaction().add(yield.step()).commit();state.pendingTool=weapon.copy();state.phase=ProcessState.Phase.READY;
                    vat.processStateChanged(true);
                }
                if(state.data.contains("killActor") && state.data.getLong("killActor")!=route.actor().asLong())continue;
                if(!state.data.contains("killActor")){
                    // Old saves prepared loot before charging durability. Freeze the responsible Tool once.
                    if(!VanillaEntityLoot.isWeapon(tool.heldItem()))continue;
                    var weapon=tool.heldItem();var damaged=VanillaEntityLoot.damageWeapon(c.level(),c.identity(),weapon);
                    tool.setHeldItem(damaged);state.data.putLong("killActor",route.actor().asLong());state.data.putFloat("killHealth",0);
                    c.metrics().record(MachineMetrics.DURABILITY,Math.max(0,(damaged.isEmpty()?weapon.getMaxDamage():damaged.getDamageValue())-weapon.getDamageValue()));
                    vat.toolInteracted(tool,c.head());
                    vat.processStateChanged(true);
                }
                transaction.add(ProcessOutput.buffered(tool.results(),state.pending.items()));reward=state.pending.xp();count=state.pending.items().stream().mapToLong(ItemStack::getCount).sum();
            }
            var xp=xp(reward,reserved);if(reward>0 && xp==null)return ProcessResult.of(OUTPUT_BLOCKED);if(xp!=null)transaction.add(xp);
            if(!vat.isAssembled() || !c.operational())return ProcessResult.of(MISSING_ACTOR);
            if(!transaction.add(plan.step()).add(ProcessTransaction.lifecycle(c.state(),LifeStage.JUVENILE,0)).commit())return ProcessResult.of(OUTPUT_BLOCKED);
            if(plan.slurry()){vat.usedSlurryFeed();c.metrics().record("feedingConsumed",d.feeding_slurry().cost());}else{vat.usedItemFeed();c.metrics().record(MachineMetrics.WHEAT,d.feed().count());}
            c.state().feedSource(plan.slurry()?"feeding slurry":"item feed");c.metrics().record(MachineMetrics.XP,reward);
            if(blender){c.metrics().record(MachineMetrics.MEAT,count);c.metrics().record(MachineMetrics.BLENDER_HARVESTS,1);}else{c.metrics().record(MachineMetrics.HARVEST_ITEMS,count);c.metrics().record(MachineMetrics.HARVESTS,1);}
            state.reset();state.data.remove("killHealth");state.data.remove("killActor");return ProcessResult.committed(true);
        }return ProcessResult.of(MISSING_ACTOR);
    }
    private static Item item(String id){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));}
    ProcessResult regrow(ProcessState state,net.minecraft.nbt.CompoundTag after){var plan=feed();if(plan==null)return ProcessResult.of(WAITING_INPUT);var head=c.assembly().head();var before=head.specimenState();var change=new ProcessTransaction.Step(head,()->head.specimenState().equals(before),()->{head.updateSpecimenState(after);return true;},()->head.updateSpecimenState(before));if(!Fouling.commit(new ProcessTransaction().add(plan.step()).add(change).add(ProcessTransaction.state(state,ProcessState.Phase.IDLE,0)),c,"mobvats:shear_regrowth"))return ProcessResult.of(OUTPUT_BLOCKED);c.metrics().record(plan.slurry()?"feedingConsumed":MachineMetrics.WHEAT,plan.slurry()?d.feeding_slurry().cost():d.feed().count());c.state().feedSource(plan.slurry()?"feeding slurry":"item feed");return ProcessResult.committed(true);}
    ProcessResult container(PassiveMobDefinition.SecondaryOutput output){boolean changed=false;var container=output.container();
        for(var route:c.routes(container.actor_use())){
            var tool=c.tool(route);if(tool==null || !tool.available() || !tool.results().isEmpty())continue;
            if(tool.heldItem().is(item(container.output()))){changed|=routeContainer(tool);continue;}
            if(!tool.heldItem().is(item(container.input())))continue;
            var fluid=BuiltInRegistries.FLUID.get(ResourceLocation.parse(output.fluid()));var tank=vat.storage().select(route.actor(),new FluidStack(fluid,container.amount()));
            var input=tank==null?null:new ProcessInput.Liquid(fluid,container.amount()).plan(tank);
            if(input!=null && new ProcessTransaction().add(input).add(ProcessTransaction.tool(tool,new ItemStack(item(container.output())))).commit()){
                vat.toolInteracted(tool,c.head());routeContainer(tool);changed=true;
            }
        }return ProcessResult.committed(changed);
    }
    private boolean routeContainer(VatToolPortEntity tool){return new ProcessTransaction().add(ProcessOutput.buffered(tool.results(),List.of(tool.heldItem()))).add(ProcessTransaction.tool(tool,ItemStack.EMPTY)).commit();}
}
