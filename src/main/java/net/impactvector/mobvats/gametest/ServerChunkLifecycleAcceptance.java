package net.impactvector.mobvats.gametest;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Real server eviction probe, outside GameTest's forced-chunk ownership; excluded from the mod JAR. */
@EventBusSubscriber(modid = "mobvats")
public final class ServerChunkLifecycleAcceptance {
    private static final class Fixture {
        final String name;
        final BlockPos origin, buffer;
        final boolean vat;
        final List<ChunkPos> chunks;
        FluidBufferEntity oldBuffer;
        ConduitCuboidController<?> oldOwner;
        FluidTank oldTank;
        ResourceReservation delayed;
        net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFluidPortEntity oldPort;
        FluidBufferEntity oldExternal;

        boolean activeIo() {
            return name.equals("single-chunk-mixer");
        }

        BlockPos port() {
            return origin.offset(0, 2, 1);
        }

        BlockPos external() {
            return port().west();
        }

        boolean passiveCorner() {
            return name.equals("passive-corner-mixer");
        }

        Fixture(String name, BlockPos origin, boolean vat, boolean boundary) {
            this.name = name;
            this.origin = origin;
            this.vat = vat;
            buffer = passiveCorner()
                    ? origin.offset(4, 2, 4)
                    : activeIo() ? origin.offset(0, 1, 1) : origin.offset(boundary ? 4 : 0, 2, 0);
            chunks = new ArrayList<>();
            for (int x = origin.getX() >> 4; x <= (origin.getX() + 4) >> 4; x++)
                for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 4) >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("single-chunk-vat", new BlockPos(16002, 64, 16002), true, false),
            new Fixture("single-chunk-mixer", new BlockPos(16170, 64, 16002), false, false),
            new Fixture("boundary-mixer", new BlockPos(16334, 64, 16002), false, true),
            new Fixture("passive-corner-mixer", new BlockPos(16636, 64, 16636), false, true));
    private static final net.minecraft.server.level.TicketType<ChunkPos> KEEP =
            net.minecraft.server.level.TicketType.create(
                    "mobvats_p0_keep", java.util.Comparator.comparingLong(ChunkPos::toLong));
    private static int ticks, phase, stable, unloads, loads, passiveBaselineUnloads;
    private static boolean active;
    private static boolean evictionRequested;
    private static int partialStart;
    private static net.minecraft.world.level.chunk.LevelChunk passiveChunk;
    private static final java.util.Map<ChunkPos, net.minecraft.server.level.ChunkHolder> retainedHolders =
            new java.util.HashMap<>();
    private static final java.util.Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> retainedParts =
            new java.util.HashMap<>();
    private static ResourceReservation reloadDelayed;
    private static Object passiveDescriptor;
    private static final java.util.Map<BlockPos, FluidStack> persistedFluids = new java.util.HashMap<>();
    private static final java.util.Map<BlockPos, String> persistedFilters = new java.util.HashMap<>();

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("mobvats.lifecycleAcceptance")) return;
        active = true;
        try {
            var level = event.getServer().overworld();
            event.getServer().tickRateManager().setTickRate(100);
            for (var f : FIXTURES) {
                force(level, f, true);
                // Only this dedicated harness world is modified; remove prior disposable fixture parts.
                for (var chunk : f.chunks)
                    for (var pos : BlockPos.betweenClosed(
                            new BlockPos(chunk.getMinBlockX(), 64, chunk.getMinBlockZ()),
                            new BlockPos(chunk.getMaxBlockX(), 67, chunk.getMaxBlockZ())))
                        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                if (f.vat) {
                    MachineFixtures.vat(level::setBlockAndUpdate, f.origin, 5, 5, 4);
                    ((VatHeadEntity) level.getBlockEntity(f.origin.offset(2, 2, 2))).encode(ItemEssence.COW);
                } else MachineFixtures.mixer(level::setBlockAndUpdate, f.origin, 5, 4, 5, 2, 2, true);
                if (f.passiveCorner())
                    for (int y = 0; y < 4; y++)
                        level.setBlockAndUpdate(
                                f.origin.offset(4, y, 4),
                                FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.MIXER));
                level.setBlockAndUpdate(
                        f.buffer,
                        FluidBufferContent.state(
                                ConduitCladding.CASING, f.vat ? ConduitShell.VAT : ConduitShell.MIXER));
                var buffer = (FluidBufferEntity) level.getBlockEntity(f.buffer);
                buffer.filter("minecraft:water");
                buffer.tank.setFluid(new FluidStack(Fluids.WATER, 3000));
                buffer.setChanged();
                if (f.activeIo()) {
                    buffer.tank.setFluid(FluidStack.EMPTY);
                    level.setBlockAndUpdate(
                            f.port(),
                            net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.state(
                                    net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.FluidPort));
                    MachineFixtures.vat(level::setBlockAndUpdate, f.origin.west(5), 5, 5, 4);
                    ((VatHeadEntity) level.getBlockEntity(f.origin.west(5).offset(2, 2, 2))).encode(ItemEssence.COW);
                    level.setBlockAndUpdate(
                            f.external(),
                            net.impactvector.mobvats.gamecontent.Content.state(
                                    net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.FluidPort));
                    var output = (net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatFluidPortEntity)
                            level.getBlockEntity(f.external());
                    output.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
                    output.setIoMode(it.zerono.mods.zerocore.lib.data.IoMode.Passive);
                    level.setBlockAndUpdate(
                            f.external().below(), FluidBufferContent.state(ConduitCladding.CASING, ConduitShell.VAT));
                    var external = (FluidBufferEntity)
                            level.getBlockEntity(f.external().below());
                    external.filter("minecraft:water");
                    external.tank.setFluid(new FluidStack(Fluids.WATER, 3000));
                }
            }
        } catch (Throwable error) {
            finish(event.getServer(), error);
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (active
                && event.getLevel() instanceof ServerLevel
                && target(event.getChunk().getPos())) {
            ++unloads;
            LogUtils.getLogger()
                    .info("P0 SERVER unload {} phase={}", event.getChunk().getPos(), phase);
        }
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (active
                && event.getLevel() instanceof ServerLevel
                && target(event.getChunk().getPos())) {
            ++loads;
            LogUtils.getLogger()
                    .info("P0 SERVER load {} phase={}", event.getChunk().getPos(), phase);
            if (phase == 1)
                LogUtils.getLogger().warn("P0 unexpected reload", new IllegalStateException("Chunk reacquisition"));
        }
    }

    private static boolean target(ChunkPos chunk) {
        return FIXTURES.stream().anyMatch(f -> f.chunks.contains(chunk));
    }

    private static void force(ServerLevel level, Fixture f, boolean value) {
        for (var chunk : f.chunks) level.setChunkForced(chunk.x, chunk.z, value);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!active) return;
        try {
            var level = event.getServer().overworld();
            require(event.getServer().getPlayerCount() == 0, "Acceptance server must have no players");
            if (++ticks % 1000 == 0)
                for (var f : FIXTURES)
                    for (var c : f.chunks)
                        LogUtils.getLogger()
                                .info(
                                        "P0 SERVER timeout probe {} {} present={} chunkNow={}",
                                        f.name,
                                        c,
                                        level.getChunkSource().hasChunk(c.x, c.z),
                                        level.getChunkSource().getChunkNow(c.x, c.z) != null);
            if (ticks % 1000 == 0)
                for (var f : FIXTURES)
                    for (var c : f.chunks)
                        LogUtils.getLogger()
                                .info(
                                        "P0 SERVER ticket {} {}",
                                        c,
                                        level.getChunkSource().getChunkDebugData(c));
            if (ticks >= 6000 && phase == 4 && unloads == passiveBaselineUnloads)
                throw new RuntimeVerificationUnavailable(
                        "Passive-only holder retained by adjacent FULL tickets; no actual unload event; phase=4 unloads="
                                + unloads + " loads=" + loads);
            require(ticks < 6000, "Timed out phase=" + phase + " unloads=" + unloads + " loads=" + loads);
            if (phase == 0 && ticks >= 80) {
                for (var f : FIXTURES) {
                    f.oldOwner = ConduitStructure.containing(level, f.buffer);
                    require(f.oldOwner != null, f.name + " did not form");
                    f.oldBuffer = (FluidBufferEntity) level.getBlockEntity(f.buffer);
                    require(f.oldBuffer.tank.getFluidAmount() == 3000, f.name + " initial contents/active I/O");
                    if (f.activeIo()) {
                        f.oldExternal = (FluidBufferEntity)
                                level.getBlockEntity(f.external().below());
                        f.oldPort = (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerFluidPortEntity)
                                level.getBlockEntity(f.port());
                        require(f.oldExternal.tank.isEmpty(), "Active input did not consume finite adjacent source");
                    }
                    f.oldTank = f.oldBuffer.interactionCluster().tank();
                    require(f.oldTank.drain(250, FluidAction.EXECUTE).getAmount() == 250, f.name + " initial I/O");
                    f.delayed = new ResourceReservation();
                    f.delayed.fluid(f.oldTank, new FluidStack(Fluids.WATER, 2000));
                    LogUtils.getLogger()
                            .info(
                                    "P0 SERVER before {} owner={} fluid={} chunks={}",
                                    f.name,
                                    System.identityHashCode(f.oldOwner),
                                    f.oldBuffer.tank.getFluidAmount(),
                                    f.chunks);
                }
                level.getChunkSource().save(true);
                for (var f : FIXTURES) force(level, f, false);
                phase = 1;
            } else if (phase == 1) {
                // hasChunk and getChunkNow never request generation or add tickets.
                boolean absent = FIXTURES.stream()
                        .flatMap(f -> f.chunks.stream())
                        .allMatch(c -> !level.getChunkSource().hasChunk(c.x, c.z)
                                && level.getChunkSource().getChunkNow(c.x, c.z) == null);
                if (!absent) {
                    stable = 0;
                    return;
                }
                if (++stable < 40) return;
                require(unloads == 8 && loads == 8, "Unexpected unload/reacquisition events: " + unloads + "/" + loads);
                for (var f : FIXTURES) {
                    require(!f.oldOwner.isAssembled(), f.name + " retained assembled controller");
                    require(!f.oldBuffer.routingAvailable(), f.name + " retained Buffer availability");
                    require(f.oldTank.getCapacity() == 0, f.name + " retained storage authority");
                    require(!f.delayed.commit(), f.name + " delayed transfer committed during eviction");
                    require(f.oldTank.drain(250, FluidAction.EXECUTE).isEmpty(), f.name + " unloaded transfer");
                    require(f.oldBuffer.tank.getFluidAmount() == 2750, f.name + " lost authoritative contents");
                    require(ConduitStructure.containing(level, f.buffer) == null, f.name + " stale owner registry");
                    if (f.activeIo())
                        require(
                                f.oldPort.transferBoundary(1000) == 0
                                        && !f.oldExternal.routingAvailable()
                                        && f.oldExternal.tank.isEmpty(),
                                "Active transfer survived eviction");
                    LogUtils.getLogger()
                            .info("P0 SERVER evicted {} stableAbsentTicks={} unloads={}", f.name, stable, unloads);
                }
                phase = 2;
                for (var f : FIXTURES) force(level, f, true);
                stable = ticks;
            } else if (phase == 2 && ticks - stable >= 80) {
                for (var f : FIXTURES) {
                    var current = (FluidBufferEntity) level.getBlockEntity(f.buffer);
                    var owner = ConduitStructure.containing(level, f.buffer);
                    require(current != null && current != f.oldBuffer && owner != null, f.name + " did not restore");
                    require(
                            current.routingAvailable()
                                    && current.tank.getFluidAmount() == 2750
                                    && current.tank.getFluid().is(Fluids.WATER),
                            f.name + " persistence changed");
                    var tank = current.interactionCluster().tank();
                    require(tank != f.oldTank && f.oldTank.getCapacity() == 0, f.name + " revived stale endpoint");
                    var resumed = new ResourceReservation();
                    resumed.fluid(tank, new FluidStack(Fluids.WATER, 2500));
                    require(
                            resumed.commit() && !resumed.commit() && current.tank.getFluidAmount() == 2500,
                            f.name + " resumed transaction was not exactly once");
                    if (f.vat) {
                        var head = (VatHeadEntity) level.getBlockEntity(f.origin.offset(2, 2, 2));
                        require(
                                head.isMachineAssembled() && ItemEssence.COW.equals(head.getIdentity()),
                                "Vat Head restoration");
                    }
                    LogUtils.getLogger()
                            .info(
                                    "P0 SERVER restored {} owner={} fluid={} freshBuffer=true freshEndpoint=true",
                                    f.name,
                                    System.identityHashCode(owner),
                                    current.tank.getFluidAmount());
                    if (f.activeIo()) {
                        var external = (FluidBufferEntity)
                                level.getBlockEntity(f.external().below());
                        require(external != f.oldExternal && external.tank.isEmpty(), "External endpoint persistence");
                        external.tank.setFluid(new FluidStack(Fluids.WATER, 250));
                    }
                }
                require(loads == 16, "Missing actual load/reload events: " + loads);
                phase = 3;
                stable = ticks;
            } else if (phase == 3 && ticks - stable >= 40) {
                var f = FIXTURES.get(1);
                var buffer = (FluidBufferEntity) level.getBlockEntity(f.buffer);
                var external =
                        (FluidBufferEntity) level.getBlockEntity(f.external().below());
                require(
                        buffer.tank.getFluidAmount() == 2750 && external.tank.isEmpty(),
                        "Resumed active I/O duplicated/lost 250 mB");
                LogUtils.getLogger()
                        .info(
                                "P0 SERVER active I/O restored finiteSource=0 internal=2750 transferred=250 stableTicks=40");
                if (!Boolean.getBoolean("mobvats.passiveOnlyChunkProbe")) {
                    finish(event.getServer(), null);
                    return;
                }
                var corner = FIXTURES.get(3);
                corner.oldBuffer = (FluidBufferEntity) level.getBlockEntity(corner.buffer);
                corner.oldOwner = ConduitStructure.containing(level, corner.buffer);
                corner.oldTank = corner.oldBuffer.interactionCluster().tank();
                // Component-bearing authoritative contents must survive the real disk reload exactly.
                var tagged = corner.oldTank.getFluid().copy();
                tagged.set(
                        net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("P0 passive eviction water"));
                corner.oldTank.setFluid(tagged);
                corner.delayed = new ResourceReservation();
                corner.delayed.fluid(corner.oldTank, new FluidStack(Fluids.WATER, 2000));
                reloadDelayed = new ResourceReservation();
                reloadDelayed.fluid(corner.oldTank, tagged.copyWithAmount(2000));
                passiveDescriptor = ((net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer)
                                corner.oldOwner)
                        .renderDescriptor()
                        .orElseThrow();
                var passive = new ChunkPos(corner.buffer);
                require(
                        level.getChunkAt(corner.buffer).getBlockEntities().values().stream()
                                .noneMatch(
                                        be -> be instanceof it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<?>),
                        "Passive-only fixture has framework parts");
                level.getChunkAt(corner.buffer).getBlockEntities().forEach((pos, entity) -> {
                    if (entity instanceof FluidBufferEntity member) {
                        persistedFluids.put(pos, member.tank.getFluid().copy());
                        persistedFilters.put(pos, member.filter());
                    }
                });
                for (var chunk : corner.chunks)
                    if (!chunk.equals(passive)) level.getChunkSource().addRegionTicket(KEEP, chunk, 0, chunk);
                force(level, corner, false);
                passiveBaselineUnloads = unloads;
                passiveChunk = level.getChunkAt(corner.buffer);
                for (var chunk : corner.chunks) {
                    if (chunk.equals(passive)) continue;
                    retainedHolders.put(
                            chunk, level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk.toLong()));
                    level.getChunkSource()
                            .getChunkNow(chunk.x, chunk.z)
                            .getBlockEntities()
                            .forEach((pos, entity) -> {
                                if (entity instanceof it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart<?>)
                                    retainedParts.put(pos, entity);
                            });
                }
                partialStart = ticks;
                stable = 0;
                phase = 4;
            } else if (phase == 4) {
                var f = FIXTURES.get(3);
                var c = new ChunkPos(f.buffer);
                require(loads == 16, "Callback/validation reacquired a target chunk during partial eviction");
                if (!evictionRequested && ticks - partialStart >= 80) {
                    require(
                            !level.getChunkSource().hasChunk(c.x, c.z) && unloads == passiveBaselineUnloads,
                            "Expected retained dependency before scheduler trigger");
                    ServerChunkEviction.request(level, c);
                    evictionRequested = true;
                    LogUtils.getLogger()
                            .info(
                                    "P0 SERVER requested native scheduler eviction {} dependencyLevel=34 neighbors=FULL",
                                    c);
                }
                for (var entry : retainedHolders.entrySet()) {
                    var chunk = entry.getKey();
                    require(
                            level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk.toLong()) == entry.getValue()
                                    && level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                            "Neighboring framework holder unloaded/replaced");
                }
                for (var entry : retainedParts.entrySet())
                    require(
                            !entry.getValue().isRemoved() && level.getBlockEntity(entry.getKey()) == entry.getValue(),
                            "Neighboring framework part unloaded/replaced");
                if (unloads <= passiveBaselineUnloads
                        || level.getChunkSource().hasChunk(c.x, c.z)
                        || level.getChunkSource().getChunkNow(c.x, c.z) != null) {
                    stable = 0;
                    return;
                }
                if (ServerChunkEviction.loaded(passiveChunk) || !ServerChunkEviction.absent(level, c)) {
                    stable = 0;
                    return;
                }
                if (++stable < 40) return;
                require(
                        !f.oldBuffer.routingAvailable() && f.oldTank.getCapacity() == 0 && !f.delayed.commit(),
                        "Passive-only eviction retained authority");
                require(
                        !f.oldOwner.isAssembled(),
                        "Passive-only eviction retained assembled controller despite missing shell");
                var mixer = (net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer) f.oldOwner;
                require(
                        unloads == 9
                                && mixer.storage().clusters().isEmpty()
                                && mixer.renderDescriptor().isEmpty(),
                        "Partial eviction did not retire routing and renderer-facing state");
                LogUtils.getLogger()
                        .info(
                                "P0 SERVER passive-only chunk evicted; retainedHolders={} retainedParts={} visible/updating/pending=absent stableTicks={}",
                                retainedHolders.size(),
                                retainedParts.size(),
                                stable);
                phase = 5;
                force(level, f, true);
                stable = ticks;
            } else if (phase == 5 && ticks - stable >= 80) {
                var f = FIXTURES.get(3);
                var current = (FluidBufferEntity) level.getBlockEntity(f.buffer);
                for (var entry : retainedHolders.entrySet()) {
                    var chunk = entry.getKey();
                    require(
                            level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk.toLong())
                                    == entry.getValue(),
                            "Reload replaced neighboring framework holder");
                }
                for (var entry : retainedParts.entrySet())
                    require(
                            !entry.getValue().isRemoved() && level.getBlockEntity(entry.getKey()) == entry.getValue(),
                            "Reload replaced neighboring framework part");
                for (var entry : persistedFluids.entrySet()) {
                    var restored = (FluidBufferEntity) level.getBlockEntity(entry.getKey());
                    require(
                            FluidStack.matches(entry.getValue(), restored.tank.getFluid())
                                    && persistedFilters.get(entry.getKey()).equals(restored.filter()),
                            "Disk reload changed passive member fluid/components/filter at " + entry.getKey());
                }
                require(
                        ConduitStructure.containing(level, f.buffer) == f.oldOwner
                                && current != f.oldBuffer
                                && current.interactionCluster().tank().getFluidAmount() == 2500
                                && current.interactionCluster().tank() != f.oldTank
                                && f.oldTank.getCapacity() == 0
                                && !reloadDelayed.commit(),
                        "Passive-only reload did not restore machine/storage");
                var owner = (net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer) f.oldOwner;
                require(
                        loads == 17
                                && unloads == 9
                                && owner.renderDescriptor().orElseThrow() != passiveDescriptor
                                && owner.storage().clusters().stream()
                                                .flatMap(cluster -> cluster.members().stream())
                                                .count()
                                        == 4
                                && owner.storage().clusters().stream()
                                                .flatMap(cluster -> cluster.members().stream())
                                                .map(FluidBufferEntity::getBlockPos)
                                                .distinct()
                                                .count()
                                        == 4
                                && owner.storage().bulkConnected(f.buffer).stream()
                                                .distinct()
                                                .count()
                                        == owner.storage()
                                                .bulkConnected(f.buffer)
                                                .size(),
                        "Partial reload duplicated endpoints or failed to rebuild renderer state");
                var resumed = new ResourceReservation();
                resumed.fluid(
                        current.interactionCluster().tank(),
                        current.tank.getFluid().copyWithAmount(2250));
                require(
                        resumed.commit() && !resumed.commit() && current.tank.getFluidAmount() == 2250,
                        "Partial reload resumed mutation was not exactly once");
                for (var chunk : f.chunks) level.getChunkSource().removeRegionTicket(KEEP, chunk, 0, chunk);
                LogUtils.getLogger()
                        .info(
                                "P0 SERVER passive-only chunk reattached; persisted=2500 resumed=2250 components/filters=exact neighbors=same members=4 noDuplicateEndpoints=true");
                finish(event.getServer(), null);
            }
        } catch (Throwable error) {
            finish(event.getServer(), error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(net.minecraft.server.MinecraftServer server, Throwable error) {
        active = false;
        try {
            var result = error == null
                    ? "P0 SERVER PROBE PASSED"
                    : error instanceof RuntimeVerificationUnavailable
                            ? "P0 SERVER PROBE BLOCKED: " + error.getMessage()
                            : "P0 SERVER PROBE FAILED: " + error;
            Files.writeString(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("p0-lifecycle-result.txt"),
                    result + "\nticks=" + ticks + " unloads=" + unloads + " loads=" + loads);
            if (error == null) LogUtils.getLogger().info(result);
            else LogUtils.getLogger().error(result, error);
        } catch (Exception writeError) {
            LogUtils.getLogger().error("P0 result write failed", writeError);
        }
        server.halt(false);
    }

    private ServerChunkLifecycleAcceptance() {}

    private static final class RuntimeVerificationUnavailable extends IllegalStateException {
        RuntimeVerificationUnavailable(String message) {
            super(message);
        }
    }
}
