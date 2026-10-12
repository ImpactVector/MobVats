package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
/** Vanilla/NeoForge operation adapter; no species-specific output table. */
public final class SecondaryToolOperations {
    public record Regrowth(int ticks,java.util.function.Consumer<net.minecraft.world.entity.Mob> operation){}
    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>,Regrowth> REGROWTH=new java.util.HashMap<>();
    static {registerRegrowth(net.minecraft.world.entity.EntityType.SHEEP,new Regrowth(1200,m->((net.minecraft.world.entity.animal.Sheep)m).ate()));}
    public static void registerRegrowth(net.minecraft.world.entity.EntityType<?> type,Regrowth strategy){REGROWTH.put(type,strategy);}
    /** Compatibility entry point for in-code callers; selection remains definition-owned. */
    public static ProcessResult shear(ProcessContext c,ProcessState state) {
        var definition=PassiveMobDefinitionRegistry.resolve(c.identity());
        var interaction=definition.toolInteractions().stream().filter(i->i.actor_use().equals(PassiveUses.SHEAR)).findFirst().orElse(null);
        return interaction==null?ProcessResult.of(ProcessResult.Reason.MISSING_TOOL):ToolInteractionExecutorRegistry.require(net.minecraft.resources.ResourceLocation.parse(interaction.executor())).execute(c,state,interaction);
    }
    public static ProcessResult regrow(ProcessContext c,ProcessState state){
        var definition=PassiveMobDefinitionRegistry.resolve(c.identity());
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(c.identity());var regrowth=REGROWTH.get(type);
        if(regrowth!=null && definition!=null && definition.lifecycle() && c.assembly().head().specimenState().getBoolean("Sheared")){
            if(state.elapsed<regrowth.ticks()){state.elapsed=Math.min(regrowth.ticks(),state.elapsed+state.advance(VatProcessEngine.CADENCE*c.multiplier()));return ProcessResult.committed(true);}
            var entity=VanillaEntityLoot.specimen(c.level(),c.identity(),c.assembly().head().specimenState());regrowth.operation().accept(entity);return new PassiveOperations(c,definition).regrow(state,CapturedSpecimen.capture(entity).state());
        }
        return null;
    }
    private SecondaryToolOperations(){}
}
