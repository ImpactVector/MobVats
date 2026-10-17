package net.impactvector.mobvats.gamecontent.mob;

import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Identity/presentation only. Never stores equipment, AI, UUID, navigation or combat state. */
public record CapturedSpecimen(ResourceLocation identity, SimulacrumGeometry geometry, CompoundTag state) {
    private static final Set<String> FIELDS = Set.of(
            "Color",
            "Sheared",
            "Type",
            "RabbitType",
            "Variant",
            "variant",
            "IsScreamingGoat",
            "HasLeftHorn",
            "HasRightHorn",
            "Size",
            "PuffState",
            "MainGene",
            "HiddenGene",
            "CollarColor",
            "Baby",
            "IsBaby");
    private static final Set<String> INTERACTION_FIELDS = new java.util.HashSet<>();
    /** Code adapters may opt persisted state into the capture contract; JSON cannot add NBT fields. */
    public static synchronized void registerInteractionStateField(String field) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("Empty interaction state field");
        INTERACTION_FIELDS.add(field);
    }

    public CapturedSpecimen {
        java.util.Objects.requireNonNull(identity);
        java.util.Objects.requireNonNull(geometry);
        state = filter(state);
    }

    @Override
    public CompoundTag state() {
        return state.copy();
    }

    public static CompoundTag filter(CompoundTag source) {
        var result = appearance(source);
        if (source != null)
            for (var field : INTERACTION_FIELDS)
                if (source.contains(field)) result.put(field, source.get(field).copy());
        return result;
    }

    /** Fixed presentation allowlist, excluding adapter-owned interaction state. */
    public static CompoundTag appearance(CompoundTag source) {
        var result = new CompoundTag();
        if (source != null)
            for (var key : FIELDS)
                if (source.contains(key)
                        && (source.get(key) instanceof net.minecraft.nbt.NumericTag || source.contains(key, 8)))
                    result.put(key, source.get(key).copy());
        if (source != null && source.contains("VillagerData", 10)) {
            var original = source.getCompound("VillagerData");
            var villager = new CompoundTag();
            for (var key : Set.of("type", "profession"))
                if (original.contains(key, 8)) villager.putString(key, original.getString(key));
            if (original.contains("level", 3)) villager.putInt("level", original.getInt("level"));
            result.put("VillagerData", villager);
        }
        if (source != null && source.contains("CustomName", 8))
            result.putString("CustomName", source.getString("CustomName"));
        return result;
    }
    /** Reject changes to unpersisted state instead of silently losing a mod's interaction state. */
    public static boolean preservesInteractionChanges(CompoundTag before, CompoundTag after) {
        return withoutPersisted(before, filter(before)).equals(withoutPersisted(after, filter(after)));
    }

    private static CompoundTag withoutPersisted(CompoundTag source, CompoundTag persisted) {
        var remaining = source.copy();
        for (var key : persisted.getAllKeys()) {
            if (source.get(key) instanceof CompoundTag raw && persisted.get(key) instanceof CompoundTag saved) {
                var child = withoutPersisted(raw, saved);
                if (child.isEmpty()) remaining.remove(key);
                else remaining.put(key, child);
            } else remaining.remove(key);
        }
        return remaining;
    }

    public static CapturedSpecimen capture(LivingEntity target) {
        var saved = new CompoundTag();
        target.addAdditionalSaveData(saved);
        saved.putBoolean("Baby", target.isBaby());
        if (target.getCustomName() != null)
            saved.putString(
                    "CustomName",
                    net.minecraft.network.chat.Component.Serializer.toJson(
                            target.getCustomName(), target.registryAccess()));
        return new CapturedSpecimen(
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()), SimulacrumGeometry.capture(target), saved);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("specimen_version", 1);
        tag.putString("entity", identity.toString());
        tag.put("geometry", geometry.save());
        tag.put("specimen_state", state());
        return tag;
    }

    public static CapturedSpecimen load(CompoundTag tag, Direction facing) {
        if (tag.contains("specimen_version") && tag.getInt("specimen_version") != 1)
            throw new IllegalArgumentException("Unsupported specimen version");
        var id = tag.getString("entity").isEmpty() ? null : ResourceLocation.tryParse(tag.getString("entity"));
        if (id == null) throw new IllegalArgumentException("Essence identity missing or invalid");
        var geometry = tag.contains("geometry")
                ? SimulacrumGeometry.load(tag.getCompound("geometry"))
                : SimulacrumGeometry.legacy(id, facing);
        return new CapturedSpecimen(id, geometry, tag.getCompound("specimen_state"));
    }

    public static void apply(LivingEntity entity, CompoundTag state) {
        var defaults = new CompoundTag();
        entity.addAdditionalSaveData(defaults);
        defaults.merge(filter(state));
        entity.readAdditionalSaveData(defaults);
        if (state.contains("CustomName", 8))
            entity.setCustomName(net.minecraft.network.chat.Component.Serializer.fromJson(
                    state.getString("CustomName"), entity.registryAccess()));
    }
}
