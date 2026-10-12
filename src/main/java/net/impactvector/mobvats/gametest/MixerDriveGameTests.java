package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class MixerDriveGameTests {
    private static final BlockPos ORIGIN=new BlockPos(1,1,1),CONTROLLER=ORIGIN.offset(0,1,2),IMPELLER=ORIGIN.offset(2,3,2);
    private static void rig(GameTestHelper h){MixerGameTests.rig(h);}
    private static MultiblockMixer mixer(GameTestHelper h){return ((MixerControllerEntity)h.getBlockEntity(CONTROLLER)).getMultiblockController().orElseThrow();}
    private static void placeDrive(GameTestHelper h,BlockPos pos){h.setBlock(pos,CreativeContent.block(CreativeDeviceType.DRIVE));}
    private static void attached(GameTestHelper h,BlockPos pos){
        var c=mixer(h);var drive=(MixerCreativeDriveEntity)h.getBlockEntity(pos);
        h.assertTrue(c.isAssembled(),"Drive prevented formation: "+c.getLastError().map(e->e.getChatMessage().getString()));
        h.assertTrue(drive.getPartType()==MixerPartType.Drive && c.containsPart(drive)
            && drive.getMultiblockController().orElseThrow()==c && drive.isMachineAssembled(),"Drive is not an assembled Mixer part");
        h.assertTrue(c.drive().orElseThrow()==drive && c.availableDrive()==1.0 && drive.availableDrive(c)==1.0,"Attached drive failed discovery/output");
        h.assertTrue(c.assembly().orElseThrow().impeller().equals(h.absolutePos(IMPELLER)) && c.assembly().orElseThrow().shaftLength()==2,"Drive changed rotor geometry");
    }
    private static void legal(GameTestHelper h,Direction side){rig(h);var pos=IMPELLER.relative(side);placeDrive(h,pos);h.runAtTickTime(35,()->{attached(h,pos);h.succeed();});}
    private static void invalid(GameTestHelper h,BlockPos pos){
        rig(h);placeDrive(h,pos);var drive=(MixerCreativeDriveEntity)h.getBlockEntity(pos);
        h.runAtTickTime(35,()->{var c=mixer(h);h.assertTrue(!c.isAssembled() && c.drive().isEmpty() && c.availableDrive()==0 && drive.availableDrive(c)==0,"Invalid drive supplied power or formed");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60) public static void eastDriveForms(GameTestHelper h){legal(h,Direction.EAST);}
    @GameTest(template="empty",timeoutTicks=60) public static void westDriveForms(GameTestHelper h){legal(h,Direction.WEST);}
    @GameTest(template="empty",timeoutTicks=60) public static void northDriveForms(GameTestHelper h){legal(h,Direction.NORTH);}
    @GameTest(template="empty",timeoutTicks=60) public static void southDriveForms(GameTestHelper h){legal(h,Direction.SOUTH);}
    @GameTest(template="empty",timeoutTicks=60) public static void missingDriveHasZeroOutput(GameTestHelper h){
        rig(h);h.runAtTickTime(35,()->{var c=mixer(h);h.assertTrue(c.isAssembled() && c.drive().isEmpty() && c.availableDrive()==0 && c.operatingState()==MixerOperatingState.STOPPED,"Missing-drive fallback remains");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void spinningMixerPauseDoesNotNotifyReferenceChunk(GameTestHelper h){pause(h,false);}
    @GameTest(template="empty",timeoutTicks=100) public static void runningMixerPauseDoesNotNotifyReferenceChunk(GameTestHelper h){pause(h,true);}
    private static void pause(GameTestHelper h,boolean running){
        rig(h);placeDrive(h,IMPELLER.east());
        var part=(MixerControllerEntity)h.getBlockEntity(CONTROLLER);
        h.runAtTickTime(35,()->{
            var c=mixer(h);attached(h,IMPELLER.east());
            MixerCombustionGameTests.ticks(c,running?c.spinUpTicks():1);
            h.assertTrue(c.operatingState()==(running?MixerOperatingState.RUNNING:MixerOperatingState.SPINNING_UP),"Pause fixture did not start its drive");
            var reference=c.getReferenceCoord().orElseThrow();
            var chunk=h.getLevel().getChunkAt(reference);boolean unsaved=chunk.isUnsaved(),notified;
            try{
                chunk.setUnsaved(false);
                // Exercise the actual ZC pause callback; keep the test chunk loaded so a
                // regressed notification is observable as a dirty mark instead of hanging.
                part.onChunkUnloaded();
                notified=chunk.isUnsaved();
            }finally{chunk.setUnsaved(unsaved);}
            h.assertTrue(!notified,"Unload pause notified/marked the reference chunk; this reacquires it during real unload");
            h.assertTrue(!c.isAssembled() && c.operatingState()==MixerOperatingState.STOPPED && c.startupTicks()==0,"Pause retained drive state");
            h.assertTrue(c.assembly().isEmpty() && c.conduitTopology()==ConduitTopology.EMPTY,"Pause retained geometry/connectivity");
            part.clearRemoved();
        });
        h.runAtTickTime(75,()->{
            var c=mixer(h);attached(h,IMPELLER.east());
            h.assertTrue(c.operatingState()==MixerOperatingState.SPINNING_UP && c.startupTicks()<c.spinUpTicks(),"Reload skipped drive startup");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60) public static void externalAboveImpellerIsNotDrive(GameTestHelper h){invalid(h,IMPELLER.above());}
    @GameTest(template="empty",timeoutTicks=60) public static void diagonalTopDriveRejects(GameTestHelper h){invalid(h,IMPELLER.east().north());}
    @GameTest(template="empty",timeoutTicks=60) public static void topFrameDriveRejects(GameTestHelper h){invalid(h,IMPELLER.east(2));}
    @GameTest(template="empty",timeoutTicks=60) public static void sideDriveRejects(GameTestHelper h){invalid(h,ORIGIN.offset(4,2,2));}
    @GameTest(template="empty",timeoutTicks=60) public static void bottomDriveRejects(GameTestHelper h){invalid(h,ORIGIN.offset(3,0,2));}
    @GameTest(template="empty",timeoutTicks=60) public static void interiorDriveRejects(GameTestHelper h){invalid(h,IMPELLER.east().below());}
    @GameTest(template="empty",timeoutTicks=120) public static void multipleDrivesRejectAndRecover(GameTestHelper h){
        rig(h);placeDrive(h,IMPELLER.east());placeDrive(h,IMPELLER.west());
        h.runAtTickTime(35,()->{var c=mixer(h);h.assertTrue(!c.isAssembled() && c.drive().isEmpty() && c.availableDrive()==0,"Multiple drives selected an arbitrary motor");h.setBlock(IMPELLER.west(),MixerContent.state(MixerPartType.Casing));});
        h.runAtTickTime(70,()->{attached(h,IMPELLER.east());h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=160) public static void driveBreakRepairRefreshesDiscovery(GameTestHelper h){
        rig(h);var pos=IMPELLER.east();placeDrive(h,pos);var old=(MixerCreativeDriveEntity)h.getBlockEntity(pos);
        h.runAtTickTime(35,()->{attached(h,pos);h.setBlock(pos,Blocks.AIR);h.assertTrue(old.availableDrive(mixer(h))==0,"Removed motor remained active");});
        h.runAtTickTime(65,()->{var c=mixer(h);h.assertTrue(!c.isAssembled() && c.drive().isEmpty() && c.availableDrive()==0,"Broken drive shell retained discovery");h.setBlock(pos,MixerContent.state(MixerPartType.Casing));});
        h.runAtTickTime(95,()->{var c=mixer(h);h.assertTrue(c.isAssembled() && c.drive().isEmpty() && c.availableDrive()==0,"Casing repair restored compatibility power");placeDrive(h,pos);});
        h.runAtTickTime(130,()->{attached(h,pos);h.assertTrue(mixer(h).drive().orElseThrow()!=old && old.availableDrive(mixer(h))==0,"Repair reused stale motor");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=130) public static void driveIsLocalConduitActorAndRebuilds(GameTestHelper h){
        rig(h);var pos=IMPELLER.east();var tap=pos.south();placeDrive(h,pos);
        var pipe=ConduitContent.state(ConduitResource.ITEM,ConduitCladding.CASING,ConduitShell.MIXER).setValue(ConduitBlock.MODE,ConduitMode.TAP);h.setBlock(tap,pipe);
        h.runAtTickTime(35,()->{
            attached(h,pos);var c=mixer(h);var absolute=h.absolutePos(pos);
            h.assertTrue(c.conduitTopology().components().stream().anyMatch(component->component.resource()==ConduitResource.ITEM && component.positions().contains(absolute)),"Drive absent from item conduit topology");
            h.assertTrue(c.conduitGeometry().snapshot().cells().stream().anyMatch(cell->cell.position().equals(h.absolutePos(tap)) && cell.attachments().stream().anyMatch(e->e.endpoint().equals(absolute))),"Conduit geometry omitted drive fitting");
            h.setBlock(pos,MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(70,()->{var c=mixer(h);h.assertTrue(c.isAssembled() && c.conduitTopology().components().stream().noneMatch(component->component.positions().contains(h.absolutePos(pos))),"Removed drive retained topology actor");placeDrive(h,pos);});
        h.runAtTickTime(105,()->{attached(h,pos);h.assertTrue(mixer(h).conduitTopology().components().stream().anyMatch(component->component.positions().contains(h.absolutePos(pos))),"Drive repair did not rebuild topology");h.succeed();});
    }
}
