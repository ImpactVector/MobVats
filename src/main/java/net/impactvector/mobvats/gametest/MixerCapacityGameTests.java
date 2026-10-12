package net.impactvector.mobvats.gametest;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class MixerCapacityGameTests {
    @GameTest(template="empty",timeoutTicks=10)
    public static void fullDepthTableShortShaftsAndEveryLegalReduction(GameTestHelper h){
        int[][] expected={{1,2,3,4},{4,8,12,16},{9,18,27,36},{16,32,48,64}};
        for(int size=0;size<4;size++){
            int diameter=3+2*size;
            for(int height=0;height<4;height++){
                int depth=2+2*height;
                var full=new MixerAssembly(BlockPos.ZERO,depth,diameter,0,MixerVesselGeometry.SHELL_DEPTH_PIXELS);
                h.assertTrue(full.slotCount()==expected[size][height],"Full-depth capacity table changed: "+diameter+" / "+depth);
            }
            for(int reduction=0;MixerVesselGeometry.reduced(diameter,reduction).isPresent();reduction++)
                for(int depth=1;depth<=8;depth++){
                    var a=new MixerAssembly(BlockPos.ZERO,depth,diameter,reduction);
                    int effective=diameter-2*reduction;
                    h.assertTrue(effective>=3 && a.effectiveDiameter()==effective && a.slotCount()==(effective-1)*(effective-1)*depth/8,
                        "Reduction or actual short-shaft depth ignored");
                    var floor=new MixerAssembly(BlockPos.ZERO,depth,diameter,reduction,MixerVesselGeometry.SHELL_DEPTH_PIXELS);
                    h.assertTrue(a.slotCount()==floor.slotCount() && MixerAssembly.load(floor.save()).slotCount()==a.slotCount(),
                        "Floor pixels or save/load changed formula");
                }
        }
        h.succeed();
    }
    private static final BlockPos O=new BlockPos(1,1,1),C=O.offset(0,1,1);
    private static BlockPos rig(GameTestHelper h,int width,int height,int length){
        return MachineFixtures.mixer(h::setBlock,O,width,height,width,length,1,false);
    }
    private static MultiblockMixer controller(GameTestHelper h){return ((MixerControllerEntity)h.getBlockEntity(C)).getMultiblockController().orElseThrow();}
    private static MixerAssembly assembly(GameTestHelper h){var c=controller(h);h.assertTrue(c.isAssembled(),"Capacity fixture failed: "+c.getLastError());return c.assembly().orElseThrow();}
    @GameTest(template="empty",timeoutTicks=60)
    public static void minimumPolygonBaseline(GameTestHelper h){
        rig(h,MachineFixtures.MINIMUM_MIXER_WIDTH,MachineFixtures.MINIMUM_MIXER_HEIGHT,MachineFixtures.MINIMUM_MIXER_SHAFT);h.runAtTickTime(35,()->{var a=assembly(h);h.assertTrue(a.shaftLength()==2 && a.slotCount()==1 && a.vessel()==MixerVesselGeometry.forDiameter(3),"Minimum cylinder baseline incorrect");
            var status=controller(h).capacityDiagnostics();h.assertTrue(status.contains("Shaft depth: 2") && status.contains("Geometric mixing volume:") && status.contains("Processing slots: 1"),"Missing diagnostics");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void deeperShaftAddsOnlyReachedLevels(GameTestHelper h){
        rig(h,3,5,4);h.runAtTickTime(35,()->{var a=assembly(h);h.assertTrue(a.shaftLength()==4 && a.slotCount()==2,"Shaft extension did not add reached volume above the floor");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void earlyStopExcludesLowerAndTopLayers(GameTestHelper h){
        var top=rig(h,3,6,2);h.setBlock(top.east(),FluidBufferContent.state(ConduitCladding.CASING,ConduitShell.MIXER));
        h.runAtTickTime(35,()->{var a=assembly(h);h.assertTrue(a.shaftLength()==2 && a.floorInsetPixels()==0 && a.slotCount()==1,"Unreached or machinery layers changed capacity");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void widerPolygonIncreasesCapacity(GameTestHelper h){
        rig(h,5,3,2);h.runAtTickTime(35,()->{var a=assembly(h);h.assertTrue(a.effectiveDiameter()==5 && a.slotCount()==4,"Capacity used rectangular volume or ignored radius");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void bufferIntrusionWithNoClearRadiusRejectsFormation(GameTestHelper h){
        var top=rig(h,5,4,2);h.setBlock(top.below().east(),FluidBufferContent.state(ConduitCladding.BARE));
        h.runAtTickTime(35,()->{var c=controller(h);h.assertTrue(!c.isAssembled() && c.slotCount()==0 && c.getLastError().orElseThrow().getChatMessage().getString().contains("intersects"),"Interior buffer retained an obstructed vessel");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void cornerConduitPreservesCapacity(GameTestHelper h){
        var top=rig(h,5,3,2);h.setBlock(O.offset(0,1,0),ConduitContent.state(ConduitResource.FLUID,ConduitCladding.CASING,ConduitShell.MIXER));
        h.runAtTickTime(35,()->{var a=assembly(h);var clean=MixerSweep.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(O.offset(4,2,4)),h.absolutePos(top),2,ConduitGeometry.Geometry.EMPTY);
            h.assertTrue(a.slotCount()==clean.slotCount() && a.slotCount()==4,"Legal corner conduit destroyed cylinder capacity");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void conduitIntrusionWithNoClearRadiusRejectsFormation(GameTestHelper h){
        var top=rig(h,5,3,2);h.setBlock(top.below().east(),ConduitContent.state(ConduitResource.FLUID,ConduitCladding.BARE));
        h.runAtTickTime(35,()->{h.assertTrue(!controller(h).isAssembled() && controller(h).slotCount()==0,"Conduit left an obstruction in the rotating vessel");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=140)
    public static void ordinaryInteriorEditInvalidatesAndRecovers(GameTestHelper h){
        var top=rig(h,5,4,2);final MixerAssembly[] before={null};
        h.runAtTickTime(35,()->{before[0]=assembly(h);h.setBlock(top.below().east(),Blocks.STONE);});
        h.runAtTickTime(65,()->{h.assertTrue(!controller(h).isAssembled() && controller(h).slotCount()==0,"Interior edit retained valid capacity");h.setBlock(top.below().east(),Blocks.AIR);});
        h.runAtTickTime(95,()->{h.assertTrue(assembly(h).equals(before[0]),"Interior correction failed automatic recovery");before[0]=assembly(h);});
        h.runAtTickTime(115,()->{h.assertTrue(controller(h).assembly().orElseThrow()==before[0],"Idle ticks rebuilt cached capacity");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void capacityPersistsAndReforms(GameTestHelper h){
        var top=rig(h,3,5,4);final MixerAssembly[] saved={null};
        h.runAtTickTime(35,()->{var c=controller(h);saved[0]=assembly(h);var data=c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            h.assertTrue(MixerAssembly.load(data.getCompound("mixerAssembly")).equals(saved[0]),"Capacity tag round trip failed");
            c.syncDataFrom(data,h.getLevel().registryAccess(),SyncReason.FullSync);h.assertTrue(c.assembly().orElseThrow().equals(saved[0]),"Controller reload changed capacity");h.setBlock(top,Blocks.AIR);});
        h.runAtTickTime(65,()->{h.assertTrue(controller(h).slotCount()==0 && controller(h).assembly().isEmpty(),"Broken Mixer exposed stale capacity");h.setBlock(top,MixerContent.state(MixerPartType.Impeller));});
        h.runAtTickTime(95,()->{h.assertTrue(assembly(h).equals(saved[0]),"Reform changed capacity");h.setBlock(top.below(4),MixerContent.state(MixerPartType.Casing));});
        h.runAtTickTime(125,()->{var a=assembly(h);h.assertTrue(a.shaftLength()==3 && a.slotCount()==1,"Shaft shortening retained cached volume");h.succeed();});
    }
}
