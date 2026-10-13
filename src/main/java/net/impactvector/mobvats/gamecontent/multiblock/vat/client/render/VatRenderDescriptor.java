package net.impactvector.mobvats.gamecontent.multiblock.vat.client.render;

import net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One derived geometry snapshot per formation. No world/entity references or fluid simulation. */
public record VatRenderDescriptor(
        AABB vatBounds,
        AABB interiorBounds,
        BlockPos harnessPosition,
        BlockPos drainPosition,
        Vec3 specimenPosition,
        float specimenYaw,
        java.util.List<BlockPos> harnessPositions,
        AABB occupiedBounds,
        AABB attachmentBounds) {
    public static VatRenderDescriptor from(MultiblockVat vat, VatHeadEntity anchor) {
        var assembly = vat.getAssemblies().stream()
                .filter(a -> a.head() == anchor)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Render anchor must be a validated specimen head"));
        var min = vat.getBoundingBox().getMin();
        var max = vat.getBoundingBox().getMax();
        return new VatRenderDescriptor(
                new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max.offset(1, 1, 1))),
                new AABB(Vec3.atLowerCornerOf(min.offset(1, 1, 1)), Vec3.atLowerCornerOf(max)),
                assembly.harness().getBlockPos().immutable(),
                assembly.drain().getBlockPos().immutable(),
                assembly.renderPosition(),
                anchor.getSpecimenYaw(),
                assembly.harnessPositions(),
                assembly.occupiedBounds(),
                assembly.attachmentBounds());
    }
}
