package net.impactvector.mobvats;

import it.zerono.mods.zerocore.api.multiblock.IMultiblockPart;
import it.zerono.mods.zerocore.api.multiblock.MultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.rectangular.RectangularMultiblockControllerBase;
import it.zerono.mods.zerocore.api.multiblock.validation.IMultiblockValidator;
import it.zerono.mods.zerocore.lib.block.ModTileEntity;
import net.impactvector.mobvats.block.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MultiblockVat extends RectangularMultiblockControllerBase {


    public MultiblockVat(World world) {
        super(world);
    }

    @Override
    public void onAttachedPartWithMultiblockData(IMultiblockPart iMultiblockPart, NBTTagCompound nbtTagCompound) {

    }

    @Override
    protected void onBlockAdded(IMultiblockPart iMultiblockPart) {

    }

    @Override
    protected void onBlockRemoved(IMultiblockPart iMultiblockPart) {

    }

    @Override
    protected void onMachineAssembled() {

    }

    @Override
    protected void onMachineRestored() {

    }

    @Override
    protected void onMachinePaused() {

    }

    @Override
    protected void onMachineDisassembled() {

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
        return 5;
    }

    @Override
    protected int getMinimumYSize() {
        return 6;
    }

    @Override
    protected int getMinimumZSize() {
        return 5;
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
    protected boolean isBlockGoodForInterior(World world, int i, int i1, int i2, IMultiblockValidator iMultiblockValidator) {
        return false;
    }

    @Override
    protected void syncDataFrom(NBTTagCompound nbtTagCompound, ModTileEntity.SyncReason syncReason) {

    }

    @Override
    protected void syncDataTo(NBTTagCompound nbtTagCompound, ModTileEntity.SyncReason syncReason) {

    }
}
