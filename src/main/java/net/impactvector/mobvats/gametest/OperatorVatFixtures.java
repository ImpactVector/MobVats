package net.impactvector.mobvats.gametest;

import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Separate operator rigs. Only vanilla supply containers are preloaded; the engine does all transfers. */
final class OperatorVatFixtures {
    record Fixture(
            String id,
            String operation,
            String entity,
            String use,
            String process,
            String output,
            BlockPos origin,
            Item input,
            Item food) {}

    private static final List<Fixture> ROW = new ArrayList<>();
    private static final Set<String> FED = new HashSet<>(),
            LOADED = new HashSet<>(),
            ACQUIRED = new HashSet<>(),
            PROGRESSED = new HashSet<>();

    static int count() {
        return ROW.size();
    }

    static final int SETTLE_TICKS = 5000;
    private static final int ROW_Z = -24;
    private static final Map<BlockPos, Boolean> PAUSED = new LinkedHashMap<>();
    /** Suspend production through the ordinary operator setting during P-06 observation. */
    static void pause(ServerLevel level) {
        for (var fixture : ROW) {
            var pos = fixture.origin().offset(1, 1, 0);
            var controller = ((VatControllerEntity) level.getBlockEntity(pos))
                    .getMultiblockController()
                    .orElseThrow();
            if (!controller.isAssembled()) throw new IllegalStateException("Operator Vat unformed at pause: " + pos);
            PAUSED.putIfAbsent(pos, controller.isMachineActive());
            controller.setMachineActive(false);
        }
    }

    static void resume(ServerLevel level) {
        for (var entry : PAUSED.entrySet()) {
            var controller = ((VatControllerEntity) level.getBlockEntity(entry.getKey()))
                    .getMultiblockController()
                    .orElseThrow();
            if (!controller.isAssembled())
                throw new IllegalStateException("Operator Vat unformed at resume: " + entry.getKey());
            controller.setMachineActive(entry.getValue());
        }
        PAUSED.clear();
    }

    /** Only a broken brush with a real staged replacement can defer the final snapshot. */
    static boolean brushReplacementPending(ServerLevel level) {
        for (var fixture : ROW) {
            if (fixture.input() != Items.BRUSH) continue;
            var origin = fixture.origin();
            if (brushReplacementPending(
                    (VatToolPortEntity) level.getBlockEntity(origin.offset(2, 1, 4)),
                    (VatItemPortEntity) level.getBlockEntity(origin.offset(1, 1, 4)))) return true;
        }
        return false;
    }

    static boolean brushReplacementPending(VatToolPortEntity tool, VatItemPortEntity input) {
        return tool.heldItem().isEmpty() && input.staging().getStackInSlot(0).is(Items.BRUSH);
    }

    static boolean suppliedInput(VatToolPortEntity tool, VatItemPortEntity input, Item expected) {
        return tool.heldItem().is(expected)
                || (expected == Items.BUCKET
                        && input.staging().getStackInSlot(0).is(expected));
    }

