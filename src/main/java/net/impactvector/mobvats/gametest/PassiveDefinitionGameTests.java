package net.impactvector.mobvats.gametest;

import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import it.zerono.mods.zerocore.lib.data.IoDirection;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class PassiveDefinitionGameTests {
    private static final BlockPos O=new BlockPos(1,1,1),HEAD=O.offset(2,2,2),FEED=O.offset(2,1,0),TOOL=O.offset(2,1,4);
    private static final ResourceLocation COW=ResourceLocation.withDefaultNamespace("cow");
    private static Path directory() throws Exception { Files.createDirectories(Path.of("build"));return Files.createTempDirectory(Path.of("build"),"passive-test-"); }
    private static void write(Path dir,String name,Map<String,PassiveMobDefinition> definitions) throws Exception {
        Files.writeString(dir.resolve(name),PassiveMobDefinitionRegistry.JSON.toJson(definitions));
    }
    private static PassiveMobDefinition changed(PassiveMobDefinition d,String entity,int growth,List<PassiveMobDefinition.SecondaryOutput> outputs) {
        return new PassiveMobDefinition(entity,d.feed(),growth,outputs,d.harvest(),d.culture_slurry(),d.feeding_slurry());
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void generatesDefaultsAndAlwaysDeserializesExistingFiles(GameTestHelper h) throws Exception {
        var parent=directory();var dir=parent.resolve("missing");var loaded=PassiveMobDefinitionRegistry.load(dir,s->{});
        h.assertTrue(Files.isDirectory(dir) && Files.isRegularFile(dir.resolve("_defaults.json")) && loaded.errors().isEmpty(),"First launch failed to generate/load defaults");
        var d=loaded.definitions().get(COW);h.assertTrue(d.juvenile_growth_ticks()==24000 && d.secondary_outputs().getFirst().requires_stage()==StageRequirement.ADULT,"Canonical lifecycle incorrect");
        write(dir,"_defaults.json",Map.of("minecraft:cow",changed(d,d.entity(),77,List.of())));
        var before=Files.readString(dir.resolve("_defaults.json"));var again=PassiveMobDefinitionRegistry.load(dir,s->{});
        h.assertTrue(again.definitions().get(COW).juvenile_growth_ticks()==77 && again.definitions().get(COW).secondary_outputs().isEmpty()
            && before.equals(Files.readString(dir.resolve("_defaults.json"))),"Runtime bypassed deserialization or rewrote defaults");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void deterministicWholeReplacementAndAdditiveFiles(GameTestHelper h) throws Exception {
        var dir=directory();var d=PassiveDefaults.data().get("minecraft:cow");
        write(dir,"00_before_defaults.json",Map.of(d.entity(),changed(d,d.entity(),70,List.of())));
        write(dir,"50_add.json",Map.of("minecraft:pig",changed(d,"minecraft:pig",80,List.of())));
        write(dir,"90_last.json",Map.of(d.entity(),changed(d,d.entity(),90,List.of())));
        var before=Files.readString(dir.resolve("00_before_defaults.json"));var loaded=PassiveMobDefinitionRegistry.load(dir,s->{});
        h.assertTrue(loaded.errors().isEmpty() && loaded.definitions().size()==PassiveDefaults.data().size() && loaded.definitions().get(COW).juvenile_growth_ticks()==90
            && loaded.sources().get(COW).equals("90_last.json") && loaded.definitions().get(COW).secondary_outputs().isEmpty(),"Merge order/whole replacement/additive semantics incorrect");
        h.assertTrue(before.equals(Files.readString(dir.resolve("00_before_defaults.json"))),"Generating defaults overwrote sibling");
        Files.delete(dir.resolve("90_last.json"));loaded=PassiveMobDefinitionRegistry.load(dir,s->{});
        h.assertTrue(loaded.definitions().get(COW).juvenile_growth_ticks()==70,"Lexically early sibling loaded before defaults");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void invalidFileIsRejectedAtomicallyWithoutOverwrite(GameTestHelper h) throws Exception {
        var dir=directory();var loaded=PassiveMobDefinitionRegistry.load(dir,s->{});var d=loaded.definitions().get(COW);
        var root=PassiveMobDefinitionRegistry.JSON.toJsonTree(Map.of("minecraft:cow",changed(d,d.entity(),99,List.of()),"minecraft:pig",changed(d,"minecraft:pig",90,List.of()))).getAsJsonObject();
        root.getAsJsonObject("minecraft:pig").addProperty("juvenile_growth_ticks",-1);
        var text=root.toString();Files.writeString(dir.resolve("10_invalid.json"),text);
        Files.writeString(dir.resolve("20_syntax.json"),"{broken");
        write(dir,"90_valid.json",Map.of("minecraft:sheep",changed(d,"minecraft:sheep",80,List.of())));
        var errors=new ArrayList<String>();loaded=PassiveMobDefinitionRegistry.load(dir,errors::add);
        h.assertTrue(loaded.definitions().get(COW).juvenile_growth_ticks()==24000 && loaded.definitions().size()==PassiveDefaults.data().size()
            && errors.stream().anyMatch(s->s.contains("10_invalid.json") && s.contains("positive"))
            && errors.stream().anyMatch(s->s.contains("20_syntax.json")) && text.equals(Files.readString(dir.resolve("10_invalid.json"))),"Malformed file partly applied, overwritten or poorly diagnosed");
        Files.writeString(dir.resolve("_defaults.json"),"{broken");loaded=PassiveMobDefinitionRegistry.load(dir,s->{});
        h.assertTrue(!loaded.definitions().containsKey(COW) && loaded.errors().stream().anyMatch(s->s.contains("Required built-in")),"Missing required definition was silently replaced");h.succeed();
    }
    private static void rig(GameTestHelper h,ItemStack tool) {
        VatGameTests.build(h,5);h.setBlock(FEED,Content.state(VatPartType.ItemPort));h.setBlock(TOOL,Content.state(VatPartType.ToolPort));
        ((VatToolPortEntity)h.getBlockEntity(TOOL)).setHeldItem(tool);((VatHeadEntity)h.getBlockEntity(HEAD)).encode(ItemEssence.COW);Slice11Fixtures.vatStorage(h,O,5,5);Slice11Fixtures.feedFrom(h,FEED,FEED.south());
    }
    private static VatItemPortEntity feed(GameTestHelper h) { return (VatItemPortEntity)h.getBlockEntity(FEED); }
    private static MultiblockVat vat(GameTestHelper h) { return ((VatHeadEntity)h.getBlockEntity(HEAD)).getMultiblockController().orElseThrow(); }
    @GameTest(template="empty",timeoutTicks=90)
    public static void dairyDoesNotConsumeWheatOrFeedingSlurry(GameTestHelper h) {
        rig(h,ItemStack.EMPTY);h.runAtTickTime(30,()->{
            feed(h).staging().setStackInSlot(0,new ItemStack(Items.WHEAT,5));
            vat(h).fluidHandler(IoDirection.Input).fill(new FluidStack(ProductFluids.FEEDING_SLURRY.get(),500),FluidAction.EXECUTE);
            var state=vat(h).processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.MILK);state.elapsed=1200;state.phase=ProcessState.Phase.READY;
        });h.runAtTickTime(65,()->{
            h.assertTrue(vat(h).processEngine().state(h.absolutePos(HEAD)).stage()==LifeStage.ADULT && feed(h).staging().getStackInSlot(0).getCount()==5
                && vat(h).feedingAmount()==500 && vat(h).milkAmount()==(int)Math.floor(1000*vat(h).yieldMultiplier(h.absolutePos(HEAD))),"Dairy fed/replaced or failed adult milk");
            h.assertTrue(vat(h).statusSnapshot().pages().get(1).contains("Disabled by topology"),"Dairy diagnostic absent");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void immediateSwordReplacementPausesMilkAndBucket(GameTestHelper h) {
        rig(h,new ItemStack(Items.IRON_SWORD));h.runAtTickTime(30,()->feed(h).staging().setStackInSlot(0,new ItemStack(Items.WHEAT,2)));
        h.runAtTickTime(75,()->{
            var state=vat(h).processEngine().state(h.absolutePos(HEAD));
            h.assertTrue(state.stage()==LifeStage.JUVENILE && state.growth().elapsed>0 && state.growth().elapsed<50
                && vat(h).getExperienceMb()>0 && feed(h).staging().getStackInSlot(0).getCount()==1,"Sword did not harvest immediately and start one juvenile");
            ((VatToolPortEntity)h.getBlockEntity(TOOL)).setHeldItem(new ItemStack(Items.BUCKET));vat(h).produceMilk();
            state.process(PassiveProcessCompiler.MILK).elapsed=444;state.process(PassiveProcessCompiler.MILK).phase=ProcessState.Phase.RUNNING;
        });h.runAtTickTime(80,()->{
            h.assertTrue(vat(h).processEngine().state(h.absolutePos(HEAD)).process(PassiveProcessCompiler.MILK).elapsed==444
                && ((VatToolPortEntity)h.getBlockEntity(TOOL)).heldItem().is(Items.BUCKET) && vat(h).milkAmount()==1000,"Juvenile milk timer or bucket progressed");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void configuredGrowthCompletionResumesAdultMilk(GameTestHelper h) {
        rig(h,ItemStack.EMPTY);h.runAtTickTime(30,()->{
            var state=vat(h).processEngine().state(h.absolutePos(HEAD));state.stage(LifeStage.JUVENILE);state.growth().start();state.growth().elapsed=23990;
            var milk=state.process(PassiveProcessCompiler.MILK);milk.phase=ProcessState.Phase.READY;milk.elapsed=1200;
        });h.runAtTickTime(60,()->{
            var state=vat(h).processEngine().state(h.absolutePos(HEAD));h.assertTrue(state.stage()==LifeStage.ADULT && state.growth().elapsed==0
                && vat(h).milkAmount()==(int)Math.floor(1000*vat(h).yieldMultiplier(h.absolutePos(HEAD))) && ((VatHeadEntity)h.getBlockEntity(HEAD)).lifeStage()==LifeStage.ADULT,"Growth completion failed generic stage/reset/milk resume");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void genericStagePredicatesAndLifecycleRollback(GameTestHelper h) {
        h.assertTrue(StageRequirement.ANY.accepts(LifeStage.JUVENILE) && StageRequirement.ADULT.accepts(LifeStage.ADULT)
            && !StageRequirement.ADULT.accepts(LifeStage.JUVENILE) && StageRequirement.JUVENILE.accepts(LifeStage.JUVENILE),"Generic stage predicates incorrect");
        var specimen=new SpecimenProcessState();var resource=new Object();
        h.assertTrue(!new ProcessTransaction().add(ProcessTransaction.lifecycle(specimen,LifeStage.JUVENILE,15))
            .add(new ProcessTransaction.Step(resource,()->true,()->false,()->{})).commit()
            && specimen.stage()==LifeStage.ADULT && specimen.growth().elapsed==0,"Lifecycle rollback failed");
        specimen.stage(LifeStage.JUVENILE);specimen.growth().elapsed=1234;
        var restored=SpecimenProcessState.load(specimen.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.stage()==LifeStage.JUVENILE && restored.growth().elapsed==1234,"Stage/progress persistence failed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void reloadUsesDeserializedDurationAndRecompilesActiveEngine(GameTestHelper h) {
        rig(h,ItemStack.EMPTY);h.runAtTickTime(30,()->{
            var original=new PassiveMobDefinitionRegistry.Loaded(PassiveMobDefinitionRegistry.definitions(),Map.of(COW,"_defaults.json"),List.of());
            try {
                var dir=directory();var loaded=PassiveMobDefinitionRegistry.load(dir,message->{});var d=loaded.definitions().get(COW);
                write(dir,"90_override.json",Map.of(d.entity(),changed(d,d.entity(),8,d.secondary_outputs())));
                loaded=PassiveMobDefinitionRegistry.load(dir,message->{});PassiveMobDefinitionRegistry.publish(loaded);
                var engine=vat(h).processEngine();int compilations=engine.compilationCount();
                var specimen=engine.state(h.absolutePos(HEAD));specimen.stage(LifeStage.JUVENILE);specimen.growth().start();
                for(int i=0;i<15;i++)engine.tick();
                h.assertTrue(specimen.stage()==LifeStage.JUVENILE && specimen.growth().elapsed==7 && engine.compilationCount()>compilations,"Reload ignored deserialized duration or failed to invalidate cached profile");
                engine.tick();h.assertTrue(specimen.stage()==LifeStage.ADULT && specimen.growth().elapsed==0,"Configured sixteenth dry tick failed to complete growth");
            } catch(Exception error) { throw new IllegalStateException(error); }
            finally { PassiveMobDefinitionRegistry.publish(original); }
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void legacyStagesAndReadyBoundaryMigrateProportionally(GameTestHelper h) {
        for(var cycle:List.of("IDLE","READY","GROWING")) {
            var old=new CompoundTag();var list=new net.minecraft.nbt.ListTag();var entry=new CompoundTag();
            entry.putLong("head",0);entry.putString("cycle",cycle);entry.putInt("replacementTicks",1200);list.add(entry);old.put("cowProcesses",list);old.putLong("cowXp",17);
            var engine=new VatProcessEngine(new MultiblockVat(h.getLevel()));engine.load(old,h.getLevel().registryAccess());
            var specimen=engine.state(BlockPos.ZERO);
            h.assertTrue(engine.legacyXpPending()==17 && engine.experienceMb()==0 && specimen.stage()==(cycle.equals("GROWING")?LifeStage.JUVENILE:LifeStage.ADULT)
                && specimen.growth().elapsed==(cycle.equals("GROWING")?24000:0)
                && (!cycle.equals("GROWING") || specimen.growth().phase==ProcessState.Phase.RUNNING),"Legacy mapping or growth boundary stuck: "+cycle);
            var versioned=new CompoundTag();engine.save(versioned,h.getLevel().registryAccess());
            versioned.putLong("cowXp",999);versioned.put("cowProcesses",new net.minecraft.nbt.ListTag());
            engine.load(versioned,h.getLevel().registryAccess());h.assertTrue(engine.legacyXpPending()==17 && engine.experienceMb()==0 && engine.state(BlockPos.ZERO).stage()==specimen.stage(),"Stale legacy fields overrode versioned generic state");
        }h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=10)
    public static void unsupportedStrategiesEntitiesAndIncompleteDefinitionsDiagnoseCleanly(GameTestHelper h) throws Exception {
        var dir=directory();var d=PassiveDefaults.data().get("minecraft:cow");
        write(dir,"10_hostile.json",Map.of("minecraft:zombie",changed(d,"minecraft:zombie",20,List.of())));
        var invalid=PassiveMobDefinitionRegistry.JSON.toJsonTree(Map.of(d.entity(),d)).getAsJsonObject();
        invalid.getAsJsonObject(d.entity()).getAsJsonObject("harvest").addProperty("sword","mobvats:arbitrary_java");Files.writeString(dir.resolve("20_strategy.json"),invalid.toString());
        invalid.getAsJsonObject(d.entity()).remove("feed");Files.writeString(dir.resolve("30_partial.json"),invalid.toString());
        var loaded=PassiveMobDefinitionRegistry.load(dir,message->{});
        h.assertTrue(loaded.definitions().size()==PassiveDefaults.data().size() && loaded.definitions().containsKey(ResourceLocation.withDefaultNamespace("zombie"))
            && loaded.errors().stream().anyMatch(e->e.contains("20_strategy.json") && e.contains("Unsupported sword"))
            && loaded.errors().stream().anyMatch(e->e.contains("30_partial.json") && e.contains("require feed")),"Unsupported/incomplete definitions escaped validation");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void legacyConfiguredCultureCostDoesNotBuyAcceleration(GameTestHelper h) {
        rig(h,ItemStack.EMPTY);h.runAtTickTime(30,()->{
            var d=PassiveMobDefinitionRegistry.resolve(COW);
            var custom=new PassiveMobDefinition(d.entity(),d.feed(),d.juvenile_growth_ticks(),d.secondary_outputs(),d.harvest(),
                new PassiveMobDefinition.Slurry(true,25),d.feeding_slurry());
            var registry=new MobProcessRegistry();registry.register(COW,PassiveProcessCompiler.compile(custom));
            var engine=new VatProcessEngine(vat(h),registry.freeze());engine.assembled();var state=engine.state(h.absolutePos(HEAD));state.stage(LifeStage.JUVENILE);state.growth().start();
            vat(h).fluidHandler(IoDirection.Input).fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(),10),FluidAction.EXECUTE);
            engine.tick();h.assertTrue(state.growth().elapsed==0 && vat(h).cultureAmount()==10,"Insufficient configured Culture payment granted acceleration");
            vat(h).fluidHandler(IoDirection.Input).fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(),15),FluidAction.EXECUTE);
            engine.tick();h.assertTrue(state.growth().elapsed==1 && vat(h).cultureAmount()==25 && vat(h).metrics().total("cultureConsumed")==0,"Legacy culture costs affected frequency");h.succeed();
        });
    }

}
