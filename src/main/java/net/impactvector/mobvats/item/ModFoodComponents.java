package net.impactvector.mobvats.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;

public class ModFoodComponents {
    public static FoodComponent MEAT_NUGGET = (new FoodComponent.Builder()).hunger(2).saturationModifier(0.2F).statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200, 0), 0.3F).meat().build();
    public static FoodComponent MEAT_BAR = (new FoodComponent.Builder()).hunger(6).saturationModifier(0.6F).statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 600, 0), 0.3F).meat().build();
    public static FoodComponent COOKED_MEAT_NUGGET = (new FoodComponent.Builder()).hunger(4).saturationModifier(0.4F).meat().build();
    public static FoodComponent NUTRIENT_BAR = (new FoodComponent.Builder()).hunger(6).saturationModifier(1.0F).build();
}
