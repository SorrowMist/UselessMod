package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.OptionalDouble;

/** Static expectations for vanilla loot-number JSON; context-dependent providers are rejected. */
public final class NativeNumberExpectations {
    private NativeNumberExpectations() {}

    public static OptionalDouble expected(JsonElement json) {
        if (json == null || json.isJsonNull()) return OptionalDouble.empty();
        if (json.isJsonPrimitive()) {
            if (!json.getAsJsonPrimitive().isNumber()) return OptionalDouble.empty();
            return finite(json.getAsDouble());
        }
        if (!json.isJsonObject()) return OptionalDouble.empty();
        JsonObject object = json.getAsJsonObject();
        if (!object.has("type")) return OptionalDouble.empty();
        String type = object.get("type").getAsString();
        if (type.equals("minecraft:constant")) return expected(object.get("value"));
        if (type.equals("minecraft:uniform")) {
            OptionalDouble min = expected(object.get("min"));
            OptionalDouble max = expected(object.get("max"));
            if (min.isEmpty() || max.isEmpty() || min.getAsDouble() > max.getAsDouble())
                return OptionalDouble.empty();
            return finite((min.getAsDouble() + max.getAsDouble()) / 2.0);
        }
        if (type.equals("minecraft:binomial")) {
            OptionalDouble n = expected(object.get("n"));
            OptionalDouble p = expected(object.get("p"));
            if (n.isEmpty() || p.isEmpty() || n.getAsDouble() < 0
                    || p.getAsDouble() < 0 || p.getAsDouble() > 1) return OptionalDouble.empty();
            return finite(n.getAsDouble() * p.getAsDouble());
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble finite(double value) {
        return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
    }
}
