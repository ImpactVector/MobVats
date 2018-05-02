package net.impactvector.mobvats.block;

import it.zerono.mods.zerocore.lib.block.BlockMultiblockPart;
import it.zerono.mods.zerocore.lib.block.properties.Orientation;
import net.impactvector.mobvats.helpers.MachinePartState;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;

public class VatHead extends PartBase {
    public VatHead(String name)
    {
        super(PartType.VatHead, name, Material.CLAY);

        setDefaultState(blockState.getBaseState().withProperty(Orientation.HFACING, EnumFacing.NORTH));
    }

    @Override
    protected void buildBlockState(BlockStateContainer.Builder builder) {

        super.buildBlockState(builder);
        builder.add(Orientation.HFACING);
    }



//    @Override
//    public boolean onBlockActivated(World world, BlockPos position, IBlockState state, EntityPlayer player, EnumHand hand,
//                                    ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ) {
//
//        TileEntity te = world.getTileEntity(position);
//        if(te == null) { return false; }
//
//        if(player.isSneaking()) {
//
//            // Wrench + Sneak = Dismantle
//            if(StaticUtils.Inventory.isPlayerHoldingWrench(heldItem)) {
//                // Pass simulate == true on the client to prevent creation of "ghost" item stacks
//                dismantleBlock(player, world, position, false);
//                return true;
//            }
//
//            return false;
//        }
//
//        if(te instanceof IWrenchable && StaticUtils.Inventory.isPlayerHoldingWrench(heldItem)) {
//            return ((IWrenchable)te).onWrench(player, side);
//        }
//
//        if (!player.worldObj.isRemote){
//            if(heldItem != null
//                    && heldItem.getItem() instanceof ItemVialEssence) {
//
//                if (ItemVialEssence.hasMobName(heldItem)) {
//                    if (te instanceof TileEntityReactorHead) {
//                        if (!MobVats.mobRegistry.isValidMobName(((TileEntityReactorHead) te).getMobName())) {
//                            ((TileEntityReactorHead) te).setMobName(ItemVialEssence.getMobName(heldItem), ItemVialEssence.getDisplayName(heldItem), ItemVialEssence.getXp(heldItem));
//                            if (!player.capabilities.isCreativeMode)
//                                heldItem.stackSize--;
//                            return true;
//                        }
//                    }
//                }
//                else {
//                    player.addChatComponentMessage(new TextComponentString("Mob essence required."));
//                    return false;
//                }
//            }
//            else {
//                return super.onBlockActivated(world, position, state, player, hand, heldItem, side, hitX, hitY, hitZ);
//            }
//        }
//
//        return true;
//    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos, state.withProperty(Orientation.HFACING, (null != placer) ? placer.getHorizontalFacing().getOpposite() : EnumFacing.NORTH), 2);
    }

    /**
     * Called by ItemBlocks just before a block is actually set in the world, to allow for adjustments to the
     * IBlockstate
     */
    public IBlockState onBlockPlaced(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                     int meta, EntityLivingBase placer) {

        facing = (null != placer) ? placer.getHorizontalFacing().getOpposite() : EnumFacing.NORTH;

        return this.getDefaultState().withProperty(Orientation.HFACING, facing);
    }

    @Override
    public void onBlockAdded(World world, BlockPos position, IBlockState state) {

        EnumFacing newFacing = Orientation.suggestDefaultHorizontalFacing(world, position,
                state.getValue(Orientation.HFACING));

        world.setBlockState(position, state.withProperty(Orientation.HFACING, newFacing), 2);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(Orientation.HFACING, EnumFacing.getHorizontal(meta));
    }
    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(Orientation.HFACING).getIndex();
    }
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, Orientation.HFACING);
    }

//
//    @Override
//    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
//
//        TileEntity te = world.getTileEntity(data.getPos());
//        if (te instanceof TileEntityReactorHead) {
//            TileEntityReactorHead head = (TileEntityReactorHead)te;
//
//            if (!head.getMobDisplayName().equals((""))) {
//
//                probeInfo.text(TextFormatting.GREEN + "Mob: " + head.getMobDisplayName());
//            }
//            else {
//                probeInfo.text(TextFormatting.GREEN + "Mob: " + "None");
//            }
//        }
//    }
//
//    @Override
//    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
//        TileEntity te = accessor.getTileEntity();
//        if (te instanceof TileEntityReactorHead) {
//            TileEntityReactorHead head = (TileEntityReactorHead) te;
//            if (!head.getMobDisplayName().equals((""))) {
//                currenttip.add(TextFormatting.GREEN + "Mob: " + head.getMobDisplayName());
//            }
//            else {
//                currenttip.add(TextFormatting.GREEN + "Mob: " + "None");
//            }
//        }
//        return currenttip;
//    }
}
