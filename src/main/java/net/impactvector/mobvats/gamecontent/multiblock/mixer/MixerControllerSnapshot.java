package net.impactvector.mobvats.gamecontent.multiblock.mixer;

import com.google.gson.Gson;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Detached presentation only; stack NBT is an icon/component payload, never controller save data. */
public record MixerControllerSnapshot(
        boolean formed,
        boolean enabled,
        boolean inhibited,
        boolean advancing,
        String condition,
        String drive,
        String power,
        Batch current,
        Batch completed,
        List<Resource> resources,
        String diagnostics) {
    private static final Gson JSON = new Gson();
    public static final MixerControllerSnapshot EMPTY = new MixerControllerSnapshot(
            false,
            false,
            false,
            false,
            "Mixer disconnected",
            "None",
            "",
            Batch.EMPTY,
            Batch.EMPTY,
            List.of(),
            "Mixer disconnected");

    public record Product(boolean fluid, String name, int quantity, String stack, String details) {
        public static Product item(ItemStack stack, HolderLookup.Provider registries) {
            return new Product(
                    false,
                    stack.getHoverName().getString(),
                    stack.getCount(),
                    stack.save(registries).toString(),
                    stack.getComponents().toString());
        }

        public static Product fluid(FluidStack stack, HolderLookup.Provider registries) {
            return new Product(
                    true,
                    stack.getHoverName().getString(),
                    stack.getAmount(),
                    stack.save(registries).toString(),
                    stack.getComponents().toString());
        }

        public String label() {
            return quantity + (fluid ? " mB " : " × ") + name;
        }
    }

    public record Batch(
            String recipe,
            String name,
            boolean active,
            int progress,
            int duration,
            long remainingTicks,
            List<Product> products,
            String note) {
        public static final Batch EMPTY = new Batch("", "", false, 0, 0, -1, List.of(), "");

        public Batch {
            products = List.copyOf(products);
        }
    }

    public record Resource(String section, String location, String availability, int capacity, Product product) {}

    public MixerControllerSnapshot {
        resources = List.copyOf(resources);
    }

    public String encode() {
        return JSON.toJson(this);
    }

    public static MixerControllerSnapshot decode(String text) {
        return JSON.fromJson(text, MixerControllerSnapshot.class);
    }

    public static String recipeName(String id) {
        if (id.isBlank()) return "";
        String path = id.substring(id.indexOf(':') + 1);
        String fallback = Arrays.stream(path.replace('/', ' ').replace('_', ' ').split(" "))
                .filter(s -> !s.isEmpty())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
        return Component.translatableWithFallback("recipe." + id.replace(':', '.'), fallback)
                .getString();
    }

    public static long remainingTicks(int progress, int duration, int phase, boolean advancing) {
        if (!advancing || duration <= 0) return -1;
        int cadence = SlurryBalance.CADENCE;
        return Math.max(1, ((Math.max(1L, (long) duration - progress) + cadence - 1) / cadence) * cadence - phase);
    }

    public static MixerControllerSnapshot capture(MultiblockMixer mixer, MixerControllerEntity controller) {
        var state = mixer.state();
        var registries = controller.getLevel().registryAccess();
        var batch = state.batchView();
        var motor = mixer.drive().orElse(null);
        String drive = motor instanceof MixerFeDriveEntity
                ? "FE drive"
                : motor instanceof MixerCombustionDriveEntity
                        ? "Combustion drive"
                        : motor == null ? "None" : "Creative drive";
        String power = motor instanceof MixerFeDriveEntity fe
                ? fe.energy().getEnergyStored() + " FE available"
                : motor instanceof MixerCombustionDriveEntity fuel
                        ? fuel.burnTicks() + " burn ticks; "
                                + fuel.fuel().getStackInSlot(0).getCount() + " fuel staged"
                                + (fuel.canRefill(mixer.resourceRouting()) ? "; routed fuel available" : "")
                        : motor == null ? "No eligible drive" : "Unlimited power";
        boolean powered = motor != null && motor.availableDrive(mixer) >= 1;
        String condition = !mixer.isAssembled()
                ? "Mixer unformed"
                : !mixer.isMachineActive()
                        ? "Paused by operator"
                        : mixer.controllerInhibited()
                                ? "Inhibited by redstone"
                                : motor == null && mixer.driveInhibited()
                                        ? "Drive inhibited by redstone"
                                        : !powered
                                                ? "Insufficient drive power"
                                                : mixer.operatingState() == MixerOperatingState.SPINNING_UP
                                                        ? "Spin-up: " + mixer.startupTicks() + " / "
                                                                + mixer.spinUpTicks() + " ticks"
                                                        : mixer.operatingState() != MixerOperatingState.RUNNING
                                                                ? "Waiting for drive"
                                                                : !batch.executable()
                                                                        ? batch.condition()
                                                                        : batch.active()
                                                                                ? "Processing"
                                                                                : "Ready for next batch";
        boolean advancing = mixer.isAssembled()
                && mixer.processingEnabled()
                && powered
                && mixer.operatingState() == MixerOperatingState.RUNNING
                && batch.executable()
                && batch.active();
        var products = new ArrayList<Product>();
        batch.items().forEach(s -> products.add(Product.item(s, registries)));
        var definition = MixerRecipeRegistry.recipe(batch.recipe());
        for (int i = 0; i < batch.fluids().size(); i++) {
            var stack = batch.fluids().get(i);
            var output = definition.fluidOutputs().get(i);
            if (!batch.productsResolved() && output.transform() != null)
                products.add(new Product(
                        true,
                        "Unresolved fluid output",
                        stack.getAmount(),
                        "",
                        "Identity and composition require matching inputs"));
            else products.add(Product.fluid(stack, registries));
        }
        var current = new Batch(
                batch.recipe(),
                recipeName(batch.recipe()),
                batch.active(),
                batch.progress(),
                batch.duration(),
                remainingTicks(batch.progress(), batch.duration(), mixer.runningCadence(), advancing),
                products,
                batch.productsResolved() || batch.recipe().isEmpty()
                        ? ""
                        : "Output composition will resolve when matching inputs are available");
        var last = state.lastCompletion();
        var completed = Batch.EMPTY;
        if (last != null) {
            var actual = new ArrayList<Product>();
            last.items().forEach(s -> actual.add(Product.item(s, registries)));
            last.fluids().forEach(s -> actual.add(Product.fluid(s, registries)));
            completed = new Batch(last.recipe(), recipeName(last.recipe()), false, 0, 0, -1, actual, "Completed");
        }
        var resources = new ArrayList<Resource>();
        for (boolean input : new boolean[] {true, false}) {
            var inventory = state.itemPort(input, s -> true);
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty())
                    resources.add(new Resource(
                            input ? "Internal · staged inputs" : "Internal · pending outputs",
                            "Slot " + (slot + 1),
                            input ? "Staged; consumed at successful commit" : "Awaiting extraction",
                            0,
                            Product.item(stack, registries)));
            }
        }
        state.internalFluids()
                .forEach(s -> resources.add(new Resource(
                        "Internal · fluid escrow",
                        "Controller",
                        "Legacy escrow; migration pending",
                        0,
                        Product.fluid(s, registries))));
        if (mixer.isAssembled()) {
            for (var endpoint : mixer.presentationItemConsumers()) {
                if (!(endpoint instanceof MixerCombustionDriveEntity fuel)) continue;
                for (int slot = 0; slot < fuel.fuel().getSlots(); slot++) {
                    var stack = fuel.fuel().getStackInSlot(slot);
                    if (!stack.isEmpty())
                        resources.add(new Resource(
                                slot == 0 ? "Internal · drive fuel" : "Internal · fuel remainders",
                                fuel.getBlockPos().toShortString(),
                                fuel == motor ? "Selected drive" : "Standby drive; not consuming",
                                0,
                                Product.item(stack, registries)));
                }
            }
            for (var entry : mixer.storage().storedFluids(controller.getBlockPos()))
                resources.add(new Resource(
                        entry.bulk()
                                ? "Connected buffers · fluids"
                                : entry.local() ? "Internal · pending fluids" : "Connected endpoints · fluids",
                        entry.location().toShortString(),
                        entry.bulk() ? "Available to recipe planner" : "Service staging; accessible to recipe planner",
                        entry.capacity(),
                        Product.fluid(entry.stack(), registries)));
            for (var entry : mixer.resourceRouting().storedItems(mixer.presentationItemConsumers()))
                resources.add(new Resource(
                        "Connected buffers · items",
                        entry.location().toShortString() + " / slot " + (entry.slot() + 1),
                        entry.available()
                                ? "Routable to Mixer endpoints; recipe inputs require staging"
                                : "Stored; excluded by routing filter",
                        0,
                        Product.item(entry.stack(), registries)));
        }
        resources.sort(Comparator.comparingInt((Resource r) -> r.section().startsWith("Internal") ? 0 : 1)
                .thenComparing(Resource::section)
                .thenComparing(Resource::location));
        String diagnostics = state.observe(() -> mixer.diagnosticReport().getString());
        return new MixerControllerSnapshot(
                mixer.isAssembled(),
                mixer.isMachineActive(),
                mixer.controllerInhibited(),
                advancing,
                condition,
                drive,
                power,
                current,
                completed,
                resources,
                diagnostics);
    }
}
