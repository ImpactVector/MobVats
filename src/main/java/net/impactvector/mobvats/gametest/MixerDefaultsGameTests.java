package net.impactvector.mobvats.gametest;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class MixerDefaultsGameTests {
    private static byte[] bundled(String name)throws Exception {
        try(var in=MixerDefaultsGameTests.class.getResourceAsStream("/data/mobvats/"+name+".json")) {
            return Objects.requireNonNull(in).readAllBytes();
        }
    }
    private static JsonObject json(byte[] bytes){return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static String override(int ticks,int priority)throws Exception {
        var root=json(bundled("mixer_defaults"));
        var recipe=root.getAsJsonObject("recipes").getAsJsonObject("culture_slurry").deepCopy();
        recipe.addProperty("batch_ticks",ticks);recipe.addProperty("priority",priority);
        var recipes=new JsonObject();recipes.add("culture_slurry",recipe);
        var result=new JsonObject();result.add("recipes",recipes);return result.toString();
    }
    @GameTest(template="empty") public static void producedBundledCultureQualifiesForEnrichment(GameTestHelper h)throws Exception {
        var loaded=MixerRecipeRegistry.parse(json(bundled("mixer_defaults")));
        var recipe=loaded.recipes().get("culture_slurry");
        h.assertTrue(recipe.strategies().getFirst().fluids().get(1).amount()==250,"Culture XP must be exactly 250 mB");
        h.assertTrue(recipe.batchTicks()==200 && recipe.priority()==-1000,"Culture duration/priority changed");
        var before=MixerRecipeRegistry.snapshot();var profiles=CultureProfiles.snapshot();
        try {
            MixerRecipeRegistry.publish(loaded);CultureProfiles.publish(CultureProfiles.parse(json(bundled("culture_defaults"))));
            var state=new MixerState(()->{});
            state.fluids(true).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);
            state.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(),249),EXECUTE);
            h.assertTrue(!state.commit(recipe),"Culture accepted less than 250 mB XP");
            state.fluids(true).fill(new FluidStack(ProductFluids.EXPERIENCE.get(),1),EXECUTE);
            h.assertTrue(state.commit(recipe),"Bundled culture batch failed");
            var produced=state.fluids(false).drain(1000,EXECUTE);
            var expected=new CompoundComposition(List.of(
                new CompoundComposition.Entry(CompoundComposition.Kind.FLUID,"minecraft:water",1000),
                new CompoundComposition.Entry(CompoundComposition.Kind.FLUID,"mobvats:experience",250)));
            h.assertTrue(produced.is(ProductFluids.CULTURE_SLURRY.get()) && produced.getAmount()==1000 && expected.equals(produced.get(CompoundFluids.COMPOSITION.get())),"Produced culture composition/volume changed");
            var cow=ResourceLocation.withDefaultNamespace("cow");var rule=CultureProfiles.resolve(cow,produced);
            h.assertTrue(rule!=null && rule.required()!=null && rule.matches(produced),"Produced clean culture did not qualify for enrichment");
            h.assertTrue(CultureProfiles.multiplier(cow,FluidStack.EMPTY)==0.5 && CultureProfiles.multiplier(cow,produced)>0.5,"Culture did not improve dry frequency");
        } finally {MixerRecipeRegistry.publish(before);CultureProfiles.publish(profiles);}
        h.succeed();
    }
    @GameTest(template="empty") public static void missingDefaultsGenerateAndExistingOverridesSurvive(GameTestHelper h)throws Exception {
        var directory=Files.createTempDirectory("mobvats-mixer-defaults-");
        var defaults=directory.resolve("_defaults.json");var a=directory.resolve("a.json");var z=directory.resolve("z.json");
        try {
            var first=MixerRecipeRegistry.load(directory,s->{});
            h.assertTrue(first.errors().isEmpty() && Arrays.equals(bundled("mixer_defaults"),Files.readAllBytes(defaults)),"Missing defaults did not copy bundled bytes");
            var authored=override(777,100);var early=override(333,200);var late=override(444,300);
            Files.writeString(defaults,authored);Files.writeString(a,early);Files.writeString(z,late);
            for(int i=0;i<2;i++) {
                var loaded=MixerRecipeRegistry.load(directory,s->{});
                h.assertTrue(loaded.errors().isEmpty() && loaded.recipes().get("culture_slurry").batchTicks()==444 && loaded.recipes().get("culture_slurry").priority()==300,"Sorted sibling override precedence changed");
                h.assertTrue(Files.readString(defaults).equals(authored) && Files.readString(a).equals(early) && Files.readString(z).equals(late),"Loader rewrote existing definitions");
            }
        } finally {Files.deleteIfExists(z);Files.deleteIfExists(a);Files.deleteIfExists(defaults);Files.delete(directory);}
        h.succeed();
    }
    @GameTest(template="empty") public static void reloadCommandPreservesModifiedDefaultsAndSibling(GameTestHelper h)throws Exception {
        var directory=FMLPaths.CONFIGDIR.get().resolve("mobvats/mixer").toAbsolutePath().normalize();
        // Only the isolated headless run may exercise the command against its real config.
        h.assertTrue(directory.toString().replace('\\','/').contains("/.cache/gametest/config/"),"Reload test requires isolated GameTest configuration");
        var defaults=directory.resolve("_defaults.json");var sibling=directory.resolve("zz_defaults_regression.json");
        var saved=Files.readAllBytes(defaults);var before=MixerRecipeRegistry.snapshot();
        h.assertTrue(!Files.exists(sibling),"Regression sibling unexpectedly exists");
        try {
            var authored=override(777,100);var custom=override(888,200);
            Files.writeString(defaults,authored);Files.writeString(sibling,custom);
            var server=h.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"mobvats reload_mixer");
            h.assertTrue(MixerRecipeRegistry.recipe("culture_slurry").batchTicks()==888,"Command did not reload sorted override");
            h.assertTrue(Files.readString(defaults).equals(authored) && Files.readString(sibling).equals(custom),"Reload command rewrote authored files");
        } finally {Files.write(defaults,saved);Files.deleteIfExists(sibling);MixerRecipeRegistry.publish(before);}
        h.succeed();
    }
}
