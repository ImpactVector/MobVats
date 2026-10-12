package net.impactvector.mobvats.gametest;

import java.util.List;
import it.zerono.mods.zerocore.lib.data.*;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class PortLifecycleGameTests {
    private static final BlockPos O=new BlockPos(1,1,1),P=O.offset(4,1,2),N=P.east();
    private static List<net.minecraft.world.level.block.state.BlockState> ports(){return List.of(Content.state(VatPartType.ItemPort),Content.state(VatPartType.FluidPort),MixerContent.state(MixerPartType.ItemPort),MixerContent.state(MixerPartType.FluidPort));}
    @GameTest(template="empty",timeoutTicks=10)
    public static void modeDefaultsPersistencePortableStateAndIndependentRoles(GameTestHelper h){
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for(var block:ports()){
            h.setBlock(P,Blocks.AIR);h.setBlock(P,block);var port=(ConfigurableEndpoint)h.getBlockEntity(P);var entity=(BlockEntity)port;
            ((ISyncableEntity)port).syncDataFrom(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(port.getIoMode()==IoMode.Both,"Legacy mode must default to Both: "+block);
            String filter=port.endpoint().resource()==EndpointConfiguration.Resource.ITEM?"minecraft:wheat":"minecraft:water";
            port.filter(filter);
            for(var mode:IoMode.values()){
                port.setIoMode(mode);port.setIoDirection(IoDirection.Output);
                h.assertTrue(port.getIoMode()==mode && port.filter().equals(filter),"Direction discarded mode/filter");
                port.setIoMode(IoMode.Both);port.setIoMode(mode);
                h.assertTrue(port.getIoDirection()==IoDirection.Output && port.filter().equals(filter),"Mode discarded direction/filter");
                var endpoint=(ResourceEndpoint)port;var resource=port.endpoint().resource()==EndpointConfiguration.Resource.ITEM?ConduitResource.ITEM:ConduitResource.FLUID;
                h.assertTrue(endpoint.roles(resource).equals(java.util.Set.of(ConduitTopology.Role.CONSUMER)),"Mode changed output role");
                port.setIoDirection(IoDirection.Input);
                h.assertTrue(endpoint.roles(resource).equals(java.util.Set.of(ConduitTopology.Role.STORE)),"Mode changed input role");
                var tag=((ISyncableEntity)port).syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
                port.setIoMode(IoMode.None);((ISyncableEntity)port).syncDataFrom(tag,h.getLevel().registryAccess(),SyncReason.FullSync);
                h.assertTrue(port.getIoMode()==mode && port.filter().equals(filter),"Save/load discarded mode/filter");
                var pick=block.getBlock().getCloneItemStack(h.getLevel(),h.absolutePos(P),entity.getBlockState());
                var drop=Block.getDrops(entity.getBlockState(),h.getLevel(),h.absolutePos(P),entity).getFirst();
                for(var stack:List.of(pick,drop)){
                    h.assertTrue(EndpointState.read(stack).getString("ioMode").equals(mode.name()),"Pick/drop discarded mode");
                    h.setBlock(P,Blocks.AIR);h.setBlock(P,block);block.getBlock().setPlacedBy(h.getLevel(),h.absolutePos(P),block,player,stack);
                    port=(ConfigurableEndpoint)h.getBlockEntity(P);entity=(BlockEntity)port;
                    h.assertTrue(port.getIoMode()==mode && port.getIoDirection()==IoDirection.Input && port.filter().equals(filter),"Place discarded state");
                }
            }
            var invalid=new CompoundTag();invalid.putString("ioMode","future-invalid-mode");
            ((ISyncableEntity)port).syncDataFrom(invalid,h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(port.getIoMode()==IoMode.Both,"Invalid mode did not default safely");
        }h.succeed();
    }
    private static void rig(GameTestHelper h,boolean mixer){
        if(mixer){MixerGameTests.rig(h);h.setBlock(P,Blocks.AIR);h.setBlock(P,MixerContent.state(MixerPartType.FluidPort));}
        else{VatGameTests.build(h,5);((VatHeadEntity)h.getBlockEntity(O.offset(2,2,2))).encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);Slice11Fixtures.vatStorage(h,O,5,5);h.setBlock(P,Content.state(VatPartType.FluidPort));}
    }
    @GameTest(template="empty",timeoutTicks=110)
    public static void vatConnectivitySurvivesChunkLifecycleCallbacks(GameTestHelper h){chunkConnectivity(h,false);}
    @GameTest(template="empty",timeoutTicks=110)
    public static void mixerConnectivitySurvivesChunkLifecycleCallbacks(GameTestHelper h){chunkConnectivity(h,true);}
    private static void chunkConnectivity(GameTestHelper h,boolean mixer){
        rig(h,mixer);
        var port=(it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity<?>)h.getBlockEntity(P);
        var cached=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(P),Direction.EAST);
        final ConduitTopology[] previous=new ConduitTopology[1];
        h.runAtTickTime(35,()->{
            h.assertTrue(port.isMachineAssembled(),"Chunk lifecycle fixture failed assembly");
            previous[0]=mixer?((MixerFluidPortEntity)port).getMultiblockController().orElseThrow().conduitTopology():((VatFluidPortEntity)port).getMultiblockController().orElseThrow().getTopology().conduits();
            h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.EXECUTE)==100,"Initial cached handler fill failed");
            port.onChunkUnloaded();
            h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==0,"Unloaded port retained cached connectivity");
        });
        h.runAtTickTime(55,port::clearRemoved);
        h.runAtTickTime(90,()->{
            h.assertTrue(port.isMachineAssembled(),"Reload callback did not reassemble machine");
            var current=mixer?((MixerFluidPortEntity)port).getMultiblockController().orElseThrow().conduitTopology():((VatFluidPortEntity)port).getMultiblockController().orElseThrow().getTopology().conduits();
            h.assertTrue(current!=previous[0],"Reload reused old topology generation");
            h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==100,"Reload retained retired handler/storage identities");h.succeed();
        });
    }
    private static IFluidPortHandlerAdapter adapter(GameTestHelper h){
        var port=(ConfigurableEndpoint)h.getBlockEntity(P);
        return new IFluidPortHandlerAdapter(port,(it.zerono.mods.zerocore.base.multiblock.part.io.fluid.IFluidPort)port);
    }
    private record IFluidPortHandlerAdapter(ConfigurableEndpoint config,it.zerono.mods.zerocore.base.multiblock.part.io.fluid.IFluidPort fluid){
        boolean connected(){return fluid.getFluidPortHandler().isConnected();}
    }
    @GameTest(template="empty",timeoutTicks=110)
    public static void vatFluidModesNeighborsAndBreakRepair(GameTestHelper h){fluidLifecycle(h,false);}
    @GameTest(template="empty",timeoutTicks=110)
    public static void mixerFluidModesNeighborsAndBreakRepair(GameTestHelper h){fluidLifecycle(h,true);}
    private static void fluidLifecycle(GameTestHelper h,boolean mixer){
        rig(h,mixer);
        var cached=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(P),Direction.EAST);
        h.assertTrue(cached!=null && cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==0,"Unassembled port exposed storage");
        h.runAtTickTime(35,()->{
            var a=adapter(h);var port=a.config();
            var invalidations=new java.util.concurrent.atomic.AtomicInteger();
            var capabilityCache=BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK,h.getLevel(),h.absolutePos(P),Direction.EAST,()->true,invalidations::incrementAndGet);
            h.assertTrue(capabilityCache.getCapability()!=null,"Capability cache did not resolve");
            h.assertTrue(((it.zerono.mods.zerocore.base.multiblock.part.AbstractMultiblockEntity<?>)port).isMachineAssembled(),"Port fixture failed assembly");
            for(var mode:IoMode.values()){
                port.setIoDirection(IoDirection.Input);port.setIoMode(mode);
                h.assertTrue(invalidations.get()>0,"Configuration did not invalidate NeoForge capability");capabilityCache.getCapability();
                int accepted=cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE);
                h.assertTrue(accepted==(mode.isPassive()?100:0),"Passive fill policy differs: "+mode);
                h.assertTrue(a.fluid().getFluidPortHandler() instanceof it.zerono.mods.zerocore.base.multiblock.part.io.fluid.FluidPortHandlerForge,"Fluid handler no longer uses ZeroCore");
                var handler=(IFluidHandler)a.fluid().getFluidPortHandler();
                h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==accepted,"ZeroCore handler fill gating differs from external forwarding");
                port.setIoDirection(IoDirection.Output);
                h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==0,"Cached capability retained input direction");
            }
            port.setIoMode(IoMode.Both);port.setIoDirection(IoDirection.Input);
            h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==1000,"Both could not fill");
            if(port instanceof MixerFluidPortEntity mp){var c=mp.getMultiblockController().orElseThrow();h.assertTrue(c.state().depositExperienceMb(250) && c.state().commit(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe("culture_slurry")),"Mixer fixture did not produce exportable fluid");}
            port.setIoDirection(IoDirection.Output);
            for(var mode:IoMode.values()){
                port.setIoMode(mode);
                h.assertTrue(cached.drain(100,FluidAction.SIMULATE).getAmount()==(mode.isPassive()?100:0),"Passive drain policy differs: "+mode);
                port.setIoMode(IoMode.Both);var sample=cached.drain(100,FluidAction.SIMULATE);port.setIoMode(mode);
                h.assertTrue(cached.drain(sample,FluidAction.SIMULATE).getAmount()==(mode.isPassive()?100:0),"Stack drain policy differs: "+mode);
                h.assertTrue(((IFluidHandler)a.fluid().getFluidPortHandler()).drain(sample,FluidAction.SIMULATE).getAmount()==(mode.isPassive()?100:0),"ZeroCore handler drain gating differs from external forwarding");
            }
            port.setIoMode(IoMode.Both);h.assertTrue(!a.connected(),"Empty outward side reported connected");
            h.setBlock(N,net.impactvector.mobvats.gamecontent.creative.CreativeContent.block(net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.FLUID_SINK));
            h.assertTrue(a.connected(),"Neighbor placement did not refresh connection");
            port.setIoMode(IoMode.Passive);h.assertTrue(!a.connected(),"Passive-only port discovered active neighbor");
            port.setIoMode(IoMode.Active);h.assertTrue(a.connected(),"Active port did not discover neighbor");
            port.setIoMode(IoMode.None);h.assertTrue(!a.connected(),"None retained connection");
            port.setIoMode(IoMode.Both);h.setBlock(N,Blocks.AIR);h.assertTrue(!a.connected(),"Neighbor removal retained connection");
            // Exercise entity association callbacks without changing controller membership.
            if(port instanceof VatFluidPortEntity vat){var c=vat.getMultiblockController().orElseThrow();var topology=c.getTopology();vat.setIoMode(IoMode.Active);h.assertTrue(c.getTopology()==topology,"Mode rebuilt Vat topology");vat.setIoMode(IoMode.Both);vat.onDetached(c);h.assertTrue(cached.drain(100,FluidAction.SIMULATE).isEmpty(),"Detached Vat retained delegate");vat.onAttached(c);h.assertTrue(cached.drain(100,FluidAction.SIMULATE).getAmount()==100,"Attach did not restore Vat delegate");vat.onDetached(c);vat.onAssimilated(c);}
            else if(port instanceof MixerFluidPortEntity mp){var c=mp.getMultiblockController().orElseThrow();var topology=c.conduitTopology();mp.setIoMode(IoMode.Active);h.assertTrue(c.conduitTopology()==topology,"Mode rebuilt Mixer topology");mp.setIoMode(IoMode.Both);mp.onDetached(c);h.assertTrue(cached.drain(100,FluidAction.SIMULATE).isEmpty(),"Detached Mixer retained delegate");mp.onAttached(c);h.assertTrue(cached.drain(100,FluidAction.SIMULATE).getAmount()==100,"Attach did not restore Mixer delegate");mp.onDetached(c);mp.onAssimilated(c);}
            port.setIoMode(IoMode.Both);
            h.setBlock(O.offset(1,3,2),Blocks.AIR);
        });
        h.runAtTickTime(65,()->{
            h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==0 && cached.drain(100,FluidAction.SIMULATE).isEmpty(),"Broken machine retained delegate");
            h.assertTrue(!adapter(h).connected(),"Broken machine retained remote connection");
            h.setBlock(O.offset(1,3,2),mixer?MixerContent.state(MixerPartType.Casing):Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(95,()->{
            adapter(h).config().setIoDirection(IoDirection.Input);
            h.assertTrue(cached.fill(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==100,"Repair did not restore cached forwarding");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void vatItemModesRetainStagingAndGateCachedCapability(GameTestHelper h){
        h.setBlock(P,Content.state(VatPartType.ItemPort));var port=(VatItemPortEntity)h.getBlockEntity(P);port.filter("minecraft:wheat");
        var cached=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),Direction.EAST);
        cached.insertItem(0,new ItemStack(Items.WHEAT,3),false);
        for(var mode:IoMode.values()){
            port.setIoMode(mode);port.setIoDirection(IoDirection.Output);
            h.assertTrue(cached.extractItem(0,1,true).getCount()==(mode.isPassive()?1:0),"Item mode gating failed");
            h.assertTrue(port.staging().getStackInSlot(0).getCount()==3 && port.filter().equals("minecraft:wheat"),"Mode lost staging/filter");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void vatItemNeighborAndMachineLifecycle(GameTestHelper h){itemLifecycle(h,false);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void mixerItemNeighborAndMachineLifecycle(GameTestHelper h){itemLifecycle(h,true);}
    private static boolean itemConnected(ConfigurableEndpoint p){return p instanceof AbstractVatPortEntity v?v.isItemPortConnected():((MixerPortEntity)p).isItemPortConnected();}
    private static void itemLifecycle(GameTestHelper h,boolean mixer){
        rig(h,mixer);h.setBlock(P,mixer?MixerContent.state(MixerPartType.ItemPort):Content.state(VatPartType.ItemPort));
        h.runAtTickTime(35,()->{
            var port=(ConfigurableEndpoint)h.getBlockEntity(P);port.filter("minecraft:wheat");
            var cached=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(P),Direction.EAST);
            h.assertTrue(cached!=null,"Missing item capability");
            h.assertTrue(!itemConnected(port),"Empty item neighbor reported connected");
            h.setBlock(N,Blocks.CHEST);h.assertTrue(itemConnected(port),"Chest placement did not refresh item connection");
            for(var mode:IoMode.values()){
                port.setIoMode(mode);port.setIoDirection(IoDirection.Input);
                h.assertTrue(itemConnected(port)==mode.isActive(),"Item remote mode policy failed: "+mode);
                h.assertTrue(cached.insertItem(0,new ItemStack(Items.WHEAT),true).isEmpty()==mode.isPassive(),"Item passive insert mode policy failed: "+mode);
            }
            port.setIoMode(IoMode.Both);h.setBlock(N,Blocks.AIR);h.assertTrue(!itemConnected(port),"Chest removal retained connection");
            h.setBlock(N,Blocks.CHEST);h.assertTrue(itemConnected(port),"Replacement chest did not reconnect");
            if(port instanceof VatItemPortEntity v){var c=v.getMultiblockController().orElseThrow();v.onDetached(c);h.assertTrue(!itemConnected(v),"Detached Vat retained item source");v.onAttached(c);h.assertTrue(itemConnected(v),"Attached Vat did not reconnect item source");v.onDetached(c);v.onAssimilated(c);}
            else if(port instanceof MixerItemPortEntity mp){var c=mp.getMultiblockController().orElseThrow();mp.onDetached(c);h.assertTrue(!itemConnected(mp),"Detached Mixer retained item source");mp.onAttached(c);h.assertTrue(itemConnected(mp),"Attached Mixer did not reconnect item source");mp.onDetached(c);mp.onAssimilated(c);}
            h.setBlock(O.offset(1,3,2),Blocks.AIR);
        });
        h.runAtTickTime(65,()->{
            var port=(ConfigurableEndpoint)h.getBlockEntity(P);h.assertTrue(!itemConnected(port),"Broken machine retained item source");
            h.setBlock(O.offset(1,3,2),mixer?MixerContent.state(MixerPartType.Casing):Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(95,()->{var port=(ConfigurableEndpoint)h.getBlockEntity(P);h.assertTrue(itemConnected(port),"Repair did not reconnect item source");h.succeed();});
    }
}
