package net.impactvector.mobvats.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.*;
import net.impactvector.mobvats.gamecontent.mob.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class SpecimenPresentationGameTests {
    private static TranslatableContents translated(Component component) {
        return (TranslatableContents) component.getContents();
    }

    private static ItemStack essence(ResourceLocation id, SimulacrumGeometry geometry) {
        var stack = new ItemStack(Content.ESSENCE.get());
        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(new CapturedSpecimen(id, geometry, new CompoundTag()).save()));
        return stack;
    }

    private static ArrayList<Component> tooltip(ItemStack stack, TooltipFlag flag) {
        var lines = new ArrayList<Component>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, flag);
        return lines;
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void genericNamesAndSavedGeometry(GameTestHelper h) throws Exception {
        try (var stream = SpecimenPresentationGameTests.class.getResourceAsStream("/assets/mobvats/lang/en_us.json")) {
            var lang = com.google.gson.JsonParser.parseReader(
                            new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            h.assertTrue(
                    lang.get("item.mobvats.essence").getAsString().equals("Essence")
                            && !lang.get("multiblock.mobvats.substrate")
                                    .getAsString()
                                    .contains("cow")
                            && !lang.get("multiblock.mobvats.room")
                                    .getAsString()
                                    .contains("cow"),
                    "Stale Cow fallback/validation names");
        }
        h.assertTrue(
                translated(new ItemStack(Content.ESSENCE.get()).getHoverName())
                        .getKey()
                        .equals("item.mobvats.essence"),
                "Blank Essence fallback changed: " + new ItemStack(Content.ESSENCE.get()).getHoverName());
        h.assertTrue(
                translated(new ItemStack(Content.block(VatPartType.Head)).getHoverName())
                        .getKey()
                        .equals("block.mobvats.vat_head"),
                "Blank Head fallback changed");
        for (var id : java.util.List.of(ItemEssence.COW, ResourceLocation.withDefaultNamespace("sheep"))) {
            var g = new SimulacrumGeometry(3, 4, 2, new BlockPos(1, 2, 1), Direction.NORTH, 2.2, 3.1, 1.5);
            var stack = essence(id, g);
            var name = translated(stack.getHoverName());
            h.assertTrue(
                    name.getKey().equals("item.mobvats.specimen_essence")
                            && name.getArgs()[0].equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                    .get(id)
                                    .getDescription()),
                    "Essence name ignored entity identity");
            var pos = new BlockPos(2, 2, 2);
            h.setBlock(pos, Content.state(VatPartType.Head));
            var head = (VatHeadEntity) h.getBlockEntity(pos);
            ItemEssence.encode(stack, head);
            var clone = Content.block(VatPartType.Head)
                    .getCloneItemStack(h.getLevel(), h.absolutePos(pos), head.getBlockState());
            h.assertTrue(
                    translated(clone.getHoverName()).getKey().equals("item.mobvats.specimen_head")
                            && translated(clone.getHoverName()).getArgs()[0].equals(name.getArgs()[0]),
                    "Encoded Head name ignored identity");
            var basic = tooltip(stack, TooltipFlag.NORMAL);
            var advanced = tooltip(stack, TooltipFlag.ADVANCED);
            h.assertTrue(
                    basic.equals(tooltip(clone, TooltipFlag.NORMAL))
                            && advanced.equals(tooltip(clone, TooltipFlag.ADVANCED)),
                    "Essence/Head requirements differ");
            h.assertTrue(
                    basic.size() == 2 && Arrays.equals(translated(basic.get(0)).getArgs(), new Object[] {3, 4, 2}),
                    "Tooltip recomputed persisted dimensions");
            h.assertTrue(
                    advanced.stream()
                                    .anyMatch(c -> translated(c).getKey().equals("tooltip.mobvats.head_offset")
                                            && Arrays.equals(translated(c).getArgs(), new Object[] {1, 2, 1}))
                            && advanced.stream()
                                    .anyMatch(c -> translated(c).getKey().equals("tooltip.mobvats.entity")
                                            && translated(c).getArgs()[0].equals(id.toString())),
                    "Advanced tooltip lost persisted offset/identity");
            h.setBlock(pos, Blocks.AIR);
        }
        int[][] footprints = {{1, 1, 1}, {1, 5, 2}, {5, 1, 2}, {3, 3, 4}};
        for (var f : footprints) {
            var g = new SimulacrumGeometry(f[0], 2, f[1], new BlockPos(0, 1, 0), Direction.WEST, 1, 1, 1);
            h.assertTrue(
                    SpecimenPresentation.harnessCount(g) == f[2], "Harness count disagrees with footprint corners");
            h.assertTrue(
                    translated(tooltip(essence(ItemEssence.COW, g), TooltipFlag.NORMAL)
                                    .get(1))
                            .getArgs()[0]
                            .equals(f[2]),
                    "Tooltip support count disagrees with validator");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void clonedHeadPlacementPreservesSpecimenAndPlacedFacing(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, Content.state(VatPartType.Head));
        var g = new SimulacrumGeometry(3, 4, 2, new BlockPos(1, 2, 1), Direction.EAST, 2.2, 3.1, 1.5);
        var state = new CompoundTag();
        state.putByte("Color", (byte) 11);
        var head = (VatHeadEntity) h.getBlockEntity(pos);
        head.encode(ResourceLocation.withDefaultNamespace("sheep"), g, state);
        var clone = Content.block(VatPartType.Head)
                .getCloneItemStack(h.getLevel(), h.absolutePos(pos), head.getBlockState());
        var originalId = head.specimenId();
        h.setBlock(pos, Blocks.AIR);
        h.setBlock(pos.below(), Blocks.STONE);
        var player = h.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(Direction.NORTH.toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, clone);
        var clicked = h.absolutePos(pos.below());
        clone.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
        var placed = (VatHeadEntity) h.getBlockEntity(pos);
        h.assertTrue(
                placed != null
                        && placed.getIdentity().equals(ResourceLocation.withDefaultNamespace("sheep"))
                        && placed.geometry().equals(g.withFacing(Direction.SOUTH))
                        && placed.specimenState().equals(state)
                        && !placed.specimenId().equals(originalId),
                "Clone placement lost specimen data, reused process identity or overrode placement facing: identity="
                        + (placed == null ? null : placed.getIdentity()) + " geometry="
                        + (placed == null ? null : placed.geometry()) + " state="
                        + (placed == null ? null : placed.specimenState()) + " block=" + h.getBlockState(pos));
        var drops = net.minecraft.world.level.block.Block.getDrops(
                placed.getBlockState(), h.getLevel(), placed.getBlockPos(), placed);
        h.assertTrue(
                drops.size() == 1 && !drops.getFirst().has(DataComponents.BLOCK_ENTITY_DATA),
                "Existing blank Head drop semantics changed");
        h.succeed();
    }
}
