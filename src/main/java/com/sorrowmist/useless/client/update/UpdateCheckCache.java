package com.sorrowmist.useless.client.update;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Disk cache with an injectable clock/path, independent of Minecraft and Forge bootstrap. */
final class UpdateCheckCache {
    static final long TTL_MILLIS = 6L * 60L * 60L * 1000L;
    private final Path file;

    UpdateCheckCache(Path file) {
        this.file = file;
    }

    /** Null means cache miss; an entry with a null version means a successful check found no file. */
    Entry read(String minecraftVersion, long now) {
        try {
            Properties properties = load();
            String key = key(minecraftVersion);
            long checkedAt = Long.parseLong(properties.getProperty("checked_at." + key, "0"));
            if (checkedAt <= 0 || checkedAt > now || now - checkedAt >= TTL_MILLIS) {
                return null;
            }
            String latest = properties.getProperty("latest." + key);
            if (latest == null) {
                return null;
            }
            if (latest.isEmpty()) {
                return new Entry(null);
            }
            String normalized = UpdateVersion.normalize(latest);
            return normalized == null ? null : new Entry(normalized);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    void write(String minecraftVersion, String latestVersion, long now) throws IOException {
        Properties properties;
        try {
            properties = load();
        } catch (IOException | IllegalArgumentException ignored) {
            // Replace a corrupt cache instead of permanently disabling caching.
            properties = new Properties();
        }
        String key = key(minecraftVersion);
        properties.setProperty("checked_at." + key, Long.toString(now));
        properties.setProperty("latest." + key, latestVersion == null ? "" : latestVersion);
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, "Useless Mod update check cache (Forge)");
        }
    }

    private Properties load() throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    private static String key(String minecraftVersion) {
        // Do not reuse the upstream loader-agnostic cache, which might contain a NeoForge/Fabric file.
        return "forge." + minecraftVersion;
    }

    record Entry(String latestVersion) {
    }
}
