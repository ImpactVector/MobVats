package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.EndpointState;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class AppliedCladdingGameTests {
    private static final BlockPos P = new BlockPos(2, 2, 2);

    private static InteractionResult click(GameTestHelper h, BlockPos pos, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var absolute = h.absolutePos(pos);
        return stack.useOn(new net.minecraft.world.item.context.UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
    }

    private static List<Block> mechanical() {
        var blocks = new ArrayList<Block>(ConduitContent.blocks());
        blocks.addAll(FluidBufferContent.blocks());
        for (var type : VatPartType.values()) if (MechanicalPartBlock.supported(type)) blocks.add(Content.block(type));
        return blocks;
    }

    @GameTest(template = "empty")
    public static void survivalAppliesReplacesAndRecoversExactMaterials(GameTestHelper h) {
        for (var block : mechanical()) {
            h.setBlock(P, Blocks.AIR);
            h.setBlock(P, block.defaultBlockState());
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setShiftKeyDown(true);
            for (var material : CladdingMaterial.values()) {
                if (material == CladdingMaterial.NONE
                        || block instanceof MechanicalPartBlock part && !part.accepts(material)) continue;
                var before = CladdingMaterial.installed(h.getBlockState(P));
                var stack = new ItemStack(material.item(), 2);
                h.assertTrue(
                        click(h, P, player, stack).consumesAction() && stack.getCount() == 1,
                        "Installation did not consume exactly one material");
                h.assertTrue(
                        h.getBlockState(P).is(block) && CladdingMaterial.installed(h.getBlockState(P)) == material,
                        "Installation changed functional identity");
                if (before != CladdingMaterial.NONE)
                    h.assertTrue(player.getInventory().countItem(before.item()) > 0, "Replacement lost old material");
                h.assertTrue(
                        click(h, P, player, stack).consumesAction() && stack.getCount() == 1,
                        "Identical installation consumed material");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndInvalidMaterialAndExplicitSingleBlock(GameTestHelper h) {
        h.setBlock(P, ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE));
        h.setBlock(P.east(), FluidBufferContent.state(ConduitCladding.BARE));
        h.setBlock(P.west(), ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE));
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        var stack = new ItemStack(CladdingMaterial.VAT_T1_GLASS.item());
        click(h, P, player, stack);
        h.assertTrue(
                CladdingMaterial.installed(h.getBlockState(P)) == CladdingMaterial.NONE,
                "Non-sneaking applied material");
        player.setShiftKeyDown(true);
        click(h, P, player, new ItemStack(Items.DIRT));
        h.assertTrue(
                CladdingMaterial.installed(h.getBlockState(P)) == CladdingMaterial.NONE, "Invalid material applied");
        click(h, P, player, stack);
        h.assertTrue(
                stack.getCount() == 1
                        && CladdingMaterial.installed(h.getBlockState(P)) == CladdingMaterial.VAT_T1_GLASS,
                "Creative consumption or material wrong");
        h.assertTrue(
                CladdingMaterial.installed(h.getBlockState(P.east())) == CladdingMaterial.NONE
                        && CladdingMaterial.installed(h.getBlockState(P.west())) == CladdingMaterial.NONE,
                "Cladding propagated to a neighbor");
        h.assertTrue(
                h.getBlockState(P)
                                .getCollisionShape(h.getLevel(), h.absolutePos(P))
                                .equals(net.minecraft.world.phys.shapes.Shapes.block())
                        && h.getBlockState(P.west())
                                .getCollisionShape(h.getLevel(), h.absolutePos(P.west()))
                                .isEmpty(),
                "Bare/clad conduit collision lost");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bareAndCladLootConserveMachineryAndMaterial(GameTestHelper h) {
        for (var block : mechanical())
            for (var material : CladdingMaterial.values()) {
                if (block instanceof MechanicalPartBlock part && !part.accepts(material)) continue;
                h.setBlock(P, Blocks.AIR);
                var state = block.defaultBlockState().setValue(CladdingMaterial.PROPERTY, material);
                h.setBlock(P, state);
                var drops = Block.getDrops(
                        state, h.getLevel(), h.absolutePos(P), h.getLevel().getBlockEntity(h.absolutePos(P)));
                h.assertTrue(
                        drops.stream()
                                        .filter(s -> s.is(block.asItem()))
                                        .mapToInt(ItemStack::getCount)
                                        .sum()
                                == 1,
                        "Missing/duplicate universal machinery drop");
                h.assertTrue(
                        drops.stream()
                                        .filter(s -> material != CladdingMaterial.NONE && s.is(material.item()))
                                        .mapToInt(ItemStack::getCount)
                                        .sum()
                                == (material == CladdingMaterial.NONE ? 0 : 1),
                        "Missing/duplicate material drop");
                h.assertTrue(drops.size() == (material == CladdingMaterial.NONE ? 1 : 2), "Unexpected drop");
                h.assertTrue(
                        block.getCloneItemStack(h.getLevel(), h.absolutePos(P), state)
                                .is(block.asItem()),
                        "Pick returned material-specific machinery");
            }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bufferContentsComponentsFilterAndEntitySurviveCladding(GameTestHelper h) {
        h.setBlock(P, FluidBufferContent.state(ConduitCladding.BARE));
        var entity = (FluidBufferEntity) h.getBlockEntity(P);
        entity.filter("minecraft:water");
        var fluid = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 750);
        fluid.set(
                net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Preserved"));
        entity.tank.setFluid(fluid);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        for (var material : List.of(CladdingMaterial.VAT_T1_CASING, CladdingMaterial.MIXER_T1_GLASS)) {
            click(h, P, player, new ItemStack(material.item()));
            h.assertTrue(
                    h.getBlockEntity(P) == entity
                            && FluidStack.matches(entity.tank.getFluid(), fluid)
                            && entity.filter().equals("minecraft:water"),
                    "Cladding changed buffer state/entity");
        }
        var drops = Block.getDrops(h.getBlockState(P), h.getLevel(), h.absolutePos(P), entity);
        var machinery = drops.stream()
                .filter(s -> s.is(entity.getBlockState().getBlock().asItem()))
                .findFirst()
                .orElseThrow();
        h.setBlock(P, Blocks.AIR);
        h.setBlock(P, FluidBufferContent.state(ConduitCladding.BARE));
        var restored = (FluidBufferEntity) h.getBlockEntity(P);
        EndpointState.restore(machinery, restored);
        h.assertTrue(
                FluidStack.matches(restored.tank.getFluid(), fluid)
                        && restored.filter().equals("minecraft:water"),
                "Buffer drop lost contents/components/filter");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void familyReplacementPreservesPortSettingsAndDormantResources(GameTestHelper h) {
        h.setBlock(P, Content.state(VatPartType.ItemPort));
        var vat = (VatItemPortEntity) h.getBlockEntity(P);
        vat.filter("minecraft:coal");
        vat.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        vat.staging().setStackInSlot(0, new ItemStack(Items.COAL, 3));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        click(h, P, player, new ItemStack(CladdingMaterial.MIXER_T1_GLASS.item()));
        var mixer = (MixerItemPortEntity) h.getBlockEntity(P);
        h.assertTrue(
                mixer.filter().equals("minecraft:coal")
                        && mixer.getIoDirection().isOutput(),
                "Family replacement lost settings");
        mixer.fuelStore().setStackInSlot(0, new ItemStack(Items.COAL, 2));
        click(h, P, player, new ItemStack(CladdingMaterial.VAT_T1_CASING.item()));
        vat = (VatItemPortEntity) h.getBlockEntity(P);
        h.assertTrue(vat.staging().getStackInSlot(0).getCount() == 3, "Replacement lost staged Vat items");
        click(h, P, player, new ItemStack(CladdingMaterial.MIXER_T1_CASING.item()));
        mixer = (MixerItemPortEntity) h.getBlockEntity(P);
        h.assertTrue(mixer.fuelStore().getStackInSlot(0).getCount() == 2, "Replacement lost staged Mixer fuel");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void playerCladdingFormsAndFamilyReplacementDisassembles(GameTestHelper h) {
        VatGameTests.build(h, 5);
        ((VatHeadEntity) h.getBlockEntity(new BlockPos(3, 3, 3)))
                .encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);
        var wall = new BlockPos(1, 2, 3);
        h.setBlock(
                wall,
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        h.runAtTickTime(30, () -> {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setShiftKeyDown(true);
            click(h, wall, player, new ItemStack(CladdingMaterial.VAT_T1_GLASS.item()));
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(
                    ((VatHeadEntity) h.getBlockEntity(new BlockPos(3, 3, 3))).isMachineAssembled(),
                    "Cladding failed formation");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setShiftKeyDown(true);
            click(h, wall, player, new ItemStack(CladdingMaterial.MIXER_T1_GLASS.item()));
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(
                    !((VatHeadEntity) h.getBlockEntity(new BlockPos(3, 3, 3))).isMachineAssembled(),
                    "Foreign family failed disassembly");
            h.assertTrue(
                    h.getBlockState(wall).getValue(ConduitBlock.MODE) == ConduitMode.TAP, "Cladding changed TAP mode");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void materialAndModeBlockstateSerializationIsExact(GameTestHelper h) {
        for (var resource : ConduitResource.values())
            for (var material : CladdingMaterial.values())
                for (var mode : ConduitMode.values()) {
                    var state = ConduitContent.state(resource, material.cladding(), material.shell())
                            .setValue(ConduitBlock.MODE, mode);
                    var restored = net.minecraft.nbt.NbtUtils.readBlockState(
                            h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                            net.minecraft.nbt.NbtUtils.writeBlockState(state));
                    h.assertTrue(state.equals(restored), "Blockstate serialization lost material/mode");
                }
        h.assertTrue(
                ConduitContent.blocks().size() == 2
                        && ConduitContent.items().size() == 4
                        && FluidBufferContent.blocks().size() == 1,
                "Material multiplied registry entries");
        long blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getNamespace().equals("mobvats"))
                .count();
        long items = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("mobvats"))
                .count();
        h.assertTrue(
                blocks == 40 && items == 44, "Unexpected registry count: " + blocks + " blocks, " + items + " items");
        org.slf4j.LoggerFactory.getLogger(AppliedCladdingGameTests.class)
                .info("Unified registry: {} blocks, {} items", blocks, items);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeBreakingSuppressesBothDrops(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        h.setBlock(P, FluidBufferContent.state(ConduitCladding.GLASS, ConduitShell.MIXER));
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(P)), "Creative destruction failed");
        h.assertTrue(
                h.getLevel()
                        .getEntitiesOfClass(
                                net.minecraft.world.entity.item.ItemEntity.class,
                                new AABB(h.absolutePos(P)).inflate(.5))
                        .isEmpty(),
                "Creative emitted material or machinery");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silkTouchAndCertainExplosionUseOneLootPath(GameTestHelper h) {
        var state = ConduitContent.state(ConduitResource.ITEM, ConduitCladding.GLASS, ConduitShell.VAT)
                .setValue(ConduitBlock.MODE, ConduitMode.TAP);
        h.setBlock(P, state);
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.enchant(
                h.getLevel()
                        .holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH),
                1);
        var builder = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(
                        net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        Vec3.atCenterOf(h.absolutePos(P)))
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, tool)
                .withParameter(
                        net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, 1.0f);
        var drops = state.getDrops(builder);
        h.assertTrue(
                drops.size() == 2
                        && drops.stream().mapToInt(ItemStack::getCount).sum() == 2
                        && drops.stream()
                                .anyMatch(stack -> stack.is(ConduitContent.item(ConduitResource.ITEM, ConduitMode.TAP)))
                        && drops.stream().anyMatch(stack -> stack.is(CladdingMaterial.VAT_T1_GLASS.item())),
                "Silk/explosion path duplicated or lost drops");
        h.succeed();
    }
}
