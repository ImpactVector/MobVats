package net.impactvector.mobvats.proxy;

import net.impactvector.mobvats.MobVats;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.model.ModelLoader;

public class ClientProxy extends CommonProxy {
    @Override
    public void registerItemRenderer(Item item, int meta, String id) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(MobVats.MODID + ":" + id, "inventory"));
    }

    public void sendPlayerStatusMessage(EntityPlayer player, ITextComponent message) {
        player.sendMessage(message);
    }
}
