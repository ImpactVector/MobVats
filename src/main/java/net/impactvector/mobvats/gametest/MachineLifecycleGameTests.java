package net.impactvector.mobvats.gametest;

import java.util.*;
import it.zerono.mods.zerocore.lib.IActivableMachine;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Real ZeroCore machines; synchronous ticks make production assertions independent of wall time. */
@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class MachineLifecycleGameTests {
    private static final BlockPos O=new BlockPos(1,1,1),H=O.offset(2,2,2),V=O.offset(1,1,0),M=O.offset(0,1,2),D=O.offset(3,3,2);
    private static void rig(GameTestHelper h,boolean mixer){
        if(mixer){MixerGameTests.rig(h);h.setBlock(D,CreativeContent.block(CreativeDeviceType.DRIVE));}
        else{
            VatGameTests.build(h,5);
            var baby=new CompoundTag();baby.putBoolean("Baby",true);
            var head=(VatHeadEntity)h.getBlockEntity(H);head.encode(ItemEssence.COW,head.geometry()==null?net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.legacy(ItemEssence.COW,Direction.NORTH):head.geometry(),baby);
            Slice11Fixtures.vatStorage(h,O,5,5);
            h.setBlock(O.offset(2,1,0),Content.state(VatPartType.ItemPort));
            h.setBlock(O.offset(2,1,4),Content.state(VatPartType.ToolPort));
            ((VatToolPortEntity)h.getBlockEntity(O.offset(2,1,4))).setHeldItem(new ItemStack(Items.IRON_SWORD));
        }
    }
    private static ConduitCuboidController<?> controller(GameTestHelper h,boolean mixer){
        return mixer?((MixerControllerEntity)h.getBlockEntity(M)).getMultiblockController().orElseThrow():((VatHeadEntity)h.getBlockEntity(H)).getMultiblockController().orElseThrow();
    }
    private static IActivableMachine operator(ConduitCuboidController<?> c){return (IActivableMachine)c;}
    private static PhysicalFluidStorage storage(ConduitCuboidController<?> c){return c instanceof MultiblockVat v?v.storage():((MultiblockMixer)c).storage();}
    private static CompoundTag save(GameTestHelper h,ConduitCuboidController<?> c){return c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);}
    private static void call(ConduitCuboidController<?> c,String name){
        try{var method=c.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(c);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void ticks(ConduitCuboidController<?> c,int n){for(int i=0;i<n;i++)call(c,"updateServer");}
    private static CompoundTag process(GameTestHelper h,ConduitCuboidController<?> c){
        if(c instanceof MultiblockMixer m){var tag=m.state().save(h.getLevel().registryAccess());tag.remove("metrics");return tag;}
        var tag=new CompoundTag();((MultiblockVat)c).processEngine().save(tag,h.getLevel().registryAccess());return tag;
    }
    private static List<FluidStack> fluids(ConduitCuboidController<?> c){return storage(c).clusters().stream().map(x->x.tank().getFluid().copy()).toList();}
    private static void sameFluids(GameTestHelper h,List<FluidStack> a,ConduitCuboidController<?> c){
        var b=fluids(c);h.assertTrue(a.size()==b.size(),"Cluster ownership changed");
        for(int i=0;i<a.size();i++)h.assertTrue(FluidStack.matches(a.get(i),b.get(i)),"Fluid amount/components changed in cluster "+i);
    }
    private static void seed(GameTestHelper h,ConduitCuboidController<?> c){
        var water=new FluidStack(Fluids.WATER,777);if(!(c instanceof MultiblockMixer))water.set(DataComponents.CUSTOM_NAME,Component.literal("Lifecycle identity"));
        var tank=storage(c).clusters().stream().map(PhysicalFluidStorage.Cluster::tank).filter(t->t.isFluidValid(water)).findFirst().orElseThrow();
        tank.fill(water,EXECUTE);
        if(c instanceof MultiblockMixer m){
            m.state().itemInput().insertItem(0,new ItemStack(Items.WHEAT,4),false);
            var waste=new FluidStack(net.impactvector.mobvats.gamecontent.fluid.ProductFluids.WASTE.get(),123);waste.set(DataComponents.CUSTOM_NAME,Component.literal("Lifecycle components"));
            storage(c).clusters().stream().map(PhysicalFluidStorage.Cluster::tank).filter(t->t.isFluidValid(waste)).findFirst().orElseThrow().fill(waste,EXECUTE);
        }
        else{
            var v=(MultiblockVat)c;v.processEngine().state(h.absolutePos(H)).growth().elapsed=137;
            ((VatItemPortEntity)h.getBlockEntity(O.offset(2,1,0))).staging().setStackInSlot(0,new ItemStack(Items.WHEAT,8));
            v.medium().fill(new FluidStack(Fluids.WATER,1000),EXECUTE);
        }
    }
    private static void activation(GameTestHelper h,boolean mixer){
        rig(h,mixer);h.runAtTickTime(35,()->{
            var c=controller(h,mixer);h.assertTrue(c.isAssembled() && operator(c).isMachineActive(),"New machine must be assembled/enabled");seed(h,c);
            if(mixer){var m=(MultiblockMixer)c;m.state().fluids(true).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);m.state().depositExperienceMb(250);ticks(c,m.spinUpTicks()+40);h.assertTrue(m.state().progress()>0,"Mixer fixture did not start recipe");}
            operator(c).setMachineActive(false);var before=process(h,c);var resource=fluids(c);
            var medium=mixer?null:((MultiblockVat)c).medium().save(h.getLevel().registryAccess());
            // Metrics observe assembled time; production state and resources must remain unchanged.
            before.remove("metrics");ticks(c,500);var after=process(h,c);after.remove("metrics");
            h.assertTrue(c.isAssembled() && before.equals(after),"Disabled production advanced/reset state");sameFluids(h,resource,c);
            if(!mixer){
                h.assertTrue(medium.equals(((MultiblockVat)c).medium().save(h.getLevel().registryAccess())),"Disable changed medium");
                h.assertTrue(((VatItemPortEntity)h.getBlockEntity(O.offset(2,1,0))).staging().getStackInSlot(0).getCount()==8 && ((VatToolPortEntity)h.getBlockEntity(O.offset(2,1,4))).heldItem().is(Items.IRON_SWORD),"Disable consumed input/tool");
                h.assertTrue(!((MultiblockVat)c).processEngine().tick(),"Direct engine tick bypassed disable");
            }else h.assertTrue(((MultiblockMixer)c).operatingState()==MixerOperatingState.STOPPED && ((MultiblockMixer)c).availableDrive()==1 && ((MultiblockMixer)c).diagnosticReport().getString().contains("Mixer formed / disabled"),"Disable conflated drive availability or diagnostics");
            var tag=save(h,c);operator(c).setMachineActive(true);c.syncDataFrom(tag,h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(!operator(c).isMachineActive(),"Full save/load lost disabled setting");
            h.assertTrue(!c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate).getBoolean("active"),"Network update omitted disabled setting");
            operator(c).setMachineActive(true);ticks(c,mixer?((MultiblockMixer)c).spinUpTicks()+10:1);
            var resumed=process(h,c);resumed.remove("metrics");h.assertTrue(!resumed.equals(before),"Enable did not resume production without block changes");
            if(!mixer)h.assertTrue(((MultiblockVat)c).processEngine().state(h.absolutePos(H)).growth().elapsed<150,"Reenable caught up elapsed disabled time");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=70) public static void vatDisableRetainsStateAndResumes(GameTestHelper h){activation(h,false);}
    @GameTest(template="empty",timeoutTicks=70) public static void mixerDisableRetainsStateAndResumes(GameTestHelper h){activation(h,true);}

    private static void pause(GameTestHelper h,boolean mixer,boolean active){
        rig(h,mixer);final CompoundTag[] saved={null};final var resource=new java.util.concurrent.atomic.AtomicReference<List<FluidStack>>();
        h.runAtTickTime(35,()->{
            var c=controller(h,mixer);seed(h,c);operator(c).setMachineActive(active);
            var head=mixer?null:(VatHeadEntity)h.getBlockEntity(H);long revision=head==null?0:head.presentationRevision();
            var chunk=h.getLevel().getChunkAt(c.getReferenceCoord().orElseThrow());boolean dirty=chunk.isUnsaved();
            saved[0]=process(h,c);resource.set(fluids(c));
            try{chunk.setUnsaved(false);if(mixer)((MixerControllerEntity)h.getBlockEntity(M)).onChunkUnloaded();else ((VatControllerEntity)h.getBlockEntity(V)).onChunkUnloaded();
                h.assertTrue(!chunk.isUnsaved(),"Pause/removal notified or dirtied reference chunk");
            }finally{chunk.setUnsaved(dirty);}
            h.assertTrue(c.isPaused() && operator(c).isMachineActive()==active,"Pause changed operator preference");
            h.assertTrue(storage(c).clusters().isEmpty() && c.conduitGeometry()==ConduitGeometry.Geometry.EMPTY,"Pause retained transient storage/geometry references");
            h.assertTrue(saved[0].equals(process(h,c)),"Pause deleted process state");
            if(head!=null)h.assertTrue(head.presentationRevision()==revision && ((MultiblockVat)c).processEngine().actorStatus(h.absolutePos(H),PassiveUses.HARVEST).isEmpty(),"Pause traversed presentation or retained contexts");
            if(mixer)((MixerControllerEntity)h.getBlockEntity(M)).clearRemoved();else ((VatControllerEntity)h.getBlockEntity(V)).clearRemoved();
        });
        h.runAtTickTime(75,()->{
            var c=controller(h,mixer);h.assertTrue(c.isAssembled() && operator(c).isMachineActive()==active,"Restore changed structure/preference");sameFluids(h,resource.get(),c);
            if(!active)h.assertTrue(saved[0].equals(process(h,c)),"Disabled reload advanced process");
            operator(c).setMachineActive(false);var before=process(h,c);
            for(int i=0;i<3;i++)call(c,"onMachineRestored");
            h.assertTrue(!operator(c).isMachineActive() && before.equals(process(h,c)),"Repeated restore reactivated/reset process");sameFluids(h,resource.get(),c);
            var count=storage(c).clusters().stream().flatMap(x->x.members().stream()).count();
            h.assertTrue(count==storage(c).clusters().stream().flatMap(x->x.members().stream()).distinct().count(),"Restore duplicated physical resource ownership");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100) public static void activeVatPauseRetiresReferencesWithoutNotifications(GameTestHelper h){pause(h,false,true);}
    @GameTest(template="empty",timeoutTicks=100) public static void disabledVatPauseRestoreKeepsPreference(GameTestHelper h){pause(h,false,false);}
    @GameTest(template="empty",timeoutTicks=100) public static void idleMixerPauseRestoreKeepsPreference(GameTestHelper h){pause(h,true,false);}
    private static void singleTickAfterRestore(GameTestHelper h,boolean mixer){
        rig(h,mixer);final ConduitCuboidController<?>[] restored={null};
        h.runAtTickTime(35,()->{operator(controller(h,mixer)).setMachineActive(false);if(mixer){var p=(MixerControllerEntity)h.getBlockEntity(M);p.onChunkUnloaded();p.clearRemoved();}else{var p=(VatControllerEntity)h.getBlockEntity(V);p.onChunkUnloaded();p.clearRemoved();}});
        h.runAtTickTime(75,()->{var c=controller(h,mixer);restored[0]=c;h.assertTrue(c.isAssembled(),"Tick fixture failed restoration");for(int i=0;i<3;i++)call(c,"onMachineRestored");if(mixer)((MultiblockMixer)c).state().metrics().load(new CompoundTag());else ((MultiblockVat)c).metrics().load(new CompoundTag());});
        h.runAtTickTime(95,()->{var c=controller(h,mixer);int ticks=mixer?((MultiblockMixer)c).state().metrics().elapsedTicks():((MultiblockVat)c).metrics().elapsedTicks();h.assertTrue(c==restored[0] && ticks==20,"Repeated restoration duplicated/missed controller ticks: "+ticks);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=115) public static void vatReloadAndRepeatedRestoreTickOnce(GameTestHelper h){singleTickAfterRestore(h,false);}
    @GameTest(template="empty",timeoutTicks=115) public static void mixerReloadAndRepeatedRestoreTickOnce(GameTestHelper h){singleTickAfterRestore(h,true);}

    private static void replacement(GameTestHelper h,boolean mixer,boolean replaceController){
        rig(h,mixer);var p=replaceController?(mixer?M:V):O.offset(4,1,2);
        final ConduitCuboidController<?>[] original={null};
        final CompoundTag[] partData={null},engine={null};final net.minecraft.world.level.block.state.BlockState[] state={null};final var resource=new java.util.concurrent.atomic.AtomicReference<List<FluidStack>>();
        h.runAtTickTime(35,()->{
            var c=controller(h,mixer);original[0]=c;seed(h,c);operator(c).setMachineActive(false);engine[0]=process(h,c);resource.set(fluids(c));
            var part=h.getBlockEntity(p);partData[0]=part.saveWithFullMetadata(h.getLevel().registryAccess());state[0]=h.getBlockState(p);h.setBlock(p,Blocks.AIR);
        });
        h.runAtTickTime(55,()->{h.assertTrue(!original[0].isAssembled(),"Removal did not break structure");h.setBlock(p,state[0]);h.getBlockEntity(p).loadWithComponents(partData[0],h.getLevel().registryAccess());h.getBlockEntity(p).setChanged();});
        h.runAtTickTime(90,()->{
            var c=controller(h,mixer);h.assertTrue(c.isAssembled() && !operator(c).isMachineActive(),"Replacement reactivated/lost structure");h.assertTrue(engine[0].equals(process(h,c)),"Replacement lost process state");sameFluids(h,resource.get(),c);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=110) public static void vatControllerRemovalRestoration(GameTestHelper h){replacement(h,false,true);}
    @GameTest(template="empty",timeoutTicks=110) public static void mixerControllerRemovalRestoration(GameTestHelper h){replacement(h,true,true);}
    @GameTest(template="empty",timeoutTicks=110) public static void vatPartReplacementPreservesDisabledState(GameTestHelper h){replacement(h,false,false);}
    @GameTest(template="empty",timeoutTicks=110) public static void mixerPartReplacementPreservesDisabledState(GameTestHelper h){replacement(h,true,false);}

    private static void assimilation(GameTestHelper h,boolean mixer,boolean disabledRecipient){
        var a=O;var b=O.east();
        h.setBlock(a,mixer?MixerContent.state(MixerPartType.Casing):Content.state(VatPartType.Casing));
        h.setBlock(b,mixer?MixerContent.state(MixerPartType.Casing):Content.state(VatPartType.Casing));
        if(mixer){
            var target=new MultiblockMixer(h.getLevel());var source=new MultiblockMixer(h.getLevel());
            target.attachPart((AbstractMixerEntity)h.getBlockEntity(a));source.attachPart((AbstractMixerEntity)h.getBlockEntity(b));
            (disabledRecipient?target:source).setMachineActive(false);source.state().itemInput().insertItem(0,new ItemStack(Items.WHEAT,3),false);
            target.assimilateController(source);h.assertTrue(!target.isMachineActive() && target.state().plantCount()==3 && source.state().plantCount()==0 && target.containsPart((AbstractMixerEntity)h.getBlockEntity(b)),"Mixer assimilation lost disable/resource ownership");
        }else{
            var target=new MultiblockVat(h.getLevel());var source=new MultiblockVat(h.getLevel());
            var pa=(AbstractVatEntity)h.getBlockEntity(a);var pb=(AbstractVatEntity)h.getBlockEntity(b);target.attachPart(pa);source.attachPart(pb);
            (disabledRecipient?target:source).setMachineActive(false);pb.service().tank(new FluidStack(Fluids.WATER,1)).fill(new FluidStack(Fluids.WATER,333),EXECUTE);
            target.assimilateController(source);h.assertTrue(!target.isMachineActive() && target.containsPart(pb) && pb.service().tank(new FluidStack(Fluids.WATER,1)).getFluidAmount()==333 && source.isEmpty(),"Vat assimilation lost disable/resource ownership");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10) public static void vatDisabledDonorAssimilation(GameTestHelper h){assimilation(h,false,false);}
    @GameTest(template="empty",timeoutTicks=10) public static void vatDisabledRecipientAssimilation(GameTestHelper h){assimilation(h,false,true);}
    @GameTest(template="empty",timeoutTicks=10) public static void mixerDisabledDonorAssimilation(GameTestHelper h){assimilation(h,true,false);}
    @GameTest(template="empty",timeoutTicks=10) public static void mixerDisabledRecipientAssimilation(GameTestHelper h){assimilation(h,true,true);}
    @GameTest(template="empty",timeoutTicks=10) public static void legacyControllersDefaultEnabledAndNetworkRoundTrip(GameTestHelper h){
        for(var c:List.<ConduitCuboidController<?>>of(new MultiblockVat(h.getLevel()),new MultiblockMixer(h.getLevel()))){
            c.syncDataFrom(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);h.assertTrue(operator(c).isMachineActive(),"Legacy/new controller default disabled");
            operator(c).setMachineActive(false);var tag=c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate);
            operator(c).setMachineActive(true);c.syncDataFrom(tag,h.getLevel().registryAccess(),SyncReason.NetworkUpdate);h.assertTrue(!operator(c).isMachineActive(),"Network flag did not round trip");
        }h.succeed();
    }
}
