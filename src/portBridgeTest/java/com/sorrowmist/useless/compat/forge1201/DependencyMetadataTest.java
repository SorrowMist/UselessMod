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

    @Test void everyCompiledForgeModHasABoundedCompatibleRuntimeRequirement() throws Exception {
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
                    assertTrue(range.getRestrictions().stream().allMatch(r -> r.getLowerBound() != null
                            && r.getUpperBound() != null), "Unbounded API line: " + id);
                    assertEquals(id.equals("ae2"), requirement.<Boolean>get("mandatory"), id);
                    checked.add(id);
                }
            }
        }
        assertEquals(41, checked.size(), "All compiled mod metadata must be inspected");
    }

    @Test void obsoleteApisAreRejectedButTheUpdatedPackVersionsRemainAllowed() throws Exception {
        var requirements = requirements();
        for (var entry : Map.of("ae2", "15.4.10", "expatternprovider", "1.20-1.4.12-forge",
                "jei", "15.48.0.177", "extendedcrafting", "6.1.5u", "mekanism", "10.4.15").entrySet()) {
            var range = VersionRange.createFromVersionSpec(requirements.get(entry.getKey()).get("versionRange"));
            assertTrue(range.containsVersion(new DefaultArtifactVersion(entry.getValue())), entry.toString());
        }
        for (var entry : Map.of("ae2", "14.0.0", "create", "0.5.1", "mekanism", "10.3.0",
                "jei", "15.0.0", "exdeorum", "1.0").entrySet()) {
            var range = VersionRange.createFromVersionSpec(requirements.get(entry.getKey()).get("versionRange"));
            assertFalse(range.containsVersion(new DefaultArtifactVersion(entry.getValue())), entry.toString());
        }
        assertTrue(requirements.get("forge").<Boolean>get("mandatory"));
        assertTrue(requirements.get("minecraft").<Boolean>get("mandatory"));
        assertFalse(requirements.get("create").<Boolean>get("mandatory"));
    }
}
