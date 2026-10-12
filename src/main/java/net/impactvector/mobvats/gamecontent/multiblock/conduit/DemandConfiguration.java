package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import net.minecraft.nbt.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors;

/** Owned and persisted by a machine/operation. The router receives only the resulting ordered plan. */
public final class DemandConfiguration {
    public record Option(String id,boolean enabled,List<String> itemSelectors,List<String> fluidSelectors) {
        public Option { itemSelectors=List.copyOf(itemSelectors);fluidSelectors=List.copyOf(fluidSelectors);
            ResourceSelectors.items(itemSelectors);ResourceSelectors.fluids(fluidSelectors); }
    }
    private List<Option> options=List.of();
    private final Runnable changed;
    public DemandConfiguration(Runnable changed){this.changed=changed;}
    public List<Option> options(){return options;}
    public void configure(List<Option> value){
        if(value.stream().map(Option::id).distinct().count()!=value.size())throw new IllegalArgumentException("Duplicate strategy");
        options=List.copyOf(value);changed.run();
    }
    public List<ResourceBundle.Strategy> plan(List<ResourceBundle.Strategy> definitions){
        if(options.isEmpty())return List.copyOf(definitions);
        var result=new ArrayList<ResourceBundle.Strategy>();
        for(var option:options)if(option.enabled())for(var definition:definitions)if(option.id().equals(definition.id())){
            var itemFilter=ResourceSelectors.items(option.itemSelectors());var fluidFilter=ResourceSelectors.fluids(option.fluidSelectors());
            result.add(new ResourceBundle.Strategy(definition.id(),new ResourceBundle(
                definition.inputs().items().stream().map(d->new ResourceBundle.ItemDemand(s->d.selector().test(s)&&(option.itemSelectors().isEmpty()||itemFilter.test(s)),d.count())).toList(),
                definition.inputs().fluids().stream().map(d->new ResourceBundle.FluidDemand(s->d.selector().test(s)&&(option.fluidSelectors().isEmpty()||fluidFilter.test(s)),d.amount())).toList())));
        }
        return List.copyOf(result);
    }
    public CompoundTag save(){var tag=new CompoundTag();var list=new ListTag();for(var option:options){var entry=new CompoundTag();entry.putString("id",option.id());entry.putBoolean("enabled",option.enabled());entry.put("items",strings(option.itemSelectors()));entry.put("fluids",strings(option.fluidSelectors()));list.add(entry);}tag.put("options",list);return tag;}
    public void load(CompoundTag tag){var result=new ArrayList<Option>();for(var value:tag.getList("options",Tag.TAG_COMPOUND)){var entry=(CompoundTag)value;result.add(new Option(entry.getString("id"),entry.getBoolean("enabled"),read(entry,"items"),read(entry,"fluids")));}configure(result);}
    private static ListTag strings(List<String> values){var list=new ListTag();values.forEach(s->list.add(StringTag.valueOf(s)));return list;}
    private static List<String> read(CompoundTag tag,String key){var result=new ArrayList<String>();for(var value:tag.getList(key,Tag.TAG_STRING))result.add(value.getAsString());return result;}
}
