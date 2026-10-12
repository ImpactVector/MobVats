package net.impactvector.mobvats.gamecontent.multiblock.common;

import java.util.*;
import java.util.stream.Stream;
import net.impactvector.mobvats.gamecontent.fluid.compound.CompoundBucketItem;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

/** Read-only, local presentation snapshot. No integration APIs, route discovery or process execution. */
public final class MachineInspection {
    private static final int LIMIT=6;
    public static List<Component> lines(Level level, BlockPos pos) {
        var out=new ArrayList<Component>();
        var entity=level.getBlockEntity(pos);
        if(level.getBlockState(pos).getBlock() instanceof ConduitBlock block) {
            out.add(Component.literal(level.getBlockState(pos).getValue(ConduitBlock.MODE).name()));
            line(out,"Shell",block.shell(level.getBlockState(pos)).name());
            var controller=ConduitStructure.containing(level,pos);
            var topology=controller instanceof MultiblockVat vat?vat.getTopology().conduits():
                controller instanceof MultiblockMixer mixer?mixer.conduitTopology():ConduitTopology.EMPTY;
            topology.componentAt(pos).ifPresent(component->{
                line(out,"Producers",component.with(ConduitTopology.Role.PRODUCER).size());
                line(out,"Stores",component.with(ConduitTopology.Role.STORE).size());
                line(out,"Consumers",component.with(ConduitTopology.Role.CONSUMER).size());
                var positions=component.positions();
                if(!positions.isEmpty())out.add(Component.literal("Connected:"));
                positions.stream().limit(LIMIT).forEach(p->out.add(level.getBlockState(p).getBlock().getName().copy().append(" @ "+p.toShortString())));
                more(out,positions.size());
            });
            if(controller==null)line(out,"Status","No formed machine component");
        } else if(entity instanceof FluidBufferEntity buffer) {
            line(out,"Shell",((FluidBufferBlock)buffer.getBlockState().getBlock()).shell(buffer.getBlockState()).name());
            filter(out,buffer.filter());
            tanks(out,List.of(buffer.tank));
        } else if(entity instanceof VatControllerEntity part) {
            var vat=part.getMultiblockController().orElse(null);
            line(out,"Vat",vat!=null && vat.isAssembled()?"Formed":"Unformed");
            if(vat!=null) {
                if(vat.isAssembled())line(out,"Specimens",vat.getAssemblies().size());
                else vat.getLastError().ifPresent(e->out.add(Component.literal("Reason: ").append(e.getChatMessage())));
            }
        } else if(entity instanceof MixerControllerEntity part) {
            var mixer=part.getMultiblockController().orElse(null);
            line(out,"Mixer",mixer!=null && mixer.isAssembled()?"Formed":"Unformed");
            if(mixer!=null) {
                if(!mixer.isAssembled())mixer.getLastError().ifPresent(e->out.add(Component.literal("Reason: ").append(e.getChatMessage())));
                else {
                    line(out,"Slots",mixer.slotCount());line(out,"Drive",mixer.operatingState());
                    var state=mixer.state();line(out,"Recipe",state.recipe().isEmpty()?"none":state.recipe());
                    var recipe=MixerRecipeRegistry.recipe(state.recipe());
                    if(recipe!=null)line(out,"Progress",state.progress()+" / "+recipe.batchTicks());
                    if(!state.blocked().isBlank())line(out,"Status",state.blocked());
                }
            }
        } else if(entity instanceof AbstractMixerDriveEntity drive) {
            if(drive instanceof MixerCombustionDriveEntity combustion) {
                var fuel=combustion.burningFuel();
                if(fuel.isEmpty())fuel=combustion.fuel().getStackInSlot(0);
                out.add(Component.literal("Fuel: ").append(fuel.isEmpty()?Component.literal(combustion.burnTicks()>0?"Unknown (legacy save)":"none"):fuel.getHoverName()));
                line(out,"Burn",combustion.burnTicks()+" / "+combustion.burnDuration());
                items(out,combustion.fuel());
            } else if(drive instanceof MixerFeDriveEntity fe) {
                out.add(Component.literal(fe.energy().getEnergyStored()+" / "+fe.energy().getMaxEnergyStored()+" FE"));
            }
            line(out,"Mixer",drive.getMultiblockController().map(MultiblockMixer::operatingState).map(Enum::name).orElse("Unformed"));
        } else if(entity instanceof VatToolPortEntity tool) {
            actor(out,tool);
            out.add(Component.literal("Tool/Input: ").append(tool.heldItem().isEmpty()?Component.literal("none"):tool.heldItem().getHoverName()));
            items(out,tool.results().resources());tanks(out,tool.service().tanks());
        } else if(entity instanceof ConfigurableEndpoint port) {
            line(out,"Direction",port.endpoint().direction().isInput()?"Input":"Output");line(out,"Mode",port.getIoMode().name());filter(out,port.endpoint().filter());
            // Only locally retained resources; connected machine storage belongs on its buffers.
            if(entity instanceof VatItemPortEntity item)items(out,item.staging());
            if(entity instanceof MixerPortEntity mixer && port.endpoint().resource()==EndpointConfiguration.Resource.ITEM) {
                out.add(Component.literal("Available through port:"));items(out,mixer.items());
            }
            if(entity instanceof AbstractVatEntity vat)tanks(out,vat.service().tanks());
            if(entity instanceof AbstractMixerEntity mixer)tanks(out,mixer.service().tanks());
            if(entity instanceof VatActor && entity instanceof AbstractVatEntity vat)actor(out,vat);
        } else if(entity instanceof AbstractVatEntity vat && entity instanceof VatActor) {
            actor(out,vat);tanks(out,vat.service().tanks());
        } else if(entity instanceof MixerPumpEntity boundary) {
            filter(out,boundary.filter());line(out,"Status",boundary.status());
        } else if(entity instanceof MixerFluidVoidEntity boundary) {
            filter(out,boundary.filter());line(out,"Status",boundary.status());line(out,"Disposed",boundary.disposedMb()+" mB");
        } else if(entity instanceof VatBoundaryEntity boundary) {
            filter(out,boundary.filter());line(out,"Status",boundary.status());tanks(out,boundary.service().tanks());
            if(boundary.getPartType()==VatPartType.FillValve && !boundary.fillDetails().isEmpty())
                boundary.fillDetails().lines().forEach(detail->out.add(Component.literal(detail)));
        }
        return List.copyOf(out);
    }

