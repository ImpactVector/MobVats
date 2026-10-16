package net.impactvector.mobvats.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import java.nio.file.*;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.CultureFouling;
import net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitBlock;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

/** Acceptance rigs, driven only by ordinary machine/hopper ticks. Never substitutes a transfer tick
 * for missing inter-machine plumbing. Missing requirements remain explicit acceptance failures. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "mobvats")
final class ProductionWorldFixtures {
    private record Mixer(String id, String recipe, BlockPos origin, boolean combustion, boolean attached) {}

    private static final List<Mixer> MIXERS = new ArrayList<>();
    private static final Set<String> SEEDED = new HashSet<>(), ITEMS = new HashSet<>(), RECIPES = new HashSet<>();
    private static final Set<BlockPos> SERVICED = new HashSet<>();
    private static final List<String> HARVEST_HISTORY = new ArrayList<>();
    private static final Map<BlockPos, LifeStage> LAST_STAGE = new HashMap<>();
    private static long lastHarvest = -1;
    private static final Map<BlockPos, Integer> LAST_GROWTH = new HashMap<>();
    private static boolean mediumUsed;
    private static final List<BlockPos> HEADS = new ArrayList<>(), TOOLS = new ArrayList<>();
    private static final BlockPos VAT = new BlockPos(4, -60, 116);
    private static final int SWORDS = 12;
    private static final int STARTING_SLURRY = 3000;
    private static final Set<BlockPos> SLURRY_FED = new HashSet<>();
    private static boolean primed;
    private static boolean swordSupplyPaused, swordSupplyReleased;
    private static BlockPos swordLever;
    private static BlockPos sharedFeed;
    private static BlockPos swordPort, swordChest, swordHopper, itemOutput;
    private static int width, depth, height;

    /** Allow one complete replacement cohort at the dry 0.5x frequency, plus settling time. */
    static int observationDeadline() {
        return Math.addExact(
                OperatorVatFixtures.SETTLE_TICKS,
                Math.multiplyExact(
                        PassiveMobDefinitionRegistry.resolve(ResourceLocation.withDefaultNamespace("cow"))
                                .juvenile_growth_ticks(),
                        2));
    }

    static boolean cultureObserved(ServerLevel level) {
        var fixture =
                MIXERS.stream().filter(f -> f.id().equals("P-06")).findFirst().orElseThrow();
        var mixer = controller(level, fixture);
        return mixer != null
                && RECIPES.contains(fixture.id())
                && mixer.state().metrics().total("culture_slurryProduced") >= 1000
                && mediumUsed;
    }

    static String cultureProgress(ServerLevel level) {
        var fixture =
                MIXERS.stream().filter(f -> f.id().equals("P-06")).findFirst().orElseThrow();
        var mixer = controller(level, fixture);
        var vat = vatController(level);
        return "P-06 XP=" + (mixer == null ? "unformed" : mixer.state().amount(4)) + " mB / 250 mB; harvests="
                + (vat == null ? 0 : vat.metrics().total(MachineMetrics.HARVESTS)) + "; enriched growth=" + mediumUsed;
    }
    /** Opt-in headless regression: exercise the minimum legal vanilla cow reward, before yield. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void minimumCowXp(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        var p = event.getEntity().blockPosition();
        if (Boolean.getBoolean("mobvats.buildTestWorld")
                && Boolean.getBoolean("mobvats.acceptanceMinimumCowXp")
                && event.getEntity().getType() == net.minecraft.world.entity.EntityType.COW
                && p.getX() >= VAT.getX()
                && p.getX() < VAT.getX() + width
                && p.getY() >= VAT.getY()
                && p.getY() < VAT.getY() + height
                && p.getZ() >= VAT.getZ()
                && p.getZ() < VAT.getZ() + depth
                && event.getDroppedExperience() > 0) {
            event.setDroppedExperience(1);
            com.mojang.logging.LogUtils.getLogger().info("MOBVATS MINIMUM COW XP at {}: 1 point before yield", p);
        }
    }

    static void build(ServerLevel level) {
        primed = false;
        swordSupplyPaused = false;
        swordSupplyReleased = false;
        SLURRY_FED.clear();
        HARVEST_HISTORY.clear();
        LAST_STAGE.clear();
        LAST_GROWTH.clear();
        mediumUsed = false;
        lastHarvest = -1;
        MIXERS.clear();
        SEEDED.clear();
        ITEMS.clear();
        RECIPES.clear();
        SERVICED.clear();
        HEADS.clear();
        TOOLS.clear();
        ManualTestWorld.sign(
                level,
                new BlockPos(-3, -60, 91),
                "MIXER RECIPES",
                "Real port supplies",
                "FE + combustion",
                "Acceptance row");
        stripe(level, 96, 76);
        mixer(level, "P-01", "feeding_slurry", new BlockPos(8, -60, 96), false, false);
        mixer(level, "P-02", "culture_slurry", new BlockPos(26, -60, 96), true, false);
        mixer(level, "P-03", "mobvats:filter_general", new BlockPos(42, -60, 96), false, false);
        mixer(level, "P-04", "mobvats:filter_lava", new BlockPos(58, -60, 96), true, false);
        stripe(level, 116, 76);
        ManualTestWorld.sign(
                level,
                new BlockPos(-3, -60, 111),
                "FULL PRODUCTION VAT",
                "4 cow assemblies",
                "One sword Store",
                "Direct port loop");
        vat(level);
        mixer(level, "P-05", "feeding_slurry", VAT.offset(-5, 2, 1), true, true);
        mixer(level, "P-06", "culture_slurry", VAT.offset(width, 2, 1), false, true);
        connectLoop(level);
        serviceModes(level, VAT, VAT.offset(width - 1, height - 1, depth - 1));
        for (var f : MIXERS)
            if (f.attached()) serviceModes(level, f.origin(), f.origin().offset(4, 3, 4));
    }

    static void installRedstoneControls(ServerLevel level, StringBuilder manifest) {
        for (var f : MIXERS)
            if (!f.attached())
                WorldRedstoneControls.install(
                        level, manifest, f.id(), f.origin().offset(0, 1, 2), Direction.WEST);
        WorldRedstoneControls.install(level, manifest, "P-VAT (linked loop)", VAT.offset(1, 2, 0), Direction.NORTH);
    }

    private static void stripe(ServerLevel level, int z, int end) {
        for (int x = -4; x <= end; x++)
            level.setBlockAndUpdate(new BlockPos(x, -61, z - 7), Blocks.YELLOW_WOOL.defaultBlockState());
    }

    private static void connectLoop(ServerLevel level) {
        // A continuous west wall riser joins floor collection to roof storage;
        // ports remain endpoints and never serve as transit nodes.
        for (int y = 0; y < height; y++) fluid(level, VAT.offset(0, y, depth / 2 + 1));
        fluid(level, VAT.offset(1, 0, depth / 2 + 1));
        for (var f : MIXERS.stream().filter(Mixer::attached).toList()) {
            boolean feeding = f.recipe().equals("feeding_slurry");
            int mx = feeding ? 4 : 0, vx = feeding ? 0 : width - 1;
            mixerBoundary(level, f.origin().offset(mx, 1, 1), feeding ? "mobvats:waste" : "mobvats:experience", false);
            mixerBoundary(
                    level,
                    f.origin().offset(mx, 1, 3),
                    feeding ? "mobvats:feeding_slurry" : "mobvats:culture_slurry",
                    true);
            // Move the culture Void to the opposite wall so its west output faces the Vat.
            if (!feeding) {
                level.setBlockAndUpdate(f.origin().offset(4, 1, 3), MixerContent.state(MixerPartType.FluidVoid));
                ((MixerFluidVoidEntity) level.getBlockEntity(f.origin().offset(4, 1, 3)))
                        .filter("mobvats:culture_slurry");
                AcceptanceMixerFixtures.connectControllerAbove(level, f.origin());
            }
            vatFluidPort(level, VAT.offset(vx, 3, 2), feeding ? "mobvats:waste" : "mobvats:experience", true);
            vatFluidPort(
                    level, VAT.offset(vx, 3, 4), feeding ? "mobvats:feeding_slurry" : "mobvats:culture_slurry", false);
            // Actual inward attachments descend in the empty cross aisle. Bare conduit
            // is legal inside; casing cladding is reserved for structural shell cells.
            int inward = feeding ? 1 : width - 2;
            for (int y = 1; y <= 3; y++)
                level.setBlockAndUpdate(
                        VAT.offset(inward, y, 4),
                        ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                                .setValue(ConduitBlock.MODE, ConduitMode.TAP));
            // Short wall header attaches both boundary endpoints without traversing a port.
            fluid(level, VAT.offset(vx, 3, 3));
            for (int z = 0; z < depth; z++) if (vx != width - 1 || z != depth / 2) fluid(level, VAT.offset(vx, 4, z));
        }
    }

    private static void mixerBoundary(ServerLevel level, BlockPos pos, String filter, boolean output) {
        level.setBlockAndUpdate(pos, MixerContent.state(MixerPartType.FluidPort));
        var port = (MixerFluidPortEntity) level.getBlockEntity(pos);
        port.filter(filter);
        port.setIoDirection(output ? IoDirection.Output : IoDirection.Input);
    }

    private static void fluid(ServerLevel level, BlockPos pos) {
        fluid(level, pos, ConduitShell.VAT);
    }

    private static void fluid(ServerLevel level, BlockPos pos, ConduitShell shell) {
        level.setBlockAndUpdate(
                pos,
                ConduitContent.state(ConduitResource.FLUID, ConduitCladding.CASING, shell)
                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
    }

    private static void item(ServerLevel level, BlockPos pos) {
        item(level, pos, ConduitShell.VAT);
    }

    private static void item(ServerLevel level, BlockPos pos, ConduitShell shell) {
        level.setBlockAndUpdate(
                pos,
                ConduitContent.state(ConduitResource.ITEM, ConduitCladding.CASING, shell)
                        .setValue(ConduitBlock.MODE, ConduitMode.TAP));
    }

    private static void buffer(ServerLevel level, BlockPos pos, String filter) {
        buffer(level, pos, filter, ConduitShell.VAT);
    }

    private static void buffer(ServerLevel level, BlockPos pos, String filter, ConduitShell shell) {
        level.setBlockAndUpdate(pos, FluidBufferContent.state(ConduitCladding.CASING, shell));
        ((FluidBufferEntity) level.getBlockEntity(pos)).filter(filter);
    }
    /** Fixture configuration only: transit cells use TRUNK; endpoint attachments use TAP. */
    private static void serviceModes(ServerLevel level, BlockPos min, BlockPos max) {
        for (var p : BlockPos.betweenClosed(min, max))
            if (level.getBlockState(p).getBlock() instanceof ConduitBlock conduit) {
                boolean tap = false;
                for (var direction : Direction.values()) {
                    var entity = level.getBlockEntity(p.relative(direction));
                    var resource = conduit.resource();
                    tap |= entity instanceof ResourceEndpoint e
                                    && !e.roles(resource).isEmpty()
                            || entity instanceof VatActor a
                                    && (resource == ConduitResource.FLUID
                                            ? a.usesLocalFluidPorts()
                                            : a.usesLocalItemPorts())
                            || entity instanceof VatBoundaryEntity e && e.resource() == resource
                            || entity instanceof MixerFluidVoidEntity && resource == ConduitResource.FLUID
                            || entity instanceof AbstractMixerDriveEntity e
                                    && e.conduitResources().contains(resource);
                }
                level.setBlockAndUpdate(
                        p,
                        level.getBlockState(p).setValue(ConduitBlock.MODE, tap ? ConduitMode.TAP : ConduitMode.TRUNK));
            }
    }

    private static void mixer(
            ServerLevel level, String id, String recipe, BlockPos o, boolean combustion, boolean attached) {
        MIXERS.add(new Mixer(id, recipe, o, combustion, attached));
        boolean filtering = recipe.contains("filter_");
        String output = filtering
                ? (recipe.endsWith("lava") ? "minecraft:lava" : "minecraft:water")
                : recipe.equals("feeding_slurry") ? "mobvats:feeding_slurry" : "mobvats:culture_slurry";
        String input = filtering ? "mobvats:culture_slurry" : "minecraft:water";
        String reagentFluid = filtering
                ? "mobvats:culture_slurry"
                : recipe.equals("feeding_slurry") ? "mobvats:waste" : "mobvats:experience";
        AcceptanceMixerFixtures.build(
                level,
                o,
                List.of(
                        input,
                        input,
                        input,
                        reagentFluid,
                        reagentFluid,
                        reagentFluid,
                        output,
                        output,
                        output,
                        output,
                        output,
                        output));
        var out = (MixerFluidPortEntity) level.getBlockEntity(o.offset(4, 1, 2));
        out.filter(output);
        // Fuel has a dedicated real Input Port directly adjacent to the combustion motor.
        level.setBlockAndUpdate(
                o.offset(1, 3, 2),
                MixerContent.state(combustion ? MixerPartType.CombustionDrive : MixerPartType.FeDrive));
        if (combustion) {
            level.setBlockAndUpdate(o.offset(3, 3, 1), MixerContent.state(MixerPartType.ItemPort));
            item(level, o.offset(2, 3, 1), ConduitShell.MIXER);
            item(level, o.offset(1, 3, 1), ConduitShell.MIXER);
            ((MixerPortEntity) level.getBlockEntity(o.offset(3, 3, 1))).filter("minecraft:coal");
            supply(level, o.offset(3, 3, 1), Direction.DOWN, Items.COAL, 64, false);
        }
        Item reagent = filtering
                ? (recipe.endsWith("lava") ? Items.SAND : Items.CHARCOAL)
                : recipe.equals("feeding_slurry") ? Items.WHEAT : null;
        if (reagent != null) {
            ((MixerPortEntity) level.getBlockEntity(o.offset(2, 1, 4)))
                    .filter(net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(reagent)
                            .toString());
            supply(level, o.offset(2, 1, 4), Direction.NORTH, reagent, 64, false);
        }
        ManualTestWorld.sign(
                level,
                o.offset(0, 0, -5),
                id,
                recipe,
                "Drive: " + (combustion ? "combustion" : "FE"),
                attached ? "Vat service Mixer" : "Production Mixer");
        ManualTestWorld.sign(
                level,
                o.offset(4, 0, -5),
                "Inputs via ports",
                filtering
                        ? "Dirty " + (recipe.endsWith("lava") ? "lava + sand" : "water + charcoal")
                        : "Water + " + (recipe.equals("feeding_slurry") ? "waste + wheat" : "XP fluid"),
                "Out: " + output,
                "6 x 4000 mB out");
        level.setBlockAndUpdate(o.offset(0, 1, 3), MixerContent.state(MixerPartType.FluidVoid));
        ((MixerFluidVoidEntity) level.getBlockEntity(o.offset(0, 1, 3))).filter(output);
        ManualTestWorld.sign(
                level,
                o.offset(attached ? 0 : 6, 0, attached ? -6 : 2),
                "OUTPUT / OVERFLOW",
                "Filtered port + Void",
                "Storage first",
                "See acceptance.md");
    }

    private static void part(ServerLevel level, BlockPos pos, VatPartType type) {
        level.setBlockAndUpdate(pos, Content.state(type));
    }

    private static void vat(ServerLevel level) {
        var cow = ResourceLocation.withDefaultNamespace("cow");
        var size = net.minecraft.world.entity.EntityType.COW.getDimensions();
        var definition = PassiveMobDefinitionRegistry.resolve(cow);
        var g = SimulacrumGeometry.resolve(
                size.width(), size.height(), size.width(), Direction.NORTH, definition.simulacrum());
        // Resolve authoritative geometry before fixing the requested envelope. Refuse
        // changed profiles rather than silently widening or guessing occupied cells.
        int pitchX = 4, pitchZ = 4;
        width = 9;
        depth = 9;
        height = 6;
        require(g.width() == 1 && g.depth() == 1 && g.height() == 2, "P-VAT cow geometry changed: " + g);
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                for (int z = 0; z < depth; z++) {
                    boolean shell = x == 0 || x == width - 1 || y == 0 || y == height - 1 || z == 0 || z == depth - 1;
                    level.setBlockAndUpdate(
                            VAT.offset(x, y, z),
                            shell
                                    ? Content.state(y == 0 || y == height - 1 ? VatPartType.Casing : VatPartType.Glass)
                                    : Blocks.AIR.defaultBlockState());
                }
        part(level, VAT.offset(1, 2, 0), VatPartType.Controller);
        // Separate embedded floor perimeters; only short Sump/feeder attachments branch off.
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++) {
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) item(level, VAT.offset(x, 0, z));
                else if (x == 1 || x == width - 2 || z == 1 || z == depth - 2) fluid(level, VAT.offset(x, 0, z));
            }
        for (int x : new int[] {2, 2 + pitchX})
            for (int z : new int[] {2, 2 + pitchZ}) {
                var headPos = VAT.offset(x, 2, z).offset(g.rotate(g.headOffset()));
                HEADS.add(headPos);
                part(level, headPos, VatPartType.Head);
                ((VatHeadEntity) level.getBlockEntity(headPos)).encode(cow, g);
                for (var cell : g.occupiedCells(headPos))
                    if (!cell.equals(headPos)) part(level, cell, VatPartType.Flesh);
                for (var corner : g.harnessCorners(headPos, VAT.getY() + height - 1))
                    part(level, corner, VatPartType.Harness);
                part(level, VAT.offset(x, 0, z), VatPartType.Sump);
                int edge = z == 2 ? 0 : depth - 1;
                var tool = VAT.offset(x, 2, edge);
                TOOLS.add(tool);
                part(level, tool, VatPartType.ToolPort);
                item(level, tool.below());
            }
        // One feeder reaches all four diagonal bodies. Its floor branch runs in the
        // central aisle, outside every comfort shell; the final TAP rises below it.
        sharedFeed = VAT.offset(4, 1, 4);
        part(level, sharedFeed, VatPartType.FeedPort);
        for (int z = 1; z <= 4; z++) fluid(level, VAT.offset(4, 0, z));
        // Fluid perimeter and continuous west wall column connect the floor to roof storage.
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++)
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) fluid(level, VAT.offset(x, height - 1, z));
        String[] fluids = {
            "minecraft:milk", "mobvats:waste", "mobvats:experience", "mobvats:feeding_slurry", "mobvats:culture_slurry"
        };
        for (int i = 0; i < fluids.length; i++) buffer(level, VAT.offset(1 + i, height - 1, 0), fluids[i]);
        // Different filtered buffers are endpoints, not transit nodes. A short wall header gives
        // every roof buffer and both front slurry ports an actual shared conduit attachment.
        for (int x = 0; x < width - 1; x++) fluid(level, VAT.offset(x, height - 2, 0));
        buffer(level, VAT.offset(2, height - 1, depth - 1), "mobvats:waste");
        buffer(level, VAT.offset(3, height - 1, depth - 1), "mobvats:experience");
        buffer(level, VAT.offset(6, height - 1, 0), "mobvats:feeding_slurry");
        for (int x = 4; x <= 6; x++) buffer(level, VAT.offset(x, height - 1, depth - 1), "mobvats:waste");
        // A low first-cohort XP roll needs a dry replacement cycle. Retain its waste
        // in real roof buffers, with spare capacity for the unchanged acceptance checks.
        // Four 2x-yield cows can produce 2,000 mB every 1,200 ticks (~88 buckets
        // by the deadline). Seventeen extra buffers retain a full dry cycle.
        for (int z = 1; z < depth - 1; z++) buffer(level, VAT.offset(4, height - 1, z), "mobvats:waste");
        for (int x = 1; x < width - 1; x++) if (x != 4) buffer(level, VAT.offset(x, height - 1, 4), "mobvats:waste");
        for (int z = 1; z < depth - 1; z++)
            level.setBlockAndUpdate(
                    VAT.offset(4, height - 2, z),
                    ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        for (int x = 1; x < width - 1; x++)
            level.setBlockAndUpdate(
                    VAT.offset(x, height - 2, 4),
                    ConduitContent.state(ConduitResource.FLUID, ConduitCladding.BARE)
                            .setValue(ConduitBlock.MODE, ConduitMode.TAP));
        for (int x : new int[] {3, 5}) for (int z = 2; z <= 6; z++) fluid(level, VAT.offset(x, height - 1, z));
        for (int x : new int[] {2, 6})
            for (int z : new int[] {3, 5}) buffer(level, VAT.offset(x, height - 1, z), "mobvats:waste");
        for (int x = 0; x < width - 1; x++) fluid(level, VAT.offset(x, height - 2, depth - 1));
        swordPort = VAT.offset(width / 2, 1, 0);
        part(level, swordPort, VatPartType.ItemPort);
        ((VatItemPortEntity) level.getBlockEntity(swordPort)).filter("minecraft:diamond_sword");
        swordHopper = swordPort.north();
        swordChest = swordHopper.above();
        supply(level, swordPort, Direction.SOUTH, Items.DIAMOND_SWORD, SWORDS, true);
        // Ordinary redstone startup interlock: allow the first sword, then hold the
        // remaining supply until the annex has returned one real feeding batch.
        swordLever = swordHopper.north();
        level.setBlockAndUpdate(swordLever.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(
                swordLever,
                Blocks.LEVER
                        .defaultBlockState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        // Optional fallback stock stays disconnected during required slurry observations.
        var fallback = VAT.offset(4, 0, -4);
        level.setBlockAndUpdate(fallback, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(fallback)).setItem(0, new ItemStack(Items.WHEAT, 64));
        item(level, VAT.offset(4, 0, depth - 1));
        itemOutput = VAT.offset(width / 2, 0, depth - 2);
        part(level, itemOutput, VatPartType.ItemPort);
        ((VatItemPortEntity) level.getBlockEntity(itemOutput)).setIoDirection(IoDirection.Output);
        level.setBlockAndUpdate(
                itemOutput.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(
                itemOutput.below().south(),
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(itemOutput.below().south(2), Blocks.CHEST.defaultBlockState());
        vatFluidPort(level, VAT.offset(1, 3, 0), "mobvats:feeding_slurry", false);
        vatFluidPort(level, VAT.offset(3, 3, 0), "mobvats:culture_slurry", false);
        vatFluidPort(level, VAT.offset(1, 3, depth - 1), "mobvats:waste", true);
        vatFluidPort(level, VAT.offset(3, 3, depth - 1), "mobvats:experience", true);
        fluid(level, VAT.offset(1, 4, 0));
        fluid(level, VAT.offset(3, 4, 0));
        fluid(level, VAT.offset(1, 4, depth - 1));
        fluid(level, VAT.offset(3, 4, depth - 1));
        part(level, VAT.offset(width - 1, 4, depth / 2), VatPartType.FillValve);
        ((VatBoundaryEntity) level.getBlockEntity(VAT.offset(width - 1, 4, depth / 2)))
                .filter("mobvats:culture_slurry");
        part(level, VAT.offset(width - 2, height - 1, 1), VatPartType.FluidVoid);
        ((VatBoundaryEntity) level.getBlockEntity(VAT.offset(width - 2, height - 1, 1))).filter("mobvats:waste");
        // Prime only an inspectable physical reserve, as with a player's fluid container.
        // Returned compound slurry occupies the earlier front buffer and is selected
        // before this reserve by the unchanged storage/feeding order.
        var reserve = (FluidBufferEntity) level.getBlockEntity(VAT.offset(6, height - 1, 0));
        require(
                reserve.interactionCluster()
                                .tank()
                                .fill(new FluidStack(ProductFluids.FEEDING_SLURRY.get(), STARTING_SLURRY), EXECUTE)
                        == STARTING_SLURRY,
                "P-VAT startup reserve rejected");
        primed = true;
        ManualTestWorld.sign(
                level,
                VAT.offset(0, 0, -5),
                "P-VAT / 4 COWS",
                width + " x " + height + " x " + depth,
                "Shared sword Store",
                "Drops -> rear chest");
        ManualTestWorld.sign(
                level,
                VAT.offset(4, 0, -5),
                "LOOP CONNECTIONS",
                "Front: slurry in",
                "Rear: waste / XP",
                "Culture -> Fill Valve");
    }

    private static void vatFluidPort(ServerLevel level, BlockPos pos, String filter, boolean output) {
        part(level, pos, VatPartType.FluidPort);
        var port = (VatFluidPortEntity) level.getBlockEntity(pos);
        port.filter(filter);
        port.setIoDirection(output ? IoDirection.Output : IoDirection.Input);
    }

    private static void supply(
            ServerLevel level, BlockPos port, Direction toward, Item value, int count, boolean serials) {
        var hopper = port.relative(toward.getOpposite());
        var chestPos = hopper.above();
        level.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, toward));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        for (int slot = 0; count > 0; slot++) {
            int n = Math.min(count, value.getDefaultMaxStackSize());
            var stack = new ItemStack(value, n);
            if (serials) stack.set(DataComponents.CUSTOM_NAME, Component.literal("Acceptance sword " + slot));
            chest.setItem(slot, stack);
            count -= n;
        }
        chest.setChanged();
    }

    /** One-time external supply through the exposed boundary capability, after formation. It does
     * not advance recipes, fill a process tank, or transport Vat products back to a Mixer. */
    static void observe(ServerLevel level) {
        for (var f : MIXERS) {
            var mixer = controller(level, f);
            if (mixer == null || !mixer.isAssembled()) continue;
            if (!SEEDED.contains(f.id())) {
                var boundary = level.getCapability(
                        Capabilities.FluidHandler.BLOCK, f.origin().offset(2, 1, 0), Direction.NORTH);
                require(boundary != null, f.id() + " missing fluid boundary capability");
                var inputs = f.attached()
                        ? List.of(new FluidStack(Fluids.WATER, 4000))
                        : f.recipe().contains("filter_")
                                ? List.of(CultureFouling.dirty(
                                                new FluidStack(
                                                        f.recipe().endsWith("lava") ? Fluids.LAVA : Fluids.WATER, 1),
                                                100)
                                        .copyWithAmount(4000))
                                : List.of(
                                        new FluidStack(Fluids.WATER, 4000),
                                        new FluidStack(
                                                f.recipe().equals("feeding_slurry")
                                                        ? ProductFluids.WASTE.get()
                                                        : ProductFluids.EXPERIENCE.get(),
                                                f.recipe().equals("feeding_slurry") ? 1000 : 400));
                for (var stack : inputs)
                    require(
                            boundary.fill(stack, EXECUTE) == stack.getAmount(),
                            f.id() + " supply rejected by Fluid Input Port: " + stack);
                if (!f.combustion()) {
                    var energy = level.getCapability(
                            Capabilities.EnergyStorage.BLOCK, f.origin().offset(1, 3, 2), Direction.UP);
                    require(
                            energy != null
                                    && energy.receiveEnergy(MixerFeDriveEntity.CAPACITY, false)
                                            == MixerFeDriveEntity.CAPACITY,
                            f.id() + " FE boundary unavailable");
                }
                SEEDED.add(f.id());
            }
            if (mixer.state().plantCount() > 0) ITEMS.add(f.id());
            if (mixer.state().recipe().equals(f.recipe()) && mixer.state().progress() > 0) RECIPES.add(f.id());
        }
        var vat = vatController(level);
        if (vat == null || !vat.isAssembled()) return;
        var comforts = vat.getAssemblies().stream().map(a -> a.clearance(level)).toList();
        for (var assembly : vat.getAssemblies())
            require(
                    assembly.clearance(level).creditedFraction(comforts) == 1.0
                            && vat.yieldMultiplier(assembly.head().getBlockPos()) == 2.0,
                    "P-VAT happiness penalty during production at "
                            + assembly.head().getBlockPos());
        if (!swordSupplyPaused
                && TOOLS.stream()
                        .anyMatch(p -> !((VatToolPortEntity) level.getBlockEntity(p))
                                .heldItem()
                                .isEmpty())) {
            ((LeverBlock) Blocks.LEVER).pull(level.getBlockState(swordLever), level, swordLever, null);
            swordSupplyPaused = true;
        }
        if (swordSupplyPaused
                && !swordSupplyReleased
                && vatAmount(vat, ProductFluids.FEEDING_SLURRY.get()) - startupRemaining(vat) >= 1000) {
            ((LeverBlock) Blocks.LEVER).pull(level.getBlockState(swordLever), level, swordLever, null);
            swordSupplyReleased = true;
        }
        require(vat.metrics().total(MachineMetrics.WHEAT) == 0, "P-VAT silently used wheat instead of slurry");
        for (var pos : HEADS) {
            var state = vat.processEngine().state(pos);
            if (state != null && !state.feedSource().equals("None")) {
                require(state.feedSource().equals("feeding slurry"), "P-VAT non-slurry feed at " + pos);
                SLURRY_FED.add(pos);
            }
        }
        long harvested = vat.metrics().total(MachineMetrics.HARVESTS);
        for (var pos : HEADS) {
            var state = vat.processEngine().state(pos);
            if (state == null) continue;
            var previous = LAST_STAGE.put(pos, state.stage());
            if (previous != state.stage())
                HARVEST_HISTORY.add(level.getGameTime() + " " + pos + " " + previous + " -> " + state.stage()
                        + " harvests=" + harvested + " feed=" + state.feedSource());
        }
        if (harvested != lastHarvest) {
            HARVEST_HISTORY.add(level.getGameTime() + " harvest counter " + lastHarvest + " -> " + harvested);
            lastHarvest = harvested;
        }
        for (var pos : HEADS) {
            var state = vat.processEngine().state(pos);
            if (state == null) continue;
            var previous = LAST_GROWTH.put(pos, state.growth().elapsed);
            if (previous != null
                    && state.stage() == LifeStage.JUVENILE
                    && state.growth().elapsed > previous
                    && !vat.medium().isEmpty()
                    && vat.medium().frequency(((VatHeadEntity) level.getBlockEntity(pos)).getIdentity()) > 0.5)
                mediumUsed = true;
        }
        for (var pos : TOOLS) {
            var tool = (VatToolPortEntity) level.getBlockEntity(pos);
            if (tool.interactionStart() != Long.MIN_VALUE && tool.interactionHead() != null)
                SERVICED.add(tool.interactionHead());
        }
        // Tag each non-stackable sword so both total-count and duplicate-identity checks are meaningful.
        var seen = new HashSet<String>();
        int count = 0;
        for (var pos : List.of(swordChest, swordHopper)) {
            var container = (net.minecraft.world.Container) level.getBlockEntity(pos);
            for (int slot = 0; slot < container.getContainerSize(); slot++)
                count += sword(container.getItem(slot), seen);
        }
        var store = ((VatItemPortEntity) level.getBlockEntity(swordPort)).staging();
        for (int slot = 0; slot < store.getSlots(); slot++) count += sword(store.getStackInSlot(slot), seen);
        for (var pos : TOOLS) count += sword(((VatToolPortEntity) level.getBlockEntity(pos)).heldItem(), seen);
        require(count == SWORDS, "P-VAT sword conservation: expected " + SWORDS + ", got " + count);
    }
    /** Replenish the external supply for the user's run, through the same real ports. Output remains
     * accumulated from ordinary processing; service Mixers receive only external water. */
    static void prepareRun(ServerLevel level) {
        for (var f : MIXERS) {
            var mixer = controller(level, f);
            if (mixer == null || !mixer.isAssembled() || !SEEDED.contains(f.id())) continue;
            var boundary = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, f.origin().offset(2, 1, 0), Direction.NORTH);
            require(boundary != null, f.id() + " input capability disappeared");
            var inputs = f.attached()
                    ? List.of(new FluidStack(Fluids.WATER, 4000))
                    : f.recipe().contains("filter_")
                            ? List.of(CultureFouling.dirty(
                                            new FluidStack(f.recipe().endsWith("lava") ? Fluids.LAVA : Fluids.WATER, 1),
                                            100)
                                    .copyWithAmount(4000))
                            : List.of(
                                    new FluidStack(Fluids.WATER, 4000),
                                    new FluidStack(
                                            f.recipe().equals("feeding_slurry")
                                                    ? ProductFluids.WASTE.get()
                                                    : ProductFluids.EXPERIENCE.get(),
                                            f.recipe().equals("feeding_slurry") ? 1000 : 400));
            for (var stack : inputs) {
                int present = 0;
                for (int i = 0; i < boundary.getTanks(); i++)
                    if (FluidStack.isSameFluidSameComponents(boundary.getFluidInTank(i), stack))
                        present += boundary.getFluidInTank(i).getAmount();
                int needed = Math.max(0, stack.getAmount() - present);
                if (needed > 0)
                    require(
                            boundary.fill(stack.copyWithAmount(needed), EXECUTE) == needed,
                            f.id() + " manual starting supply rejected");
            }
        }
    }

    private static int sword(ItemStack stack, Set<String> seen) {
        if (stack.isEmpty()) return 0;
        require(stack.is(Items.DIAMOND_SWORD), "P-VAT unexpected sword-chain item " + stack);
        var name = stack.get(DataComponents.CUSTOM_NAME);
        require(
                stack.getCount() == 1 && name != null && seen.add(name.getString()),
                "P-VAT duplicate/unidentified sword reservation: " + stack);
        return 1;
    }

    private static MultiblockMixer controller(ServerLevel level, Mixer f) {
        return level.getBlockEntity(f.origin().offset(0, 1, 2)) instanceof MixerControllerEntity entity
                ? entity.getMultiblockController().orElse(null)
                : null;
    }

    private static MultiblockVat vatController(ServerLevel level) {
        return HEADS.isEmpty()
                ? null
                : ((VatHeadEntity) level.getBlockEntity(HEADS.getFirst()))
                        .getMultiblockController()
                        .orElse(null);
    }

    static void verify(ServerLevel level, StringBuilder manifest) throws java.io.IOException {
        var report =
                new StringBuilder("# Production acceptance\n\nNative routing only. No simulated transfer loop.\n\n");
        if (Boolean.getBoolean("mobvats.acceptanceMinimumCowXp"))
            report.append("Regression mode: minimum vanilla cow XP (1 point before happiness yield) in P-VAT.\n\n");
        var failures = new ArrayList<String>();
        for (var f : MIXERS) {
            try {
                var mixer = controller(level, f);
                require(
                        mixer != null && mixer.isAssembled(),
                        f.id() + " unformed: "
                                + (mixer == null
                                        ? "no controller"
                                        : mixer.getLastError()
                                                .map(e -> e.getChatMessage().getString())
                                                .orElse("unknown")));
                require(mixer.drive().isPresent() && mixer.availableDrive() > 0, f.id() + " drive unavailable");
                require(SEEDED.contains(f.id()), f.id() + " real port inputs missing");
                require(
                        f.recipe().equals("culture_slurry") || ITEMS.contains(f.id()),
                        f.id() + " hopper reagent never reached Item Input Port");
                require(
                        RECIPES.contains(f.id()),
                        f.id() + " intended recipe never progressed: "
                                + mixer.state().status(true) + "; "
                                + mixer.storage().diagnostics());
                long produced = mixer.state().metrics().total(f.recipe() + "Produced");
                require(
                        produced >= 1000,
                        f.id() + " no completed recipe cycle: " + mixer.state().status(true));
                var output =
                        (MixerFluidPortEntity) level.getBlockEntity(f.origin().offset(4, 1, 2));
                require(
                        output.getIoDirection().isOutput() && !output.filter().isEmpty(),
                        f.id() + " output not configured");
                var sample = output.fluids().drain(1000, SIMULATE);
                if (!f.attached()) require(!sample.isEmpty(), f.id() + " product not reachable at output boundary");
                if (!f.attached())
                    require(
                            mixer.storage().bulkConnected(output.getBlockPos()).stream()
                                            .filter(t -> t.isFluidValid(sample))
                                            .mapToInt(t -> t.fill(sample, SIMULATE))
                                            .sum()
                                    >= 1000,
                            f.id() + " no normal product output capacity");
                var sink = (MixerFluidVoidEntity) level.getBlockEntity(
                        f.origin().offset(f.attached() && f.recipe().equals("culture_slurry") ? 4 : 0, 1, 3));
                require(
                        sink != null
                                && sink.isMachineAssembled()
                                && sink.filter().equals(output.filter()),
                        f.id() + " filtered Fluid Void unavailable");
                require(
                        mixer.storage()
                                .connectedEndpoints(
                                        f.origin().offset(0, 1, 2), sink.getBlockPos(), ConduitResource.FLUID),
                        f.id() + " Void disconnected from recipe topology");
                require(sink.disposedMb() == 0, f.id() + " discarded product despite available real storage");
                if (!f.attached())
                    require(
                            produced
                                    == mixer.storage().connected(f.origin().offset(0, 1, 2)).stream()
                                            .filter(t -> sink.accepts(t.getFluid()))
                                            .mapToLong(t -> t.getFluidAmount())
                                            .sum(),
                            f.id() + " product did not accumulate in real storage");
                report.append(
                        "PASS: " + f.id() + " | " + f.recipe() + " | "
                                + f.origin().toShortString() + " | formed; real port supplies; " + produced
                                + " mB produced; " + (f.combustion() ? "combustion" : "FE")
                                + " drive; filtered Output Port and connected Fluid Void; real storage first; 0 mB discarded\n\n");
            } catch (IllegalStateException e) {
                failures.add(e.getMessage());
            }
        }
        try {
            verifyVat(level, report);
        } catch (IllegalStateException e) {
            failures.add(e.getMessage());
        }
        report.append(
                "## Acceptance status\n\nPASS: Verified end-to-end. FAIL: Broken behavior. BLOCKED: Unsupported capability.\n\n");
        if (failures.isEmpty())
            report.append(
                    "PASS: All required production behavior verified; no unsupported capability blockers. Client visual acceptance remains manual.\n\n");
        for (var failure : failures) report.append("- FAIL: " + failure + "\n");
        manifest.append("\n## Production acceptance\n\n").append(report);
        Files.writeString(Path.of(System.getProperty("mobvats.testWorldPath"), "acceptance.md"), report);
        require(failures.isEmpty(), "Production acceptance failed; see acceptance.md: " + String.join("; ", failures));
    }

    private static void verifyVat(ServerLevel level, StringBuilder report) {
        var vat = vatController(level);
        require(
                vat != null && vat.isAssembled(),
                "P-VAT unformed: "
                        + (vat == null
                                ? "no controller"
                                : vat.getLastError()
                                        .map(e -> e.getChatMessage().getString())
                                        .orElse("unknown")));
        require(
                vat.getAssemblies().size() == 4 && vat.specimenHeadCount() == 4,
                "P-VAT expected exactly four cow assemblies");
        require(width == 9 && height == 6 && depth == 9, "P-VAT dimensions changed");
        require(primed && SLURRY_FED.containsAll(HEADS), "P-VAT each cow must complete a slurry feeding");
        int feeders = 0;
        for (var p : BlockPos.betweenClosed(VAT, VAT.offset(width - 1, height - 1, depth - 1)))
            if (level.getBlockEntity(p) instanceof VatFeedPortEntity) feeders++;
        require(feeders == 1, "P-VAT expected one shared Feed Port");
        var clearances =
                vat.getAssemblies().stream().map(a -> a.clearance(level)).toList();
        var owners = new HashSet<java.util.UUID>();
        var states = Collections.newSetFromMap(new IdentityHashMap<SpecimenProcessState, Boolean>());
        for (var assembly : vat.getAssemblies()) {
            var head = assembly.head();
            require(
                    head.getIdentity().equals(ResourceLocation.withDefaultNamespace("cow"))
                            && vat.getTopology().operational(head.getBlockPos()),
                    "P-VAT inactive/non-cow Head " + head.getBlockPos());
            var clearance = assembly.clearance(level);
            require(
                    clearance.solidIntrusions().isEmpty(),
                    "P-VAT comfort shell obstructed " + head.getBlockPos() + ": " + clearance.solidIntrusions());
            require(
                    clearance.creditedFraction(clearances) == 1.0 && vat.yieldMultiplier(head.getBlockPos()) == 2.0,
                    "P-VAT cow below maximum happiness/yield: " + head.getBlockPos() + " credit="
                            + clearance.creditedFraction(clearances) + " yield="
                            + vat.yieldMultiplier(head.getBlockPos()));
            require(
                    owners.add(head.specimenId())
                            && vat.processEngine().state(head.getBlockPos()) != null
                            && states.add(vat.processEngine().state(head.getBlockPos())),
                    "P-VAT specimen identity/process state shared or absent");
            require(
                    vat.getTopology().actors().stream()
                            .anyMatch(r -> r.head().equals(head.getBlockPos())
                                    && r.actor().equals(sharedFeed)
                                    && r.use().equals(PassiveUses.FEED)),
                    "P-VAT central feeder cannot service cow " + head.getBlockPos());
            for (var other : vat.getAssemblies())
                if (other != assembly)
                    require(
                            clearance.sharedShell(other.clearance(level)).isEmpty(),
                            "P-VAT independent comfort shells overlap");
            require(
                    SERVICED.contains(head.getBlockPos()),
                    "P-VAT cow never serviced " + head.getBlockPos() + ": "
                            + vat.processEngine().status(head.getBlockPos()));
            require(
                    vat.getTopology().actors().stream()
                            .anyMatch(r -> r.head().equals(head.getBlockPos())
                                    && r.use().equals(PassiveUses.HARVEST)),
                    "P-VAT cow has no kill route " + head.getBlockPos());
        }
        for (var pos : TOOLS) {
            var tool = (VatToolPortEntity) level.getBlockEntity(pos);
            require(tool.heldItem().is(Items.DIAMOND_SWORD), "P-VAT tool did not acquire shared-chest sword " + pos);
            require(
                    vat.getTopology()
                            .conduits()
                            .reachable(pos, ConduitResource.ITEM)
                            .contains(swordPort),
                    "P-VAT Tool Port cannot reach shared Store " + pos);
            require(tool.heldItem().getDamageValue() > 0, "P-VAT sword never incurred durability at " + pos);
            require(
                    vat.getTopology().actors().stream()
                                    .filter(r ->
                                            r.actor().equals(pos) && r.use().equals(PassiveUses.HARVEST))
                                    .count()
                            == 1,
                    "P-VAT Tool Port must have exactly one kill assignment " + pos);
        }
        require(
                TOOLS.stream()
                                .mapToLong(p -> ((VatToolPortEntity) level.getBlockEntity(p))
                                        .heldItem()
                                        .getDamageValue())
                                .sum()
                        == vat.metrics().total(MachineMetrics.DURABILITY),
                "P-VAT retained sword durability does not match charged wear");
        require(
                vat.getTopology()
                        .conduits()
                        .reachable(sharedFeed, ConduitResource.FLUID)
                        .contains(VAT.offset(1, 3, 0)),
                "P-VAT feeder disconnected from inward startup TAP");
        for (int x : new int[] {0, width - 1}) {
            var port = VAT.offset(x, 3, 4);
            var tap = port.relative(x == 0 ? Direction.EAST : Direction.WEST);
            require(
                    level.getBlockState(tap).getBlock() instanceof ConduitBlock c
                            && c.resource() == ConduitResource.FLUID
                            && level.getBlockState(tap).getValue(ConduitBlock.MODE) == ConduitMode.TAP
                            && vat.getTopology()
                                    .conduits()
                                    .reachable(sharedFeed, ConduitResource.FLUID)
                                    .contains(port),
                    "P-VAT slurry inlet lacks a connected inward TAP " + port);
        }
        require(
                vat.metrics().total(MachineMetrics.HARVESTS) >= 4,
                "P-VAT four kills never committed: harvests=" + vat.metrics().total(MachineMetrics.HARVESTS) + " feed="
                        + vat.metrics().total("feedingConsumed") + " history=" + HARVEST_HISTORY + " states="
                        + HEADS.stream()
                                .map(p -> p + ":" + vat.processEngine().status(p))
                                .toList());
        var chestPos = itemOutput.below().south(2);
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        boolean room = false;
        int drops = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            room |= chest.getItem(slot).isEmpty();
            drops += chest.getItem(slot).getCount();
        }
        require(
                room && drops > 0 && level.getBlockState(chestPos.above()).isAir(),
                "P-VAT normal output chest blocked, full or empty");
        long retained = vat.killOutputs().itemCount();
        for (var pos : TOOLS)
            retained +=
                    ((VatToolPortEntity) level.getBlockEntity(pos)).results().itemCount();
        var outputPort = (VatItemPortEntity) level.getBlockEntity(itemOutput);
        for (int slot = 0; slot < outputPort.staging().getSlots(); slot++)
            retained += outputPort.staging().getStackInSlot(slot).getCount();
        for (var pos : List.of(itemOutput.below(), itemOutput.below().south())) {
            var hopper = (HopperBlockEntity) level.getBlockEntity(pos);
            for (int slot = 0; slot < hopper.getContainerSize(); slot++)
                retained += hopper.getItem(slot).getCount();
        }
        require(
                vat.metrics().total(MachineMetrics.HARVEST_ITEMS) == drops + retained,
                "P-VAT item drops lost/duplicated: produced=" + vat.metrics().total(MachineMetrics.HARVEST_ITEMS)
                        + " chest=" + drops + " retained=" + retained);
        for (var fluid : List.of(ProductFluids.EXPERIENCE.get(), ProductFluids.WASTE.get())) {
            var sample = new FluidStack(fluid, 1000);
            require(
                    vat.storage().clusters().stream()
                                    .filter(c -> c.tank().isFluidValid(sample))
                                    .mapToInt(c -> c.tank().fill(sample, SIMULATE))
                                    .sum()
                            >= 1000,
                    "P-VAT normal byproduct capacity unavailable: " + sample);
        }
        require(vat.metrics().total(MachineMetrics.XP) > 0, "P-VAT no real XP production");
        // Larger Mixer edge storage may receive every emitted byproduct before verification.
        // Production metrics plus the conservation check below prove emission after transfer.
        require(vat.metrics().total(MachineMetrics.WASTE) > 0, "P-VAT no real waste byproduct");
        for (var port : List.of(VAT.offset(1, 3, depth - 1), VAT.offset(3, 3, depth - 1))) {
            var boundary = level.getCapability(Capabilities.FluidHandler.BLOCK, port, Direction.SOUTH);
            require(boundary != null, "P-VAT byproduct output capability missing " + port);
        }
        var feeding = controller(
                level,
                MIXERS.stream()
                        .filter(f -> f.attached() && f.recipe().equals("feeding_slurry"))
                        .findFirst()
                        .orElseThrow());
        var culture = controller(
                level,
                MIXERS.stream()
                        .filter(f -> f.attached() && f.recipe().equals("culture_slurry"))
                        .findFirst()
                        .orElseThrow());
        require(feeding.state().metrics().total("wasteConsumed") > 0, "Vat waste never consumed by feeding Mixer");
        require(culture.state().metrics().total("experienceConsumedMb") > 0, "Vat XP never consumed by culture Mixer");
        require(vat.metrics().total("feedingConsumed") > 0, "Returned feeding slurry never consumed by Vat");
        long returnedConsumed = vat.metrics().total("feedingConsumed") - (STARTING_SLURRY - startupRemaining(vat));
        require(
                startupRemaining(vat) < STARTING_SLURRY,
                "P-VAT first startup feed never consumed the physical reserve");
        require(
                swordSupplyReleased && returnedConsumed > 0,
                "Returned feeding slurry never consumed; startup consumption alone is insufficient");
        require(
                !level.getBlockState(swordLever).getValue(LeverBlock.POWERED)
                        && level.getBlockState(swordHopper).getValue(HopperBlock.ENABLED),
                "P-VAT sword supply left locked after startup");
        require(
                level.getBlockEntity(VAT.offset(width - 1, 4, depth / 2)) instanceof VatBoundaryEntity valve
                        && valve.getPartType() == VatPartType.FillValve
                        && valve.isMachineAssembled(),
                "Culture Fill Valve missing or unformed");
        require(!vat.medium().isEmpty(), "Returned culture never reached medium");
        require(
                STARTING_SLURRY + feeding.state().metrics().total("feeding_slurryProduced")
                        == feeding.state().amount(3)
                                + vatAmount(vat, ProductFluids.FEEDING_SLURRY.get())
                                + vat.metrics().total("feedingConsumed"),
                "Feeding slurry conservation failed");
        require(
                culture.state().metrics().total("culture_slurryProduced")
                        == culture.state().amount(2)
                                + vatAmount(vat, ProductFluids.CULTURE_SLURRY.get())
                                + vat.medium().getFluidAmount(),
                "Culture slurry conservation failed");
        require(
                vat.metrics().total(MachineMetrics.WASTE)
                        == vatAmount(vat, ProductFluids.WASTE.get())
                                + feeding.state().amount(1)
                                + feeding.state().metrics().total("wasteConsumed"),
                "Waste boundary conservation failed");
        int ratio = net.impactvector.mobvats.gamecontent.fluid.ExperienceFluid.ratio();
        long producedCulture = culture.state().metrics().total("culture_slurryProduced");
        require(producedCulture > 0 && producedCulture % 1000 == 0, "Culture production was not in 1000 mB batches");
        require(
                culture.state().metrics().total("fluidConsumed") == producedCulture / 1000 * 1250,
                "Culture batches did not consume 1000 mB water + 250 mB XP");
        // Whole-XP metrics truncate fractional points; conserve actual recipe fluid volumes.
        require(
                vat.metrics().total(MachineMetrics.XP) * ratio
                        == vatAmount(vat, ProductFluids.EXPERIENCE.get())
                                + culture.state().amount(4)
                                + (producedCulture / 1000) * 250,
                "XP boundary conservation failed (250 mB per culture batch)");
        var cow = ResourceLocation.withDefaultNamespace("cow");
        var rule = CultureProfiles.resolve(cow, vat.medium().cleanPrototype());
        require(
                rule != null
                        && rule.required() != null
                        && rule.matches(vat.medium().cleanPrototype()),
                "Produced culture did not qualify for compound enrichment");
        require(vat.medium().frequency(cow) > 0.5, "Produced culture frequency did not exceed dry baseline");
        require(
                feeding.getBoundingBox().getMax().getX()
                                < vat.getBoundingBox().getMin().getX()
                        && culture.getBoundingBox().getMin().getX()
                                > vat.getBoundingBox().getMax().getX(),
                "Structural cross assembly");
        report.append(
                "PASS: Boundary circulation — Vat waste/XP consumed by service Mixers; returned feeding slurry consumed; "
                        + vat.medium().getFluidAmount()
                        + " mB culture reached medium. Resource balances conserved; independent structures retained.\n\n");
        report.append("PASS: P-VAT | " + VAT.toShortString() + " | " + width + " x " + height + " x " + depth
                + " | four active cow assemblies; four serviced; " + drops
                + " chest items; 12 unique swords conserved\n\n");
        require(
                mediumUsed,
                "Returned culture medium never supported actual juvenile growth; resolved cow medium frequency="
                        + vat.medium().frequency(cow));
        report.append(
                "PASS: Verified end-to-end — 250 mB XP per culture batch; produced compound qualified for enrichment; returned culture supported actual juvenile growth; cow medium frequency="
                        + vat.medium().frequency(cow) + " (dry baseline 0.5).\n\n");
        report.append("## Four-Cow Production Vat\n\nDimensions: 9×6×9\nFormed: PASS\nActive specimens: 4/4\n\n");
        for (var head : HEADS.stream()
                .sorted(Comparator.<BlockPos>comparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()))
                .toList()) {
            var local = head.subtract(VAT);
            var name = (local.getZ() == 2 ? "N" : "S") + (local.getX() == 2 ? "W" : "E");
            var tool = TOOLS.stream()
                    .filter(p -> vat.getTopology().actors().stream()
                            .anyMatch(r -> r.actor().equals(p)
                                    && r.head().equals(head)
                                    && r.use().equals(PassiveUses.HARVEST)))
                    .findFirst()
                    .orElseThrow();
            report.append("Cow " + name + ": happiness 100% / yield " + vat.yieldMultiplier(head) + "x / frequency "
                    + vat.frequencyMultiplier(head) + "x / assigned tool " + tool.toShortString() + "\n\n");
        }
        report.append("Shared feed: feeding slurry at local (4,1,4)\nFeed source: fluid; " + STARTING_SLURRY
                + " mB initial reserve; zero wheat consumed; " + returnedConsumed
                + " mB returned slurry consumed\nCulture: " + vat.medium().cleanPrototype() + " / composition "
                + vat.medium().cleanPrototype().getComponents() + " / fill "
                + vat.medium().getFluidAmount() + " mB / enriched / frequency "
                + vat.medium().frequency(cow) + "x\n\n");
        report.append("Swords: supplied " + SWORDS + " / consumed 0 / remaining " + SWORDS + "; durability used "
                + vat.metrics().total(MachineMetrics.DURABILITY) + "\nDrops: produced "
                + vat.metrics().total(MachineMetrics.HARVEST_ITEMS) + " / collected " + drops + "\nWaste: produced "
                + vat.metrics().total(MachineMetrics.WASTE) + " / transferred "
                + (feeding.state().amount(1) + feeding.state().metrics().total("wasteConsumed"))
                + " mB\nXP fluid: produced " + vat.metrics().total(MachineMetrics.XP) * ratio + " / transferred "
                + (culture.state().amount(4) + producedCulture / 1000 * 250) + " mB\nFeeding slurry: produced "
                + feeding.state().metrics().total("feeding_slurryProduced") + " / returned "
                + (feeding.state().metrics().total("feeding_slurryProduced")
                        - feeding.state().amount(3)) + " / consumed "
                + vat.metrics().total("feedingConsumed") + " mB\nCulture: produced " + producedCulture + " / returned "
                + (producedCulture - culture.state().amount(2)) + " / applied "
                + vat.medium().getFluidAmount() + " mB\n\n");
    }

    private static long vatAmount(MultiblockVat vat, net.minecraft.world.level.material.Fluid fluid) {
        return vat.storage().connected(sharedFeed).stream()
                .filter(t -> t.getFluid().is(fluid))
                .mapToLong(t -> t.getFluidAmount())
                .sum();
    }

    private static long startupRemaining(MultiblockVat vat) {
        var seed = new FluidStack(ProductFluids.FEEDING_SLURRY.get(), 1);
        return vat.storage().connected(sharedFeed).stream()
                .filter(t -> FluidStack.isSameFluidSameComponents(t.getFluid(), seed))
                .mapToLong(t -> t.getFluidAmount())
                .sum();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private ProductionWorldFixtures() {}
}
