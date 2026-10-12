package net.impactvector.mobvats.gametest;

import java.util.*;
import it.zerono.mods.zerocore.lib.data.IoMode;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.CultureFouling;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Development-only fixtures. Configuration supplies identities; all movement uses normal ticks. */
final class CreativeProductionFixtures {
    static final class Rig {
        final ServerLevel level;
        final BlockPos origin, vatOrigin;
        final MixerRecipeDefinition recipe;
        final FluidStack product;
        boolean spinUp, running, selected, replenished, liquid, increased, full;
        int previousVolume;
        int emptyProcessingFrames;
        long fullProduced=-1;
        Rig(ServerLevel level,BlockPos origin,MixerRecipeDefinition recipe,FluidStack product,BlockPos vatOrigin){
            this.level=level;this.origin=origin;this.recipe=recipe;this.product=product;this.vatOrigin=vatOrigin;
        }
        MultiblockMixer mixer(){return ((MixerControllerEntity)level.getBlockEntity(origin.offset(0,1,2))).getMultiblockController().orElse(null);}
        MultiblockVat vat(){return vatOrigin==null?null:((VatHeadEntity)level.getBlockEntity(vatOrigin.offset(2,2,1))).getMultiblockController().orElse(null);}
        MixerFluidVoidEntity sink(){return (MixerFluidVoidEntity)level.getBlockEntity(origin.offset(0,1,3));}
        long produced(){return mixer().state().metrics().total(recipe.id()+"Produced");}
        void observe(){
            var c=mixer();if(c==null||!c.isAssembled())return;
            spinUp|=c.operatingState()==MixerOperatingState.SPINNING_UP;
            running|=c.operatingState()==MixerOperatingState.RUNNING;
            boolean processing=c.state().recipe().equals(recipe.id())&&c.state().progress()>0;
            selected|=processing;
            replenished|=produced()>0&&c.storage().connected(origin.offset(0,1,2)).stream().anyMatch(t->!t.isEmpty()&&recipe.matchesInput(0,t.getFluid()));
            liquid|=processing&&!c.workingFluid().isEmpty();
            if(processing&&c.workingFluid().isEmpty())emptyProcessingFrames++;
            var v=vat();if(v!=null&&v.isAssembled()){
                int amount=v.medium().getFluidAmount();increased|=amount>previousVolume;
                previousVolume=amount;
                if(amount==v.medium().getCapacity()&&amount>0){full=true;if(fullProduced<0)fullProduced=produced();}
            }
        }
        String diagnostics(){var c=mixer();return c==null?"Missing controller":
            "formed="+c.isAssembled()+" error="+c.getLastError()+" drive="+c.availableDrive()+" state="+c.operatingState()+
            " recipe="+c.state().status(true)+" storage="+c.storage().diagnostics()+" workingFluid="+c.workingFluid()+
            (vat()==null?"":" vatFormed="+vat().isAssembled()+" vatError="+vat().getLastError().map(e->e.getChatMessage().getString()).orElse("")+" medium="+vat().medium().getFluidAmount()+"/"+vat().medium().getCapacity()+" vatStorage="+vat().storage().diagnostics());}
        String verify(){
            var c=mixer();check(c!=null&&c.isAssembled(),"Structure missing");
            check(c.assembly().orElseThrow().radiusReduction()==0,"Fixture obstructs canonical polygon vessel");
            check(c.hasFullDrive(),"Insufficient power");check(spinUp&&running,"Drive transitions missing");
            check(selected&&replenished,"Recipe mismatch, missing connection or input starvation");
            check(liquid&&emptyProcessingFrames==0,"Presentation data empty during processing: "+emptyProcessingFrames+" ticks");
            var descriptor=c.renderDescriptor().orElseThrow();
            check(!descriptor.liquidSurface(descriptor.liquidLayer(c.workingFluid().getAmount())).isEmpty(),"Working fluid has no renderable liquid surface");
            if(!recipe.items().isEmpty())check(c.state().plantCount()>0,"Creative item source did not replenish normal recipe inventory");
            check(produced()>=recipe.outputAmount()*6L,"No sustained completed batches");
            var out=(MixerFluidPortEntity)level.getBlockEntity(origin.offset(4,1,2));
            check(c.storage().connectedEndpoints(origin.offset(0,1,2),out.getBlockPos(),ConduitResource.FLUID),"Output conduit disconnected");
            check(!out.fluids().drain(product.copyWithAmount(1),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).isEmpty(),"Output collection empty or composition changed");
            long retained=c.storage().connected(origin.offset(0,1,2)).stream().filter(t->FluidStack.isSameFluidSameComponents(t.getFluid(),product)).mapToLong(t->t.getFluidAmount()).sum();
            var v=vat();long medium=0;
            if(v!=null){
                check(v.isAssembled()&&increased&&full,"Vat did not fill through normal routing to capacity");
                check(v.medium().getFluidAmount()==v.medium().getCapacity()&&!v.medium().overCapacity(),"Vat overfill or capacity regression");
                check(FluidStack.isSameFluidSameComponents(v.medium().cleanPrototype(),product),"Vat composition changed");
                medium=v.medium().getFluidAmount()+v.storage().clusters().stream().filter(t->FluidStack.isSameFluidSameComponents(t.tank().getFluid(),product)).mapToLong(t->t.tank().getFluidAmount()).sum();
                check(produced()>fullProduced+recipe.outputAmount(),"Mixer stopped after Vat filled");
            }
            check(sink().disposedMb()>0,"Output backpressure not exercised");
            check(produced()==retained+medium+sink().disposedMb(),"Product lost or duplicated");
            return "PASS: "+recipe.id()+" at "+origin.toShortString()+"; "+produced()+" mB produced; "+retained+" retained; "+medium+" in Vat/network; "+sink().disposedMb()+" overflow disposed. "+diagnostics()+"\n\n";
        }
        void check(boolean condition,String message){if(!condition)throw new IllegalStateException(recipe.id()+": "+message+"; "+diagnostics());}
    }
    static Rig mixer(ServerLevel level,BlockPos o,String id,boolean integrated){
        var recipe=Objects.requireNonNull(MixerRecipeRegistry.recipe(id),"Missing acceptance recipe "+id);
        var strategy=recipe.strategies().stream().filter(MixerRecipeDefinition.Strategy::enabled).findFirst().orElseThrow();
        if(strategy.fluids().isEmpty()||strategy.fluids().size()>2||strategy.items().size()>1||recipe.fluidOutputs().size()!=1||!recipe.itemOutputs().isEmpty())
            throw new IllegalStateException("Unsupported creative fixture recipe shape: "+id);
        var inputs=new ArrayList<FluidStack>();
        for(int i=0;i<strategy.fluids().size();i++){
            final int index=i;
            var candidates=new ArrayList<FluidStack>();
            if(recipe.transform()!=null){
                for(var raw:List.of(Fluids.WATER,Fluids.LAVA))candidates.add(CultureFouling.dirty(new FluidStack(raw,1),net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles.wasteFormulaMb()));
            }
            BuiltInRegistries.FLUID.stream().filter(f->f.isSource(f.defaultFluidState())&&f!=Fluids.EMPTY).forEach(f->candidates.add(new FluidStack(f,1)));
            inputs.add(candidates.stream().filter(s->recipe.matchesInput(strategy,index,s)).findFirst().orElseThrow(()->new IllegalStateException("No supported source sample: "+id)).copyWithAmount(strategy.fluids().get(i).amount()));
        }
        var product=recipe.result(strategy,recipe.fluidOutputs().getFirst(),inputs.stream().map(List::of).toList());
        var filters=new ArrayList<String>();
        // Eleven input cells and one observable product cell make overflow testable in minutes.
        for(int i=0;i<11;i++)filters.add(fluidId(inputs.get(i%inputs.size())));
        filters.add(fluidId(product));
        AcceptanceMixerFixtures.build(level,o,filters);
        level.setBlockAndUpdate(o.offset(1,3,2),CreativeContent.block(CreativeDeviceType.DRIVE).defaultBlockState());
        fluidInput(level,o.offset(2,1,0),inputs.getFirst());
        source(level,o.offset(2,1,-1),inputs.getFirst(),ItemStack.EMPTY);
        if(inputs.size()>1){
            fluidInput(level,o.offset(1,1,0),inputs.get(1));
            source(level,o.offset(1,1,-1),inputs.get(1),ItemStack.EMPTY);
            // Cross the cardinal midpoint in the roof machinery layer, above the open vessel.
            for(var local:List.of(new BlockPos(1,2,0),new BlockPos(1,3,0),new BlockPos(2,3,0)))
                level.setBlockAndUpdate(o.offset(local),ConduitContent.state(ConduitResource.FLUID,ConduitCladding.GLASS,ConduitShell.MIXER).setValue(ConduitBlock.MODE,ConduitMode.TAP));
        }
        if(!strategy.items().isEmpty()){
            var demand=strategy.items().getFirst();
            var sample=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(demand.selector()).findFirst().orElseThrow();
            ((MixerPortEntity)level.getBlockEntity(o.offset(2,1,4))).filter(BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());
            level.setBlockAndUpdate(o.offset(2,1,5),Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,Direction.NORTH));
            source(level,o.offset(2,2,5),FluidStack.EMPTY,sample);
        }
        var out=(MixerFluidPortEntity)level.getBlockEntity(o.offset(4,1,2));out.filter(fluidId(product));out.setIoMode(IoMode.Passive);
        level.setBlockAndUpdate(o.offset(0,1,3),MixerContent.state(MixerPartType.FluidVoid));
        ((MixerFluidVoidEntity)level.getBlockEntity(o.offset(0,1,3))).filter(fluidId(product));
        BlockPos v=integrated?o.offset(5,0,1):null;
        if(v!=null){
            vat(level,v,product);
            // Adjacent output/input boundaries: passive output is pulled by the Vat's active inlet.
        }
        ManualTestWorld.sign(level,o.offset(0,0,-5),"CREATIVE MIXER",id,"Normal input ports","Storage -> Void");
        ManualTestWorld.sign(level,o.offset(3,0,-5),"SOURCE FLOW",inputs.stream().map(CreativeProductionFixtures::fluidId).reduce((a,b)->a+" + "+b).orElse(""),"Out: "+fluidId(product),integrated?"Culture -> Fill Valve":"Glass corner collects");
        return new Rig(level,o,recipe,product,v);
    }
    private static String fluidId(FluidStack s){return BuiltInRegistries.FLUID.getKey(s.getFluid()).toString();}
    private static void fluidInput(ServerLevel level,BlockPos p,FluidStack sample){
        level.setBlockAndUpdate(p,MixerContent.state(MixerPartType.FluidPort));
        var port=(MixerFluidPortEntity)level.getBlockEntity(p);port.filter(fluidId(sample));port.setIoMode(IoMode.Active);
    }
    static void source(ServerLevel level,BlockPos p,FluidStack fluid,ItemStack item){
        level.setBlockAndUpdate(p,CreativeContent.block(item.isEmpty()?CreativeDeviceType.FLUID_SOURCE:CreativeDeviceType.ITEM_SOURCE).defaultBlockState());
        var tag=new CompoundTag();if(!fluid.isEmpty())tag.put("fluidSample",fluid.save(level.registryAccess()));if(!item.isEmpty())tag.put("itemSample",item.save(level.registryAccess()));
        ((CreativeEndpointEntity)level.getBlockEntity(p)).configuration(tag,level.registryAccess());
    }
    static void vat(ServerLevel level,BlockPos v,FluidStack sample){
        MachineFixtures.vat(level::setBlockAndUpdate,v,5,3,4);
        ((VatHeadEntity)level.getBlockEntity(v.offset(2,2,1))).encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
        level.setBlockAndUpdate(v.offset(0,1,1),Content.state(VatPartType.FluidPort));
        var port=(VatFluidPortEntity)level.getBlockEntity(v.offset(0,1,1));port.filter(fluidId(sample));port.setIoMode(IoMode.Active);
        // Vat boundary ports require an inward TAP; side-adjacent shell headers alone do not attach.
        for(var local:List.of(new BlockPos(1,1,1),new BlockPos(1,2,1)))
            level.setBlockAndUpdate(v.offset(local),ConduitContent.state(ConduitResource.FLUID,ConduitCladding.BARE).setValue(ConduitBlock.MODE,ConduitMode.TAP));
        level.setBlockAndUpdate(v.offset(0,2,1),ConduitContent.state(ConduitResource.FLUID,ConduitCladding.GLASS,ConduitShell.VAT).setValue(ConduitBlock.MODE,ConduitMode.TAP));
        level.setBlockAndUpdate(v.offset(0,2,0),FluidBufferContent.state(ConduitCladding.GLASS,ConduitShell.VAT));
        ((FluidBufferEntity)level.getBlockEntity(v.offset(0,2,0))).filter(fluidId(sample));
        level.setBlockAndUpdate(v.offset(1,3,1),Content.state(VatPartType.FillValve));
        ((VatBoundaryEntity)level.getBlockEntity(v.offset(1,3,1))).filter(fluidId(sample));
        ManualTestWorld.sign(level,v.offset(0,0,7),"CREATIVE VAT",fluidId(sample),"Port -> Buffer", "-> Fill Valve");
    }
    private static final List<Rig> RIGS=new ArrayList<>();
    private static BlockPos waterVat;
    private static boolean waterIncreased,waterFull;
    static void build(ServerLevel level){
        RIGS.clear();waterIncreased=false;waterFull=false;
        ManualTestWorld.sign(level,new BlockPos(0,-60,4),"CREATIVE HARNESS","Follow cyan path","South to Z=156","4 Mixers + 2 Vats");
        for(int z=6;z<154;z++)level.setBlockAndUpdate(new BlockPos(-6,-61,z),Blocks.CYAN_WOOL.defaultBlockState());
        for(int x=-6;x<78;x++)level.setBlockAndUpdate(new BlockPos(x,-61,149),Blocks.CYAN_WOOL.defaultBlockState());
        RIGS.add(mixer(level,new BlockPos(8,-60,156),"feeding_slurry",false));
        RIGS.add(mixer(level,new BlockPos(26,-60,156),"culture_slurry",true));
        RIGS.add(mixer(level,new BlockPos(44,-60,156),"mobvats:filter_general",false));
        RIGS.add(mixer(level,new BlockPos(62,-60,156),"mobvats:filter_lava",false));
        waterVat=new BlockPos(8,-60,176);var water=new FluidStack(Fluids.WATER,1);vat(level,waterVat,water);
        source(level,waterVat.offset(-1,1,1),water,ItemStack.EMPTY);
    }
    static void observe(ServerLevel level){
        RIGS.forEach(Rig::observe);
        var vat=((VatHeadEntity)level.getBlockEntity(waterVat.offset(2,2,1))).getMultiblockController().orElse(null);
        if(vat!=null&&vat.isAssembled()){waterIncreased|=vat.medium().getFluidAmount()>0;waterFull|=vat.medium().getFluidAmount()==vat.medium().getCapacity();}
    }
    static void verify(ServerLevel level,StringBuilder manifest) throws java.io.IOException {
        var report=new StringBuilder("# Creative-fed acceptance\n\nNative machine, hopper and conduit ticks; no controller injection.\n\n");
        var failures=new ArrayList<String>();
        for(var rig:RIGS)try{report.append(rig.verify());}catch(IllegalStateException e){failures.add(e.getMessage());}
        var vat=((VatHeadEntity)level.getBlockEntity(waterVat.offset(2,2,1))).getMultiblockController().orElse(null);
        if(vat==null||!vat.isAssembled()||!waterIncreased||!waterFull||vat.medium().getFluidAmount()!=vat.medium().getCapacity()||vat.medium().overCapacity()||!vat.medium().cleanPrototype().is(Fluids.WATER))failures.add("Water Vat failed formation, filling, composition or capacity enforcement");
        else report.append("PASS: Water Vat at "+waterVat.toShortString()+"; "+vat.medium().getFluidAmount()+" mB / "+vat.medium().getCapacity()+" mB; normal active inlet, glass buffer, conduit and Fill Valve.\n\n");
        report.append("Client visual acceptance pending: inspect Mixer working-fluid color, level and visibility through processing, and Vat tinted glass coverage/top surface against actual stored medium height; headless tests verify state and geometry, not rendered pixels.\n\n");
        for(var failure:failures)report.append("FAIL: "+failure+"\n\n");
        manifest.append(report);
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("mobvats.testWorldPath"),"creative-acceptance.md"),report);
        if(!failures.isEmpty())throw new IllegalStateException(String.join("; ",failures));
    }
    private CreativeProductionFixtures(){}
}
