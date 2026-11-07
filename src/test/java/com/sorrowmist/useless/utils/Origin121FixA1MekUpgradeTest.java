package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.core.config.ConfigManager;
import mekanism.api.Upgrade;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.interfaces.IUpgradeTile;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Origin121FixA1MekUpgradeTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            Class.forName(ConfigManager.class.getName());
        }
    }

    private static IUpgradeTile tile(int speed, int energy, boolean supported) {
        var tile = mock(IUpgradeTile.class);
        var upgrades = mock(TileComponentUpgrade.class);
        when(tile.supportsUpgrade(any())).thenReturn(supported);
        when(tile.getComponent()).thenReturn(upgrades);
        when(upgrades.getUpgrades(Upgrade.SPEED)).thenReturn(speed);
        when(upgrades.getUpgrades(Upgrade.ENERGY)).thenReturn(energy);
        return tile;
    }

    private static MockedStatic<ConfigManager> config(int time, int electricity, int capacity) {
        var config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getTimeMultiplier).thenReturn(time);
        config.when(ConfigManager::getElectricityMultiplier).thenReturn(electricity);
        config.when(ConfigManager::getCapacityMultiplier).thenReturn(capacity);
        return config;
    }

    @Test void speedHasDoublePowerWeightAndEnergyOffsetsOneWeight() throws Exception {
        try (var caps = new EffectiveCaps(16, 16); var config = config(1, 1, 1)) {
            assertEquals(100.0, MekUtils.electricity(tile(16, 0, true)), 1e-12);
            assertEquals(10.0, MekUtils.electricity(tile(16, 16, true)), 1e-12);
            assertEquals(0.1, MekUtils.electricity(tile(0, 16, true)), 1e-12);
            assertEquals(0.1, MekUtils.time(tile(16, 0, true)), 1e-12);
            assertEquals(10.0, MekUtils.capacity(tile(0, 16, true)), 1e-12);
        }
    }

    @Test void ratiosFollowEachEffectiveUpgradeMaxRatherThanEightOrTheConfigCap() throws Exception {
        try (var caps = new EffectiveCaps(16, 64); var config = config(3, 2, 4)) {
            config.when(ConfigManager::getLoadedMaxUpgrade).thenReturn(32);
            var tile = tile(8, 16, true);
            assertEquals(Math.pow(30.0, -0.5), MekUtils.time(tile), 1e-12);
            assertEquals(10.0 * Math.pow(20.0, -0.25), MekUtils.electricity(tile), 1e-12);
            assertEquals(Math.pow(40.0, 0.25), MekUtils.capacity(tile), 1e-12);
            assertEquals(10.0, MekUtils.electricity(tile(8, 0, true)), 1e-12,
                    "The electricity config must not multiply the speed term");
        }
    }

    @Test void unsupportedTypesAndNoInstalledUpgradesAreNeutral() throws Exception {
        try (var caps = new EffectiveCaps(16, 64); var config = config(3, 2, 4)) {
            for (var tile : new IUpgradeTile[]{tile(64, 64, false), tile(0, 0, true)}) {
                assertEquals(1.0, MekUtils.time(tile));
                assertEquals(1.0, MekUtils.electricity(tile));
                assertEquals(1.0, MekUtils.capacity(tile));
            }
        }
    }

    @Test void nonFiniteMultipliersRemainGuardedWithoutClippingFiniteOnes() throws Exception {
        try (var caps = new EffectiveCaps(1, 1); var config = config(1, 1, 1)) {
            assertEquals(1e100, MekUtils.electricity(tile(50, 0, true)), 1e86);
            assertEquals(1.0, MekUtils.electricity(tile(1000, 0, true)));
            assertEquals(1.0, MekUtils.capacity(tile(0, 1000, true)));
            assertEquals(0.0, MekUtils.time(tile(1000, 0, true)));
        }
    }

    @Test void loadedDynamicCapRemainsLiveInsteadOfBeingFrozenAtStartup() {
        try (var config = mockStatic(ConfigManager.class)) {
            config.when(ConfigManager::getLoadedMaxUpgrade).thenReturn(16, 64, 32);
            assertEquals(16, MekUtils.getMaxUpgrade());
            assertEquals(64, MekUtils.getMaxUpgrade());
            assertEquals(32, MekUtils.getMaxUpgrade());
        }
    }

    /** Simulates effective caps after Forge/UpgradesReborn mixins without loading a mod launcher. */
    private static final class EffectiveCaps implements AutoCloseable {
        private final Field cap = Upgrade.class.getDeclaredField("maxStack");
        private final Field resolved;
        private final Field cached;
        private final Object multiplier = MekanismConfig.general.maxUpgradeMultiplier;
        private final int oldSpeed;
        private final int oldEnergy;
        private final int oldBase;
        private final boolean oldResolved;

        private EffectiveCaps(int speed, int energy) throws Exception {
            cap.setAccessible(true);
            oldSpeed = cap.getInt(Upgrade.SPEED);
            oldEnergy = cap.getInt(Upgrade.ENERGY);
            resolved = multiplier.getClass().getDeclaredField("resolved");
            cached = multiplier.getClass().getDeclaredField("cachedValue");
            resolved.setAccessible(true);
            cached.setAccessible(true);
            oldResolved = resolved.getBoolean(multiplier);
            oldBase = cached.getInt(multiplier);
            cap.setInt(Upgrade.SPEED, speed);
            cap.setInt(Upgrade.ENERGY, energy);
            resolved.setBoolean(multiplier, true);
            cached.setInt(multiplier, 10);
        }

        @Override public void close() throws Exception {
            cap.setInt(Upgrade.SPEED, oldSpeed);
            cap.setInt(Upgrade.ENERGY, oldEnergy);
            cached.setInt(multiplier, oldBase);
            resolved.setBoolean(multiplier, oldResolved);
        }
    }
}
