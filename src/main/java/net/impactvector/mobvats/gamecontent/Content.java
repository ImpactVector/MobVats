package net.impactvector.mobvats.gamecontent;

import java.util.EnumMap;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.item.ItemSyringe;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.AbstractVatEntity;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatConstructionBlock;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatGlassBlock;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadBlock;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatPartBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class Content {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MobVats.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MobVats.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MobVats.MOD_ID);
    public static final DeferredHolder<
                    net.minecraft.world.item.crafting.RecipeSerializer<?>,
                    net.minecraft.world.item.crafting.RecipeSerializer<
                            net.minecraft.world.item.crafting.ShapelessRecipe>>
            BOUNDARY_SWITCH = RECIPES.register(
                    "boundary_switch", () -> new net.minecraft.world.item.crafting.RecipeSerializer<>() {
                        public com.mojang.serialization.MapCodec<net.minecraft.world.item.crafting.ShapelessRecipe>
                                codec() {
                            return net.minecraft.world.item.crafting.RecipeSerializer.SHAPELESS_RECIPE
                                    .codec()
                                    .xmap(net.impactvector.mobvats.gamecontent.item.BoundarySwitchRecipe::new, r -> r);
                        }

                        public net.minecraft.network.codec.StreamCodec<
                                        net.minecraft.network.RegistryFriendlyByteBuf,
                                        net.minecraft.world.item.crafting.ShapelessRecipe>
                                streamCodec() {
                            return net.minecraft.world.item.crafting.RecipeSerializer.SHAPELESS_RECIPE
                                    .streamCodec()
                                    .map(net.impactvector.mobvats.gamecontent.item.BoundarySwitchRecipe::new, r -> r);
                        }
                    });
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MobVats.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MobVats.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MobVats.MOD_ID);
    public static final DeferredHolder<
                    net.minecraft.world.inventory.MenuType<?>,
                    net.minecraft.world.inventory.MenuType<
                            net.impactvector.mobvats.gamecontent.multiblock.common.MachinePortMenu>>
            PORT_MENU = MENUS.register(
                    "vat_port",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            net.impactvector.mobvats.gamecontent.multiblock.common.MachinePortMenu::new));
    private static final EnumMap<VatPartType, DeferredBlock<Block>> PART_BLOCKS = new EnumMap<>(VatPartType.class);
    public static final DeferredHolder<
                    net.minecraft.world.inventory.MenuType<?>,
                    net.minecraft.world.inventory.MenuType<
                            net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu>>
            CONTROLLER_MENU = MENUS.register(
                    "vat_controller",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            net.impactvector.mobvats.gamecontent.multiblock.vat.menu.VatControllerMenu::new));
    private static final EnumMap<VatPartType, DeferredHolder<BlockEntityType<?>, BlockEntityType<AbstractVatEntity>>>
            PART_ENTITIES = new EnumMap<>(VatPartType.class);
    public static final DeferredItem<ItemSyringe> SYRINGE =
            ITEMS.register("syringe", () -> new ItemSyringe(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ItemEssence> ESSENCE =
            ITEMS.register("essence", () -> new ItemEssence(new Item.Properties().stacksTo(16)));

    static {
        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.initialize();
        net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.initialize();
        net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.initialize();
        net.impactvector.mobvats.gamecontent.creative.CreativeContent.initialize();
        for (VatPartType type : VatPartType.values()) {
            var block = BLOCKS.<Block>register(
                    net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.id(type),
                    () -> net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.supported(type)
                            ? new net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock(type)
                            : switch (type) {
                                case PullPump, PushPump, FillValve, DrainValve, ItemVoid, FluidVoid ->
                                    new net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatBoundaryBlock(type);
                                case Glass -> new VatGlassBlock();
                                case Head -> new VatHeadBlock();
                                case Flesh -> new VatConstructionBlock(type);
                                case ItemPort, FluidPort, ToolPort ->
                                    new net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatPortBlock(type);
                                default -> new VatPartBlock(type);
                            });
            PART_BLOCKS.put(type, block);
            if (type == VatPartType.Head)
                ITEMS.register(
                        net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.id(type),
                        () -> new net.impactvector.mobvats.gamecontent.item.ItemVatHead(
                                block.get(), new Item.Properties()));
            else if (type == VatPartType.ItemPort || type == VatPartType.FluidPort)
                ITEMS.register(
                        net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.id(type),
                        () -> new net.impactvector.mobvats.gamecontent.item.ItemEndpoint(block.get(), false));
            else if (type.ordinal() >= VatPartType.PullPump.ordinal())
                ITEMS.register(
                        net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.id(type),
                        () -> new net.impactvector.mobvats.gamecontent.item.ItemBoundary(block.get()));
            else if (type == VatPartType.Casing || type == VatPartType.Glass)
                ITEMS.register(
                        type.getSerializedName(),
                        () -> new net.impactvector.mobvats.gamecontent.item.CladdingBlockItem(block.get()));
            else ITEMS.registerSimpleBlockItem(block);
            PART_ENTITIES.put(
                    type,
                    ENTITIES.register(
                            type.getSerializedName(),
                            () -> BlockEntityType.Builder.<AbstractVatEntity>of(
                                            (pos, state) -> type.createTileEntity(state, pos), block.get())
                                    .build(null)));
        }
        TABS.register(
                "mobvats",
                () -> CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.mobvats"))
                        .icon(() -> new ItemStack(block(VatPartType.Controller)))
                        .displayItems((parameters, output) -> {
                            for (VatPartType type : VatPartType.values()) output.accept(block(type));
                            for (var type :
                                    net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.values())
                                if (net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock.shared(
                                                type)
                                        == null)
                                    output.accept(
                                            net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.block(
                                                    type));
                            for (var item :
                                    net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitContent.items())
                                output.accept(item);
                            for (var buffer :
                                    net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferContent.blocks())
                                output.accept(buffer);
                            output.accept(SYRINGE.get());
                            for (var type : net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.values())
                                if (type != net.impactvector.mobvats.gamecontent.creative.CreativeDeviceType.DRIVE)
                                    output.accept(
                                            net.impactvector.mobvats.gamecontent.creative.CreativeContent.block(type));
                            for (var bucket : net.impactvector.mobvats.gamecontent.fluid.ProductFluids.buckets())
                                output.accept(bucket);
                        })
                        .build());
    }

    public static Block block(VatPartType type) {
        return PART_BLOCKS.get(type).get();
    }

    public static net.minecraft.world.level.block.state.BlockState state(VatPartType type) {
        var block = block(type);
        return block instanceof net.impactvector.mobvats.gamecontent.multiblock.common.MechanicalPartBlock
                ? block.defaultBlockState()
                        .setValue(
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.PROPERTY,
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.VAT_T1_CASING)
                : block.defaultBlockState();
    }

    public static BlockEntityType<AbstractVatEntity> entityType(VatPartType type) {
        return PART_ENTITIES.get(type).get();
    }

    public static void register(IEventBus bus) {
        net.impactvector.mobvats.gamecontent.fluid.ProductFluids.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        RECIPES.register(bus);
    }

    private Content() {}
}