    static void build(ServerLevel level) throws java.io.IOException {
        ROW.clear();
        PAUSED.clear();
        FED.clear();
        LOADED.clear();
        ACQUIRED.clear();
        PROGRESSED.clear();
        var defaults = PassiveDefaults.data();
        var definitions = new TreeMap<String, PassiveMobDefinition>();
        for (var entity : List.of("cow", "sheep", "chicken", "goat", "sniffer", "armadillo"))
            definitions.put("minecraft:" + entity, defaults.get("minecraft:" + entity));
        publish(definitions);
        Files.writeString(
                Path.of(System.getProperty("mobvats.testWorldPath"), "operator-definitions.json"),
                PassiveMobDefinitionRegistry.JSON.toJson(definitions) + "\n");
        String[] operations = {
            "Shears",
            "Milk/container",
            "Kill/harvest",
            "Goat horn",
            "Sniffer dig",
            "Chicken eggs",
            "Blender",
            "Feed",
            "Armadillo brush"
        };
        String[] entities = {"sheep", "cow", "cow", "goat", "sniffer", "chicken", "cow", "sheep", "armadillo"};
        String[] uses = {
            PassiveUses.SHEAR,
            PassiveUses.SECONDARY,
            PassiveUses.HARVEST,
            "mobvats:goat_horn",
            "mobvats:sniffer_dig",
            PassiveUses.DRAIN,
            PassiveUses.BLEND,
            PassiveUses.FEED,
            "mobvats:armadillo_brush"
        };
        String[] processes = {
            PassiveUses.SHEAR,
            PassiveProcessCompiler.MILK,
            PassiveProcessCompiler.HARVEST,
            "mobvats:goat_horn",
            "mobvats:sniffer_dig",
            "mobvats:egg",
            PassiveProcessCompiler.HARVEST,
            PassiveUses.SHEAR,
            "mobvats:armadillo_brush"
        };
        String[] outputs = {
            "Wool",
            "Milk buckets",
            "Beef / leather / XP",
            "Goat horn",
            "Ancient plant seeds",
            "Eggs -> chest",
            "Meat -> wall buffer",
            "Wool regrowth",
            "Scutes -> chest"
        };
        Item[] inputs = {
            Items.SHEARS,
            Items.BUCKET,
            Items.DIAMOND_SWORD,
            Items.STONE,
            Items.MOSS_BLOCK,
            null,
            null,
            null,
            Items.BRUSH
        };
        Item[] foods = {
            Items.WHEAT,
            Items.WHEAT,
            Items.WHEAT,
            Items.WHEAT,
            Items.TORCHFLOWER_SEEDS,
            Items.WHEAT_SEEDS,
            Items.WHEAT,
            Items.WHEAT,
            Items.SPIDER_EYE
        };
        ManualTestWorld.sign(
                level,
                new BlockPos(-3, -60, ROW_Z - 5),
                "OPERATOR ROW",
                "9 supplied Vats",
                "Feed: front",
                "Tools: rear");
        for (int x = -4; x <= 148; x++)
            level.setBlockAndUpdate(new BlockPos(x, -61, ROW_Z - 7), Blocks.LIGHT_BLUE_WOOL.defaultBlockState());
        for (int i = 0; i < operations.length; i++) {
            var o = new BlockPos(8 + i * 16, -60, ROW_Z);
            var f = new Fixture(
                    "O-0" + (i + 1),
                    operations[i],
                    entities[i],
                    uses[i],
                    processes[i],
                    outputs[i],
                    o,
                    inputs[i],
                    foods[i]);
            ROW.add(f);
            MachineFixtures.vat(level::setBlockAndUpdate, o, 5, 5, 4);
            storage(level, o);
            var head = (VatHeadEntity) level.getBlockEntity(o.offset(2, 2, 2));
            head.encode(ResourceLocation.withDefaultNamespace(f.entity()));
            MachineFixtures.specimen(level::setBlockAndUpdate, head.getBlockPos(), head.geometry());
            if (i == 7) {
                var state = head.specimenState();
                state.putBoolean("Sheared", true);
                head.updateSpecimenState(state);
            }
            part(level, o.offset(2, 1, 0), VatPartType.ItemPort);
            part(level, o.offset(2, 1, 1), VatPartType.FeedPort);
            supply(level, o.offset(2, 1, 0), Direction.SOUTH, f.food(), 64, "Feed input");
            if (f.input() != null) {
                part(level, o.offset(2, 1, 4), VatPartType.ToolPort);
                part(level, o.offset(1, 1, 4), VatPartType.ItemPort);
                int quantity = f.input() == Items.BRUSH ? 54 : f.input().getDefaultMaxStackSize() == 1 ? 6 : 64;
                supply(level, o.offset(1, 1, 4), Direction.NORTH, f.input(), quantity, "Tool input");
            }
            if (i == 6) part(level, o.offset(4, 1, 2), VatPartType.Blender);
            for (var p : List.of(o.offset(2, 0, 3), o.offset(2, 0, 4)))
                level.setBlockAndUpdate(
                        p,
                        ConduitContent.state(ConduitResource.ITEM, ConduitCladding.CASING)
                                .setValue(ConduitBlock.MODE, ConduitMode.TAP));
            output(level, o.offset(3, 0, 3), Direction.EAST, "Item / Sump out");
            ManualTestWorld.sign(
                    level,
                    o.offset(0, 0, -5),
                    f.id() + " " + f.operation(),
                    "Specimen: " + f.entity(),
                    "Use: " + f.operation(),
                    f.output());
            ManualTestWorld.sign(
                    level,
                    o.offset(5, 0, -3),
                    "Upper wall buffers",
                    "Front: milk / meat",
                    "Rear: waste / XP",
                    "Empty with bucket");
        }
    }

