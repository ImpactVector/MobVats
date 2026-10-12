package net.impactvector.mobvats.gamecontent.multiblock.conduit;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** All requirements in one strategy must be acquired together. Selectors belong to the operation. */
public record ResourceBundle(List<ItemDemand> items,List<FluidDemand> fluids) {
    public ResourceBundle { items=List.copyOf(items);fluids=List.copyOf(fluids); }
    public record ItemDemand(Predicate<ItemStack> selector,int count) {
        public ItemDemand { Objects.requireNonNull(selector);if(count<=0)throw new IllegalArgumentException("count"); }
    }
    public record FluidDemand(Predicate<FluidStack> selector,int amount) {
        public FluidDemand { Objects.requireNonNull(selector);if(amount<=0)throw new IllegalArgumentException("amount"); }
    }
    public record Strategy(String id,ResourceBundle inputs) {
        public Strategy { Objects.requireNonNull(id);Objects.requireNonNull(inputs); }
    }
    public static ResourceBundle item(Predicate<ItemStack> selector,int count){return new ResourceBundle(List.of(new ItemDemand(selector,count)),List.of());}
}
