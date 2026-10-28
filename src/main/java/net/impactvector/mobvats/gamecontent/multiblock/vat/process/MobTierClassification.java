package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

/** Deterministic generation policy. Existing files never pass through this classifier again. */
public final class MobTierClassification {
    private static final Set<String> BOSSES = Set.of("wither", "ender_dragon", "elder_guardian", "warden");
    private static final Map<String, Integer> ORDINARY_TIERS =
            Map.of("villager", 1, "iron_golem", 2, "skeleton", 2, "snow_golem", 2);
    private static final Set<String> OTHERWORLDLY = Set.of(
            "evoker",
            "breeze",
            "guardian",
            "illusioner",
            "phantom",
            "enderman",
            "endermite",
            "shulker",
            "blaze",
            "ghast",
            "magma_cube",
            "piglin",
            "piglin_brute",
            "zombified_piglin",
            "hoglin",
            "zoglin",
            "strider",
            "wither_skeleton",
            "vex");
    public static final Set<String> SIGNAL_TAGS = Set.of(
            "c:bosses",
            "mobvats:nether_mobs",
            "mobvats:end_mobs",
            "mobvats:teleporting_mobs",
            "mobvats:supernatural_mobs");

    public record Result(int tier, boolean excluded, boolean ambiguous, List<String> reasons) {}

    public static boolean builtInBoss(ResourceLocation id) {
        return id.getNamespace().equals("minecraft") && BOSSES.contains(id.getPath());
    }

    public static Result classify(ResourceLocation id, MobCategory category, Set<String> tags, Set<String> regions) {
        var reasons = new ArrayList<String>();
        reasons.add("category=" + category.getName());
        new TreeSet<>(tags)
                .forEach(tag ->
                        reasons.add((SIGNAL_TAGS.contains(tag) ? "recognized tag=" : "unclassified tag=") + tag));
        new TreeSet<>(regions).forEach(region -> reasons.add("biome spawn affinity=" + region));
        if (builtInBoss(id) || tags.contains("c:bosses")) {
            reasons.add("boss excluded from automatic eligibility and generic farming");
            return new Result(3, true, false, List.copyOf(reasons));
        }
        var ordinaryTier = id.getNamespace().equals("minecraft") ? ORDINARY_TIERS.get(id.getPath()) : null;
        if (ordinaryTier != null) {
            reasons.add("explicit built-in tier assignment=" + id);
            return new Result(ordinaryTier, false, false, List.copyOf(reasons));
        }
        if (id.getNamespace().equals("minecraft") && OTHERWORLDLY.contains(id.getPath())) {
            reasons.add("explicit built-in otherworldly/containment exception=" + id);
            return new Result(3, false, false, List.copyOf(reasons));
        }
        if (tags.stream().anyMatch(tag -> SIGNAL_TAGS.contains(tag) && !tag.equals("c:bosses"))
                || regions.contains("nether")
                || regions.contains("end")) {
            reasons.add("otherworldly affinity or containment tag");
            return new Result(3, false, false, List.copyOf(reasons));
        }
        if (category == MobCategory.MONSTER) {
            boolean overworld =
                    regions.contains("overworld") || id.getNamespace().equals("minecraft");
            reasons.add(
                    overworld
                            ? "ordinary Overworld hostile"
                            : "hostile with unresolved native affinity; conservative T3");
            return new Result(overworld ? 2 : 3, false, !overworld, List.copyOf(reasons));
        }
        if (category != MobCategory.MISC) {
            reasons.add("non-hostile mob category; abilities and lifecycle not inferred");
            return new Result(1, false, false, List.copyOf(reasons));
        }
        reasons.add("uncategorized mob; conservative T3; review required");
        return new Result(3, false, true, List.copyOf(reasons));
    }

    private MobTierClassification() {}
}
