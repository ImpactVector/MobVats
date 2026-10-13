package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Historical test API only; runtime uses VanillaEntityLoot with the loaded identity. */
@Deprecated
public final class CowHarvestLoot {
    public record Result(List<ItemStack> items, int xp) {}

    public static boolean isWeapon(ItemStack stack) {
        return VanillaEntityLoot.isWeapon(stack);
    }

    public static Result roll(ServerLevel level, Vec3 position, ItemStack weapon, long seed) {
        var r = VanillaEntityLoot.roll(level, ResourceLocation.withDefaultNamespace("cow"), position, weapon, seed);
        return new Result(r.items(), r.xp());
    }

    public static ItemStack damageWeapon(ServerLevel level, ItemStack weapon) {
        return VanillaEntityLoot.damageWeapon(level, ResourceLocation.withDefaultNamespace("cow"), weapon);
    }

    private CowHarvestLoot() {}
}
