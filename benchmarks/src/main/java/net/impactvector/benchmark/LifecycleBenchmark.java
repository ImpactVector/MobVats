package net.impactvector.benchmark;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import it.zerono.mods.zerocore.lib.IActivableMachine;
import it.zerono.mods.zerocore.lib.multiblock.AbstractMultiblockController;
import it.zerono.mods.zerocore.lib.multiblock.IMultiblockPart;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated development mod. Does not replace controllers or invoke lifecycle callbacks manually. */
@Mod("lifecycle_benchmark")
@EventBusSubscriber(modid = "lifecycle_benchmark")
public final class LifecycleBenchmark {
    private static final Gson JSON =
            new com.google.gson.GsonBuilder().serializeNulls().create();
    private static final com.sun.management.ThreadMXBean THREAD =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final String MACHINE = System.getProperty("benchmark.machine", "mixer_min");
    private static final String IMPLEMENTATION = System.getProperty("benchmark.implementation");
    private static final int COUNT = positive("count", 1), REPS = positive("repetitions", 5);
    private static final int WARMUP = positive("warmup", 200), MEASURE = positive("measure", 200);
    private static final Path RESULTS = Path.of(System.getProperty("benchmark.results"));
    private static final List<BlockPos> ORIGINS = new ArrayList<>(), PROBES = new ArrayList<>();
    private static final List<BlockEntity> OLD = new ArrayList<>();
    private static final Set<Long> UNLOADED = new HashSet<>();
    private static final Map<BlockPos, BlockState> BROKEN = new LinkedHashMap<>();
    private static Recording recording;
    private static String stage;
    private static int ticks, stable, repetition = 1, parts;
    private static Sample start;
    private static Map<String, long[]> probeStart;
    private static final Map<String, Object> ARTIFACTS = new TreeMap<>();
    private static long tickBegin, tickTotal, tickStart;
    private static Double observed;
    private static BenchmarkWindow window;
    private static boolean failed;

    private record Sample(long wall, long cpu, long allocation, long gcCount, long gcMillis) {}

