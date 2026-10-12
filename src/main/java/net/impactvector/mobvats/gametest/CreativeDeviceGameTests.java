package net.impactvector.mobvats.gametest;

import java.util.List;
import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerCreativeDriveEntity;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class CreativeDeviceGameTests {
    private static final BlockPos P=new BlockPos(2,2,2);
    private static CreativeEndpointEntity device(GameTestHelper h,CreativeDeviceType type){h.setBlock(P,CreativeContent.block(type));return (CreativeEndpointEntity)h.getBlockEntity(P);}
    private static Player player(GameTestHelper h){var p=h.makeMockPlayer(GameType.CREATIVE);p.setShiftKeyDown(true);return p;}
    private static void click(GameTestHelper h,Player player,ItemStack stack){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);var pos=h.absolutePos(P);var state=h.getBlockState(P);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        if(stack.isEmpty())state.useWithoutItem(h.getLevel(),player,hit);
        else state.useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
    }
    private static FluidStack compound(String component){
        var stack=new FluidStack(ProductFluids.CULTURE_SLURRY.get(),1000);
        stack.set(CompoundFluids.COMPOSITION.get(),new CompoundComposition(List.of(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID,component,1000),new CompoundComposition.Entry(CompoundComposition.Kind.ITEM,"#mobvats:plant_biomass",8))));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Exact test composition"));return stack;
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void creativeDriveProvidesFullDriveOnlyOnFormedImpeller(GameTestHelper h){
        var origin=new BlockPos(1,1,1);MachineFixtures.mixerRig(h::setBlock,origin);Slice11Fixtures.mixerStorage(h,origin);
        var top=origin.offset(2,3,2);h.setBlock(top.east(),CreativeContent.block(DRIVE));
        var motor=(MixerCreativeDriveEntity)h.getBlockEntity(top.east());
        var misplaced=origin.offset(6,1,1);h.setBlock(misplaced,CreativeContent.block(DRIVE));var other=(MixerCreativeDriveEntity)h.getBlockEntity(misplaced);
        h.runAtTickTime(35,()->{
            var mixer=((MixerControllerEntity)h.getBlockEntity(origin.offset(0,1,2))).getMultiblockController().orElseThrow();
            h.assertTrue(mixer.isAssembled()&&mixer.drive().orElseThrow()==motor&&mixer.hasFullDrive()&&motor.availableDrive(mixer)==1&&other.availableDrive(mixer)==0,"Creative Drive did not satisfy formed Mixer drive contract");
            mixer.state().fluids(true).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);mixer.state().depositExperienceMb(250);
            mixer.state().cadence();h.assertTrue(mixer.state().progress()>0&&mixer.state().amount(0)==1000,"Creative Drive bypassed recipe timing");
            h.setBlock(top,Blocks.AIR);
        });
        h.runAtTickTime(60,()->{var mixer=((MixerControllerEntity)h.getBlockEntity(origin.offset(0,1,2))).getMultiblockController().orElseThrow();h.assertTrue(!mixer.isAssembled()&&motor.availableDrive(mixer)==0&&!mixer.hasFullDrive(),"Unformed Mixer retained valid drive");h.succeed();});
    }
    @GameTest(template="empty")
    public static void itemSourceSamplesComponentsAndExtractsForever(GameTestHelper h){
        var e=device(h,ITEM_SOURCE);var p=player(h);var cap=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),Direction.NORTH);
        h.assertTrue(cap!=null&&cap.extractItem(0,64,false).isEmpty(),"Unconfigured source supplied items");
        var sample=new ItemStack(Items.DIAMOND,7);sample.set(DataComponents.CUSTOM_NAME,Component.literal("Test diamond"));click(h,p,sample);
        h.assertTrue(p.getMainHandItem().getCount()==7&&e.itemSample().getCount()==1,"Sampling consumed or retained volume");
        for(int i=0;i<100;i++){var extracted=cap.extractItem(0,64,i%2==0);h.assertTrue(extracted.getCount()==64&&ItemStack.isSameItemSameComponents(extracted,sample),"Source depleted or stripped item components");extracted.set(DataComponents.CUSTOM_NAME,Component.literal("Changed copy"));}
        h.assertTrue(!cap.insertItem(0,sample,false).isEmpty()&&ItemStack.isSameItemSameComponents(e.itemSample(),sample),"Source accepted insertion or leaked mutable sample");
        p.setShiftKeyDown(false);click(h,p,new ItemStack(Items.STONE));h.assertTrue(ItemStack.isSameItemSameComponents(e.itemSample(),sample),"Ordinary click reconfigured source");h.succeed();
    }
    @GameTest(template="empty")
    public static void fluidSourceSamplesContainerAndDrainsForever(GameTestHelper h){
        var e=device(h,FLUID_SOURCE);var p=player(h);var cap=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(P),null);
        h.assertTrue(cap!=null&&cap.drain(1000,EXECUTE).isEmpty(),"Unconfigured source supplied fluid");click(h,p,new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET)&&e.fluidSample().getAmount()==1,"Sampling consumed container or retained volume");
        for(int i=0;i<100;i++)h.assertTrue(cap.drain(100000,i%2==0?SIMULATE:EXECUTE).getAmount()==100000,"Fluid source depleted");
        h.assertTrue(cap.drain(new FluidStack(Fluids.LAVA,100),EXECUTE).isEmpty()&&cap.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"Source accepted wrong drain or insertion");
        p.setShiftKeyDown(false);click(h,p,new ItemStack(Items.LAVA_BUCKET));h.assertTrue(e.fluidSample().is(Fluids.WATER),"Ordinary click changed fluid");
        p.setShiftKeyDown(true);click(h,p,new ItemStack(Items.STONE));h.assertTrue(e.fluidSample().is(Fluids.WATER),"Invalid sample cleared fluid");h.succeed();
    }
    @GameTest(template="empty")
    public static void compoundSourcePreservesAllComponents(GameTestHelper h){
        var e=device(h,FLUID_SOURCE);var p=player(h);var sample=compound("minecraft:water");var bucket=FluidUtil.getFilledBucket(sample);var before=bucket.copy();click(h,p,bucket);
        var cap=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(P),Direction.UP);
        h.assertTrue(ItemStack.matches(before,p.getMainHandItem())&&FluidStack.isSameFluidSameComponents(sample,cap.drain(12000,EXECUTE)),"Compound sample consumed container or lost full components");
        h.assertTrue(cap.drain(compound("minecraft:lava"),SIMULATE).isEmpty(),"Compound source matched different composition");
        var copy=cap.getFluidInTank(0);copy.remove(CompoundFluids.COMPOSITION.get());h.assertTrue(FluidStack.isSameFluidSameComponents(sample,e.fluidSample()),"Capability leaked sample reference");h.succeed();
    }
    @GameTest(template="empty")
    public static void wildcardItemSinkDiscardsWithoutBackpressure(GameTestHelper h){
        var e=device(h,ITEM_SINK);var cap=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),Direction.DOWN);
        for(int i=0;i<100;i++)for(var item:List.of(Items.STONE,Items.DIAMOND))h.assertTrue(cap.insertItem(0,new ItemStack(item,64),i%2==0).isEmpty(),"Wildcard item sink backpressured");
        h.assertTrue(cap.getStackInSlot(0).isEmpty()&&cap.extractItem(0,64,false).isEmpty()&&e.itemSample().isEmpty(),"Sink stored accepted items");h.succeed();
    }
    @GameTest(template="empty")
    public static void wildcardFluidSinkDiscardsWithoutBackpressure(GameTestHelper h){
        var e=device(h,FLUID_SINK);var cap=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(P),null);
        for(int i=0;i<100;i++)for(var fluid:List.of(new FluidStack(Fluids.WATER,Integer.MAX_VALUE),compound("minecraft:lava")))h.assertTrue(cap.fill(fluid,i%2==0?SIMULATE:EXECUTE)==fluid.getAmount(),"Wildcard fluid sink backpressured");
        h.assertTrue(cap.getFluidInTank(0).isEmpty()&&cap.drain(1000,EXECUTE).isEmpty()&&e.fluidSample().isEmpty(),"Sink stored accepted fluid");h.succeed();
    }
    @GameTest(template="empty")
    public static void filteredItemSinkRejectsNonmatchingIdentity(GameTestHelper h){
        var e=device(h,ITEM_SINK);var p=player(h);var sample=new ItemStack(Items.DIAMOND);sample.set(DataComponents.CUSTOM_NAME,Component.literal("Accepted"));click(h,p,sample);
        var cap=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),null);
        h.assertTrue(!cap.insertItem(0,new ItemStack(Items.DIAMOND),false).isEmpty()&&!cap.insertItem(0,new ItemStack(Items.STONE),true).isEmpty(),"Filtered item sink accepted mismatch");
        for(int i=0;i<100;i++)h.assertTrue(cap.insertItem(0,sample.copyWithCount(64),false).isEmpty(),"Filtered item sink backpressured");
        h.assertTrue(cap.extractItem(0,1,false).isEmpty(),"Filtered sink retained items");p.setShiftKeyDown(false);click(h,p,new ItemStack(Items.STONE));h.assertTrue(ItemStack.isSameItemSameComponents(e.itemSample(),sample),"Ordinary click changed sink filter");h.succeed();
    }
    @GameTest(template="empty")
    public static void filteredFluidSinkRejectsNonmatchingComposition(GameTestHelper h){
        var e=device(h,FLUID_SINK);var p=player(h);var sample=compound("minecraft:water");click(h,p,FluidUtil.getFilledBucket(sample));
        var cap=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(P),Direction.WEST);
        h.assertTrue(cap.fill(compound("minecraft:lava"),EXECUTE)==0&&cap.fill(new FluidStack(Fluids.WATER,1000),SIMULATE)==0,"Filtered sink accepted different fluid/composition");
        for(int i=0;i<100;i++)h.assertTrue(cap.fill(sample.copyWithAmount(100000),EXECUTE)==100000,"Filtered fluid sink backpressured");
        h.assertTrue(cap.drain(sample,EXECUTE).isEmpty()&&cap.getFluidInTank(0).isEmpty(),"Filtered fluid sink stored resources");
        p.setShiftKeyDown(false);click(h,p,new ItemStack(Items.LAVA_BUCKET));h.assertTrue(FluidStack.isSameFluidSameComponents(e.fluidSample(),sample),"Ordinary click changed sink composition");h.succeed();
    }
    @GameTest(template="empty")
    public static void saveLoadPickDropAndPlacementRetainSamples(GameTestHelper h){
        var p=player(h);var item=new ItemStack(Items.DIAMOND);item.set(DataComponents.CUSTOM_NAME,Component.literal("Persist me"));var fluid=compound("minecraft:water");
        for(var type:List.of(ITEM_SOURCE,ITEM_SINK,FLUID_SOURCE,FLUID_SINK)){
            var e=device(h,type);click(h,p,type.item()?item.copy():FluidUtil.getFilledBucket(fluid));var before=e.configuration(h.getLevel().registryAccess());
            var saved=e.saveWithFullMetadata(h.getLevel().registryAccess());click(h,p,ItemStack.EMPTY);e.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(before.equals(e.configuration(h.getLevel().registryAccess())),"Save/load lost sample "+type);
            var block=(CreativeDeviceBlock)e.getBlockState().getBlock();var clone=block.getCloneItemStack(h.getLevel(),h.absolutePos(P),e.getBlockState());
            var drops=Block.getDrops(e.getBlockState(),h.getLevel(),h.absolutePos(P),e);h.assertTrue(drops.size()==1&&EndpointState.read(clone).equals(EndpointState.read(drops.getFirst())),"Pick/drop lost sample "+type);
            var portable=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)clone.save(h.getLevel().registryAccess()));
            h.setBlock(P,Blocks.AIR);h.setBlock(P,block);block.setPlacedBy(h.getLevel(),h.absolutePos(P),h.getBlockState(P),p,portable);
            e=(CreativeEndpointEntity)h.getBlockEntity(P);h.assertTrue(before.equals(e.configuration(h.getLevel().registryAccess())),"Placement lost sample "+type);
            h.assertTrue(type.item()?ItemStack.isSameItemSameComponents(e.itemSample(),item):FluidStack.isSameFluidSameComponents(e.fluidSample(),fluid),"Roundtrip stripped components "+type);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void emptyHandClearsAllSourcesAndSinkFilters(GameTestHelper h){
        var p=player(h);
        for(var type:List.of(ITEM_SOURCE,ITEM_SINK,FLUID_SOURCE,FLUID_SINK)){
            var e=device(h,type);click(h,p,type.item()?new ItemStack(Items.DIAMOND):new ItemStack(Items.WATER_BUCKET));click(h,p,ItemStack.EMPTY);
            h.assertTrue(e.itemSample().isEmpty()&&e.fluidSample().isEmpty(),"Empty hand failed to clear "+type);
            h.assertTrue(type.item()?(type.source()?e.items().extractItem(0,64,false).isEmpty():e.items().insertItem(0,new ItemStack(Items.STONE),false).isEmpty()):(type.source()?e.fluids().drain(1000,EXECUTE).isEmpty():e.fluids().fill(new FluidStack(Fluids.LAVA,1000),EXECUTE)==1000),"Clear did not restore unconfigured/wildcard behavior "+type);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void standardCapabilitiesFeedMixerBusAndDisposeOutputs(GameTestHelper h){
        var origin=new BlockPos(1,1,1);MachineFixtures.mixerRig(h::setBlock,origin);Slice11Fixtures.mixerStorage(h,origin);
        // This test drives transfers explicitly; isolate it from controller automation.
        for(var pos:java.util.List.of(origin.offset(2,1,0),origin.offset(4,1,2),origin.offset(2,1,4)))
            ((net.impactvector.mobvats.gamecontent.multiblock.common.ConfigurableEndpoint)h.getBlockEntity(pos)).setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Passive);
        var fluidPos=origin.offset(2,1,-1);var itemPos=origin.offset(2,1,5);var sinkPos=origin.offset(6,1,2);
        h.setBlock(fluidPos,CreativeContent.block(FLUID_SOURCE));h.setBlock(itemPos,CreativeContent.block(ITEM_SOURCE));h.setBlock(sinkPos,CreativeContent.block(FLUID_SINK));
        var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));((CreativeEndpointEntity)h.getBlockEntity(fluidPos)).interact(p,InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CARROT));((CreativeEndpointEntity)h.getBlockEntity(itemPos)).interact(p,InteractionHand.MAIN_HAND);
        h.runAtTickTime(35,()->{
            var fluidSource=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(fluidPos),Direction.SOUTH);
            var fluidInput=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(origin.offset(2,1,0)),Direction.NORTH);
            var itemSource=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(itemPos),Direction.NORTH);
            var itemInput=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(origin.offset(2,1,4)),Direction.SOUTH);
            h.assertTrue(FluidUtil.tryFluidTransfer(fluidInput,fluidSource,1000,true).getAmount()==1000,"Standard capability transfer did not reach Mixer bus storage");
            h.assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(itemInput,itemSource.extractItem(0,3,false),false).isEmpty(),"Standard capability item transfer failed");
            var mixer=((MixerControllerEntity)h.getBlockEntity(origin.offset(0,1,2))).getMultiblockController().orElseThrow();
            h.assertTrue(mixer.state().amount(0)==1000&&mixer.state().plantCount()==3,"Mixer inputs failed bus storage integration");
            mixer.state().depositExperienceMb(250);h.assertTrue(mixer.state().commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe("culture_slurry")),"Mixer could not use source input");
            var output=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(origin.offset(4,1,2)),Direction.EAST);
            var sink=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(sinkPos),Direction.WEST);
            h.assertTrue(FluidUtil.tryFluidTransfer(sink,output,1000,true).getAmount()==1000&&mixer.state().amount(2)==0&&sink.getFluidInTank(0).isEmpty(),"Standard sink transfer retained or backpressured Mixer output");h.succeed();
        });
    }
}
