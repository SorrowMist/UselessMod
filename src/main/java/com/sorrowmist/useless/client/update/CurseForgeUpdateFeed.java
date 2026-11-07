package com.sorrowmist.useless.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

/** Pure cfwidget feed selection; deliberately never uses the cross-version "download" field. */
final class CurseForgeUpdateFeed {
    private static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::uploadedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Candidate::version, UpdateVersion::compare);

    private CurseForgeUpdateFeed() {
    }

    static String pickLatest(JsonElement root, String minecraftVersion) {
        if (root == null || !root.isJsonObject() || minecraftVersion == null || minecraftVersion.isBlank()) {
            return null;
        }
        JsonElement files = root.getAsJsonObject().get("files");
        if (files == null || !files.isJsonArray()) {
            return null;
        }

        Candidate latest = null;
        for (JsonElement element : files.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            if ("alpha".equalsIgnoreCase(stringField(file, "type"))) {
                continue;
            }
            JsonElement versions = file.get("versions");
            if (versions == null || !versions.isJsonArray()
                    || !contains(versions.getAsJsonArray(), minecraftVersion)
                    || !contains(versions.getAsJsonArray(), "Forge")) {
                continue;
            }
            String version = UpdateVersion.normalize(stringField(file, "display"));
            if (version == null) {
                continue;
            }
            Candidate candidate = new Candidate(version, parseUploadedAt(stringField(file, "uploaded_at")));
            // Follow upstream upload order; use numeric version order only for equal/missing dates.
            if (latest == null || ORDER.compare(candidate, latest) > 0) {
                latest = candidate;
            }
        }
        return latest == null ? null : latest.version();
    }

    private static boolean contains(JsonArray values, String expected) {
        for (JsonElement value : values) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && expected.equalsIgnoreCase(value.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String stringField(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static Instant parseUploadedAt(String timestamp) {
        try {
            return timestamp == null ? null : Instant.parse(timestamp);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private record Candidate(String version, Instant uploadedAt) {
    }
}
