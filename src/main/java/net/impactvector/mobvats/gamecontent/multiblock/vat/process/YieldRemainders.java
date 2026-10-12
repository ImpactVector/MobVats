package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Per-specimen/resource carry. Plans are pure until their output transaction commits. */
public final class YieldRemainders {
    private final Map<String,Double> values=new TreeMap<>();
    public CompoundTag save(){var tag=new CompoundTag();values.forEach(tag::putDouble);return tag;}
    public void load(CompoundTag tag){values.clear();for(var key:tag.getAllKeys()){double value=tag.getDouble(key);if(Double.isFinite(value) && value>=0 && value<1)values.put(key,value);}}
    public Batch plan(double multiplier,HolderLookup.Provider registries){return new Batch(multiplier,registries);}
    public final class Batch {
        private final Map<String,Double> before=new TreeMap<>(values),after=new TreeMap<>(values);
        private final double multiplier;private final HolderLookup.Provider registries;
        private Batch(double multiplier,HolderLookup.Provider registries){if(!Double.isFinite(multiplier) || multiplier<0)throw new IllegalArgumentException("Yield multiplier");this.multiplier=multiplier;this.registries=registries;}
        public int amount(String stream,int base){
            if(base<0)throw new IllegalArgumentException("Negative base output");
            double scaled=base*multiplier+after.getOrDefault(stream,0.0);int emitted=(int)Math.floor(scaled+1e-10);
            if(scaled>Integer.MAX_VALUE)throw new IllegalArgumentException("Yield overflow");
            after.put(stream,Math.max(0,scaled-emitted));return emitted;
        }
        public List<ItemStack> items(String stream,List<ItemStack> nativeDrops){
            var result=new ArrayList<ItemStack>();
            for(var item:nativeDrops){if(item.isEmpty())continue;String key=stream+"/item/"+item.copyWithCount(1).save(registries);
                int amount=amount(key,item.getCount());while(amount>0){int n=Math.min(amount,item.getMaxStackSize());result.add(item.copyWithCount(n));amount-=n;}
            }return List.copyOf(result);
        }
        public FluidStack fluid(String stream,FluidStack output){return output.copyWithAmount(amount(stream+"/fluid/"+output.copyWithAmount(1).save(registries),output.getAmount()));}
        public ProcessTransaction.Step step(){return new ProcessTransaction.Step(values,()->values.equals(before),()->{values.clear();values.putAll(after);return true;},()->{values.clear();values.putAll(before);});}
    }
}
