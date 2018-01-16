package net.impactvector.mobvats.item;


import net.impactvector.mobvats.MobVats;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Created by impactvector on 11/30/2016.
 */
public class ItemEssence extends ItemBase {

    public ItemEssence(String itemName) {
        super(itemName);
        this.setMaxStackSize(16);
    }

//    @Override
//    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
//
//        if (worldIn.isRemote)
//            return EnumActionResult.PASS;
//
//        if (!hasMobName(stack))
//            return EnumActionResult.FAIL;
//
//        TileEntity te = worldIn.getTileEntity(pos);
//        if (te instanceof TileEntityReactorHead) {
//            if (!MobVats.mobRegistry.isValidMobName(((TileEntityReactorHead) te).getMobName())) {
//                ((TileEntityReactorHead) te).setMobName(getMobName(stack), getDisplayName(stack), getXp(stack));
//                if (!playerIn.capabilities.isCreativeMode)
//                    stack.stackSize--;
//                return EnumActionResult.SUCCESS;
//            }
//        }
//
//        return EnumActionResult.FAIL;
//    }


    /**
     * All we store in the prism is the name of the mob
     */
    static final String NBT_MOBNAME = "mobName";
    static final String NBT_DISPLAYNAME = "displayName";
    static final String NBT_XP_VALUE = "mobXpCost";
    public static void setMobName(ItemStack itemStack, String mobName, String displayName, int xp) {

//        if (xp <= 0)
//            xp = MobRegistry.MobInfo.MIN_XP_VALUE;

        if (itemStack.getTagCompound() == null)
            itemStack.setTagCompound(new NBTTagCompound());

        itemStack.getTagCompound().setString(NBT_MOBNAME, mobName);
        itemStack.getTagCompound().setString(NBT_DISPLAYNAME, displayName);
        itemStack.getTagCompound().setInteger(NBT_XP_VALUE, xp);
    }

    public static String getMobName(ItemStack itemStack) {

        if (itemStack.getTagCompound() == null)
            return "";

        return itemStack.getTagCompound().getString(NBT_MOBNAME);
    }


    public static String getDisplayName(ItemStack itemStack) {

        if (itemStack.getTagCompound() == null)
            return "";

        return itemStack.getTagCompound().getString(NBT_DISPLAYNAME);
    }

    public static int getXp(ItemStack itemStack) {

        if (itemStack.getTagCompound() == null)
            return 1;

        return itemStack.getTagCompound().getInteger(NBT_XP_VALUE);
    }

//    public static boolean hasMobName(ItemStack itemStack) {
//
//        if (itemStack.getItem() != ModItems.itemEssence)
//            return false;
//
//        return MobVats.mobRegistry.isValidMobName(getMobName(itemStack));
//    }

//    @SideOnly(Side.CLIENT)
//    @Override
//    public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced) {
//
////        tooltip.add(StringHelper.localize(Lang.TAG_TOOLTIP + BASENAME + ".0"));
////        tooltip.add(StringHelper.localize(Lang.TAG_TOOLTIP + BASENAME + ".1"));
//        if (stack != null && hasMobName(stack) && MobVats.mobRegistry.isValidMobName(getMobName(stack))) {
//            String displayName = getDisplayName(stack);
//            if (!displayName.equals(""))
//                tooltip.add(TextFormatting.GREEN + String.format("Mob: %s", StringHelper.localize(displayName)));
//
////            int xp = MobVats.mobRegistry.getSpawnXp(getMobName(stack));
////            EnumMobFactoryTier t = MobVats.tierMapper.getTierForEntity(getMobName(stack), xp);
////            tooltip.add(TextFormatting.BLUE + t.getTranslated(Lang.WAILA_FACTORY_TIER));
//        }
//    }

//    @Override
//    public boolean hasEffect(ItemStack stack) {
//
//        return hasMobName(stack);
//    }
//
//    public static ItemStack getItemStack(String mobVatsName, int xp) {
//        //return null;
//        if (!MobVats.mobRegistry.isValidMobName(mobVatsName))
//            return null;
//
//        ItemStack itemStack = new ItemStack(ModItems.itemEssence);
//        setMobName(itemStack, mobVatsName, mobVatsName, xp);
//        return itemStack;
//    }
}