    private static void publish(Map<String, PassiveMobDefinition> definitions) {
        var all = new HashMap<>(PassiveMobDefinitionRegistry.definitions());
        definitions.values().forEach(d -> all.put(d.identity(), d));
        var sources = new HashMap<ResourceLocation, String>();
        all.keySet().forEach(id -> sources.put(id, PassiveMobDefinitionRegistry.source(id)));
        definitions.values().forEach(d -> sources.put(d.identity(), "manual world operator-definitions.json"));
        PassiveMobDefinitionRegistry.publish(new PassiveMobDefinitionRegistry.Loaded(all, sources, List.of()));
    }
    /** Development-only generated saves restore their verified species snapshot when opened. */
    static void restoreDefinitions(Path world) throws java.io.IOException {
        if (!Files.isRegularFile(world.resolve(".mobvats-generated-world"))
                || !Files.isRegularFile(world.resolve("operator-definitions.json"))) return;
        var json = com.google.gson.JsonParser.parseString(Files.readString(world.resolve("operator-definitions.json")))
                .getAsJsonObject();
        var definitions = new TreeMap<String, PassiveMobDefinition>();
        json.entrySet()
                .forEach(e -> definitions.put(
                        e.getKey(),
                        PassiveMobDefinitionRegistry.JSON.fromJson(e.getValue(), PassiveMobDefinition.class)));
        publish(definitions);
    }

    private static void part(ServerLevel level, BlockPos pos, VatPartType type) {
        level.setBlockAndUpdate(pos, Content.state(type));
    }

