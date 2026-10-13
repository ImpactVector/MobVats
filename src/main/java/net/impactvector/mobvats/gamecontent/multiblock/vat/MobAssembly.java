package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Head-owned specimen with independently associated support and waste endpoints. */
public record MobAssembly(List<VatHarnessEntity> harnesses, VatHeadEntity head, VatSumpEntity drain) {
    public MobAssembly {
        harnesses = List.copyOf(harnesses);
    }

    public VatHarnessEntity harness() {
        return harnesses.getFirst();
    }

    public int fleshCount() {
        var g = head.geometry();
        return g.width() * g.height() * g.depth() - 1;
    }

    public AABB occupiedBounds() {
        return head.geometry().occupiedBounds(head.getBlockPos());
    }

    public List<net.minecraft.core.BlockPos> harnessPositions() {
        return harnesses.stream().map(h -> h.getBlockPos().immutable()).toList();
    }

    public SpecimenClearance clearance(net.minecraft.world.level.Level world) {
        return SpecimenClearance.inspect(
                world,
                head.geometry(),
                head.getBlockPos(),
                head.getMultiblockController().map(MultiblockVat::getAssemblies).orElse(List.of()));
    }
    /** Stable adult envelope: world routes target substrate, never a moving client entity. */
    public net.minecraft.world.phys.AABB bodyBounds() {
        return head.geometry().bodyBounds(head.getBlockPos(), 1);
    }
    /** Stage-specific attachment geometry, separate from the stable substrate reach envelope. */
    public net.minecraft.world.phys.AABB attachmentBounds() {
        return head.geometry()
                .bodyBounds(
                        head.getBlockPos(),
                        head.lifeStage()
                                        == net.impactvector.mobvats.gamecontent.multiblock.vat.process.LifeStage
                                                .JUVENILE
                                ? .5
                                : 1);
    }

    public Vec3 renderPosition() {
        return head.geometry().renderPosition(head.getBlockPos());
    }
}
