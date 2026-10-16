package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import java.util.stream.Stream;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

/** Read-only menu summary; contains no graph, loot roll, inventory or process implementation objects. */
public record VatStatusSnapshot(List<String> pages) {
    public VatStatusSnapshot {
        pages = List.copyOf(pages);
    }

    public static final VatStatusSnapshot DISCONNECTED = new VatStatusSnapshot(
            List.of("Unformed / inactive", "No specimen", "No samples", "Controller disconnected"));

    public static VatStatusSnapshot capture(MultiblockVat vat, Collection<AbstractVatEntity> attached) {
        var topology = vat.getTopology();
        var metrics = vat.metrics();
        var ports = attached.stream()
                .sorted(Comparator.comparing(AbstractVatEntity::getBlockPos, VatTopologyCompiler.POSITION))
                .toList();
        var tools = ports.stream()
                .filter(p -> p instanceof VatToolPortEntity)
                .map(p -> (VatToolPortEntity) p)
                .toList();
        var assignments = Stream.concat(topology.actors().stream(), topology.toolLoading().stream())
                .toList();
        long assigned = tools.stream()
                .filter(p -> assignments.stream().anyMatch(a -> a.actor().equals(p.getBlockPos())))
                .count();
        var overview = new ArrayList<String>();
        overview.add(
                vat.isAssembled()
                        ? "Formed / "
                                + (vat.isMachineActive()
                                        ? "enabled (automatic processes)"
                                        : "disabled (production suspended)")
                        : "Unformed / inactive");
        overview.add("Simulacra: " + vat.getAssemblies().size());
        long heads = vat.specimenHeadCount();
        overview.add("Validated: " + vat.getAssemblies().size() + " / invalid or unvalidated: "
                + (heads - vat.getAssemblies().size()));
        overview.add("Next scheduled Head: "
                + (vat.processEngine().nextHead() == null
                        ? "first in position order"
                        : position(vat.processEngine().nextHead())));
        overview.add("Process cadence: every " + CowBalance.PROCESS_CADENCE + " ticks");
        overview.add("Physical ports / compiled topology:");
        overview.add("Item inputs: " + count(ports, VatItemPortEntity.class, true) + " / outputs: "
                + count(ports, VatItemPortEntity.class, false));
        overview.add("Fluid inputs: " + count(ports, VatFluidPortEntity.class, true) + " / outputs: "
                + count(ports, VatFluidPortEntity.class, false));
        overview.add(
                "Tools: " + tools.size() + " / assigned: " + assigned + " / unassigned: " + (tools.size() - assigned));
        overview.add("Blenders: "
                + ports.stream().filter(VatBlenderEntity.class::isInstance).count() + " / Sumps: "
                + ports.stream().filter(VatSumpEntity.class::isInstance).count());
        overview.add("Feed Port routes: "
                + topology.actors().stream()
                        .filter(r -> r.use().equals(PassiveUses.FEED))
                        .count());
        overview.add("Generic item outputs: " + topology.genericItemOutputs().size() + " / reserved local: "
                + topology.reservedItemOutputs().size());
        overview.add("Configuration: physical placement and port modes");
        var production = new ArrayList<String>();
        var buffers = vat.processFluids();
        for (var id : buffers.ids()) {
            var tank = buffers.buffer(id);
            production.add(id + ": "
                    + (tank.isEmpty() ? "empty" : tank.getFluid().getHoverName().getString()) + " / "
                    + tank.getFluidAmount() + " / " + tank.getCapacity() + " mB");
        }
        production.addAll(buffers.diagnostics());
        production.add("Current/last feed source: " + vat.feedSource());
        production.add("Vat medium: " + vat.medium().getFluidAmount() + " / "
                + vat.medium().getCapacity() + " mB");
        var prototype = vat.medium().cleanPrototype();
        var cultureName = net.impactvector.mobvats.gamecontent.multiblock.mixer.process.MixerRecipeRegistry.name(
                prototype, false);
        production.add("Culture: "
                + (prototype.isEmpty()
                        ? "empty"
                        : cultureName == null ? prototype.getHoverName().getString() : cultureName.getString()));
        production.add("Clean: " + vat.medium().cleanAmount() + " mB / Dirty: "
                + vat.medium().dirtyAmount() + " mB / Fouling: "
                + number(
                        vat.medium().isEmpty()
                                ? 0
                                : 100.0
                                        * vat.medium().dirtyAmount()
                                        / vat.medium().getFluidAmount())
                + "%");
        production.add("Culture fouled: " + metrics.total("cultureFouledMb") + " mB / completed fouling events: "
                + metrics.total("foulingEvents"));
        production.add(number(metrics.perSecond("feedingConsumed")) + " mB/s Feeding Slurry consumed");
        var fluid = vat.fluidContents();
        production.add("Tank contents: "
                + (fluid.isEmpty()
                        ? "empty"
                        : fluid.getAmount() + " mB " + fluid.getHoverName().getString()));
        production.add("XP fluid: " + vat.getExperienceMb() + " mB");
        production.add("Harvest output buffered: " + vat.killOutputStackCount() + " stacks / "
                + vat.killOutputItemCount() + " items");
        for (var assembly : vat.getAssemblies()) {
            var head = assembly.head().getBlockPos();
            var state = vat.cowState(head);
            production.add("Specimen: " + assembly.head().getIdentity() + " @ " + position(head));
            production.add("Harnesses: "
                    + assembly.harnessPositions().stream()
                            .map(VatStatusSnapshot::position)
                            .collect(java.util.stream.Collectors.joining("; "))
                    + " / Sump: " + position(assembly.drain().getBlockPos()));
            var clearance = vat.specimenClearance(head);
            if (clearance != null)
                production.add(
                        "Clearance observations: " + clearance.solidIntrusions().size() + " occupied shell cells / "
                                + clearance.neighboringBodies().size() + " neighboring bodies / "
                                + clearance.serviceGeometry().size() + " service volumes");
            production.add(
                    "Current biological frequency: " + vat.processEngine().multiplier(head) + "x");
            var passive = PassiveMobDefinitionRegistry.resolve(assembly.head().getIdentity());
            if (passive != null) production.addAll(passive.tagDiagnostics());
            production.add("Culture frequency: " + number(vat.frequencyMultiplier(head)) + "x; happiness yield: "
                    + number(vat.yieldMultiplier(head)) + "x");
            production.add("Mixed medium frequency before exposure: "
                    + String.format(
                            Locale.ROOT,
                            "%.4f",
                            vat.medium().frequency(assembly.head().getIdentity())) + "x");
            var runtime = vat.processEngine().state(head);
            var stage = runtime == null ? assembly.head().lifeStage() : runtime.stage();
            production.add("Definition: "
                    + PassiveMobDefinitionRegistry.source(assembly.head().getIdentity()));
            production.add(
                    passive == null
                            ? "Definition missing; processing disabled; identity/geometry preserved"
                            : "Capture: " + (passive.capture().enabled() ? "enabled" : "disabled") + " / "
                                    + passive.capture().status());
            production.add("Profile: "
                    + (passive == null
                            ? "unsupported"
                            : passive.lifecycle()
                                    ? "passive lifecycle"
                                    : "configured processes / no replacement lifecycle"));
            production.add("Stage: " + (stage == LifeStage.ADULT ? "Adult" : "Juvenile"));
            production.add(
                    "Growth: " + (runtime == null ? 0 : runtime.readProcess(PassiveProcessCompiler.GROWTH).elapsed)
                            + " / " + (passive == null ? 0 : passive.juvenile_growth_ticks()));
            production.add("Adult secondary outputs: " + (stage == LifeStage.ADULT ? "Active" : "Paused (juvenile)"));
            var harvest = topology.actors().stream()
                    .filter(a -> a.head().equals(head)
                            && (a.use().equals(PassiveUses.HARVEST) || a.use().equals(PassiveUses.BLEND)))
                    .findFirst();
            production.add("Harvest actor: "
                    + harvest.map(a -> vat.getWorld().getBlockEntity(a.actor()) instanceof VatBlenderEntity
                                    ? "Blender"
                                    : "Sword")
                            .orElse("None"));
            production.add("Replacement cycle: "
                    + (harvest.isEmpty() ? "Disabled by topology" : replacementStatus(vat, head, state)));
            production.add("Feed source: " + (runtime == null ? "None" : runtime.feedSource()));
            vat.processEngine()
                    .status(head)
                    .forEach((id, result) -> production.add(id + ": " + result.reason()
                            + (result.reason() == ProcessResult.Reason.OUTPUT_BLOCKED
                                    ? " / service staging and bulk output blocked"
                                    : "")));
            if (passive != null)
                for (var process : passive.processes())
                    production.add(process.id() + ": "
                            + (runtime == null
                                    ? "inactive"
                                    : runtime.readProcess(process.id()).phase + " / "
                                            + runtime.readProcess(process.id()).elapsed + " of "
                                            + process.duration_ticks()
                                            + " ticks"));
            if (passive != null)
                for (var output : passive.secondary_outputs())
                    production.add(output.process() + ": "
                            + (output.requires_stage().accepts(stage) ? "Active" : "Paused (juvenile)") + " / "
                            + (runtime == null ? 0 : runtime.readProcess(output.process()).elapsed) + " of "
                            + output.interval_ticks() + " ticks");
            for (var contact : topology.itemContacts())
                if (contact.head().equals(head)) {
                    var source = vat.getWorld().getBlockEntity(contact.input());
                    var stack = source instanceof VatItemPortEntity port
                            ? port.staging().getStackInSlot(0)
                            : ItemStack.EMPTY;
                    production.add("Feed @ " + position(contact.input()) + ": "
                            + (passive != null
                                            && passive.lifecycle()
                                            && passive.feed().accepts(stack)
                                    ? stack.getCount() + " "
                                            + stack.getHoverName().getString() + " available"
                                    : "Waiting for valid feed"));
                }
            if (topology.itemContacts().stream().noneMatch(c -> c.head().equals(head)))
                production.add("Feed: no compiled source");
        }
        production.add("Recent rates (up to 10 active seconds):");
        production.add(number(metrics.perSecond(MachineMetrics.MILK)) + " mB/s milk");
        production.add(number(metrics.perSecond(MachineMetrics.ITEMS)) + " items/s routed to output staging");
        production.add(number(metrics.perSecond(MachineMetrics.XP)) + " XP/s generated");
        production.add(number(metrics.perSecond(MachineMetrics.MEAT)) + " mB/s MEAT");
        production.add(number(metrics.perSecond(MachineMetrics.WASTE)) + " mB/s WASTE");
        production.add("Blender harvests committed: " + metrics.total(MachineMetrics.BLENDER_HARVESTS));
        var efficiency = List.of(
                "Lifetime process ratios (no aggregate efficiency score):",
                ratio(metrics, MachineMetrics.HARVEST_ITEMS, MachineMetrics.WHEAT, "harvest items / wheat"),
                ratio(metrics, MachineMetrics.XP, MachineMetrics.WHEAT, "XP / wheat"),
                ratio(metrics, MachineMetrics.DURABILITY, MachineMetrics.HARVESTS, "durability / harvest"),
                ratio(metrics, MachineMetrics.MEAT, MachineMetrics.WHEAT, "mB MEAT / wheat"),
                "Waste: passive active-time production; no feed/harvest input ratio",
                "Milk input cost: none",
                "Wheat consumed: " + metrics.total(MachineMetrics.WHEAT),
                "Sword harvests committed: " + metrics.total(MachineMetrics.HARVESTS),
                "Harvest items: " + metrics.total(MachineMetrics.HARVEST_ITEMS),
                "Rates reset on reload; totals persist.",
                "Rate window pauses while unformed.");
        var diagnostics = new ArrayList<String>();
        diagnostics.addAll(vat.storage().diagnostics());
        diagnostics.addAll(vat.medium().diagnostics());
        for (var part : ports)
            if (part instanceof VatBoundaryEntity e)
                diagnostics.add(e.getPartType() + " @ " + position(e.getBlockPos()) + ": " + e.status() + " / filter "
                        + (e.filter().isEmpty() ? "any" : e.filter()));
        diagnostics.addAll(topology.conduits().diagnostics());
        for (var blocked : topology.physical().blocked())
            if (diagnostics.size() < 96) diagnostics.add(blocked.description());
        for (var head : topology.physical().inactiveHeads())
            diagnostics.add("Simulacrum inactive: harness obstruction @ " + position(head));
        for (var route : assignments)
            if (diagnostics.size() < 96)
                diagnostics.add("Actor @ " + position(route.actor()) + ": item "
                        + route.itemInputs().size() + " in / "
                        + route.itemOutputs().size() + " out; fluid "
                        + route.fluidInputs().size() + " in / "
                        + route.fluidOutputs().size() + " out");
        if (vat.killOutputStackCount() > 0) {
            diagnostics.add("Harvest output buffered: " + vat.killOutputStackCount() + " stacks");
            diagnostics.add("Tool-local output backlog: only that Tool waits until its result buffer is empty");
            diagnostics.add("Each Tool drains only to its physically connected Item Outputs");
        }
        if (vat.milkAmount() > 0 || vat.meatAmount() > 0 || vat.wasteAmount() > 0)
            diagnostics.add(
                    topology.fluidOutputs().isEmpty()
                            ? "No Fluid Output Ports; fluids safely buffered"
                            : "Fluid outputs: waiting for extraction from connected service staging / bulk storage");
        if (vat.meatAmount() > CowBalance.FLUID_CAPACITY - CowBalance.MEAT_PER_HARVEST)
            diagnostics.add("Blender blocked: MEAT buffer lacks harvest capacity");
        if (vat.wasteAmount() > CowBalance.FLUID_CAPACITY - CowBalance.WASTE_PER_INTERVAL)
            diagnostics.add("Sump blocked: WASTE buffer lacks production capacity");
        for (var actor : ports)
            if (actor instanceof VatBlenderEntity) {
                var selected = topology.actors().stream()
                        .filter(a -> a.actor().equals(actor.getBlockPos()))
                        .findFirst();
                var obstruction = topology.physical().blocked().stream()
                        .filter(b -> b.part().equals(actor.getBlockPos()))
                        .findFirst();
                if (obstruction.isPresent()) {
                    diagnostics.add(obstruction.get().description());
                    continue;
                }
                diagnostics.add(actor.getPartType().name() + " @ " + position(actor.getBlockPos()) + ": "
                        + selected.map(a -> "Assigned / " + a.use() + " -> head @ " + position(a.head())
                                        + (" / " + replacementStatus(vat, a.head(), vat.cowState(a.head()))))
                                .orElse("Unassigned / no selected reachable specimen"));
            }
        for (var tool : tools) {
            var route = assignments.stream()
                    .filter(a -> a.actor().equals(tool.getBlockPos()))
                    .findFirst();
            String status = "Unassigned (no selected compatible route)";
            if (route.isPresent()) {
                var r = route.get();
                var state = vat.cowState(r.head());
                var stack = tool.heldItem();
                status = stack.isEmpty() ? "Assigned / missing valid tool" : "Assigned / " + r.use();
                status += " -> head @ " + position(r.head());
                if (r.use().equals("specimenInteraction")) status += " / no implemented process for held item";
                if (r.use().equals(CowBalance.HARVEST_USE))
                    status += !tool.results().isEmpty()
                            ? " / local output blocked"
                            : state.replacement().equals("GROWING")
                                    ? " / juvenile growth"
                                    : " / adult, waiting for feed";
                if (r.use().equals(CowBalance.MILK_USE))
                    status += stack.is(Items.MILK_BUCKET)
                            ? " / output blocked (resident bucket)"
                            : vat.milkAmount() < 1000 ? " / waiting for milk" : " / milk ready";
            }
            if (route.isEmpty()
                    && VanillaEntityLoot.isWeapon(tool.heldItem())
                    && topology.actors().stream()
                            .anyMatch(a -> a.use().equals(CowBalance.HARVEST_USE)
                                    && vat.getWorld().getBlockEntity(a.actor()) instanceof VatBlenderEntity))
                status += " / Sword displaced by Blender priority (or invalid physical reach)";
            var obstruction = topology.physical().blocked().stream()
                    .filter(b -> b.part().equals(tool.getBlockPos()))
                    .findFirst();
            if (obstruction.isPresent())
                status =
                        "Blocked by physical obstruction / " + obstruction.get().description();
            else if (!topology.physical().inactiveHeads().isEmpty() && route.isEmpty())
                status = "Unassigned / simulacrum inactive: harness obstruction";
            diagnostics.add("Tool @ " + position(tool.getBlockPos()) + ": " + status);
            diagnostics.add("Held: "
                    + (tool.heldItem().isEmpty()
                            ? "empty"
                            : tool.heldItem().getHoverName().getString()));
            if (diagnostics.size() >= 128) {
                diagnostics.add("Further ports omitted from summary; inspect their port menus.");
                break;
            }
        }
        for (var part : ports)
            if (part instanceof AbstractVatPortEntity port && diagnostics.size() < 128) {
                String connection = port instanceof VatFluidPortEntity
                        ? (vat.isAssembled() ? "physically connected Fluid Buffer clusters" : "inactive")
                        : topology.reservedItemOutputs().contains(port.getBlockPos())
                                ? "reserved actor-local output"
                                : topology.genericItemOutputs().contains(port.getBlockPos())
                                        ? "unclaimed logistics output"
                                        : topology.itemContacts().stream()
                                                        .anyMatch(c -> c.input().equals(port.getBlockPos()))
                                                ? "compiled feed contact"
                                                : assignments.stream()
                                                                .anyMatch(a -> a.itemInputs()
                                                                                .contains(port.getBlockPos())
                                                                        || a.itemOutputs()
                                                                                .contains(port.getBlockPos()))
                                                        ? "compiled tool endpoint"
                                                        : "no compiled item route";
                diagnostics.add(port.getPartType().name() + " @ " + position(port.getBlockPos()) + ": "
                        + port.getIoDirection() + " / " + connection);
                if (port instanceof VatItemPortEntity item) {
                    var stack = item.staging().getStackInSlot(0);
                    diagnostics.add("Staging: "
                            + (stack.isEmpty()
                                    ? "empty"
                                    : stack.getCount() + " x "
                                            + stack.getHoverName().getString()));
                }
            }
        if (diagnostics.isEmpty()) diagnostics.add("No attached ports");
        return new VatStatusSnapshot(List.of(
                String.join("\n", overview),
                String.join("\n", production),
                String.join("\n", efficiency),
                String.join("\n", diagnostics)));
    }

