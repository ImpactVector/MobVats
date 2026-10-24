package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gametest.PairedMachineFixtures.Family.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import it.zerono.mods.zerocore.lib.IActivableMachine;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.common.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

/** No eviction trigger: only native ticket expiry, save, unload, load and ZeroCore reformation. */
@GameTestHolder("mobvats_lifecycle")
@PrefixGameTestTemplate(false)
public final class IntactMachineReloadGameTests {
    private static void reload(GameTestHelper h, int combination, int offset) {
        var a = combination == 1 ? MIXER : VAT;
        var b = combination == 0 ? VAT : MIXER;
        int id = combination * 3 + (offset == 1 ? 0 : offset == 11 ? 1 : 2);
        var remote = new BlockPos(28000000 + id * 512 + offset, 80, 28000001);
        var relative = remote.subtract(h.absolutePos(BlockPos.ZERO));
        var f = new PairedMachineFixtures(
                h,
                new PairedMachineFixtures.Machine(a),
                new PairedMachineFixtures.Machine(b),
                1,
                FluidBufferContent.state(ConduitCladding.BARE),
                PairedMachineFixtures.Port.fluid(true),
                PairedMachineFixtures.Port.fluid(false),
                relative);
        var level = h.getLevel();
        var chunks = new ArrayList<ChunkPos>();
        for (int x = remote.getX() >> 4; x <= ((remote.getX() + 10) >> 4); ++x)
            chunks.add(new ChunkPos(x, remote.getZ() >> 4));
        chunks.forEach(p -> level.setChunkForced(p.x, p.z, true));
        var originals = new ArrayList<LevelChunk>();
        var owners = new ConduitCuboidController<?>[2];
        var before = new CompoundTag[1];
        var stale = new IFluidHandler[1];
        var stalePorts = new IFluidHandler[2];
        var sample = InterMachineLogisticsGameTests.fluid(true, 1777);
        h.runAtTickTime(40, () -> {
            for (var p : BlockPos.betweenClosed(remote.offset(-1, 0, -1), remote.offset(10, 3, 4)))
                level.removeBlock(p, false);
            f.build();
            for (boolean first : new boolean[] {true, false}) {
                var o = first ? f.left : f.right;
                if ((first ? a : b) == MIXER)
                    h.setBlock(o.offset(3, 3, 2), CreativeContent.block(CreativeDeviceType.DRIVE));
                else {
                    h.setBlock(o.offset(2, 1, 0), Content.state(VatPartType.ItemPort));
                    h.setBlock(o.offset(2, 1, 4), Content.state(VatPartType.ToolPort));
                    var head = (VatHeadEntity) h.getBlockEntity(o.offset(2, 2, 2));
                    var baby = new CompoundTag();
                    baby.putBoolean("Baby", true);
                    head.encode(head.getIdentity(), head.geometry(), baby);
                }
            }
        });
        h.runAtTickTime(100, () -> {
            f.independent();
            for (boolean first : new boolean[] {true, false}) {
                var c = f.controller(first);
                var o = first ? f.left : f.right;
                owners[first ? 0 : 1] = c;
                h.assertTrue(((IActivableMachine) c).isMachineActive(), "Fixture must retain enabled operator setting");
                if (c instanceof MultiblockMixer mixer) {
                    mixer.state().itemInput().insertItem(0, new ItemStack(Items.WHEAT, 4), false);
                    mixer.state().fluids(true).fill(new FluidStack(Fluids.WATER, 1777), EXECUTE);
                    mixer.state().depositExperienceMb(250);
                    for (int n = 0; n < mixer.spinUpTicks() + 10; ++n) mixer.updateMultiblockEntity();
                    h.assertTrue(
                            mixer.state().progress() > 0
                                    && !mixer.state().recipe().isEmpty(),
                            "Mixer must have a real in-flight recipe");
                    // Controller redstone pauses the real batch while retaining enabled configuration.
                    h.setBlock(o.offset(-1, 1, 1), Blocks.REDSTONE_BLOCK);
                } else {
                    var vat = (MultiblockVat) c;
                    vat.processEngine().state(h.absolutePos(o.offset(2, 2, 2))).growth().elapsed = 137;
                    var clean = new FluidStack(Fluids.WATER, 1500);
                    clean.set(DataComponents.CUSTOM_NAME, Component.literal("Native culture identity"));
                    h.assertTrue(vat.medium().fill(clean, EXECUTE) == 1500, "Culture setup rejected");
                    h.assertTrue(vat.medium().foul(257) == 257, "Dirty fraction setup failed");
                    var item = new ItemStack(Items.WHEAT, first ? 8 : 13);
                    item.set(DataComponents.CUSTOM_NAME, Component.literal("Intact reload identity"));
                    ((VatItemPortEntity) h.getBlockEntity(o.offset(2, 1, 0)))
                            .staging()
                            .setStackInSlot(0, item);
                    ((VatToolPortEntity) h.getBlockEntity(o.offset(2, 1, 4)))
                            .setHeldItem(new ItemStack(Items.IRON_SWORD));
                    h.setBlock(o.offset(1, 1, -1), Blocks.REDSTONE_BLOCK);
                }
                // Include exact component-bearing input and output storage in each physical machine.
                var storage = f.storage(first);
                for (var fluid : List.of(
                        InterMachineLogisticsGameTests.fluid(true, first ? 611 : 733),
                        new FluidStack(ProductFluids.FEEDING_SLURRY.get(), first ? 355 : 477))) {
                    var tank = storage.clusters().stream()
                            .map(PhysicalFluidStorage.Cluster::tank)
                            .filter(t -> t.isFluidValid(fluid) && t.isEmpty())
                            .findFirst()
                            .orElseThrow();
                    h.assertTrue(tank.fill(fluid, EXECUTE) == fluid.getAmount(), "Physical input/output seed failed");
                }
                f.port(first).filter("mobvats:culture_slurry");
                f.port(first).setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Passive);
            }
            var buffer = (FluidBufferEntity) h.getBlockEntity(f.middle);
            buffer.filter("mobvats:culture_slurry");
            h.assertTrue(f.bufferCapability().fill(sample, EXECUTE) == sample.getAmount(), "Gap buffer seed failed");
        });
        h.runAtTickTime(140, () -> {
            f.independent();
            before[0] = snapshot(f);
            stale[0] = f.bufferCapability();
            stalePorts[0] = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    h.absolutePos(f.output),
                    Direction.EAST);
            stalePorts[1] = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    h.absolutePos(f.input),
                    Direction.WEST);
            h.assertTrue(stalePorts[0] != null && stalePorts[1] != null, "Expected live passive port capabilities");
            chunks.forEach(p -> originals.add(level.getChunk(p.x, p.z)));
            chunks.forEach(p -> level.setChunkForced(p.x, p.z, false));
        });
        h.startSequence()
                .thenIdle(150)
                .thenWaitUntil(() -> {
                    try {
                        for (int n = 0; n < chunks.size(); ++n) {
                            var p = chunks.get(n);
                            h.assertTrue(
                                    ServerChunkEviction.absent(level, p)
                                            && !ServerChunkEviction.loaded(originals.get(n)),
                                    "Waiting for legitimate intact native unload: "
                                            + ServerChunkEviction.diagnostic(level, p));
                        }
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                })
                .thenExecute(() -> {
                    h.assertTrue(
                            stale[0].drain(1, EXECUTE).isEmpty() && stale[0].fill(sample, EXECUTE) == 0,
                            "Old buffer capability usable after unload");
                    for (var port : stalePorts)
                        h.assertTrue(
                                port.drain(1, EXECUTE).isEmpty() && port.fill(sample, EXECUTE) == 0,
                                "Old machine port accessed unloaded storage");
                    chunks.forEach(p -> level.setChunkForced(p.x, p.z, true));
                    for (int n = 0; n < chunks.size(); ++n) {
                        var p = chunks.get(n);
                        h.assertTrue(level.getChunk(p.x, p.z) != originals.get(n), "Chunk instance was not replaced");
                    }
                })
                .thenIdle(70)
                .thenExecute(() -> {
                    f.independent();
                    h.assertTrue(
                            f.controller(true) != owners[0] && f.controller(false) != owners[1],
                            "Old controller survived native reload");
                    h.assertTrue(
                            before[0].equals(snapshot(f)),
                            "Native reload changed exact process/resources/configuration: before=" + before[0]
                                    + " after=" + snapshot(f));
                    h.assertTrue(
                            stale[0].drain(1, EXECUTE).isEmpty() && stale[0].fill(sample, EXECUTE) == 0,
                            "Chunk replacement revived old buffer capability");
                    for (var port : stalePorts)
                        h.assertTrue(
                                port.drain(1, EXECUTE).isEmpty() && port.fill(sample, EXECUTE) == 0,
                                "Old machine port revived after chunk replacement");
                    f.report(
                            "PASS INTACT native chunk replacement; exact process, specimen, culture fractions, items, compound fluids, filters and drive; offset="
                                    + offset);
                    for (var p : BlockPos.betweenClosed(remote.offset(-1, 0, -1), remote.offset(10, 3, 4)))
                        level.removeBlock(p, false);
                    chunks.forEach(p -> level.setChunkForced(p.x, p.z, false));
                })
                .thenSucceed();
    }

    private static CompoundTag snapshot(PairedMachineFixtures f) {
        var tag = new CompoundTag();
        var registries = f.h.getLevel().registryAccess();
        for (boolean first : new boolean[] {true, false}) {
            var c = f.controller(first);
            var state = new CompoundTag();
            state.putBoolean("enabled", ((IActivableMachine) c).isMachineActive());
            if (c instanceof MultiblockMixer mixer) {
                var process = mixer.state().save(registries);
                process.remove("metrics");
                state.put("process", process);
            } else {
                var vat = (MultiblockVat) c;
                vat.processEngine().save(state, registries);
                state.put("medium", vat.medium().save(registries));
            }
            tag.put(first ? "a" : "b", state);
        }
        for (var p : BlockPos.betweenClosed(f.left, f.right.offset(4, 3, 4))) {
            var entity = LoadedWorld.blockEntity(f.h.getLevel(), f.h.absolutePos(p));
            var cell = new CompoundTag();
            cell.putString("block", f.h.getBlockState(p).toString());
            if (entity instanceof ConfigurableEndpoint endpoint)
                endpoint.endpoint().save(cell);
            if (entity instanceof FluidBufferEntity buffer) {
                cell.putString("filter", buffer.filter());
                cell.put("fluid", buffer.tank.getFluid().saveOptional(registries));
            }
            if (entity instanceof VatItemPortEntity item)
                cell.put("items", item.staging().serializeNBT(registries));
            if (entity instanceof VatToolPortEntity tool)
                cell.put("tool", tool.heldItem().saveOptional(registries));
            if (entity instanceof VatHeadEntity head) {
                cell.putUUID("specimenId", head.specimenId());
                cell.putString("species", head.getIdentity().toString());
                cell.put("specimenState", head.specimenState());
                cell.put("geometry", head.geometry().save());
            }
            tag.put(p.subtract(f.left).toShortString(), cell);
        }
        return tag;
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void intactVatVat(GameTestHelper h) {
        reload(h, 0, 1);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void intactMixerMixer(GameTestHelper h) {
        reload(h, 1, 1);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void intactVatMixer(GameTestHelper h) {
        reload(h, 2, 1);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void separateChunksVatVat(GameTestHelper h) {
        reload(h, 0, 11);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void separateChunksMixerMixer(GameTestHelper h) {
        reload(h, 1, 11);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void separateChunksVatMixer(GameTestHelper h) {
        reload(h, 2, 11);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void spanningVatVat(GameTestHelper h) {
        reload(h, 0, 14);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void spanningMixerMixer(GameTestHelper h) {
        reload(h, 1, 14);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1600)
    public static void spanningVatMixer(GameTestHelper h) {
        reload(h, 2, 14);
    }

    @GameTest(batch = "intactMachineReload", template = "empty", timeoutTicks = 1200)
    public static void unavailableBoundarySuspendsWithoutTicketsOrResourceLoss(GameTestHelper h) {
        var level = h.getLevel();
        var remote = new BlockPos(28500011, 80, 28500001);
        var leftChunk = new ChunkPos(remote);
        var rightChunk = new ChunkPos(remote.east(6));
        var ticket = net.minecraft.server.level.TicketType.create(
                "mobvats_lifecycle_full", Comparator.comparingLong(ChunkPos::toLong));
        var f = new PairedMachineFixtures(
                h,
                new PairedMachineFixtures.Machine(VAT),
                new PairedMachineFixtures.Machine(MIXER),
                1,
                FluidBufferContent.state(ConduitCladding.BARE),
                PairedMachineFixtures.Port.fluid(true),
                PairedMachineFixtures.Port.fluid(false),
                remote.subtract(h.absolutePos(BlockPos.ZERO)));
        level.setChunkForced(leftChunk.x, leftChunk.z, true);
        level.setChunkForced(rightChunk.x, rightChunk.z, true);
        var original = new LevelChunk[1];
        var stale = new IFluidHandler[1];

        var sample = InterMachineLogisticsGameTests.fluid(true, 1000);
        var placement = remote.offset(4, 5, 3);
        h.runAtTickTime(40, () -> {
            f.build();
            for (var face : Direction.values())
                level.setBlockAndUpdate(placement.relative(face), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(placement, Blocks.AIR.defaultBlockState());
        });
        h.runAtTickTime(100, () -> {
            f.independent();
            h.assertTrue(f.seed(true, sample) == 1000, "Boundary source seed failed");
            original[0] = level.getChunk(rightChunk.x, rightChunk.z);
            stale[0] = f.bufferCapability();
            // A legitimate FULL-only ticket models the non-ticking player fringe. No holder is edited.
            level.getChunkSource().addRegionTicket(ticket, leftChunk, 0, leftChunk);
            level.setChunkForced(leftChunk.x, leftChunk.z, false);
            level.setChunkForced(rightChunk.x, rightChunk.z, false);
        });
        h.startSequence()
                .thenIdle(120)
                .thenWaitUntil(() -> {
                    h.assertTrue(
                            level.getChunkSource().getChunkNow(rightChunk.x, rightChunk.z) == null,
                            "Waiting for adjacent endpoint to become unavailable "
                                    + ServerChunkEviction.diagnostic(level, rightChunk));
                })
                .thenExecute(() -> {
                    var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(leftChunk.toLong());
                    h.assertTrue(
                            holder != null
                                    && holder.getTicketLevel() == 33
                                    && f.controller(true).isAssembled(),
                            "FULL source must remain intact, non-ticking");
                    f.port(true).setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Active);
                    for (int n = 0; n < 100; ++n) {
                        h.assertTrue(
                                f.port(true).transferBoundary(1000) == 0, "Transfer accessed an unavailable neighbor");
                        h.assertTrue(
                                LoadedWorld.blockEntity(level, h.absolutePos(f.middle)) == null,
                                "Missing endpoint lookup created a block entity");
                    }
                    var supply = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
                    supply.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE);
                    var endpoint = new WorldFluidPumps.Endpoint() {
                        public boolean accepts(FluidStack stack) {
                            return true;
                        }

                        public void status(String value) {}
                    };
                    h.assertTrue(
                            !WorldFluidPumps.push(level, placement, supply, endpoint)
                                    && supply.getFluidAmount() == 1000
                                    && LoadedWorld.blockState(level, placement).isAir(),
                            "Pump placement crossed an unavailable notification boundary or spent fluid");
                    h.assertTrue(
                            f.amount(true, sample) == 1000 && stale[0].fill(sample, EXECUTE) == 0,
                            "Unavailable endpoint lost or duplicated fluid");
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(
                            !level.hasChunk(rightChunk.x, rightChunk.z),
                            "Boundary reads reacquired the unavailable FULL chunk");

                    h.assertTrue(
                            f.amount(true, sample) == 1000, "Non-ticking controller processed resource transaction");
                    level.getChunkSource().removeRegionTicket(ticket, leftChunk, 0, leftChunk);
                    level.setChunkForced(leftChunk.x, leftChunk.z, true);
                    level.setChunkForced(rightChunk.x, rightChunk.z, true);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    f.independent();
                    level.removeBlock(placement, false);
                    for (var face : Direction.values()) level.removeBlock(placement.relative(face), false);
                    for (var p : BlockPos.betweenClosed(remote, remote.offset(10, 3, 4))) level.removeBlock(p, false);
                    level.setChunkForced(leftChunk.x, leftChunk.z, false);
                    level.setChunkForced(rightChunk.x, rightChunk.z, false);
                })
                .thenSucceed();
    }
}
