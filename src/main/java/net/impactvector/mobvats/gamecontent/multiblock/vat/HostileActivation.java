package net.impactvector.mobvats.gamecontent.multiblock.vat;

import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHarnessEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;

/** Consumes vanilla's actual strike event, emitted after LightningBolt powers its struck rod. */
public final class HostileActivation {
    public static void lightningStrike(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !event.getVanillaEvent().equals(GameEvent.LIGHTNING_STRIKE)
                || !(event.getCause() instanceof LightningBolt bolt)) return;
        // Match LightningBolt#getStrikePosition, including the top-surface epsilon.
        var rod = BlockPos.containing(bolt.getX(), bolt.getY() - 1.0E-6, bolt.getZ());
        var state = level.getBlockState(rod);
        if (!state.is(Blocks.LIGHTNING_ROD)
                || state.getValue(LightningRodBlock.FACING) != Direction.UP
                || !(level.getBlockEntity(rod.below()) instanceof VatHarnessEntity harness)) return;
        harness.getMultiblockController().ifPresent(vat -> {
            if (!vat.isAssembled()
                    || harness.getBlockPos().getY()
                            != vat.getBoundingBox().getMax().getY()) return;
            // Revalidate current blocks: a strike must not exploit pending topology invalidation.
            if (!vat.validForLightning()) return;
            var owners = vat.getAssemblies().stream()
                    .filter(a -> a.harnesses().contains(harness))
                    .toList();
            if (owners.size() != 1) return;
            var head = owners.getFirst().head();
            if (!head.hostile()) return;
            try {
                SpecimenGeometry.substrate(level, new SpecimenGeometry.Claim(head.getBlockPos(), head.geometry()));
                head.activateFromLightning();
            } catch (IllegalArgumentException ignored) {
                // A malformed or incomplete specimen never receives activation.
            }
        });
    }

    private HostileActivation() {}
}
