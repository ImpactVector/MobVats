package net.impactvector.mobvats.gamecontent.multiblock.common;
import net.impactvector.mobvats.gamecontent.Content;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatItemPortEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import it.zerono.mods.zerocore.lib.data.IIoEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
public final class MachinePortMenu extends AbstractContainerMenu {
    public final BlockEntity port;
    private final IIoEntity io;
    private final ContainerData data=new SimpleContainerData(1);
    private final int stagingSlots;
    public MachinePortMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) { this(id,inventory,inventory.player.level().getBlockEntity(buffer.readBlockPos())); }
    public MachinePortMenu(int id, Inventory inventory, BlockEntity port) {
        super(Content.PORT_MENU.get(),id); this.port=port; this.io=(IIoEntity)port; stagingSlots=port instanceof VatItemPortEntity ? 1 : 0;
        if (port instanceof VatItemPortEntity item) addSlot(new SlotItemHandler(item.staging(),0,80,36) {
            public boolean mayPlace(ItemStack stack) { return io.getIoDirection().isInput() && super.mayPlace(stack); }
        });
        for (int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(inventory,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(inventory,col,8+col*18,142));
        data.set(0,io.getIoDirection().ordinal()); addDataSlots(data);
    }
    public boolean isInput() { return data.get(0)==0; }
    public boolean stillValid(Player player) { return !port.isRemoved() && player.level()==port.getLevel()
        && player.level().getBlockEntity(port.getBlockPos())==port && player.distanceToSqr(port.getBlockPos().getX()+.5,port.getBlockPos().getY()+.5,port.getBlockPos().getZ()+.5)<=64; }
    public void broadcastChanges() { if (port.getLevel()!=null && !port.getLevel().isClientSide) data.set(0,io.getIoDirection().ordinal()); super.broadcastChanges(); }
    public boolean clickMenuButton(Player player,int id) {
        if (id!=0 || !stillValid(player) || player.level().isClientSide) return false;
        io.toggleIoDirection(); broadcastChanges(); return true;
    }
    public ItemStack quickMoveStack(Player player,int index) {
        var slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack=slot.getItem(); var copy=stack.copy();
        if (index<stagingSlots) { if (!moveItemStackTo(stack,stagingSlots,slots.size(),true)) return ItemStack.EMPTY; }
        else if (stagingSlots==0 || !io.getIoDirection().isInput() || !moveItemStackTo(stack,0,stagingSlots,false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack); return copy;
    }
}
