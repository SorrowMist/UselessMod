package com.sorrowmist.useless.compat.forge1201;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class DependencyMetadataTest {
    private static Map<String, Config> requirements() throws Exception {
        Config metadata = new TomlParser().parse(Files.readString(Path.of("src/main/resources/META-INF/mods.toml")));
        List<Config> dependencies = metadata.get("dependencies.useless_mod");
        Map<String, Config> result = new HashMap<>();
        for (Config dependency : dependencies) {
            String id = dependency.get("modId");
            assertNull(result.put(id, dependency), "Duplicate dependency: " + id);
            assertTrue(Set.of("BOTH", "CLIENT", "SERVER").contains(dependency.get("side")), id);
        }
        return result;
    }

    @Test void everyCompiledForgeModHasACompatibleRuntimeRequirement() throws Exception {
        var requirements = requirements();
        Set<String> checked = new HashSet<>();
        for (String entry : System.getProperty("uselessMod.compileClasspath").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar")) continue;
            try (var jar = new ZipFile(entry)) {
                var metadataEntry = jar.getEntry("META-INF/mods.toml");
                if (metadataEntry == null) continue;
                Config metadata = new TomlParser().parse(new String(
                        jar.getInputStream(metadataEntry).readAllBytes(), StandardCharsets.UTF_8));
                List<Config> mods = metadata.get("mods");
                if (mods == null) continue;
                for (Config mod : mods) {
                    String id = mod.get("modId");
                    if (id.equals("forge") || id.equals("minecraft")) continue;
                    Config requirement = requirements.get(id);
                    assertNotNull(requirement, "Missing runtime version requirement: " + id);
                    String version = mod.get("version");
                    if (version.equals("${file.jarVersion}")) {
                        version = new Manifest(jar.getInputStream(jar.getEntry("META-INF/MANIFEST.MF")))
                                .getMainAttributes().getValue("Implementation-Version");
                    }
                    String spec = requirement.get("versionRange");
                    var range = VersionRange.createFromVersionSpec(spec);
                    assertTrue(range.containsVersion(new DefaultArtifactVersion(version)), id + " " + version + " outside " + spec);
                    assertTrue(range.getRestrictions().stream().allMatch(r -> r.getLowerBound() != null),
                            "Missing minimum version: " + id);
                    if (id.equals("neoecoae")) {
                        assertEquals("[20.5.0,)", spec, "NeoECO accepts 20.5.0 and later versions");
                    } else {
                        assertTrue(range.getRestrictions().stream().allMatch(r -> r.getUpperBound() != null),
                                "Unbounded API line: " + id);
                    }
                    assertEquals(id.equals("ae2"), requirement.<Boolean>get("mandatory"), id);
                    checked.add(id);
                }
            }
        }
        assertEquals(48, checked.size(), "All compiled mod metadata must be inspected");
    }

    @Test void obsoleteApisAreRejectedButTheUpdatedPackVersionsRemainAllowed() throws Exception {
        var requirements = requirements();
        for (var entry : Map.of("ae2", "15.4.10", "expatternprovider", "1.20-1.4.12-forge",
                "jei", "15.48.0.177", "extendedcrafting", "6.1.5u", "mekanism", "10.4.15",
                "botanypots", "13.0.0", "mekanism_extras", "1.5.0").entrySet()) {
            var range = VersionRange.createFromVersionSpec(requirements.get(entry.getKey()).get("versionRange"));
            assertTrue(range.containsVersion(new DefaultArtifactVersion(entry.getValue())), entry.toString());
        }
        for (var entry : Map.of("ae2", "14.0.0", "create", "0.5.1", "mekanism", "10.3.0",
                "jei", "15.0.0", "exdeorum", "1.0",
                "botanypots", "12.0.0", "mekanism_extras", "1.4.1").entrySet()) {
            var range = VersionRange.createFromVersionSpec(requirements.get(entry.getKey()).get("versionRange"));
            assertFalse(range.containsVersion(new DefaultArtifactVersion(entry.getValue())), entry.toString());
        }
        assertTrue(requirements.get("forge").<Boolean>get("mandatory"));
        assertTrue(requirements.get("minecraft").<Boolean>get("mandatory"));
        assertFalse(requirements.get("create").<Boolean>get("mandatory"));
    }

    @Test void neoEcoRequires2050AndAllowsLaterVersions() throws Exception {
        var range = VersionRange.createFromVersionSpec(requirements().get("neoecoae").get("versionRange"));
        for (String version : List.of("20.5.0", "20.5.1", "20.6.0", "21.0.0")) {
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)), version);
        }
        for (String version : List.of("20.4.2", "20.4.99", "20.5.0-beta.1")) {
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)), version);
        }
    }

    @Test void thermalRequirementsAllowTheOldestApiAndThePacksEmbeddedCore() throws Exception {
        var requirements = requirements();
        for (String id : List.of("thermal", "cofh_core")) {
            var range = VersionRange.createFromVersionSpec(requirements.get(id).get("versionRange"));
            assertTrue(range.containsVersion(new DefaultArtifactVersion("11.0.0")), id);
            assertFalse(range.containsVersion(new DefaultArtifactVersion("10.3.0")), id);
            assertFalse(range.containsVersion(new DefaultArtifactVersion("12.0.0")), id);
        }
        assertTrue(VersionRange.createFromVersionSpec(requirements.get("thermal").get("versionRange"))
                .containsVersion(new DefaultArtifactVersion("11.0.6")));
        assertTrue(VersionRange.createFromVersionSpec(requirements.get("cofh_core").get("versionRange"))
                .containsVersion(new DefaultArtifactVersion("11.0.2")));
    }
}
