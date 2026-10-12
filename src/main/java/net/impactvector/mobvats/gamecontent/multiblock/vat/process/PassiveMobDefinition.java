package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Serializable data only. Strategy IDs select the small, explicit supported strategy set. */
public record PassiveMobDefinition(String entity, Feed feed, int juvenile_growth_ticks,
        List<SecondaryOutput> secondary_outputs, Harvest harvest,
        Slurry culture_slurry, Slurry feeding_slurry, Simulacrum simulacrum,
        java.util.Map<String,ProductBufferDefinition> buffers, List<ResourceProcess> processes, Capture capture,
        List<ToolInteractionDefinition> interactions) {
    public PassiveMobDefinition(String entity, Feed feed, int growth, List<SecondaryOutput> outputs, Harvest harvest, Slurry culture, Slurry feeding, Simulacrum simulacrum, java.util.Map<String,ProductBufferDefinition> buffers, List<ResourceProcess> processes, Capture capture) {
        this(entity,feed,growth,outputs,harvest,culture,feeding,simulacrum,buffers,processes,capture,null);
    }
    public PassiveMobDefinition(String entity, Feed feed, int growth, List<SecondaryOutput> outputs, Harvest harvest, Slurry culture, Slurry feeding, Simulacrum simulacrum, java.util.Map<String,ProductBufferDefinition> buffers, List<ResourceProcess> processes) {
        this(entity,feed,growth,outputs,harvest,culture,feeding,simulacrum,buffers,processes,null);
    }
    public record Capture(Boolean enabled, String tool, CaptureCost cost, int health_cost,
            Boolean requires_alive, Boolean allow_hostile, Boolean allow_boss, Boolean adult_only,
            Boolean encoding_enabled, String status) {
        public Capture {
            enabled=enabled==null?true:enabled;tool=tool==null?"mobvats:syringe":tool;
            requires_alive=requires_alive==null?true:requires_alive;
            allow_hostile=allow_hostile==null?true:allow_hostile;allow_boss=allow_boss==null?true:allow_boss;
            adult_only=adult_only==null?true:adult_only;encoding_enabled=encoding_enabled==null?true:encoding_enabled;
            status=status==null?"configured":status;
        }
        public static Capture legacy(){return new Capture(true,null,new CaptureCost(List.of("minecraft:glass_bottle"),1),0,true,false,false,true,true,"legacy capture policy");}
    }
    public record CaptureCost(List<String> items, int count) {
        public CaptureCost { items=items==null?List.of():List.copyOf(items); }
    }
    public PassiveMobDefinition(String entity, Feed feed, int growth, List<SecondaryOutput> outputs, Harvest harvest, Slurry culture, Slurry feeding, Simulacrum simulacrum) {
        this(entity,feed,growth,outputs,harvest,culture,feeding,simulacrum,null,null);
    }
    public PassiveMobDefinition(String entity, Feed feed, int growth, List<SecondaryOutput> outputs, Harvest harvest, Slurry culture, Slurry feeding) {
        this(entity,feed,growth,outputs,harvest,culture,feeding,null);
    }
    public record Simulacrum(List<Integer> dimensions, List<Integer> head_offset) {
        public Simulacrum {
            dimensions=dimensions==null?null:List.copyOf(dimensions);
            head_offset=head_offset==null?null:List.copyOf(head_offset);
            for(var vector:java.util.Arrays.asList(dimensions,head_offset))
                if(vector!=null && vector.size()!=3)throw new IllegalArgumentException("Geometry vectors require three integers");
            if(dimensions!=null && dimensions.stream().anyMatch(v->v<1))throw new IllegalArgumentException("Dimensions must be positive");
            if(head_offset!=null)for(int i=0;i<3;i++)
                if(head_offset.get(i)<0 || dimensions!=null && head_offset.get(i)>=dimensions.get(i))throw new IllegalArgumentException("Head offset outside dimensions");
        }
    }
    public PassiveMobDefinition {
        capture=capture==null?Capture.legacy():capture;
        secondary_outputs=secondary_outputs==null?List.of():List.copyOf(secondary_outputs);
        buffers=buffers==null?java.util.Map.of():java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(buffers));
        processes=processes==null?List.of():List.copyOf(processes);
        interactions=interactions==null?null:List.copyOf(interactions);
    }
    /** Missing field imports legacy data; an explicit empty array disables Tool interactions. */
    public List<ToolInteractionDefinition> toolInteractions() {
        if(interactions!=null)return interactions;
        var result=new java.util.ArrayList<ToolInteractionDefinition>();
        if(lifecycle() && !harvest.sword().isEmpty())result.add(new ToolInteractionDefinition("mobvats:weapon_harvest","mobvats:weapon_harvest",List.of("#minecraft:swords"),List.of("sword_sweep"),harvest.actor_use(),StageRequirement.ADULT,20,harvest.acquisition_priority(),"retain",null));
        if(lifecycle())result.add(new ToolInteractionDefinition("mobvats:shear","mobvats:regrowing_entity_item_interaction",List.of(),List.of("shears_harvest"),PassiveUses.SHEAR,StageRequirement.ADULT,20,0,"retain",null));
        for(var output:secondary_outputs)if(output.container()!=null) {
            var container=output.container();
            result.add(new ToolInteractionDefinition(output.process()+"_container","mobvats:stored_container",List.of(container.input(),container.output()),List.of(),container.actor_use(),output.requires_stage(),20,container.acquisition_priority(),"retain",output.process()));
        }
        return List.copyOf(result);
    }
    public PassiveMobDefinition withToolInteractions(List<ToolInteractionDefinition> configured) {
        return new PassiveMobDefinition(entity,feed,juvenile_growth_ticks,secondary_outputs,harvest,culture_slurry,feeding_slurry,simulacrum,buffers,processes,capture,configured);
    }
    public boolean lifecycle(){return feed!=null;}
    public List<String> tagDiagnostics(){
        var issues=new java.util.ArrayList<String>();
        issues.addAll(ResourceSelectors.missingItemTags(List.of(capture.tool())));
        if(capture.cost()!=null)issues.addAll(ResourceSelectors.missingItemTags(capture.cost().items()));
        if(feed!=null)issues.addAll(ResourceSelectors.missingItemTags(feed.items()));
        bufferDefinitions().values().forEach(b->issues.addAll(ResourceSelectors.missingFluidTags(b.fluids())));
        processes.forEach(p->p.inputs().forEach(i->{issues.addAll(ResourceSelectors.missingItemTags(i.items()));issues.addAll(ResourceSelectors.missingFluidTags(i.fluids()));}));
        toolInteractions().forEach(i->{issues.addAll(ResourceSelectors.missingItemTags(i.items()));issues.addAll(ResourceSelectors.missingBlockTags(i.block_items()));});
        return issues.stream().distinct().toList();
    }
    public java.util.Map<ResourceLocation,ProductBufferDefinition> bufferDefinitions(){
        var result=new java.util.TreeMap<ResourceLocation,ProductBufferDefinition>(java.util.Comparator.comparing(ResourceLocation::toString));
        if(lifecycle())result.putAll(ProductBuffers.builtins());
        buffers.forEach((key,value)->result.put(ResourceLocation.parse(key),value));
        for(var output:secondary_outputs)result.putIfAbsent(ProductBuffers.key(output.buffer()),new ProductBufferDefinition(List.of(output.fluid()),null,false));
        return java.util.Collections.unmodifiableMap(result);
    }
    public record ResourceProcess(String id, int duration_ticks, StageRequirement requires_stage, String actor_use,
                                  List<ResourceInput> inputs,List<ResourceOutput> outputs,boolean accelerated,Integer fouling_mb) {
        public ResourceProcess(String id,int duration,StageRequirement stage,String use,List<ResourceInput> inputs,List<ResourceOutput> outputs,boolean accelerated){this(id,duration,stage,use,inputs,outputs,accelerated,null);}
        public ResourceProcess(String id,int duration,StageRequirement stage,String use,List<ResourceInput> inputs,List<ResourceOutput> outputs){this(id,duration,stage,use,inputs,outputs,false);}
        public ResourceProcess { inputs=inputs!=null?List.copyOf(inputs):List.of();outputs=outputs==null?List.of():List.copyOf(outputs);requires_stage=requires_stage==null?StageRequirement.ANY:requires_stage;actor_use=actor_use==null?"":PassiveUses.canonical(actor_use);if(fouling_mb!=null && fouling_mb<0)throw new IllegalArgumentException("fouling_mb must be nonnegative"); }
    }
    public record ResourceInput(String type,List<String> items,List<String> fluids,int count,int amount) {}
    public record ResourceOutput(String type,String buffer,String fluid,String item,int count,int amount,boolean yield_scaled) {
        public ResourceOutput(String type,String buffer,String fluid,String item,int count,int amount){this(type,buffer,fluid,item,count,amount,false);}
    }
    public record Feed(List<String> items, int count) {
        public Feed { items=List.copyOf(items); }
        public boolean accepts(ItemStack stack) {
            return ResourceSelectors.items(items).test(stack);
        }
    }
    public record Slurry(boolean supported, int cost) {}
    public record Harvest(String sword, String blender, int blender_amount, int blender_xp,
            String actor_use, int acquisition_priority) { public Harvest { actor_use=PassiveUses.canonical(actor_use); } }
    public record Container(String input, String output, int amount, String actor_use, int acquisition_priority) { public Container { actor_use=PassiveUses.canonical(actor_use); } }
    public record SecondaryOutput(String process, String buffer, String fluid, int amount, int interval_ticks,
            StageRequirement requires_stage, String actor_use, boolean accelerated, String metric, Container container,Integer fouling_mb) {
        public SecondaryOutput(String process,String buffer,String fluid,int amount,int ticks,StageRequirement stage,String use,boolean accelerated,String metric,Container container){this(process,buffer,fluid,amount,ticks,stage,use,accelerated,metric,container,null);}
        public SecondaryOutput { actor_use=PassiveUses.canonical(actor_use);if(fouling_mb!=null && fouling_mb<0)throw new IllegalArgumentException("fouling_mb must be nonnegative"); }
    }
    public ResourceLocation identity() { return ResourceLocation.parse(entity); }
}
