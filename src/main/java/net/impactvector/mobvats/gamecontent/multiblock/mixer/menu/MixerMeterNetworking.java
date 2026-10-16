package net.impactvector.mobvats.gamecontent.multiblock.mixer.menu;

import net.impactvector.mobvats.MobVats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MixerMeterNetworking {
    public record Quantity(int containerId, String text) implements CustomPacketPayload {
        public static final Type<Quantity> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MobVats.MOD_ID, "meter_quantity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Quantity> CODEC = new StreamCodec<>() {
            public void encode(RegistryFriendlyByteBuf buffer, Quantity value) {
                buffer.writeVarInt(value.containerId());
                buffer.writeUtf(value.text(), 64);
            }

            public Quantity decode(RegistryFriendlyByteBuf buffer) {
                return new Quantity(buffer.readVarInt(), buffer.readUtf(64));
            }
        };

        public Type<Quantity> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToServer(
                        Quantity.TYPE,
                        Quantity.CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            var player = context.player();
                            if (player.containerMenu instanceof MixerMeterMenu menu
                                    && menu.containerId == payload.containerId()) {
                                if (!menu.applyQuantity(player, payload.text()))
                                    player.displayClientMessage(
                                            net.minecraft.network.chat.Component.translatable(
                                                    "message.mobvats.meter_invalid_quantity"),
                                            true);
                            }
                        })));
    }

    private MixerMeterNetworking() {}
}
