package net.impactvector.mobvats.item;

import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.impactvector.mobvats.MobVats;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class ModItemGroup {
    public static final ItemGroup MEAT = FabricItemGroupBuilder.build(new Identifier(MobVats.MOD_ID, "meat"),
            () -> new ItemStack(ModItems.MEAT_BUCKET));
}
