package net.impactvector.mobvats.gametest;

import java.util.*;
import java.nio.file.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerControllerEntity;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class MixerVesselGeometryGameTests {
    private static void close(GameTestHelper h,double a,double b,String message){h.assertTrue(Math.abs(a-b)<1e-10,message+": "+a+" != "+b);}
    @GameTest(template="empty",timeoutTicks=10)
    public static void claddingDoesNotChangeConduitOccupiedVolumes(GameTestHelper h){
        for(int d:List.of(3,5,7,9))for(int x=-(d-1)/2;x<=(d-1)/2;x++)for(int z=-(d-1)/2;z<=(d-1)/2;z++)for(var mode:ConduitMode.values()){
            var bare=MixerVesselGeometry.referenceConduit(d,x,z,ConduitCladding.BARE,mode);
            h.assertTrue(bare.equals(MixerVesselGeometry.referenceConduit(d,x,z,ConduitCladding.CASING,mode)) && bare.equals(MixerVesselGeometry.referenceConduit(d,x,z,ConduitCladding.GLASS,mode)),"Cladding changed actual conduit envelope at "+x+","+z);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void polygonOrientationAndVertexEnvelope(GameTestHelper h) {
        for(int d:List.of(3,5,7,9)) {
            var g=MixerVesselGeometry.forDiameter(d);double r=g.faceRadius();
            h.assertTrue(g.sides()==4*(d-1) && g.vertices().size()==g.sides(),"Incorrect side count");
            close(h,g.vertexRadius(),r/Math.cos(Math.PI/g.sides()),"Vertex radius");
            for(int i=0;i<g.sides();i++) {
                var a=g.vertices().get(i);var b=g.vertices().get((i+g.sides()-1)%g.sides());
                close(h,a.length(),g.vertexRadius(),"Vertex length");
                if(i%(g.sides()/4)==0) {
                    close(h,a.lerp(b,.5).length(),r,"Cardinal flat face midpoint");
                    h.assertTrue(Math.abs(a.x)>1e-6 && Math.abs(a.z)>1e-6,"Cardinal vertex instead of face");
                }
            }
            var v=g.vertices().get(0);
            h.assertTrue(g.intersects(new AABB(v.x-.001,0,v.z-.001,v.x+.001,1,v.z+.001)),"Vertex collision incorrectly uses apothem circle");
            h.assertTrue(!g.intersects(new AABB(r+.001,0,-.1,r+.01,1,.1)),"Flat face collision extends beyond apothem");
            close(h,g.faceRadius(),MixerVesselGeometry.REFERENCE_FACE_RADIUS*(d-1)/4.0,"Consistent extrapolation");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void referenceRadiusIsMaximalAndDeterministic(GameTestHelper h) {
        var g=MixerVesselGeometry.forDiameter(5);double minimum=Double.POSITIVE_INFINITY;
        for(int x:new int[]{-2,2})for(int z:new int[]{-2,2}) {
            var boxes=MixerVesselGeometry.referenceBuffer(5,x,z);h.assertTrue(g.clears(boxes),"Corner buffer does not fit");
            for(var box:boxes)minimum=Math.min(minimum,g.clearanceLimit(box.inflate(MixerVesselGeometry.SAFETY_CLEARANCE,0,MixerVesselGeometry.SAFETY_CLEARANCE)));
        }
        for(int sign:new int[]{-1,1})for(int offset:new int[]{-1,1})for(boolean swap:new boolean[]{false,true})
            for(var cladding:List.of(ConduitCladding.CASING,ConduitCladding.GLASS))for(var mode:ConduitMode.values()) {
                var boxes=MixerVesselGeometry.referenceConduit(5,swap?offset:2*sign,swap?2*sign:offset,cladding,mode);
                h.assertTrue(g.clears(boxes),"Edge riser/collar does not fit");
                for(var box:boxes)minimum=Math.min(minimum,g.clearanceLimit(box.inflate(MixerVesselGeometry.SAFETY_CLEARANCE,0,MixerVesselGeometry.SAFETY_CLEARANCE)));
            }
        close(h,g.faceRadius(),minimum,"Maximum reference apothem");
        close(h,g.faceRadius(),1.9365234375,"Stable reference radius");
        h.assertTrue(g==MixerVesselGeometry.forDiameter(5),"Descriptor is not cached");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void minimumAndLargerChartsUseGeometry(GameTestHelper h) {
        h.assertTrue(MixerClearanceCharts.chart(3).equals("C X C\nX S X\nC X C"),"Minimum chart: "+MixerClearanceCharts.chart(3));
        h.assertTrue(MixerClearanceCharts.chart(5).equals("B C X C B\nC X X X C\nX X S X X\nC X X X C\nB C X C B"),"Reference chart: "+MixerClearanceCharts.chart(5));
        for(int d:List.of(3,5,7,9)) {
            int half=(d-1)/2;long b=0;
            for(int z=-half;z<=half;z++)for(int x=-half;x<=half;x++) {
                var buffer=MixerClearanceCharts.buffer(d,x,z);
                h.assertTrue(buffer.legal(),"Chart device should use legal cladding");
                if(buffer.clears()){b++;h.assertTrue(!buffer.intersects() && buffer.radiusReduction()==0,"Outside buffer changes vessel");}
                for(var c:ConduitCladding.values())for(var mode:ConduitMode.values()) {
                    var pipe=MixerClearanceCharts.conduit(d,x,z,c,mode);
                    if(pipe.clears())h.assertTrue(!pipe.intersects() && pipe.radiusReduction()==0,"Outside pipe changes vessel");
                }
            }
            if(d>5)h.assertTrue(b>4,"Larger diameter has no extra buffer sites");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void bufferBodySupportsMountsAndBridges(GameTestHelper h) {
        var body=FluidBufferGeometry.BODY;
        close(h,body.getXsize(),10.0/16,"Buffer width");close(h,body.getZsize(),10.0/16,"Buffer depth");close(h,body.getYsize(),13.0/16,"Buffer height");
        close(h,body.minX,3.0/16,"Body centering");close(h,body.maxZ,13.0/16,"Body centering");
        var volumes=FluidBufferGeometry.envelope(BlockPos.ZERO,true,Set.of(Direction.NORTH),Set.of(Direction.EAST));
        h.assertTrue(volumes.size()==4,"Missing support/mount/bridge");
        h.assertTrue(volumes.stream().anyMatch(v->v.kind()==FluidBufferGeometry.Kind.SUPPORT && v.bounds().minY==0),"Support does not reach below");
        h.assertTrue(volumes.stream().anyMatch(v->v.kind()==FluidBufferGeometry.Kind.MOUNT && v.bounds().minZ==0),"Mount does not reach casing");
        h.assertTrue(volumes.stream().anyMatch(v->v.kind()==FluidBufferGeometry.Kind.BRIDGE && v.bounds().maxX==1 && v.bounds().getZsize()==10.0/16),"Bridge envelope incorrect");
        // A mount at the neighboring inward face can cross the polygon even when the body fits.
        var g=MixerVesselGeometry.forDiameter(5);double x=g.faceRadius()+.3125+.01;
        var bodyOnly=FluidBufferGeometry.envelope(BlockPos.ZERO,false,Set.of(),Set.of()).stream().map(v->v.bounds().move(x-.5,0,-.5)).toList();
        var mounted=FluidBufferGeometry.envelope(BlockPos.ZERO,false,Set.of(Direction.WEST),Set.of()).stream().map(v->v.bounds().move(x-.5,0,-.5)).toList();
        h.assertTrue(!g.intersects(bodyOnly.getFirst()) && mounted.stream().anyMatch(g::intersects),"Attachment intrusion ignored");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void authoritativeClustersPreventIncompatibleBridges(GameTestHelper h) {
        var p=new BlockPos(2,2,2);h.setBlock(p,FluidBufferContent.state(ConduitCladding.BARE));h.setBlock(p.east(),FluidBufferContent.state(ConduitCladding.BARE));h.setBlock(p.east(2),FluidBufferContent.state(ConduitCladding.BARE));
        var a=(FluidBufferEntity)h.getBlockEntity(p);var b=(FluidBufferEntity)h.getBlockEntity(p.east());var c=(FluidBufferEntity)h.getBlockEntity(p.east(2));
        a.tank.setFluid(new FluidStack(Fluids.WATER,1000));c.tank.setFluid(new FluidStack(Fluids.LAVA,1000));
        var volumes=FluidBufferGeometry.compile(h.getLevel(),h.absolutePos(p),h.absolutePos(p.east(2)));
        var aa=h.absolutePos(p);var bb=h.absolutePos(p.east());var cc=h.absolutePos(p.east(2));
        h.assertTrue(volumes.stream().filter(v->v.kind()==FluidBufferGeometry.Kind.BRIDGE).count()==2,"Empty intermediary joined incompatible stored identities");
        h.assertTrue(volumes.stream().anyMatch(v->v.owner().equals(aa) && v.kind()==FluidBufferGeometry.Kind.BRIDGE && v.bounds().maxX==aa.getX()+1),"Compatible adjacent buffers not bridged");
        h.assertTrue(volumes.stream().noneMatch(v->v.owner().equals(cc) && v.kind()==FluidBufferGeometry.Kind.BRIDGE),"Incompatible buffer bridged");
        b.filter("minecraft:water");volumes=FluidBufferGeometry.compile(h.getLevel(),aa,cc);
        h.assertTrue(volumes.stream().noneMatch(v->v.owner().equals(bb) && v.kind()==FluidBufferGeometry.Kind.BRIDGE),"Mismatched filters bridged");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void conduitFittingsAndStructuralLegalityRemainDistinct(GameTestHelper h) {
        var min=new BlockPos(-2,-2,-2);var max=new BlockPos(2,2,2);var p=new BlockPos(1,0,2);
        var cell=new ConduitGeometry.Cell(p,ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TAP,0,
            List.of(new ConduitGeometry.Attachment(new BlockPos(0,0,2),EndpointGeometry.Kind.PASSIVE)));
        var pipes=ConduitGeometry.bake(new ConduitGeometry.Snapshot(min,max,List.of(cell)));
        h.assertTrue(!pipes.endpointFittings().isEmpty(),"Missing TAP fitting");
        var g=MixerVesselGeometry.forDiameter(5);
        var actual=pipes.occupied().stream().map(v->v.bounds().move(-.5,0,-.5)).toList();
        h.assertTrue(actual.stream().allMatch(b->!g.intersects(b)),"Required edge fitting intersects reference vessel");
        h.assertTrue(MixerPlacement.allows(ConduitCladding.CASING,ConduitShell.MIXER,p,min,max)
            && !MixerPlacement.allows(ConduitCladding.BARE,ConduitShell.MIXER,p,min,max),"Physical clearance altered shell permissions");
        h.assertTrue(!MixerPlacement.allows(ConduitCladding.CASING,ConduitShell.VAT,p,min,max),"Wrong shell family allowed");
        var interior=new ConduitGeometry.Cell(BlockPos.ZERO,ConduitResource.FLUID,ConduitCladding.BARE,ConduitMode.TAP,0,
            List.of(new ConduitGeometry.Attachment(BlockPos.ZERO.east(),EndpointGeometry.Kind.PASSIVE)));
        var inside=ConduitGeometry.bake(new ConduitGeometry.Snapshot(min,max,List.of(interior)));
        boolean rejected=false;
        try {MixerSweep.compileOccupied(new BlockPos(0,1,0),1,5,inside.occupied().stream().map(ConduitGeometry.Volume::bounds).toList());}
        catch(MixerSweep.Interference interference){rejected=true;}
        h.assertTrue(rejected,"Central TAP fitting left an obstruction in vessel");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void sweepMaskShaftAndCapacityMatchPolygon(GameTestHelper h) {
        long previous=0;
        for(int d:List.of(3,5,7,9)) {
            var g=MixerVesselGeometry.forDiameter(d);var assembly=MixerSweep.compileOccupied(BlockPos.ZERO,2,d,List.of());
            long count=0;int r=g.gridRadiusPixels();
            for(int z=0;z<g.gridSide();z++)for(int x=0;x<g.gridSide();x++) {
                double px=(x-r+.5)/16,pz=(z-r+.5)/16;
                h.assertTrue(g.sweptColumn(x,z)==(g.contains(px,pz) && !MixerVesselGeometry.shaftContains(px,pz)),"Sweep disagrees with polygon");
                if(g.sweptColumn(x,z))count++;
                h.assertTrue(MixerRenderDescriptor.occupied(assembly,x,0,z)==g.sweptColumn(x,z),"Descriptor uses another boundary");
            }
            h.assertTrue(!g.sweptColumn(r,r) && !g.sweptColumn(r-1,r-1),"Shaft credited volume");
            h.assertTrue(count*32==assembly.geometricSweptVoxels() && assembly.slotCount()==(d-1)*(d-1)/4,"Capacity formula changed");
            h.assertTrue(assembly.geometricSweptVoxels()>previous,"Capacity does not scale");previous=assembly.geometricSweptVoxels();
            h.assertTrue(assembly.equals(MixerAssembly.load(assembly.save())),"Polygon save/load changes assembly");
            var outside=MixerSweep.compileOccupied(BlockPos.ZERO,2,d,List.of(new AABB(g.faceRadius()+.6,-2,0,g.faceRadius()+.8,0,1)));
            h.assertTrue(outside.equals(assembly),"Outside obstruction changed mask");
            boolean rejected=false;
            try {MixerSweep.compileOccupied(BlockPos.ZERO,2,d,List.of(new AABB(.75,-1,.25,1,-.25,.75)));}
            catch(MixerSweep.Interference interference){rejected=true;}
            h.assertTrue(rejected,"Obstruction near Shaft did not reject every radius");
        }
        h.assertTrue(MixerSweep.compileOccupied(BlockPos.ZERO,2,3,List.of()).slotCount()==1,"Minimum is unusable");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void bufferIdentityChangeAndRebuildPreserveGeometry(GameTestHelper h) {
        var o=new BlockPos(1,1,1);var top=MachineFixtures.mixer(h::setBlock,o,5,4,5,2,1,false);
        var p=o.offset(0,2,0);h.setBlock(p,FluidBufferContent.state(ConduitCladding.CASING,ConduitShell.MIXER));h.setBlock(p.below(),FluidBufferContent.state(ConduitCladding.CASING,ConduitShell.MIXER));
        final MixerAssembly[] before={null};
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,1))).getMultiblockController().orElseThrow();
            before[0]=c.assembly().orElseThrow();
            ((FluidBufferEntity)h.getBlockEntity(p)).tank.setFluid(new FluidStack(Fluids.WATER,1000));
            ((FluidBufferEntity)h.getBlockEntity(p.below())).tank.setFluid(new FluidStack(Fluids.LAVA,1000));
        });
        h.runAtTickTime(65,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,1))).getMultiblockController().orElseThrow();var after=c.assembly().orElseThrow();
            h.assertTrue(after.equals(before[0]) && after.radiusReduction()==0,"Outside buffer identity change altered capacity");
            h.assertTrue(FluidBufferGeometry.compile(h.getLevel(),h.absolutePos(o),h.absolutePos(o.offset(4,3,4))).stream().noneMatch(v->v.kind()==FluidBufferGeometry.Kind.BRIDGE),"Identity change retained obsolete buffer bridge");
            h.assertTrue(after.equals(MixerAssembly.load(after.save())),"Save/load changed geometry");
            before[0]=after;c.rebuildTopology();h.assertTrue(c.assembly().orElseThrow().equals(after),"Rebuild changed polygon geometry");
            h.setBlock(o.offset(0,1,0),Blocks.AIR);
        });
        h.runAtTickTime(95,()->h.setBlock(o.offset(0,1,0),MixerContent.state(MixerPartType.Casing)));
        h.runAtTickTime(130,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,1))).getMultiblockController().orElseThrow();
            h.assertTrue(c.assembly().orElseThrow().equals(before[0]),"Break/reform changed geometry");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void radiusStepsRemoveAllIntersectionsWithoutCutouts(GameTestHelper h) {
        var impeller=new BlockPos(0,4,0);
        var near=new AABB(2.1875,0,.1875,2.8125,3.8125,.8125);
        var far=new AABB(3.1875,0,.1875,3.8125,3.8125,.8125);
        var a=MixerSweep.compileOccupied(impeller,4,9,List.of(near,far));
        var b=MixerSweep.compileOccupied(impeller,4,9,List.of(near));
        h.assertTrue(a.equals(b) && a.radiusReduction()==3,"Steps depend on obstruction count rather than necessary clearance");
        close(h,a.rotorRadius(),MixerVesselGeometry.forDiameter(9).faceRadius()-3,"Whole-block radius reduction");
        h.assertTrue(a.effectiveDiameter()==3 && a.slotCount()==2,"Capacity includes local deductions");
        for(var box:List.of(near,far))h.assertTrue(!MixerSweep.intersects(a.vessel(),impeller,4,box,true),"Selected vessel intersects device");
        var d=MixerRenderDescriptor.from(a);long count=0;
        for(int y=0;y<64;y++)for(int z=0;z<a.vessel().gridSide();z++)for(int x=0;x<a.vessel().gridSide();x++)if(MixerRenderDescriptor.occupied(a,x,y,z))count++;
        h.assertTrue(count==a.geometricSweptVoxels() && d.tankBounds().maxX==a.rotorRadius(),"Rendering uses a different vessel after shrink");
        var above=new AABB(.25,4,.25,.75,5,.75);var below=new AABB(.25,-2,.25,.75,0,.75);
        h.assertTrue(MixerSweep.compileOccupied(impeller,4,9,List.of(above,below)).radiusReduction()==0,"Unreached layers changed radius");
        var floor=new MixerAssembly(impeller,4,9,0,MixerVesselGeometry.SHELL_DEPTH_PIXELS);
        h.assertTrue(!MixerRenderDescriptor.occupied(floor,floor.rotorRadiusPixels()+2,0,floor.rotorRadiusPixels()),"Tank overlaps formed floor");
        h.assertTrue(MixerRenderDescriptor.from(floor).tankBounds().minY==floor.vesselBottom(),"Floor clearance missing in renderer");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=130)
    public static void shellIntrusionShrinksAndRemovalRestoresCanonicalRadius(GameTestHelper h) {
        var o=new BlockPos(1,1,1);MachineFixtures.mixer(h::setBlock,o,5,5,5,4,1,false);
        var p=o.offset(4,2,2);h.setBlock(p,FluidBufferContent.state(ConduitCladding.CASING,ConduitShell.MIXER));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,1))).getMultiblockController().orElseThrow();var a=c.assembly().orElseThrow();
            h.assertTrue(a.radiusReduction()==1 && a.slotCount()==2,"Shell buffer did not select the smaller uniform vessel: "+a);
            for(var v:FluidBufferGeometry.compile(h.getLevel(),h.absolutePos(o),h.absolutePos(o.offset(4,4,4))))
                h.assertTrue(!MixerSweep.intersects(a.vessel(),a.impeller(),a.shaftLength(),a.floorInsetPixels(),v.bounds(),true),"Shrunk vessel still intersects buffer");
            h.assertTrue(a.equals(MixerAssembly.load(a.save())),"Selected reduction did not persist");
            h.setBlock(p,MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(75,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,1))).getMultiblockController().orElseThrow();
            h.assertTrue(c.assembly().orElseThrow().radiusReduction()==0 && c.slotCount()>1,"Removing device did not restore canonical vessel");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void generatedClearanceReport(GameTestHelper h) {
        try {Files.writeString(Path.of("mixer-clearance.md"),MixerClearanceCharts.markdown());Files.writeString(Path.of("mixer-reference.svg"),MixerClearanceCharts.referenceSvg());}
        catch(java.io.IOException e){throw new IllegalStateException("Cannot generate clearance report",e);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void historicalDenseStorageReportsActualInterference(GameTestHelper h) {
        var o=new BlockPos(1,1,1);MachineFixtures.mixerRig(h::setBlock,o);
        Slice11Fixtures.mixerStorage(h.getLevel(),h.absolutePos(o));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,2))).getMultiblockController().orElseThrow();
            h.assertTrue(!c.isAssembled() && c.slotCount()==0,"Historical storage retained an obstructed vessel");
            var error=c.getLastError().orElseThrow().getChatMessage().getString();
            h.assertTrue(error.contains("intersects every available Mixer vessel radius"),"Historical fixture failed without physical diagnostic: "+error);
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void tapsConnectAllCardinalFaceEndpointsWithoutShrinking(GameTestHelper h) {
        var o=new BlockPos(1,1,1);MachineFixtures.mixer(h::setBlock,o,5,5,5,4,1,false);
        var endpoints=List.of(o.offset(2,2,0),o.offset(4,2,2),o.offset(2,2,4),o.offset(0,2,2));
        for(var p:endpoints)h.setBlock(p,MixerContent.state(MixerPartType.FluidPort));
        for(int y=1;y<=3;y++)for(int side:new int[]{0,4})for(int offset:new int[]{1,3}) {
            for(var p:List.of(o.offset(side,y,offset),o.offset(offset,y,side)))
                h.setBlock(p,ConduitContent.state(ConduitResource.FLUID,ConduitCladding.CASING,ConduitShell.MIXER).setValue(ConduitBlock.MODE,ConduitMode.TAP));
        }
        h.setBlock(o.offset(0,3,2),MixerContent.state(MixerPartType.Controller));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,3,2))).getMultiblockController().orElseThrow();var a=c.assembly().orElseThrow();
            h.assertTrue(a.radiusReduction()==0,"Cardinal face TAP connections shrank the vessel: "+a);
            for(var p:endpoints)h.assertTrue(c.conduitGeometry().endpointFittings().stream().anyMatch(f->f.endpoint().position().equals(h.absolutePos(p))),"Missing cardinal face fitting: "+p);
            for(var v:c.conduitGeometry().occupied())h.assertTrue(!MixerSweep.intersects(a.vessel(),a.impeller(),a.shaftLength(),a.floorInsetPixels(),v.bounds(),true),"Cardinal face fitting intersects: "+v);
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void runtimePeripheralFixtureKeepsCanonicalRadius(GameTestHelper h) {
        var o=new BlockPos(1,1,1);MixerGameTests.rig(h);h.setBlock(o.offset(3,3,2),MixerContent.state(MixerPartType.Drive));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(o.offset(0,1,2))).getMultiblockController().orElseThrow();var a=c.assembly().orElseThrow();
            var conflicts=MixerSweep.occupied(h.getLevel(),h.absolutePos(o),h.absolutePos(o.offset(4,3,4)),c.conduitGeometry()).stream()
                .filter(v->MixerSweep.intersects(MixerVesselGeometry.forDiameter(5),a.impeller(),a.shaftLength(),a.floorInsetPixels(),v.bounds(),true))
                .map(v->v.owner().subtract(h.absolutePos(o)).toShortString()+" "+v.kind()+" "+v.bounds().move(-h.absolutePos(o).getX(),-h.absolutePos(o).getY(),-h.absolutePos(o).getZ())).toList();
            h.assertTrue(a.radiusReduction()==0,"Peripheral runtime fixture shrank: "+conflicts);h.succeed();
        });
    }
}