    private static int positive(String key, int fallback) {
        int value = Integer.getInteger("benchmark." + key, fallback);
        if (value < 1) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    private static Sample sample() {
        long n = 0, ms = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, bean.getCollectionCount());
            ms += Math.max(0, bean.getCollectionTime());
        }
        long id = Thread.currentThread().threadId();
        return new Sample(
                System.nanoTime(),
                THREAD.isThreadCpuTimeSupported() ? THREAD.getThreadCpuTime(id) : -1,
                THREAD.isThreadAllocatedMemorySupported() ? THREAD.getThreadAllocatedBytes(id) : -1,
                n,
                ms);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        try {
            Files.deleteIfExists(RESULTS.resolveSibling("complete.txt"));
            Files.deleteIfExists(RESULTS.resolveSibling("failed.txt"));
            if (THREAD.isThreadCpuTimeSupported()) THREAD.setThreadCpuTimeEnabled(true);
            if (THREAD.isThreadAllocatedMemorySupported()) THREAD.setThreadAllocatedMemoryEnabled(true);
            for (String id : List.of(IMPLEMENTATION.equals("xr2") ? "bigreactors" : "mobvats", "zerocore")) {
                var path = ModList.get().getModFileById(id).getFile().getFilePath();
                ARTIFACTS.put(
                        id,
                        Map.of(
                                "file",
                                path.getFileName().toString(),
                                "sha256",
                                HexFormat.of()
                                        .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(Files.readAllBytes(path)))));
            }
            if (Boolean.parseBoolean(System.getProperty("benchmark.profile", "true"))) {
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.setName(IMPLEMENTATION + "-" + MACHINE);
                recording.start();
            }
            var server = event.getServer();
            var level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
            level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, server);
            level.setDayTime(6000);
            for (int i = 0; i < COUNT; i++) ORIGINS.add(new BlockPos(1024 + i * 64, -60, 1024));
            begin("jvm_start_to_server_started");
            start = new Sample(
                    System.nanoTime() - ManagementFactory.getRuntimeMXBean().getUptime() * 1_000_000L, 0, 0, 0, 0);
            emit("jvm_start_to_server_started");
            boolean cold = System.getProperty("benchmark.phase", "suite").equals("cold");
            if (!cold)
                for (var o : ORIGINS) {
                    level.setChunkForced(o.getX() >> 4, o.getZ() >> 4, true);
                    level.getChunk(o);
                }
            begin(cold ? "cold_restore" : "preparation_assembly");
            for (var o : ORIGINS) {
                level.setChunkForced(o.getX() >> 4, o.getZ() >> 4, true);
                level.getChunk(o);
                if (!stage.equals("cold_restore")) build(level, o);
                PROBES.add(probe(o));
                if (MACHINE.equals("connected_pair")) PROBES.add(o.offset(5, 1, 2));
            }
        } catch (Throwable error) {
            fail(event.getServer(), error);
        }
    }

    private static void begin(String next) {
        stage = next;
        ticks = 0;
        stable = 0;
        observed = null;
        probeStart = Probes.snapshot();
        tickStart = tickTotal;
        window = new BenchmarkWindow();
        window.implementation = IMPLEMENTATION;
        window.machine = MACHINE;
        window.scenario = next;
        window.count = COUNT;
        window.repetition = repetition;
        window.begin();
        start = sample();
    }

    private static AbstractMultiblockController<?> controller(ServerLevel level, BlockPos p) {
        var entity = level.getBlockEntity(p);
        if (entity instanceof IMultiblockPart<?> part
                && part.getMultiblockController().orElse(null) instanceof AbstractMultiblockController<?> c) return c;
        return null;
    }

    private static boolean assembled(ServerLevel level) {
        for (var p : PROBES) {
            var c = controller(level, p);
            if (c == null || !c.isAssembled()) return false;
        }
        return true;
    }

    private static boolean ready(ServerLevel level) throws Exception {
        if (!assembled(level)) return false;
        if (IMPLEMENTATION.equals("mobvats")) {
            Class<?> type = Class.forName("net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure");
            synchronized (type) {
                for (String name : List.of("DIRTY", "MIXER_GEOMETRY_DIRTY")) {
                    var field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    var value = field.get(null);
                    if (value instanceof Map<?, ?> map && !map.isEmpty()
                            || value instanceof Set<?> set && !set.isEmpty()) return false;
                }
            }
            for (var p : PROBES) {
                var c = controller(level, p);
                if (c.getClass().getSimpleName().equals("MultiblockMixer")
                        && ((Optional<?>) c.getClass().getMethod("assembly").invoke(c)).isEmpty()) return false;
                if (c.getClass().getSimpleName().equals("MultiblockVat")
                        && ((List<?>) c.getClass().getMethod("getAssemblies").invoke(c)).isEmpty()) return false;
            }
        }
        return true;
    }

    private static void disable(ServerLevel level) {
        parts = 0;
        for (var p : PROBES) {
            var c = Objects.requireNonNull(controller(level, p));
            parts += c.getPartsCount();
            if (c instanceof IActivableMachine a) {
                a.setMachineActive(false);
                if (a.isMachineActive()) throw new IllegalStateException("Deactivate failed");
            } else throw new IllegalStateException("Missing IActivableMachine: " + c.getClass());
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == net.minecraft.world.level.Level.OVERWORLD)
            UNLOADED.add(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preTick(ServerTickEvent.Pre event) {
        tickBegin = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        tickTotal += System.nanoTime() - tickBegin;
        if (stage == null || failed || stage.equals("shutdown")) return;
        try {
            var level = event.getServer().overworld();
            ticks++;
            if (ticks == 100 && !stage.equals("idle") && !stage.equals("warmup"))
                LogUtils.getLogger().info("BENCHMARK waiting {} {}", stage, diagnostic(level));
            if (ticks > 2400 && !stage.equals("idle"))
                throw new IllegalStateException("Timeout in " + stage + " " + diagnostic(level));
            switch (stage) {
                case "preparation_assembly",
                        "initial_assembly",
                        "cold_restore",
                        "structural_reassembly",
                        "warm_chunk_reload" -> {
                    stable = ready(level) ? stable + 1 : 0;
                    if (stable == 1) {
                        observed = (System.nanoTime() - start.wall) / 1e6;
                        disable(level);
                    }
                    if (stable < 20) return;
                    if (stage.equals("preparation_assembly")) {
                        begin("preparation_warmup");
                        return;
                    }
                    emit(stage);
                    if (stage.equals("cold_restore")) {
                        finish(event.getServer());
                        return;
                    }
                    if (stage.equals("initial_assembly") || stage.equals("cold_restore")) begin("warmup");
                    else if (stage.equals("structural_reassembly")) unload(level);
                    else if (repetition < REPS) {
                        repetition++;
                        reset(level);
                    } else finish(event.getServer());
                }
                case "preparation_warmup" -> {
                    if (ticks >= WARMUP) reset(level);
                }
                case "reset" -> {
                    if (ticks < 20) return;
                    begin("initial_assembly");
                    for (var o : ORIGINS) build(level, o);
                }
                case "warmup" -> {
                    if (!assembled(level)) throw new IllegalStateException("Lost assembly during warmup");
                    if (ticks >= WARMUP) begin("idle");
                }
                case "idle" -> {
                    if (!assembled(level)) throw new IllegalStateException("Lost assembly during idle");
                    for (var p : PROBES)
                        if (((IActivableMachine) controller(level, p)).isMachineActive())
                            throw new IllegalStateException("Active during idle");
                    if (ticks < MEASURE) return;
                    emit("idle");
                    begin("activation_deactivation");
                    for (var p : PROBES) {
                        var a = (IActivableMachine) controller(level, p);
                        a.setMachineActive(true);
                        a.setMachineActive(false);
                    }
                    emit("activation_deactivation");
                    BROKEN.clear();
                    begin("structural_disassembly");
                    for (var o : ORIGINS) {
                        BROKEN.put(o, level.getBlockState(o));
                        level.setBlockAndUpdate(o, Blocks.AIR.defaultBlockState());
                    }
                    if (MACHINE.equals("connected_pair"))
                        for (var o : ORIGINS) {
                            var p = o.east(5);
                            BROKEN.put(p, level.getBlockState(p));
                            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        }
                }
                case "structural_disassembly" -> {
                    for (var p : PROBES) {
                        var c = controller(level, p);
                        if (c != null && c.isAssembled()) return;
                    }
                    emit("structural_disassembly");
                    begin("structural_reassembly");
                    BROKEN.forEach(level::setBlockAndUpdate);
                }
                case "warm_chunk_unload" -> {
                    for (var o : ORIGINS)
                        if (!UNLOADED.contains(net.minecraft.world.level.ChunkPos.asLong(o.getX() >> 4, o.getZ() >> 4))
                                || level.getChunkSource().getChunkNow(o.getX() >> 4, o.getZ() >> 4) != null) return;
                    for (var old : OLD) if (!old.isRemoved()) return;
                    emit("warm_chunk_unload");
                    begin("warm_chunk_reload");
                    for (var o : ORIGINS) {
                        level.setChunkForced(o.getX() >> 4, o.getZ() >> 4, true);
                        level.getChunk(o);
                    }
                }
                default -> throw new IllegalStateException("Unknown stage " + stage);
            }
        } catch (Throwable error) {
            fail(event.getServer(), error);
        }
    }

    private static void unload(ServerLevel level) {
        OLD.clear();
        UNLOADED.clear();
        for (var p : PROBES) OLD.add(Objects.requireNonNull(level.getBlockEntity(p)));
        begin("warm_chunk_unload");
        for (var o : ORIGINS) level.setChunkForced(o.getX() >> 4, o.getZ() >> 4, false);
    }

    private static void reset(ServerLevel level) {
        begin("reset");
        for (var o : ORIGINS)
            for (int x = 0; x < 10; x++)
                for (int y = 0; y < 7; y++)
                    for (int z = 0; z < 5; z++)
                        level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
    }

    private static String diagnostic(ServerLevel level) {
        var out = new ArrayList<String>();
        if ("warm_chunk_unload".equals(stage)) {
            for (var o : ORIGINS)
                out.add("chunk=" + (o.getX() >> 4) + "," + (o.getZ() >> 4) + " unloadEvent="
                        + UNLOADED.contains(net.minecraft.world.level.ChunkPos.asLong(o.getX() >> 4, o.getZ() >> 4))
                        + " fullChunkPresent="
                        + (level.getChunkSource().getChunkNow(o.getX() >> 4, o.getZ() >> 4) != null));
            out.add("removed old entities="
                    + OLD.stream().filter(BlockEntity::isRemoved).count() + "/" + OLD.size());
            return out.toString();
        }
        for (var p : PROBES) {
            if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) {
                out.add("full chunk absent");
                continue;
            }
            var c = controller(level, p);
            out.add(
                    c == null
                            ? "no controller"
                            : c.getLastError()
                                    .map(e -> e.getChatMessage().getString() + " @ " + e.getPosition())
                                    .orElse("no validation error"));
        }
        return out.toString();
    }

    private static void emit(String scenario) throws Exception {
        var end = sample();
        var row = new LinkedHashMap<String, Object>();
        window.end();
        window.commit();
        if (scenario.equals("idle") && Boolean.parseBoolean(System.getProperty("benchmark.probes", "true"))) {
            long calls = 0;
            for (var entry : Probes.difference(probeStart).entrySet())
                if (entry.getKey().endsWith(".updateServer"))
                    calls += ((Number) ((Map<?, ?>) entry.getValue()).get("calls")).longValue();
            if (calls != (long) MEASURE * PROBES.size())
                throw new IllegalStateException("Unexpected idle controller tick count: " + calls);
        }
        row.put("schema_version", 1);
        row.put("implementation", IMPLEMENTATION);
        row.put("machine", MACHINE);
        row.put("scenario", scenario);
        row.put("count", COUNT);
        row.put("repetition", repetition);
        row.put("duration_ms", (end.wall - start.wall) / 1e6);
        row.put("server_cpu_ms", start.cpu < 0 ? null : (end.cpu - start.cpu) / 1e6);
        row.put("allocation_bytes", start.allocation < 0 ? null : end.allocation - start.allocation);
        row.put("gc_count", end.gcCount - start.gcCount);
        row.put("gc_ms", end.gcMillis - start.gcMillis);
        row.put("ticks", ticks);
        row.put("connected_parts", parts);
        row.put("server_tick_processing_ms", (tickTotal - tickStart) / 1e6);
        row.put("transition_observed_ms", observed);
        row.put("settling_ms", observed == null ? null : (end.wall - start.wall) / 1e6 - observed);
        row.put(
                "callbacks",
                Boolean.parseBoolean(System.getProperty("benchmark.probes", "true"))
                        ? Probes.difference(probeStart)
                        : null);
        row.put("deferred_operation_counts", null);
        row.put("network", null);
        row.put("recorded_at_epoch_ms", System.currentTimeMillis());
        row.put(
                "environment",
                Map.ofEntries(
                        Map.entry("mobvats_commit", System.getProperty("benchmark.mobvatsCommit")),
                        Map.entry("harness_sha256", System.getProperty("benchmark.harnessSha256")),
                        Map.entry("artifacts", ARTIFACTS),
                        Map.entry("xr2_reference_commit", "f6cc65140a8abbfc87137f5c54fc641085f66048"),
                        Map.entry("xr2_artifact", "curse.maven:extreme-reactors-250277:7293725"),
                        Map.entry("zerocore", "curse.maven:zerocore-247921:6648746 / 1.21.1-2.4.18"),
                        Map.entry("minecraft", "1.21.1"),
                        Map.entry("neoforge", "21.1.252"),
                        Map.entry("java", System.getProperty("java.runtime.version")),
                        Map.entry("java_vendor", System.getProperty("java.vendor")),
                        Map.entry(
                                "jvm_flags",
                                ManagementFactory.getRuntimeMXBean().getInputArguments()),
                        Map.entry("os", System.getProperty("os.name") + " " + System.getProperty("os.arch")),
                        Map.entry("processors", Runtime.getRuntime().availableProcessors()),
                        Map.entry("max_heap", Runtime.getRuntime().maxMemory()),
                        Map.entry("warmup_ticks", WARMUP),
                        Map.entry("measure_ticks", MEASURE),
                        Map.entry("tick_rate", 20),
                        Map.entry("view_distance", 6),
                        Map.entry("simulation_distance", 6),
                        Map.entry("profile", recording != null),
                        Map.entry("probes", Boolean.parseBoolean(System.getProperty("benchmark.probes", "true"))),
                        Map.entry(
                                "completion",
                                "assembled for 20 consecutive Post ticks; unload requires Unload event, absent full chunk, removed old entity")));
        Files.writeString(RESULTS, JSON.toJson(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        LogUtils.getLogger()
                .info(
                        "BENCHMARK {} {} count={} repetition={} cpuMs={} elapsedMs={}",
                        IMPLEMENTATION,
                        scenario,
                        COUNT,
                        repetition,
                        row.get("server_cpu_ms"),
                        row.get("duration_ms"));
    }

    private static void finish(MinecraftServer server) throws Exception {
        Files.writeString(RESULTS.resolveSibling("complete.txt"), "Scenario suite completed\n");
        begin("shutdown");
        server.halt(false);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        try {
            if (!failed && "shutdown".equals(stage)) emit("shutdown_to_server_stopped");
            if (recording != null) {
                recording.stop();
                recording.dump(RESULTS.resolveSibling("profile.jfr"));
                recording.close();
            }
        } catch (Exception error) {
            fail(event.getServer(), error);
        }
    }

    private static void fail(MinecraftServer server, Throwable error) {
        failed = true;
        LogUtils.getLogger().error("BENCHMARK FAILED", error);
        if (window != null) {
            window.scenario = stage + ":failed";
            window.end();
            window.commit();
        }
        try {
            Files.writeString(RESULTS.resolveSibling("failed.txt"), error.toString());
        } catch (Exception ignored) {
        }
        server.halt(false);
    }

    private static BlockState block(String id) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalArgumentException("Missing block " + key);
        return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }

    private static BlockPos probe(BlockPos o) {
        return MACHINE.startsWith("mixer") ? o.offset(0, 1, 1) : o.offset(1, 1, 0);
    }

    private static void put(ServerLevel l, BlockPos o, int x, int y, int z, String id) {
        l.setBlockAndUpdate(o.offset(x, y, z), block(id));
    }

    private static void build(ServerLevel l, BlockPos o) throws Exception {
        build(l, o, MACHINE);
    }

    private static void build(ServerLevel l, BlockPos o, String machine) throws Exception {
        if (machine.equals("connected_pair")) {
            if (!IMPLEMENTATION.equals("mobvats")) throw new IllegalArgumentException("Pair requires MobVats");
            build(l, o, "vat_multi");
            var m = o.east(5);
            build(l, m, "mixer_5x5");
            put(l, m, 0, 1, 2, "mobvats:mixer_controller");
            l.setBlockAndUpdate(m.offset(0, 1, 1), material("mobvats:fluid_port", "mixer_t1_casing"));
            l.setBlockAndUpdate(o.offset(4, 1, 1), material("mobvats:fluid_port", "vat_t1_casing"));
            for (int y = 2; y <= 3; y++) pipe(l, o.offset(4, y, 1), "mobvats:fluid_conduit", "vat_t1_casing");
            pipe(l, m.offset(0, 2, 1), "mobvats:fluid_conduit", "mixer_t1_casing");
            l.setBlockAndUpdate(o.offset(4, 3, 0), material("mobvats:fluid_buffer", "vat_t1_casing"));
            l.setBlockAndUpdate(m.offset(0, 2, 0), material("mobvats:fluid_buffer", "mixer_t1_casing"));
            var output = l.getBlockEntity(o.offset(4, 1, 1));
            var input = l.getBlockEntity(m.offset(0, 1, 1));
            output.getClass()
                    .getMethod("setIoDirection", it.zerono.mods.zerocore.lib.data.IoDirection.class)
                    .invoke(output, it.zerono.mods.zerocore.lib.data.IoDirection.Output);
            output.getClass()
                    .getMethod("setIoMode", it.zerono.mods.zerocore.lib.data.IoMode.class)
                    .invoke(output, it.zerono.mods.zerocore.lib.data.IoMode.Active);
            input.getClass()
                    .getMethod("setIoDirection", it.zerono.mods.zerocore.lib.data.IoDirection.class)
                    .invoke(input, it.zerono.mods.zerocore.lib.data.IoDirection.Input);
            return;
        }
        boolean mob = IMPLEMENTATION.equals("mobvats");
        if (mob != (machine.startsWith("mixer") || machine.startsWith("vat")))
            throw new IllegalArgumentException("Machine belongs to other environment");
        int w = machine.endsWith("5x5") || machine.equals("vat_multi") ? 5 : 3;
        int h = machine.startsWith("vat") ? 4 : machine.equals("reprocessor") ? 7 : 3;
        String casing = mob
                ? (machine.startsWith("vat") ? "mobvats:vat_casing" : "mobvats:mixer_casing")
                : "bigreactors:" + (machine.equals("reactor") ? "basic_reactor" : machine) + "casing";
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                for (int z = 0; z < w; z++)
                    l.setBlockAndUpdate(
                            o.offset(x, y, z),
                            x == 0 || x == w - 1 || z == 0 || z == w - 1 || y == 0 || y == h - 1
                                    ? block(casing)
                                    : Blocks.AIR.defaultBlockState());
        switch (machine) {
            case "mixer_min", "mixer_5x5" -> {
                put(l, o, 0, 1, 1, "mobvats:mixer_controller");
                put(l, o, w / 2, h - 1, w / 2, "mobvats:mixer_impeller");
                for (int y = 0; y < h - 1; y++) put(l, o, w / 2, y, w / 2, "mobvats:mixer_shaft");
            }
            case "vat_min", "vat_multi" -> {
                put(l, o, 1, 1, 0, "mobvats:vat_controller");
                put(l, o, w / 2, 0, w / 2, "mobvats:vat_drain");
                int[] coordinates = w == 3 ? new int[] {1} : new int[] {1, 3};
                if (w > 3) put(l, o, w / 2, 0, w / 2, "mobvats:vat_casing");
                for (int x : coordinates)
                    for (int z : coordinates) {
                        put(l, o, x, 0, z, "mobvats:vat_drain");
                        put(l, o, x, 3, z, "mobvats:vat_harness");
                        put(l, o, x, 1, z, "mobvats:vat_flesh");
                        put(l, o, x, 2, z, "mobvats:vat_head");
                        var head = l.getBlockEntity(o.offset(x, 2, z));
                        head.getClass()
                                .getMethod("encode", ResourceLocation.class)
                                .invoke(head, ResourceLocation.withDefaultNamespace("cow"));
                    }
            }
            case "fluidizer" -> {
                put(l, o, 1, 1, 0, "bigreactors:fluidizercontroller");
                put(l, o, 0, 1, 1, "bigreactors:fluidizersolidinjector");
                put(l, o, 2, 1, 1, "bigreactors:fluidizeroutputport");
                put(l, o, 1, 1, 2, "bigreactors:fluidizerpowerport");
            }
            case "reprocessor" -> {
                put(l, o, 1, 1, 0, "bigreactors:reprocessorcontroller");
                put(l, o, 1, 6, 1, "bigreactors:reprocessorwasteinjector");
                put(l, o, 2, 1, 1, "bigreactors:reprocessoroutputport");
                put(l, o, 1, 1, 2, "bigreactors:reprocessorpowerport");
                put(l, o, 1, 2, 0, "bigreactors:reprocessorfluidinjector");
                put(l, o, 1, 0, 1, "bigreactors:reprocessorcollector");
            }
            case "reactor" -> {
                put(l, o, 1, 1, 0, "bigreactors:basic_reactorcontroller");
                put(l, o, 1, 2, 1, "bigreactors:basic_reactorcontrolrod");
                put(l, o, 1, 1, 1, "bigreactors:basic_reactorfuelrod");
            }
            default -> throw new IllegalArgumentException("Unknown machine " + machine);
        }
    }

    private static BlockState material(String id, String name) {
        var state = block(id);
        for (var property : state.getProperties())
            if (property.getName().equals("cladding_material")) return withValue(state, property, name);
        throw new IllegalArgumentException("Missing installed material property: " + id);
    }

    private static void pipe(ServerLevel l, BlockPos p, String id, String cladding) {
        var state = material(id, cladding);
        for (var property : state.getProperties())
            if (property.getName().equals("mode")) state = withValue(state, property, "tap");
        l.setBlockAndUpdate(p, state);
    }

    private static <T extends Comparable<T>> BlockState withValue(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String name) {
        return state.setValue(property, property.getValue(name).orElseThrow());
    }
}
