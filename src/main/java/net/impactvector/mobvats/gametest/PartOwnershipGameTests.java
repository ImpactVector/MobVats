package net.impactvector.mobvats.gametest;

import java.util.*;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerState;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class PartOwnershipGameTests {
    private static final BlockPos O=new BlockPos(1,1,1),P=O.offset(4,1,2),C=O.offset(0,1,2),D=O.offset(3,3,2);
    private static void call(Object c,String name){
        try{var m=c.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(c);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static AttachedPartCollection<?> roles(Object c,String name){
        try{var f=c.getClass().getDeclaredField(name);f.setAccessible(true);return (AttachedPartCollection<?>)f.get(c);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void rig(GameTestHelper h,boolean mixer){
        if(mixer){MixerGameTests.rig(h);h.setBlock(D,CreativeContent.block(CreativeDeviceType.DRIVE));h.setBlock(P,MixerContent.state(MixerPartType.FluidPort));}
        else{VatGameTests.build(h,5);((VatHeadEntity)h.getBlockEntity(O.offset(2,2,2))).encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);h.setBlock(P,Content.state(VatPartType.FluidPort));}
    }
    private static ConduitCuboidController<?> controller(GameTestHelper h,boolean mixer){
        return mixer?((MixerControllerEntity)h.getBlockEntity(C)).getMultiblockController().orElseThrow():((VatHeadEntity)h.getBlockEntity(O.offset(2,2,2))).getMultiblockController().orElseThrow();
    }
    private static final class Probe implements ActiveBoundaryPort {
        private final BlockPos pos;
        private final EndpointConfiguration endpoint=new EndpointConfiguration(EndpointConfiguration.Resource.FLUID,IoDirection.Output,()->{});
        int calls;
        Probe(BlockPos pos){this.pos=pos;}
        public BlockPos getBlockPos(){return pos;}
        public EndpointConfiguration endpoint(){return endpoint;}
        public int transferBoundary(int limit){calls++;return 0;}
    }
    private static void dispatch(GameTestHelper h,boolean mixer){
        rig(h,mixer);h.runAtTickTime(35,()->{
            var c=controller(h,mixer);h.assertTrue(c.isAssembled(),"Fixture did not assemble");
            ((it.zerono.mods.zerocore.lib.IActivableMachine)c).setMachineActive(false);
            var roles=roles(c,"activePorts");var actual=h.getBlockEntity(P);
            h.assertTrue(roles.parts().stream().filter(p->p==actual).count()==1,"Port not registered exactly once");
            var probe=new Probe(h.absolutePos(P).above(20));roles.add(probe);roles.add(probe);
            for(int i=0;i<5;i++)call(c,"updateServer");
            h.assertTrue(probe.calls==5,"Controller dispatch must invoke each boundary once even while production is disabled");
            roles.remove(probe);for(int i=0;i<3;i++)call(c,"updateServer");h.assertTrue(probe.calls==5,"Retired device still ticks");
            roles.add(probe);call(c,"updateServer");h.assertTrue(probe.calls==6,"Reattached device missed dispatch");
            roles.remove(probe);var snapshot=roles.parts();
            var chunk=h.getLevel().getChunkAt(c.getReferenceCoord().orElseThrow());boolean dirty=chunk.isUnsaved();
            try{chunk.setUnsaved(false);call(c,"rebuildAttachedParts");h.assertTrue(!chunk.isUnsaved(),"Pure role rebuild dirtied reference");}finally{chunk.setUnsaved(dirty);}
            h.assertTrue(roles.parts().equals(snapshot),"Role rebuild changed membership/order");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=65) public static void vatOrderedBoundaryDispatchOnceAndCacheRebuildSilent(GameTestHelper h){dispatch(h,false);}
    @GameTest(template="empty",timeoutTicks=65) public static void mixerOrderedBoundaryDispatchOnceAndCacheRebuildSilent(GameTestHelper h){dispatch(h,true);}
    private static void lifecycle(GameTestHelper h,boolean mixer){
        rig(h,mixer);h.runAtTickTime(35,()->{
            var c=controller(h,mixer);var part=h.getBlockEntity(P);
            ((it.zerono.mods.zerocore.lib.IActivableMachine)c).setMachineActive(false);
            part.onChunkUnloaded();
            h.assertTrue(c.isPaused() && roles(c,"activePorts").parts().isEmpty(),"Pause retained port references");
            h.assertTrue(roles(c,mixer?"attachedPumps":"boundaryDevices").parts().isEmpty(),"Pause retained boundary references");
            if(mixer){
                try{var validation=c.getClass().getDeclaredMethod("isMachineWhole",it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator.class);validation.setAccessible(true);validation.invoke(c,c);}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                h.assertTrue(roles(c,"attachedDrives").parts().isEmpty() && roles(c,"activePorts").parts().isEmpty(),"Paused validation retained role references");
            }
            part.clearRemoved();
        });
        h.runAtTickTime(75,()->{
            var c=controller(h,mixer);h.assertTrue(c.isAssembled(),"Restore failed");
            for(int i=0;i<3;i++)call(c,"onMachineRestored");
            var part=h.getBlockEntity(P);var registered=roles(c,"activePorts").parts();
            h.assertTrue(registered.stream().filter(p->p==part).count()==1,"Restore duplicated/omitted port");
            if(mixer)h.assertTrue(roles(c,"attachedDrives").parts().size()==1 && ((MultiblockMixer)c).availableDrive()==1,"Restore failed drive discovery");
            h.setBlock(P,net.minecraft.world.level.block.Blocks.AIR);
            h.assertTrue(!roles(c,"activePorts").parts().contains(part),"Removal retained old block entity");
            h.setBlock(P,mixer?MixerContent.state(MixerPartType.FluidPort):Content.state(VatPartType.FluidPort));
        });
        h.runAtTickTime(115,()->{
            var c=controller(h,mixer);h.assertTrue(c.isAssembled() && roles(c,"activePorts").parts().contains(h.getBlockEntity(P)),"Replacement failed role rebuilding");
            call(c,"onMachineDisassembled");h.assertTrue(roles(c,"activePorts").parts().isEmpty(),"Break retained dispatch references");
            if(mixer)h.assertTrue(roles(c,"attachedDrives").parts().isEmpty(),"Break retained drives");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=145) public static void vatRolePauseRestoreRemovalReplacement(GameTestHelper h){lifecycle(h,false);}
    @GameTest(template="empty",timeoutTicks=145) public static void mixerRolePauseRestoreRemovalReplacement(GameTestHelper h){lifecycle(h,true);}
    private static void assimilation(GameTestHelper h,boolean mixer){
        h.setBlock(O,mixer?MixerContent.state(MixerPartType.FluidPort):Content.state(VatPartType.FluidPort));
        h.setBlock(O.east(),mixer?MixerContent.state(MixerPartType.FluidPort):Content.state(VatPartType.FluidPort));
        Object target,source;
        if(mixer){var a=new MultiblockMixer(h.getLevel());var b=new MultiblockMixer(h.getLevel());a.attachPart((AbstractMixerEntity)h.getBlockEntity(O));b.attachPart((AbstractMixerEntity)h.getBlockEntity(O.east()));a.assimilateController(b);target=a;source=b;}
        else{var a=new MultiblockVat(h.getLevel());var b=new MultiblockVat(h.getLevel());a.attachPart((AbstractVatEntity)h.getBlockEntity(O));b.attachPart((AbstractVatEntity)h.getBlockEntity(O.east()));a.assimilateController(b);target=a;source=b;}
        h.assertTrue(roles(target,"activePorts").parts().size()==2 && roles(source,"activePorts").parts().isEmpty(),"Assimilation duplicated/lost part roles");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10) public static void vatAssimilationTransfersBoundaryRoles(GameTestHelper h){assimilation(h,false);}
    @GameTest(template="empty",timeoutTicks=10) public static void mixerAssimilationTransfersBoundaryRoles(GameTestHelper h){assimilation(h,true);}
    @GameTest(template="empty",timeoutTicks=10) public static void unchangedBlockedMixerCadenceDoesNotNotify(GameTestHelper h){
        int[] updates={0};var state=new MixerState(()->updates[0]++);state.cadence();updates[0]=0;
        for(int i=0;i<20;i++)state.cadence();h.assertTrue(updates[0]==0,"Unchanged blocked cadence sends updates: "+updates[0]);
        state.itemInput().insertItem(0,new ItemStack(Items.WHEAT),false);h.assertTrue(updates[0]>0,"Resource mutation failed notification");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=65) public static void mixerSpinupPresentationDoesNotDirtyButGameplayDoes(GameTestHelper h){
        rig(h,true);h.runAtTickTime(35,()->{
            var c=(MultiblockMixer)controller(h,true);call(c,"tickDrive");
            var chunk=h.getLevel().getChunkAt(c.getReferenceCoord().orElseThrow());boolean dirty=chunk.isUnsaved();
            try{
                chunk.setUnsaved(false);int before=c.startupTicks();call(c,"tickDrive");
                h.assertTrue(c.startupTicks()==before+1 && !chunk.isUnsaved(),"Spin-up presentation dirtied persistent state");
                c.state().itemInput().insertItem(0,new ItemStack(Items.WHEAT),false);
                h.assertTrue(chunk.isUnsaved(),"Gameplay resource change failed to dirty reference");
            }finally{chunk.setUnsaved(dirty);}
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=65) public static void disabledIdleMixerDoesNotDirtyOnIntakeCadence(GameTestHelper h){
        rig(h,true);h.runAtTickTime(35,()->{
            var c=(MultiblockMixer)controller(h,true);c.setMachineActive(false);
            var chunk=h.getLevel().getChunkAt(c.getReferenceCoord().orElseThrow());boolean dirty=chunk.isUnsaved();
            try{chunk.setUnsaved(false);for(int i=0;i<40;i++)c.updateMultiblockEntity();h.assertTrue(!chunk.isUnsaved(),"Empty intake cadence dirtied an unchanged disabled machine");}
            finally{chunk.setUnsaved(dirty);}h.succeed();
        });
    }
}
