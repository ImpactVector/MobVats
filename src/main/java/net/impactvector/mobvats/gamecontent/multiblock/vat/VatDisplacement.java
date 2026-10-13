package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Numeric extension point for interior volume displacement, in mB per cell. */
public final class VatDisplacement {
    public interface Provider {
        int vatDisplacement(Level level, BlockPos pos);
    }

    private static final Map<Block, ToIntBiFunction<Level, BlockPos>> RULES = new IdentityHashMap<>();

    public static void register(Block block, ToIntBiFunction<Level, BlockPos> rule) {
        RULES.put(block, Objects.requireNonNull(rule));
    }

    public static int displaced(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        var rule = RULES.get(block);
        int value = rule != null
                ? rule.applyAsInt(level, pos)
                : block instanceof Provider provider ? provider.vatDisplacement(level, pos) : 0;
        if (value < 0 || value > 1000) throw new IllegalArgumentException("Vat displacement must be 0..1000: " + value);
        return value;
    }

    private VatDisplacement() {}
}
