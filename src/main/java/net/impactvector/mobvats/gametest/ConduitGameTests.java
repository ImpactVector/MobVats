package net.impactvector.mobvats.gametest;
import java.util.*;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CowBalance;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class ConduitGameTests {
    private static final BlockPos O=new BlockPos(1,1,1),HEAD=O.offset(2,2,2),TOOL=O.offset(2,1,4),ACTOR_TAP=O.offset(2,0,4),INPUT=O.offset(1,0,3),OUTPUT=O.offset(1,0,2),GENERIC=O.offset(3,1,0),FIN=O.offset(0,1,3),FOUT=O.offset(0,1,2);
    private static void conduit(GameTestHelper h,BlockPos pos,ConduitResource resource,ConduitCladding cladding,ConduitMode mode){conduit(h,pos,resource,cladding,ConduitShell.VAT,mode);}
    private static void conduit(GameTestHelper h,BlockPos pos,ConduitResource resource,ConduitCladding cladding,ConduitShell shell,ConduitMode mode){h.setBlock(pos,ConduitContent.state(resource,cladding,shell).setValue(ConduitBlock.MODE,mode));}
    static MultiblockVat vat(GameTestHelper h){return ((VatHeadEntity)h.getBlockEntity(HEAD)).getMultiblockController().orElseThrow();}
    private static void rig(GameTestHelper h){VatGameTests.build(h,5);((VatHeadEntity)h.getBlockEntity(HEAD)).encode(ItemEssence.COW);}
    @GameTest(template="empty",timeoutTicks=110)
    public static void lastConduitItemCompletesPreviouslyUnformedVat(GameTestHelper h){
        rig(h);var last=O.offset(0,1,2);h.setBlock(last,Blocks.AIR);
        h.runAtTickTime(30,()->{
            h.assertTrue(!vat(h).isAssembled(),"Incomplete vat assembled");
            ConduitItemGameTests.place(h,last,ConduitResource.ITEM,ConduitCladding.GLASS,ConduitMode.TAP);
        });
        h.runAtTickTime(50,()->h.succeedWhen(()->h.assertTrue(vat(h).isAssembled(),"Last conduit did not complete vat")));
    }
    @GameTest(template="empty",timeoutTicks=130)
    public static void lateConduitItemsJoinSeparatedVatPartsAndCompleteShell(GameTestHelper h){
        rig(h);var gaps=new ArrayList<BlockPos>();var last=O.offset(2,3,1);
        for(int x=0;x<5;x++)for(int y=0;y<4;y++)for(int z=0;z<5;z++){
            var pos=O.offset(x,y,z);
            var entity=h.getLevel().getBlockEntity(h.absolutePos(pos));
            if(entity instanceof VatCasingEntity || entity instanceof VatGlassEntity){
                if(z==1){gaps.add(pos);h.setBlock(pos,Blocks.AIR);}
                else conduit(h,pos,ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TRUNK);
            }
        }
        h.runAtTickTime(30,()->{
            h.assertTrue(!vat(h).isAssembled(),"Incomplete passive shell assembled");
            h.assertTrue(((AbstractVatEntity)h.getBlockEntity(O.offset(1,1,0))).getMultiblockController().orElseThrow()!=vat(h),"Regression setup did not separate shell controller from specimen parts");
            for(var pos:gaps)if(!pos.equals(last))ConduitItemGameTests.place(h,pos,ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TRUNK);
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(!vat(h).isAssembled(),"Shell assembled with final conduit missing");
            ConduitItemGameTests.place(h,last,ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TAP);
        });
        h.runAtTickTime(80,()->h.succeedWhen(()->h.assertTrue(vat(h).isAssembled(),"Late passive bridges did not merge parts and complete vat: parts="+vat(h).getPartsCount()+" error="+vat(h).getLastError().map(e->e.getChatMessage().getString()))));
    }
    private static VatItemPortEntity item(GameTestHelper h,BlockPos pos){return (VatItemPortEntity)h.getBlockEntity(pos);}
    private static void itemPort(GameTestHelper h,BlockPos pos,IoDirection io){h.setBlock(pos,Content.state(VatPartType.ItemPort));item(h,pos).setIoDirection(io);}
    private static void fluidPort(GameTestHelper h,BlockPos pos,IoDirection io){h.setBlock(pos,Content.state(VatPartType.FluidPort));((VatFluidPortEntity)h.getBlockEntity(pos)).setIoDirection(io);}
    static void routedRig(GameTestHelper h){
        rig(h);var buffer=O.offset(2,3,4);h.setBlock(buffer,FluidBufferContent.state(ConduitCladding.CASING));((FluidBufferEntity)h.getBlockEntity(buffer)).filter("minecraft:milk");h.setBlock(TOOL,Content.state(VatPartType.ToolPort));itemPort(h,INPUT,IoDirection.Input);itemPort(h,OUTPUT,IoDirection.Output);itemPort(h,GENERIC,IoDirection.Output);
        for(var pos:List.of(ACTOR_TAP,O.offset(1,0,4),O.offset(0,0,4),O.offset(0,0,3),O.offset(0,0,2)))
            conduit(h,pos,ConduitResource.ITEM,ConduitCladding.CASING,(pos.equals(ACTOR_TAP)||pos.equals(O.offset(0,0,3))||pos.equals(O.offset(0,0,2)))?ConduitMode.TAP:ConduitMode.TRUNK);
        fluidPort(h,FIN,IoDirection.Input);fluidPort(h,FOUT,IoDirection.Output);
        for(var pos:List.of(O.offset(2,2,4),O.offset(1,2,4),O.offset(0,2,4),O.offset(0,2,3),O.offset(0,2,2)))
            conduit(h,pos,ConduitResource.FLUID,pos.equals(O.offset(0,2,4))?ConduitCladding.CASING:ConduitCladding.GLASS,pos.equals(O.offset(1,2,4))||pos.equals(O.offset(0,2,4))?ConduitMode.TRUNK:ConduitMode.TAP);
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void allSixConduitsFormWithoutEntitiesOrCapabilities(GameTestHelper h){
        rig(h);
        conduit(h,O.offset(1,1,1),ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TRUNK);
        conduit(h,O.offset(3,1,1),ConduitResource.FLUID,ConduitCladding.BARE,ConduitMode.TAP);
        conduit(h,O.offset(0,1,2),ConduitResource.ITEM,ConduitCladding.GLASS,ConduitMode.TAP);
        conduit(h,O.offset(4,1,2),ConduitResource.FLUID,ConduitCladding.GLASS,ConduitMode.TRUNK);
        conduit(h,O,ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TRUNK);
        conduit(h,O.offset(4,3,4),ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TAP);
        conduit(h,O.offset(0,0,2),ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TRUNK);
        h.runAtTickTime(30,()->{
            h.assertTrue(vat(h).isAssembled(),"Mixed conduit cladding invalidated vat");
            for(var pos:List.of(O.offset(1,1,1),O.offset(3,1,1),O.offset(0,1,2),O.offset(4,1,2),O,O.offset(4,3,4))){
                h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(pos))==null,"Conduit unexpectedly has BE");
                h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(pos),Direction.UP)==null && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(pos),Direction.UP)==null,"Conduit exposed resource capability");
            }
            h.assertTrue(vat(h).getTopology().conduits().components().size()>=4,"Interior/wall components missing");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void shellCladdingValidationAndChangeRecovery(GameTestHelper h){
        rig(h);var wall=O.offset(0,1,2);
        h.runAtTickTime(30,()->{h.assertTrue(vat(h).isAssembled(),"Baseline not formed");conduit(h,wall,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TRUNK);});
        h.runAtTickTime(50,()->{h.assertTrue(!vat(h).isAssembled(),"Bare conduit accepted as glass wall");conduit(h,wall,ConduitResource.ITEM,ConduitCladding.GLASS,ConduitMode.TRUNK);});
        h.runAtTickTime(70,()->{h.assertTrue(vat(h).isAssembled(),"Glass replacement did not recover");conduit(h,O,ConduitResource.FLUID,ConduitCladding.GLASS,ConduitMode.TRUNK);});
        h.runAtTickTime(90,()->{h.assertTrue(!vat(h).isAssembled(),"Glass-clad corner accepted");conduit(h,O,ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TAP);});
        h.runAtTickTime(110,()->{h.assertTrue(vat(h).isAssembled(),"Casing-clad corner did not recover");h.setBlock(wall,Blocks.AIR);});
        h.runAtTickTime(130,()->{h.assertTrue(!vat(h).isAssembled(),"Removed BE-less wall remained assembled");conduit(h,wall,ConduitResource.ITEM,ConduitCladding.GLASS,ConduitMode.TRUNK);});
        h.runAtTickTime(145,()->h.succeedWhen(()->h.assertTrue(vat(h).isAssembled(),"Replacement BE-less wall did not recover: parts="+vat(h).getPartsCount()+" error="+vat(h).getLastError().map(e->e.getChatMessage().getString()))));
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void claddingUsesExactVatAndMixerPositionRules(GameTestHelper h){
        var min=h.absolutePos(O);var max=h.absolutePos(O.offset(4,3,4));
        var positions=List.of(O,O.offset(0,1,0),O.offset(0,0,2),O.offset(0,1,2),O.offset(2,3,2),O.offset(2,0,2),O.offset(2,1,2));
        boolean[] vatGlass={false,true,false,true,true,false,false},mixerGlass={true,true,true,true,true,true,false};
        for(int i=0;i<positions.size();i++){
            var pos=positions.get(i);conduit(h,pos,ConduitResource.ITEM,ConduitCladding.GLASS,ConduitMode.TRUNK);
            h.assertTrue(ConduitStructure.allows(h.getLevel(),h.absolutePos(pos),min,max,false)==vatGlass[i],"Vat glass position mismatch "+pos);
            h.setBlock(pos,ConduitContent.state(ConduitResource.ITEM,ConduitCladding.GLASS,ConduitShell.MIXER));
            h.assertTrue(ConduitStructure.allows(h.getLevel(),h.absolutePos(pos),min,max,true)==mixerGlass[i],"Mixer glass position mismatch "+pos);
            conduit(h,pos,ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TAP);
            h.assertTrue(ConduitStructure.allows(h.getLevel(),h.absolutePos(pos),min,max,false)==(i!=6),"Casing placement mismatch "+pos);
            conduit(h,pos,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TAP);
            h.assertTrue(ConduitStructure.allows(h.getLevel(),h.absolutePos(pos),min,max,false)==(i==6),"Bare placement mismatch "+pos);
        }
        h.succeed();
    }
    private static ConduitTopology.Endpoint endpoint(GameTestHelper h,BlockPos pos,ConduitResource resource,ConduitTopology.Role... role){return new ConduitTopology.Endpoint(h.absolutePos(pos),resource,role);}
    @GameTest(template="empty",timeoutTicks=10)
    public static void trunkTapResourceAndActorAttachmentRules(GameTestHelper h){
        var actor=O;var first=O.east();var second=first.east();var input=second.east();
        conduit(h,first,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TRUNK);conduit(h,second,ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TRUNK);
        var endpoints=List.of(endpoint(h,actor,ConduitResource.ITEM,ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER),endpoint(h,input,ConduitResource.ITEM,ConduitTopology.Role.STORE),endpoint(h,input,ConduitResource.FLUID,ConduitTopology.Role.CONSUMER));
        var graph=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(input),endpoints);
        h.assertTrue(graph.components().size()==1 && graph.components().getFirst().trunks()==2 && graph.components().getFirst().inputs().isEmpty() && graph.components().getFirst().positions().isEmpty(),"TRUNK attached endpoints or cladding broke graph");
        conduit(h,second,ConduitResource.ITEM,ConduitCladding.GLASS,ConduitMode.TAP);
        graph=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(input),endpoints);
        h.assertTrue(graph.components().getFirst().inputs().equals(List.of(h.absolutePos(input))) && graph.expand(h.absolutePos(actor),ConduitResource.ITEM,List.of(),List.of()).inputs().isEmpty(),"Endpoint TAP failed or actor TRUNK attached");
        conduit(h,first,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TAP);
        graph=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(input),endpoints);
        h.assertTrue(graph.expand(h.absolutePos(actor),ConduitResource.ITEM,List.of(),List.of()).inputs().equals(List.of(h.absolutePos(input))) && graph.components().getFirst().outputs().isEmpty(),"Actor TAP or resource compatibility wrong");
        conduit(h,second,ConduitResource.FLUID,ConduitCladding.CASING,ConduitMode.TAP);
        graph=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(input),endpoints);
        h.assertTrue(graph.components().size()==2 && graph.expand(h.absolutePos(actor),ConduitResource.ITEM,List.of(),List.of()).inputs().isEmpty(),"Item and Fluid cross-connected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void loopsBranchesDeduplicateSortAndStayWithinMachineBounds(GameTestHelper h){
        var nodes=List.of(O.offset(1,1,1),O.offset(2,1,1),O.offset(2,1,2),O.offset(1,1,2),O.offset(1,2,1),O.offset(0,1,1));
        for(var pos:nodes)conduit(h,pos,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TAP);
        var actor=O.offset(0,1,2);var duplicate=O.offset(1,2,2);var second=O.offset(2,2,1);var external=O.offset(-1,1,1);
        conduit(h,external,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TAP);
        var endpoints=new ArrayList<>(List.of(endpoint(h,actor,ConduitResource.ITEM,ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER),endpoint(h,duplicate,ConduitResource.ITEM,ConduitTopology.Role.CONSUMER),endpoint(h,duplicate,ConduitResource.ITEM,ConduitTopology.Role.CONSUMER),endpoint(h,second,ConduitResource.ITEM,ConduitTopology.Role.CONSUMER),endpoint(h,external.west(),ConduitResource.ITEM,ConduitTopology.Role.STORE)));
        var graph=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(O.offset(3,3,3)),endpoints);
        Collections.reverse(endpoints);var reordered=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(O.offset(3,3,3)),endpoints);
        h.assertTrue(graph.equals(reordered) && graph.components().size()==1 && graph.components().getFirst().taps()==6,"Loop/branch unstable or escaped bounds");
        var resolved=graph.expand(h.absolutePos(actor),ConduitResource.ITEM,List.of(),List.of(h.absolutePos(second)));
        h.assertTrue(resolved.outputs().equals(List.of(h.absolutePos(duplicate),h.absolutePos(second))) && resolved.inputs().isEmpty(),"Duplicate exposure or direct/conduit ordering wrong");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=125)
    public static void remoteActorEndpointsLoadExportAndReserveKillOutput(GameTestHelper h){
        routedRig(h);
        h.runAtTickTime(30,()->{
            var c=vat(h);h.assertTrue(c.isAssembled(),"Routed vat did not form");var route=c.getTopology().toolLoading().getFirst();
            h.assertTrue(route.itemInputs().equals(List.of(h.absolutePos(INPUT))) && route.itemOutputs().equals(List.of(h.absolutePos(OUTPUT))) && route.fluidInputs().equals(List.of(h.absolutePos(FIN))) && route.fluidOutputs().equals(List.of(h.absolutePos(FOUT))),"Actor endpoint expansion missing");
            h.assertTrue(c.getTopology().reservedItemOutputs().equals(List.of(h.absolutePos(OUTPUT))) && c.getTopology().genericItemOutputs().equals(List.of(h.absolutePos(GENERIC))),"Conduit output classification wrong");
            h.assertTrue(c.getTopology().itemContacts().isEmpty(),"Conduit generalized item-to-cow feeding");
            c.produceMilk();item(h,INPUT).staging().setStackInSlot(0,new ItemStack(Items.BUCKET));((VatToolPortEntity)h.getBlockEntity(TOOL)).results().store(List.of(new ItemStack(Items.BREAD,5)));
        });
        h.runAtTickTime(65,()->{
            h.assertTrue(item(h,OUTPUT).staging().getStackInSlot(0).is(Items.BREAD) && item(h,OUTPUT).staging().getStackInSlot(0).getCount()==5 && item(h,GENERIC).staging().getStackInSlot(0).isEmpty(),"Tool results escaped local route");
            item(h,OUTPUT).staging().setStackInSlot(0,ItemStack.EMPTY);
        });
        h.runAtTickTime(105,()->{
            h.assertTrue(item(h,INPUT).staging().getStackInSlot(0).isEmpty() && item(h,OUTPUT).staging().getStackInSlot(0).is(Items.MILK_BUCKET),"Remote bucket transform failed after local backlog cleared");
            ((VatToolPortEntity)h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
            h.assertTrue(vat(h).getTopology().actors().stream().filter(r->r.actor().equals(h.absolutePos(TOOL))).allMatch(r->r.itemOutputs().equals(List.of(h.absolutePos(OUTPUT)))),"Active Tool lost conduit route");h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void modeStateChangeAndItemReplacementRecompileAndReloadPreservesMode(GameTestHelper h){
        routedRig(h);final long[] revision={0};
        h.runAtTickTime(30,()->{
            revision[0]=vat(h).getTopologyRevision();conduit(h,ACTOR_TAP,ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TRUNK);
            var pos=h.absolutePos(ACTOR_TAP);var state=h.getLevel().getBlockState(pos);
            var saved=NbtUtils.writeBlockState(state);var restored=NbtUtils.readBlockState(h.getLevel().holderLookup(Registries.BLOCK),saved);
            h.assertTrue(restored.equals(state) && restored.getValue(ConduitBlock.MODE)==ConduitMode.TRUNK,"Mode NBT lost state");
        });
        h.runAtTickTime(50,()->{
            var c=vat(h);h.assertTrue(c.isAssembled() && c.getTopologyRevision()>revision[0] && c.getTopology().toolLoading().getFirst().itemInputs().isEmpty(),"Mode switch did not recompile");
            ((VatToolPortEntity)h.getBlockEntity(TOOL)).results().store(List.of(new ItemStack(Items.BREAD,2)));revision[0]=c.getTopologyRevision();
        });
        h.runAtTickTime(75,()->{h.assertTrue(item(h,OUTPUT).staging().getStackInSlot(0).isEmpty() && ((VatToolPortEntity)h.getBlockEntity(TOOL)).results().itemCount()==2,"Disconnected Tool output leaked to Drain");h.getLevel().destroyBlock(h.absolutePos(ACTOR_TAP),false);ConduitItemGameTests.place(h,ACTOR_TAP,ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TAP);});
        h.runAtTickTime(95,()->{h.assertTrue(vat(h).getTopologyRevision()>revision[0] && vat(h).getTopology().reservedItemOutputs().contains(h.absolutePos(OUTPUT)),"TAP restoration did not reserve route");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void conduitCannotReplaceToolSpecimenReachOrFeedContact(GameTestHelper h){
        routedRig(h);h.setBlock(TOOL,Content.state(VatPartType.Glass));var invalid=O.offset(1,1,4);h.setBlock(invalid,Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity)h.getBlockEntity(invalid)).setHeldItem(new ItemStack(Items.IRON_SWORD));conduit(h,O.offset(1,0,4),ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TAP);
        h.runAtTickTime(30,()->{var c=vat(h);h.assertTrue(c.isAssembled() && c.getTopology().actors().stream().noneMatch(r->h.getLevel().getBlockEntity(r.actor()) instanceof VatToolPortEntity) && c.getTopology().toolLoading().isEmpty(),"Conduit granted Tool remote specimen reach");
            h.assertTrue(c.getTopology().itemContacts().isEmpty(),"Rejected actor reserved output or conduit fed cow");
            ((VatToolPortEntity)h.getBlockEntity(invalid)).results().store(List.of(new ItemStack(Items.BREAD)));
        });
        h.runAtTickTime(55,()->{h.assertTrue(item(h,OUTPUT).staging().getStackInSlot(0).is(Items.BREAD),"Invalid physical actor blocked generic output");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void blenderStillWinsAndDisplacedToolStillReservesConduitOutput(GameTestHelper h){
        routedRig(h);h.setBlock(O.offset(2,1,0),Content.state(VatPartType.Blender));((VatToolPortEntity)h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30,()->{
            var c=vat(h);h.assertTrue(c.isAssembled() && c.getTopology().actors().stream().anyMatch(r->r.actor().equals(h.absolutePos(O.offset(2,1,0))) && r.use().equals(net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveUses.BLEND) && r.itemOutputs().isEmpty()),"Conduit changed Blender priority/item compatibility");
            h.assertTrue(c.getTopology().reservedItemOutputs().contains(h.absolutePos(OUTPUT)),"Displaced valid Sword lost reserved conduit endpoint");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=90)
    public static void sumpConduitsPreservePhysicalOwnership(GameTestHelper h){
        rig(h);var output=O.offset(2,0,1);fluidPort(h,output,IoDirection.Output);
        h.runAtTickTime(30,()->{var c=vat(h);var sump=c.getAssemblies().getFirst().drain();h.assertTrue(c.isAssembled() && c.getTopology().actors().stream().anyMatch(r->r.actor().equals(sump.getBlockPos()) && r.fluidOutputs().contains(h.absolutePos(output))),"Sump lost adjacent bus output");h.assertTrue(c.getTopology().actors().stream().filter(r->r.use().equals(net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveUses.DRAIN)).allMatch(r->r.actor().equals(sump.getBlockPos())),"Bus changed specimen owner");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void packedMinimumMixerKeepsStructuralBoundsButRejectsPhysicalIntersections(GameTestHelper h){
        var controller=O.offset(0,1,1);var input=O.offset(1,0,1);var output=O.offset(0,2,1);var item=O.offset(1,1,0);
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)if(x==0 || x==2 || y==0 || y==3 || z==0 || z==2)conduit(h,O.offset(x,y,z),ConduitResource.ITEM,ConduitCladding.CASING,ConduitShell.MIXER,ConduitMode.TAP);
        h.setBlock(controller,MixerContent.state(MixerPartType.Controller));h.setBlock(input,MixerContent.state(MixerPartType.FluidPort));h.setBlock(output,MixerContent.state(MixerPartType.FluidPort).setValue(net.impactvector.mobvats.gamecontent.multiblock.common.PortVisualState.OUTPUT,true));h.setBlock(item,MixerContent.state(MixerPartType.ItemPort));
        h.setBlock(O.offset(1,3,1),MixerContent.state(MixerPartType.Impeller));h.setBlock(O.offset(1,2,1),MixerContent.state(MixerPartType.Shaft));
        for(var pos:List.of(O.offset(0,0,1),O.offset(0,0,0),O.offset(0,1,0),O.offset(0,2,0),O.offset(0,3,0),O.offset(0,3,1)))conduit(h,pos,ConduitResource.FLUID,ConduitCladding.CASING,ConduitShell.MIXER,ConduitMode.TAP);
        conduit(h,O.offset(2,1,1),ConduitResource.ITEM,ConduitCladding.GLASS,ConduitShell.MIXER,ConduitMode.TAP);
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(controller)).getMultiblockController().orElseThrow();
            h.assertTrue(!c.isAssembled() && c.getLastError().orElseThrow().getChatMessage().getString().contains("intersects") && c.getBoundingBox().getMin().equals(h.absolutePos(O)) && c.getBoundingBox().getMax().equals(h.absolutePos(O.offset(2,3,2))),"Packed shell changed structural bounds or ignored clearance");
            var graph=ConduitTopology.compile(h.getLevel(),h.absolutePos(O),h.absolutePos(O.offset(2,3,2)),List.of(
                new ConduitTopology.Endpoint(h.absolutePos(controller),ConduitResource.FLUID,ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER),
                new ConduitTopology.Endpoint(h.absolutePos(controller),ConduitResource.ITEM,ConduitTopology.Role.PRODUCER,ConduitTopology.Role.CONSUMER),
                new ConduitTopology.Endpoint(h.absolutePos(input),ConduitResource.FLUID,ConduitTopology.Role.STORE),
                new ConduitTopology.Endpoint(h.absolutePos(output),ConduitResource.FLUID,ConduitTopology.Role.CONSUMER),
                new ConduitTopology.Endpoint(h.absolutePos(item),ConduitResource.ITEM,ConduitTopology.Role.STORE)));
            h.assertTrue(graph.expand(h.absolutePos(controller),ConduitResource.FLUID,List.of(),List.of()).inputs().equals(List.of(h.absolutePos(input))) && graph.expand(h.absolutePos(controller),ConduitResource.FLUID,List.of(),List.of()).outputs().equals(List.of(h.absolutePos(output))),"Mixer Fluid endpoints missing");
            h.assertTrue(graph.expand(h.absolutePos(controller),ConduitResource.ITEM,List.of(),List.of()).inputs().equals(List.of(h.absolutePos(item))),"Mixer plant endpoint missing");
            h.assertTrue(c.state().fluids(true).fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==0,"Conduit-only Mixer granted invisible storage");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void minimumVatWithPassiveShellPreservesStrictSpecimenValidation(GameTestHelper h){
        VatGameTests.build(h,3);var head=O.offset(1,2,1);((VatHeadEntity)h.getBlockEntity(head)).encode(ItemEssence.COW);
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++){
            var pos=O.offset(x,y,z);if(h.getBlockEntity(pos) instanceof VatCasingEntity || h.getBlockEntity(pos) instanceof VatGlassEntity)
                conduit(h,pos,ConduitResource.ITEM,ConduitCladding.CASING,ConduitMode.TRUNK);
        }
        h.runAtTickTime(35,()->{
            var c=((VatHeadEntity)h.getBlockEntity(head)).getMultiblockController().orElseThrow();
            h.assertTrue(c.isAssembled() && c.getPartsCount()==5 && c.getBoundingBox().getMin().equals(h.absolutePos(O)) && c.getBoundingBox().getMax().equals(h.absolutePos(O.offset(2,3,2))),"Minimum passive-shell vat did not retain full structure");
            h.setBlock(O.offset(1,1,1),Blocks.AIR);
        });
        h.runAtTickTime(60,()->{h.assertTrue(!((VatHeadEntity)h.getBlockEntity(head)).isMachineAssembled(),"Passive shell weakened required Flesh validation");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=50)
    public static void solidLosStillRejectsConduitConnectedActors(GameTestHelper h){
        routedRig(h);((VatToolPortEntity)h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.IRON_SWORD));
        h.runAtTickTime(30,()->{
            var c=vat(h);var assemblies=c.getAssemblies();var ports=List.<AbstractVatEntity>of((VatToolPortEntity)h.getBlockEntity(TOOL),item(h,INPUT),item(h,OUTPUT),item(h,GENERIC));
            h.assertTrue(!c.getTopology().actors().isEmpty(),"Baseline sword route absent");
            var obstruction=O.offset(2,1,3);h.setBlock(obstruction,Blocks.STONE);
            var blocked=VatTopologyCompiler.compile(h.getLevel(),c.getBoundingBox().getMin(),c.getBoundingBox().getMax(),assemblies,ports);
            h.assertTrue(blocked.actors().isEmpty() && blocked.reservedItemOutputs().isEmpty(),"Solid LOS bypassed through conduit");
            conduit(h,obstruction,ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TAP);
            var physical=VatTopologyCompiler.compile(h.getLevel(),c.getBoundingBox().getMin(),c.getBoundingBox().getMax(),assemblies,ports);
            h.assertTrue(physical.actors().isEmpty() && physical.physical().blocked().stream().anyMatch(b->b.kind()==VatPhysicalState.Kind.TOOL),"6C failed to replace deferred placeholder with physical obstruction");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=65)
    public static void outsideRunCannotJoinVatToAdjacentMixerEndpoints(GameTestHelper h){
        routedRig(h);
        for(var pos:List.of(O.offset(3,2,4),O.offset(4,2,4),O.offset(4,2,3),O.offset(4,2,2)))conduit(h,pos,ConduitResource.FLUID,pos.equals(O.offset(4,2,4))?ConduitCladding.CASING:ConduitCladding.GLASS,ConduitMode.TAP);
        var m=O.offset(6,0,1);var controller=m.offset(2,1,1);var input=m.offset(0,2,1);
        for(int x=0;x<3;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)if(x==0 || x==2 || y==0 || y==3 || z==0 || z==2)h.setBlock(m.offset(x,y,z),MixerContent.state(MixerPartType.Casing));
        h.setBlock(controller,MixerContent.state(MixerPartType.Controller));h.setBlock(input,MixerContent.state(MixerPartType.FluidPort));
        h.setBlock(m.offset(1,3,1),MixerContent.state(MixerPartType.Impeller));h.setBlock(m.offset(1,2,1),MixerContent.state(MixerPartType.Shaft));
        conduit(h,m.offset(0,1,0),ConduitResource.FLUID,ConduitCladding.GLASS,ConduitShell.MIXER,ConduitMode.TAP);
        conduit(h,O.offset(5,2,2),ConduitResource.FLUID,ConduitCladding.BARE,ConduitMode.TAP);
        h.runAtTickTime(35,()->{
            var c=vat(h);var mixer=((MixerControllerEntity)h.getBlockEntity(controller)).getMultiblockController().orElseThrow();
            h.assertTrue(c.isAssembled() && mixer.isAssembled(),"Separate containing machines did not assemble");
            h.assertTrue(c.getTopology().conduits().components().stream().noneMatch(component->component.inputs().contains(h.absolutePos(input))),"Vat conduit attached Mixer endpoint across machine bounds");
            h.assertTrue(mixer.conduitTopology().components().stream().noneMatch(component->component.inputs().contains(h.absolutePos(FIN)) || component.outputs().contains(h.absolutePos(FOUT))),"Mixer attached Vat endpoints");h.succeed();
        });
    }

}
