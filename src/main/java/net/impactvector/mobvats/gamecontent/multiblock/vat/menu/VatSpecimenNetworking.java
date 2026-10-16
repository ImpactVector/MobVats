package net.impactvector.mobvats.gamecontent.multiblock.vat.menu;

import com.google.gson.Gson;
import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Menu-only, server-to-client bounded chunks. Complete generations replace the view atomically. */
public final class VatSpecimenNetworking {
    private static final Gson JSON = new Gson();
    public static final int CHUNK_CHARS = 16000;

    public record View(
            boolean formed,
            boolean enabled,
            boolean inhibited,
            List<VatSpecimenSnapshot> specimens,
            List<String> diagnostics) {
        public static final View EMPTY =
                new View(false, false, false, List.of(), VatStatusSnapshot.DISCONNECTED.pages());

        public View {
            specimens = List.copyOf(specimens);
            diagnostics = List.copyOf(diagnostics);
        }

        public String encode() {
            return JSON.toJson(this);
        }

        public static View decode(String text) {
            return JSON.fromJson(text, View.class);
        }

        public static View capture(MultiblockVat vat) {
            // Capture detached biology before the legacy diagnostic adapter is queried.
            var specimens = VatSpecimenSnapshot.capture(vat);
            return new View(
                    vat.isAssembled(),
                    vat.isMachineActive(),
                    vat.controllerInhibited(),
                    specimens,
                    vat.statusSnapshot().pages());
        }
    }

    public record Chunk(int menu, long generation, int index, int count, String text) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.parse("mobvats:vat_specimens"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> CODEC = new StreamCodec<>() {
            public void encode(RegistryFriendlyByteBuf b, Chunk v) {
                b.writeVarInt(v.menu);
                b.writeLong(v.generation);
                b.writeVarInt(v.index);
                b.writeVarInt(v.count);
                b.writeUtf(v.text, CHUNK_CHARS);
            }

            public Chunk decode(RegistryFriendlyByteBuf b) {
                return new Chunk(b.readVarInt(), b.readLong(), b.readVarInt(), b.readVarInt(), b.readUtf(CHUNK_CHARS));
            }
        };

        public Type<Chunk> type() {
            return TYPE;
        }
    }

    public static List<Chunk> chunks(int menu, long generation, String text) {
        var result = new ArrayList<Chunk>();
        var parts = new ArrayList<String>();
        for (int start = 0; start < text.length(); ) {
            int end = Math.min(text.length(), start + CHUNK_CHARS);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            parts.add(text.substring(start, end));
            start = end;
        }
        if (parts.isEmpty()) parts.add("");
        for (int i = 0; i < parts.size(); i++) result.add(new Chunk(menu, generation, i, parts.size(), parts.get(i)));
        return List.copyOf(result);
    }

    public static final class Receiver {
        private long generation = -1;
        private int next, count;
        private final StringBuilder text = new StringBuilder();

        public Optional<View> accept(Chunk chunk) {
            if (chunk.generation < generation) return Optional.empty();
            if (chunk.index == 0 && chunk.generation > generation) {
                generation = chunk.generation;
                next = 0;
                count = chunk.count;
                text.setLength(0);
            }
            if (chunk.generation != generation || chunk.index != next || chunk.count != count || count < 1)
                return Optional.empty();
            text.append(chunk.text);
            next++;
            if (next != count) return Optional.empty();
            var view = View.decode(text.toString());
            text.setLength(0);
            return Optional.of(view);
        }
    }

    public static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToClient(
                        Chunk.TYPE,
                        Chunk.CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            if (context.player().containerMenu instanceof VatControllerMenu menu
                                    && menu.containerId == payload.menu) menu.receive(payload);
                        })));
    }

    private VatSpecimenNetworking() {}
}
