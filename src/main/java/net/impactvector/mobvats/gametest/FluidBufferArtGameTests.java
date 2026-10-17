package net.impactvector.mobvats.gametest;

import java.util.*;
import net.impactvector.mobvats.gamecontent.fluid.*;
import net.impactvector.mobvats.gamecontent.fluid.compound.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class FluidBufferArtGameTests {
    private static final BlockPos P = new BlockPos(2, 2, 2);

    private static FluidBufferEntity buffer(GameTestHelper h, BlockPos p) {
        h.setBlock(p, FluidBufferContent.state(ConduitCladding.BARE));
        return (FluidBufferEntity) h.getBlockEntity(p);
    }

    private static void refresh(GameTestHelper h, FluidBufferEntity e) {
        FluidBufferPresentation.refresh(h.getLevel(), List.of(e.getBlockPos()));
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void rectangularReservoirClosesCornersAndRecoversAfterRemoval(GameTestHelper h) {
        var members = new ArrayList<FluidBufferEntity>();
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 2; z++) {
                    var member = buffer(h, P.offset(x, y, z));
                    member.filter("minecraft:water");
                    member.tank.setFluid(new FluidStack(Fluids.WATER, 2000));
                    members.add(member);
                }
        var first = members.getFirst();
        refresh(h, first);
        for (var member : members) {
            h.assertTrue((member.visualJoins() & FluidBufferArt.RECTANGULAR) != 0, "Box retained open junctions");
            close(
                    h,
                    member.getBlockPos().getY() + member.visualHeight(),
                    h.absolutePos(P).getY() + 1,
                    "Rectangular reservoir has unequal liquid levels");
            var restored = new FluidBufferEntity(member.getBlockPos(), member.getBlockState());
            restored.handleUpdateTag(
                    member.getUpdateTag(h.getLevel().registryAccess()),
                    h.getLevel().registryAccess());
            h.assertTrue(restored.visualJoins() == member.visualJoins(), "Packet lost rectangular shell state");
        }
        var volumes = FluidBufferGeometry.compile(h.getLevel(), h.absolutePos(P), h.absolutePos(P.offset(1, 1, 1)));
        var junction =
                net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(P)).add(.95, .95, .95);
        h.assertTrue(volumes.stream().anyMatch(v -> v.bounds().contains(junction)), "Closed junction is not reserved");
        h.assertTrue(
                members.stream().mapToInt(m -> m.tank.getFluidAmount()).sum() == 16000,
                "Rectangular rendering changed stored contents");
        h.assertTrue(
                members.stream().mapToInt(m -> m.tank.getCapacity()).sum() == 32000,
                "Rectangular rendering changed capacity");
        h.setBlock(P.offset(1, 1, 1), net.minecraft.world.level.block.Blocks.AIR);
        refresh(h, first);
        for (var member : members.subList(0, 7))
            h.assertTrue(
                    (member.visualJoins() & FluidBufferArt.RECTANGULAR) == 0, "Missing corner retained closed box");
        var replacement = buffer(h, P.offset(1, 1, 1));
        replacement.filter("minecraft:lava");
        refresh(h, first);
        h.assertTrue(
                (first.visualJoins() & FluidBufferArt.RECTANGULAR) == 0,
                "Incompatible corner incorrectly completed reservoir");
        replacement.filter("minecraft:water");
        refresh(h, first);
        h.assertTrue((first.visualJoins() & FluidBufferArt.RECTANGULAR) != 0, "Replaced corner did not restore box");
        h.succeed();
    }

    private static void close(GameTestHelper h, double a, double b, String message) {
        h.assertTrue(Math.abs(a - b) < 1e-10, message);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void filterDoesNotInventFluidAndLevelsFollowContents(GameTestHelper h) {
        var a = buffer(h, P);
        a.filter("minecraft:water");
        refresh(h, a);
        h.assertTrue(a.visualFluid().isEmpty() && a.visualHeight() == 0, "Filter invented fluid");
        a.tank.setFluid(new FluidStack(Fluids.WATER, 2000));
        refresh(h, a);
        close(h, a.visualHeight(), .5, "Half level");
        a.tank.setFluid(new FluidStack(Fluids.WATER, 4000));
        refresh(h, a);
        close(h, a.visualHeight(), 13. / 16, "Full level");
        a.tank.setFluid(FluidStack.EMPTY);
        refresh(h, a);
        h.assertTrue(a.visualFluid().isEmpty() && a.visualHeight() == 0, "Drain retained liquid");
        h.assertTrue(
                a.tank.getCapacity() == 4000
                        && ((FluidBufferBlock) a.getBlockState().getBlock())
                                        .vatDisplacement(h.getLevel(), a.getBlockPos())
                                == 1000,
                "Mechanics changed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void sixDirectionsJoinWithoutChangingPersistentDistribution(GameTestHelper h) {
        for (var face : Direction.values()) {
            var a = buffer(h, P);
            var b = buffer(h, P.relative(face));
            a.tank.setFluid(new FluidStack(Fluids.WATER, 4000));
            refresh(h, a);
            h.assertTrue(
                    FluidBufferArt.has(a.visualJoins(), face)
                            && FluidBufferArt.has(b.visualJoins(), face.getOpposite()),
                    "Missing six-way join " + face);
            h.assertTrue(
                    a.visualFluid().getAmount() == 4000 && b.visualFluid().getAmount() == 4000,
                    "Wrong shared snapshot");
            h.assertTrue(a.tank.getFluidAmount() == 4000 && b.tank.isEmpty(), "Presentation redistributed storage");
            close(
                    h,
                    a.getBlockPos().getY() + a.visualHeight(),
                    b.getBlockPos().getY() + b.visualHeight(),
                    "Different cluster liquid heights");
            h.setBlock(P.relative(face), net.minecraft.world.level.block.Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void incompatibleContentsAndFiltersStaySeparate(GameTestHelper h) {
        var a = buffer(h, P);
        var b = buffer(h, P.east());
        var c = buffer(h, P.east(2));
        a.tank.setFluid(new FluidStack(Fluids.WATER, 1000));
        c.tank.setFluid(new FluidStack(Fluids.LAVA, 1000));
        refresh(h, a);
        h.assertTrue(
                FluidBufferArt.has(a.visualJoins(), Direction.EAST)
                        && FluidBufferArt.has(b.visualJoins(), Direction.WEST)
                        && c.visualJoins() == 0,
                "Empty member merged incompatible clusters");
        b.filter("minecraft:water");
        refresh(h, a);
        h.assertTrue(
                a.visualJoins() == 0 && b.visualJoins() == 0 && c.visualJoins() == 0,
                "Filter mismatch visually joined");
        b.filter("");
        a.tank.setFluid(new FluidStack(Fluids.LAVA, 1000));
        refresh(h, a);
        h.assertTrue(
                FluidBufferArt.has(b.visualJoins(), Direction.EAST)
                        && FluidBufferArt.has(b.visualJoins(), Direction.WEST),
                "Identity transition did not rejoin");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void contentsComponentsSurviveSnapshotAndNetworkTag(GameTestHelper h) {
        var a = buffer(h, P);
        var b = buffer(h, P.east());
        var fluid = new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 2000);
        var composition = new CompoundComposition(List.of(
                new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "minecraft:water", 900),
                new CompoundComposition.Entry(CompoundComposition.Kind.FLUID, "mobvats:waste", 100)));
        fluid.set(CompoundFluids.COMPOSITION.get(), composition);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Buffer contents"));
        a.tank.setFluid(fluid);
        refresh(h, a);
        h.assertTrue(
                FluidStack.isSameFluidSameComponents(fluid, a.visualFluid())
                        && FluidStack.isSameFluidSameComponents(fluid, b.visualFluid()),
                "Snapshot lost composition or metadata");
        var restored = new FluidBufferEntity(a.getBlockPos(), a.getBlockState());
        restored.handleUpdateTag(
                a.getUpdateTag(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(
                FluidStack.matches(a.visualFluid(), restored.visualFluid())
                        && a.visualJoins() == restored.visualJoins(),
                "Packet tag lost state");
        close(h, a.visualHeight(), restored.visualHeight(), "Packet height changed");
        h.assertTrue(
                MixerLiquidPresentation.compoundTint(a.visualFluid(), 0xffffffff)
                        == MixerLiquidPresentation.compoundTint(fluid, 0xffffffff),
                "Composition appearance differs");
        var different = fluid.copy();
        different.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        b.tank.setFluid(different);
        refresh(h, a);
        h.assertTrue(a.visualJoins() == 0 && b.visualJoins() == 0, "Component-incompatible contents joined");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void casingConduitsAndIncompatibleBuffersNeverAddHardware(GameTestHelper h) {
        var neighbors = List.of(
                MixerContent.state(MixerPartType.Casing),
                MixerContent.state(MixerPartType.Glass),
                net.impactvector.mobvats.gamecontent.Content.state(
                        net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.Casing),
                net.impactvector.mobvats.gamecontent.Content.state(
                        net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.Glass),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        for (var variant : java.util.Arrays.stream(CladdingMaterial.values())
                .map(m -> FluidBufferContent.state(m.cladding(), m.shell()))
                .toList())
            for (var face : Direction.values()) {
                h.setBlock(P, variant);
                var a = (FluidBufferEntity) h.getBlockEntity(P);
                a.filter("minecraft:water");
                for (var neighbor : neighbors) {
                    h.setBlock(P.relative(face), neighbor);
                    refresh(h, a);
                    h.assertTrue(
                            a.visualJoins() == 0
                                    && !a.getUpdateTag(h.getLevel().registryAccess())
                                            .contains("visualMounts"),
                            "Non-buffer attachment snapshot");
                    h.assertTrue(
                            FluidBufferArt.mesh(a.visualJoins()) == FluidBufferArt.mesh(0), "Neighbor added hardware");
                    h.setBlock(P.relative(face), net.minecraft.world.level.block.Blocks.AIR);
                }
                var incompatible = buffer(h, P.relative(face));
                incompatible.filter("minecraft:lava");
                refresh(h, a);
                h.assertTrue(a.visualJoins() == 0 && incompatible.visualJoins() == 0, "Incompatible buffers connected");
                h.setBlock(P.relative(face), net.minecraft.world.level.block.Blocks.AIR);
            }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void saveLoadRebuildsPresentationAndIgnoresLegacyMounts(GameTestHelper h) {
        var a = buffer(h, P);
        buffer(h, P.east());
        a.filter("minecraft:water");
        ((FluidBufferEntity) h.getBlockEntity(P.east())).filter("minecraft:water");
        a.tank.setFluid(new FluidStack(Fluids.WATER, 3000));
        refresh(h, a);
        var saved = a.saveWithoutMetadata(h.getLevel().registryAccess());
        var packet = a.getUpdateTag(h.getLevel().registryAccess());
        packet.putInt("visualMounts", 63);
        var client = new FluidBufferEntity(a.getBlockPos(), a.getBlockState());
        client.handleUpdateTag(packet, h.getLevel().registryAccess());
        h.assertTrue(
                !client.getUpdateTag(h.getLevel().registryAccess()).contains("visualMounts"),
                "Legacy mount tag retained");
        h.setBlock(P, net.minecraft.world.level.block.Blocks.AIR);
        var loaded = buffer(h, P);
        loaded.loadWithComponents(saved, h.getLevel().registryAccess());
        refresh(h, loaded);
        h.assertTrue(
                loaded.filter().equals("minecraft:water")
                        && loaded.tank.getFluidAmount() == 3000
                        && loaded.visualJoins() == FluidBufferArt.bit(Direction.EAST),
                "Save/load lost storage or join");
        h.assertTrue(
                FluidStack.matches(loaded.visualFluid(), a.visualFluid()), "Save/load changed presentation contents");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void presentationCoalescesAndRecoversAfterLifecycleHooks(GameTestHelper h) {
        var a = buffer(h, P);
        var b = buffer(h, P.east());
        a.tank.setFluid(new FluidStack(Fluids.WATER, 2000));
        refresh(h, a);
        long revision = a.visualRevision();
        for (int i = 0; i < 10; i++) refresh(h, a);
        h.assertTrue(a.visualRevision() == revision, "Unchanged state sends repeated update");
        var mesh = FluidBufferArt.mesh(a.visualJoins());
        a.tank.setFluid(new FluidStack(Fluids.WATER, 3000));
        refresh(h, a);
        h.assertTrue(FluidBufferArt.mesh(a.visualJoins()) == mesh, "Amount invalidated static geometry");
        a.onChunkUnloaded();
        a.setRemoved();
        a.clearRemoved();
        a.onLoad();
        refresh(h, a);
        h.assertTrue(
                a.visualJoins() == FluidBufferArt.bit(Direction.EAST)
                        && a.visualFluid().getAmount() == 3000,
                "Lifecycle lost joined presentation");
        h.setBlock(P.east(), net.minecraft.world.level.block.Blocks.AIR);
        refresh(h, a);
        h.assertTrue(a.visualJoins() == 0 && FluidBufferArt.mesh(0) != mesh, "Neighbor removal retained mesh");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cladBuffersPreserveReservoirAcrossFormationAndDisassembly(GameTestHelper h) {
        var origin = new BlockPos(1, 1, 1);
        var controllerPos = origin.offset(0, 1, 2);
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++)
                    h.setBlock(
                            origin.offset(x, y, z),
                            x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4
                                    ? MixerContent.state(MixerPartType.Casing)
                                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.setBlock(controllerPos, MixerContent.state(MixerPartType.Controller));
        h.setBlock(origin.offset(2, 4, 2), MixerContent.state(MixerPartType.Impeller));
        h.setBlock(origin.offset(2, 3, 2), MixerContent.state(MixerPartType.Shaft));
        var p = origin.offset(0, 2, 0);
        h.setBlock(p, FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
        h.setBlock(p.above(), FluidBufferContent.state(ConduitCladding.GLASS, ConduitShell.MIXER));
        var a = (FluidBufferEntity) h.getBlockEntity(p);
        a.tank.setFluid(new FluidStack(Fluids.WATER, 4000));
        h.runAtTickTime(30, () -> {
            var owner = ConduitStructure.containing(h.getLevel(), a.getBlockPos());
            h.assertTrue(owner instanceof MultiblockMixer, "Clad buffer fixture did not assemble");
            h.assertTrue(
                    a.visualJoins() == FluidBufferArt.bit(Direction.UP)
                            && a.visualFluid().getAmount() == 4000,
                    "Formed buffer lost reservoir snapshot");
            h.assertTrue(
                    !a.getUpdateTag(h.getLevel().registryAccess()).contains("visualMounts"),
                    "Formed shell added mounts");
            h.setBlock(origin.offset(2, 4, 0), net.minecraft.world.level.block.Blocks.AIR);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(
                    ConduitStructure.containing(h.getLevel(), a.getBlockPos()) == null, "Fixture did not disassemble");
            h.assertTrue(
                    a.visualJoins() == FluidBufferArt.bit(Direction.UP)
                            && a.visualFluid().getAmount() == 4000,
                    "Unformed buffer lost actual connection/contents");
            h.assertTrue(
                    !a.getUpdateTag(h.getLevel().registryAccess()).contains("visualMounts"),
                    "Unformed frame added mounts");
            h.setBlock(origin.offset(2, 4, 0), MixerContent.state(MixerPartType.Casing));
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(
                    ConduitStructure.containing(h.getLevel(), a.getBlockPos()) instanceof MultiblockMixer,
                    "Fixture did not reform");
            h.assertTrue(
                    a.visualJoins() == FluidBufferArt.bit(Direction.UP) && a.tank.getFluidAmount() == 4000,
                    "Reassembly changed reservoir or storage");
            h.succeed();
        });
    }
}
