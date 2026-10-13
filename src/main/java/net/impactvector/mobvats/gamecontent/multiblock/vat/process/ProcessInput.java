package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Typed consumption plans; null means unavailable, with no mutation. */
public final class ProcessInput {
    public record Item(Predicate<ItemStack> predicate, int amount) {
        public Item {
            if (amount < 1) throw new IllegalArgumentException("amount");
        }

        public ProcessTransaction.Step plan(ItemStackHandler handler) {
            var before = handler.getStackInSlot(0);
            if (!predicate.test(before) || before.getCount() < amount) return null;
            var after = before.copy();
            after.shrink(amount);
            return ProcessTransaction.item(handler, after);
        }
    }

    public record Liquid(Fluid fluid, int amount) {
        public Liquid {
            if (amount < 1) throw new IllegalArgumentException("amount");
        }

        public ProcessTransaction.Step plan(FluidTank tank) {
            if (!tank.getFluid().is(fluid) || tank.getFluidAmount() < amount) return null;
            var after = tank.getFluid().copy();
            after.shrink(amount);
            return ProcessTransaction.fluid(tank, after);
        }
    }

    public record Experience(long amount) {
        public Experience {
            if (amount < 0) throw new IllegalArgumentException("amount");
        }

        public ProcessTransaction.Step plan(VatProcessEngine engine) {
            return engine.experienceMb() < amount
                    ? null
                    : ProcessTransaction.experienceMb(engine, engine.experienceMb() - amount);
        }
    }

    private ProcessInput() {}
}
