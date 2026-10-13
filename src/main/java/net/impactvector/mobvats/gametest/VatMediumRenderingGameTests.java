package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatMediumRenderingGameTests {
    @GameTest(template = "empty")
    public static void glassClippingSurfaceAndPresentationLevels(GameTestHelper h) {
        var min = h.absolutePos(new BlockPos(2, 2, 2));
        var max = min.offset(4, 4, 4);
        for (int y = 1; y < 4; y++)
            h.getLevel().setBlockAndUpdate(min.offset(0, y, 2), Content.state(VatPartType.Glass));
        var m = new VatMediumStorage(() -> {});
        m.compile(h.getLevel(), min, max);
        h.assertTrue(
                m.getCapacity() == 27000
                        && VatMediumMesh.bake(h.getLevel(), min, max, m).faces().isEmpty(),
                "Empty medium has mesh or capacity changed");
        m.fill(new FluidStack(Fluids.WATER, 13500), EXECUTE);
        var mesh = VatMediumMesh.bake(h.getLevel(), min, max, m);
        double height = min.getY() + 2.5;
        h.assertTrue(mesh.height() == height, "Half-full height incorrect");
        var window = mesh.faces().stream()
                .filter(f -> f.normal() == Direction.WEST && f.vertices().getFirst().y == min.getY() + 2)
                .findFirst()
                .orElseThrow();
        h.assertTrue(
                window.vertices().stream().mapToDouble(p -> p.y).max().orElseThrow() == height,
                "Mid-height glass not half covered");
        h.assertTrue(
                mesh.faces().stream()
                        .noneMatch(f ->
                                f.normal() == Direction.WEST && f.vertices().getFirst().y >= min.getY() + 3),
                "Dry glass tinted above fill height");
        double area = 0;
        for (var face : mesh.faces()) {
            var p = face.vertices();
            Vec3 n = Vec3.atLowerCornerOf(face.normal().getNormal());
            h.assertTrue(
                    p.get(1)
                                    .subtract(p.getFirst())
                                    .cross(p.get(2).subtract(p.getFirst()))
                                    .dot(n)
                            > 0,
                    "Medium face winding incorrect");
            if (face.normal() == Direction.UP) {
                h.assertTrue(
                        p.stream().allMatch(v -> v.y == height - VatMediumMesh.INSET),
                        "Top surface has gaps in height");
                area += p.get(1)
                        .subtract(p.getFirst())
                        .cross(p.get(2).subtract(p.getFirst()))
                        .length();
            }
        }
        h.assertTrue(
                Math.abs(area - Math.pow(5 - 2 * VatMediumMesh.INSET, 2)) < 1e-6,
                "Top surface overlaps or leaves gaps: area=" + area);
        var client = new VatMediumStorage(() -> {});
        client.load(m.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        client.loadPresentationLevels(m.presentationLevels());
        h.assertTrue(
                client.getCapacity() == m.getCapacity() && VatMediumMesh.height(client, min, max) == height,
                "Network view lost displacement-aware height");
        m.fill(new FluidStack(Fluids.WATER, 27000), EXECUTE);
        h.assertTrue(VatMediumMesh.height(m, min, max) == max.getY(), "Full medium exceeds enclosure");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void displacementControlsLiquidHeight(GameTestHelper h) {
        var min = h.absolutePos(new BlockPos(2, 2, 2));
        var max = min.offset(3, 3, 3);
        var displaced = min.offset(1, 1, 1);
        VatDisplacement.register(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK, (level, pos) -> 1000);
        h.getLevel()
                .setBlockAndUpdate(displaced, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState());
        var m = new VatMediumStorage(() -> {});
        m.compile(h.getLevel(), min, max);
        m.fill(new FluidStack(Fluids.WATER, 1500), EXECUTE);
        h.assertTrue(
                m.getCapacity() == 7000 && VatMediumMesh.height(m, min, max) == min.getY() + 1.5,
                "Presentation used raw fullness rather than displaced layer volume");
        var mesh = VatMediumMesh.bake(h.getLevel(), min, max, m);
        h.assertTrue(
                mesh.faces().stream().filter(f -> f.normal() == Direction.UP).noneMatch(f -> {
                    var p = f.vertices().getFirst();
                    return Math.floor(p.x) == displaced.getX() && Math.floor(p.z) == displaced.getZ();
                }),
                "Top rendered through fully displaced cell");
        VatDisplacement.register(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK, (level, pos) -> 0);
        h.succeed();
    }
}
