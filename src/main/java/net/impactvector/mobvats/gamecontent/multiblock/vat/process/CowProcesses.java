package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
/** Compatibility adapter for existing save fields and GUI snapshots. Contains no executor. */
public final class CowProcesses {
    public record Snapshot(String replacement,int replacementTicks,int milkTicks,int pendingItemCount){}
    static Snapshot snapshot(SpecimenProcessState specimen){
        if(specimen==null)return new Snapshot("IDLE",0,0,0);
        var replacement=specimen.process(PassiveProcessCompiler.HARVEST);
        return new Snapshot(specimen.stage()==LifeStage.JUVENILE?"GROWING":"IDLE",specimen.growth().elapsed,specimen.process(PassiveProcessCompiler.MILK).elapsed,replacement.pending==null?0:replacement.pending.items().stream().mapToInt(ItemStack::getCount).sum());
    }
    static void readLegacy(SpecimenProcessState specimen,CompoundTag tag,HolderLookup.Provider registries){
        var replacement=specimen.process(PassiveProcessCompiler.HARVEST);
        replacement.elapsed=0;
        specimen.stage(tag.getString("cycle").equals("GROWING")?LifeStage.JUVENILE:LifeStage.ADULT);
        timer(specimen.growth(),specimen.stage()==LifeStage.JUVENILE?proportional(tag.getInt("replacementTicks")):0,duration());
        if(specimen.stage()==LifeStage.ADULT)specimen.growth().reset();else specimen.growth().phase=ProcessState.Phase.RUNNING;
        replacement.phase=ProcessState.Phase.IDLE;
        replacement.pending=null;replacement.pendingTool=ItemStack.EMPTY;
        if((tag.getString("cycle").equals("READY") || tag.getString("cycle").equals("BLOCKED")) && tag.contains("pendingWeapon")){
            var saved=tag.copy();saved.putString("phase","READY");saved.put("pendingTool",tag.getCompound("pendingWeapon"));
            var pending=ProcessState.load(saved,registries);if(!pending.pendingTool.isEmpty()){replacement.pending=pending.pending;replacement.pendingTool=pending.pendingTool;}
        }
        timer(specimen.process(PassiveProcessCompiler.MILK),tag.getInt("milkTicks"),CowBalance.MILK_INTERVAL);
        timer(specimen.process(PassiveProcessCompiler.WASTE),tag.getInt("wasteTicks"),CowBalance.WASTE_INTERVAL);
    }
    private static void timer(ProcessState state,int elapsed,int duration){state.elapsed=Math.clamp(elapsed,0,duration);state.phase=state.elapsed>=duration?ProcessState.Phase.READY:ProcessState.Phase.RUNNING;}
    static int duration(){var definition=PassiveMobDefinitionRegistry.resolve(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"));return definition==null?24000:definition.juvenile_growth_ticks();}
    static int proportional(int old){return (int)((long)Math.clamp(old,0,1200)*duration()/1200);}
    static void migrateOldGeneric(SpecimenProcessState specimen){
        var old=specimen.process("mobvats:cow/replacement");
        if(old.phase==ProcessState.Phase.RUNNING){specimen.stage(LifeStage.JUVENILE);timer(specimen.growth(),proportional(old.elapsed),duration());specimen.growth().phase=ProcessState.Phase.RUNNING;}
        var replacement=specimen.process(PassiveProcessCompiler.HARVEST);replacement.pending=old.pending;replacement.pendingTool=old.pendingTool;
        copyTimer(specimen.process("mobvats:cow/milk"),specimen.process(PassiveProcessCompiler.MILK));
        copyTimer(specimen.process("mobvats:cow/waste"),specimen.process(PassiveProcessCompiler.WASTE));
    }
    private static void copyTimer(ProcessState from,ProcessState to){to.phase=from.phase;to.elapsed=from.elapsed;}
    private CowProcesses(){}
}
