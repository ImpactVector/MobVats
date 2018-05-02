package net.impactvector.mobvats;

import io.netty.buffer.ByteBuf;
import it.zerono.mods.zerocore.api.multiblock.IMultiblockPart;
import it.zerono.mods.zerocore.api.multiblock.MultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.rectangular.RectangularMultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;
import it.zerono.mods.zerocore.lib.IDebugMessages;
import it.zerono.mods.zerocore.lib.IDebuggable;
import it.zerono.mods.zerocore.lib.block.ModTileEntity;
import net.impactvector.mobvats.block.*;
import net.impactvector.mobvats.helpers.MobAssembly;
import net.impactvector.mobvats.init.ModBlocks;
import net.impactvector.mobvats.interfaces.IActivateable;
import net.impactvector.mobvats.net.CommonPacketHandler;
import net.impactvector.mobvats.net.message.multiblock.VatUpdateMessage;
import net.impactvector.mobvats.tileentity.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.HashSet;
import java.util.Set;

public class MultiblockVat extends RectangularMultiblockControllerBase implements IActivateable, IDebuggable {

    // Updates
    private Set<EntityPlayer> updatePlayers;
    private int ticksSinceLastUpdate;
    private static final int ticksBetweenUpdates = 3;

    // Game stuff - stored
    protected boolean active;

    // Lists of connected parts
//    private Set<TileEntityReactorPowerTap> attachedPowerTaps;
//    private Set<ITickableMultiblockPart> attachedTickables;

    private Set<TileEntityVatItemPort> attachedItemPorts;
    private Set<TileEntityVatController> attachedControllers;
    private Set<TileEntityVatFluidPort> attachedFluidPorts;

    private Set<TileEntityVatHarness> attachedHarnesses;
    public Set<TileEntityVatHead> attachedHeads;
    public Set<TileEntityVatFlesh> attachedFlesh;
    private Set<TileEntityVatDrain> attachedDrains;

    private Set<TileEntityVatGlass> attachedGlass;

    private MobAssembly[] _mobAssemblies;

    public MultiblockVat(World world) {
        super(world);

        active = false;

        attachedItemPorts = new HashSet<TileEntityVatItemPort>();
        attachedControllers = new HashSet<TileEntityVatController>();
        attachedFluidPorts = new HashSet<TileEntityVatFluidPort>();
        attachedHarnesses = new HashSet<TileEntityVatHarness>();
        attachedHeads = new HashSet<TileEntityVatHead>();
        attachedFlesh = new HashSet<TileEntityVatFlesh>();
        attachedDrains = new HashSet<TileEntityVatDrain>();
        attachedGlass = new HashSet<TileEntityVatGlass>();

		this._mobAssemblies = null;

        updatePlayers = new HashSet<EntityPlayer>();

        ticksSinceLastUpdate = 0;
    }

    public void beginUpdatingPlayer(EntityPlayer playerToUpdate) {
        updatePlayers.add(playerToUpdate);
        sendIndividualUpdate(playerToUpdate);
    }

    public void stopUpdatingPlayer(EntityPlayer playerToRemove) {
        updatePlayers.remove(playerToRemove);
    }

    @Override
    public void onAttachedPartWithMultiblockData(IMultiblockPart iMultiblockPart, NBTTagCompound nbtTagCompound) {

    }

    @Override
    protected void onBlockAdded(IMultiblockPart part) {
        if (part instanceof TileEntityVatItemPort) {
            attachedItemPorts.add((TileEntityVatItemPort)part);
        } else if (part instanceof TileEntityVatDrain) {
            attachedDrains.add((TileEntityVatDrain)part);
        } else if (part instanceof TileEntityVatController) {
            attachedControllers.add((TileEntityVatController)part);
        } else if (part instanceof TileEntityVatHead) {
            attachedHeads.add((TileEntityVatHead)part);
        } else if (part instanceof TileEntityVatFlesh) {
            attachedFlesh.add((TileEntityVatFlesh)part);
        } else if (part instanceof TileEntityVatHarness) {
            attachedHarnesses.add((TileEntityVatHarness)part);
        } else if (part instanceof TileEntityVatGlass) {
            attachedGlass.add((TileEntityVatGlass) part);
        }
    }

    @Override
    protected void onBlockRemoved(IMultiblockPart part) {
        if (part instanceof TileEntityVatItemPort) {
            attachedItemPorts.remove((TileEntityVatItemPort)part);
        } else if (part instanceof TileEntityVatDrain) {
            attachedDrains.remove((TileEntityVatDrain)part);
        } else if (part instanceof TileEntityVatController) {
            attachedControllers.remove((TileEntityVatController)part);
        } else if (part instanceof TileEntityVatHead) {
            attachedHeads.remove((TileEntityVatHead)part);
        } else if (part instanceof TileEntityVatFlesh) {
            attachedFlesh.remove((TileEntityVatFlesh)part);
        } else if (part instanceof TileEntityVatHarness) {
            attachedHarnesses.remove((TileEntityVatHarness)part);
        } else if (part instanceof TileEntityVatGlass) {
            attachedGlass.remove((TileEntityVatGlass)part);
        }
    }
    @Override
    protected boolean isMachineWhole(IMultiblockValidator validatorCallback) {

        if (this.attachedControllers.size() < 1) {

            validatorCallback.setLastError("multiblock.validation.reactor.too_few_controllers");
            return false;
        }

        if (!super.isMachineWhole(validatorCallback))
            return false;

        //previous call adds "floating" parts inside the machine
        if (this.attachedHeads.size() < 1) {

            validatorCallback.setLastError("multiblock.validation.vat.too_few_heads");
            return false;
        }
        if (this.attachedHeads.size() > 1) {

            validatorCallback.setLastError("multiblock.validation.vat.too_many_heads");
            return false;
        }

        return true;
    }


