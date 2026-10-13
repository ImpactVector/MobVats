package net.impactvector.mobvats.gametest;

import static net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import it.zerono.mods.zerocore.lib.data.IoDirection;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceBundle;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class ResourceRoleGameTests {
    private static class Endpoint implements ResourceEndpoint {
        final BlockPos position;
        final Set<ConduitTopology.Role> roles;
        final ItemStackHandler items = new ItemStackHandler(3);
        final FluidTank tank = new FluidTank(1000);

        Endpoint(int x, ConduitTopology.Role... roles) {
            position = new BlockPos(x, 1, 1);
            this.roles = Set.of(roles);
        }

        public BlockPos getBlockPos() {
            return position;
        }

        public Set<ConduitTopology.Role> roles(ConduitResource resource) {
            return roles;
        }

        public IItemHandlerModifiable items(ConduitTopology.Role role) {
            return roles.contains(role) ? items : null;
        }

        public List<FluidTank> fluids(ConduitTopology.Role role) {
            return roles.contains(role) ? List.of(tank) : List.of();
        }
    }

    private static ResourceRouting routing(ResourceEndpoint... endpoints) {
        var components = new ArrayList<ConduitTopology.Component>();
        for (var resource : ConduitResource.values()) {
            var connections = Arrays.stream(endpoints)
                    .flatMap(e -> e.connections().stream())
                    .filter(e -> e.resource() == resource)
                    .toList();
            components.add(new ConduitTopology.Component(resource, BlockPos.ZERO, 1, 1, connections));
        }
        return new ResourceRouting(new ConduitTopology(components), List.of(endpoints));
    }

    private static List<ResourceBundle.Strategy> itemPlan(Item item, int count) {
        return List.of(new ResourceBundle.Strategy("use", ResourceBundle.item(s -> s.is(item), count)));
    }

    private static ItemStack named(Item item, int count, String name) {
        var stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    @GameTest(template = "empty")
    public static void producerCompletionPrefersStoreAndRetainsBackpressure(GameTestHelper h) {
        var producer = new Endpoint(1, PRODUCER, CONSUMER);
        var store = new Endpoint(5, STORE);
        var r = routing(store, producer);
        h.assertTrue(producer.roles(ConduitResource.ITEM).containsAll(Set.of(PRODUCER, CONSUMER)), "Roles exclusive");
        var stack = named(Items.APPLE, 7, "Batch");
        h.assertTrue(r.complete(producer, List.of(stack), List.of()), "Completion failed");
        h.assertTrue(
                ItemStack.matches(store.items.getStackInSlot(0), stack)
                        && producer.items.getStackInSlot(0).isEmpty(),
                "Store not preferred or components lost");
        for (int slot = 0; slot < 3; slot++) store.items.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        h.assertTrue(r.complete(producer, List.of(stack), List.of()), "Local retention failed");
        h.assertTrue(
                r.publish(producer) == 0 && ItemStack.matches(producer.items.getStackInSlot(0), stack),
                "Backlog deleted");
        for (int slot = 0; slot < 3; slot++) producer.items.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        h.assertTrue(
                !r.complete(producer, List.of(stack), List.of())
                        && producer.items.getStackInSlot(0).getCount() == 64,
                "Full producer did not backpressure");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void consumerPrefersStoreThenDirectProducerDeterministically(GameTestHelper h) {
        var consumer = new Endpoint(1, CONSUMER);
        var later = new Endpoint(9, STORE);
        var earlier = new Endpoint(5, STORE);
        var producer = new Endpoint(3, PRODUCER);
        earlier.items.setStackInSlot(0, named(Items.APPLE, 1, "Earlier store"));
        later.items.setStackInSlot(0, named(Items.APPLE, 1, "Later store"));
        producer.items.setStackInSlot(0, named(Items.APPLE, 1, "Producer"));
        var r = routing(later, producer, consumer, earlier);
        var first = r.prepare(consumer, itemPlan(Items.APPLE, 1)).orElseThrow();
        h.assertTrue(
                first.items().getFirst().getHoverName().getString().equals("Earlier store"),
                "Position ordering or Store preference wrong");
        first.commit(() -> {}, () -> {});
        var second = r.prepare(consumer, itemPlan(Items.APPLE, 1)).orElseThrow();
        h.assertTrue(
                second.items().getFirst().getHoverName().getString().equals("Later store"), "Producer outranked store");
        second.commit(() -> {}, () -> {});
        var direct = r.prepare(consumer, itemPlan(Items.APPLE, 1)).orElseThrow();
        h.assertTrue(
                direct.items().getFirst().getHoverName().getString().equals("Producer")
                        && direct.commit(() -> {}, () -> {}),
                "Direct supply failed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedBundleIsAtomicAndReservesRepeatedRequirements(GameTestHelper h) {
        var consumer = new Endpoint(1, CONSUMER);
        var store = new Endpoint(5, STORE);
        store.items.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        store.items.setStackInSlot(1, new ItemStack(Items.CARROT, 1));
        var input = new ResourceBundle(
                List.of(
                        new ResourceBundle.ItemDemand(s -> s.is(Items.APPLE), 1),
                        new ResourceBundle.ItemDemand(s -> s.is(Items.APPLE), 1),
                        new ResourceBundle.ItemDemand(s -> s.is(Items.CARROT), 1)),
                List.of(new ResourceBundle.FluidDemand(s -> s.is(Fluids.WATER), 500)));
        var r = routing(consumer, store);
        var plan = List.of(new ResourceBundle.Strategy("mixed", input));
        h.assertTrue(
                r.prepare(consumer, plan).isEmpty()
                        && store.items.getStackInSlot(0).getCount() == 2
                        && store.items.getStackInSlot(1).getCount() == 1,
                "Incomplete bundle consumed items");
        var fluid = new FluidStack(Fluids.WATER, 700);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Formula"));
        store.tank.setFluid(fluid);
        var acquisition = r.prepare(consumer, plan).orElseThrow();
        h.assertTrue(
                store.tank.getFluidAmount() == 700
                        && acquisition
                                .fluids()
                                .getFirst()
                                .get(DataComponents.CUSTOM_NAME)
                                .getString()
                                .equals("Formula"),
                "Reservation mutated fluid or lost components");
        h.assertTrue(
                acquisition.commit(() -> {}, () -> {})
                        && store.items.getStackInSlot(0).isEmpty()
                        && store.items.getStackInSlot(1).isEmpty()
                        && store.tank.getFluidAmount() == 200,
                "Atomic bundle failed");
        h.assertTrue(!acquisition.commit(() -> {}, () -> {}), "Double commit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedOutputBundleDoesNotPartiallyProduce(GameTestHelper h) {
        var producer = new Endpoint(1, PRODUCER);
        var store = new Endpoint(5, STORE);
        producer.tank.setFluid(new FluidStack(Fluids.LAVA, 1000));
        store.tank.setFluid(new FluidStack(Fluids.LAVA, 1000));
        h.assertTrue(
                !routing(producer, store)
                                .complete(
                                        producer,
                                        List.of(new ItemStack(Items.APPLE)),
                                        List.of(new FluidStack(Fluids.WATER, 1)))
                        && store.items.getStackInSlot(0).isEmpty()
                        && producer.items.getStackInSlot(0).isEmpty(),
                "Blocked fluid output partially created items");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void strategyOrderEnabledSelectorsAndPersistence(GameTestHelper h) {
        var consumer = new Endpoint(1, CONSUMER);
        var store = new Endpoint(5, STORE);
        store.items.setStackInSlot(0, named(Items.APPLE, 2, "Keep"));
        store.items.setStackInSlot(1, new ItemStack(Items.CARROT));
        store.tank.setFluid(new FluidStack(Fluids.WATER, 500));
        var liquid = new ResourceBundle.Strategy(
                "liquid",
                new ResourceBundle(List.of(), List.of(new ResourceBundle.FluidDemand(s -> s.is(Fluids.WATER), 500))));
        var item = new ResourceBundle.Strategy("item", ResourceBundle.item(s -> true, 1));
        var configuration = new DemandConfiguration(() -> {});
        configuration.configure(List.of(
                new DemandConfiguration.Option("item", true, List.of("minecraft:carrot"), List.of()),
                new DemandConfiguration.Option("liquid", false, List.of(), List.of())));
        var restored = new DemandConfiguration(() -> {});
        restored.load(configuration.save());
        var r = routing(store, consumer);
        var acquisition =
                r.prepare(consumer, restored.plan(List.of(liquid, item))).orElseThrow();
        h.assertTrue(
                acquisition.strategy().equals("item")
                        && acquisition.items().getFirst().is(Items.CARROT)
                        && acquisition.commit(() -> {}, () -> {}),
                "Config order/filter ignored");
        h.assertTrue(
                store.items.getStackInSlot(0).getCount() == 2 && store.tank.getFluidAmount() == 500,
                "Disabled strategy or rejected items consumed");
        configuration.configure(List.of(
                new DemandConfiguration.Option("liquid", true, List.of(), List.of("minecraft:water")),
                new DemandConfiguration.Option("item", true, List.of(), List.of())));
        h.assertTrue(
                r.prepare(consumer, configuration.plan(List.of(item, liquid)))
                        .orElseThrow()
                        .strategy()
                        .equals("liquid"),
                "Persisted ordering not authoritative");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void staleReservationsAndOperationFailureRestoreInputs(GameTestHelper h) {
        var consumer = new Endpoint(1, CONSUMER);
        var store = new Endpoint(5, STORE);
        store.items.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        var r = routing(store, consumer);
        var stale = r.prepare(consumer, itemPlan(Items.APPLE, 1)).orElseThrow();
        store.items.setStackInSlot(0, new ItemStack(Items.APPLE, 3));
        h.assertTrue(
                !stale.commit(
                        () -> {
                            throw new AssertionError("Stale operation ran");
                        },
                        () -> {}),
                "Stale reservation committed");
        boolean failed = false;
        try {
            r.prepare(consumer, itemPlan(Items.APPLE, 1))
                    .orElseThrow()
                    .commit(
                            () -> {
                                throw new IllegalStateException("Operation failure");
                            },
                            () -> {});
        } catch (IllegalStateException expected) {
            failed = true;
        }
        h.assertTrue(failed && store.items.getStackInSlot(0).getCount() == 3, "Failure lost inputs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realToolConsumesAndProducesOnSameComponent(GameTestHelper h) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, Content.state(VatPartType.ToolPort));
        h.setBlock(p.offset(4, 0, 0), Content.state(VatPartType.ItemPort));
        h.setBlock(p.offset(8, 0, 0), Content.state(VatPartType.ItemPort));
        var tool = (VatToolPortEntity) h.getBlockEntity(p);
        var input = (VatItemPortEntity) h.getBlockEntity(p.offset(4, 0, 0));
        var output = (VatItemPortEntity) h.getBlockEntity(p.offset(8, 0, 0));
        output.setIoDirection(IoDirection.Output);
        input.endpoint().filter("minecraft:shears");
        var shears = named(Items.SHEARS, 1, "Named tool");
        input.staging().setStackInSlot(0, shears);
        tool.results().store(List.of(named(Items.WHITE_WOOL, 3, "Result")));
        var r = routing(tool, input, output);
        var acquired = r.prepare(tool, itemPlan(Items.SHEARS, 1)).orElseThrow();
        h.assertTrue(
                acquired.commit(
                                () -> tool.setHeldItem(acquired.items().getFirst()),
                                () -> tool.setHeldItem(ItemStack.EMPTY))
                        && ItemStack.matches(tool.heldItem(), shears),
                "Tool demand failed");
        h.assertTrue(r.publish(tool) == 0 && !tool.results().isEmpty(), "Filter bypassed Store");
        h.assertTrue(
                r.pull(output) == 3
                        && tool.results().isEmpty()
                        && output.staging()
                                .getStackInSlot(0)
                                .getHoverName()
                                .getString()
                                .equals("Result"),
                "Tool production unavailable on same component");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void portDirectionsAndRealBlenderBufferRoles(GameTestHelper h) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, Content.state(VatPartType.ItemPort));
        h.setBlock(p.east(), Content.state(VatPartType.FluidPort));
        h.setBlock(p.offset(4, 0, 0), Content.state(VatPartType.Blender));
        h.setBlock(p.offset(8, 0, 0), FluidBufferContent.state(ConduitCladding.BARE));
        var item = (VatItemPortEntity) h.getBlockEntity(p);
        var fluid = (VatFluidPortEntity) h.getBlockEntity(p.east());
        var blender = (VatBlenderEntity) h.getBlockEntity(p.offset(4, 0, 0));
        var buffer = (FluidBufferEntity) h.getBlockEntity(p.offset(8, 0, 0));
        h.assertTrue(
                item.roles(ConduitResource.ITEM).equals(Set.of(STORE))
                        && fluid.roles(ConduitResource.FLUID).equals(Set.of(STORE)),
                "Input direction role wrong");
        item.setIoDirection(IoDirection.Output);
        fluid.setIoDirection(IoDirection.Output);
        h.assertTrue(
                item.roles(ConduitResource.ITEM).equals(Set.of(CONSUMER))
                        && fluid.roles(ConduitResource.FLUID).equals(Set.of(CONSUMER)),
                "Output direction role wrong");
        var stack = new FluidStack(Fluids.WATER, 800);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Unchanged"));
        blender.service().tank(stack).setFluid(stack);
        buffer.filter("minecraft:water");
        var r = routing(blender, buffer, fluid);
        h.assertTrue(
                r.publish(blender) == 800 && FluidStack.matches(buffer.tank.getFluid(), stack),
                "Real producer/store routing failed");
        var handler = r.fluidConsumer(fluid, s -> true);
        h.assertTrue(
                handler.drain(200, SIMULATE).getAmount() == 200 && buffer.tank.getFluidAmount() == 800,
                "Simulation spent fluid");
        var drained = handler.drain(200, EXECUTE);
        h.assertTrue(
                drained.get(DataComponents.CUSTOM_NAME).getString().equals("Unchanged")
                        && buffer.tank.getFluidAmount() == 600,
                "Fluid consumer lost metadata");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void combustionDemandConfigurationAndIsolation(GameTestHelper h) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, MixerContent.state(MixerPartType.CombustionDrive));
        var drive = (MixerCombustionDriveEntity) h.getBlockEntity(p);
        var store = new Endpoint(9, STORE);
        store.items.setStackInSlot(0, new ItemStack(Items.APPLE));
        store.items.setStackInSlot(1, new ItemStack(Items.COAL, 2));
        drive.demandConfiguration()
                .configure(List.of(new DemandConfiguration.Option("fuel", false, List.of(), List.of())));
        h.assertTrue(!drive.refill(routing(drive, store)), "Disabled fuel demand ran");
        drive.demandConfiguration()
                .configure(List.of(new DemandConfiguration.Option("fuel", true, List.of("minecraft:coal"), List.of())));
        h.assertTrue(
                drive.refill(routing(drive, store))
                        && drive.fuel().getStackInSlot(0).is(Items.COAL)
                        && store.items.getStackInSlot(0).is(Items.APPLE)
                        && store.items.getStackInSlot(1).getCount() == 1,
                "Fuel demand mixed recipe inputs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void threeRolesCanOwnDistinctInventoriesAndSharedViewsDoNotDoubleSpend(GameTestHelper h) {
        var storeInventory = new ItemStackHandler(1);
        var producedInventory = new ItemStackHandler(1);
        storeInventory.setStackInSlot(0, new ItemStack(Items.APPLE));
        producedInventory.setStackInSlot(0, new ItemStack(Items.CARROT));
        var combined = new Endpoint(5, STORE, PRODUCER, CONSUMER) {
            public IItemHandlerModifiable items(ConduitTopology.Role role) {
                return role == STORE ? storeInventory : role == PRODUCER ? producedInventory : null;
            }
        };
        var alias = new Endpoint(7, STORE) {
            public IItemHandlerModifiable items(ConduitTopology.Role role) {
                return storeInventory;
            }
        };
        var consumer = new Endpoint(1, CONSUMER);
        var r = routing(alias, consumer, combined);
        h.assertTrue(r.prepare(consumer, itemPlan(Items.APPLE, 2)).isEmpty(), "Aliased store spent twice");
        var bundle = new ResourceBundle(
                List.of(
                        new ResourceBundle.ItemDemand(s -> s.is(Items.APPLE), 1),
                        new ResourceBundle.ItemDemand(s -> s.is(Items.CARROT), 1)),
                List.of());
        var acquisition = r.prepare(consumer, List.of(new ResourceBundle.Strategy("both", bundle)))
                .orElseThrow();
        h.assertTrue(
                acquisition.commit(() -> {}, () -> {})
                        && storeInventory.getStackInSlot(0).isEmpty()
                        && producedInventory.getStackInSlot(0).isEmpty(),
                "Producer role hidden behind Store role");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inputFuelStoreSurvivesPortableSaveAndLegacyLoad(GameTestHelper h) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, MixerContent.state(MixerPartType.ItemPort));
        var port = (MixerPortEntity) h.getBlockEntity(p);
        var old = new net.minecraft.nbt.CompoundTag();
        port.syncDataFrom(
                old,
                h.getLevel().registryAccess(),
                it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason.FullSync);
        h.assertTrue(port.fuelStore().getSlots() == 2, "Legacy port lost fuel slots");
        port.fuelStore().insertItem(0, named(Items.COAL, 4, "Stored fuel"), false);
        var portable = net.impactvector.mobvats.gamecontent.item.EndpointState.capture(
                new ItemStack(MixerContent.block(MixerPartType.ItemPort)), port);
        h.setBlock(p, net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(p, MixerContent.state(MixerPartType.ItemPort));
        var restored = (MixerPortEntity) h.getBlockEntity(p);
        net.impactvector.mobvats.gamecontent.item.EndpointState.restore(portable, restored);
        h.assertTrue(
                restored.fuelStore().getStackInSlot(0).getCount() == 4
                        && restored.fuelStore()
                                .getStackInSlot(0)
                                .getHoverName()
                                .getString()
                                .equals("Stored fuel"),
                "Portable Store lost fuel or components");
        h.succeed();
    }
}