    private static void supply(
            ServerLevel level, BlockPos port, Direction toward, Item item, int quantity, String label) {
        var hopper = port.relative(toward.getOpposite());
        var chestPos = hopper.above();
        level.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, toward));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (quantity > 27 * item.getDefaultMaxStackSize()) {
            level.setBlockAndUpdate(
                    chestPos.west(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT));
            level.setBlockAndUpdate(
                    chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT));
        }
        var chest = ChestBlock.getContainer(
                (ChestBlock) Blocks.CHEST, level.getBlockState(chestPos), level, chestPos, true);
        for (int slot = 0; quantity > 0; slot++) {
            int n = Math.min(quantity, item.getDefaultMaxStackSize());
            chest.setItem(slot, new ItemStack(item, n));
            quantity -= n;
        }
        chest.setChanged();
        ManualTestWorld.sign(
                level,
                hopper.relative(toward.getOpposite()),
                label,
                "Chest -> hopper",
                "-> Input Port",
                item.toString());
    }

    private static void output(ServerLevel level, BlockPos port, Direction outward, String label) {
        part(level, port, VatPartType.ItemPort);
        ((VatItemPortEntity) level.getBlockEntity(port))
                .setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        var hopper = port.below();
        var edge = hopper.relative(outward);
        var chest = edge.relative(outward);
        level.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, outward));
        level.setBlockAndUpdate(edge, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, outward));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ManualTestWorld.sign(
                level, chest.relative(outward), label, "Output Port", "-> hopper -> chest", "Keep space free");
    }

    private static void storage(ServerLevel level, BlockPos o) {
        var tap = ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING)
                .setValue(ConduitBlock.MODE, ConduitMode.TAP);
        // One readable roof ring and three shell risers; no interior buffer wall.
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                if (x == 0 || x == 4 || z == 0 || z == 4) level.setBlockAndUpdate(o.offset(x, 3, z), tap);
        for (var p : List.of(
                o.offset(1, 0, 2),
                o.offset(0, 0, 2),
                o.offset(0, 1, 2),
                o.offset(0, 2, 2),
                o.offset(2, 2, 4),
                o.offset(4, 2, 2))) level.setBlockAndUpdate(p, tap);
        var positions = List.of(o.offset(1, 2, 0), o.offset(3, 2, 0), o.offset(1, 2, 4), o.offset(3, 2, 4));
        var fluids = List.of("minecraft:milk", "mobvats:meat", "mobvats:waste", "mobvats:experience");
        for (int i = 0; i < positions.size(); i++) {
            level.setBlockAndUpdate(positions.get(i), FluidBufferContent.state(ConduitCladding.CASING));
            ((FluidBufferEntity) level.getBlockEntity(positions.get(i))).filter(fluids.get(i));
        }
    }
    /** Reestablish the sheared specimen precondition after observing its first natural regrowth. */
    static void prepareFeedDemo(ServerLevel level) {
        var f = ROW.stream()
                .filter(r -> r.operation().equals("Feed"))
                .findFirst()
                .orElseThrow();
        var head = (VatHeadEntity) level.getBlockEntity(f.origin().offset(2, 2, 2));
        var state = head.specimenState();
        state.putBoolean("Sheared", true);
        head.updateSpecimenState(state);
    }

    static void verifyFormation(ServerLevel level) {
        for (var f : ROW) {
            var head = (VatHeadEntity) level.getBlockEntity(f.origin().offset(2, 2, 2));
            require(
                    head.isMachineAssembled(),
                    f,
                    "Vat did not form: "
                            + head.getMultiblockController()
                                    .flatMap(v -> v.getLastError())
                                    .map(e -> e.getChatMessage().getString())
                                    .orElse("no controller"));
            require(
                    head.getMultiblockController().orElseThrow().getTopology().operational(head.getBlockPos()),
                    f,
                    "specimen inactive");
        }
    }
    /** Observe actual engine/hopper ticks, without acquiring tools or advancing processes here. */
    static void observe(ServerLevel level) {
        for (var f : ROW) {
            var o = f.origin();
            var head = (VatHeadEntity) level.getBlockEntity(o.offset(2, 2, 2));
            var vat = head.getMultiblockController().orElse(null);
            if (vat == null || !vat.isAssembled()) continue;
            var feed = (VatItemPortEntity) level.getBlockEntity(o.offset(2, 1, 0));
            if (feed.staging().getStackInSlot(0).is(f.food())) FED.add(f.id());
            if (f.input() != null) {
                var input = (VatItemPortEntity) level.getBlockEntity(o.offset(1, 1, 4));
                var tool = (VatToolPortEntity) level.getBlockEntity(o.offset(2, 1, 4));
                if (input.staging().getStackInSlot(0).is(f.input())) LOADED.add(f.id());
                if (tool.heldItem().is(f.input())) ACQUIRED.add(f.id());
                if (tool.interactionStart() != Long.MIN_VALUE) {
                    ACQUIRED.add(f.id());
                    PROGRESSED.add(f.id());
                }
            } else {
                var state = vat.processEngine().state(head.getBlockPos());
                if (state != null
                        && (state.process(f.process()).elapsed > 0
                                || vat.metrics().total(MachineMetrics.BLENDER_HARVESTS) > 0)) PROGRESSED.add(f.id());
            }
        }
    }

    static void verify(ServerLevel level, StringBuilder manifest) {
        manifest.append(
                "\n## Operator fixtures\n\nID | Interaction | Specimen | Origin | Input | Expected output | Verified progress\n---|---|---|---|---|---|---\n");
        for (var f : ROW) {
            var o = f.origin();
            var head = (VatHeadEntity) level.getBlockEntity(o.offset(2, 2, 2));
            require(
                    head.isMachineAssembled(),
                    f,
                    "Vat did not form: "
                            + head.getMultiblockController()
                                    .flatMap(v -> v.getLastError())
                                    .map(e -> e.getChatMessage().getString())
                                    .orElse("no controller"));
            var vat = head.getMultiblockController().orElseThrow();
            vat.rebuildTopology();
            require(vat.isMachineActive(), f, "operator Vat remained disabled");
            require(vat.getTopology().operational(head.getBlockPos()), f, "specimen inactive");
            var context = new ProcessContext(
                    vat.processEngine(),
                    vat.getAssemblies().getFirst(),
                    vat.processEngine().state(head.getBlockPos()),
                    MobProcessRegistry.DEFAULT.resolve(head.getIdentity()));
            require(
                    context.profile().definitions().stream()
                            .anyMatch(d -> d.stateId().equals(f.process())),
                    f,
                    "process undiscoverable");
            require(FED.contains(f.id()), f, "feed never entered Input Port through hopper");
            require(
                    context.itemInputs().stream()
                            .anyMatch(p -> p.staging().getStackInSlot(0).is(f.food())),
                    f,
                    "Feed Port has no routed supply");
            require(
                    PROGRESSED.contains(f.id()),
                    f,
                    "no actual interaction or observable process progress: "
                            + vat.processEngine().status(head.getBlockPos()) + "; timer="
                            + context.state().process(f.process()).elapsed + "; buffers="
                            + vat.storage().clusters().stream()
                                    .map(c -> c.tank().getFluid().toString())
                                    .toList());
            require(
                    !context.routes(f.use()).isEmpty()
                            || (f.input() != null
                                    && context.loading().stream()
                                            .anyMatch(r -> r.actor().equals(o.offset(2, 1, 4)))),
                    f,
                    "intended actor unassigned");
            if (f.input() != null) {
                require(LOADED.contains(f.id()), f, "required input never entered Input Port through hopper");
                var tool = (VatToolPortEntity) level.getBlockEntity(o.offset(2, 1, 4));
                require(ACQUIRED.contains(f.id()), f, "Tool Port never acquired supplied input");
                var input = (VatItemPortEntity) level.getBlockEntity(o.offset(1, 1, 4));
                var staged = input.staging().getStackInSlot(0);
                require(
                        suppliedInput(tool, input, f.input()),
                        f,
                        "Tool Port has no supplied tool/catalyst or next container: held=" + tool.heldItem()
                                + "; staged=" + staged + "; results="
                                + tool.results().snapshot()
                                + "; time=" + level.getGameTime());
                require(
                        ToolInteractions.uses(level, head.getIdentity(), new ItemStack(f.input()))
                                .contains(f.use()),
                        f,
                        "intended tool use undiscoverable");
                require(
                        context.loading().stream()
                                        .filter(r -> r.actor().equals(tool.getBlockPos()))
                                        .anyMatch(r -> !context.outputs(r).isEmpty())
                                || context.routes(f.use()).stream()
                                        .anyMatch(r -> !context.outputs(r).isEmpty()),
                        f,
                        "Tool Port has no routed output destination");
            }
            capacity(level, o.offset(3, 0, 3), Direction.EAST, f);
            for (var cluster : vat.storage().clusters())
                require(cluster.tank().getFluidAmount() < cluster.tank().getCapacity(), f, "fluid output buffer full");
            if (f.entity().equals("chicken")) {
                var eggs = PassiveMobDefinitionRegistry.resolve(head.getIdentity()).processes().stream()
                        .filter(p -> p.id().equals("mobvats:egg"))
                        .findFirst()
                        .orElseThrow();
                require(
                        new ResourceProcessRuntime(eggs).plan(context).problem() == null,
                        f,
                        "eggs have no output route");
            }
            var chest = (ChestBlockEntity) level.getBlockEntity(o.offset(5, -1, 3));
            int delivered = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++)
                delivered += chest.getItem(slot).getCount();
            if (f.input() == Items.SHEARS
                    || f.input() == Items.DIAMOND_SWORD
                    || f.input() == Items.BRUSH
                    || f.input() == Items.BUCKET) require(delivered > 0, f, "no operation result reached output chest");
            if (f.use().equals(PassiveUses.BLEND))
                require(vat.metrics().total(MachineMetrics.BLENDER_HARVESTS) > 0, f, "Blender never committed");
            manifest.append(f.id() + " | " + f.operation() + " | " + f.entity() + " | " + o.toShortString() + " | "
                    + (f.input() == null ? f.food() : f.input()) + " | " + f.output() + " | " + delivered
                    + " chest items; " + context.state().process(f.process()).elapsed + " process ticks; stage "
                    + context.state().stage() + "\n");
        }
    }

    private static void capacity(ServerLevel level, BlockPos port, Direction outward, Fixture f) {
        require(level.getBlockEntity(port.below()) instanceof HopperBlockEntity, f, "output hopper missing");
        require(
                level.getBlockEntity(port.below().relative(outward)) instanceof HopperBlockEntity,
                f,
                "output edge hopper missing");
        var chestPos = port.below().relative(outward, 2);
        require(level.getBlockState(chestPos.above()).isAir(), f, "output chest lid obstructed");
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        require(chest != null, f, "output chest missing");
        boolean free = false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
            if (chest.getItem(slot).isEmpty()) free = true;
        require(free, f, "output chest full");
    }

    private static void require(boolean value, Fixture f, String message) {
        if (!value) throw new IllegalStateException(f.id() + " " + f.operation() + ": " + message);
    }
}
