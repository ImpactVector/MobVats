package net.impactvector.mobvats.gamecontent.multiblock.common;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Parsed exact/tag selectors. Tag membership is queried from current holders, including after reload. */
public final class ResourceSelectors {
    public record ItemSelector(Item exact, TagKey<Item> tag) implements Predicate<ItemStack> {
        public boolean test(ItemStack stack) { return !stack.isEmpty() && (tag==null?stack.is(exact):stack.is(tag)); }
    }
    public record FluidSelector(Fluid exact, TagKey<Fluid> tag) implements Predicate<FluidStack> {
        public boolean test(FluidStack stack) { return !stack.isEmpty() && (tag==null?stack.is(exact):stack.is(tag)); }
    }
    private static net.minecraft.core.HolderLookup.Provider registryAccess=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    public static com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops(){return net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,registryAccess);}
    public static void registryAccess(net.minecraft.core.HolderLookup.Provider provider){registryAccess=provider;ITEMS.clear();BLOCK_ITEMS.clear();FLUIDS.clear();}
    private static final Map<List<String>,Predicate<ItemStack>> ITEMS=new ConcurrentHashMap<>();
    private static final Map<List<String>,Predicate<ItemStack>> BLOCK_ITEMS=new ConcurrentHashMap<>();
    private static final Map<List<String>,Predicate<FluidStack>> FLUIDS=new ConcurrentHashMap<>();
    private static ResourceLocation id(String text) {
        var id=ResourceLocation.tryParse(text);
        if(id==null)throw new IllegalArgumentException("Invalid selector ID: "+text);
        return id;
    }
    public static Predicate<ItemStack> items(List<String> values) {
        return ITEMS.computeIfAbsent(List.copyOf(values),key->{
            var selectors=key.stream().map(s->{
                if(s.startsWith("#"))return new ItemSelector(null,TagKey.create(Registries.ITEM,id(s.substring(1))));
                var location=id(s);if(!BuiltInRegistries.ITEM.containsKey(location))throw new IllegalArgumentException("Unknown item: "+s);
                return new ItemSelector(BuiltInRegistries.ITEM.get(location),null);
            }).toList();
            return stack->selectors.stream().anyMatch(s->s.test(stack));
        });
    }
    public static Predicate<FluidStack> fluids(List<String> values) {
        return FLUIDS.computeIfAbsent(List.copyOf(values),key->{
            var selectors=key.stream().map(s->{
                if(s.startsWith("=")) {
                    var sample=FluidStack.CODEC.parse(ops(),com.google.gson.JsonParser.parseString(s.substring(1))).getOrThrow();
                    return (Predicate<FluidStack>)(stack->!stack.isEmpty()&&FluidStack.isSameFluidSameComponents(sample,stack));
                }
                if(s.startsWith("#"))return new FluidSelector(null,TagKey.create(Registries.FLUID,id(s.substring(1))));
                var location=id(s);if(!BuiltInRegistries.FLUID.containsKey(location))throw new IllegalArgumentException("Unknown fluid: "+s);
                return new FluidSelector(BuiltInRegistries.FLUID.get(location),null);
            }).toList();
            return stack->selectors.stream().anyMatch(s->s.test(stack));
        });
    }
    /** Block selectors are evaluated on an acquired BlockItem, never on the live world. */
    public static Predicate<ItemStack> blockItems(List<String> values) {
        return BLOCK_ITEMS.computeIfAbsent(List.copyOf(values),key->{
            var selectors=key.stream().map(s->{
                if(s.startsWith("#")) {
                    var tag=TagKey.create(Registries.BLOCK,id(s.substring(1)));
                    return (Predicate<net.minecraft.world.level.block.Block>)(block->block.defaultBlockState().is(tag));
                }
                var location=id(s);if(!BuiltInRegistries.BLOCK.containsKey(location))throw new IllegalArgumentException("Unknown block: "+s);
                var exact=BuiltInRegistries.BLOCK.get(location);
                return (Predicate<net.minecraft.world.level.block.Block>)(block->block==exact);
            }).toList();
            return stack->!stack.isEmpty() && stack.getItem() instanceof BlockItem item && selectors.stream().anyMatch(s->s.test(item.getBlock()));
        });
    }
    public static List<String> missingItemTags(List<String> values){
        if(values==null)return List.of();
        return values.stream().filter(s->s.startsWith("#") && BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,id(s.substring(1)))).filter(t->t.size()>0).isEmpty()).map(s->"Unknown or empty item tag: "+s).toList();
    }
    public static List<String> missingFluidTags(List<String> values){
        if(values==null)return List.of();
        return values.stream().filter(s->s.startsWith("#") && BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID,id(s.substring(1)))).filter(t->t.size()>0).isEmpty()).map(s->"Unknown or empty fluid tag: "+s).toList();
    }
    public static List<String> missingBlockTags(List<String> values){
        if(values==null)return List.of();
        return values.stream().filter(s->s.startsWith("#") && BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK,id(s.substring(1)))).filter(t->t.size()>0).isEmpty()).map(s->"Unknown or empty block tag: "+s).toList();
    }
    private ResourceSelectors() {}
}
