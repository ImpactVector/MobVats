package net.impactvector.mobvats.gamecontent.multiblock.vat.menu;

import it.zerono.mods.zerocore.lib.item.inventory.container.*;
import it.zerono.mods.zerocore.lib.item.inventory.container.data.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;

/** ZeroCore sampled status and operator activation; no writable inventory. */
public final class VatControllerMenu extends ModTileContainer<VatControllerEntity> {

    private boolean active;

    public boolean active() {
        return active;
    }

    public void setActive(boolean value) {
        getTileEntity().sendCommandToServer(value ? "activate" : "deactivate");
    }

    public VatControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, (VatControllerEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }

    public VatControllerMenu(int id, Inventory inventory, VatControllerEntity part) {
        super(
                inventory.player.level().isClientSide,
                200,
                ContainerFactory.EMPTY,
                Content.CONTROLLER_MENU.get(),
                id,
                inventory,
                part);
        // The observer uses sampled data only, not ModTileContainer's optional whole-part NBT channel.
        syncFrom(null);
        if (inventory.player instanceof net.minecraft.server.level.ServerPlayer server) observer = server;
        BooleanData.sampled(
                1,
                this,
                () -> part.getMultiblockController()
                        .map(c -> c.isMachineActive())
                        .orElse(false),
                value -> active = value);
    }

    private final VatSpecimenNetworking.Receiver receiver = new VatSpecimenNetworking.Receiver();
    private VatSpecimenNetworking.View view = VatSpecimenNetworking.View.EMPTY;
    private java.util.UUID selected;
    private final java.util.ArrayDeque<VatSpecimenNetworking.Chunk> pending = new java.util.ArrayDeque<>();
    private String lastSent = "";
    private int refresh;
    private long generation;
    private net.minecraft.server.level.ServerPlayer observer;

    public VatSpecimenNetworking.View view() {
        return view;
    }

    public java.util.UUID selectedId() {
        return selected;
    }

    public void select(java.util.UUID id) {
        selected = id;
    }

    public net.impactvector.mobvats.gamecontent.multiblock.vat.VatSpecimenSnapshot selected() {
        return view.specimens().stream()
                .filter(s -> s.id().equals(selected))
                .findFirst()
                .orElse(null);
    }

    public void applyView(VatSpecimenNetworking.View value) {
        view = value;
        if (selected() == null)
            selected = view.specimens().isEmpty()
                    ? null
                    : view.specimens().getFirst().id();
    }

    public void receive(VatSpecimenNetworking.Chunk chunk) {
        receiver.accept(chunk).ifPresent(this::applyView);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (observer == null) return;
        if (pending.isEmpty() && refresh-- <= 0) {
            refresh = 19;
            var next = getTileEntity()
                    .getMultiblockController()
                    .map(VatSpecimenNetworking.View::capture)
                    .orElse(VatSpecimenNetworking.View.EMPTY);
            String encoded = next.encode();
            if (!encoded.equals(lastSent)) {
                lastSent = encoded;
                pending.addAll(VatSpecimenNetworking.chunks(containerId, ++generation, encoded));
            }
        }
        if (!pending.isEmpty())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(observer, pending.removeFirst());
    }

    public String page(int index) {
        return view.diagnostics().get(index);
    }

    @Override
    public boolean stillValid(Player player) {
        var part = getTileEntity();
        return !part.isRemoved()
                && player.level() == part.getLevel()
                && player.level().getBlockEntity(part.getBlockPos()) == part
                && player.distanceToSqr(
                                part.getBlockPos().getX() + .5,
                                part.getBlockPos().getY() + .5,
                                part.getBlockPos().getZ() + .5)
                        <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
