package net.impactvector.mobvats.gametest;

import java.util.List;
import it.zerono.mods.zerocore.lib.data.geometry.CuboidBoundingBox;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFluidPortEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatFluidPortEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class ShellFamilyGameTests {
    private static final BlockPos O=new BlockPos(1,1,1);
    private static boolean reaches(GameTestHelper h,boolean mixer){
        var end=h.getBlockEntity(O.offset(4,0,0));
        return mixer?ConduitStructure.neighbors(h.getLevel(),h.absolutePos(O),MultiblockMixer.class,List.of()).contains(end)
            :ConduitStructure.neighbors(h.getLevel(),h.absolutePos(O),MultiblockVat.class,List.of()).contains(end);
    }
    private static void bridge(GameTestHelper h,boolean mixer,boolean buffer){
        var family=mixer?ConduitShell.MIXER:ConduitShell.VAT;var foreign=mixer?ConduitShell.VAT:ConduitShell.MIXER;
        var part=mixer?MixerContent.block(MixerPartType.Casing):Content.block(VatPartType.Casing);
        h.setBlock(O,part);h.setBlock(O.offset(4,0,0),part);
        for(var cladding:List.of(ConduitCladding.CASING,ConduitCladding.GLASS))for(var resource:ConduitResource.values()){
            var same=buffer?FluidBufferContent.state(cladding,family):ConduitContent.state(resource,cladding,family);
            var other=buffer?FluidBufferContent.state(cladding,foreign):ConduitContent.state(resource,cladding,foreign);
            var bare=buffer?FluidBufferContent.state(ConduitCladding.BARE):ConduitContent.state(resource,ConduitCladding.BARE);
            for(int x=1;x<4;x++)h.setBlock(O.offset(x,0,0),same);
            var start=(IMultiblockPart<?>)h.getBlockEntity(O);var base=new CuboidBoundingBox(h.absolutePos(O),h.absolutePos(O));
            h.assertTrue(reaches(h,mixer),"Same-family shell did not continue to its part");
            h.assertTrue(ConduitStructure.bounds(h.getLevel(),base,List.of(start)).getMax().equals(h.absolutePos(O.offset(3,0,0))),"Same-family shell did not extend bounds");
            for(var stop:List.of(other,bare)){
                h.setBlock(O.offset(2,0,0),stop);
                h.assertTrue(!reaches(h,mixer),"Foreign or bare block bridged machine envelopes");
                h.assertTrue(ConduitStructure.bounds(h.getLevel(),base,List.of(start)).getMax().equals(h.absolutePos(O.offset(1,0,0))),"Foreign or bare block extended bounds");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void vatConduitsOnlyContinueVatShell(GameTestHelper h){bridge(h,false,false);}
    @GameTest(template="empty") public static void mixerConduitsOnlyContinueMixerShell(GameTestHelper h){bridge(h,true,false);}
    @GameTest(template="empty") public static void vatBuffersOnlyContinueVatShell(GameTestHelper h){bridge(h,false,true);}
    @GameTest(template="empty") public static void mixerBuffersOnlyContinueMixerShell(GameTestHelper h){bridge(h,true,true);}
    private static void touching(GameTestHelper h,boolean buffers,ConduitCladding cladding){
        ActiveBoundaryGameTests.pair(h,true);
        var left=O.offset(4,cladding==ConduitCladding.GLASS?2:0,1);var right=left.east();
        h.setBlock(left,buffers?FluidBufferContent.state(cladding,ConduitShell.MIXER):ConduitContent.state(ConduitResource.ITEM,cladding,ConduitShell.MIXER));
        h.setBlock(right,buffers?FluidBufferContent.state(cladding,ConduitShell.VAT):ConduitContent.state(ConduitResource.ITEM,cladding,ConduitShell.VAT));
        h.runAtTickTime(35,()->{
            var mixer=((MixerFluidPortEntity)h.getBlockEntity(O.offset(4,1,2))).getMultiblockController().orElseThrow();
            var vat=((VatFluidPortEntity)h.getBlockEntity(O.offset(5,1,2))).getMultiblockController().orElseThrow();
            h.assertTrue(mixer.isAssembled() && vat.isAssembled(),"Touching shell families invalidated machines");
            h.assertTrue(mixer.getBoundingBox().getMin().equals(h.absolutePos(O)) && mixer.getBoundingBox().getMax().equals(h.absolutePos(O.offset(4,3,4))),"Mixer envelope crossed touching cladding");
            h.assertTrue(vat.getBoundingBox().getMin().equals(h.absolutePos(O.offset(5,0,0))) && vat.getBoundingBox().getMax().equals(h.absolutePos(O.offset(9,3,4))),"Vat envelope crossed touching cladding");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60) public static void touchingCasingConduitsKeepControllersSeparate(GameTestHelper h){touching(h,false,ConduitCladding.CASING);}
    @GameTest(template="empty",timeoutTicks=60) public static void touchingGlassConduitsKeepControllersSeparate(GameTestHelper h){touching(h,false,ConduitCladding.GLASS);}
    @GameTest(template="empty",timeoutTicks=60) public static void touchingCasingBuffersKeepControllersSeparate(GameTestHelper h){touching(h,true,ConduitCladding.CASING);}
    @GameTest(template="empty",timeoutTicks=60) public static void touchingGlassBuffersKeepControllersSeparate(GameTestHelper h){touching(h,true,ConduitCladding.GLASS);}
    @GameTest(template="empty") public static void resourceTopologyIgnoresShellFamilyWithinBounds(GameTestHelper h){
        for(var resource:ConduitResource.values()){
            h.setBlock(O.offset(1,0,0),ConduitContent.state(resource,ConduitCladding.CASING,ConduitShell.VAT).setValue(ConduitBlock.MODE,ConduitMode.TAP));
            h.setBlock(O.offset(2,0,0),ConduitContent.state(resource,ConduitCladding.BARE));
            h.setBlock(O.offset(3,0,0),ConduitContent.state(resource,ConduitCladding.GLASS,ConduitShell.MIXER).setValue(ConduitBlock.MODE,ConduitMode.TAP));
            var a=h.absolutePos(O);var b=h.absolutePos(O.offset(4,0,0));
            var endpoints=List.of(new ConduitTopology.Endpoint(a,resource,ConduitTopology.Role.PRODUCER),new ConduitTopology.Endpoint(b,resource,ConduitTopology.Role.CONSUMER));
            h.assertTrue(ConduitTopology.compile(h.getLevel(),a,b,endpoints).reachable(a,resource).equals(List.of(b)),"Shell identity changed normal resource connectivity");
            h.assertTrue(ConduitTopology.compile(h.getLevel(),a,h.absolutePos(O.offset(2,0,0)),endpoints).reachable(a,resource).isEmpty(),"Resource topology escaped machine bounds");
        }
        h.succeed();
    }
}