    // Static validation helpers
    // Water, air, and metal blocks
    @Override
    protected boolean isBlockGoodForInterior(World world, int x, int y, int z, IMultiblockValidator validatorCallback) {

        BlockPos position = new BlockPos(x, y, z);

        if(world.isAirBlock(position)) { return true; } // Air is OK

        IBlockState blockState = this.WORLD.getBlockState(position);
        Block block = blockState.getBlock();

        Material material = block.getMaterial(blockState);
        if(material == MaterialLiquid.WATER  //||
//				block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK ||
//				block == Blocks.DIAMOND_BLOCK || block == Blocks.EMERALD_BLOCK
                ) {
            return true;
        }

        if (block instanceof VatHead) {
            boolean found = false;
            for (TileEntityVatHead head : this.attachedHeads) {
                if (head.getPos().equals(position))
                {
                    found = true;
                    break;
                }
            }

            if (!found)
                this.attachedHeads.add((TileEntityVatHead) ModBlocks.vatHead.createTileEntity(world, blockState));

            return true;
        }

        if (block instanceof VatFlesh) {
            boolean found = false;
            for (TileEntityVatFlesh flesh : this.attachedFlesh) {
                if (flesh.getPos().equals(position))
                {
                    found = true;
                    break;
                }
            }

            if (!found) {
                this.attachedFlesh.add((TileEntityVatFlesh) ModBlocks.vatFlesh.createTileEntity(world, blockState));
            }

            return true;
        }
        return false;
    }

    @Override
    protected boolean doesPartBelong(IMultiblockPart part) {
        if (part instanceof TileEntityVatHead
                || part instanceof TileEntityVatFlesh
                || part instanceof VatFlesh
                || part instanceof VatHead)
            return true;
        else
            return super.doesPartBelong(part);
    }

        @Override
    protected void onMachineAssembled() {
//        Iterator<TileEntityVatHead> i = attachedHeads.iterator().forEachRemaining( s -> { s. });
    }

    @Override
    protected void onMachineRestored() {
        this.onMachineAssembled();
    }

    @Override
    protected void onMachinePaused() {

    }

    @Override
    protected void onMachineDisassembled() {
        this.active = false;
    }

    @Override
    protected int getMinimumNumberOfBlocksForAssembledMachine() {
        return 0;
    }

    @Override
    protected int getMaximumXSize() {
        return 5;
    }

    @Override
    protected int getMaximumZSize() {
        return 5;
    }

    @Override
    protected int getMaximumYSize() {
        return 6;
    }

    @Override
    protected int getMinimumXSize() {
        return 3;
    }

    @Override
    protected int getMinimumYSize() {
        return 3;
    }

    @Override
    protected int getMinimumZSize() {
        return 3;
    }

    @Override
    protected void onAssimilate(MultiblockControllerBase multiblockControllerBase) {

    }

    @Override
    protected void onAssimilated(MultiblockControllerBase multiblockControllerBase) {

    }

    @Override
    protected boolean updateServer() {
        return false;
    }

    @Override
    protected void updateClient() {

    }

    @Override
    protected boolean isBlockGoodForFrame(World world, int x, int y, int z, IMultiblockValidator iMultiblockValidator) {

        IBlockState blockState = this.WORLD.getBlockState(new BlockPos(x, y, z));
        Block block = blockState.getBlock();

        iMultiblockValidator.setLastError("multiblock.validation.reactor.invalid_block_for_exterior", x, y, z, block.getLocalizedName());
        return false;
    }

    @Override
    protected boolean isBlockGoodForTop(World world, int i, int i1, int i2, IMultiblockValidator iMultiblockValidator) {
        return false;
    }

    @Override
    protected boolean isBlockGoodForBottom(World world, int i, int i1, int i2, IMultiblockValidator iMultiblockValidator) {
        return false;
    }

    @Override
    protected boolean isBlockGoodForSides(World world, int i, int i1, int i2, IMultiblockValidator iMultiblockValidator) {
        return false;
    }

    @Override
    protected void syncDataFrom(NBTTagCompound nbtTagCompound, ModTileEntity.SyncReason syncReason) {

    }

    @Override
    protected void syncDataTo(NBTTagCompound nbtTagCompound, ModTileEntity.SyncReason syncReason) {

    }

    @Override
    public void getDebugMessages(IDebugMessages iDebugMessages) {

    }

    @Override
    public boolean getActive() {
        return false;
    }

    @Override
    public void setActive(boolean active) {

    }

    // Network & Storage methods
	/*
	 * Serialize a reactor into a given Byte buffer
	 * @param buf The byte buffer to serialize into
	 */
    public void serialize(ByteBuf buf) {
        buf.writeBoolean(active);
    }

    /*
     * Deserialize a reactor's data from a given Byte buffer
     * @param buf The byte buffer containing reactor data
     */
    public void deserialize(ByteBuf buf) {
        // Basic data
        setActive(buf.readBoolean());
    }

    protected IMessage getUpdatePacket() {
        return new VatUpdateMessage(this);
    }

    /**
     * Sends a full state update to a player.
     */
    protected void sendIndividualUpdate(EntityPlayer player) {
        if(this.WORLD.isRemote) { return; }

        CommonPacketHandler.INSTANCE.sendTo(getUpdatePacket(), (EntityPlayerMP)player);
    }
}
