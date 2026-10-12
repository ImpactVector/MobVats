package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeDefinition;
import net.neoforged.neoforge.fluids.FluidStack;

/** Presentation only. Volume is selected connected storage, not a new process tank. */
public final class MixerLiquidPresentation {
    public static int vertexAlpha(int tint){return (tint>>>24)&255;}
    /** Vanilla liquid lighting takes the brighter surface/above sample; emitting fluids add block light. */
    public static int liquidLight(int surface,int above,int emission){
        int block=Math.max(Math.max(surface&255,above&255),Math.clamp(emission,0,15)*16);
        int sky=Math.max((surface>>>16)&255,(above>>>16)&255);
        return block | (sky<<16);
    }
    public static FluidStack select(MixerRecipeDefinition recipe,List<FluidStack> stored) {
        return select(recipe,recipe==null?List.of():recipe.strategies(),stored);
    }
    public static FluidStack select(MixerRecipeDefinition recipe,List<MixerRecipeDefinition.Strategy> strategies,List<FluidStack> stored) {
        var nonempty=stored.stream().filter(s->!s.isEmpty()).toList();
        if(recipe!=null) {
            var candidates=new ArrayList<FluidStack>();
            for(var strategy:strategies)if(strategy.enabled()) {
                var sources=recipe.fluidOutputs().stream().filter(o->o.transform()!=null)
                    .map(o->o.transform().sourceInput()).distinct().toList();
                for(var stack:nonempty) {
                    boolean matches=false;
                    for(int i=0;i<strategy.fluids().size();i++)
                        if((sources.isEmpty()?strategy.fluids().get(i).recordInComposition():sources.contains(i))
                            && recipe.matchesInput(strategy,i,stack))matches=true;
                    if(matches && candidates.stream().noneMatch(s->s==stack))candidates.add(stack);
                }
            }
            // Waste and XP are recipe additives, rather than the carrier, when another input exists.
            var carriers=candidates.stream().filter(s->!s.is(ProductFluids.WASTE.get()) && !s.is(ProductFluids.EXPERIENCE.get())).toList();
            if(!carriers.isEmpty())candidates=new ArrayList<>(carriers);
            if(!candidates.isEmpty())return unique(candidates);
            return FluidStack.EMPTY;
        }
        return unique(nonempty);
    }
    private static FluidStack unique(List<FluidStack> stacks) {
        if(stacks.isEmpty())return FluidStack.EMPTY;
        var first=stacks.getFirst();long amount=0;
        for(var stack:stacks) {
            if(!FluidStack.isSameFluidSameComponents(first,stack))return FluidStack.EMPTY;
            amount+=stack.getAmount();
        }
        return first.copyWithAmount((int)Math.min(Integer.MAX_VALUE,amount));
    }
    /** Muted carrier palette. Waste contributes at most 10%, independent of waste proportion. */
    public static int compoundTint(FluidStack stack,int ordinaryTint) {
        var formula=stack.get(CompoundFluids.COMPOSITION.get());
        if(!CompoundFluids.isCompound(stack) || formula==null)return ordinaryTint;
        long water=0,lava=0,end=0,waste=0;
        for(var entry:formula.entries()) {
            if(exact(entry,CompoundComposition.Kind.FLUID,"minecraft:water"))water+=entry.amount();
            else if(exact(entry,CompoundComposition.Kind.FLUID,"minecraft:lava"))lava+=entry.amount();
            else if(exact(entry,CompoundComposition.Kind.ITEM,"minecraft:chorus_fruit")
                || exact(entry,CompoundComposition.Kind.ITEM,"minecraft:popped_chorus_fruit")
                || exact(entry,CompoundComposition.Kind.ITEM,"minecraft:ender_pearl")
                || exact(entry,CompoundComposition.Kind.ITEM,"minecraft:end_stone"))end+=entry.amount();
            else if(exact(entry,CompoundComposition.Kind.FLUID,"mobvats:waste"))waste+=entry.amount();
        }
        long total=water+lava+end;
        int color=ordinaryTint;
        if(total>0) {
            int[] colors={0x477f9d,0xc46b38,0x8665ad};long[] weights={water,lava,end};
            color=0xff000000;
            for(int shift:new int[]{16,8,0}) {
                long sum=0;for(int i=0;i<3;i++)sum+=((colors[i]>>shift)&255)*weights[i];
                color|=((int)(sum/total))<<shift;
            }
        }
        // End ingredients establish the aqueous carrier's appearance, rather than counting as mB.
        if(end>0 && lava==0)color=0xff8665ad;
        if(waste>0) {
            double mix=Math.min(.1,(double)waste/Math.max(1,total+waste));int dirty=0xff000000;
            for(int shift:new int[]{16,8,0})dirty|=(int)Math.round(((color>>shift)&255)*(1-mix)+((0x756142>>shift)&255)*mix)<<shift;
            color=dirty;
        }
        return color;
    }
    private static boolean exact(CompoundComposition.Entry entry,CompoundComposition.Kind kind,String id) {
        return entry.kind()==kind && entry.selector().equals("{\""+(kind==CompoundComposition.Kind.FLUID?"fluid":"item")+"\":\""+id+"\"}");
    }
    private MixerLiquidPresentation() {}
}
