package com.sorrowmist.useless.client.update;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.regex.Pattern;

/** Version handling shared by installed metadata, the CurseForge feed and the cache. */
final class UpdateVersion {
    private static final Pattern MINECRAFT_PREFIX = Pattern.compile("^1\\.\\d+(?:\\.\\d+)?-");
    private static final Pattern MOD_VERSION = Pattern.compile("\\d+(?:\\.\\d+)+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?");

    private UpdateVersion() {
    }

    static String normalize(String display) {
        if (display == null || display.isBlank()) {
            return null;
        }
        String text = display.trim();
        // cfwidget display: "Useless Mod 20.2.4.11" or "Useless Mod 1.20.1-20.2.4.11".
        int space = text.lastIndexOf(' ');
        if (space >= 0) {
            text = text.substring(space + 1);
        }
        text = MINECRAFT_PREFIX.matcher(text).replaceFirst("");
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        // Maven accepts arbitrary text as a qualifier; reject placeholders/garbage first.
        return MOD_VERSION.matcher(text).matches() ? text : null;
    }

    static boolean isNewer(String current, String latest) {
        // Upstream 2.4.6.5 fix: normalize BOTH sides, not just the remote display name.
        String normalizedCurrent = normalize(current);
        String normalizedLatest = normalize(latest);
        return normalizedCurrent != null && normalizedLatest != null
                && compare(normalizedLatest, normalizedCurrent) > 0;
    }

    static int compare(String left, String right) {
        return new DefaultArtifactVersion(left).compareTo(new DefaultArtifactVersion(right));
    }
}
