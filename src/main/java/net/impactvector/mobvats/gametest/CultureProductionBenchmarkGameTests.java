package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.mojang.authlib.GameProfile;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

/** Deterministic discrete-event production model over server-validated geometry, not illustrative diagrams.
 * Inventory supply/extraction is ideal; native biology, weapon strikes, cadence and Mixer batches are timed.
 */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class CultureProductionBenchmarkGameTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int HOUR = 72000, WARMUP = HOUR, MEASURE = 10 * HOUR;

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void denseBenchmark(GameTestHelper h) {
        benchmark(h, "dense", 4, 4, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void denseRecoveryBenchmark(GameTestHelper h) {
        benchmark(h, "dense-recovery", 4, 5, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoRowBenchmark(GameTestHelper h) {
        benchmark(h, "two-row", 7, 9, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void optimizedBenchmark(GameTestHelper h) {
        benchmark(h, "optimized", 7, 9, true);
    }

    private static void benchmark(GameTestHelper h, String name, int height, int depth, boolean spaced) {
        var xs = spaced ? new int[] {2, 6} : new int[] {1, 2, 3, 4, 5, 6, 7};
        var zs = depth == 4 ? new int[] {1, 2} : depth == 5 ? new int[] {1, 3} : new int[] {2, 6};
        int headY = height == 4 ? 2 : 3;
        var geometry = SimulacrumGeometry.capture(Objects.requireNonNull(EntityType.COW.create(h.getLevel())));
        h.assertTrue(
                geometry.width() == 1 && geometry.height() == 2 && geometry.depth() == 1,
                "Native cow footprint changed; review benchmark fixtures");
        for (int x = 0; x < 9; x++)
            for (int y = 0; y < height; y++)
                for (int z = 0; z < depth; z++) {
                    boolean shell = x == 0 || x == 8 || y == 0 || y == height - 1 || z == 0 || z == depth - 1;
                    h.setBlock(
                            ORIGIN.offset(x, y, z),
                            shell ? Content.state(VatPartType.Casing) : Blocks.AIR.defaultBlockState());
                }
        h.setBlock(ORIGIN.offset(0, 2, 1), Content.state(VatPartType.Controller));
        var heads = new ArrayList<BlockPos>();
        for (int x : xs)
            for (int z : zs) {
                var head = ORIGIN.offset(x, headY, z);
                heads.add(head);
                h.setBlock(head, Content.state(VatPartType.Head));
                h.setBlock(head.below(), Content.state(VatPartType.Flesh));
                h.setBlock(ORIGIN.offset(x, height - 1, z), Content.state(VatPartType.Harness));
                h.setBlock(ORIGIN.offset(x, 0, z), Content.state(VatPartType.Sump));
                ((VatHeadEntity) h.getBlockEntity(head)).encode(COW, geometry);
                int faceZ = z == zs[0] ? 0 : depth - 1;
                int toolY = height == 4 && x % 2 == 0 ? headY : headY - 1;
                var tool = ORIGIN.offset(x, toolY, faceZ);
                h.setBlock(tool, Content.state(VatPartType.ToolPort));
                ((VatToolPortEntity) h.getBlockEntity(tool)).setHeldItem(new ItemStack(Items.DIAMOND_SWORD));
                if (height == 4) {
                    itemPort(h, ORIGIN.offset(x, x % 2 == 0 ? headY - 1 : headY, faceZ), x % 2 != 0);
                } else {
                    itemPort(h, ORIGIN.offset(x, headY, faceZ), false);
                    itemPort(h, ORIGIN.offset(x, headY - 2, faceZ), true);
                }
            }
        // Bottom TAP network joins every Sump to a real exterior fluid output.
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < depth; z++) {
                var pos = ORIGIN.offset(x, 0, z);
                if (!(h.getBlockEntity(pos) instanceof VatSumpEntity))
                    h.setBlock(
                            pos,
                            ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                                    .setValue(ConduitBlock.MODE, ConduitMode.TAP));
            }
        var fluidOutput = ORIGIN.offset(0, 1, 1);
        h.setBlock(fluidOutput, Content.state(VatPartType.FluidPort));
        ((VatFluidPortEntity) h.getBlockEntity(fluidOutput)).setIoDirection(IoDirection.Output);
        if (depth == 4) {
            // Side feeders can reach the exposed end cows; neighboring bodies block deeper cows.
            for (int x : new int[] {0, 8}) {
                h.setBlock(ORIGIN.offset(x, 2, 2), Content.state(VatPartType.FeedPort));
                itemPort(h, ORIGIN.offset(x, 1, 2), false);
            }
        } else {
            int serviceZ = depth == 5 ? 2 : 4;
            for (int x : spaced ? new int[] {4} : new int[] {2, 4, 6}) {
                h.setBlock(ORIGIN.offset(x, 1, serviceZ), Content.state(VatPartType.FeedPort));
                itemPort(h, ORIGIN.offset(x, 0, serviceZ), false);
            }
            h.setBlock(ORIGIN.offset(depth == 5 ? 1 : 5, 0, serviceZ), Content.state(VatPartType.DrainValve));
            h.setBlock(ORIGIN.offset(4, height - 1, serviceZ), Content.state(VatPartType.FillValve));
            h.setBlock(ORIGIN.offset(3, height - 1, serviceZ), Content.state(VatPartType.FluidPort));
        }
        h.runAtTickTime(40, () -> {
            var vat = ((VatHeadEntity) h.getBlockEntity(heads.getFirst()))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(vat.isAssembled(), "Fixture did not form: " + vat.getLastError());
            for (var port : vat.itemPorts())
                if (port.getIoDirection().isInput())
                    port.staging().insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        });
        h.runAtTickTime(90, () -> {
            var vat = ((VatHeadEntity) h.getBlockEntity(heads.getFirst()))
                    .getMultiblockController()
                    .orElseThrow();
            h.assertTrue(vat.isAssembled(), "Illegal " + name + " structure: " + vat.getLastError());
            vat.setMachineActive(false);
            h.assertTrue(vat.getAssemblies().size() == heads.size(), "Lost cow assemblies");
            var tools = new HashSet<BlockPos>();
            for (var assembly : vat.getAssemblies()) {
                var head = assembly.head().getBlockPos();
                h.assertTrue(vat.getTopology().operational(head), "Nonoperational Head " + head);
                var routes = vat.getTopology().actors().stream()
                        .filter(a -> a.head().equals(head))
                        .toList();
                if (depth > 4)
                    h.assertTrue(
                            routes.stream()
                                    .anyMatch(a -> a.use().equals(PassiveUses.FEED)
                                            && !a.itemInputs().isEmpty()),
                            "No wheat input route " + head);
                h.assertTrue(
                        routes.stream()
                                .anyMatch(a -> a.use().equals(PassiveUses.HARVEST)
                                        && !a.itemOutputs().isEmpty()),
                        "No harvest output route " + head);
                h.assertTrue(
                        vat.getTopology()
                                .conduits()
                                .reachable(assembly.drain().getBlockPos(), ConduitResource.FLUID)
                                .contains(h.absolutePos(fluidOutput)),
                        "Sump cannot export XP " + head);
                var tool = routes.stream()
                        .filter(a -> a.use().equals(PassiveUses.HARVEST))
                        .min(Comparator.comparingInt(a -> a.actor().distManhattan(head)))
                        .orElseThrow()
                        .actor();
                h.assertTrue(tools.add(tool), "Fixture needs a shared-tool scheduler: " + head);
            }
            long productive = vat.getAssemblies().stream()
                    .filter(a -> vat.getTopology().actors().stream()
                            .anyMatch(r -> r.head().equals(a.head().getBlockPos())
                                    && r.use().equals(PassiveUses.FEED)
                                    && !r.itemInputs().isEmpty()))
                    .count();
            h.assertTrue(productive > 0 && (depth == 4 || productive == heads.size()), "Missing productive cows");
            verifySpecimenFouling(h, vat);
            h.assertTrue(
                    vat.metrics().total(MachineMetrics.HARVESTS) >= productive
                            && vat.metrics().total(MachineMetrics.WHEAT) >= productive,
                    "Native feed/harvest transactions failed: " + vat.metrics().total(MachineMetrics.HARVESTS));
            var registry = MixerRecipeRegistry.snapshot();
            try {
                var loaded = MixerRecipeRegistry.parse(CultureRecoveryGameTests.defaults());
                MixerRecipeRegistry.publish(loaded);
                var lines = new ArrayList<String>();
                lines.add(
                        "layout,medium,volume,medium_capacity_mb,legal_cows,productive_cows,yield,frequency,biological_kph,kills_ph,effective_kph,effective_per_100,xp_ph,xp_cost_ph,net_xp_ph,wheat_ph,fouled_mb_ph,charcoal_ph");
                for (var mode : depth == 4
                        ? List.of("water", "fresh")
                        : List.of("water", "fresh", "sustained", "self-funded")) {
                    var first = simulate(h, vat, mode, 9 * height * depth, loaded);
                    var repeat = simulate(h, vat, mode, 9 * height * depth, loaded);
                    h.assertTrue(first.equals(repeat), "Nonreproducible benchmark " + name + "/" + mode);
                    com.mojang.logging.LogUtils.getLogger().info("Culture benchmark {}/{}: {}", name, mode, first);
                    lines.add(name + "," + mode + "," + first);
                }
                var output = Path.of("build", "reports", "culture-benchmark", name + ".csv");
                Files.createDirectories(output.getParent());
                Files.write(output, lines);
                h.succeed();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            } finally {
                MixerRecipeRegistry.publish(registry);
            }
        });
    }

    private static void itemPort(GameTestHelper h, BlockPos pos, boolean output) {
        h.setBlock(pos, Content.state(VatPartType.ItemPort));
        ((VatItemPortEntity) h.getBlockEntity(pos)).setIoDirection(output ? IoDirection.Output : IoDirection.Input);
    }

    private static void verifySpecimenFouling(GameTestHelper h, MultiblockVat vat) {
        var profile = PassiveProcessCompiler.compile(Objects.requireNonNull(PassiveMobDefinitionRegistry.resolve(COW)));
        for (var assembly : vat.getAssemblies()) {
            vat.medium().setFluid(new FluidStack(Fluids.WATER, vat.medium().getCapacity()));
            var context = new ProcessContext(vat.processEngine(), assembly, new SpecimenProcessState(), profile);
            double happiness = vat.yieldMultiplier(assembly.head().getBlockPos());
            int expected = (int) Math.floor(50 * (1 + 4 * Math.clamp((2 - happiness) / 1.5, 0, 1)) + 1e-10);
            h.assertTrue(
                    Fouling.commit(new ProcessTransaction(), context, "test:individual", 50)
                            && vat.medium().dirtyAmount() == expected,
                    "Fouling did not use individual happiness at " + context.head());
        }
    }

    private static String simulate(
            GameTestHelper h, MultiblockVat vat, String mode, int volume, MixerRecipeRegistry.Loaded recipes) {
        var assemblies = vat.getAssemblies().stream()
                .sorted(Comparator.comparing(a -> a.head().getBlockPos(), VatTopologyCompiler.POSITION))
                .toList();
        int n = assemblies.size();
        var productive = new boolean[n];
        int productiveCount = 0;
        for (int i = 0; i < n; i++) {
            var head = assemblies.get(i).head().getBlockPos();
            productive[i] = vat.getTopology().actors().stream()
                    .anyMatch(r -> r.head().equals(head)
                            && r.use().equals(PassiveUses.FEED)
                            && !r.itemInputs().isEmpty());
            if (productive[i]) productiveCount++;
        }
        var medium = vat.medium();
        var fresh = mode.equals("water")
                ? new FluidStack(Fluids.WATER, medium.getCapacity())
                : CultureRecoveryGameTests.culture(medium.getCapacity(), 250, 0);
        medium.setFluid(fresh);
        var growth = new ProcessState[n];
        var milk = new ProcessState[n];
        var yields = new YieldRemainders[n];
        var adult = new boolean[n];
        var nextStrike = new int[n];
        var hits = new int[n];
        var frequencies = new double[n];
        var happiness = new double[n];
        var definition = Objects.requireNonNull(PassiveMobDefinitionRegistry.resolve(COW));
        var contexts = new ProcessContext[n];
        var profile = PassiveProcessCompiler.compile(definition);
        for (int i = 0; i < n; i++)
            contexts[i] =
                    new ProcessContext(vat.processEngine(), assemblies.get(i), new SpecimenProcessState(), profile);
        var milkDefinition = definition.secondary_outputs().stream()
                .filter(s -> s.process().equals(PassiveProcessCompiler.MILK))
                .findFirst()
                .orElseThrow();
        int strikes = (int) Math.ceil(VanillaEntityLoot.health(h.getLevel(), COW, new CompoundTag())
                / VanillaEntityLoot.damage(h.getLevel(), COW, new ItemStack(Items.DIAMOND_SWORD), new CompoundTag()));
        var cow = Objects.requireNonNull(EntityType.COW.create(h.getLevel()));

        var player = new FakePlayer(h.getLevel(), new GameProfile(new UUID(0, 1), "[Benchmark]"));
        int rewardSample = cow.getExperienceReward(h.getLevel(), player);
        h.assertTrue(rewardSample >= 1 && rewardSample <= 3, "Native cow XP contract changed");
        for (int i = 0; i < n; i++) {
            adult[i] = !productive[i]; // Unfed diagnostic cows remain adult and still produce milk/fouling.
            growth[i] = new ProcessState();
            milk[i] = new ProcessState();
            yields[i] = new YieldRemainders();
            happiness[i] = vat.yieldMultiplier(assemblies.get(i).head().getBlockPos());
        }
        var filter = recipes.recipes().get("mobvats:filter_general");
        var enrich = recipes.recipes().get("mobvats:enrich_culture");
        var mixer = new MixerState(() -> {});
        int batchDone = -1, filterDone = -1;
        long kills = 0, xp = 0, fouled = 0, charcoal = 0, xpMb = 0;
        boolean selfFunded = mode.equals("self-funded");
        int warmup = selfFunded ? 200 * HOUR : WARMUP;
        int measurement = selfFunded ? 100 * HOUR : MEASURE;
        long xpBankMb = 0;
        int enrichmentCost = enrich.fluids().get(1).amount();
        double effective = 0, rateTotal = 0, biological = 0;
        boolean refresh = true;
        for (int tick = 0; tick < warmup + measurement; tick++) {
            if (selfFunded && tick % (50 * HOUR) == 0)
                com.mojang.logging.LogUtils.getLogger()
                        .info(
                                "Self-funded benchmark: {} cows / {} exterior, hour {}, clean {} mB, dirty {} mB, bank {} mB XP",
                                n,
                                volume,
                                tick / HOUR,
                                medium.cleanAmount(),
                                medium.dirtyAmount(),
                                xpBankMb);
            boolean measure = tick >= warmup;
            if (filterDone == tick) {
                if (!mixer.commit(filter)) throw new IllegalStateException("Benchmark filtration failed");
                if (measure) charcoal++;
                mixer.fluids(true).fill(mixer.fluids(false).drain(1000, EXECUTE), EXECUTE);
                mixer.fluids(true)
                        .fill(
                                new FluidStack(
                                        ProductFluids.EXPERIENCE.get(),
                                        enrich.fluids().get(1).amount()),
                                EXECUTE);
                filterDone = -1;
            }
            if (batchDone == tick) {
                if (!mixer.commit(enrich)) throw new IllegalStateException("Benchmark enrichment failed");
                var recovered = mixer.fluids(false).drain(1000, EXECUTE);
                if (medium.fill(recovered, EXECUTE) != 1000) throw new IllegalStateException("Recovery refill failed");
                if (measure) {
                    xpMb += enrich.fluids().get(1).amount();
                }
                // The isolated test model appends escrow tanks rather than reusing physical buffers.
                // Retire only empty escrow after a complete batch, avoiding an unbounded allocator
                // matrix in the long run. No fluid, reagent or in-flight work may be discarded.
                for (boolean input : new boolean[] {true, false})
                    for (int tank = 0; tank < mixer.fluids(input).getTanks(); tank++)
                        h.assertTrue(
                                mixer.fluids(input).getFluidInTank(tank).isEmpty(), "Recovery left fluid inventory");
                h.assertTrue(mixer.itemInput().getStackInSlot(0).isEmpty(), "Recovery left charcoal inventory");
                mixer = new MixerState(() -> {});
                batchDone = -1;
                refresh = true;
            }
            if (refresh) {
                for (int i = 0; i < n; i++)
                    frequencies[i] =
                            vat.frequencyMultiplier(assemblies.get(i).head().getBlockPos());
                refresh = false;
            }
            for (int i = 0; i < n; i++) {
                if (measure && productive[i]) {
                    rateTotal += frequencies[i];
                    biological += frequencies[i] / definition.juvenile_growth_ticks();
                }
                if (!adult[i]) {
                    growth[i].elapsed += growth[i].advance(frequencies[i]);
                    if (growth[i].elapsed >= definition.juvenile_growth_ticks()) {
                        adult[i] = true;
                        if (!mode.equals("fresh")) {
                            int before = medium.dirtyAmount();
                            if (!Fouling.commit(new ProcessTransaction(), contexts[i], PassiveProcessCompiler.GROWTH))
                                throw new IllegalStateException("Growth fouling failed");
                            int amount = medium.dirtyAmount() - before;
                            if (measure) fouled += amount;
                            refresh = true;
                        }
                    }
                }
                if (!adult[i]) continue;
                milk[i].elapsed += milk[i].advance(milkDefinition.accelerated() ? frequencies[i] : 1);
                if ((tick + 1) % CowBalance.PROCESS_CADENCE != 0) continue;
                if (milk[i].elapsed >= milkDefinition.interval_ticks()) {
                    milk[i].elapsed = 0;
                    if (!mode.equals("fresh") && milkDefinition.accelerated()) {
                        int before = medium.dirtyAmount();
                        if (!Fouling.commit(
                                new ProcessTransaction(),
                                contexts[i],
                                PassiveProcessCompiler.MILK,
                                milkDefinition.fouling_mb())) throw new IllegalStateException("Milk fouling failed");
                        int amount = medium.dirtyAmount() - before;
                        if (measure) fouled += amount;
                        refresh = true;
                    }
                }
                if (!productive[i] || tick < nextStrike[i]) continue;
                nextStrike[i] = tick + VatToolPortEntity.INTERACTION_TICKS;
                if (++hits[i] < strikes) continue;
                int nativeXp = 2; // Native cow reward is uniformly 1..3; report deterministic expected XP.
                var yield = yields[i].plan(happiness[i], h.getLevel().registryAccess());
                int reward = yield.amount("kill/xp", nativeXp);
                if (!new ProcessTransaction().add(yield.step()).commit())
                    throw new IllegalStateException("Yield commit failed");
                xpBankMb += (long) reward * ExperienceFluid.ratio();
                if (measure) {
                    kills++;
                    effective += happiness[i];
                    xp += reward;
                }
                adult[i] = false;
                hits[i] = 0;
                growth[i].elapsed = 0;
            }
            // One programmable Mixer, exact whole batches, with a buffer holding the intermediate.
            if ((mode.equals("sustained") || selfFunded)
                    && batchDone < 0
                    && medium.dirtyAmount() >= 1000
                    && (!selfFunded || xpBankMb >= enrichmentCost)) {
                if (selfFunded) xpBankMb -= enrichmentCost; // Reserve only this Vat's committed harvest XP.
                mixer.fluids(true).fill(medium.drain(1000, EXECUTE), EXECUTE);
                mixer.itemInput().insertItem(0, new ItemStack(Items.CHARCOAL), false);
                filterDone = tick + filter.batchTicks();
                batchDone = tick + filter.batchTicks() + enrich.batchTicks();
                refresh = true;
            }
        }
        h.assertTrue(xpBankMb >= 0, "External XP subsidy in self-funded benchmark");
        double hours = (double) measurement / HOUR;
        double cost = xpMb / (double) ExperienceFluid.ratio() / hours;
        return String.format(
                Locale.ROOT,
                "%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                volume,
                medium.getCapacity(),
                n,
                productiveCount,
                java.util.stream.IntStream.range(0, n)
                        .filter(i -> productive[i])
                        .mapToDouble(i -> happiness[i])
                        .average()
                        .orElseThrow(),
                rateTotal / measurement / productiveCount,
                biological / hours,
                kills / hours,
                effective / hours,
                effective / hours * 100 / volume,
                xp / hours,
                cost,
                xp / hours - cost,
                kills * definition.feed().count() / hours,
                fouled / hours,
                charcoal / hours);
    }
}