    private static void actor(List<Component> out,AbstractVatEntity actor) {
        var vat=actor.getMultiblockController().filter(MultiblockVat::isAssembled).orElse(null);
        var routes=vat==null?List.<VatTopology.Assignment>of():Stream.concat(vat.getTopology().actors().stream(),vat.getTopology().toolLoading().stream())
            .filter(a->a.actor().equals(actor.getBlockPos())).toList();
        var selected=routes.stream().filter(a->!(actor instanceof VatToolPortEntity tool) || tool.interactionHead()==null || a.head().equals(tool.interactionHead())).findFirst().orElse(routes.isEmpty()?null:routes.getFirst());
        if(selected==null) {
            line(out,"Target","none");
            String reason=vat==null?"vat is not formed":vat.getTopology().physical().blocked().stream().filter(b->b.part().equals(actor.getBlockPos())).map(b->b.description()).findFirst().orElse("no available/valid specimen");
            if(actor instanceof VatToolPortEntity tool)reason=tool.assignmentStatus();
            line(out,"Status","Unassigned - "+reason);return;
        }
        if(!(actor.getLevel().getBlockEntity(selected.head()) instanceof VatHeadEntity head)) {
            line(out,"Target","none");line(out,"Status","Unassigned - specimen unavailable");return;
        }
        out.add(Component.literal("Target: ").append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(head.getIdentity()).getDescription()).append(" @ "+selected.head().toShortString()));
        if(actor instanceof VatToolPortEntity tool) {
            var definition=PassiveMobDefinitionRegistry.resolve(head.getIdentity());
            var interaction=definition==null?null:ToolInteractions.ordered(definition).stream()
                .filter(i->routes.stream().anyMatch(a->a.head().equals(selected.head()) && a.use().equals(i.actor_use())))
                .filter(i->i.accepts(tool.heldItem()) && ToolInteractionExecutorRegistry.require(net.minecraft.resources.ResourceLocation.parse(i.executor())).supports(actor.getLevel(),head.getIdentity(),tool.heldItem()))
                .findFirst().orElse(null);
            line(out,"Operation",interaction==null?"none":interaction.id());
            if(interaction==null)line(out,"Status",tool.heldItem().isEmpty()?"Missing input":"No matching operation");
            else {
                var result=vat.processEngine().interactionStatus(selected.head(),interaction.id());
                line(out,"Status",!tool.available()?"Cooldown":result.map(r->r.reason().name()).orElse("Waiting for evaluation"));
            }
        } else {
            line(out,"Operation",selected.use());
            var status=vat.processEngine().actorStatus(selected.head(),selected.use());
            if(status.isEmpty())line(out,"Status","Waiting for evaluation");
            status.entrySet().stream().limit(LIMIT).forEach(e->line(out,"Status",e.getKey()+": "+e.getValue().reason()));more(out,status.size());
        }
    }
    private static void items(List<Component> out,IItemHandler inventory) {
        if(inventory==null)return;
        var contents=new ArrayList<ItemStack>();long capacity=0;
        for(int i=0;i<inventory.getSlots();i++){capacity+=inventory.getSlotLimit(i);var stack=inventory.getStackInSlot(i);if(!stack.isEmpty())contents.add(stack.copy());}
        line(out,"Capacity",capacity+" items / "+inventory.getSlots()+" slots");
        out.add(Component.literal(contents.isEmpty()?"Contents: empty":"Contents:"));
        contents.stream().limit(LIMIT).forEach(s->out.add(s.getHoverName().copy().append(" x"+s.getCount())));more(out,contents.size());
    }
    private static void tanks(List<Component> out,List<FluidTank> tanks) {
        tanks.stream().limit(LIMIT).forEach(t->{
            var fluid=t.getFluid();out.add(fluid.isEmpty()?Component.literal("Empty"):fluid.getHoverName());
            out.add(Component.literal(t.getFluidAmount()+" / "+t.getCapacity()+" mB"));
            if(!fluid.isEmpty()) {
                var composition=new ArrayList<Component>();CompoundBucketItem.appendComposition(fluid,composition);
                out.addAll(composition.stream().limit(LIMIT+1).toList());more(out,Math.max(0,composition.size()-1));
            }
        });more(out,tanks.size());
    }
    private static void filter(List<Component> out,String filter){line(out,"Filter",filter.isEmpty()?"Any":filter);}
    private static void line(List<Component> out,String label,Object value){out.add(Component.literal(label+": "+value));}
    private static void more(List<Component> out,int size){if(size>LIMIT)out.add(Component.literal("+"+(size-LIMIT)+" more"));}
    private MachineInspection(){}
}
