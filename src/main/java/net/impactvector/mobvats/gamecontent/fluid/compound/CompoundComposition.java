package net.impactvector.mobvats.gamecontent.fluid.compound;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Formula quantities, independent of the volume of the containing stack. Never expands tags. */
public record CompoundComposition(List<Entry> entries) {
    public enum Kind {
        ITEM,
        FLUID
    }

    public record Entry(Kind kind, String selector, int amount) {
        public Entry {
            Objects.requireNonNull(kind);
            selector = canonicalSelector(kind, selector);
            if (amount <= 0) throw new IllegalArgumentException("Composition amount must be positive");
        }

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING
                                .xmap(Kind::valueOf, Kind::name)
                                .fieldOf("kind")
                                .forGetter(Entry::kind),
                        Codec.STRING.fieldOf("selector").forGetter(Entry::selector),
                        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Entry::amount))
                .apply(i, Entry::new));
    }

    public CompoundComposition {
        var combined = new TreeMap<String, Entry>();
        for (var e : entries) {
            var key = e.kind().name() + ":" + e.selector();
            combined.merge(key, e, (a, b) -> new Entry(a.kind(), a.selector(), Math.addExact(a.amount(), b.amount())));
        }
        entries = List.copyOf(combined.values());
        if (entries.isEmpty() || entries.size() > 4096)
            throw new IllegalArgumentException("Composition requires 1 to 4096 entries");
    }

    public static final Codec<CompoundComposition> CODEC =
            Entry.CODEC.listOf().xmap(CompoundComposition::new, CompoundComposition::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, CompoundComposition> STREAM_CODEC = new StreamCodec<>() {
        public void encode(RegistryFriendlyByteBuf buf, CompoundComposition value) {
            buf.writeVarInt(value.entries.size());
            for (var e : value.entries) {
                buf.writeEnum(e.kind());
                buf.writeUtf(e.selector());
                buf.writeVarInt(e.amount());
            }
        }

        public CompoundComposition decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 1 || size > 4096) throw new IllegalArgumentException("Invalid composition entry count");
            var entries = new ArrayList<Entry>();
            for (int i = 0; i < size; i++)
                entries.add(new Entry(buf.readEnum(Kind.class), buf.readUtf(), buf.readVarInt()));
            return new CompoundComposition(entries);
        }
    };

    public static String canonicalSelector(Kind kind, String text) {
        JsonElement value;
        if (text.startsWith("{") || text.startsWith("[")) value = JsonParser.parseString(text);
        else {
            boolean tag = text.startsWith("#");
            var id = net.minecraft.resources.ResourceLocation.parse(tag ? text.substring(1) : text);
            var object = new JsonObject();
            object.addProperty(tag ? "tag" : kind == Kind.ITEM ? "item" : "fluid", id.toString());
            value = object;
        }
        var result = canonicalJson(value).toString();
        if (result.length() > 32767)
            throw new IllegalArgumentException("Composition selector exceeds network string limit");
        return result;
    }

    private static JsonElement canonicalJson(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            new TreeSet<>(value.getAsJsonObject().keySet())
                    .forEach(k ->
                            result.add(k, canonicalJson(value.getAsJsonObject().get(k))));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(e -> result.add(canonicalJson(e)));
            return result;
        }
        return value.deepCopy();
    }
}
