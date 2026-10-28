package net.impactvector.mobvats.survival;

import com.buuz135.functionalstorage.block.tile.*;
import com.buuz135.functionalstorage.item.LinkingToolItem.ActionMode;
import com.mojang.logging.LogUtils;
import com.tom.storagemod.block.AbstractStorageTerminalBlock;
import com.tom.storagemod.block.entity.CraftingTerminalBlockEntity;
import com.tom.storagemod.inventory.StoredItemStack;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Present only in the Survival source set. Never participates in ordinary worlds or published jars. */
@net.neoforged.fml.common.Mod("mobvats_survival")
@EventBusSubscriber(modid = "mobvats_survival")
public final class SurvivalWorld {
    static final int Y = -60;
    private static final int HEIGHT = net.impactvector.mobvats.gamecontent.multiblock.vat.VatTier.T3.maxY + 2;
    // The air gap behind the controller prevents Tom's scan counting both aggregate and member inventories.
    private static final BlockPos CONTROLLER = new BlockPos(21, Y, 3);
    private static final BlockPos TERMINAL = new BlockPos(21, Y, 5);
    private static final BlockPos SPAWN = new BlockPos(5, Y, 7);
    private static final Map<BlockPos, Item> DRAWERS = new LinkedHashMap<>();
    private static final Map<BlockPos, net.minecraft.world.level.material.Fluid> TANKS = new LinkedHashMap<>();
    private static int ticks;
    private static boolean active;
    private static boolean passed;
    private static String manifest;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("mobvats.survival.generate") && !Boolean.getBoolean("mobvats.survival.verify")) return;
        ticks = 0;
        passed = false;
        active = true;
        DRAWERS.clear();
        TANKS.clear();
        try {
            var level = event.getServer().overworld();
            require(
                    net.neoforged.fml.ModList.get().isLoaded("jade"),
                    "Jade inspection overlay is missing from the Survival runtime");
            for (var id : List.of(
                    "capture_fixture",
                    "discovery_passive",
                    "discovery_nether",
                    "discovery_end",
                    "discovery_ambiguous",
                    "discovery_boss"))
                require(
                        !BuiltInRegistries.ENTITY_TYPE.containsKey(
                                ResourceLocation.fromNamespaceAndPath("mobvats", id)),
                        "Headless test entity registered in Survival runtime: " + id);
            require(
                    level.getServer()
                            .getResourceManager()
                            .listResources(
                                    "neoforge/biome_modifier",
                                    id -> id.getNamespace().equals("mobvats_lifecycle"))
                            .isEmpty(),
                    "Headless biome spawn fixtures present in Survival runtime");
            for (int x = 0; x <= 2; x++) for (int z = 0; z <= 2; z++) level.setChunkForced(x, z, true);
            if (Boolean.getBoolean("mobvats.survival.verify")) {
                var world = Path.of(System.getProperty("mobvats.survival.world"));
                var rows = Files.readAllLines(world.resolve("survival-stock.csv"));
                for (String row : rows.subList(1, rows.size())) {
                    var cells = row.split(",");
                    DRAWERS.put(
                            new BlockPos(
                                    Integer.parseInt(cells[2]), Integer.parseInt(cells[3]), Integer.parseInt(cells[4])),
                            BuiltInRegistries.ITEM.get(ResourceLocation.parse(cells[0])));
                }
                var fluidRows = Files.readAllLines(world.resolve("survival-fluids.csv"));
                for (String row : fluidRows.subList(1, fluidRows.size())) {
                    var cells = row.split(",");
                    TANKS.put(
                            new BlockPos(
                                    Integer.parseInt(cells[1]), Integer.parseInt(cells[2]), Integer.parseInt(cells[3])),
                            BuiltInRegistries.FLUID.get(ResourceLocation.parse(cells[0])));
                }
                return;
            }
            for (int x = 0; x <= 42; x++) {
                for (int z = 0; z <= 42; z++) {
                    for (int y = Y - 1; y <= Y + HEIGHT; y++) {
                        boolean shell = x == 0 || x == 42 || z == 0 || z == 42 || y == Y - 1 || y == Y + HEIGHT;
                        BlockState state = (shell ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState();
                        // Flush floor lights at six-block intervals; roof lights also protect the roof.
                        if ((y == Y - 1 || y == Y + HEIGHT) && x % 6 == 3 && z % 6 == 3)
                            state = Blocks.SEA_LANTERN.defaultBlockState();
                        level.setBlockAndUpdate(new BlockPos(x, y, z), state);
                    }
                    level.setBlockAndUpdate(
                            new BlockPos(x, Y + HEIGHT + 1, z), Blocks.STONE_BRICK_SLAB.defaultBlockState());
                }
            }
            level.setDefaultSpawnPos(SPAWN, 180);
            level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
            level.setDayTime(1000);
            tools(level);
            storage(level);
            fluids(level);
            LogUtils.getLogger().info("SURVIVAL workshop constructed; waiting for storage network discovery");
        } catch (Exception error) {
            fail(event.getServer(), error);
        }
    }

    private static void storage(ServerLevel level) {
        var stock = SurvivalStock.discover(level);
        if (stock.size() > 150) throw new IllegalStateException("Stock exceeds 150 drawer positions: " + stock.size());
        put(level, CONTROLLER, "functionalstorage:storage_controller");
        var controller = (StorageControllerTile<?>) level.getBlockEntity(CONTROLLER);
        Objects.requireNonNull(controller);
        controller.getStorageUpgrades().setStackInSlot(0, stack("functionalstorage:copper_upgrade", 1));
        controller.recalculateUpgrades();
        int index = 0;
        var listing = new StringBuilder("id,count,x,y,z\n");
        for (var entry : stock.entrySet()) {
            var pos = new BlockPos(9 + index % 25, Y + index / 25, 1);
            put(level, pos, "functionalstorage:oak_1");
            var drawer = (DrawerTile) Objects.requireNonNull(level.getBlockEntity(pos));
            drawer.getStorageUpgrades().setStackInSlot(0, stack("functionalstorage:diamond_upgrade", 1));
            drawer.recalculateUpgrades();
            int count = SurvivalStock.count(entry.getValue());
            for (int remaining = count; remaining > 0; ) {
                int batch = Math.min(
                        remaining, entry.getValue().getDefaultInstance().getMaxStackSize());
                var leftover = drawer.getStorage().insertItem(0, new ItemStack(entry.getValue(), batch), false);
                if (!leftover.isEmpty()) throw new IllegalStateException("Drawer rejected stock: " + entry.getKey());
                remaining -= batch;
            }
            drawer.setLocked(true);
            drawer.setChanged();
            if (!controller.addConnectedDrawers(ActionMode.ADD, pos))
                throw new IllegalStateException("Controller refused drawer: " + pos);
            DRAWERS.put(pos, entry.getValue());
            listing.append(entry.getKey())
                    .append(',')
                    .append(count)
                    .append(',')
                    .append(pos.getX())
                    .append(',')
                    .append(pos.getY())
                    .append(',')
                    .append(pos.getZ())
                    .append('\n');
            index++;
        }
        // An empty compacting drawer is available for manual compaction tests.
        var compact = new BlockPos(8, Y, 1);
        put(level, compact, "functionalstorage:compacting_drawer");
        if (!controller.addConnectedDrawers(ActionMode.ADD, compact))
            throw new IllegalStateException("Compacting link failed");
        controller.getConnectedDrawers().rebuild();
        controller.setChanged();
        put(level, CONTROLLER.south(), "toms_storage:inventory_connector");
        var terminalState = block("toms_storage:crafting_terminal")
                .defaultBlockState()
                .setValue(AbstractStorageTerminalBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(TERMINAL, terminalState);
        sign(
                level,
                TERMINAL.offset(2, 0, 0),
                "Crafting terminal",
                "Uses drawer stock",
                "North wall",
                "4096 / resource");
        manifest = listing.toString();
    }

    private static void tools(ServerLevel level) {
        var pos = new BlockPos(4, Y, 5);
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        var chest = (ChestBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
        int slot = 0;
        for (Item tool : List.of(
                Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.DIAMOND_HOE))
            for (int i = 0; i < 3; i++) chest.setItem(slot++, new ItemStack(tool));
        chest.setItem(slot++, new ItemStack(Items.COOKED_BEEF, 64));
        chest.setItem(slot++, new ItemStack(Items.TORCH, 64));
        chest.setItem(slot++, new ItemStack(Items.LADDER, 64));
        chest.setItem(slot++, new ItemStack(Items.BUCKET, 16));
        chest.setItem(slot++, stack("functionalstorage:linking_tool", 1));
        chest.setItem(slot, stack("functionalstorage:configuration_tool", 1));
        chest.setChanged();
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(new BlockPos(7, Y, 5), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(new BlockPos(7, Y, 4), bed.setValue(BedBlock.PART, BedPart.HEAD));
        level.setBlockAndUpdate(new BlockPos(2, Y, 5), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(2, Y, 6), Blocks.FURNACE.defaultBlockState());
        sign(level, new BlockPos(4, Y, 6), "Survival workshop", "Tools in chest", "Bed to the right", "Storage: north");
    }

    private static void fluids(ServerLevel level) {
        var fluids = BuiltInRegistries.FLUID.entrySet().stream()
                .filter(e -> e.getKey().location().getNamespace().equals("mobvats"))
                .filter(e -> e.getValue().isSource(e.getValue().defaultFluidState()))
                .sorted(Comparator.comparing(e -> e.getKey().location().toString()))
                .toList();
        if (fluids.size() > 16) throw new IllegalStateException("Too many fluids for supply wall");
        int index = 0;
        for (var entry : fluids) {
            // Four buffers per fluid; no assumptions about buckets or placeable fluid blocks.
            for (int i = 0; i < 4; i++) {
                var pos = new BlockPos(2 + index * 2, Y + i, 40);
                put(level, pos, "mobvats:fluid_buffer");
                var buffer = (FluidBufferEntity) Objects.requireNonNull(level.getBlockEntity(pos));
                if (buffer.tank.fill(new FluidStack(entry.getValue(), 4000), IFluidHandler.FluidAction.EXECUTE) != 4000)
                    throw new IllegalStateException("Fluid buffer rejected " + entry.getKey());
                buffer.setChanged();
                TANKS.put(pos, entry.getValue());
            }
            sign(
                    level,
                    new BlockPos(2 + index * 2, Y, 39),
                    entry.getKey().location().getPath().replace('_', ' '),
                    "16 buckets",
                    "Finite supply",
                    "Attach fluid port");
            index++;
        }
        // Renewable ordinary water source beside the supply wall.
        for (int x = 37; x <= 38; x++)
            for (int z = 39; z <= 40; z++)
                level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.WATER.defaultBlockState());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!active) return;
        ++ticks;
        // Opening the terminal requests its normal item cache before the next server ticks.
        if (ticks == 100
                && event.getServer().overworld().getBlockEntity(TERMINAL)
                        instanceof CraftingTerminalBlockEntity terminal) terminal.getStacks();
        if (ticks < 200) return;
        active = false;
        try {
            verify(event.getServer().overworld());
            passed = true;
            for (int x = 0; x <= 2; x++)
                for (int z = 0; z <= 2; z++) event.getServer().overworld().setChunkForced(x, z, false);
            event.getServer().halt(false);
        } catch (Exception error) {
            fail(event.getServer(), error);
        }
    }

    private static void verify(ServerLevel level) {
        // Exercise vanilla first-login spawn selection, including the solid roof above world spawn.
        var newcomer = new net.minecraft.server.level.ServerPlayer(
                level.getServer(),
                level,
                new com.mojang.authlib.GameProfile(
                        java.util.UUID.nameUUIDFromBytes(
                                "mobvats-survival-spawn".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "SurvivalProbe"),
                net.minecraft.server.level.ClientInformation.createDefault());
        require(
                newcomer.blockPosition().equals(SPAWN),
                "Vanilla first-login spawn is outside workshop: " + newcomer.blockPosition());
        var terminal = (CraftingTerminalBlockEntity) Objects.requireNonNull(level.getBlockEntity(TERMINAL));
        terminal.getStacks();
        terminal.updateServer();
        var controller = (StorageControllerTile<?>) Objects.requireNonNull(level.getBlockEntity(CONTROLLER));
        require(controller.getFacingDirection() == Direction.SOUTH, "Storage controller faces away from the workshop");
        require(
                ((ControllableDrawerTile<?>) Objects.requireNonNull(level.getBlockEntity(new BlockPos(8, Y, 1))))
                                .getFacingDirection()
                        == Direction.SOUTH,
                "Compacting drawer faces away from the workshop");
        var connector = (com.tom.storagemod.block.entity.InventoryConnectorBlockEntity)
                Objects.requireNonNull(level.getBlockEntity(CONTROLLER.south()));
        LogUtils.getLogger()
                .info(
                        "SURVIVAL storage network: controller slots={}, connector inventories={}, terminal entries={}, terminal state={}",
                        controller.getStorage().getSlots(),
                        connector.getUsage(),
                        terminal.getStacks().size(),
                        terminal.getBlockState());
        for (var entry : DRAWERS.entrySet()) {
            var drawer = (DrawerTile) Objects.requireNonNull(level.getBlockEntity(entry.getKey()));
            require(
                    drawer.getFacingDirection() == Direction.SOUTH,
                    "Drawer faces away from the workshop: " + entry.getKey());
            var contents = drawer.getStorage().getStackInSlot(0);
            require(
                    contents.is(entry.getValue()) && contents.getCount() == SurvivalStock.count(entry.getValue()),
                    "Drawer contents: " + entry);
            require(
                    amount(terminal, entry.getValue()) == SurvivalStock.count(entry.getValue()),
                    "Terminal cannot see drawer: " + entry + "; amount=" + amount(terminal, entry.getValue()));
        }
        // Real terminal result-slot interaction, with an empty Survival FakePlayer inventory.
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().clearContent();
        var menu = new com.tom.storagemod.menu.CraftingTerminalMenu(1, player.getInventory(), terminal);
        long copper = amount(terminal, Items.COPPER_INGOT);
        long glass = amount(terminal, Items.GLASS);
        terminal.setCraftSlot(
                0,
                0,
                Objects.requireNonNull(terminal.pullStack(new StoredItemStack(new ItemStack(Items.COPPER_INGOT)), 1))
                        .getActualStack());
        terminal.setCraftSlot(
                1,
                0,
                Objects.requireNonNull(terminal.pullStack(new StoredItemStack(new ItemStack(Items.GLASS)), 1))
                        .getActualStack());
        menu.onCraftMatrixChanged();
        var resultSlot = menu.getCraftingResultSlot();
        var output = resultSlot.remove(4);
        require(
                output.is(stack("mobvats:vat_casing", 1).getItem()) && output.getCount() == 4,
                "Terminal crafting output incorrect");
        resultSlot.onTake(player, output);
        terminal.clear(player);
        terminal.updateServer();
        require(
                drawerAmount(level, Items.COPPER_INGOT) == copper - 1 && drawerAmount(level, Items.GLASS) == glass - 1,
                "Terminal crafting did not consume drawer ingredients");
        require(player.getInventory().isEmpty(), "Crafting unexpectedly used player inventory");
        menu.removed(player);
        // Restore consumed test inputs, so the generated manifest is exact.
        require(terminal.pushStack(new ItemStack(Items.COPPER_INGOT)).isEmpty(), "Copper restoration failed");
        require(terminal.pushStack(new ItemStack(Items.GLASS)).isEmpty(), "Glass restoration failed");
        for (var entry : TANKS.entrySet()) {
            var buffer = (FluidBufferEntity) Objects.requireNonNull(level.getBlockEntity(entry.getKey()));
            require(
                    buffer.tank.getFluid().is(entry.getValue()) && buffer.tank.getFluidAmount() == 4000,
                    "Invalid fluid supply");
        }
        for (int x = 1; x <= 41; x++)
            for (int z = 1; z <= 41; z++) {
                require(
                        level.getBlockState(new BlockPos(x, Y + HEIGHT, z))
                                .isSolidRender(level, new BlockPos(x, Y + HEIGHT, z)),
                        "Roof gap");
                for (int y = Y; y < Y + HEIGHT; y++) {
                    var pos = new BlockPos(x, y, z);
                    var state = level.getBlockState(pos);
                    if (x >= 9 && x <= 38 && z >= 6 && z <= 37)
                        require(state.isAir(), "Obstructed construction area: " + pos);
                    if (state.isAir()
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                        require(
                                level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) > 0,
                                "Unlit spawnable surface: " + pos);
                }
            }
        var chest = (ChestBlockEntity) Objects.requireNonNull(level.getBlockEntity(new BlockPos(4, Y, 5)));
        require(
                java.util.stream.IntStream.range(0, 15)
                        .allMatch(i -> !chest.getItem(i).isEmpty()),
                "Missing tool chest contents");
        require(
                level.getBlockState(new BlockPos(7, Y, 5)).is(Blocks.RED_BED)
                        && level.getBlockState(new BlockPos(7, Y, 4)).getValue(BedBlock.PART) == BedPart.HEAD,
                "Missing bed");
        for (int n = 0; n <= 42; n++)
            for (int y = Y; y < Y + HEIGHT; y++) {
                require(
                        level.getBlockState(new BlockPos(0, y, n)).is(Blocks.STONE_BRICKS)
                                && level.getBlockState(new BlockPos(42, y, n)).is(Blocks.STONE_BRICKS)
                                && level.getBlockState(new BlockPos(n, y, 0)).is(Blocks.STONE_BRICKS)
                                && level.getBlockState(new BlockPos(n, y, 42)).is(Blocks.STONE_BRICKS),
                        "Workshop wall gap");
            }
        require(
                level.getBlockState(SPAWN).isAir()
                        && level.getBlockState(SPAWN.above()).isAir(),
                "Blocked spawn");
        LogUtils.getLogger()
                .info(
                        "SURVIVAL ACCEPTANCE PASSED: {} stocked drawers, {} fluid buffers, terminal crafting consumed drawer inputs",
                        DRAWERS.size(),
                        TANKS.size());
    }

    private static long amount(CraftingTerminalBlockEntity terminal, Item item) {
        return terminal.getStacks().values().stream()
                .filter(s -> s.getStack().is(item))
                .mapToLong(StoredItemStack::getQuantity)
                .sum();
    }

    private static long drawerAmount(ServerLevel level, Item item) {
        return DRAWERS.entrySet().stream()
                .filter(e -> e.getValue() == item)
                .mapToLong(e -> ((DrawerTile) Objects.requireNonNull(level.getBlockEntity(e.getKey())))
                        .getStorage()
                        .getStackInSlot(0)
                        .getCount())
                .sum();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if ((!Boolean.getBoolean("mobvats.survival.generate") && !Boolean.getBoolean("mobvats.survival.verify"))
                || !passed) return;
        try {
            var world = Path.of(System.getProperty("mobvats.survival.world"));
            var root = net.minecraft.nbt.NbtIo.readCompressed(
                    world.resolve("level.dat"), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var data = root.getCompound("Data");
            require(
                    data.getInt("GameType") == 0
                            && data.getByte("Difficulty") == 2
                            && !data.getBoolean("allowCommands"),
                    "Saved world is not Survival/Normal/cheats disabled");
            require(
                    data.getInt("SpawnX") == SPAWN.getX()
                            && data.getInt("SpawnY") == SPAWN.getY()
                            && data.getInt("SpawnZ") == SPAWN.getZ(),
                    "Saved spawn incorrect");
            if (Boolean.getBoolean("mobvats.survival.verify")) {
                Files.writeString(
                        world.resolve("survival-smoke-complete.txt"),
                        "Reloaded inventories and terminal crafting passed\n");
                return;
            }
            // Only friendly display metadata; terrain and block entity serialization remain Minecraft-owned.
            data.putString("LevelName", "MobVats Survival Workshop");
            net.minecraft.nbt.NbtIo.writeCompressed(root, world.resolve("level.dat"));
            Files.writeString(world.resolve("survival-stock.csv"), manifest);
            var fluidManifest = new StringBuilder("id,x,y,z\n");
            TANKS.forEach((pos, fluid) -> fluidManifest
                    .append(BuiltInRegistries.FLUID.getKey(fluid))
                    .append(',')
                    .append(pos.getX())
                    .append(',')
                    .append(pos.getY())
                    .append(',')
                    .append(pos.getZ())
                    .append('\n'));
            Files.writeString(world.resolve("survival-fluids.csv"), fluidManifest);
            Files.writeString(
                    world.resolve("survival-complete.txt"),
                    "Survival workshop v1\nDrawer counts, terminal crafting and consumption, fluids, lighting, roof, spawn and saved Survival settings passed.\n");
        } catch (Exception error) {
            LogUtils.getLogger().error("SURVIVAL SAVE VERIFICATION FAILED", error);
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    private static void sign(ServerLevel level, BlockPos pos, String... lines) {
        level.setBlockAndUpdate(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
        var sign = (net.minecraft.world.level.block.entity.SignBlockEntity)
                Objects.requireNonNull(level.getBlockEntity(pos));
        var text = new net.minecraft.world.level.block.entity.SignText();
        for (int i = 0; i < Math.min(4, lines.length); i++)
            text = text.setMessage(i, net.minecraft.network.chat.Component.literal(lines[i]));
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setChanged();
    }

    private static Block block(String id) {
        var key = ResourceLocation.parse(id);
        require(BuiltInRegistries.BLOCK.containsKey(key), "Unknown block: " + id);
        return BuiltInRegistries.BLOCK.get(key);
    }

    private static void put(ServerLevel level, BlockPos pos, String id) {
        var state = block(id).defaultBlockState();
        if (state.hasProperty(com.buuz135.functionalstorage.block.Drawer.FACING_HORIZONTAL_CUSTOM))
            state = state.setValue(com.buuz135.functionalstorage.block.Drawer.FACING_HORIZONTAL_CUSTOM, Direction.SOUTH)
                    .setValue(com.buuz135.functionalstorage.block.Drawer.FACING_ALL, Direction.DOWN);
        else if (state.hasProperty(com.hrznstudio.titanium.block.RotatableBlock.FACING_HORIZONTAL))
            state = state.setValue(com.hrznstudio.titanium.block.RotatableBlock.FACING_HORIZONTAL, Direction.SOUTH);
        else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        level.setBlockAndUpdate(pos, state);
    }

    private static ItemStack stack(String id, int count) {
        var key = ResourceLocation.parse(id);
        require(BuiltInRegistries.ITEM.containsKey(key), "Unknown item: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key), count);
    }

    private static void fail(MinecraftServer server, Exception error) {
        active = false;
        passed = false;
        LogUtils.getLogger().error("SURVIVAL GENERATION FAILED", error);
        server.halt(false);
    }
}
