package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.function.Consumer;

/** Independent schema history. Steps add missing data without inferring or refreshing authored values. */
public final class MobProfileSchema {
    public static final int CURRENT_VERSION = 2;
    private static final Map<Integer, Consumer<JsonObject>> UPGRADES = Map.of(1, document -> {
        if (!document.has("minimum_vat_tier")) document.addProperty("minimum_vat_tier", 1);
    });

    public static int version(JsonObject document) {
        var value = document.get("schema_version");
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("schema_version must be an integer");
        try {
            int version = value.getAsBigDecimal().intValueExact();
            if (version < 1 || version > CURRENT_VERSION)
                throw new IllegalArgumentException("Unsupported schema_version: " + version);
            return version;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("schema_version must be a 32-bit integer", error);
        }
    }

    public static JsonObject upgrade(JsonObject original) {
        var result = original.deepCopy();
        for (int version = version(result); version < CURRENT_VERSION; version++) {
            var step = UPGRADES.get(version);
            if (step == null) throw new IllegalArgumentException("No schema migration from " + version);
            step.accept(result);
            result.addProperty("schema_version", version + 1);
        }
        return result;
    }

    private MobProfileSchema() {}
}
