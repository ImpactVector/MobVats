package net.impactvector.mobvats.gamecontent.multiblock.vat.client;

import it.zerono.mods.zerocore.lib.client.model.BlockVariantsModelBuilder;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatHeadEntityRenderer;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMechanismRenderer;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.VatGlassModel;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.VatCasingModel;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.VatWallPanelModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import java.util.IdentityHashMap;
import net.minecraft.client.resources.model.BakedModel;

@EventBusSubscriber(modid = MobVats.MOD_ID, value = Dist.CLIENT)
public final class VatClient {
    private static final ModelResourceLocation GLASS_MATERIAL = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/formed_glass_material"));
    private static final ModelResourceLocation CASING_MATERIAL = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/casing_material"));
    private static final ModelResourceLocation MIXER_GLASS_MATERIAL = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/mixer_formed_glass_material"));
    private static final BlockVariantsModelBuilder[] MODELS = {
        new ConstructionModelBuilder(VatPartType.Head, "facing=north"),
        new ConstructionModelBuilder(VatPartType.Head, "facing=east"),
        new ConstructionModelBuilder(VatPartType.Head, "facing=south"),
        new ConstructionModelBuilder(VatPartType.Head, "facing=west"),
        new ConstructionModelBuilder(VatPartType.Flesh, "")
    };
    private static final class ConstructionModelBuilder extends BlockVariantsModelBuilder {
        ConstructionModelBuilder(VatPartType type, String variant) {
            super(true, true, false);
            var hidden = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "block/hidden"));
            addBlock(type.ordinal(), new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,
                type.getSerializedName()), variant), false, hidden);
            setFallbackModelData(type.ordinal(), 0);
        }
    }
    @SubscribeEvent public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (var builder : MODELS) builder.onRegisterModels(event);
        event.register(GLASS_MATERIAL);
        event.register(CASING_MATERIAL);
        event.register(MIXER_GLASS_MATERIAL);
        event.register(VatMechanismRenderer.VISE_MODEL);
        for(var id:java.util.List.of("pull_pump","push_pump"))event.register(pumpSleeve(id));
    }
    private static ModelResourceLocation pumpSleeve(String id) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,"block/procedural/t1/"+id+"_sleeve"));
    }
    @SubscribeEvent public static void bakeModels(ModelEvent.ModifyBakingResult event) {
        for (var builder : MODELS) builder.onBakeModels(event);
        var tapPreviews = new IdentityHashMap<BakedModel, BakedModel>();
        event.getModels().replaceAll((location, original) -> location.id().getNamespace().equals(MobVats.MOD_ID)
            && (location.id().getPath().endsWith("_conduit_tap") && location.variant().equals("inventory")
                || location.id().getPath().endsWith("_conduit") && location.variant().equals("mode=tap"))
            ? tapPreviews.computeIfAbsent(original, net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitPreviewModel::new) : original);
        var material = event.getModels().get(GLASS_MATERIAL);
        var replacements = new IdentityHashMap<BakedModel, VatGlassModel>();
        // GlassBlock carries ZeroCore2's facings property even though bounds, not neighbors, select our skin.
        event.getModels().replaceAll((location, original) ->
            location.id().equals(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "vat_glass"))
                && !location.variant().equals("inventory")
                ? replacements.computeIfAbsent(original, model -> new VatGlassModel(model, material)) : original);
        var casingMaterials = event.getModels().get(CASING_MATERIAL);
        var casings = new IdentityHashMap<BakedModel, VatCasingModel>();
        event.getModels().replaceAll((location, original) ->
            location.id().equals(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "vat_casing"))
                && !location.variant().equals("inventory")
                ? casings.computeIfAbsent(original, model -> new VatCasingModel(model, casingMaterials)) : original);
        // Separate Mixer glass material, sharing the established bounds-selected shell geometry.
        var mixerCasing=event.getModels().entrySet().stream().filter(e->e.getKey().id().equals(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,"mixer_casing")) && !e.getKey().variant().equals("inventory")).findFirst().orElseThrow().getValue();
        var mixerGlass=event.getModels().get(MIXER_GLASS_MATERIAL);
        for(var cladding:java.util.List.of(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.CASING,net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.GLASS)){
            var id=ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,cladding==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.CASING?"mixer_casing":"mixer_glass");
            var cache=new IdentityHashMap<BakedModel,BakedModel>();
            event.getModels().replaceAll((location,original)->location.id().equals(id) && !location.variant().equals("inventory")?cache.computeIfAbsent(original,m->new net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitShellModel(m,location.variant().contains("cladding_material=")?net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.valueOf(location.variant().split("cladding_material=")[1].split(",")[0].toUpperCase(java.util.Locale.ROOT)).cladding():cladding,material,casingMaterials,mixerGlass,mixerCasing)):original);
        }
        for(var block:net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.blocks()){
                var conduit=(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock)block;
                var cladding=net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.BARE;
                var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                var cache=new IdentityHashMap<BakedModel,BakedModel>();
                event.getModels().replaceAll((location,original)->location.id().equals(id) && !location.variant().equals("inventory")?cache.computeIfAbsent(original,m->new net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitShellModel(m,location.variant().contains("cladding_material=")?net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.valueOf(location.variant().split("cladding_material=")[1].split(",")[0].toUpperCase(java.util.Locale.ROOT)).cladding():cladding,material,casingMaterials,mixerGlass,mixerCasing)):original);
            }
        for(var block:net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.blocks()) {
            var cladding=net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCladding.BARE;
            var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);var cache=new IdentityHashMap<BakedModel,BakedModel>();
            event.getModels().replaceAll((location,original)->location.id().equals(id)&&!location.variant().equals("inventory")
                ?cache.computeIfAbsent(original,m->new net.impactvector.mobvats.gamecontent.multiblock.conduit.client.ConduitShellModel(m,location.variant().contains("cladding_material=")?net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.valueOf(location.variant().split("cladding_material=")[1].split(",")[0].toUpperCase(java.util.Locale.ROOT)).cladding():cladding,material,casingMaterials,mixerGlass,mixerCasing)):original);
        }
        for(var type:net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.values())
            if(type!=net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Casing && type!=net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Glass && type!=net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Shaft
                && !net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel.INSTALLED.contains(type.getSerializedName())
                && !net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPlacement.fullMachinery(type)){
                var id=ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,type.getSerializedName());
                var cache=new IdentityHashMap<BakedModel,VatWallPanelModel>();
                event.getModels().replaceAll((location,original)->location.id().equals(id) && !location.variant().equals("inventory")?cache.computeIfAbsent(original,m->new VatWallPanelModel(m,mixerCasing)):original);
            }
        for (var type : VatPartType.values()) if (type.usesWallPanel() && !net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel.INSTALLED.contains(type.getSerializedName())) {
            var id = ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, type.getSerializedName());
            var panels = new IdentityHashMap<BakedModel, VatWallPanelModel>();
            event.getModels().replaceAll((location, original) -> location.id().equals(id)
                && !location.variant().equals("inventory")
                ? panels.computeIfAbsent(original, model -> new VatWallPanelModel(model, casingMaterials)) : original);
        }
        // Inventory and chunk models share the same generated fixture geometry.
        // Apply after the generic wall wrappers so these parts retain their mechanisms.
        for(var fixture:net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel.IDS) {
            var id=ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,fixture);
            event.getModels().replaceAll((location,original)->{
                if(!location.id().equals(id))return original;
                var installed=location.variant().contains("cladding_material=")?net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.valueOf(location.variant().split("cladding_material=")[1].split(",")[0].toUpperCase(java.util.Locale.ROOT)):net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.NONE;
                boolean mixer=installed.shell()==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitShell.MIXER || fixture.startsWith("mixer_");
                var model=new net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel(original,mixer?mixerCasing:casingMaterials,fixture,
                    fixture.endsWith("_pump")?java.util.Objects.requireNonNull(event.getModels().get(pumpSleeve(fixture))):null);
                return location.variant().contains("cladding_material=") || java.util.Set.of("item_port","fluid_port","fill_valve","drain_valve","pull_pump","push_pump","fluid_void","item_void").contains(fixture)?model.withCladding(installed,mixer?mixerGlass:material):model;
            });
        }
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.ENTITY.get(),net.impactvector.mobvats.gamecontent.multiblock.conduit.client.FluidBufferRenderer::new);
        event.registerBlockEntityRenderer(Content.entityType(VatPartType.Controller), net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMediumRenderer::new);
        event.registerBlockEntityRenderer(Content.entityType(VatPartType.Head), VatHeadEntityRenderer::new);
    }
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Content.CONTROLLER_MENU.get(),net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen.VatControllerScreen::new);
        event.register(Content.PORT_MENU.get(),net.impactvector.mobvats.gamecontent.multiblock.vat.client.screen.VatPortScreen::new);
    }
    @SubscribeEvent public static void fluidExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerFluidType(productAppearance(0xff9877cf),net.impactvector.mobvats.gamecontent.fluid.ProductFluids.COMPONENT_TYPE.get());
        event.registerFluidType(productAppearance(0xff67bba7),net.impactvector.mobvats.gamecontent.fluid.ProductFluids.CULTURE_SLURRY_TYPE.get());
        event.registerFluidType(productAppearance(0xff789b3f),net.impactvector.mobvats.gamecontent.fluid.ProductFluids.FEEDING_SLURRY_TYPE.get());
        event.registerFluidType(productAppearance(0xffb64b4b),net.impactvector.mobvats.gamecontent.fluid.ProductFluids.MEAT_TYPE.get());
        event.registerFluidType(productAppearance(0xff756142),net.impactvector.mobvats.gamecontent.fluid.ProductFluids.WASTE_TYPE.get());
        event.registerFluidType(productAppearance(0xff9cdd32),net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE_TYPE.get());
    }
    private static net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions productAppearance(int color) {
        return new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
            public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
            public int getTintColor() { return color; }
        };
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Content.block(VatPartType.Glass), RenderType.cutout());
            for(var block:net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.blocks())
                ItemBlockRenderTypes.setRenderLayer(block,RenderType.cutout());
            for(var block:net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.blocks())
                ItemBlockRenderTypes.setRenderLayer(block,RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.block(net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.Glass),RenderType.cutout());
        });
    }
}
