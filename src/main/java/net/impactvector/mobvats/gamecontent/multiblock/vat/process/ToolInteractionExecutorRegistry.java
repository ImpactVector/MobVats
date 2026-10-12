package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Explicit IDs only: no class loading, method names or reflective invocation. Register before definition loading. */
public final class ToolInteractionExecutorRegistry {
    private static final Map<ResourceLocation,ToolInteractionExecutor> EXECUTORS=new LinkedHashMap<>();
    static {
        register(ResourceLocation.parse("mobvats:goat_horn"),new GoatHornExecutor());
        register(ResourceLocation.parse("mobvats:sniffer_dig"),new SnifferDigExecutor());
        register(ResourceLocation.parse("mobvats:armadillo_brush"),new ArmadilloBrushExecutor());
        register(ResourceLocation.parse("mobvats:entity_item_interaction"),new EntityItemInteractionExecutor());
        register(ResourceLocation.parse("mobvats:regrowing_entity_item_interaction"),new EntityItemInteractionExecutor() {
            @Override public boolean supports(net.minecraft.world.level.Level level,ResourceLocation identity,net.minecraft.world.item.ItemStack held) {
                if(level==null || !super.supports(level,identity,held))return false;
                try {return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(identity).create(level instanceof net.minecraft.server.level.ServerLevel server?new InteractionLevel(server):level) instanceof net.neoforged.neoforge.common.IShearable;}
                catch(UnsupportedOperationException | ClassCastException unsupported) {return false;}
            }
            @Override public String stateId(ToolInteractionDefinition d) {return d.id().equals("mobvats:shear")?PassiveUses.SHEAR:d.id();}
            @Override public boolean appliesInteractionCooldown(ProcessContext c) {return !c.assembly().head().specimenState().getBoolean("Sheared");}
            @Override public boolean requiresHeldInput(ProcessContext c,ProcessState state) {return !c.assembly().head().specimenState().getBoolean("Sheared");}
            @Override public void validateDefinition(PassiveMobDefinition owner,ToolInteractionDefinition d) {
                if(!owner.lifecycle())throw new IllegalArgumentException("regrowing_entity_item_interaction requires lifecycle");
            }
            @Override public ProcessResult execute(ProcessContext c,ProcessState s,ToolInteractionDefinition d) {
                var regrowth=SecondaryToolOperations.regrow(c,s);
                return regrowth==null?super.execute(c,s,d):regrowth;
            }
        });
        register(ResourceLocation.parse("mobvats:weapon_harvest"),new ToolInteractionExecutor() {
            public String stateId(ToolInteractionDefinition d) {return PassiveProcessCompiler.HARVEST;}
            public boolean requiresHeldInput(ProcessContext c,ProcessState state) {return state.pending==null;}
            public void validateDefinition(PassiveMobDefinition owner,ToolInteractionDefinition d) {
                if(!owner.lifecycle() || !d.actor_use().equals(owner.harvest().actor_use()))throw new IllegalArgumentException("weapon_harvest requires lifecycle and matching actor_use");
            }
            public ProcessResult execute(ProcessContext c,ProcessState s,ToolInteractionDefinition d) {
                return new PassiveOperations(c,PassiveMobDefinitionRegistry.resolve(c.identity())).replace(s);
            }
            public boolean supports(net.minecraft.world.level.Level level,ResourceLocation id,net.minecraft.world.item.ItemStack held) {return VanillaEntityLoot.isWeapon(held);}
            public boolean readyToAcquire(ProcessContext c,ToolInteractionDefinition d,net.impactvector.mobvats.gamecontent.multiblock.vat.VatTopology.Assignment route) {return new PassiveOperations(c,PassiveMobDefinitionRegistry.resolve(c.identity())).hasFeed();}
        });
        register(ResourceLocation.parse("mobvats:stored_container"),new ToolInteractionExecutor() {
            public boolean acceptsAcquisition(ProcessContext c,ToolInteractionDefinition d,net.minecraft.world.item.ItemStack candidate) {
                return d.accepts(candidate) && candidate.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(source(c,d).container().input())));
            }
            public void validateDefinition(PassiveMobDefinition owner,ToolInteractionDefinition d) {
                if(owner.secondary_outputs().stream().noneMatch(o->o.container()!=null && o.process().equals(d.source_process()) && o.container().actor_use().equals(d.actor_use())))
                    throw new IllegalArgumentException("stored_container requires source_process with container and matching actor_use");
            }
            public ProcessResult execute(ProcessContext c,ProcessState s,ToolInteractionDefinition d) {
                return new PassiveOperations(c,PassiveMobDefinitionRegistry.resolve(c.identity())).container(source(c,d));
            }
            public boolean readyToAcquire(ProcessContext c,ToolInteractionDefinition d,net.impactvector.mobvats.gamecontent.multiblock.vat.VatTopology.Assignment route) {
                var output=source(c,d);var container=output.container();
                return c.state().process(output.process()).phase==ProcessState.Phase.READY ||
                    c.controller().storage().select(route.actor(),new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.get(ResourceLocation.parse(output.fluid())),container.amount()))!=null;
            }
        });
    }
    private static PassiveMobDefinition.SecondaryOutput source(ProcessContext c,ToolInteractionDefinition d) {
        return PassiveMobDefinitionRegistry.resolve(c.identity()).secondary_outputs().stream().filter(o->o.process().equals(d.source_process())).findFirst().orElseThrow();
    }
    public static synchronized void register(ResourceLocation id,ToolInteractionExecutor executor) {
        Objects.requireNonNull(id);Objects.requireNonNull(executor);
        if(EXECUTORS.putIfAbsent(id,executor)!=null)throw new IllegalArgumentException("Duplicate interaction executor: "+id);
    }
    public static synchronized ToolInteractionExecutor require(ResourceLocation id) {
        var executor=EXECUTORS.get(id);
        if(executor==null)throw new IllegalArgumentException("Unknown interaction executor: "+id);
        return executor;
    }
    private ToolInteractionExecutorRegistry() {}
}
