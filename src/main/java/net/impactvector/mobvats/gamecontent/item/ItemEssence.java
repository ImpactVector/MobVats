package net.impactvector.mobvats.gamecontent.item;

import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class ItemEssence extends Item {
    public static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");

    public ItemEssence(Properties p) {
        super(p);
    }

    @Override
    public net.minecraft.network.chat.Component getName(ItemStack stack) {
        return SpecimenPresentation.displayName(stack, false, super.getName(stack));
    }

    public static ResourceLocation identity(ItemStack stack) {
        var value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getString("entity");
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> tooltip,
            TooltipFlag flag) {
        SpecimenPresentation.appendTooltip(stack, false, tooltip, flag);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var id = identity(context.getItemInHand());
        if (id == null
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof VatHeadEntity head)
                || head.isMachineAssembled()
                || head.getIdentity() != null
                        && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()))
            return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            try {
                encode(
                        context.getItemInHand(),
                        head,
                        context.getPlayer() != null && context.getPlayer().isShiftKeyDown());
            } catch (IllegalArgumentException error) {
                if (context.getPlayer() != null)
                    context.getPlayer()
                            .displayClientMessage(
                                    net.minecraft.network.chat.Component.literal(error.getMessage()), true);
                return InteractionResult.FAIL;
            }
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)
                context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    public static void encode(ItemStack essence, VatHeadEntity head) {
        encode(essence, head, false);
    }

    /** Sneak-use explicitly replaces an existing encoding on an unassembled Head. */
    public static void encode(ItemStack essence, VatHeadEntity head, boolean replace) {
        if (head.isMachineAssembled() || head.getIdentity() != null && !replace)
            throw new IllegalArgumentException("Head must be unassembled; sneak-use to replace an encoding");
        var specimen = net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen.load(
                essence.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag(),
                head.getSpecimenFacing());
        var profile = net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(
                specimen.identity());
        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(specimen.identity()))
            throw new IllegalArgumentException("Unknown Essence entity: " + specimen.identity());
        if (profile == null)
            throw new IllegalArgumentException("Definition missing; encoding unavailable: " + specimen.identity());
        if (!profile.capture().encoding_enabled())
            throw new IllegalArgumentException("Encoding disabled: " + specimen.identity());
        // Captured yaw describes the source mob; placement controls the vat specimen's orientation.
        head.encode(specimen.identity(), specimen.geometry().withFacing(head.getSpecimenFacing()), specimen.state());
    }
}
