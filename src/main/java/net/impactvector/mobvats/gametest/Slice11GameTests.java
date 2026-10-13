package net.impactvector.mobvats.gametest;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.*;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

/** Slice 11 contract tests. Fixtures drive setup and transactions without a client. */
@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class Slice11GameTests {
    private static final BlockPos O = new BlockPos(1, 1, 1),
            A = O.offset(2, 3, 4),
            B = O.offset(6, 3, 4),
            CENTER = O.offset(4, 2, 4),
            INPUT = O.offset(4, 4, 4);

    private static ResourceLocation id(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static void shell(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int y = 0; y <= 4; y++)
                for (int z = 0; z <= 8; z++)
                    if (x == 0 || x == 8 || y == 0 || y == 4 || z == 0 || z == 8)
                        h.setBlock(
                                O.offset(x, y, z),
                                Content.state(y == 0 || y == 4 ? VatPartType.Casing : VatPartType.Glass));
        h.setBlock(O.offset(1, 2, 0), Content.state(VatPartType.Controller));
    }

    private static MobAssembly specimen(GameTestHelper h, BlockPos pos, String name) {
        h.setBlock(pos, Content.state(VatPartType.Head));
        h.setBlock(pos.below(), Content.state(VatPartType.Flesh));
        var head = (VatHeadEntity) h.getBlockEntity(pos);
        head.encode(id(name), SimulacrumGeometry.resolve(.9, 1.4, .9, Direction.NORTH, null));
        h.setBlock(pos.above(), Content.state(VatPartType.Harness));
        h.setBlock(pos.below(2), Content.state(VatPartType.Sump));
        return new MobAssembly(List.of((VatHarnessEntity) h.getBlockEntity(pos.above())), head, (VatSumpEntity)
                h.getBlockEntity(pos.below(2)));
    }

    private static VatToolPortEntity tool(GameTestHelper h, BlockPos pos, ItemStack held) {
        h.setBlock(pos, Content.state(VatPartType.ToolPort));
        var tool = (VatToolPortEntity) h.getBlockEntity(pos);
        tool.setHeldItem(held);
        return tool;
    }

    private static VatFeedPortEntity feed(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, Content.state(VatPartType.FeedPort));
        return (VatFeedPortEntity) h.getBlockEntity(pos);
    }

    private static VatItemPortEntity item(GameTestHelper h, BlockPos pos, boolean input) {
        h.setBlock(pos, Content.state(VatPartType.ItemPort));
        var port = (VatItemPortEntity) h.getBlockEntity(pos);
        port.setIoDirection(input ? IoDirection.Input : IoDirection.Output);
        return port;
    }

    private static FluidBufferEntity buffer(GameTestHelper h, BlockPos pos, ConduitCladding clad, String filter) {
        h.setBlock(pos, FluidBufferContent.state(clad));
        var e = (FluidBufferEntity) h.getBlockEntity(pos);
        e.filter(filter);
        return e;
    }

    private static PhysicalFluidStorage storage(GameTestHelper h, List<FluidBufferEntity> members) {
        var storage = new PhysicalFluidStorage();
        storage.compile(members, ConduitTopology.EMPTY);
        return storage;
    }

    private static VatTopology topology(GameTestHelper h, List<MobAssembly> assemblies, AbstractVatEntity... ports) {
        return VatTopologyCompiler.compile(
                h.getLevel(), h.absolutePos(O), h.absolutePos(O.offset(8, 4, 8)), assemblies, List.of(ports));
    }

    private static MultiblockVat vat(GameTestHelper h) {
        var c = ((VatHeadEntity) h.getBlockEntity(A)).getMultiblockController().orElseThrow();
        h.assertTrue(
                c.isAssembled(),
                "Fixture did not form: "
                        + c.getLastError()
                                .map(e -> e.getChatMessage().getString())
                                .orElse("no error"));
        return c;
    }

    private static void cycles(MultiblockVat c, int n) {
        for (int i = 0; i < n * VatProcessEngine.CADENCE; i++) c.processEngine().tick();
    }

    private static void twoRig(GameTestHelper h) {
        shell(h);
        specimen(h, A, "cow");
        specimen(h, B, "cow");
    }

    private static void suppliedRig(GameTestHelper h, String species) {
        shell(h);
        specimen(h, A, species);
        feed(h, CENTER.above());
        item(h, INPUT, true).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 16));
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void legacyUseAliasesNormalize(GameTestHelper h) {
        h.assertTrue(
                PassiveUses.canonical("cowHarvest").equals("mobvats:kill")
                        && PassiveUses.canonical("cowMilk").equals("mobvats:tool")
                        && PassiveUses.canonical("cowDrain").equals("mobvats:drain"),
                "Legacy use migration failed");
        var harvest = new PassiveMobDefinition.Harvest(
                "mobvats:vanilla_entity_death_loot", "mobvats:meat", 1000, 0, "cowHarvest", 10);
        h.assertTrue(harvest.actor_use().equals(PassiveUses.HARVEST), "Constructor failed to migrate configuration");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void interiorToolReachesOppositeSpecimens(GameTestHelper h) {
        var a = specimen(h, A, "cow");
        var b = specimen(h, B, "cow");
        var t = tool(h, CENTER, new ItemStack(Items.IRON_SWORD));
        var topology = topology(h, List.of(a, b), t, a.drain(), b.drain());
        h.assertTrue(
                topology.actors().stream()
                                .filter(r -> r.actor().equals(t.getBlockPos()))
                                .count()
                        == 2,
                "Interior Tool did not compile opposite independent routes");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void toolObstructionOnlyRemovesOneRoute(GameTestHelper h) {
        var a = specimen(h, A, "cow");
        var b = specimen(h, B, "cow");
        var t = tool(h, CENTER, new ItemStack(Items.IRON_SWORD));
        h.setBlock(CENTER.west(), Blocks.STONE);
        var topology = topology(h, List.of(a, b), t);
        h.assertTrue(
                topology.actors().size() == 1
                        && topology.actors().getFirst().head().equals(b.head().getBlockPos()),
                "One obstruction disabled the other Tool target");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void centralFeedReachesFourWithinThreeBlocks(GameTestHelper h) {
        var assemblies = new ArrayList<MobAssembly>();
        for (var p : List.of(A, B, O.offset(4, 3, 2), O.offset(4, 3, 6))) assemblies.add(specimen(h, p, "cow"));
        var f = feed(h, CENTER);
        var topology = topology(h, assemblies, f);
        h.assertTrue(
                topology.actors().size() == 4,
                "Central Feed did not reach four optimally spaced specimens: " + topology.actors());
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void feedObstructionOnlyRemovesOneTube(GameTestHelper h) {
        var a = specimen(h, A, "cow");
        var b = specimen(h, B, "cow");
        var f = feed(h, CENTER);
        h.setBlock(CENTER.west(), Blocks.STONE);
        var topology = topology(h, List.of(a, b), f);
        h.assertTrue(
                topology.actors().size() == 1
                        && topology.actors().getFirst().head().equals(b.head().getBlockPos()),
                "Feed obstruction leaked to clear target");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void genericInputNeverCompilesFeedContacts(GameTestHelper h) {
        var a = specimen(h, A, "cow");
        var input = item(h, O.offset(2, 2, 0), true);
        var topology = topology(h, List.of(a), input);
        h.assertTrue(
                topology.itemContacts().isEmpty() && topology.actors().isEmpty(),
                "Generic Item Input acquired feed semantics");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void buffersMergeAtFourThousandPerMember(GameTestHelper h) {
        var p = O.offset(2, 1, 2);
        var a = buffer(h, p, ConduitCladding.BARE, "");
        var b = buffer(h, p.east(), ConduitCladding.BARE, "");
        var c = buffer(h, p.east(2), ConduitCladding.BARE, "");
        var s = storage(h, List.of(a, b, c));
        h.assertTrue(
                s.clusters().size() == 1
                        && s.clusters().getFirst().tank().getCapacity() == 12000
                        && a.tank.getCapacity() == 4000,
                "Physical cluster capacity incorrect");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void disconnectedBuffersRemainSeparate(GameTestHelper h) {
        var a = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "");
        var b = buffer(h, O.offset(4, 1, 2), ConduitCladding.BARE, "");
        var s = storage(h, List.of(a, b));
        h.assertTrue(
                s.clusters().size() == 2
                        && s.clusters().stream().allMatch(c -> c.tank().getCapacity() == 4000),
                "Disconnected buffers merged");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void filtersPreventIncompatibleMerging(GameTestHelper h) {
        var p = O.offset(2, 1, 2);
        var a = buffer(h, p, ConduitCladding.BARE, "minecraft:water");
        var b = buffer(h, p.east(), ConduitCladding.BARE, "mobvats:experience");
        var s = storage(h, List.of(a, b));
        h.assertTrue(
                s.clusters().size() == 2
                        && a.tank.fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 100), FluidAction.EXECUTE) == 0,
                "Incompatible endpoint filters merged/accepted fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void tagFilterAcceptsOnlyTaggedFluids(GameTestHelper h) {
        var a = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "#minecraft:water");
        h.assertTrue(
                a.tank.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 100
                        && a.tank.fill(new FluidStack(ProductFluids.WASTE.get(), 100), FluidAction.EXECUTE) == 0,
                "Fluid tag filter incorrect");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void clustersPreferMatchingContent(GameTestHelper h) {
        var a = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "");
        var b = buffer(h, O.offset(4, 1, 2), ConduitCladding.BARE, "");
        b.tank.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        var s = storage(h, List.of(a, b));
        var tanks =
                s.clusters().stream().map(PhysicalFluidStorage.Cluster::tank).toList();
        h.assertTrue(
                PhysicalFluidStorage.select(tanks, new FluidStack(Fluids.WATER, 100)) == tanks.getLast(),
                "Empty storage displaced matching content");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void physicalClusterSplitConservesContents(GameTestHelper h) {
        var p = O.offset(2, 1, 2);
        var a = buffer(h, p, ConduitCladding.BARE, "");
        var b = buffer(h, p.east(), ConduitCladding.BARE, "");
        var s = storage(h, List.of(a, b));
        s.clusters().getFirst().tank().fill(new FluidStack(Fluids.WATER, 6500), FluidAction.EXECUTE);
        s.compile(List.of(a), ConduitTopology.EMPTY);
        h.assertTrue(
                a.tank.getFluidAmount() == 4000
                        && b.tank.getFluidAmount() == 2500
                        && s.clusters().getFirst().tank().getFluidAmount() == 4000,
                "Cluster split duplicated/dropped contents");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void transactionWritesThroughPhysicalCluster(GameTestHelper h) {
        var a = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "");
        var s = storage(h, List.of(a));
        var step = ProcessOutput.fluid(s.clusters().getFirst().tank(), new FluidStack(Fluids.WATER, 1000));
        h.assertTrue(
                new ProcessTransaction().add(step).commit() && a.tank.getFluidAmount() == 1000,
                "Transaction changed only transient cluster state");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void bufferFilterAndContentPersist(GameTestHelper h) {
        var a = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "minecraft:water");
        a.tank.fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE);
        var saved = a.saveWithoutMetadata(h.getLevel().registryAccess());
        var b = new FluidBufferEntity(a.getBlockPos(), a.getBlockState());
        b.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(
                b.filter().equals("minecraft:water") && b.tank.getFluidAmount() == 3000,
                "Physical buffer state lost on reload");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void claddingHasMatchingShellRules(GameTestHelper h) {
        var shell = O.offset(0, 2, 2);
        buffer(h, shell, ConduitCladding.GLASS, "");
        h.assertTrue(
                ConduitStructure.allows(
                        h.getLevel(), h.absolutePos(shell), h.absolutePos(O), h.absolutePos(O.offset(8, 4, 8)), false),
                "Glass buffer rejected legal side");
        buffer(h, O, ConduitCladding.CASING, "");
        h.assertTrue(
                ConduitStructure.allows(
                        h.getLevel(), h.absolutePos(O), h.absolutePos(O), h.absolutePos(O.offset(8, 4, 8)), false),
                "Casing buffer rejected corner");
        buffer(h, shell, ConduitCladding.BARE, "");
        h.assertTrue(
                !ConduitStructure.allows(
                        h.getLevel(), h.absolutePos(shell), h.absolutePos(O), h.absolutePos(O.offset(8, 4, 8)), false),
                "Bare buffer accepted shell");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void toolLocalMultiStackBackpressureAndPersistence(GameTestHelper h) {
        var t = tool(h, CENTER, new ItemStack(Items.IRON_SWORD));
        var other = tool(h, CENTER.north(2), new ItemStack(Items.IRON_SWORD));
        var out = item(h, INPUT, false);
        out.staging().setStackInSlot(0, new ItemStack(Items.BEEF, 63));
        t.results().store(List.of(new ItemStack(Items.BEEF, 64), new ItemStack(Items.LEATHER, 5)));
        long moved = t.results().drain(List.of(out));
        h.assertTrue(
                moved == 1
                        && t.results().itemCount() == 68
                        && other.results().store(List.of(new ItemStack(Items.BEEF, 2))),
                "Backpressure leaked or partial capacity lost batch");
        var saved = t.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        t.results().clear();
        t.syncDataFrom(saved, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                t.results().itemCount() == 68 && t.results().stackCount() == 2, "Tool-local batch did not persist");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void actualWeaponDamageRequiresRepeatedHits(GameTestHelper h) {
        var s = new ItemStack(Items.WOODEN_SWORD);
        float damage = VanillaEntityLoot.damage(h.getLevel(), id("cow"), s, new CompoundTag());
        float health = VanillaEntityLoot.health(h.getLevel(), id("cow"), new CompoundTag());
        h.assertTrue(
                damage == 4 && Math.ceil(health / damage) == 3,
                "Actual weapon/health hit count incorrect: " + damage + " / " + health);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void sharpnessReducesRequiredHits(GameTestHelper h) {
        var s = new ItemStack(Items.WOODEN_SWORD);
        float base = VanillaEntityLoot.damage(h.getLevel(), id("cow"), s, new CompoundTag());
        s.enchant(
                h.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SHARPNESS),
                3);
        float enchanted = VanillaEntityLoot.damage(h.getLevel(), id("cow"), s, new CompoundTag());
        h.assertTrue(Math.ceil(10 / enchanted) < Math.ceil(10 / base), "Sharpness did not reduce hit count");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void repeatedStabsRespectUnbreaking(GameTestHelper h) {
        var s = new ItemStack(Items.NETHERITE_SWORD);
        s.enchant(
                h.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.UNBREAKING),
                3);
        for (int i = 0; i < 200; i++) s = VanillaEntityLoot.damageWeapon(h.getLevel(), id("cow"), s);
        h.assertTrue(
                s.getDamageValue() > 0 && s.getDamageValue() < 120,
                "Repeated stabs bypassed Unbreaking: " + s.getDamageValue());
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 70)
    public static void wheatFeedsSheepThroughDedicatedPort(GameTestHelper h) {
        suppliedRig(h, "sheep");
        tool(h, CENTER, new ItemStack(Items.WOODEN_SWORD));
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(
                    PassiveMobDefinitionRegistry.resolve(id("sheep")).feed().accepts(new ItemStack(Items.WHEAT)),
                    "1.21.1 sheep_food does not resolve wheat");
            h.assertTrue(
                    !c.getTopology().actors().stream()
                            .filter(r -> r.use().equals(PassiveUses.FEED))
                            .toList()
                            .isEmpty(),
                    "Feed route missing");
            h.assertTrue(
                    c.processEngine()
                                    .state(h.absolutePos(A))
                                    .process(PassiveProcessCompiler.HARVEST)
                                    .data
                                    .contains("killHealth")
                            || c.metrics().total(MachineMetrics.HARVESTS) > 0
                                    && c.metrics().total(MachineMetrics.WHEAT) > 0,
                    "Wheat never made sheep combat eligible through Feed Port");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 70)
    public static void shearsProduceCapturedColorIntoLocalBuffer(GameTestHelper h) {
        suppliedRig(h, "sheep");
        var state = new CompoundTag();
        state.putByte("Color", (byte) DyeColor.RED.getId());
        ((VatHeadEntity) h.getBlockEntity(A)).updateSpecimenState(state);
        var t = tool(h, CENTER, new ItemStack(Items.SHEARS));
        h.runAtTickTime(35, () -> {
            vat(h);
            h.assertTrue(
                    t.results().snapshot().stream().anyMatch(s -> s.is(Items.RED_WOOL))
                            && t.heldItem().getDamageValue() == 1
                            && ((VatHeadEntity) h.getBlockEntity(A))
                                    .specimenState()
                                    .getBoolean("Sheared"),
                    "Shearing lost color/state/durability/local result");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 75)
    public static void eggBlocksAndCommitsOnlyThroughOwnedDrain(GameTestHelper h) {
        suppliedRig(h, "chicken");
        var out = item(h, A.below(3), false);
        out.staging().setStackInSlot(0, new ItemStack(Items.STONE, 64));
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var state = c.processEngine().state(h.absolutePos(A)).process("mobvats:egg");
            state.phase = ProcessState.Phase.READY;
            state.elapsed = 9000;
            cycles(c, 1);
            h.assertTrue(state.phase == ProcessState.Phase.READY, "Blocked egg reset without capacity");
            out.staging().setStackInSlot(0, ItemStack.EMPTY);
            cycles(c, 1);
            h.assertTrue(
                    out.staging().getStackInSlot(0).is(Items.EGG)
                            && c.metrics().total("mobvats:egg/item") == 1
                            && c.killOutputs().isEmpty(),
                    "Egg did not commit once through Drain output: output="
                            + out.staging().getStackInSlot(0) + " phase=" + state.phase + " metric="
                            + c.metrics().total("mobvats:egg/item") + " status="
                            + c.processEngine().status(h.absolutePos(A)));
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 70)
    public static void swordBreakPreservesHealthProgress(GameTestHelper h) {
        suppliedRig(h, "cow");
        var s = new ItemStack(Items.WOODEN_SWORD);
        s.setDamageValue(s.getMaxDamage() - 1);
        var t = tool(h, CENTER, s);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var state = c.processEngine().state(h.absolutePos(A));
            h.assertTrue(
                    t.heldItem().isEmpty()
                            && state.stage() == LifeStage.ADULT
                            && state.process(PassiveProcessCompiler.HARVEST)
                                            .data
                                            .getFloat("killHealth")
                                    == 6,
                    "Sword break destroyed or reset specimen health");
            var saved = state.save(h.getLevel().registryAccess());
            h.assertTrue(
                    SpecimenProcessState.load(saved, h.getLevel().registryAccess())
                                    .process(PassiveProcessCompiler.HARVEST)
                                    .data
                                    .getFloat("killHealth")
                            == 6,
                    "Kill progress lost on reload");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 100)
    public static void lethalKillRoutesExperienceFluidThroughDrain(GameTestHelper h) {
        suppliedRig(h, "cow");
        tool(h, CENTER, new ItemStack(Items.IRON_SWORD));
        var xp = buffer(h, A.below(3), ConduitCladding.CASING, "mobvats:experience");
        h.runAtTickTime(65, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.processEngine().state(h.absolutePos(A)).stage() == LifeStage.JUVENILE
                            && xp.tank.getFluid().is(ProductFluids.EXPERIENCE.get())
                            && xp.tank.getFluidAmount()
                                    == c.metrics().total(MachineMetrics.XP) * ExperienceFluid.ratio(),
                    "Kill XP did not become Drain-routed normal fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void mixerCultureConsumesExperienceFluid(GameTestHelper h) {
        var s = new MixerState(() -> {});
        s.fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var xp = new FluidStack(ProductFluids.EXPERIENCE.get(), 250);
        h.assertTrue(
                s.fluids(true).fill(xp, FluidAction.EXECUTE) == xp.getAmount()
                        && s.commit(
                                net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry
                                        .recipe("culture_slurry"))
                        && s.amount(4) == 0
                        && s.amount(2) == 1000
                        && !s.save(h.getLevel().registryAccess()).contains("xp"),
                "Mixer retained private XP scalar or failed normal fluid recipe input");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void feedAndToolRenderGeometryDoesNotChangeComfort(GameTestHelper h) {
        var a = specimen(h, A, "cow");
        feed(h, CENTER);
        tool(h, CENTER.east(), new ItemStack(Items.SHEARS));
        buffer(h, A.west().below(), ConduitCladding.BARE, "");
        var observed = a.clearance(h.getLevel());
        h.assertTrue(
                observed.solidIntrusions().contains(h.absolutePos(A.west().below())),
                "Physical buffer missing from clearance");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 70)
    public static void noPhysicalBuffersMeansNoVatCapacity(GameTestHelper h) {
        suppliedRig(h, "cow");
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.processFluids().getTanks() == 0
                            && c.fluidHandler(IoDirection.Input)
                                            .fill(
                                                    new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 1000),
                                                    FluidAction.EXECUTE)
                                    == 0,
                    "Profile granted invisible bulk storage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 70)
    public static void eggsReachReadyWithoutAnyOutputRoute(GameTestHelper h) {
        suppliedRig(h, "chicken");
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            var state = c.processEngine().state(h.absolutePos(A)).process("mobvats:egg");
            state.phase = ProcessState.Phase.RUNNING;
            state.elapsed = 8999;
            cycles(c, 2);
            h.assertTrue(
                    state.phase == ProcessState.Phase.READY
                            && state.elapsed == 9000
                            && c.killOutputs().isEmpty(),
                    "Egg timer failed to reach/preserve blocked readiness without a route");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 140)
    public static void oneToolServicesMultipleSpecimensOverTime(GameTestHelper h) {
        twoRig(h);
        var f = feed(h, CENTER.above());
        item(h, INPUT, true).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 8));
        var t = tool(h, CENTER, new ItemStack(Items.WOODEN_SWORD));
        buffer(h, A.below(3), ConduitCladding.CASING, "mobvats:experience");
        buffer(h, B.below(3), ConduitCladding.CASING, "mobvats:experience");
        h.runAtTickTime(35, () -> {
            h.assertTrue(
                    vat(h).getTopology().actors().stream()
                                    .filter(r -> r.actor().equals(t.getBlockPos()))
                                    .count()
                            == 2,
                    "Shared Tool routes missing");
        });
        h.runAtTickTime(80, () -> {
            var c = vat(h);
            for (var p : List.of(A, B)) {
                var state = c.processEngine().state(h.absolutePos(p));
                h.assertTrue(
                        state.process(PassiveProcessCompiler.HARVEST).data.contains("killHealth")
                                || state.stage() == LifeStage.JUVENILE,
                        "Shared Tool starved a cardinal specimen");
            }
            h.assertTrue(t.heldItem().getDamageValue() >= 3, "Tool cadence never scheduled repeated interactions");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 110)
    public static void blockedToolDoesNotBlockAnotherTool(GameTestHelper h) {
        twoRig(h);
        feed(h, CENTER.above());
        item(h, INPUT, true).staging().setStackInSlot(0, new ItemStack(Items.WHEAT, 8));
        var a = tool(h, O.offset(0, 2, 4), new ItemStack(Items.IRON_SWORD));
        var b = tool(h, O.offset(8, 2, 4), new ItemStack(Items.IRON_SWORD));
        a.results().store(List.of(new ItemStack(Items.DIAMOND, 64)));
        buffer(h, A.below(3), ConduitCladding.CASING, "mobvats:experience");
        buffer(h, B.below(3), ConduitCladding.CASING, "mobvats:experience");
        h.runAtTickTime(75, () -> {
            var c = vat(h);
            h.assertTrue(
                    a.results().itemCount() == 64
                            && a.heldItem().getDamageValue() == 0
                            && b.heldItem().getDamageValue() >= 2
                            && c.metrics().total(MachineMetrics.HARVESTS) >= 1,
                    "Tool-local backpressure escaped to independent weapon: "
                            + c.processEngine().status(h.absolutePos(B)));
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 100)
    public static void slurryFeedsThroughFeedFluidBus(GameTestHelper h) {
        suppliedRig(h, "cow");
        ((VatItemPortEntity) h.getBlockEntity(INPUT)).staging().setStackInSlot(0, ItemStack.EMPTY);
        var source = buffer(h, INPUT.south(), ConduitCladding.CASING, "mobvats:feeding_slurry");
        source.tank.fill(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 500), FluidAction.EXECUTE);
        h.setBlock(
                CENTER.above().south(),
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        tool(h, CENTER, new ItemStack(Items.IRON_SWORD));
        buffer(h, A.below(3), ConduitCladding.CASING, "mobvats:experience");
        h.runAtTickTime(70, () -> {
            var c = vat(h);
            h.assertTrue(
                    c.metrics().total("feedingConsumed") == 250
                            && source.tank.getFluidAmount() == 250
                            && c.processEngine().state(h.absolutePos(A)).stage() == LifeStage.JUVENILE,
                    "Slurry failed last-mile Feed bus routing: "
                            + c.processEngine().status(h.absolutePos(A)));
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 100)
    public static void lethalResultWaitsForXpStorageWithoutReroll(GameTestHelper h) {
        suppliedRig(h, "cow");
        var t = tool(h, CENTER, new ItemStack(Items.IRON_SWORD));
        ((VatSumpEntity) h.getBlockEntity(A.below(2)))
                .service()
                .tank(new FluidStack(ProductFluids.EXPERIENCE.get(), 1))
                .fill(new FluidStack(ProductFluids.EXPERIENCE.get(), 1000), FluidAction.EXECUTE);
        h.runAtTickTime(60, () -> {
            var c = vat(h);
            var state = c.processEngine().state(h.absolutePos(A));
            var kill = state.process(PassiveProcessCompiler.HARVEST);
            h.assertTrue(
                    kill.pending != null
                            && kill.data.getFloat("killHealth") == 0
                            && state.stage() == LifeStage.ADULT
                            && t.results().isEmpty(),
                    "Lethal result was lost/transitioned without XP capacity");
            var before =
                    kill.pending.items().stream().mapToInt(ItemStack::getCount).sum();
            cycles(c, 3);
            h.assertTrue(
                    kill.pending.items().stream().mapToInt(ItemStack::getCount).sum() == before
                            && t.heldItem().getDamageValue() == 2,
                    "Blocked lethal result rerolled or charged another durability event");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 70)
    public static void shearedRegrowthUsesFeedAndPreservesColor(GameTestHelper h) {
        suppliedRig(h, "sheep");
        var state = new CompoundTag();
        state.putByte("Color", (byte) DyeColor.BLUE.getId());
        state.putBoolean("Sheared", true);
        ((VatHeadEntity) h.getBlockEntity(A)).updateSpecimenState(state);
        h.runAtTickTime(35, () -> {
            var c = vat(h);
            c.processEngine().state(h.absolutePos(A)).process(PassiveUses.SHEAR).elapsed = 1200;
            cycles(c, 1);
            var after = ((VatHeadEntity) h.getBlockEntity(A)).specimenState();
            h.assertTrue(
                    !after.getBoolean("Sheared")
                            && after.getByte("Color") == DyeColor.BLUE.getId()
                            && c.metrics().total(MachineMetrics.WHEAT) == 1,
                    "Feed-driven vanilla regrowth lost state or did not consume wheat");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void fullMatchingClusterDoesNotHideEmptyCompatibleStorage(GameTestHelper h) {
        var p = O.offset(3, 1, 3);
        var a = buffer(h, p.west(), ConduitCladding.BARE, "minecraft:water");
        var b = buffer(h, p.east(), ConduitCladding.BARE, "minecraft:water");
        a.tank.fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
        var s = storage(h, List.of(a, b));
        var selected = s.selectOutput(h.absolutePos(p), new FluidStack(Fluids.WATER, 1000));
        h.assertTrue(
                selected != null
                        && selected.isEmpty()
                        && selected.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000
                        && a.tank.getFluidAmount() == 4000
                        && b.tank.getFluidAmount() == 1000,
                "Full preferred cluster hid available physical capacity");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void legacyFluidMigrationRetriesAfterPhysicalExport(GameTestHelper h) {
        var member = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "minecraft:milk");
        member.tank.fill(
                new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), 4000), FluidAction.EXECUTE);
        var s = storage(h, List.of(member));
        var legacy = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000);
        legacy.fill(new FluidStack(net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), 500), FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("milk", legacy.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
        var resources = new ProductBuffers(() -> {});
        resources.load(tag, h.getLevel().registryAccess());
        resources.bind(s);
        h.assertTrue(
                member.tank.getFluidAmount() == 4000
                        && resources.diagnostics().stream().anyMatch(d -> d.contains("Legacy migration pending")),
                "Full physical tank destroyed migration escrow");
        s.clusters().getFirst().tank().drain(500, FluidAction.EXECUTE);
        h.assertTrue(
                resources.migrate()
                        && member.tank.getFluidAmount() == 4000
                        && resources.diagnostics().stream().noneMatch(d -> d.contains("Legacy migration pending")),
                "Export did not permit exact migration retry");
        resources.bind(s);
        h.assertTrue(member.tank.getFluidAmount() == 4000, "Rebinding duplicated migrated legacy fluid");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void culturePaymentSpansPhysicalClustersAtomically(GameTestHelper h) {
        var a = buffer(h, O.offset(2, 1, 2), ConduitCladding.BARE, "mobvats:culture_slurry");
        var b = buffer(h, O.offset(4, 1, 2), ConduitCladding.BARE, "mobvats:culture_slurry");
        for (var member : List.of(a, b))
            member.tank.fill(new FluidStack(ProductFluids.CULTURE_SLURRY.get(), 5), FluidAction.EXECUTE);
        var resources = new ProductBuffers(() -> {});
        resources.bind(storage(h, List.of(a, b)));
        h.assertTrue(
                !resources.consumeCulture(11) && a.tank.getFluidAmount() == 5 && b.tank.getFluidAmount() == 5,
                "Insufficient fragmented Culture was partly spent");
        h.assertTrue(
                resources.consumeCulture(10) && a.tank.isEmpty() && b.tank.isEmpty(),
                "Culture payment lost a partial first cluster");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void unformedMixerHasNoInvisibleFluidCapacity(GameTestHelper h) {
        var mixer = new net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer(h.getLevel());
        h.assertTrue(
                mixer.state().fluids(true).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0
                        && !mixer.state().depositExperienceMb(250),
                "Unformed Mixer retained active hidden water/XP storage");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void mixerPortFilterPersistsAndUsesTags(GameTestHelper h) {
        var p = O.offset(2, 1, 2);
        h.setBlock(
                p,
                net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerContent.state(
                        net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerPartType.FluidPort));
        var port = (net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerPortEntity) h.getBlockEntity(p);
        port.filter("#minecraft:water");
        var tag = port.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), SyncReason.FullSync);
        port.filter("");
        port.syncDataFrom(tag, h.getLevel().registryAccess(), SyncReason.FullSync);
        h.assertTrue(
                port.filter().equals("#minecraft:water")
                        && port.accepts(new FluidStack(Fluids.WATER, 1))
                        && !port.accepts(new FluidStack(ProductFluids.EXPERIENCE.get(), 1)),
                "Mixer fluid selector/persistence incorrect");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void largeLegacyMixerXpMigratesWithoutOverflowOrLoss(GameTestHelper h) {
        var p = O.offset(3, 1, 3);
        var member = buffer(h, p.east(), ConduitCladding.BARE, "mobvats:experience");
        var physical = storage(h, List.of(member));
        var state = new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerState(() -> {});
        state.bind(physical, h.absolutePos(p));
        var tag = new CompoundTag();
        tag.putLong("xp", Long.MAX_VALUE);
        state.load(tag, h.getLevel().registryAccess());
        state.bind(physical, h.absolutePos(p));
        int ratio = ExperienceFluid.ratio();
        long moved = 4000 / ratio;
        h.assertTrue(
                member.tank.getFluidAmount() == moved * ratio
                        && state.save(h.getLevel().registryAccess()).getLong("legacyXpMigration")
                                == Long.MAX_VALUE - moved,
                "Large XP migration overflowed/lost its remainder");
        var restored = new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerState(() -> {});
        restored.bind(physical, h.absolutePos(p));
        restored.load(state.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        member.tank.drain(4000, FluidAction.EXECUTE);
        restored.cadence();
        h.assertTrue(
                member.tank.getFluidAmount() == moved * ratio
                        && restored.save(h.getLevel().registryAccess()).getLong("legacyXpMigration")
                                == Long.MAX_VALUE - 2 * moved,
                "Reload/export migration lost or duplicated XP");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "slice11", timeoutTicks = 10)
    public static void oversizedMixerXpDepositIsRejectedWithoutMutation(GameTestHelper h) {
        var state = new net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerState(() -> {});
        h.assertTrue(
                !state.depositExperienceMb(Long.MAX_VALUE) && state.experienceMb() == 0,
                "Oversized player deposit overflowed or mutated storage");
        h.succeed();
    }
}
