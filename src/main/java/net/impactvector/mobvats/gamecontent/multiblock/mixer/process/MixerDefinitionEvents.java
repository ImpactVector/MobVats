package net.impactvector.mobvats.gamecontent.multiblock.mixer.process;

import com.mojang.logging.LogUtils;
import net.impactvector.mobvats.MobVats;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.fluid.compound.CompoundBucketItem;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MixerDefinitionEvents {
    public record Names(String json) implements CustomPacketPayload {
        public static final Type<Names> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID,"compound_names"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Names> CODEC=new StreamCodec<>() {
            public void encode(RegistryFriendlyByteBuf buf,Names value){buf.writeUtf(value.json(),1048576);}
            public Names decode(RegistryFriendlyByteBuf buf){return new Names(buf.readUtf(1048576));}
        };
        public Type<Names> type(){return TYPE;}
    }
    public static MixerRecipeRegistry.Loaded reload(){var loaded=MixerRecipeRegistry.load(FMLPaths.CONFIGDIR.get().resolve("mobvats/mixer"),s->LogUtils.getLogger().error("Mixer definitions: {}",s));MixerRecipeRegistry.publish(loaded);LogUtils.getLogger().info("Loaded {} Mixer recipes",loaded.recipes().size());return loaded;}
    public static void register(IEventBus bus){
        bus.addListener((RegisterCapabilitiesEvent event)->event.registerItem(Capabilities.FluidHandler.ITEM,(stack,context)->new CompoundBucketItem.Handler(stack),ProductFluids.buckets().toArray(net.minecraft.world.item.Item[]::new)));
        bus.addListener((RegisterPayloadHandlersEvent event)->event.registrar("1").playToClient(Names.TYPE,Names.CODEC,(payload,context)->context.enqueueWork(()->{net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors.registryAccess(context.player().registryAccess());MixerRecipeRegistry.clientNames(payload.json());})));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event)->{net.impactvector.mobvats.gamecontent.multiblock.common.ResourceSelectors.registryAccess(event.getServer().registryAccess());reload();});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event)->{if(event.getEntity() instanceof ServerPlayer player && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection,Names.TYPE.id()))PacketDistributor.sendToPlayer(player,new Names(MixerRecipeRegistry.namesJson()));});
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event)->event.getDispatcher().register(Commands.literal("mobvats").requires(s->s.hasPermission(2)).then(Commands.literal("reload_mixer").executes(c->{var loaded=reload();for(var player:c.getSource().getServer().getPlayerList().getPlayers())if(net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection,Names.TYPE.id()))PacketDistributor.sendToPlayer(player,new Names(MixerRecipeRegistry.namesJson()));c.getSource().sendSuccess(()->Component.literal("Loaded "+loaded.recipes().size()+" Mixer recipes; "+loaded.errors().size()+" diagnostics. See server log."),true);return loaded.recipes().size();}))));
    }
    private MixerDefinitionEvents(){}
}
