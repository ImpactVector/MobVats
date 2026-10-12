package net.impactvector.mobvats.gamecontent.item;

import java.util.List;
import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** Shared adapter for persisted Essence and block-entity specimen data. */
public final class SpecimenPresentation {
    private SpecimenPresentation() {}

    public static CompoundTag data(ItemStack stack, boolean head) {
        var tag = stack.getOrDefault(head ? DataComponents.BLOCK_ENTITY_DATA : DataComponents.CUSTOM_DATA,
            CustomData.EMPTY).copyTag();
        return head && tag.contains("zcvase_payload", 10) ? tag.getCompound("zcvase_payload") : tag;
    }

    public static Component displayName(ItemStack stack, boolean head, Component fallback) {
        var tag = data(stack, head);
        if (tag.getString("entity").isEmpty()) return fallback;
        var id = ResourceLocation.tryParse(tag.getString("entity"));
        if (id == null) return fallback;
        var entityName = BuiltInRegistries.ENTITY_TYPE.containsKey(id)
            ? BuiltInRegistries.ENTITY_TYPE.get(id).getDescription() : Component.literal(id.toString());
        return Component.translatable(head ? "item.mobvats.specimen_head" : "item.mobvats.specimen_essence", entityName);
    }

    public static int harnessCount(SimulacrumGeometry geometry) {
        return geometry.harnessCorners(BlockPos.ZERO, geometry.height()).size();
    }

    public static void appendTooltip(ItemStack stack, boolean head, List<Component> tooltip, TooltipFlag flag) {
        var tag = data(stack, head);
        if (!tag.contains("entity")) return;
        try {
            // Legacy data follows the same migration as encoding; saved geometry is never re-resolved.
            var specimen = CapturedSpecimen.load(tag, Direction.NORTH);
            var g = specimen.geometry();
            int harnesses = harnessCount(g);
            tooltip.add(Component.translatable("tooltip.mobvats.geometry", g.width(), g.height(), g.depth()));
            tooltip.add(Component.translatable(harnesses == 1 ? "tooltip.mobvats.support_single" : "tooltip.mobvats.support_plural", harnesses));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable("tooltip.mobvats.entity", specimen.identity().toString()));
                tooltip.add(Component.translatable("tooltip.mobvats.head_offset", g.headOffset().getX(), g.headOffset().getY(), g.headOffset().getZ()));
                tooltip.add(Component.translatable("tooltip.mobvats.facing", Component.translatable("tooltip.mobvats.facing." + g.facing().getSerializedName())));
                tooltip.add(Component.translatable("tooltip.mobvats.harnesses", harnesses));
                tooltip.add(Component.translatable("tooltip.mobvats.drain", 1));
                tooltip.add(Component.translatable("tooltip.mobvats.geometry_version", g.save().getInt("version")));
            }
        } catch (IllegalArgumentException error) {
            tooltip.add(Component.translatable("tooltip.mobvats.invalid_geometry", error.getMessage()));
        }
    }
}
