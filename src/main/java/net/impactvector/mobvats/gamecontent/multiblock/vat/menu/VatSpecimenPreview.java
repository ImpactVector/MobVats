package net.impactvector.mobvats.gamecontent.multiblock.vat.menu;

import java.util.UUID;
import net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatSpecimenSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** Screen-owned presentation state. Never added to a level, spawned, or ticked. No client classes. */
public final class VatSpecimenPreview {
    private record Key(UUID id, String species, boolean adult, String appearance) {}

    private Key key;
    private Level level;
    private Mob entity;

    public Mob update(VatSpecimenSnapshot selected, Level world) {
        if (selected == null || world == null) {
            clear();
            return null;
        }
        var next = new Key(selected.id(), selected.species(), selected.adult(), selected.appearance());
        if (next.equals(key) && level == world) return entity;
        clear();
        key = next;
        level = world;
        try {
            var id = ResourceLocation.tryParse(next.species());
            var type = id == null
                    ? null
                    : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null || !(type.create(world) instanceof Mob mob)) return null;
            CapturedSpecimen.apply(mob, CapturedSpecimen.appearance(TagParser.parseTag(next.appearance())));
            // Runtime life stage overrides the original capture's baby flag after maturation.
            mob.setBaby(!next.adult());
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setSilent(true);
            mob.setCustomNameVisible(false);
            mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = 155;
            mob.setYRot(155);
            mob.yRotO = 155;
            mob.setXRot(0);
            mob.xRotO = 0;
            entity = mob;
        } catch (Exception error) {
            failed(error);
        }
        return entity;
    }

    /** Retain the failed key so creation/render failures are attempted only once per identity. */
    public void failed(Exception error) {
        entity = null;
        com.mojang.logging.LogUtils.getLogger().debug("Vat menu preview unavailable for {}", key, error);
    }

    public void clear() {
        entity = null;
        key = null;
        level = null;
    }
}
