package net.impactvector.mobvats.gamecontent.multiblock.vat.menu;

import it.zerono.mods.zerocore.lib.item.inventory.container.*;
import it.zerono.mods.zerocore.lib.item.inventory.container.data.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.VatStatusSnapshot;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;

/** ZeroCore sampled status and operator activation; no writable inventory. */
public final class VatControllerMenu extends ModTileContainer<VatControllerEntity> {
    private final String[] pages = {"Loading controller status", "Loading", "Loading", "Loading"};
    private ItemStack displayedTool = ItemStack.EMPTY;
    private boolean active;
    public boolean active(){return active;}
    public void setActive(boolean value){getTileEntity().sendCommandToServer(value?"activate":"deactivate");}
    public VatControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, (VatControllerEntity)inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }
    public VatControllerMenu(int id, Inventory inventory, VatControllerEntity part) {
        super(inventory.player.level().isClientSide, 200, ContainerFactory.EMPTY, Content.CONTROLLER_MENU.get(), id, inventory, part);
        // The observer uses sampled data only, not ModTileContainer's optional whole-part NBT channel.
        syncFrom(null);
        BooleanData.sampled(1,this,()->part.getMultiblockController().map(c->c.isMachineActive()).orElse(false),value->active=value);
        for (int i = 0; i < pages.length; ++i) {
            final int page = i;
            StringData.sampled(10, this, 32767, () -> {
                var snapshot = part.getMultiblockController().map(c -> c.statusSnapshot()).orElse(VatStatusSnapshot.DISCONNECTED);
                var text = snapshot.pages().get(page);
                return text.substring(0, Math.min(text.length(),32767));
            }, value -> pages[page] = value);
        }
        ItemStackData.sampled(10, this, () -> part.getMultiblockController().map(c -> c.getTopology().actors().stream()
            .map(a -> part.getLevel().getBlockEntity(a.actor())).filter(p -> p instanceof VatToolPortEntity)
            .map(p -> ((VatToolPortEntity)p).heldItem()).filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY))
            .orElse(ItemStack.EMPTY), value -> displayedTool = value);
    }
    public String page(int index) { return pages[index]; }
    public ItemStack displayedTool() { return displayedTool; }
    @Override public boolean stillValid(Player player) {
        var part = getTileEntity();
        return !part.isRemoved() && player.level() == part.getLevel() && player.level().getBlockEntity(part.getBlockPos()) == part
            && player.distanceToSqr(part.getBlockPos().getX()+.5,part.getBlockPos().getY()+.5,part.getBlockPos().getZ()+.5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
