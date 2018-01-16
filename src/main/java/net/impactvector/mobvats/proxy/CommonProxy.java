package net.impactvector.mobvats.proxy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.SidedProxy;

public class CommonProxy {
    public void registerItemRenderer(Item item, int meta, String id) {
    }

    public void sendPlayerStatusMessage(EntityPlayer player, ITextComponent message) {
    }
}
