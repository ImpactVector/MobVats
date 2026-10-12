package net.impactvector.mobvats.gametest;

import java.util.ArrayList;
import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class ConduitItemGameTests {
    static void place(GameTestHelper h, BlockPos pos, ConduitResource resource, ConduitCladding cladding, ConduitMode mode) {
        place(h, pos, new ItemStack(ConduitContent.item(resource, mode)));
        if(cladding!=ConduitCladding.BARE){
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(true);
            var material=CladdingMaterial.of(cladding,ConduitShell.VAT);var stack=new ItemStack(material.item());
            var absolute=h.absolutePos(pos);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            h.getLevel().getBlockState(absolute).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false));
            h.assertTrue(CladdingMaterial.installed(h.getLevel().getBlockState(absolute))==material && stack.isEmpty(),"Player cladding failed");
        }
    }

    static void place(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var item = (ConduitBlockItem)stack.getItem();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(pos.offset(0, 0, 3))));
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var absolute = h.absolutePos(pos);
        var result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        h.assertTrue(result.consumesAction() && player.getMainHandItem().isEmpty(), "Placement failed or did not consume conduit item");
        var state = h.getLevel().getBlockState(absolute);
        h.assertTrue(state.is(item.getBlock()) && state.getValue(ConduitBlock.MODE) == item.mode(),
            "Item placed wrong block/mode: " + item);
    }

    @GameTest(template="empty", timeoutTicks=10)
    public static void allTenFamiliesPlaceAndDropBothModes(GameTestHelper h) {
        int family = 0;
        for (var resource : ConduitResource.values()) for (var cladding : ConduitCladding.values()) for(var shell:cladding==ConduitCladding.BARE?List.of(ConduitShell.SHARED):List.of(ConduitShell.VAT,ConduitShell.MIXER)) {
            var block = ConduitContent.block(resource);
            h.assertTrue(block.asItem() == ConduitContent.item(resource, ConduitMode.TRUNK), "Default block item changed from Trunk");
            for (var mode : ConduitMode.values()) {
                var pos = new BlockPos(1 + family, 2, 1 + mode.ordinal() * 2);
                h.setBlock(pos, Blocks.AIR);
                place(h,pos,new ItemStack(ConduitContent.item(resource,mode)));
                var absolute = h.absolutePos(pos);
                var state = h.getLevel().getBlockState(absolute);
                var player = h.makeMockPlayer(GameType.SURVIVAL);
                state.useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
                h.assertTrue(h.getLevel().getBlockState(absolute).equals(state), "Empty-hand interaction changed mode");
                h.assertTrue(block.getCloneItemStack(h.getLevel(), absolute, state).is(ConduitContent.item(resource, mode)), "Pick block lost mode");
                h.assertTrue(h.getLevel().destroyBlock(absolute, true, player), "Breaking failed");
                var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(0.5));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1
                    && drops.getFirst().getItem().is(ConduitContent.item(resource, mode)), "Breaking lost mode or duplicated drops: " + resource + "/" + cladding + "/" + mode);
                var dropped = drops.getFirst().getItem().copy();
                drops.getFirst().discard();
                // Re-place the actual drop through the normal item interaction path.
                place(h, pos, dropped);
                h.assertTrue(h.getLevel().getBlockState(absolute).equals(state), "Dropped item did not restore placed mode");
            }
            family++;
        }
        h.assertTrue(family == 10 && ConduitContent.blocks().size() == 2 && ConduitContent.items().size() == 4, "Expected ten resource/shell variants and twenty inventory forms");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=10)
    public static void allTenFamiliesCraftBothDirections(GameTestHelper h) {
        for (var resource : ConduitResource.values()) for (var cladding : ConduitCladding.values()) for(var shell:cladding==ConduitCladding.BARE?List.of(ConduitShell.SHARED):List.of(ConduitShell.VAT,ConduitShell.MIXER)) for (var mode : ConduitMode.values()) {
            for (int size : List.of(2, 3)) {
                var stacks = new ArrayList<ItemStack>();
                for (int slot = 0; slot < size * size; slot++) stacks.add(ItemStack.EMPTY);
                stacks.set(size * size - 1, new ItemStack(ConduitContent.item(resource, mode)));
                var input = CraftingInput.of(size, size, stacks);
                var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
                var result = recipe.value().assemble(input, h.getLevel().registryAccess());
                var target = mode == ConduitMode.TRUNK ? ConduitMode.TAP : ConduitMode.TRUNK;
                h.assertTrue(recipe.value() instanceof ShapelessRecipe && result.getCount() == 1
                    && result.is(ConduitContent.item(resource, target)), "Conversion recipe failed: " + resource + "/" + cladding + "/" + mode);
                place(h, new BlockPos(1, 2, 1), result);
                h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
            }
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=10)
    public static void creativeTabContainsBothModesForAllTenFamilies(GameTestHelper h) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("mobvats", "mobvats"));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        for (var resource : ConduitResource.values()) for (var cladding : ConduitCladding.values()) for(var shell:cladding==ConduitCladding.BARE?List.of(ConduitShell.SHARED):List.of(ConduitShell.VAT,ConduitShell.MIXER)) for (var mode : ConduitMode.values()) {
            var item = ConduitContent.item(resource, mode);
            h.assertTrue(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item))
                && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)), "Creative inventory missing " + item);
        }
        h.succeed();
    }
}
