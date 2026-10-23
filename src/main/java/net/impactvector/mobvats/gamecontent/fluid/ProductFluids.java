package net.impactvector.mobvats.gamecontent.fluid;

import java.util.List;
import java.util.function.Supplier;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.*;

/** Process fluids with standard buckets and the minimal liquid blocks needed for safe placement. */
public final class ProductFluids {
    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MobVats.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MobVats.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MobVats.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MobVats.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> MEAT_TYPE = TYPES.register(
            "meat",
            () -> new ComponentPreservingFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mobvats.meat")
                    .density(1100)
                    .viscosity(2000)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MEAT =
            FLUIDS.register("meat", () -> new BaseFlowingFluid.Source(meatProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_MEAT =
            FLUIDS.register("flowing_meat", () -> new BaseFlowingFluid.Flowing(meatProperties()));
    public static final DeferredItem<BucketItem> MEAT_BUCKET = bucket("meat", MEAT);
    private static final DeferredBlock<LiquidBlock> MEAT_BLOCK = liquidBlock("meat", MEAT);

    private static BaseFlowingFluid.Properties meatProperties() {
        return new BaseFlowingFluid.Properties(MEAT_TYPE, MEAT, FLOWING_MEAT)
                .bucket(MEAT_BUCKET)
                .block(MEAT_BLOCK);
    }

    public static final DeferredHolder<FluidType, FluidType> STRANGE_MEAT_TYPE = TYPES.register(
            "strange_meat",
            () -> new ComponentPreservingFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mobvats.strange_meat")
                    .density(1100)
                    .viscosity(2000)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STRANGE_MEAT =
            FLUIDS.register("strange_meat", () -> new BaseFlowingFluid.Source(strangeMeatProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_STRANGE_MEAT =
            FLUIDS.register("flowing_strange_meat", () -> new BaseFlowingFluid.Flowing(strangeMeatProperties()));
    public static final DeferredItem<BucketItem> STRANGE_MEAT_BUCKET = bucket("strange_meat", STRANGE_MEAT);
    private static final DeferredBlock<LiquidBlock> STRANGE_MEAT_BLOCK = liquidBlock("strange_meat", STRANGE_MEAT);

    private static BaseFlowingFluid.Properties strangeMeatProperties() {
        return new BaseFlowingFluid.Properties(STRANGE_MEAT_TYPE, STRANGE_MEAT, FLOWING_STRANGE_MEAT)
                .bucket(STRANGE_MEAT_BUCKET)
                .block(STRANGE_MEAT_BLOCK);
    }

    public static final DeferredHolder<FluidType, FluidType> WASTE_TYPE = TYPES.register(
            "waste",
            () -> new ComponentPreservingFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mobvats.waste")
                    .density(1100)
                    .viscosity(1500)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WASTE =
            FLUIDS.register("waste", () -> new BaseFlowingFluid.Source(wasteProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_WASTE =
            FLUIDS.register("flowing_waste", () -> new BaseFlowingFluid.Flowing(wasteProperties()));
    public static final DeferredItem<BucketItem> WASTE_BUCKET = bucket("waste", WASTE);
    private static final DeferredBlock<LiquidBlock> WASTE_BLOCK = liquidBlock("waste", WASTE);

    private static BaseFlowingFluid.Properties wasteProperties() {
        return new BaseFlowingFluid.Properties(WASTE_TYPE, WASTE, FLOWING_WASTE)
                .bucket(WASTE_BUCKET)
                .block(WASTE_BLOCK);
    }

    public static final DeferredHolder<FluidType, FluidType> CULTURE_SLURRY_TYPE = TYPES.register(
            "culture_slurry",
            () -> new CompoundFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mobvats.culture_slurry")
                    .viscosity(1500)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CULTURE_SLURRY =
            FLUIDS.register("culture_slurry", () -> new BaseFlowingFluid.Source(cultureSlurryProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CULTURE_SLURRY =
            FLUIDS.register("flowing_culture_slurry", () -> new BaseFlowingFluid.Flowing(cultureSlurryProperties()));
    public static final DeferredItem<BucketItem> CULTURE_SLURRY_BUCKET = bucket("culture_slurry", CULTURE_SLURRY);
    private static final DeferredBlock<LiquidBlock> CULTURE_SLURRY_BLOCK =
            liquidBlock("culture_slurry", CULTURE_SLURRY);

    private static BaseFlowingFluid.Properties cultureSlurryProperties() {
        return new BaseFlowingFluid.Properties(CULTURE_SLURRY_TYPE, CULTURE_SLURRY, FLOWING_CULTURE_SLURRY)
                .bucket(CULTURE_SLURRY_BUCKET)
                .block(CULTURE_SLURRY_BLOCK);
    }

    public static final DeferredHolder<FluidType, FluidType> FEEDING_SLURRY_TYPE = TYPES.register(
            "feeding_slurry",
            () -> new CompoundFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mobvats.feeding_slurry")
                    .viscosity(1500)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FEEDING_SLURRY =
            FLUIDS.register("feeding_slurry", () -> new BaseFlowingFluid.Source(feedingSlurryProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_FEEDING_SLURRY =
            FLUIDS.register("flowing_feeding_slurry", () -> new BaseFlowingFluid.Flowing(feedingSlurryProperties()));
    public static final DeferredItem<BucketItem> FEEDING_SLURRY_BUCKET = bucket("feeding_slurry", FEEDING_SLURRY);
    private static final DeferredBlock<LiquidBlock> FEEDING_SLURRY_BLOCK =
            liquidBlock("feeding_slurry", FEEDING_SLURRY);

    private static BaseFlowingFluid.Properties feedingSlurryProperties() {
        return new BaseFlowingFluid.Properties(FEEDING_SLURRY_TYPE, FEEDING_SLURRY, FLOWING_FEEDING_SLURRY)
                .bucket(FEEDING_SLURRY_BUCKET)
                .block(FEEDING_SLURRY_BLOCK);
    }

    public static final DeferredHolder<FluidType, FluidType> EXPERIENCE_TYPE = TYPES.register(
            "experience",
            () -> new ComponentPreservingFluidType(
                    FluidType.Properties.create().descriptionId("fluid.mobvats.experience")));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> EXPERIENCE =
            FLUIDS.register("experience", () -> new BaseFlowingFluid.Source(experienceProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_EXPERIENCE =
            FLUIDS.register("flowing_experience", () -> new BaseFlowingFluid.Flowing(experienceProperties()));
    public static final DeferredItem<BucketItem> EXPERIENCE_BUCKET = bucket("experience", EXPERIENCE);
    private static final DeferredBlock<LiquidBlock> EXPERIENCE_BLOCK = liquidBlock("experience", EXPERIENCE);

    private static BaseFlowingFluid.Properties experienceProperties() {
        return new BaseFlowingFluid.Properties(EXPERIENCE_TYPE, EXPERIENCE, FLOWING_EXPERIENCE)
                .bucket(EXPERIENCE_BUCKET)
                .block(EXPERIENCE_BLOCK);
    }

    public static final DeferredHolder<FluidType, FluidType> COMPONENT_TYPE = TYPES.register(
            "component_fluid",
            () -> new CompoundFluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mobvats.component_fluid")
                    .viscosity(1500)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COMPONENT_FLUID =
            FLUIDS.register("component_fluid", () -> new BaseFlowingFluid.Source(componentProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_COMPONENT =
            FLUIDS.register("flowing_component_fluid", () -> new BaseFlowingFluid.Flowing(componentProperties()));
    public static final DeferredItem<BucketItem> COMPONENT_BUCKET = bucket("component_fluid", COMPONENT_FLUID);
    private static final DeferredBlock<LiquidBlock> COMPONENT_BLOCK = liquidBlock("component_fluid", COMPONENT_FLUID);

    private static BaseFlowingFluid.Properties componentProperties() {
        return new BaseFlowingFluid.Properties(COMPONENT_TYPE, COMPONENT_FLUID, FLOWING_COMPONENT)
                .bucket(COMPONENT_BUCKET)
                .block(COMPONENT_BLOCK);
    }

    private static DeferredItem<BucketItem> bucket(String name, Supplier<? extends Fluid> fluid) {
        return ITEMS.register(
                name + "_bucket",
                () -> name.equals("culture_slurry") || name.equals("feeding_slurry") || name.equals("component_fluid")
                        ? new CompoundBucketItem(
                                fluid.get(),
                                new Item.Properties()
                                        .craftRemainder(Items.BUCKET)
                                        .stacksTo(1))
                        : new ComponentPreservingBucketItem(
                                fluid.get(),
                                new Item.Properties()
                                        .craftRemainder(Items.BUCKET)
                                        .stacksTo(1)));
    }

    private static DeferredBlock<LiquidBlock> liquidBlock(String name, Supplier<BaseFlowingFluid.Source> fluid) {
        return BLOCKS.register(
                name, () -> new LiquidBlock(fluid.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
    }

    public static List<BucketItem> buckets() {
        return List.of(
                MEAT_BUCKET.get(),
                WASTE_BUCKET.get(),
                CULTURE_SLURRY_BUCKET.get(),
                FEEDING_SLURRY_BUCKET.get(),
                EXPERIENCE_BUCKET.get(),
                COMPONENT_BUCKET.get(),
                STRANGE_MEAT_BUCKET.get());
    }

    public static List<BaseFlowingFluid.Source> sources() {
        return List.of(
                MEAT.get(),
                WASTE.get(),
                CULTURE_SLURRY.get(),
                FEEDING_SLURRY.get(),
                EXPERIENCE.get(),
                COMPONENT_FLUID.get(),
                STRANGE_MEAT.get());
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private ProductFluids() {}
}
