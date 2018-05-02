package net.impactvector.mobvats;

import it.zerono.mods.zerocore.lib.IModInitializationHandler;
import it.zerono.mods.zerocore.lib.config.ConfigHandler;
import net.impactvector.mobvats.client.MobVatsTab;
import net.impactvector.mobvats.data.Config;
import net.impactvector.mobvats.init.ObjectsHandler;
import net.impactvector.mobvats.proxy.CommonProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import javax.annotation.Nonnull;

@Mod(modid = MobVats.MODID, version = MobVats.VERSION, name = MobVats.NAME)
public class MobVats implements IModInitializationHandler {
    public static final String MODID = "mobvats";
    public static final String VERSION = "0.1";
    public static final String NAME = "Mob Vats";
    public static final Config CONFIG;
    public static final MobVatsTab s_tab = new MobVatsTab();
    private final ObjectsHandler _objectsHandler;


    public MobVats() {
        this._objectsHandler = new ObjectsHandler(new ConfigHandler[]{CONFIG});
    }

    public static MobVats getInstance() {
        return s_instance;
    }

    public static CommonProxy getProxy() {
        return s_proxy;
    }

    public static MobVatsTab getTab() {
        return s_tab;
    }

    @SidedProxy(serverSide = "net.impactvector.mobvats.proxy.CommonProxy", clientSide = "net.impactvector.mobvats.proxy.ClientProxy")
    public static CommonProxy s_proxy;

    @Mod.Instance(MODID)
    public static MobVats s_instance;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        System.out.println(NAME + ": Begin load...");
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {

    }

    public static void sendStatusMessage(@Nonnull EntityPlayer player, @Nonnull ITextComponent message) {
        getProxy().sendPlayerStatusMessage(player, message);
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        System.out.println(NAME + ": Load complete.");
    }

//    @Mod.EventBusSubscriber
//    public static class RegistrationHandler {
//        @SubscribeEvent
//        public static void registerBlocks(RegistryEvent.Register<Block> event) {
//            ModBlocks.register(event.getRegistry());
//        }
//        @SubscribeEvent
//        public static void registerItems(RegistryEvent.Register<Item> event) {
//            ModItems.register(event.getRegistry());
//            ModBlocks.registerItemBlocks(event.getRegistry());
//        }
//        @SubscribeEvent
//        public static void registerItems(ModelRegistryEvent event) {
//            ModItems.registerModels();
//            ModBlocks.registerModels();
//        }
//    }
    static {
        CONFIG = new Config();
    }
}
