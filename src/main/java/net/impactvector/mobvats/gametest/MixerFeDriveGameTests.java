package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.config.MixerEnergyConfig;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class MixerFeDriveGameTests {
    @GameTest(template="empty",timeoutTicks=140)
    public static void maximum64SlotsPowerSchedulingAndTransactions(GameTestHelper h){
        var top=MachineFixtures.mixer(h::setBlock,O,9,9,9,8,1,false);
        var p=top.east();h.setBlock(p,MixerContent.state(MixerPartType.FeDrive));
        h.runAtTickTime(35,()->{
            var c=mixer(h);var d=motor(h,p);
            h.assertTrue(c.isAssembled() && c.slotCount()==64 && c.spinUpTicks()==12800,"Maximum Mixer capacity/startup invalid");
            var data=c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            c.syncDataFrom(data,h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(c.slotCount()==64,"Maximum capacity lost on controller reload");
            var saved=net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.snapshot();
            try{
                net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.publish(
                    net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.parse(com.google.gson.JsonParser.parseString("""
                    {"recipes":{"test:maximum":{"batch_ticks":200,"strategies":[{"id":"default","items":[{"selector":"minecraft:carrot","amount":1}]}],
                    "outputs":{"items":[{"item":"minecraft:iron_ingot","amount":1}]}}}}
                    """)));
                c.state().itemInput().insertItem(0,new ItemStack(net.minecraft.world.item.Items.CARROT,2),false);
                d.energy().receiveEnergy(MixerFeDriveEntity.CAPACITY,false);
                int before=d.energy().getEnergyStored(),cost=MixerEnergyConfig.FE_PER_CYCLE.get();
                ticks(c,12799);state(h,c,MixerOperatingState.SPINNING_UP,12799);
                h.assertTrue(c.state().progress()==0 && c.state().plantCount()==2,"Recipe consumed during spin-up");
                ticks(c,1);state(h,c,MixerOperatingState.RUNNING,12800);
                h.assertTrue(before-d.energy().getEnergyStored()==64*cost,"64-slot startup FE cost overflowed or changed");
                ticks(c,199);h.assertTrue(c.state().plantCount()==2,"Recipe committed before its cadence");
                ticks(c,1);
                var out=c.state().itemPort(false,s->true);
                h.assertTrue(c.state().plantCount()==1 && out.getStackInSlot(0).getCount()==1 && c.state().progress()==0,
                    "64-slot machine changed established single-batch scheduling or duplicated output");
                h.assertTrue(before-d.energy().getEnergyStored()==65*cost && d.saveEnergy().getInt("credit")==0,"Running FE handling changed");
                var reservation=c.state().prepare(net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.recipe("test:maximum")).orElseThrow();
                h.assertTrue(reservation.commit() && !reservation.commit() && c.state().plantCount()==0 && out.getStackInSlot(0).getCount()==2,
                    "Maximum Mixer transaction replay duplicated resources");
                var depleted=d.saveEnergy();depleted.putInt("stored",0);depleted.putInt("credit",0);d.loadEnergy(depleted);
                ticks(c,1);state(h,c,MixerOperatingState.STOPPED,0);
            }finally{net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.publish(saved);}
            c.rebuildTopology();h.assertTrue(c.slotCount()==64,"Maximum reassembly changed capacity");
            h.setBlock(top.below(8),MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(70,()->{
            h.assertTrue(mixer(h).isAssembled() && mixer(h).slotCount()==56 && mixer(h).assembly().orElseThrow().shaftLength()==7,
                "Shortened maximum Shaft retained stale 64-slot capacity");
            h.setBlock(top.below(8),MixerContent.state(MixerPartType.Shaft));
        });
        h.runAtTickTime(105,()->{h.assertTrue(mixer(h).isAssembled() && mixer(h).slotCount()==64,"Restored maximum Shaft did not reassemble to 64 slots");h.succeed();});
    }
    private static final BlockPos O=new BlockPos(1,1,1),C=O.offset(0,1,1);
    private static BlockPos rig(GameTestHelper h,int depth){
        // A 5x5 three-shaft vessel reduced once provides one slot under the new geometry.
        boolean oneSlot=depth==1;if(oneSlot)depth=3;
        var top=MachineFixtures.mixer(h::setBlock,O,5,depth+2,5,depth,1,false);
        if(oneSlot)h.setBlock(O.offset(4,2,2),net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.state(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.CASING,net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitShell.MIXER));
        var p=top.east();h.setBlock(p,MixerContent.state(MixerPartType.FeDrive));return p;
    }
    private static MultiblockMixer mixer(GameTestHelper h){return ((MixerControllerEntity)h.getBlockEntity(C)).getMultiblockController().orElseThrow();}
    private static MixerFeDriveEntity motor(GameTestHelper h,BlockPos p){return (MixerFeDriveEntity)h.getBlockEntity(p);}
    private static void ticks(MultiblockMixer c,int count){MixerCombustionGameTests.ticks(c,count);}
    private static void state(GameTestHelper h,MultiblockMixer c,MixerOperatingState state,int startup){h.assertTrue(c.operatingState()==state && c.startupTicks()==startup,"Unexpected state/startup: "+c.operatingState()+" / "+c.startupTicks());}
    private static void startup(GameTestHelper h,int depth){
        var p=rig(h,depth);h.runAtTickTime(35,()->{
            var c=mixer(h);var d=motor(h,p);int cost=MixerEnergyConfig.FE_PER_CYCLE.get();
            h.assertTrue(c.isAssembled() && c.drive().orElseThrow()==d,"FE drive did not attach");
            d.energy().receiveEnergy(MixerFeDriveEntity.CAPACITY,false);int before=d.energy().getEnergyStored();
            for(int i=0;i<20;i++)h.assertTrue(c.availableDrive()==1,"Charged drive unavailable");
            h.assertTrue(d.energy().getEnergyStored()==before,"Query consumed FE");
            ticks(c,c.spinUpTicks()-1);state(h,c,MixerOperatingState.SPINNING_UP,c.spinUpTicks()-1);
            ticks(c,1);state(h,c,MixerOperatingState.RUNNING,c.spinUpTicks());
            h.assertTrue(before-d.energy().getEnergyStored()==c.slotCount()*cost && c.state().progress()==0,"Startup cost or recipe gating wrong");
            before=d.energy().getEnergyStored();ticks(c,200);
            h.assertTrue(before-d.energy().getEnergyStored()==cost,"Idle running cost scaled with slots");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60) public static void oneSlotStartupAndIdleCost(GameTestHelper h){startup(h,1);}
    @GameTest(template="empty",timeoutTicks=60) public static void eightSlotStartupAndIdleCost(GameTestHelper h){startup(h,5);}
    @GameTest(template="empty",timeoutTicks=60) public static void capabilitySimulationPersistenceAndPortableDrop(GameTestHelper h){
        var p=rig(h,1);h.runAtTickTime(35,()->{
            var d=motor(h,p);var c=mixer(h);
            for(var side:Direction.values())h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,h.absolutePos(p),side)==d.energy(),"Sided FE capability missing");
            var e=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,h.absolutePos(p),null);
            h.assertTrue(e!=null && e.canReceive() && !e.canExtract(),"Unsided receive-only capability missing");
            h.assertTrue(e.receiveEnergy(Integer.MAX_VALUE,true)==MixerFeDriveEntity.CAPACITY && e.getEnergyStored()==0,"Simulation changed FE");
            h.assertTrue(e.receiveEnergy(-1,false)==0 && e.receiveEnergy(Integer.MAX_VALUE,false)==MixerFeDriveEntity.CAPACITY && e.receiveEnergy(1,false)==0,"Capacity/negative input wrong");
            h.assertTrue(e.extractEnergy(100,false)==0 && e.getEnergyStored()==MixerFeDriveEntity.CAPACITY,"External extraction drained motor");
            ticks(c,1);var saved=d.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            int remaining=e.getEnergyStored();int credit=d.saveEnergy().getInt("credit");
            ticks(c,1);d.syncDataFrom(saved,h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(e.getEnergyStored()==remaining && d.saveEnergy().getInt("credit")==credit,"Save/load lost FE or credit");
            var drops=h.getBlockState(p).getDrops(new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,h.absolutePos(p).getCenter())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,ItemStack.EMPTY)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY,d));
            h.assertTrue(drops.size()==1 && drops.getFirst().is(MixerContent.block(MixerPartType.FeDrive).asItem()),"FE drive loot missing");
            var portable=drops.getFirst();h.setBlock(p,Blocks.AIR);h.assertTrue(e.receiveEnergy(10,false)==0 && d.availableDrive(c)==0,"Removed drive accepted FE or drove Mixer");
            h.setBlock(p,MixerContent.state(MixerPartType.FeDrive));EndpointState.restore(portable,h.getBlockEntity(p));
            var restored=motor(h,p);h.assertTrue(restored.energy().getEnergyStored()==remaining && restored.saveEnergy().getInt("credit")==credit,"Drop lost FE/credit");
            h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(p),null)==null && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(p),null)==null && restored.conduitResources().isEmpty(),"FE drive exposes item/fluid routing");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60) public static void configurableFractionalCostsAndAtomicPayment(GameTestHelper h){
        var p=rig(h,1);h.runAtTickTime(35,()->{
            var c=mixer(h);var d=motor(h,p);int original=MixerEnergyConfig.FE_PER_CYCLE.get();
            try{
                for(int cost:new int[]{1,200,2501,19999}){
                    MixerEnergyConfig.FE_PER_CYCLE.set(cost);d.loadEnergy(new CompoundTag());
                    var reset=c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);c.syncDataFrom(reset,h.getLevel().registryAccess(),SyncReason.FullSync);
                    d.energy().receiveEnergy(cost*3,false);ticks(c,200);h.assertTrue(d.energy().getEnergyStored()==cost*2,"Configured startup cost rounded incorrectly: "+cost);
                    ticks(c,400);h.assertTrue(d.energy().getEnergyStored()==0 && d.saveEnergy().getInt("credit")==0,"Fractional cost drift: "+cost);
                    ticks(c,1);state(h,c,MixerOperatingState.STOPPED,0);
                }
                MixerEnergyConfig.FE_PER_CYCLE.set(2501);d.loadEnergy(new CompoundTag());d.energy().receiveEnergy(12,false);ticks(c,1);
                state(h,c,MixerOperatingState.STOPPED,0);h.assertTrue(d.energy().getEnergyStored()==12 && d.saveEnergy().getInt("credit")==0,"Partial payment advanced startup or drained FE");
            }finally{MixerEnergyConfig.FE_PER_CYCLE.set(original);}h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60) public static void exhaustionRestartAndFullSpeedRecipe(GameTestHelper h){
        MixerGameTests.rig(h);var p=O.offset(3,3,2);h.setBlock(p,MixerContent.state(MixerPartType.FeDrive));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(O.offset(0,1,2))).getMultiblockController().orElseThrow();var d=motor(h,p);
            c.syncDataFrom(c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync),h.getLevel().registryAccess(),SyncReason.FullSync);
            int cost=MixerEnergyConfig.FE_PER_CYCLE.get();d.energy().receiveEnergy(cost*(c.slotCount()+1),false);ticks(c,c.spinUpTicks());
            c.state().fluids(true).fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);c.state().depositExperienceMb(250);
            ticks(c,30);h.assertTrue(c.state().progress()==30,"FE recipe did not process at normal speed: "+c.state().status(true)+" drive="+c.operatingState()+" slots="+c.slotCount());
            var saved=d.saveEnergy();saved.putInt("stored",0);saved.putInt("credit",0);d.loadEnergy(saved);ticks(c,100);
            state(h,c,MixerOperatingState.STOPPED,0);h.assertTrue(c.state().progress()==30,"Unpowered recipe advanced/lost progress");
            d.energy().receiveEnergy(cost*(c.slotCount()+1),false);ticks(c,c.spinUpTicks()-1);
            state(h,c,MixerOperatingState.SPINNING_UP,c.spinUpTicks()-1);h.assertTrue(c.state().progress()==30,"Startup advanced recipe");ticks(c,1);ticks(c,170);
            h.assertTrue(c.state().amount(2)==1000,"Recipe did not resume/complete at full speed");h.succeed();
        });
    }
    private static void legal(GameTestHelper h,Direction side){
        var p=rig(h,1);h.setBlock(p,MixerContent.state(MixerPartType.Casing));var target=p.west().relative(side);h.setBlock(target,MixerContent.state(MixerPartType.FeDrive));
        h.runAtTickTime(35,()->{var c=mixer(h);var d=motor(h,target);h.assertTrue(c.isAssembled() && c.drive().orElseThrow()==d,"Legal FE attachment rejected");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60) public static void westAttachment(GameTestHelper h){legal(h,Direction.WEST);}
    @GameTest(template="empty",timeoutTicks=60) public static void northAttachment(GameTestHelper h){legal(h,Direction.NORTH);}
    @GameTest(template="empty",timeoutTicks=60) public static void southAttachment(GameTestHelper h){legal(h,Direction.SOUTH);}
    private static void invalid(GameTestHelper h,BlockPos target){
        var p=rig(h,1);h.setBlock(p,MixerContent.state(MixerPartType.Casing));h.setBlock(target,MixerContent.state(MixerPartType.FeDrive));
        h.runAtTickTime(35,()->{var c=mixer(h);var d=motor(h,target);d.energy().receiveEnergy(10000,false);ticks(c,200);h.assertTrue(c.drive().isEmpty() && d.availableDrive(c)==0 && d.energy().getEnergyStored()==10000,"Invalid FE drive supplied/spent energy");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60) public static void diagonalRejected(GameTestHelper h){invalid(h,O.offset(3,2,1));}
    @GameTest(template="empty",timeoutTicks=60) public static void sideRejected(GameTestHelper h){invalid(h,O.offset(4,1,2));}
    @GameTest(template="empty",timeoutTicks=60) public static void bottomRejected(GameTestHelper h){invalid(h,O.offset(3,0,2));}
    @GameTest(template="empty",timeoutTicks=60) public static void mixedDrivesRejected(GameTestHelper h){
        var p=rig(h,1);h.setBlock(p.west(2),MixerContent.state(MixerPartType.CombustionDrive));
        h.runAtTickTime(35,()->{var c=mixer(h);var d=motor(h,p);d.energy().receiveEnergy(10000,false);ticks(c,200);h.assertTrue(!c.isAssembled() && c.drive().isEmpty() && d.energy().getEnergyStored()==10000,"Mixed drives formed/consumed FE");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60) public static void startupExhaustionRequiresFullRestart(GameTestHelper h){
        var p=rig(h,1);h.runAtTickTime(35,()->{
            var c=mixer(h);var d=motor(h,p);int cost=MixerEnergyConfig.FE_PER_CYCLE.get();
            d.energy().receiveEnergy(cost,false);ticks(c,100);state(h,c,MixerOperatingState.SPINNING_UP,100);
            var depleted=d.saveEnergy();depleted.putInt("stored",0);depleted.putInt("credit",0);d.loadEnergy(depleted);
            ticks(c,1);state(h,c,MixerOperatingState.STOPPED,0);
            d.energy().receiveEnergy(cost,false);ticks(c,199);state(h,c,MixerOperatingState.SPINNING_UP,199);
            ticks(c,1);state(h,c,MixerOperatingState.RUNNING,200);h.assertTrue(d.energy().getEnergyStored()==0,"Restart cost wrong");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=160) public static void shellBreakPreservesBufferAndReformRestarts(GameTestHelper h){
        var p=rig(h,1);int[] stored={0};
        h.runAtTickTime(35,()->{var c=mixer(h);motor(h,p).energy().receiveEnergy(MixerFeDriveEntity.CAPACITY,false);ticks(c,80);h.setBlock(O,Blocks.AIR);});
        h.runAtTickTime(65,()->{
            var c=mixer(h);var d=motor(h,p);h.assertTrue(!c.isAssembled(),"Broken FE rig still formed");state(h,c,MixerOperatingState.STOPPED,0);
            stored[0]=d.energy().getEnergyStored();ticks(c,200);h.assertTrue(d.energy().getEnergyStored()==stored[0],"Unformed drive spent FE");h.setBlock(O,MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(100,()->{
            var c=mixer(h);h.assertTrue(c.isAssembled() && c.operatingState()==MixerOperatingState.SPINNING_UP && c.startupTicks()<200,"Reform skipped startup");
            h.assertTrue(motor(h,p).energy().getEnergyStored()<stored[0],"Reform did not pay startup FE");h.succeed();
        });
    }
}
