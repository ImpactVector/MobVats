package net.impactvector.mobvats.gametest;

import java.util.List;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatItemPortEntity;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class SharedPortGameTests {
    private static final BlockPos P=new BlockPos(2,2,2),O=new BlockPos(1,1,1);
    private static List<net.minecraft.world.level.block.state.BlockState> ports(){return List.of(Content.state(VatPartType.ItemPort),Content.state(VatPartType.FluidPort),MixerContent.state(MixerPartType.ItemPort),MixerContent.state(MixerPartType.FluidPort));}
    private static ConfigurableEndpoint endpoint(GameTestHelper h,net.minecraft.world.level.block.state.BlockState state){h.setBlock(P,net.minecraft.world.level.block.Blocks.AIR);h.setBlock(P,state);return (ConfigurableEndpoint)h.getBlockEntity(P);}
    private static FluidStack compound(){
        var stack=new FluidStack(ProductFluids.CULTURE_SLURRY.get(),1000);
        stack.set(CompoundFluids.COMPOSITION.get(),new CompoundComposition(List.of(new CompoundComposition.Entry(CompoundComposition.Kind.FLUID,"minecraft:water",1000))));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Sample identity"));return stack;
    }
    private static void click(GameTestHelper h,net.minecraft.world.entity.player.Player player,ItemStack held){
        player.setItemInHand(InteractionHand.MAIN_HAND,held);var pos=h.absolutePos(P);var state=h.getLevel().getBlockState(pos);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false);
        if(held.isEmpty())state.useWithoutItem(h.getLevel(),player,hit);
        else state.useItemOn(held,h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void allPortsSampleClearAndUseVatDirectionMenu(GameTestHelper h){
        var player=h.makeMockServerPlayerInLevel();player.setShiftKeyDown(true);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        for(var block:ports()){
            var port=endpoint(h,block);boolean item=port.endpoint().resource()==EndpointConfiguration.Resource.ITEM;
            click(h,player,item?new ItemStack(Items.WHEAT):new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(port.filter().equals(item?"minecraft:wheat":"minecraft:water"),"Sample UX differs: "+block);
            click(h,player,ItemStack.EMPTY);h.assertTrue(port.filter().isEmpty(),"Empty-hand clear differs: "+block);
            var menu=new MachinePortMenu(0,player.getInventory(),(BlockEntity)port);
            h.assertTrue(menu.clickMenuButton(player,0) && port.getIoDirection()==IoDirection.Output,"Shared direction menu failed: "+block);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void savePickDropPlaceRetainFiltersAndCompoundIdentity(GameTestHelper h){
        var player=h.makeMockServerPlayerInLevel();player.setShiftKeyDown(true);
        for(var block:ports()){
            var port=endpoint(h,block);boolean item=port.endpoint().resource()==EndpointConfiguration.Resource.ITEM;var sample=compound();
            click(h,player,item?new ItemStack(Items.WHEAT):FluidUtil.getFilledBucket(sample));port.setIoDirection(IoDirection.Output);port.setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Active);var filter=port.filter();
            var entity=(BlockEntity)port;var tag=((ISyncableEntity)entity).syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            port.filter("");port.setIoDirection(IoDirection.Input);((ISyncableEntity)entity).syncDataFrom(tag,h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(port.filter().equals(filter)&&port.getIoDirection().isOutput(),"Save/load lost endpoint configuration");
            var pick=block.getBlock().getCloneItemStack(h.getLevel(),h.absolutePos(P),entity.getBlockState());
            var drops=Block.getDrops(entity.getBlockState(),h.getLevel(),h.absolutePos(P),entity);
            h.assertTrue(drops.size()==2,"Expected port plus installed material");
            for(var stack:List.of(pick,drops.getFirst())){
                h.assertTrue(EndpointState.read(stack).getString("filter").equals(filter),"Pick/drop lost filter");
                h.setBlock(P,net.minecraft.world.level.block.Blocks.AIR);h.setBlock(P,block);
                block.getBlock().setPlacedBy(h.getLevel(),h.absolutePos(P),block,player,stack);port=(ConfigurableEndpoint)h.getBlockEntity(P);
                h.assertTrue(port.filter().equals(filter)&&port.getIoDirection().isOutput()&&port.getIoMode()==it.zerono.mods.zerocore.lib.data.IoMode.Active,"Place lost endpoint configuration");
                if(!item){h.assertTrue(port.endpoint().accepts(sample.copyWithAmount(17)),"Compound filter included volume");var different=sample.copy();different.remove(DataComponents.CUSTOM_NAME);h.assertTrue(!port.endpoint().accepts(different),"Compound filter stripped components");}
            }
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void obsoletePortRegistrationsAreAbsent(GameTestHelper h){
        for(var id:List.of("mixer_fluid_input","mixer_fluid_output","mixer_item_input"))
            h.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mobvats",id)),"Obsolete port registration remains");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void vatItemCapabilitySwitchesDirectionAndHonorsFilter(GameTestHelper h){
        var port=(VatItemPortEntity)endpoint(h,Content.state(VatPartType.ItemPort));port.filter("minecraft:wheat");
        var input=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),Direction.NORTH);
        h.assertTrue(input!=null && input.insertItem(0,new ItemStack(Items.WHEAT,2),false).isEmpty() && !input.insertItem(0,new ItemStack(Items.CARROT),false).isEmpty() && input.extractItem(0,1,false).isEmpty(),"Vat input policy failed");
        port.setIoDirection(IoDirection.Output);var output=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),Direction.NORTH);
        h.assertTrue(output!=null && !output.insertItem(0,new ItemStack(Items.WHEAT),false).isEmpty() && output.extractItem(0,1,false).getCount()==1,"Vat output policy failed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void consolidatedMixerPortsAcceptRecipeAndExportAfterDirectionChange(GameTestHelper h){
        MixerGameTests.rig(h);var inputPos=O.offset(2,1,0);var outputPos=O.offset(4,1,2);var itemPos=O.offset(2,1,4);
        h.setBlock(inputPos,MixerContent.state(MixerPartType.FluidPort));h.setBlock(outputPos,MixerContent.state(MixerPartType.FluidPort));h.setBlock(itemPos,MixerContent.state(MixerPartType.ItemPort));
        var item=(MixerItemPortEntity)h.getBlockEntity(itemPos);item.filter("minecraft:wheat");
        h.runAtTickTime(35,()->{
            var in=(MixerFluidPortEntity)h.getBlockEntity(inputPos);var out=(MixerFluidPortEntity)h.getBlockEntity(outputPos);out.setIoDirection(IoDirection.Output);
            var c=in.getMultiblockController().orElseThrow();h.assertTrue(c.isAssembled(),"Consolidated Mixer did not assemble");
            var fluid=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(inputPos),Direction.NORTH);
            h.assertTrue(fluid!=null && fluid.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==1000 && fluid.drain(1000,FluidAction.EXECUTE).isEmpty(),"Mixer fluid input policy failed");
            var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(itemPos),Direction.SOUTH);
            h.assertTrue(items!=null && !items.insertItem(0,new ItemStack(Items.CARROT),false).isEmpty() && items.insertItem(0,new ItemStack(Items.WHEAT,8),false).isEmpty() && items.extractItem(0,1,false).isEmpty(),"Mixer item input/filter policy failed");
            item.setIoDirection(IoDirection.Output);items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(itemPos),Direction.SOUTH);
            h.assertTrue(items!=null && !items.insertItem(0,new ItemStack(Items.WHEAT),false).isEmpty() && items.extractItem(0,1,false).isEmpty(),"Mixer output port exported recipe inputs");item.setIoDirection(IoDirection.Input);
            h.assertTrue(c.state().depositExperienceMb(250) && c.state().commit(MixerRecipeRegistry.recipe("culture_slurry")),"Consolidated ports broke recipe storage");
            var result=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(outputPos),Direction.EAST);
            h.assertTrue(result!=null && result.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==0 && result.drain(1000,FluidAction.EXECUTE).getAmount()==1000,"Mixer output export failed");
            h.assertTrue(fluid.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==1000 && fluid.fill(new FluidStack(ProductFluids.WASTE.get(),250),FluidAction.EXECUTE)==250,"Consolidated port rejected feeding recipe fluids");
            h.assertTrue(c.state().commit(MixerRecipeRegistry.recipe("feeding_slurry")) && c.state().plantCount()==7,"Recipe failed to consume items from consolidated Item Port");
            var feeding=result.drain(1000,FluidAction.EXECUTE);
            h.assertTrue(feeding.is(ProductFluids.FEEDING_SLURRY.get()) && feeding.getAmount()==1000,"Consolidated output lost feeding recipe product");
            in.setIoDirection(IoDirection.Output);
            h.assertTrue(fluid.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==0,"Cached fluid handler retained input direction");
            fluid=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(inputPos),Direction.NORTH);h.assertTrue(fluid!=null && fluid.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==0,"Direction change retained input capability");
            var player=h.makeMockServerPlayerInLevel();player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));in.configure(player,InteractionHand.MAIN_HAND);h.assertTrue(in.filter().isEmpty(),"Mixer diverged from assembled Vat filter lock");
            h.succeed();
        });
    }
}
