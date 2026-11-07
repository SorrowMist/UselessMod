package com.sorrowmist.useless.client.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class A6UpdateCheckCacheTest {
    private static final long NOW = 1_800_000_000_000L;
    @TempDir Path directory;

    private Path file() {
        return directory.resolve("nested/useless_mod-update-check.properties");
    }

    @Test
    void cachesForExactlySixHoursAndSurvivesANewInstance() throws Exception {
        UpdateCheckCache cache = new UpdateCheckCache(file());
        assertNull(cache.read("1.20.1", NOW));
        cache.write("1.20.1", "20.2.4.12", NOW);
        UpdateCheckCache reloaded = new UpdateCheckCache(file());
        assertEquals("20.2.4.12", reloaded.read("1.20.1", NOW).latestVersion());
        assertEquals("20.2.4.12", reloaded.read("1.20.1", NOW + UpdateCheckCache.TTL_MILLIS - 1).latestVersion());
        assertNull(reloaded.read("1.20.1", NOW + UpdateCheckCache.TTL_MILLIS));
        assertNull(reloaded.read("1.20.1", NOW - 1));
    }

    @Test
    void successfulNoMatchIsCachedToo() throws Exception {
        UpdateCheckCache cache = new UpdateCheckCache(file());
        cache.write("1.20.1", null, NOW);
        assertNotNull(cache.read("1.20.1", NOW + 1));
        assertNull(cache.read("1.20.1", NOW + 1).latestVersion());
        assertNull(cache.read("1.20.1", NOW + UpdateCheckCache.TTL_MILLIS));
    }

    @Test
    void keepsMinecraftVersionsSeparateAndPreservesOtherEntries() throws Exception {
        UpdateCheckCache cache = new UpdateCheckCache(file());
        cache.write("1.20.1", "20.2.4.12", NOW);
        assertNull(cache.read("1.21.1", NOW));
        cache.write("1.21.1", "2.4.6.5", NOW);
        assertEquals("20.2.4.12", cache.read("1.20.1", NOW).latestVersion());
        assertEquals("2.4.6.5", cache.read("1.21.1", NOW).latestVersion());
    }

    @Test
    void ignoresUpstreamLoaderAgnosticAndOtherLoaderEntries() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "checked_at.1.20.1=" + NOW + "\nlatest.1.20.1=99.0.0\n"
                + "checked_at.neoforge.1.20.1=" + NOW + "\nlatest.neoforge.1.20.1=99.0.1\n");
        assertNull(new UpdateCheckCache(file()).read("1.20.1", NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "0", "-1", "9223372036854775807"})
    void ignoresBadMissingAndFutureTimestamps(String timestamp) throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "checked_at.forge.1.20.1=" + timestamp + "\nlatest.forge.1.20.1=20.2.4.12\n");
        assertNull(new UpdateCheckCache(file()).read("1.20.1", NOW));
    }

    @Test
    void ignoresMissingVersionsAndGarbageAndRecoversFromCorruptProperties() throws Exception {
        Files.createDirectories(file().getParent());
        UpdateCheckCache cache = new UpdateCheckCache(file());
        Files.writeString(file(), "checked_at.forge.1.20.1=" + NOW + "\n");
        assertNull(cache.read("1.20.1", NOW));
        Files.writeString(file(), "checked_at.forge.1.20.1=" + NOW + "\nlatest.forge.1.20.1=unknown\n");
        assertNull(cache.read("1.20.1", NOW));
        Files.writeString(file(), "broken=" + '\\' + "uZZZZ\n");
        assertNull(cache.read("1.20.1", NOW));
        cache.write("1.20.1", "20.2.4.12", NOW);
        assertEquals("20.2.4.12", cache.read("1.20.1", NOW).latestVersion());
    }
}
