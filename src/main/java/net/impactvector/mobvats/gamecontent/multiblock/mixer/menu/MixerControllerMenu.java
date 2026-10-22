package net.impactvector.mobvats.gamecontent.multiblock.mixer.menu;

import it.zerono.mods.zerocore.lib.item.inventory.container.*;
import it.zerono.mods.zerocore.lib.item.inventory.container.data.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.MixerControllerSnapshot;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;

/** ZeroCore sampled status and operator activation; no writable inventory. */
public final class MixerControllerMenu extends ModTileContainer<MixerControllerEntity> {

    public MixerControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, (MixerControllerEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }

    public MixerControllerMenu(int id, Inventory inventory, MixerControllerEntity part) {
        super(
                inventory.player.level().isClientSide,
                200,
                ContainerFactory.EMPTY,
                Content.MIXER_CONTROLLER_MENU.get(),
                id,
                inventory,
                part);
        // The observer uses sampled data only, not ModTileContainer's optional whole-part NBT channel.
        syncFrom(null);
        if (inventory.player instanceof net.minecraft.server.level.ServerPlayer server) observer = server;
    }

    private final MixerControllerNetworking.Receiver receiver = new MixerControllerNetworking.Receiver();
    private MixerControllerSnapshot view = MixerControllerSnapshot.EMPTY;
    private final java.util.ArrayDeque<MixerControllerNetworking.Chunk> pending = new java.util.ArrayDeque<>();
    private String lastSent = "";
    private int refresh;
    private long generation;
    private net.minecraft.server.level.ServerPlayer observer;

    public MixerControllerSnapshot view() {
        return view;
    }

    public void applyView(MixerControllerSnapshot value) {
        view = value;
    }

    public void receive(MixerControllerNetworking.Chunk chunk) {
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
                    .map(c -> MixerControllerSnapshot.capture(c, getTileEntity()))
                    .orElse(MixerControllerSnapshot.EMPTY);
            String encoded = next.encode();
            if (!encoded.equals(lastSent)) {
                lastSent = encoded;
                pending.addAll(MixerControllerNetworking.chunks(containerId, ++generation, encoded));
            }
        }
        if (!pending.isEmpty())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(observer, pending.removeFirst());
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