    private static long count(List<AbstractVatEntity> ports, Class<?> type, boolean input) {
        return ports.stream()
                .filter(type::isInstance)
                .map(p -> (AbstractVatPortEntity) p)
                .filter(p -> p.getIoDirection().isInput() == input)
                .count();
    }

    private static String replacementStatus(MultiblockVat vat, BlockPos head, CowProcesses.Snapshot state) {
        if (!vat.getTopology().operational(head)) return "Inactive: harness obstruction";
        if (state.replacement().equals("GROWING")) return "Growing replacement";

        var route = vat.getTopology().actors().stream()
                .filter(a -> a.head().equals(head) && a.use().equals(CowBalance.HARVEST_USE))
                .findFirst();
        if (route.isPresent() && vat.getWorld().getBlockEntity(route.get().actor()) instanceof VatBlenderEntity)
            return vat.meatAmount() > CowBalance.FLUID_CAPACITY - CowBalance.MEAT_PER_HARVEST
                    ? "Blender ready / MEAT buffer full"
                    : "Blender harvest ready";
        if (route.isPresent()
                && vat.getWorld().getBlockEntity(route.get().actor()) instanceof VatToolPortEntity tool
                && !tool.results().isEmpty()) return "Harvest ready / local output blocked";
        return route.isEmpty() ? "Disabled by topology" : "Adult / waiting for valid feed and output capacity";
    }

    private static String position(BlockPos pos) {
        return pos.toShortString();
    }

    private static String number(double n) {
        return String.format(Locale.ROOT, "%.2f", n);
    }

    private static String ratio(MachineMetrics m, String n, String d, String label) {
        var value = m.ratio(n, d);
        return (Double.isNaN(value) ? "N/A (no input samples)" : number(value)) + " " + label;
    }
}
