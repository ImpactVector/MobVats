package net.impactvector.mobvats.gamecontent.creative;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** No inventory: samples are identity only, and simulation never mutates them. */
public final class CreativeEndpointEntity extends BlockEntity {
    private ItemStack item=ItemStack.EMPTY;
    private FluidStack fluid=FluidStack.EMPTY;
    public CreativeEndpointEntity(BlockPos pos,BlockState state){super(CreativeContent.ENDPOINT.get(),pos,state);}
    public CreativeDeviceType type(){return ((CreativeDeviceBlock)getBlockState().getBlock()).type();}
    public ItemStack itemSample(){return item.copy();}
    public FluidStack fluidSample(){return fluid.copy();}
    public boolean accepts(ItemStack stack){return !stack.isEmpty() && (item.isEmpty() || ItemStack.isSameItemSameComponents(item,stack));}
    public boolean accepts(FluidStack stack){return !stack.isEmpty() && (fluid.isEmpty() || FluidStack.isSameFluidSameComponents(fluid,stack));}
    public boolean interact(Player player,InteractionHand hand){
        if(level.isClientSide)return true;
        var held=player.getItemInHand(hand);
        if(player.isShiftKeyDown()){
            if(held.isEmpty()){item=ItemStack.EMPTY;fluid=FluidStack.EMPTY;}
            else if(type().item())item=held.copyWithCount(1);
            else {var sample=FluidUtil.getFluidContained(held);if(sample.isEmpty())return true;fluid=sample.get().copyWithAmount(1);}
            setChanged();
        }
        String identity=type().item()?(item.isEmpty()?"":item.getHoverName().getString()):(fluid.isEmpty()?"":fluid.getHoverName().getString());
        player.displayClientMessage(Component.literal("Creative "+(type().source()?"source: ":"sink: ")+(identity.isEmpty()?(type().source()?"unconfigured":"accept all"):identity)),true);
        return true;
    }
    private final IItemHandler items=new IItemHandler(){
        public int getSlots(){return 1;}
        private void slot(int slot){if(slot!=0)throw new IndexOutOfBoundsException(slot);}
        public ItemStack getStackInSlot(int slot){slot(slot);return type().source()&&!item.isEmpty()?item.copyWithCount(item.getMaxStackSize()):ItemStack.EMPTY;}
        public int getSlotLimit(int slot){slot(slot);return Integer.MAX_VALUE;}
        public boolean isItemValid(int slot,ItemStack stack){slot(slot);return !type().source()&&accepts(stack);}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return isItemValid(slot,stack)?ItemStack.EMPTY:stack;}
        public ItemStack extractItem(int slot,int amount,boolean simulate){slot(slot);return type().source()&&!item.isEmpty()&&amount>0?item.copyWithCount(Math.min(amount,item.getMaxStackSize())):ItemStack.EMPTY;}
    };
    public IItemHandler items(){return items;}
    private final IFluidHandler fluids=new IFluidHandler(){
        private void tank(int tank){if(tank!=0)throw new IndexOutOfBoundsException(tank);}
        public int getTanks(){return 1;}
        public FluidStack getFluidInTank(int tank){tank(tank);return type().source()&&!fluid.isEmpty()?fluid.copyWithAmount(Integer.MAX_VALUE):FluidStack.EMPTY;}
        public int getTankCapacity(int tank){tank(tank);return Integer.MAX_VALUE;}
        public boolean isFluidValid(int tank,FluidStack stack){tank(tank);return !type().source()&&accepts(stack);}
        public int fill(FluidStack stack,FluidAction action){return !type().source()&&accepts(stack)?stack.getAmount():0;}
        public FluidStack drain(FluidStack stack,FluidAction action){return !fluid.isEmpty()&&FluidStack.isSameFluidSameComponents(fluid,stack)?drain(stack.getAmount(),action):FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){return type().source()&&!fluid.isEmpty()&&amount>0?fluid.copyWithAmount(amount):FluidStack.EMPTY;}
    };
    public IFluidHandler fluids(){return fluids;}
    public CompoundTag configuration(HolderLookup.Provider registries){
        var tag=new CompoundTag();if(!item.isEmpty())tag.put("itemSample",item.save(registries));if(!fluid.isEmpty())tag.put("fluidSample",fluid.save(registries));return tag;
    }
    public void configuration(CompoundTag tag,HolderLookup.Provider registries){
        item=ItemStack.parseOptional(registries,tag.getCompound("itemSample"));if(!item.isEmpty())item.setCount(1);
        fluid=FluidStack.parseOptional(registries,tag.getCompound("fluidSample"));if(!fluid.isEmpty())fluid.setAmount(1);setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.put("creativeConfiguration",configuration(registries));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);configuration(tag.getCompound("creativeConfiguration"),registries);}
}
