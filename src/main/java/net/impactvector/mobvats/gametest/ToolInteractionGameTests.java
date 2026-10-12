package net.impactvector.mobvats.gametest;

import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class ToolInteractionGameTests {
    private static final ResourceLocation GENERIC=ResourceLocation.parse("mobvats:entity_item_interaction"),CUSTOM=ResourceLocation.parse("mobvats:test_interaction");
    private static final BlockPos O=new BlockPos(1,1,1),HEAD=O.offset(2,2,2),TOOL=O.offset(2,1,4),FEED=O.offset(2,1,0);
    private static final BlockPos SOURCE=TOOL.west();
    static {
        CapturedSpecimen.registerInteractionStateField("TestInteractionCount");
        ToolInteractionExecutorRegistry.register(CUSTOM,(c,s,d)-> {
            var tool=c.tool(c.routes(d.actor_use()).getFirst());
            var before=c.assembly().head().specimenState();var after=before.copy();after.putInt("TestInteractionCount",before.getInt("TestInteractionCount")+1);
            var committed=new ProcessTransaction().add(ProcessOutput.buffered(tool.results(),List.of(new ItemStack(Items.DIAMOND))))
                .add(ProcessTransaction.tool(tool,new ItemStack(Items.BOWL)))
                .add(EntityItemInteractionExecutor.specimenStep(c.assembly().head(),before,after)).commit();
            return committed?ProcessResult.committed(true):ProcessResult.of(ProcessResult.Reason.OUTPUT_BLOCKED);
        });
    }
    private static ToolInteractionDefinition interaction(String executor,String input) {
        return new ToolInteractionDefinition("mobvats:test_operation",executor,List.of(input),List.of(),"mobvats:test_use",StageRequirement.ANY,20,30,"retain",null);
    }
    private static PassiveMobDefinitionRegistry.Loaded snapshot() {
        return new PassiveMobDefinitionRegistry.Loaded(PassiveMobDefinitionRegistry.definitions(),Map.of(),List.of());
    }
    private static void publish(PassiveMobDefinition definition) {
        var all=new HashMap<>(PassiveMobDefinitionRegistry.definitions());all.put(definition.identity(),definition);
        PassiveMobDefinitionRegistry.publish(new PassiveMobDefinitionRegistry.Loaded(Map.copyOf(all),Map.of(),List.of()));
    }
    private static void rig(GameTestHelper h) {
        VatGameTests.build(h,5);((VatHeadEntity)h.getBlockEntity(HEAD)).encode(ItemEssence.COW);
        h.setBlock(TOOL,Content.state(VatPartType.ToolPort));h.setBlock(FEED,Content.state(VatPartType.ItemPort));
        h.setBlock(SOURCE,Content.state(VatPartType.ItemPort));
        Slice11Fixtures.vatStorage(h,O,5,5);Slice11Fixtures.feedFrom(h,FEED,FEED.south());
    }
    private static VatHeadEntity head(GameTestHelper h) {return (VatHeadEntity)h.getBlockEntity(HEAD);}
    private static VatToolPortEntity tool(GameTestHelper h) {return (VatToolPortEntity)h.getBlockEntity(TOOL);}
    private static ProcessContext context(GameTestHelper h) {
        var vat=head(h).getMultiblockController().orElseThrow();vat.rebuildTopology();
        return new ProcessContext(vat.processEngine(),vat.getAssemblies().getFirst(),new SpecimenProcessState(),MobProcessRegistry.DEFAULT.resolve(ItemEssence.COW));
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void registeredLookupAndDuplicateRejection(GameTestHelper h) {
        h.assertTrue(ToolInteractionExecutorRegistry.require(GENERIC) instanceof EntityItemInteractionExecutor && ToolInteractionExecutorRegistry.require(CUSTOM)!=null,"Executor lookup failed");
        boolean rejected=false;try {ToolInteractionExecutorRegistry.register(CUSTOM,(c,s,d)->ProcessResult.committed(false));}catch(IllegalArgumentException expected) {rejected=true;}
        h.assertTrue(rejected,"Duplicate executor replaced a code adapter");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void unknownExecutorAndArbitraryMethodRejectWholeFile(GameTestHelper h) throws Exception {
        Files.createDirectories(Path.of("build"));var dir=Files.createTempDirectory(Path.of("build"),"interaction-definitions-");
        var base=PassiveDefaults.data().get("minecraft:cow");
        var bad=base.withToolInteractions(List.of(interaction("mobvats:unknown_executor","minecraft:bread")));
        Files.writeString(dir.resolve("10_unknown.json"),PassiveMobDefinitionRegistry.JSON.toJson(Map.of(base.entity(),bad)));
        var root=PassiveMobDefinitionRegistry.JSON.toJsonTree(Map.of(base.entity(),base.withToolInteractions(List.of(interaction(GENERIC.toString(),"minecraft:bread"))))).getAsJsonObject();
        root.getAsJsonObject(base.entity()).getAsJsonArray("interactions").get(0).getAsJsonObject().addProperty("method","Sniffer.dig");
        Files.writeString(dir.resolve("20_method.json"),root.toString());
        var loaded=PassiveMobDefinitionRegistry.load(dir,s->{});
        h.assertTrue(loaded.errors().stream().anyMatch(e->e.contains("Unknown interaction executor")) && loaded.errors().stream().anyMatch(e->e.contains("unknown field method"))
            && loaded.definitions().get(ItemEssence.COW).equals(base),"Unknown executor/arbitrary method partly replaced the definition");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void authoredDefinitionsRoundTripAndEmptyDisablesLegacy(GameTestHelper h) {
        var base=PassiveDefaults.data().get("minecraft:cow");var d=base.withToolInteractions(List.of(interaction(GENERIC.toString(),"minecraft:bucket")));
        var loaded=PassiveMobDefinitionRegistry.JSON.fromJson(PassiveMobDefinitionRegistry.JSON.toJson(d),PassiveMobDefinition.class);
        h.assertTrue(loaded.toolInteractions().equals(d.toolInteractions()) && base.withToolInteractions(List.of()).toolInteractions().isEmpty(),"Interaction data did not round trip or empty list re-enabled legacy categories");
        var legacy=new PassiveMobDefinition(base.entity(),base.feed(),base.juvenile_growth_ticks(),base.secondary_outputs(),base.harvest(),base.culture_slurry(),base.feeding_slurry());
        h.assertTrue(legacy.toolInteractions().stream().anyMatch(i->i.executor().equals("mobvats:weapon_harvest")),"Legacy definitions lost harvest migration");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void normalInteractionCapturesContainerReplacement(GameTestHelper h) {
        var plan=EntityItemInteractionExecutor.plan(h.getLevel(),l->EntityType.COW.create(l),new ItemStack(Items.BUCKET),Vec3.ZERO);
        h.assertTrue(plan!=null && plan.held().is(Items.MILK_BUCKET) && plan.drops().isEmpty(),"Normal entity interaction lost bucket replacement");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void realSheepStateDropsAndDamageRemainDetached(GameTestHelper h) {
        var held=new ItemStack(Items.SHEARS);
        var plan=EntityItemInteractionExecutor.plan(h.getLevel(),l->{var sheep=EntityType.SHEEP.create(l);sheep.setColor(DyeColor.RED);return sheep;},held,Vec3.ZERO);
        h.assertTrue(plan!=null && plan.specimen().getBoolean("Sheared") && plan.specimen().getByte("Color")==DyeColor.RED.getId()
            && plan.held().getDamageValue()==1 && held.getDamageValue()==0 && plan.drops().stream().allMatch(s->s.is(Items.RED_WOOL)) && !plan.drops().isEmpty(),"Real shearing lost state, copied durability or drops");h.succeed();
    }
    /** Alternate IShearable contract on a cow subtype. No executor species table is involved. */
    private static final class AlternateCow extends Cow implements IShearable {
        private int count;
        AlternateCow(Level level) {super(EntityType.COW,level);}
        public boolean isShearable(Player player,ItemStack held,Level level,BlockPos pos) {return count==0;}
        public List<ItemStack> onSheared(Player player,ItemStack held,Level level,BlockPos pos) {count++;return List.of(new ItemStack(Items.LEATHER,2));}
        public void addAdditionalSaveData(CompoundTag tag) {super.addAdditionalSaveData(tag);tag.putInt("TestInteractionCount",count);}
        public void readAdditionalSaveData(CompoundTag tag) {super.readAdditionalSaveData(tag);count=tag.getInt("TestInteractionCount");}
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void alternateShearableAndRegisteredSpecimenField(GameTestHelper h) {
        var plan=EntityItemInteractionExecutor.plan(h.getLevel(),AlternateCow::new,new ItemStack(Items.SHEARS),Vec3.ZERO);
        h.assertTrue(plan!=null && plan.specimen().getInt("TestInteractionCount")==1 && plan.drops().size()==1 && plan.drops().getFirst().is(Items.LEATHER)
            && plan.drops().getFirst().getCount()==2 && plan.held().getDamageValue()==1,"Alternate IShearable interaction was not inherited");
        var again=EntityItemInteractionExecutor.plan(h.getLevel(),l->{var cow=new AlternateCow(l);CapturedSpecimen.apply(cow,plan.specimen());return cow;},plan.held(),Vec3.ZERO);
        h.assertTrue(again==null,"Reconstructed specimen lost registered state and repeated interaction");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void toolBreakageCaptured(GameTestHelper h) {
        var held=new ItemStack(Items.SHEARS);held.setDamageValue(held.getMaxDamage()-1);
        var plan=EntityItemInteractionExecutor.plan(h.getLevel(),l->EntityType.SHEEP.create(l),held,Vec3.ZERO);
        h.assertTrue(plan!=null && plan.held().isEmpty() && held.getCount()==1 && !plan.drops().isEmpty(),"Tool breakage lost output or mutated input");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void unsupportedWorldEffectsAndUnpersistedStateReject(GameTestHelper h) {
        var world=EntityItemInteractionExecutor.plan(h.getLevel(),l->new Cow(EntityType.COW,l) {
            public InteractionResult mobInteract(Player p,InteractionHand hand) {level().setBlockAndUpdate(BlockPos.ZERO,net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState());return InteractionResult.SUCCESS;}
        },new ItemStack(Items.STICK),Vec3.ZERO);
        var state=EntityItemInteractionExecutor.plan(h.getLevel(),l->new Cow(EntityType.COW,l) {
            private boolean changed;
            public InteractionResult mobInteract(Player p,InteractionHand hand) {changed=true;return InteractionResult.SUCCESS;}
            public void addAdditionalSaveData(CompoundTag tag) {super.addAdditionalSaveData(tag);tag.putBoolean("UnregisteredInteractionField",changed);}
        },new ItemStack(Items.STICK),Vec3.ZERO);
        h.assertTrue(world==null && state==null,"Unsupported mutation was silently accepted");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void customDefinitionSelectsAcquiresAndInvokesExecutor(GameTestHelper h) {
        rig(h);h.runAtTickTime(35,()-> {
            var original=snapshot();
            try {
                var d=PassiveMobDefinitionRegistry.resolve(ItemEssence.COW).withToolInteractions(List.of(interaction(CUSTOM.toString(),"minecraft:bread")));publish(d);
                tool(h).demandConfiguration().configure(List.of(new net.impactvector.mobvats.gamecontent.multiblock.conduit.DemandConfiguration.Option("mobvats:test_operation",true,List.of("minecraft:bread"),List.of())));
                ((VatItemPortEntity)h.getBlockEntity(SOURCE)).staging().setStackInSlot(0,new ItemStack(Items.BREAD));
                h.assertTrue(ToolInteractions.acquire(context(h),d).changed() && tool(h).heldItem().is(Items.BREAD),"Definition-driven acquisition rejected a non-tool item");
                var c=context(h);h.assertTrue(tool(h).uses(c.assembly()).equals(List.of("mobvats:test_use")),"Tool selection used a hard-coded category");
                var op=c.profile().definitions().stream().filter(p->p.id().contains("mobvats:test_operation")).findFirst().orElseThrow();
                var state=new ProcessState();
                h.assertTrue(op.execute(c,state).changed() && tool(h).heldItem().is(Items.BOWL) && tool(h).results().snapshot().getFirst().is(Items.DIAMOND)
                    && head(h).specimenState().getInt("TestInteractionCount")==1,"Custom executor was not invoked transactionally");
                h.assertTrue(op.execute(c,state).reason()==ProcessResult.Reason.RUNNING && head(h).specimenState().getInt("TestInteractionCount")==1
                    && ProcessState.load(state.save(h.getLevel().registryAccess()),h.getLevel().registryAccess()).data.equals(state.data),"Configured cooldown was ignored or not persisted");
                tool(h).setHeldItem(new ItemStack(Items.SHEARS));h.assertTrue(tool(h).uses(c.assembly()).isEmpty(),"Explicit definitions fell back to a legacy tool category");
            } finally {PassiveMobDefinitionRegistry.publish(original);}
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void specimenToolAndDropsRollbackAndStaleReservation(GameTestHelper h) {
        rig(h);h.runAtTickTime(35,()-> {
            var before=head(h).specimenState();var after=before.copy();after.putInt("TestInteractionCount",4);
            tool(h).setHeldItem(new ItemStack(Items.BUCKET));
            var tx=new ProcessTransaction().add(ProcessOutput.buffered(tool(h).results(),List.of(new ItemStack(Items.LEATHER))))
                .add(ProcessTransaction.tool(tool(h),new ItemStack(Items.MILK_BUCKET))).add(EntityItemInteractionExecutor.specimenStep(head(h),before,after))
                .add(new ProcessTransaction.Step(new Object(),()->true,()->false,()->{}));
            h.assertTrue(!tx.commit() && tool(h).heldItem().is(Items.BUCKET) && head(h).specimenState().equals(before) && tool(h).results().isEmpty(),"Rollback failed to restore specimen/tool/drops");
            var stale=new ProcessTransaction().add(EntityItemInteractionExecutor.specimenStep(head(h),before,after)).add(ProcessTransaction.tool(tool(h),ItemStack.EMPTY));
            var intervening=before.copy();intervening.putInt("TestInteractionCount",2);head(h).updateSpecimenState(intervening);
            h.assertTrue(!stale.commit() && head(h).specimenState().equals(intervening) && tool(h).heldItem().is(Items.BUCKET),"Stale specimen reservation committed");
            var replaced=new ProcessTransaction().add(EntityItemInteractionExecutor.specimenStep(head(h),intervening,after));
            head(h).encode(ResourceLocation.withDefaultNamespace("sheep"),head(h).geometry(),intervening);
            h.assertTrue(!replaced.commit() && head(h).specimenState().equals(intervening),"Re-encoded specimen accepted a stale interaction reservation");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void genericBlockedOutputLeavesSpecimenAndHeldUntouched(GameTestHelper h) {
        rig(h);h.runAtTickTime(35,()-> {
            var original=snapshot();
            try {
                var i=interaction(GENERIC.toString(),"minecraft:bucket");publish(PassiveMobDefinitionRegistry.resolve(ItemEssence.COW).withToolInteractions(List.of(i)));
                tool(h).setHeldItem(new ItemStack(Items.BUCKET));tool(h).results().store(List.of(new ItemStack(Items.STONE)));
                var before=head(h).specimenState();var state=new ProcessState();
                var executor=ToolInteractionExecutorRegistry.require(GENERIC);
                h.assertTrue(executor.execute(context(h),state,i).reason()==ProcessResult.Reason.OUTPUT_BLOCKED && tool(h).heldItem().is(Items.BUCKET)
                    && head(h).specimenState().equals(before) && state.data.isEmpty(),"Blocked interaction partly mutated resources");
                tool(h).results().clear();h.assertTrue(executor.execute(context(h),state,i).changed() && tool(h).heldItem().is(Items.MILK_BUCKET),"Unblocked interaction did not commit");
            } finally {PassiveMobDefinitionRegistry.publish(original);}
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void neoForgePublicInteractionHookIsInherited(GameTestHelper h) {
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract> listener=event->{
            if(event.getTarget() instanceof Cow) {
                if(!event.getItemStack().is(Items.SHEARS))event.getEntity().setItemInHand(event.getHand(),new ItemStack(Items.EMERALD));
                event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            var plan=EntityItemInteractionExecutor.plan(h.getLevel(),l->EntityType.COW.create(l),new ItemStack(Items.STICK),Vec3.ZERO);
            h.assertTrue(plan!=null && plan.held().is(Items.EMERALD),"NeoForge entity interaction hook was bypassed");
            h.assertTrue(ToolInteractionExecutorRegistry.require(GENERIC).supports(h.getLevel(),ItemEssence.COW,new ItemStack(Items.SHEARS)),"Authored interaction required a species/interface branch before consulting public hooks");
            var canceled=EntityItemInteractionExecutor.plan(h.getLevel(),l->EntityType.COW.create(l),new ItemStack(Items.SHEARS),Vec3.ZERO);
            h.assertTrue(canceled!=null && canceled.held().getDamageValue()==0 && canceled.specimen().getInt("TestInteractionCount")==0 && canceled.drops().isEmpty(),"Canceled item path damaged the tool or produced drops");
        } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void persistedDemandStrategyOrderOverridesDefinitionPriority(GameTestHelper h) {
        rig(h);var second=TOOL.east();h.setBlock(second,Content.state(VatPartType.ItemPort));
        h.runAtTickTime(35,()-> {
            var original=snapshot();
            try {
                var bread=interaction(CUSTOM.toString(),"minecraft:bread");
                var carrot=new ToolInteractionDefinition("mobvats:test_alternative",CUSTOM.toString(),List.of("minecraft:carrot"),List.of(),"mobvats:test_use",StageRequirement.ANY,20,0,"retain",null);
                var d=PassiveMobDefinitionRegistry.resolve(ItemEssence.COW).withToolInteractions(List.of(bread,carrot));publish(d);
                ((VatItemPortEntity)h.getBlockEntity(SOURCE)).staging().setStackInSlot(0,new ItemStack(Items.BREAD));
                ((VatItemPortEntity)h.getBlockEntity(second)).staging().setStackInSlot(0,new ItemStack(Items.CARROT));
                tool(h).demandConfiguration().configure(List.of(
                    new net.impactvector.mobvats.gamecontent.multiblock.conduit.DemandConfiguration.Option(carrot.id(),true,List.of(),List.of()),
                    new net.impactvector.mobvats.gamecontent.multiblock.conduit.DemandConfiguration.Option(bread.id(),true,List.of(),List.of())));
                h.assertTrue(ToolInteractions.acquire(context(h),d).changed() && tool(h).heldItem().is(Items.CARROT)
                    && ((VatItemPortEntity)h.getBlockEntity(SOURCE)).staging().getStackInSlot(0).is(Items.BREAD),"Demand strategy order was replaced by category/definition priority");
                var c=context(h);
                var breadOp=c.profile().definitions().stream().filter(p->p.id().endsWith(bread.id())).findFirst().orElseThrow();
                var carrotOp=c.profile().definitions().stream().filter(p->p.id().endsWith(carrot.id())).findFirst().orElseThrow();
                h.assertTrue(breadOp.execute(c,new ProcessState()).reason()==ProcessResult.Reason.MISSING_TOOL && tool(h).heldItem().is(Items.CARROT)
                    && carrotOp.execute(c,new ProcessState()).changed(),"Shared actor_use bypassed the operation's input selector");
            } finally {PassiveMobDefinitionRegistry.publish(original);}
            h.succeed();
        });
    }
}
