package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.List;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology;
import net.minecraft.core.BlockPos;

/** Transient controller-owned active relationships. Never serialized with live entity references. */
public record VatTopology(
        List<BlockPos> assemblies,
        List<BlockPos> itemInputs,
        List<BlockPos> itemOutputs,
        List<BlockPos> fluidInputs,
        List<BlockPos> fluidOutputs,
        List<Assignment> actors,
        List<ItemContact> itemContacts,
        List<Assignment> toolLoading,
        List<BlockPos> reservedItemOutputs,
        List<BlockPos> genericItemOutputs,
        ConduitTopology conduits,
        VatPhysicalState physical) {
    public VatTopology(
            List<BlockPos> assemblies,
            List<BlockPos> itemInputs,
            List<BlockPos> itemOutputs,
            List<BlockPos> fluidInputs,
            List<BlockPos> fluidOutputs,
            List<Assignment> actors,
            List<ItemContact> contacts,
            List<Assignment> loading,
            List<BlockPos> reserved,
            List<BlockPos> generic,
            ConduitTopology conduits) {
        this(
                assemblies,
                itemInputs,
                itemOutputs,
                fluidInputs,
                fluidOutputs,
                actors,
                contacts,
                loading,
                reserved,
                generic,
                conduits,
                VatPhysicalState.EMPTY);
    }

    public boolean operational(BlockPos head) {
        return physical.operational(head);
    }

    public VatTopology(
            List<BlockPos> assemblies,
            List<BlockPos> itemInputs,
            List<BlockPos> itemOutputs,
            List<BlockPos> fluidInputs,
            List<BlockPos> fluidOutputs,
            List<Assignment> actors,
            List<ItemContact> contacts,
            List<Assignment> loading,
            List<BlockPos> reserved,
            List<BlockPos> generic) {
        this(
                assemblies,
                itemInputs,
                itemOutputs,
                fluidInputs,
                fluidOutputs,
                actors,
                contacts,
                loading,
                reserved,
                generic,
                ConduitTopology.EMPTY);
    }

    public static final VatTopology EMPTY = new VatTopology(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of());

    public record Assignment(
            BlockPos actor,
            BlockPos head,
            String use,
            List<BlockPos> itemInputs,
            List<BlockPos> itemOutputs,
            List<BlockPos> fluidInputs,
            List<BlockPos> fluidOutputs) {
        public Assignment(
                BlockPos actor, BlockPos head, String use, List<BlockPos> itemInputs, List<BlockPos> itemOutputs) {
            this(actor, head, use, itemInputs, itemOutputs, List.of(), List.of());
        }
    }

    public record ItemContact(BlockPos input, BlockPos head) {}
}
