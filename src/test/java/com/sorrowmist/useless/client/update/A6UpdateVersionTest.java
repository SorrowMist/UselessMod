package com.sorrowmist.useless.client.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class A6UpdateVersionTest {
    @ParameterizedTest
    @CsvSource({
            "20.2.4.11, 20.2.4.11",
            "Useless Mod 20.2.4.11, 20.2.4.11",
            "1.20.1-20.2.4.11, 20.2.4.11",
            "Useless Mod 1.20.1-20.2.4.12, 20.2.4.12",
            "1.21.1-2.4.6.5, 2.4.6.5",
            "Useless Mod 1.21.1-2.4.6.5, 2.4.6.5",
            "v20.2.4.11, 20.2.4.11",
            "20.2.4.12-beta.1, 20.2.4.12-beta.1"
    })
    void normalizesMetadataAndFeedWithoutStrippingTheForgeVersionLine(String display, String expected) {
        assertEquals(expected, UpdateVersion.normalize(display));
    }

    @ParameterizedTest
    @CsvSource({
            "20.2.4.11, 20.2.4.12, true",
            "20.2.4.9, 20.2.4.11, true",
            "20.2.4.11, 20.2.4.9, false",
            "20.2.4.11, 20.2.4.11, false",
            "1.20.1-20.2.4.11, Useless Mod 20.2.4.11, false",
            "20.2.4.11, Useless Mod 1.20.1-20.2.4.12, true",
            "1.21.1-2.4.6.5, 2.4.6.5, false",
            "2.4.6.5, 1.21.1-2.4.6.5, false",
            "1.21.1-2.4.6.4, 1.21.1-2.4.6.5, true",
            "20.2.4.12-beta, 20.2.4.12, true",
            "20.2.4.12, 20.2.4.12-beta, false",
            "20.2.4.11, 2.4.6.5, false"
    })
    void comparesNumericallyAndRegressesTheUpstream2465PrefixBug(String current, String latest, boolean newer) {
        assertEquals(newer, UpdateVersion.isNewer(current, latest));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "${mod_version}", "0.0NONE", "unknown", "Useless Mod", "20.2.4.11.jar"})
    void ignoresInvalidVersionsInsteadOfTreatingThemAsMavenQualifiers(String invalid) {
        assertNull(UpdateVersion.normalize(invalid));
        assertFalse(UpdateVersion.isNewer(invalid, "20.2.4.12"));
        assertFalse(UpdateVersion.isNewer("20.2.4.11", invalid));
    }

    @Test
    void ignoresMissingVersions() {
        assertNull(UpdateVersion.normalize(null));
        assertFalse(UpdateVersion.isNewer(null, "20.2.4.12"));
        assertFalse(UpdateVersion.isNewer("20.2.4.11", null));
    }
}
