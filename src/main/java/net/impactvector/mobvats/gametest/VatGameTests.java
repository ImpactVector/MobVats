package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDescriptor;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatRenderDetail;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class VatGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    private static BlockPos headPos(int width) {
        return ORIGIN.offset(width / 2, 2, width / 2);
    }

    private static VatHeadEntity head(GameTestHelper h, int width) {
        return (VatHeadEntity) h.getBlockEntity(headPos(width));
    }

    private static boolean formed(GameTestHelper h, int width) {
        return head(h, width).isMachineAssembled();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void renderDetailThresholds(GameTestHelper h) {
        double[] distances = {0, 32, 32.001, 64, 64.001, 96, 96.001, 512};
        var expected = new VatRenderDetail[] {
            VatRenderDetail.FULL,
            VatRenderDetail.FULL,
            VatRenderDetail.REDUCED,
            VatRenderDetail.REDUCED,
            VatRenderDetail.MINIMAL,
            VatRenderDetail.MINIMAL,
            VatRenderDetail.CULLED,
            VatRenderDetail.CULLED
        };
        for (int i = 0; i < distances.length; i++)
            h.assertTrue(
                    VatRenderDetail.select(distances[i] * distances[i], 32, 64, 96) == expected[i],
                    "Render tier changed at the wrong distance: " + distances[i]);
        var normalized = new VatRenderDetail.Distances(96, 32, 64);
        h.assertTrue(
                normalized.full() == 32 && normalized.reduced() == 32 && normalized.cull() == 64,
                "Misordered detail distances must respect the configured cull limit");
        h.assertTrue(
                VatRenderDetail.select(32 * 32, 96, 32, 64) == VatRenderDetail.FULL
                        && VatRenderDetail.select(33 * 33, 96, 32, 64) == VatRenderDetail.MINIMAL
                        && VatRenderDetail.select(65 * 65, 96, 32, 64) == VatRenderDetail.CULLED,
                "Equal normalized thresholds must skip empty tiers and honor cullDistance");
        var clamped = new VatRenderDetail.Distances(Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
        h.assertTrue(
                clamped.full() == 8 && clamped.reduced() == 8 && clamped.cull() == 512,
                "Extreme render distances escaped the 8..512 bounds");
        h.assertTrue(
                VatRenderDetail.select(512 * 512, 8, 8, 512) == VatRenderDetail.MINIMAL
                        && VatRenderDetail.select(513 * 513, 8, 8, 512) == VatRenderDetail.CULLED,
                "Maximum configured distance did not preserve its inclusive boundary");
        h.assertTrue(
                VatRenderDetail.select(Double.NaN, 32, 64, 96) == VatRenderDetail.CULLED
                        && VatRenderDetail.select(Double.POSITIVE_INFINITY, 32, 64, 96) == VatRenderDetail.CULLED,
                "Invalid camera distances should fail closed without crashing");
        var bounds = new AABB(-12, 8, 23, 4, 14, 39);
        h.assertTrue(
                VatRenderDetail.distanceSquared(bounds, new Vec3(-4, 10, 31)) == 0
                        && VatRenderDetail.distanceSquared(bounds, new Vec3(4, 14, 39)) == 0,
                "Camera inside/on the vat must have zero surface distance");
        h.assertTrue(
                VatRenderDetail.distanceSquared(bounds, new Vec3(36, 11, 31)) == 32 * 32
                        && VatRenderDetail.distanceSquared(bounds, new Vec3(-15, 4, 23)) == 25
                        && VatRenderDetail.distanceSquared(bounds, new Vec3(7, 18, 51)) == 169,
                "Nearest-point distance must handle faces, edges and corners");
        h.assertTrue(
                VatRenderDetail.select(VatRenderDetail.distanceSquared(bounds, new Vec3(100, 11, 31)), 32, 64, 96)
                        == VatRenderDetail.MINIMAL,
                "Far vat face must remain visible at cullDistance even when its center/Head is farther away");
        h.assertTrue(
                VatRenderDetail.FULL.animates()
                        && VatRenderDetail.FULL.rendersStraps()
                        && !VatRenderDetail.REDUCED.animates()
                        && VatRenderDetail.REDUCED.rendersStraps()
                        && !VatRenderDetail.MINIMAL.animates()
                        && !VatRenderDetail.MINIMAL.rendersStraps()
                        && !VatRenderDetail.CULLED.animates()
                        && !VatRenderDetail.CULLED.rendersStraps(),
                "Tier feature permissions are inconsistent");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void portsCapabilitiesAndTopology(GameTestHelper h) {
        build(h, 5);
        var inputPos = ORIGIN.offset(1, 1, 4);
        var outputPos = ORIGIN.offset(3, 1, 4);
        var fluidPos = ORIGIN.offset(0, 1, 2);
        var fluidOutPos = ORIGIN.offset(4, 1, 2);
        var toolPos = ORIGIN.offset(2, 1, 4);
        var blockedPos = ORIGIN.offset(1, 2, 4);
        h.setBlock(inputPos, Content.state(VatPartType.ItemPort));
        h.setBlock(outputPos, Content.state(VatPartType.ItemPort));
        h.setBlock(fluidPos, Content.state(VatPartType.FluidPort));
        h.setBlock(fluidOutPos, Content.state(VatPartType.FluidPort));
        h.setBlock(toolPos, Content.state(VatPartType.ToolPort));
        h.setBlock(blockedPos, Content.state(VatPartType.ToolPort));
        var contactPos = ORIGIN.offset(2, 1, 0);
        h.setBlock(contactPos, Content.state(VatPartType.ItemPort));
        var output = (VatItemPortEntity) h.getBlockEntity(outputPos);
        output.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        var fluidOut = (VatFluidPortEntity) h.getBlockEntity(fluidOutPos);
        fluidOut.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        var tool = (VatToolPortEntity) h.getBlockEntity(toolPos);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.useBlock(toolPos, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
        h.useBlock(blockedPos, player);
        h.assertTrue(tool.heldItem().is(Items.IRON_SWORD), "Tool world interaction did not mount item");
        head(h, 5).encode(ItemEssence.COW);
        Slice11Fixtures.vatStorage(h, ORIGIN, 5, 5);
        h.runAtTickTime(30, () -> {
            h.assertTrue(formed(h, 5), "Ports must form as optional legal shell parts");
            var controller = head(h, 5).getMultiblockController().orElseThrow();
            var topology = controller.getTopology();
            h.assertTrue(
                    topology.itemInputs().contains(h.absolutePos(inputPos))
                            && topology.itemOutputs().contains(h.absolutePos(outputPos))
                            && topology.fluidInputs().contains(h.absolutePos(fluidPos))
                            && topology.fluidOutputs().contains(h.absolutePos(fluidOutPos)),
                    "Port direction subsets not compiled");
            h.assertTrue(
                    topology.itemContacts().isEmpty(),
                    "Ordinary Item Input physical contact was not compiled for future feeding");
            h.assertTrue(
                    topology.actors().stream().anyMatch(r -> r.actor().equals(h.absolutePos(toolPos)))
                            && topology.actors().stream()
                                    .filter(r -> r.actor().equals(h.absolutePos(toolPos)))
                                    .findFirst()
                                    .orElseThrow()
                                    .itemInputs()
                                    .equals(java.util.List.of(h.absolutePos(inputPos)))
                            && topology.actors().stream()
                                    .filter(r -> r.actor().equals(h.absolutePos(toolPos)))
                                    .findFirst()
                                    .orElseThrow()
                                    .itemOutputs()
                                    .equals(java.util.List.of(h.absolutePos(outputPos))),
                    "Cardinal tool assignment or adjacent IO relationships incorrect");
            var items = h.getLevel()
                    .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            h.absolutePos(inputPos),
                            Direction.SOUTH);
            h.assertTrue(
                    items != null
                            && items.insertItem(0, new ItemStack(Items.WHEAT, 3), false)
                                    .isEmpty()
                            && items.extractItem(0, 1, false).isEmpty(),
                    "Input staging capability policy incorrect");
            output.staging().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            var out = h.getLevel()
                    .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            h.absolutePos(outputPos),
                            Direction.SOUTH);
            h.assertTrue(
                    out != null
                            && out.extractItem(0, 1, false).is(Items.IRON_INGOT)
                            && out.insertItem(0, new ItemStack(Items.WHEAT), false)
                                    .is(Items.WHEAT),
                    "Output staging capability policy incorrect");
            var input = (VatItemPortEntity) h.getBlockEntity(inputPos);
            long revision = controller.getTopologyRevision();
            var menu = new net.impactvector.mobvats.gamecontent.multiblock.common.MachinePortMenu(
                    1, player.getInventory(), input);
            player.setPos(
                    input.getBlockPos().getX() + .5,
                    input.getBlockPos().getY(),
                    input.getBlockPos().getZ() + .5);
            h.assertTrue(
                    menu.clickMenuButton(player, 0)
                            && input.getIoDirection().isOutput()
                            && controller.getTopologyRevision() > revision,
                    "Server GUI mode control did not recompile topology");
            var saved = input.saveWithFullMetadata(h.getLevel().registryAccess());
            input.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Input);
            input.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(
                    input.getIoDirection().isOutput()
                            && input.staging().getStackInSlot(0).getCount() == 3
                            && controller.getTopology().itemOutputs().contains(input.getBlockPos()),
                    "Item direction/inventory engine persistence failed");
            var fluid = h.getLevel()
                    .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            h.absolutePos(fluidPos),
                            Direction.WEST);
            var fluidOutput = h.getLevel()
                    .getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            h.absolutePos(fluidOutPos),
                            Direction.EAST);
            var action = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
            h.assertTrue(
                    fluid != null
                            && fluidOutput != null
                            && fluid.fill(
                                            new net.neoforged.neoforge.fluids.FluidStack(
                                                    net.neoforged.neoforge.common.NeoForgeMod.MILK.get(), 1000),
                                            action)
                                    == 1000
                            && fluid.drain(100, action).isEmpty()
                            && controller.milkAmount() == 1000
                            && fluidOutput.drain(250, action).getAmount() == 250,
                    "Fluid handler must delegate both ports to controller buffer with direction policy");
            var member = controller.storage().clusters().stream()
                    .filter(c -> c.tank().getFluid().is(net.neoforged.neoforge.common.NeoForgeMod.MILK.get()))
                    .findFirst()
                    .orElseThrow()
                    .members()
                    .getFirst();
            var memberSave = member.saveWithFullMetadata(h.getLevel().registryAccess());
            var fluidSave = controller.syncDataTo(
                    new CompoundTag(),
                    h.getLevel().registryAccess(),
                    it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason.FullSync);
            fluidOutput.drain(750, action);
            controller.syncDataFrom(
                    fluidSave,
                    h.getLevel().registryAccess(),
                    it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason.FullSync);
            h.assertTrue(controller.milkAmount() == 0, "Controller reload duplicated member-owned fluids");
            member.loadWithComponents(memberSave, h.getLevel().registryAccess());
            h.assertTrue(controller.milkAmount() == 750, "Physical member persistence failed");
            var fluidEntity = (VatFluidPortEntity) h.getBlockEntity(fluidPos);
            fluidEntity.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
            var fs = fluidEntity.saveWithFullMetadata(h.getLevel().registryAccess());
            fluidEntity.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Input);
            fluidEntity.loadWithComponents(fs, h.getLevel().registryAccess());
            h.assertTrue(fluidEntity.getIoDirection().isOutput(), "Fluid direction persistence failed");
            // Generic manual replacement returns the old item and consumes one new item.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 2));
            h.useBlock(toolPos, player);
            h.assertTrue(
                    tool.heldItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1,
                    "Tool replacement must mount one generic item");
            var heldSave = tool.saveWithFullMetadata(h.getLevel().registryAccess());
            tool.loadWithComponents(heldSave, h.getLevel().registryAccess());
            h.assertTrue(tool.heldItem().is(Items.BUCKET), "Held ItemStack persistence failed");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.useBlock(toolPos, player);
            h.assertTrue(
                    tool.heldItem().isEmpty()
                            && controller.getTopology().actors().stream()
                                    .noneMatch(r -> r.actor().equals(tool.getBlockPos()))
                            && controller.getTopology().toolLoading().stream()
                                    .anyMatch(a -> a.actor().equals(tool.getBlockPos())),
                    "Manual removal must recompile process acquisition routes");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            h.useBlock(toolPos, player);
            var assembly = controller.getAssemblies().getFirst();
            h.setBlock(ORIGIN.offset(2, 1, 3), Blocks.STONE);
            h.assertTrue(
                    !VatTopologyCompiler.targets(
                            h.getLevel(),
                            tool.getBlockPos(),
                            controller.getBoundingBox().getMin(),
                            controller.getBoundingBox().getMax(),
                            assembly,
                            controller.getAssemblies()),
                    "Tool LOS crossed a solid obstacle");
            h.setBlock(ORIGIN.offset(2, 1, 3), Blocks.AIR);
            h.setBlock(ORIGIN.offset(0, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(70, () -> {
            var controller = head(h, 5).getMultiblockController().orElseThrow();
            h.assertTrue(
                    !formed(h, 5) && controller.getTopology().equals(VatTopology.EMPTY),
                    "Broken vat retained executable topology");
            var cap = ((VatFluidPortEntity) h.getBlockEntity(fluidOutPos)).externalFluids();
            h.assertTrue(
                    cap.drain(100, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
                            .isEmpty(),
                    "Broken fluid port retained controller access");
            h.setBlock(ORIGIN.offset(0, 3, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(110, () -> {
            h.assertTrue(
                    formed(h, 5)
                            && head(h, 5)
                                            .getMultiblockController()
                                            .orElseThrow()
                                            .getTopology()
                                            .actors()
                                            .size()
                                    == 1,
                    "Reformation did not restore active tool topology");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void controllerFluidAssimilation(GameTestHelper h) {
        class Probe extends MultiblockVat {
            Probe() {
                super(h.getLevel());
            }

            void merge(MultiblockVat other) {
                onAssimilate(other);
            }
        }
        var survivor = new Probe();
        var source = new Probe();
        var tank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000);
        tank.fill(
                new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 750),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var data = new CompoundTag();
        data.put("fluidBuffer", tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
        var reason = it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason.FullSync;
        source.syncDataFrom(data, h.getLevel().registryAccess(), reason);
        survivor.merge(source);
        var saved = survivor.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), reason);
        tank.readFromNBT(
                h.getLevel().registryAccess(),
                saved.getCompound("products")
                        .getCompound("buffers")
                        .getCompound("mobvats:milk")
                        .getCompound("tank"));
        h.assertTrue(
                tank.getFluidAmount() == 750, "An empty controller fragment must preserve the absorbed fluid store");
        var second = new Probe();
        second.syncDataFrom(data, h.getLevel().registryAccess(), reason);
        survivor.merge(second);
        saved = survivor.syncDataTo(new CompoundTag(), h.getLevel().registryAccess(), reason);
        tank.readFromNBT(
                h.getLevel().registryAccess(),
                saved.getCompound("products")
                        .getCompound("buffers")
                        .getCompound("mobvats:milk")
                        .getCompound("tank"));
        h.assertTrue(tank.getFluidAmount() == 1500, "Compatible controller stores must merge within capacity");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void portWorldMenuRouting(GameTestHelper h) {
        var itemPos = ORIGIN.offset(1, 1, 1);
        var fluidPos = ORIGIN.offset(3, 1, 1);
        h.setBlock(itemPos, Content.state(VatPartType.ItemPort));
        h.setBlock(fluidPos, Content.state(VatPartType.FluidPort));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (var pos : java.util.List.of(itemPos, fluidPos))
            for (var stack : java.util.List.of(new ItemStack(Items.WHEAT), ItemStack.EMPTY)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var absolute = h.absolutePos(pos);
                var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
                h.assertTrue(
                        h.getLevel()
                                        .getBlockState(absolute)
                                        .useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, hit)
                                == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                        "Port clicks must reach the GUI opening path");
                h.useBlock(pos, player);
                var port = (AbstractVatPortEntity) h.getBlockEntity(pos);
                h.assertTrue(
                        port.createMenu(1, player.getInventory(), player)
                                        instanceof
                                        net.impactvector.mobvats.gamecontent.multiblock.common.MachinePortMenu menu
                                && menu.port == port
                                && port.getIoDirection().isInput(),
                        "Menu provider must bind the clicked port without toggling mode");
            }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void minimumManualToolVat(GameTestHelper h) {
        build(h, 3);
        var pos = ORIGIN.offset(1, 1, 2);
        h.setBlock(pos, Content.state(VatPartType.ToolPort));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.useBlock(pos, player);
        head(h, 3).encode(ItemEssence.COW);
        h.runAtTickTime(30, () -> {
            var c = head(h, 3).getMultiblockController().orElseThrow();
            h.assertTrue(
                    formed(h, 3)
                            && c.getTopology().actors().size() == 2
                            && c.getTopology().itemInputs().isEmpty()
                            && c.getTopology().itemOutputs().isEmpty(),
                    "Minimum vat must support a manual Tool Port without automated IO ports");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void itemPortHoppers(GameTestHelper h) {
        var pos = ORIGIN.offset(3, 2, 3);
        h.setBlock(pos, Content.state(VatPartType.ItemPort));
        h.setBlock(pos.above(), Blocks.HOPPER);
        h.setBlock(pos.below(), Blocks.HOPPER);
        var upper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(pos.above());
        upper.setItem(0, new ItemStack(Items.WHEAT, 3));
        var port = (VatItemPortEntity) h.getBlockEntity(pos);
        h.runAtTickTime(40, () -> {
            var lower = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(pos.below());
            h.assertTrue(
                    port.staging().getStackInSlot(0).getCount() == 3 && lower.isEmpty(),
                    "Hopper input or input extraction policy failed");
            port.setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection.Output);
        });
        h.runAtTickTime(90, () -> {
            var lower = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(pos.below());
            h.assertTrue(
                    port.staging().getStackInSlot(0).isEmpty()
                            && lower.getItem(0).getCount() == 3,
                    "Hopper could not extract Output staging items");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void topologyPriorityAndPositions(GameTestHelper h) {
        var head = new BlockPos(4, 2, 4);
        var low = new BlockPos(1, 1, 4);
        var high = new BlockPos(7, 1, 4);
        var candidates = java.util.List.of(
                new VatTopologyCompiler.Claim(low, head, "use", 100),
                new VatTopologyCompiler.Claim(high, head, "use", 200),
                new VatTopologyCompiler.Claim(low, head, "other", 100));
        var accepted = VatTopologyCompiler.claim(candidates);
        h.assertTrue(
                accepted.size() == 2 && accepted.getFirst().actor().equals(high),
                "Priority did not discard lower compatible alternatives for the same use");
        var equal = VatTopologyCompiler.claim(java.util.List.of(
                new VatTopologyCompiler.Claim(high, head, "use", 100),
                new VatTopologyCompiler.Claim(low, head, "use", 100)));
        h.assertTrue(
                equal.size() == 1 && equal.getFirst().actor().equals(low),
                "Equal priority must have stable coordinate ordering");
        h.assertTrue(
                !VatPartType.ToolPort.allows(it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition.TopFace, false)
                        && !VatPartType.ToolPort.allows(
                                it.zerono.mods.zerocore.lib.multiblock.cuboid.PartPosition.BottomFace, false),
                "Tool ports must reject top/bottom positions");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void nonControllerWorldClicksPass(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = ORIGIN.offset(1, 1, 1);
        for (var type : VatPartType.values()) {
            if (type == VatPartType.Controller
                    || type == VatPartType.ItemPort
                    || type == VatPartType.FluidPort
                    || type == VatPartType.ToolPort
                    || type.ordinal() >= VatPartType.PullPump.ordinal()) continue;
            h.setBlock(pos, Content.state(type));
            var absolute = h.absolutePos(pos);
            var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
            for (boolean crouching : new boolean[] {false, true}) {
                player.setShiftKeyDown(crouching);
                h.assertTrue(
                        h.getLevel().getBlockState(absolute).useWithoutItem(h.getLevel(), player, hit)
                                == net.minecraft.world.InteractionResult.PASS,
                        "Non-controller click consumed the interaction: " + type + ", crouching=" + crouching);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void controllerPlacementFacesPlayerForEveryTier(GameTestHelper h) {
        var pos = ORIGIN.offset(1, 1, 1);
        h.setBlock(pos.below(), Blocks.STONE);
        var player = h.makeMockPlayer(GameType.CREATIVE);
        player.setShiftKeyDown(true);
        for (var tier : VatTier.values())
            for (var direction : Direction.Plane.HORIZONTAL) {
                h.setBlock(pos, Blocks.AIR);
                player.setYRot(direction.toYRot());
                var block = Content.block(tier, VatPartType.Controller);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
                var clicked = h.absolutePos(pos.below());
                player.getMainHandItem()
                        .useOn(new UseOnContext(
                                player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
                h.assertBlockPresent(block, pos);
                var state = h.getBlockState(pos);
                var facing = direction.getOpposite();
                h.assertTrue(
                        state.getValue(VatControllerBlock.FACING) == facing,
                        "Controller did not face player: " + tier + ", " + direction);
                h.assertTrue(
                        state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)
                                        .getValue(VatControllerBlock.FACING)
                                == facing.getClockWise(),
                        "Controller structure rotation lost facing");
                h.assertTrue(
                        state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT)
                                        .getValue(VatControllerBlock.FACING)
                                == (facing.getAxis() == Direction.Axis.Z ? facing.getOpposite() : facing),
                        "Controller structure mirror lost facing");
            }
        h.succeed();
    }

    private static void placeFacingHead(GameTestHelper h, Direction playerFacing) {
        var pos = headPos(3);
        h.setBlock(pos, Blocks.AIR);
        var player = h.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(playerFacing.toYRot());
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.block(VatPartType.Head)));
        var clicked = h.absolutePos(pos.below());
        player.getMainHandItem()
                .useOn(new UseOnContext(
                        player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
        h.assertBlockPresent(Content.block(VatPartType.Head), pos);
        h.assertTrue(
                head(h, 3).getSpecimenFacing() == playerFacing.getOpposite(), "Placed Head did not face the player");
        var geometry =
                net.impactvector.mobvats.gamecontent.mob.SimulacrumGeometry.legacy(ItemEssence.COW, playerFacing);
        var specimen = new net.impactvector.mobvats.gamecontent.mob.CapturedSpecimen(
                ItemEssence.COW, geometry, new CompoundTag());
        var essence = new ItemStack(Content.ESSENCE.get());
        essence.set(DataComponents.CUSTOM_DATA, CustomData.of(specimen.save()));
        player.setItemInHand(InteractionHand.MAIN_HAND, essence);
        var absolute = h.absolutePos(pos);
        essence.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        h.assertTrue(ItemEssence.COW.equals(head(h, 3).getIdentity()), "Essence did not encode the placed Head");
        h.assertTrue(
                head(h, 3).getBlockState().getValue(VatHeadBlock.FACING) == playerFacing.getOpposite()
                        && head(h, 3).geometry().equals(geometry.withFacing(playerFacing.getOpposite())),
                "Captured essence must preserve placed Head facing and captured dimensions");
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void headPlacementAndSpecimenFacing(GameTestHelper h) {
        build(h, 3);
        var directions = new Direction[] {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        placeFacingHead(h, directions[0]);
        for (int i = 0; i < directions.length; i++) {
            final int index = i;
            h.runAtTickTime((i + 1) * 30, () -> {
                var head = head(h, 3);
                var expected = directions[index].getOpposite();
                h.assertTrue(
                        formed(h, 3) && head.getSpecimenFacing() == expected,
                        "Facing changed during formation or redirected the world-down scan");
                var descriptor =
                        VatRenderDescriptor.from(head.getMultiblockController().orElseThrow(), head);
                h.assertTrue(
                        descriptor.specimenYaw() == expected.toYRot(),
                        "Formed specimen did not inherit placed Head yaw");
                var saved = net.minecraft.nbt.NbtUtils.writeBlockState(head.getBlockState());
                var restored = net.minecraft.nbt.NbtUtils.readBlockState(
                        h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                        saved);
                h.assertTrue(
                        restored.getValue(VatHeadBlock.FACING) == expected, "Block-state persistence lost Head facing");
                if (index + 1 < directions.length) placeFacingHead(h, directions[index + 1]);
                else h.setBlock(ORIGIN.offset(0, 3, 0), Blocks.AIR);
            });
        }
        h.runAtTickTime(150, () -> {
            h.assertTrue(
                    !formed(h, 3) && head(h, 3).getSpecimenFacing() == Direction.WEST,
                    "Disassembly lost placed Head facing");
            h.setBlock(ORIGIN.offset(0, 3, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(180, () -> {
            var head = head(h, 3);
            h.assertTrue(
                    formed(h, 3)
                            && VatRenderDescriptor.from(
                                                    head.getMultiblockController()
                                                            .orElseThrow(),
                                                    head)
                                            .specimenYaw()
                                    == Direction.WEST.toYRot(),
                    "Reformation reset the specimen facing");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void functionalWallPanels(GameTestHelper h) {
        build(h, 5);
        var controller = (VatControllerEntity) h.getBlockEntity(ORIGIN.offset(1, 1, 0));
        var harness = (VatHarnessEntity) h.getBlockEntity(ORIGIN.offset(2, 3, 2));
        var drain = (VatSumpEntity) h.getBlockEntity(ORIGIN.offset(2, 0, 2));
        for (var part : new AbstractVatEntity[] {controller, harness, drain})
            h.assertTrue(
                    part.getPartType().usesWallPanel()
                            && part.getWallPanelState().equals(VatWallPanelState.UNFORMED),
                    "Unformed functional part must retain its construction model");
        head(h, 5).encode(ItemEssence.COW);
        h.runAtTickTime(30, () -> {
            h.assertTrue(formed(h, 5), "Panel test vat failed to form");
            var parts = new AbstractVatEntity[] {controller, harness, drain};
            var directions = new Direction[] {Direction.NORTH, Direction.UP, Direction.DOWN};
            for (int i = 0; i < parts.length; i++) {
                var state = parts[i].getWallPanelState();
                var face = directions[i];
                int innerPlane = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 3 : 13;
                var geometry = VatCasingGeometry.build(state.shell());
                h.assertTrue(
                        state.shell().outward(face)
                                && geometry.stream()
                                                .filter(p ->
                                                        p.normal() == face.getOpposite() && p.plane() == innerPlane)
                                                .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                                                .sum()
                                        == 256,
                        "Functional component must be a full 16x16 panel exactly 3px thick");
                h.assertTrue(
                        state.blockFaceFor(face) == face && state.blockFaceFor(face.getOpposite()) == face,
                        "Panel broad faces must share the outward block-face texture");
                h.assertTrue(
                        geometry.stream()
                                        .filter(state::visible)
                                        .map(VatCasingGeometry.Patch::normal)
                                        .distinct()
                                        .count()
                                == (i == 0 ? 5 : 2),
                        "Hidden casing-adjacent thickness edges were rendered");
            }
            var state = controller.getWallPanelState();
            int expected =
                    (1 << Direction.UP.ordinal()) | (1 << Direction.WEST.ordinal()) | (1 << Direction.EAST.ordinal());
            h.assertTrue(
                    state.glassEdges() == expected
                            && harness.getWallPanelState().glassEdges() == 0
                            && drain.getWallPanelState().glassEdges() == 0,
                    "Glass adjacency did not select exposed lip edges");
            h.setBlock(ORIGIN.offset(0, 1, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(
                    formed(h, 5) && (controller.getWallPanelState().glassEdges() & 1 << Direction.WEST.ordinal()) == 0,
                    "Replacing adjacent glass with casing did not hide the panel edge");
            h.setBlock(ORIGIN.offset(0, 3, 0), Blocks.AIR);
        });
        h.runAtTickTime(90, () -> {
            for (var part : new AbstractVatEntity[] {controller, harness, drain})
                h.assertTrue(
                        part.getWallPanelState().equals(VatWallPanelState.UNFORMED),
                        "Broken vat retained a formed wall panel");
            h.setBlock(ORIGIN.offset(0, 3, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(
                    formed(h, 5)
                            && controller.getWallPanelState().shell().formed()
                            && harness.getWallPanelState().shell().formed()
                            && drain.getWallPanelState().shell().formed(),
                    "Repair did not restore functional wall panels");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void casingRimsAndLifecycle(GameTestHelper h) {
        build(h, 7, 7, 6);
        var anchor = (VatHeadEntity) h.getBlockEntity(ORIGIN.offset(3, 4, 3));
        var floor = (VatCasingEntity) h.getBlockEntity(ORIGIN);
        h.assertTrue(floor.getCasingState().equals(VatCasingState.UNFORMED), "Unformed casing acquired bracket state");
        anchor.encode(ItemEssence.COW);
        h.runAtTickTime(30, () -> {
            h.assertTrue(anchor.isMachineAssembled(), "Casing test vat did not form");
            var state = floor.getCasingState();
            h.assertTrue(
                    state.outward(Direction.DOWN)
                            && state.outerEdges(Direction.DOWN) == (VatGlassState.LEFT | VatGlassState.BOTTOM),
                    "Floor corner did not acquire its complete-vat L rim");
            var patches = VatCasingGeometry.build(state);
            int hullArea = patches.stream()
                    .filter(p -> p.normal() == Direction.DOWN && p.plane() == 0)
                    .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                    .sum();
            int rimArea = patches.stream()
                    .filter(p -> p.normal() == Direction.UP && p.plane() == 16)
                    .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                    .sum();
            int innerArea = patches.stream()
                    .filter(p -> p.normal() == Direction.UP
                            && p.plane() == 16
                            && p.material() == VatCasingGeometry.Material.FACE)
                    .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                    .sum();
            h.assertTrue(
                    hullArea == 256 && rimArea == 87 && innerArea == 56,
                    "Exterior hull, 1px edge, or 3px L rim area is incorrect");
            h.assertTrue(
                    patches.stream().anyMatch(p -> p.normal() == Direction.UP && p.plane() == 16)
                            && patches.stream().anyMatch(p -> p.normal() == Direction.UP && p.plane() == 3),
                    "Base must have a recessed 3px floor and a flush perimeter");
            h.assertTrue(
                    patches.stream().anyMatch(p -> p.normal() == Direction.UP && p.edgeDirection() == Direction.NORTH)
                            && patches.stream()
                                    .anyMatch(p -> p.normal() == Direction.UP && p.edgeDirection() == Direction.WEST),
                    "Floor rim did not inherit its north/west outer edge directions");
            var center = ((VatCasingEntity) h.getBlockEntity(ORIGIN.offset(3, 0, 2))).getCasingState();
            h.assertTrue(
                    center.outerEdges(Direction.DOWN) == 0
                            && VatCasingGeometry.build(center).stream()
                                            .filter(p -> p.normal() == Direction.UP && p.plane() == 3)
                                            .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                                            .sum()
                                    == 256,
                    "Central floor must be a 3px shell without a raised rim");
            // The old min+1/max-1 selection produced the small floating rectangle.
            for (var size : new BlockPos[] {new BlockPos(3, 4, 3), new BlockPos(7, 6, 5)}) {
                var min = new BlockPos(-12, 8, 23);
                var max = min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
                for (int x = min.getX(); x <= max.getX(); x++)
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        var cap = new BlockPos(x, min.getY(), z);
                        boolean perimeter = x == min.getX() || x == max.getX() || z == min.getZ() || z == max.getZ();
                        var geometry = VatCasingGeometry.build(VatCasingState.from(cap, min, max));
                        h.assertTrue(
                                geometry.stream().anyMatch(p -> p.normal() == Direction.UP && p.plane() == 16)
                                        == perimeter,
                                "Channel must follow the glass footprint, including the minimum vat");
                        for (var patch : geometry)
                            if (patch.normal() == Direction.UP && patch.plane() == 16)
                                for (int u = patch.u0(); u < patch.u1(); u++)
                                    for (int v = patch.v0(); v < patch.v1(); v++) {
                                        int worldX = (x - min.getX()) * 16 + u;
                                        int worldZ = (z - min.getZ()) * 16 + v;
                                        h.assertTrue(
                                                worldX < 3
                                                        || worldX >= size.getX() * 16 - 3
                                                        || worldZ < 3
                                                        || worldZ >= size.getZ() * 16 - 3,
                                                "Lip is detached from the unchanged glass planes");
                                    }
                    }
            }
            var upper = ((VatCasingEntity) h.getBlockEntity(ORIGIN.offset(0, 5, 0))).getCasingState();
            h.assertTrue(
                    VatCasingGeometry.build(upper).stream()
                            .anyMatch(p -> p.material() == VatCasingGeometry.Material.INNER
                                    && p.normal() == Direction.DOWN
                                    && p.plane() == 13),
                    "Ceiling rim was not reflected toward the chamber");
            // All six isolated panels must be exactly 3px thick from the outside.
            for (var face : Direction.values()) {
                var panel = VatCasingGeometry.build(new VatCasingState(1 << face.ordinal(), 0));
                int inward = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 3 : 13;
                h.assertTrue(
                        panel.stream()
                                        .filter(p -> p.normal() == face.getOpposite()
                                                && p.plane() == inward
                                                && p.material() == VatCasingGeometry.Material.INNER)
                                        .mapToInt(p -> (p.u1() - p.u0()) * (p.v1() - p.v0()))
                                        .sum()
                                == 256,
                        "Shell panel lost its 3px inward surface on " + face);
            }
            var corner = ((VatCasingEntity) h.getBlockEntity(ORIGIN)).getCasingState();
            h.assertTrue(Integer.bitCount(corner.outwardFaces()) == 3, "Frame corner lost one hull direction");
            var cornerPatches = VatCasingGeometry.build(corner);
            h.assertTrue(
                    cornerPatches.stream()
                                    .anyMatch(p -> p.material() == VatCasingGeometry.Material.EDGE
                                            && p.edgeDirection() == Direction.WEST)
                            && cornerPatches.stream()
                                    .anyMatch(p -> p.material() == VatCasingGeometry.Material.EDGE
                                            && p.edgeDirection() == Direction.NORTH),
                    "Corner edge treatment did not follow world directions");
            // No formed casing surface may protrude into an adjacent glass cell.
            for (var pos : BlockPos.betweenClosed(ORIGIN, ORIGIN.offset(6, 5, 6)))
                if (h.getLevel().getBlockEntity(h.absolutePos(pos)) instanceof VatCasingEntity casing) {
                    var occupied = new java.util.HashSet<String>();
                    for (var patch : VatCasingGeometry.build(casing.getCasingState())) {
                        h.assertTrue(
                                patch.plane() >= 0
                                        && patch.plane() <= 16
                                        && patch.u0() >= 0
                                        && patch.u1() <= 16
                                        && patch.v0() >= 0
                                        && patch.v1() <= 16,
                                "Shell protruded outside its casing block");
                        for (int u = patch.u0(); u < patch.u1(); u++)
                            for (int v = patch.v0(); v < patch.v1(); v++) {
                                h.assertTrue(
                                        occupied.add(patch.normal() + ":" + patch.plane() + ":" + u + ":" + v),
                                        "Coplanar casing patches overlap");
                            }
                    }
                }
            h.setBlock(ORIGIN.offset(0, 5, 2), Blocks.AIR);
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(
                    !anchor.isMachineAssembled() && floor.getCasingState().equals(VatCasingState.UNFORMED),
                    "Broken vat retained formed casing");
            h.setBlock(ORIGIN.offset(0, 5, 2), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(110, () -> {
            h.assertTrue(
                    anchor.isMachineAssembled() && floor.getCasingState().formed(),
                    "Repair did not restore casing rim state");
            h.assertTrue(
                    h.getLevel()
                            .getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, h.getBounds())
                            .isEmpty(),
                    "Harness rendering created a server cow");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void bodyAnchorsAndHarnessCurves(GameTestHelper h) {
        var body = new AABB(10, 20, 30, 14, 26, 38);
        var definition = MobRenderDefinition.forEntity(ItemEssence.COW);
        var anchor = definition.bodyAnchor(body);
        h.assertTrue(
                body.contains(anchor) && anchor.x == 12 && anchor.z == 34 && Math.abs(anchor.y - 23.3) < 1.0e-8,
                "Default body anchor did not use center X/Z and 55 percent of height");
        var harness = new BlockPos(11, 28, 33);
        var curves = HarnessStraps.curves(harness, body, definition);
        var corners = HarnessStraps.corners(harness);
        h.assertTrue(
                corners.stream().allMatch(p -> p.y == harness.getY() + 13.0 / 16),
                "Straps must start at the thin harness panel's actual underside");
        h.assertTrue(
                curves.size() == 4 && new java.util.HashSet<>(corners).size() == 4, "Harness corners are not unique");
        for (int i = 0; i < 4; i++) {
            var points = curves.get(i).points();
            h.assertTrue(
                    points.size() == 17
                            && points.getFirst().equals(corners.get(i))
                            && points.getLast().equals(anchor),
                    "Harness curve endpoints/segmentation are incorrect");
            var straightMidpoint = corners.get(i).lerp(anchor, 0.5);
            h.assertTrue(
                    Math.abs(points.get(8).y - (straightMidpoint.y - HarnessStraps.SAG)) < 1.0e-8,
                    "Harness strap lost its restrained midpoint sag");
        }
        var identity = ResourceLocation.fromNamespaceAndPath("mobvats", "test_body_anchor");
        var override = new MobRenderDefinition(new Vec3(0.4, 0.6, 0.5));
        MobRenderDefinition.registerOverride(identity, override);
        h.assertTrue(
                MobRenderDefinition.forEntity(identity) == override
                        && body.contains(override.bodyAnchor(body))
                        && MobRenderDefinition.forEntity(ItemEssence.COW) == definition,
                "Entity anchor override leaked into the Cow definition");
        boolean rejected = false;
        try {
            new MobRenderDefinition(new Vec3(0.5, 1, 0.5));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "A body anchor on the external surface was accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void glassBoundsAndFormationLifecycle(GameTestHelper h) {
        build(h, 5, 5, 6);
        var anchor = (VatHeadEntity) h.getBlockEntity(ORIGIN.offset(2, 4, 2));
        var front = ORIGIN.offset(2, 2, 0);
        var edge = ORIGIN.offset(0, 2, 0);
        var roof = ORIGIN.offset(1, 5, 1);
        h.setBlock(roof, Content.state(VatPartType.Glass));
        h.assertTrue(
                !((VatGlassEntity) h.getBlockEntity(front)).getGlassState().formed(),
                "Unencoded vat used formed glass");
        anchor.encode(ItemEssence.COW);
        h.runAtTickTime(30, () -> {
            h.assertTrue(anchor.isMachineAssembled(), "Glass test vat did not form");
            var min = h.absolutePos(ORIGIN);
            var max = h.absolutePos(ORIGIN.offset(4, 5, 4));
            for (var face : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                var middle = switch (face) {
                    case NORTH -> ORIGIN.offset(2, 2, 0);
                    case SOUTH -> ORIGIN.offset(2, 2, 4);
                    case WEST -> ORIGIN.offset(0, 2, 2);
                    default -> ORIGIN.offset(4, 2, 2);
                };
                var state = ((VatGlassEntity) h.getBlockEntity(middle)).getGlassState();
                h.assertTrue(
                        state.hasFace(face) && state.edges(face) == 0,
                        "Middle glass acquired per-cell framing on " + face);
                h.assertTrue(
                        state.equals(VatGlassState.from(h.absolutePos(middle), min, max)),
                        "Mask ignored assembled bounds");
            }
            var corner = ((VatGlassEntity) h.getBlockEntity(edge)).getGlassState();
            h.assertTrue(
                    corner.hasFace(Direction.NORTH) && corner.hasFace(Direction.WEST),
                    "Vertical edge lost one outward plane");
            h.assertTrue(
                    corner.edges(Direction.NORTH) == VatGlassState.LEFT
                            && corner.edges(Direction.WEST) == VatGlassState.LEFT,
                    "Vertical edge perimeter masks are incorrect");
            var upper = VatGlassState.from(min.offset(2, 4, 0), min, max);
            var lower = VatGlassState.from(min.offset(2, 1, 0), min, max);
            h.assertTrue(
                    upper.edges(Direction.NORTH) == VatGlassState.TOP
                            && lower.edges(Direction.NORTH) == VatGlassState.BOTTOM,
                    "Casing rings did not bound the side-wall glass span");
            var roofState = ((VatGlassEntity) h.getBlockEntity(roof)).getGlassState();
            h.assertTrue(
                    roofState.hasFace(Direction.UP)
                            && roofState.edges(Direction.UP) == (VatGlassState.LEFT | VatGlassState.BOTTOM),
                    "Roof glass did not use the inset cap perimeter");
            // A functional/opaque interruption must not create a new border on adjacent glass.
            h.setBlock(front, Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(60, () -> {
            var adjacent = ((VatGlassEntity) h.getBlockEntity(ORIGIN.offset(3, 2, 0))).getGlassState();
            h.assertTrue(
                    anchor.isMachineAssembled() && adjacent.edges(Direction.NORTH) == 0,
                    "An opaque wall interruption introduced neighbor-connected framing");
            h.setBlock(ORIGIN.offset(0, 1, 2), Blocks.AIR);
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(!anchor.isMachineAssembled(), "Broken glass vat remained formed");
            h.assertTrue(
                    ((VatGlassEntity) h.getBlockEntity(edge)).getGlassState().equals(VatGlassState.UNFORMED),
                    "Broken vat retained formed glass state");
            h.setBlock(ORIGIN.offset(0, 1, 2), Content.state(VatPartType.Glass));
        });
        h.runAtTickTime(130, () -> {
            h.assertTrue(
                    anchor.isMachineAssembled()
                            && ((VatGlassEntity) h.getBlockEntity(edge))
                                    .getGlassState()
                                    .formed(),
                    "Repair did not restore formed glass");
            h.assertTrue(
                    h.getLevel()
                            .getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, h.getBounds())
                            .isEmpty(),
                    "Rendering changes created a server cow");
            h.succeed();
        });
    }

    static void build(GameTestHelper h, int width) {
        build(h, width, width, 4);
    }

    private static void build(GameTestHelper h, int width, int depth, int height) {
        MachineFixtures.vat(h::setBlock, ORIGIN, width, depth, height);
    }

    @GameTest(template = "empty", timeoutTicks = 1400)
    public static void cowLifecycleAndDrainSump(GameTestHelper h) {
        build(h, MachineFixtures.MINIMUM_VAT_WIDTH);
        head(h, 3).encode(ItemEssence.COW);
        h.runAtTickTime(20, () -> {
            h.assertTrue(formed(h, 3), "Minimum vat with vertical-edge glass did not form");
            h.assertTrue(head(h, 3).getMilkTicks() == 0, "Deprecated milk producer must remain inactive");
            h.setBlock(ORIGIN.offset(1, 3, 0), Content.state(VatPartType.Glass));
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(!formed(h, 3), "Horizontal frame glass must disassemble the vat");
            h.setBlock(ORIGIN.offset(1, 3, 0), Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(formed(h, 3), "Restoring casing did not reform the vat");
            h.setBlock(ORIGIN, Content.state(VatPartType.Glass));
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(!formed(h, 3), "Corner glass must be invalid even when ZeroCore2 classifies it FrameUpDown");
            h.setBlock(ORIGIN, Content.state(VatPartType.Casing));
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(formed(h, 3), "Corner repair did not reform the vat");
            h.setBlock(ORIGIN.offset(0, 1, 1), Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(!formed(h, 3), "Missing shell block did not disassemble the vat");
            int progress = head(h, 3).getMilkTicks();
            var saved = head(h, 3).saveWithFullMetadata(h.getLevel().registryAccess());
            h.setBlock(headPos(3), Blocks.AIR);
            h.setBlock(headPos(3), Content.state(VatPartType.Head));
            head(h, 3).loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(
                    ItemEssence.COW.equals(head(h, 3).getIdentity()),
                    "Encoded identity lost during engine NBT roundtrip");
            h.assertTrue(head(h, 3).getMilkTicks() == progress, "Milk progress lost during engine NBT roundtrip");
            h.setBlock(ORIGIN.offset(0, 1, 1), Content.state(VatPartType.Glass));
        });
        h.runAtTickTime(140, () -> h.assertTrue(formed(h, 3), "Reloaded head and repaired shell did not reform"));
        h.runAtTickTime(1320, () -> {
            h.assertTrue(!head(h, 3).hasMilk(), "Topology slice must not run the deprecated milk producer");
            h.assertTrue(
                    h.getLevel()
                            .getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, h.getBounds())
                            .isEmpty(),
                    "Vat created a server-side Cow");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            h.useBlock(ORIGIN.offset(1, 0, 1), player);
            h.assertTrue(player.getMainHandItem().is(Items.BUCKET), "Structural Drain must not extract milk");
            h.assertTrue(!head(h, 3).hasMilk(), "Drain duplicated milk");
            var saved = head(h, 3).saveWithFullMetadata(h.getLevel().registryAccess());
            head(h, 3).loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(!head(h, 3).hasMilk(), "Consumed milk returned after reload");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void captureEncodeAndLargerVat(GameTestHelper h) {
        build(h, MachineFixtures.LARGER_VAT_WIDTH);
        h.runAtTickTime(20, () -> {
            h.assertTrue(!formed(h, 5), "Physical shape alone supplied mob identity");
            var cow = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 9));
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
            var syringe = new ItemStack(Content.SYRINGE.get());
            Content.SYRINGE.get().interactLivingEntity(syringe, player, cow, InteractionHand.MAIN_HAND);
            ItemStack essence = ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var stack = player.getInventory().getItem(i);
                if (stack.is(Content.ESSENCE.get())) essence = stack;
                h.assertTrue(!stack.is(Items.GLASS_BOTTLE), "Essence capture did not consume bottle");
            }
            h.assertTrue(
                    ItemEssence.COW.equals(ItemEssence.identity(essence)),
                    "Syringe did not capture cow registry identity");
            player.setItemInHand(InteractionHand.MAIN_HAND, essence);
            h.useBlock(headPos(5), player);
            h.assertTrue(ItemEssence.COW.equals(head(h, 5).getIdentity()), "Essence interaction failed to encode head");
            h.assertTrue(essence.isEmpty(), "Encoding did not consume essence");
            cow.discard();
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(formed(h, 5), "Variable-size service-space vat did not form");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void invalidSubstrateRejected(GameTestHelper h) {
        build(h, 3);
        head(h, 3).encode(ItemEssence.COW);
        h.setBlock(headPos(3).below(), Blocks.AIR);
        h.runAtTickTime(30, () -> {
            h.assertTrue(!formed(h, 3), "Cow formed without required substrate");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void grassInteriorRejectsFormation(GameTestHelper h) {
        build(h, MachineFixtures.LARGER_VAT_WIDTH);
        head(h, 5).encode(ItemEssence.COW);
        MachineFixtures.grassInterior(h::setBlock, ORIGIN);
        h.runAtTickTime(35, () -> {
            h.assertTrue(!formed(h, 5), "Vat accepted grass block inside its service space");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rectangularTallVat(GameTestHelper h) {
        build(h, 3, 5, 6);
        var pos = ORIGIN.offset(1, 4, 2);
        ((VatHeadEntity) h.getBlockEntity(pos)).encode(ItemEssence.COW);
        h.runAtTickTime(30, () -> {
            var head = (VatHeadEntity) h.getBlockEntity(pos);
            h.assertTrue(head.isMachineAssembled(), "Rectangular 3x6x5 vat did not form");
            h.assertTrue(
                    head.getMultiblockController()
                                    .orElseThrow()
                                    .getAssemblies()
                                    .getFirst()
                                    .fleshCount()
                            == 1,
                    "Tall vat changed Head-owned substrate extent");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void minimumVatThroughPlayerPlacement(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.CREATIVE);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.setBlock(ORIGIN.offset(x, -1, z), Blocks.STONE);
        // Actual block-item placement with changing player orientation, bottom to top.
        // Click an adjacent face rather than bypassing BlockItem with setBlock.
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 3; x++)
                for (int z = 0; z < 3; z++) {
                    VatPartType type;
                    if (x == 1 && z == 1) {
                        type = switch (y) {
                            case 0 -> VatPartType.Sump;
                            case 1 -> VatPartType.Flesh;
                            case 2 -> VatPartType.Head;
                            default -> VatPartType.Harness;
                        };
                    } else if (x == 1 && y == 1 && z == 0) type = VatPartType.Controller;
                    else type = y == 0 || y == 3 ? VatPartType.Casing : VatPartType.Glass;
                    var pos = ORIGIN.offset(x, y, z);
                    player.setYRot((x + z + y) * 90);
                    player.setShiftKeyDown(true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.block(type)));
                    var absolute = h.absolutePos(pos.below());
                    // Vanilla sneak-placement bypasses the clicked block's interaction.
                    // GameTestHelper.useBlock does not implement that bypass, so invoke the item path.
                    player.getMainHandItem()
                            .useOn(new UseOnContext(
                                    player,
                                    InteractionHand.MAIN_HAND,
                                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
                    h.assertBlockPresent(Content.block(type), pos);
                    if (type == VatPartType.Harness || type == VatPartType.Sump) {
                        player.setItemInHand(
                                InteractionHand.MAIN_HAND, new ItemStack(Content.block(VatPartType.Casing)));
                        var support = h.absolutePos(pos);
                        player.getMainHandItem()
                                .useOn(new UseOnContext(
                                        player,
                                        InteractionHand.MAIN_HAND,
                                        new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false)));
                        h.assertTrue(
                                net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial.installed(
                                                h.getBlockState(pos))
                                        == net.impactvector.mobvats.gamecontent.multiblock.conduit.CladdingMaterial
                                                .VAT_T1_CASING,
                                "Player did not clad shell support");
                    }
                }
        player.setShiftKeyDown(false);
        var essence = new ItemStack(Content.ESSENCE.get());
        var identity = new CompoundTag();
        identity.putString("entity", ItemEssence.COW.toString());
        essence.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
        player.setItemInHand(InteractionHand.MAIN_HAND, essence);
        h.useBlock(headPos(3), player);
        h.runAtTickTime(30, () -> {
            h.assertTrue(formed(h, 3), "Player-placed minimum vat failed with different placement orientations");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void misalignedDrainAndExtraFleshFeedback(GameTestHelper h) {
        build(h, 5);
        head(h, 5).encode(ItemEssence.COW);
        var aligned = ORIGIN.offset(2, 0, 2);
        var misplaced = ORIGIN.offset(1, 0, 2);
        h.setBlock(aligned, Content.state(VatPartType.Casing));
        h.setBlock(misplaced, Content.state(VatPartType.Sump));
        h.runAtTickTime(30, () -> {
            var vat = head(h, 5).getMultiblockController().orElseThrow();
            h.assertTrue(
                    vat.isAssembled()
                            && vat.getAssemblies()
                                    .getFirst()
                                    .drain()
                                    .getBlockPos()
                                    .equals(h.absolutePos(misplaced)),
                    "Offset Drain was not selected");
            h.setBlock(misplaced, Content.state(VatPartType.Casing));
            h.setBlock(aligned, Content.state(VatPartType.Sump));
        });
        var extra = ORIGIN.offset(1, 1, 2);
        h.runAtTickTime(60, () -> {
            h.assertTrue(formed(h, 5), "Aligning drain did not repair the vat");
            h.setBlock(extra, Content.state(VatPartType.Flesh));
        });
        h.runAtTickTime(90, () -> {
            var vat = head(h, 5).getMultiblockController().orElseThrow();
            h.assertTrue(!vat.isAssembled(), "Extra flesh was accepted");
            var error = vat.getLastError().orElseThrow();
            h.assertTrue(
                    error.getPosition().equals(h.absolutePos(extra)), "Extra flesh error did not mark the extra block");
            h.assertTrue(
                    error.getChatMessage().getContents() instanceof TranslatableContents message
                            && message.getKey().equals("multiblock.mobvats.extra_flesh"),
                    "Extra flesh error used an ambiguous message");
            h.setBlock(extra, Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(formed(h, 5), "Removing extra flesh did not repair the vat");
            h.succeed();
        });
    }
}
