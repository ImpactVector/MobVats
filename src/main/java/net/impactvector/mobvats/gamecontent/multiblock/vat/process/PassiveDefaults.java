package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.SlurryBalance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;

/** Registry-derived catalog; these centralized exceptions describe biology, never scheduler behavior. */
public final class PassiveDefaults {
    private static final Set<String> MISC_MOBS=Set.of("iron_golem","snow_golem","villager");
    private static final Set<String> BOSSES=Set.of("wither","ender_dragon","elder_guardian","warden");
    private static final Map<String,String> FOODS=foods();
    private static Map<String,String> foods(){
        var result=new TreeMap<String,String>();
        for(var name:List.of("cow","sheep","pig","chicken","rabbit","goat","horse","camel","llama","wolf","cat","ocelot","fox","armadillo","strider","sniffer"))result.put(name,"#minecraft:"+name+"_food");
        result.put("mooshroom","#minecraft:cow_food");
        result.put("donkey","#minecraft:horse_food");result.put("trader_llama","#minecraft:llama_food");
        return Map.copyOf(result);
    }
    public static boolean targeted(EntityType<?> type){
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id.getNamespace().equals("minecraft") && (type.getCategory()!=MobCategory.MISC || MISC_MOBS.contains(id.getPath()));
    }
    public static Map<String,PassiveMobDefinition> data() {
        var result=new TreeMap<String,PassiveMobDefinition>();
        for(var type:BuiltInRegistries.ENTITY_TYPE)if(targeted(type)){
            var id=BuiltInRegistries.ENTITY_TYPE.getKey(type);var name=id.getPath();
            boolean boss=BOSSES.contains(name),hostile=type.getCategory()==MobCategory.MONSTER;
            // Undead horses have no breeding lifecycle and remain progression gated.
            boolean undead=Set.of("skeleton_horse","zombie_horse").contains(name);
            var food=FOODS.get(name);boolean lifecycle=food!=null && !hostile && !boss;
            var outputs=new ArrayList<PassiveMobDefinition.SecondaryOutput>();
            if(Set.of("cow","goat","mooshroom").contains(name))outputs.add(milk());
            if(lifecycle)outputs.add(waste());
            var processes=new ArrayList<PassiveMobDefinition.ResourceProcess>();
            if(name.equals("chicken"))processes.add(new PassiveMobDefinition.ResourceProcess("mobvats:egg",9000,StageRequirement.ADULT,"",List.of(),
                List.of(new PassiveMobDefinition.ResourceOutput("item",null,null,"minecraft:egg",1,0,true)),true));
            var size=type.getDimensions();
            // Validate procedural geometry from registry metadata; null override keeps live capture dimensions authoritative.
            net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.resolve(size.width(),size.height(),size.width(),net.minecraft.core.Direction.NORTH,null);
            var capture=new PassiveMobDefinition.Capture(!boss && !hostile && !undead,"mobvats:syringe",
                new PassiveMobDefinition.CaptureCost(List.of("minecraft:glass_bottle"),1),0,true,true,!boss,true,true,
                boss?"boss progression unavailable":hostile || undead?"hostile progression unavailable":lifecycle?"passive lifecycle; special interactions not implemented":"representation only; special reproduction/interactions not implemented");
            var definition=new PassiveMobDefinition(id.toString(),lifecycle?new PassiveMobDefinition.Feed(name.equals("cow")?List.of(food,"minecraft:wheat"):List.of(food),1):null,
                lifecycle?(name.equals("sniffer")?48000:24000):0,outputs,
                lifecycle?new PassiveMobDefinition.Harvest("mobvats:vanilla_entity_death_loot","mobvats:meat",1000,0,PassiveUses.HARVEST,10):null,
                null,
                lifecycle?new PassiveMobDefinition.Slurry(true,SlurryBalance.FEEDING_COST):null,new PassiveMobDefinition.Simulacrum(null,null),lifecycle?buffers():Map.of(),processes,capture);
            var interactions=new ArrayList<>(definition.toolInteractions());
            if(name.equals("goat"))interactions.add(environment("goat_horn",List.of("#minecraft:snaps_goat_horn"),List.of(),40,6000));
            if(name.equals("sniffer"))interactions.add(environment("sniffer_dig",List.of("#minecraft:sniffer_diggable_block"),List.of(),120,9600));
            if(name.equals("armadillo"))interactions.add(environment("armadillo_brush",List.of(),List.of("brush_brush"),20,20));
            result.put(id.toString(),definition.withToolInteractions(interactions));
        }
        return Collections.unmodifiableMap(result);
    }
    private static PassiveMobDefinition.SecondaryOutput milk(){return new PassiveMobDefinition.SecondaryOutput("mobvats:milk","mobvats:milk","minecraft:milk",1000,1200,
        StageRequirement.ADULT,"",true,MachineMetrics.MILK,new PassiveMobDefinition.Container("minecraft:bucket","minecraft:milk_bucket",1000,PassiveUses.SECONDARY,20));}
    private static PassiveMobDefinition.SecondaryOutput waste(){return new PassiveMobDefinition.SecondaryOutput("mobvats:waste","mobvats:waste","mobvats:waste",250,1200,
        StageRequirement.ANY,PassiveUses.DRAIN,false,MachineMetrics.WASTE,null);}
    private static Map<String,ProductBufferDefinition> buffers(){var result=new TreeMap<String,ProductBufferDefinition>();ProductBuffers.builtins().forEach((id,d)->result.put(id.toString(),d));return result;}
    private static ToolInteractionDefinition environment(String name,List<String> blocks,List<String> abilities,int duration,int cooldown) {
        return new ToolInteractionDefinition("mobvats:"+name,"mobvats:"+name,List.of(),abilities,"mobvats:"+name,
            StageRequirement.ADULT,cooldown,30,"retain",null,duration,blocks);
    }
    private PassiveDefaults() {}
}
