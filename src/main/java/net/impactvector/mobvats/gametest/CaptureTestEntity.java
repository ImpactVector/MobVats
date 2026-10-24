package net.impactvector.mobvats.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Test-only registered non-vanilla, non-CREATURE fixture; excluded from the distributable. */
@EventBusSubscriber(modid = "mobvats", bus = EventBusSubscriber.Bus.MOD)
public final class CaptureTestEntity {
    public static final ResourceLocation ID = ResourceLocation.parse("mobvats:capture_fixture");
    public static EntityType<Cow> TYPE;
    public static final java.util.Map<String, EntityType<Cow>> DISCOVERY = new java.util.HashMap<>();

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            TYPE = EntityType.Builder.of(Cow::new, MobCategory.MONSTER)
                    .sized(2.4f, 1.7f)
                    .build(ID.toString());
            helper.register(ID, TYPE);
            for (var name : java.util.List.of("passive", "nether", "end", "ambiguous", "boss")) {
                var category = name.equals("nether")
                        ? MobCategory.MONSTER
                        : name.equals("ambiguous") ? MobCategory.MISC : MobCategory.CREATURE;
                var key = ResourceLocation.parse("mobvats:discovery_" + name);
                var type = EntityType.Builder.of(Cow::new, category)
                        .sized(.9f, 1.4f)
                        .build(key.toString());
                DISCOVERY.put(name, type);
                helper.register(key, type);
            }
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(TYPE, Cow.createAttributes().build());
        DISCOVERY
                .values()
                .forEach(type -> event.put(type, Cow.createAttributes().build()));
    }
}
