package com.sorrowmist.useless.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class A6CurseForgeUpdateFeedTest {
    private static final String OLD = "2026-11-01T12:00:00Z";
    private static final String NEW = "2026-11-05T12:00:00Z";

    @Test
    void filtersTheRealMixedFeedByExactMinecraftVersionAndForgeRatherThanTopLevelDownload() {
        JsonObject feed = feed(
                file("Useless Mod 20.2.4.11", "release", OLD, "1.20.1", "Forge"),
                file("Useless Mod 1.20.1-20.2.4.12", "release", NEW, "1.20.1", "Forge"),
                file("Useless Mod 1.21.1-2.4.6.5", "release", NEW, "1.21.1", "NeoForge"),
                file("Useless Mod 99.0.0", "release", NEW, "1.20.1", "NeoForge"),
                file("Useless Mod 99.0.1", "release", NEW, "1.20.1", "Fabric"),
                file("Useless Mod 99.0.2", "release", NEW, "1.20.1", "Quilt"),
                file("Useless Mod 99.0.3", "alpha", NEW, "1.20.1", "Forge"));
        feed.add("download", file("Useless Mod 1.21.1-2.4.6.5", "release", NEW, "1.21.1", "NeoForge"));
        String latest = CurseForgeUpdateFeed.pickLatest(feed, "1.20.1");
        assertEquals("20.2.4.12", latest);
        assertTrue(UpdateVersion.isNewer("20.2.4.11", latest));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.20", "1.20.10", "1.20.1-extra", "1.21.1"})
    void doesNotAcceptMinecraftVersionPrefixes(String wrongVersion) {
        assertNull(CurseForgeUpdateFeed.pickLatest(
                feed(file("20.2.4.12", "release", NEW, wrongVersion, "Forge")), "1.20.1"));
    }

    @Test
    void requiresForgeButAllowsCaseDifferencesAndBetaLikeUpstream() {
        assertEquals("20.2.4.12-beta", CurseForgeUpdateFeed.pickLatest(
                feed(file("20.2.4.12-beta", "beta", NEW, "1.20.1", "fOrGe")), "1.20.1"));
        assertNull(CurseForgeUpdateFeed.pickLatest(
                feed(file("20.2.4.12", "release", NEW, "1.20.1")), "1.20.1"));
        assertNull(CurseForgeUpdateFeed.pickLatest(
                feed(file("20.2.4.12", "AlPhA", NEW, "1.20.1", "Forge")), "1.20.1"));
    }

    @Test
    void usesUploadTimeNotFeedOrderOrHighestVersion() {
        assertEquals("20.2.4.11", CurseForgeUpdateFeed.pickLatest(feed(
                file("20.2.4.99", "release", OLD, "1.20.1", "Forge"),
                file("20.2.4.11", "release", NEW, "1.20.1", "Forge")), "1.20.1"));
    }

    @Test
    void prioritizesValidDatesAndUsesNumericTieBreaksForUnknownOrEqualDates() {
        assertEquals("20.2.4.11", CurseForgeUpdateFeed.pickLatest(feed(
                file("20.2.4.99", "release", "bad-date", "1.20.1", "Forge"),
                file("20.2.4.11", "release", OLD, "1.20.1", "Forge")), "1.20.1"));
        assertEquals("20.2.4.11", CurseForgeUpdateFeed.pickLatest(feed(
                file("20.2.4.11", "release", null, "1.20.1", "Forge"),
                file("20.2.4.9", "release", "bad-date", "1.20.1", "Forge")), "1.20.1"));
        assertEquals("20.2.4.11", CurseForgeUpdateFeed.pickLatest(feed(
                file("20.2.4.11", "release", NEW, "1.20.1", "Forge"),
                file("20.2.4.9", "release", NEW, "1.20.1", "Forge")), "1.20.1"));
    }

    @Test
    void skipsMalformedEntriesWithoutLosingTheGoodFile() {
        JsonObject feed = JsonParser.parseString("""
                {"files":[null, false, 5, [], {},
                  {"display":"99.0.0","versions":"1.20.1"},
                  {"display":"99.0.1","versions":[null,{},true,"1.20.1"]},
                  {"display":{},"versions":["1.20.1","Forge"]},
                  {"display":"unknown","versions":["1.20.1","Forge"]},
                  {"display":42,"versions":["1.20.1","Forge"]}]}
                """).getAsJsonObject();
        feed.getAsJsonArray("files").add(file("20.2.4.12", "release", NEW, "1.20.1", "Forge"));
        assertEquals("20.2.4.12", CurseForgeUpdateFeed.pickLatest(feed, "1.20.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "true", "{}", "{\"files\":null}", "{\"files\":{}}", "{\"files\":[]}"})
    void toleratesMissingOrMalformedRootData(String json) {
        assertNull(CurseForgeUpdateFeed.pickLatest(JsonParser.parseString(json), "1.20.1"));
    }

    @Test
    void usesTheRuntimeMinecraftVersionAndHandlesMissingInputs() {
        assertEquals("2.4.6.5", CurseForgeUpdateFeed.pickLatest(
                feed(file("1.21.1-2.4.6.5", "release", NEW, "1.21.1", "Forge")), "1.21.1"));
        assertNull(CurseForgeUpdateFeed.pickLatest(null, "1.20.1"));
        assertNull(CurseForgeUpdateFeed.pickLatest(feed(), null));
        assertNull(CurseForgeUpdateFeed.pickLatest(feed(), ""));
    }

    private static JsonObject feed(JsonElement... files) {
        JsonArray array = new JsonArray();
        for (JsonElement file : files) {
            array.add(file);
        }
        JsonObject root = new JsonObject();
        root.add("files", array);
        return root;
    }

    private static JsonObject file(String display, String type, String uploadedAt, String... versions) {
        JsonObject file = new JsonObject();
        file.addProperty("display", display);
        file.addProperty("type", type);
        if (uploadedAt != null) {
            file.addProperty("uploaded_at", uploadedAt);
        } else {
            file.add("uploaded_at", JsonNull.INSTANCE);
        }
        JsonArray array = new JsonArray();
        for (String version : versions) {
            array.add(version);
        }
        file.add("versions", array);
        return file;
    }
}
