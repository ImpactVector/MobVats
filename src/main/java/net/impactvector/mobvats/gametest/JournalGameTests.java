package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class JournalGameTests {
    @GameTest(template = "empty")
    public static void journalServerUsePreservesItem(GameTestHelper h) {
        var item = Content.JOURNAL.get();
        h.assertTrue(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("mobvats:research_journal")) == item,
                "Journal registered");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = item.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(
                result.getResult().consumesAction() && result.getObject() == stack && stack.getCount() == 1,
                "Server journal interaction succeeds without consuming or decorating the item");
        h.succeed();
    }
}
