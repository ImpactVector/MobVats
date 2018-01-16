package net.impactvector.mobvats.item;

import net.impactvector.mobvats.MobVats;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;

/*
 * Swooped from Ipsis/Woot 11/28/2016
 * https://github.com/Ipsis/Woot
 */
public class ItemSyringe extends ItemBase {

    public ItemSyringe(String itemName) {

        super(itemName);
        setMaxStackSize(1);
    }
//    @Override
//    @SideOnly(Side.CLIENT)
//    public void onPostClientRegister() {
//        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(this.getRegistryName(), "inventory"));
//    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {

        if (attacker.world.isRemote)
            return false;

        if (!(attacker instanceof EntityPlayer))
            return false;

//        String mobVatsName = MobVats.mobRegistry.onEntityLiving((EntityLiving) target);
//        if (!MobVats.mobRegistry.isValidMobName(mobVatsName))
//            return false;
//        String displayName = MobVats.mobRegistry.getDisplayName(mobVatsName);
//
//        ItemStack vialStack = new ItemStack(ModItems.vialEssence, 1);
//
//        if (!MobVats.mobRegistry.isPrismValid(mobVatsName)) {
//            ((EntityPlayer) attacker).addChatComponentMessage(
//                    new TextComponentString(String.format(
//                            StringHelper.localize(Lang.CHAT_PRISM_INVALID), displayName, mobVatsName)));
//            return false;
//        }
//
//        ItemVialEssence.setMobName(vialStack, mobVatsName, displayName, EntityHelper.getExperienceValue((EntityLiving)target));
//        ((EntityPlayer) attacker).addChatComponentMessage(
//                new TextComponentString(String.format(
//                        StringHelper.localize(Lang.CHAT_PRISM_PROGRAM), displayName, mobVatsName)));
//
//        EntityPlayer player = (EntityPlayer)attacker;
//        if (!player.capabilities.isCreativeMode) {
//            ItemStack bottleStack = new ItemStack(Items.GLASS_BOTTLE);
//            if (player.inventory.hasItemStack(bottleStack)) {
//                int slot = player.inventory.getSlotFor(bottleStack);
//                ItemStack playerBottleStack = player.inventory.getStackInSlot(slot);
//                new MobVatsItemGlassBottle().turnBottleIntoItem(playerBottleStack, player, vialStack);
//            }
//        } else {
//            //if creative, just give them the blood
//            if (!player.inventory.addItemStackToInventory(vialStack)) {
//                player.dropItem(vialStack, false);
//            }
//        }


        return true;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        if (worldIn.isRemote)
            return new ActionResult(EnumActionResult.PASS, playerIn.getHeldItem(handIn));

        if (!playerIn.capabilities.isCreativeMode) {
            ItemStack bottleStack = new ItemStack(Items.GLASS_BOTTLE);
            if (playerIn.inventory.hasItemStack(bottleStack)) {
                int slot = playerIn.inventory.getSlotFor(bottleStack);
                ItemStack playerBottleStack = playerIn.inventory.getStackInSlot(slot);
                playerIn.attackEntityFrom(DamageSource.causePlayerDamage(playerIn), 2); //1 full heart of damage -- TODO: figure out if armor reduces it -- it should not
//                new MobVatsItemGlassBottle().turnBottleIntoItem(playerBottleStack, playerIn, new ItemStack(ModItems.vialEssence, 1));
                return new ActionResult(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
            } else {
                return new ActionResult(EnumActionResult.PASS, playerIn.getHeldItem(handIn));
            }
        } else {
            //if creative, just give them the blood
            ItemStack vialStack = new ItemStack(ModItems.itemEssence);
            if (!playerIn.inventory.addItemStackToInventory(vialStack)) {
                playerIn.dropItem(vialStack, false);
            }
            return new ActionResult(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
        }
    }





}

