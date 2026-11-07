package com.sorrowmist.useless.utils;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.mojang.logging.LogUtils;
import mekanism.api.Upgrade;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.interfaces.IUpgradeTile;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

public class MekUtils {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_UPGRADE = 16;
    private static volatile int startupMaxUpgrade = -1;

    /**
     * Reads max_upgrade from the single owning config source. The TOML fallback
     * is only used while Mekanism's Upgrade enum is being constructed, which
     * happens before Forge finishes loading the server config.
     */
    public static int getMaxUpgrade() {
        Integer loaded = ConfigManager.getLoadedMaxUpgrade();
        if (loaded != null) {
            return loaded;
        }

        int cached = startupMaxUpgrade;
        if (cached < 0) {
            synchronized (MekUtils.class) {
                cached = startupMaxUpgrade;
                if (cached < 0) {
                    cached = readStartupMaxUpgrade();
                    startupMaxUpgrade = cached;
                }
            }
        }
        return cached;
    }

    private static int readStartupMaxUpgrade() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("useless_mod-server.toml");
        if (!Files.isRegularFile(path)) {
            return DEFAULT_MAX_UPGRADE;
        }

        try (CommentedFileConfig config = CommentedFileConfig.builder(path).build()) {
            config.load();
            Config section = config.get("mekanism_upgrade");
            if (section != null) {
                Object raw = section.get("max_upgrade");
                if (raw instanceof Number number) {
                    int value = number.intValue();
                    if (value >= 1 && value <= 64) {
                        return value;
                    }
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Unable to read mekanism_upgrade.max_upgrade from {}; using {}",
                    path, DEFAULT_MAX_UPGRADE, exception);
        }
        return DEFAULT_MAX_UPGRADE;
    }

    /** Uses the effective per-type cap, including dynamic caps supplied by other mods. */
    private static double fraction(IUpgradeTile tile, Upgrade type) {
        if (tile.supportsUpgrade(type)) {
            return (double) tile.getComponent().getUpgrades(type) / type.getMax();
        }
        return 0.0;
    }

    public static double time(IUpgradeTile tile) {
        double multiplier = Math.pow(MekanismConfig.general.maxUpgradeMultiplier.get()
                * ConfigManager.getTimeMultiplier(), -fraction(tile, Upgrade.SPEED));
        // 仅防御 NaN/Infinity 异常值，不再硬截断有效倍数，避免限制高倍率增强
        return Double.isFinite(multiplier) ? multiplier : 1.0;
    }

    public static double electricity(IUpgradeTile tile) {
        // Mekanism weights speed twice and energy once. The saving config only affects energy.
        double base = MekanismConfig.general.maxUpgradeMultiplier.get();
        double multiplier = Math.pow(base, 2.0 * fraction(tile, Upgrade.SPEED))
                * Math.pow(base * ConfigManager.getElectricityMultiplier(), -fraction(tile, Upgrade.ENERGY));
        return Double.isFinite(multiplier) ? multiplier : 1.0;
    }

    public static double capacity(IUpgradeTile tile) {
        double multiplier = Math.pow(MekanismConfig.general.maxUpgradeMultiplier.get()
                * ConfigManager.getCapacityMultiplier(), fraction(tile, Upgrade.ENERGY));
        return Double.isFinite(multiplier) ? multiplier : 1.0;
    }

    public static String exponential(double d) {
        if (!Double.isFinite(d) || d <= 0) {
            return "0";
        }

        int significant = 4;
        int exp = (int)Math.floor(Math.log10(d));
        d *= Math.pow((double)10.0F, (double)(-exp));
        d = (double)((int)Math.round(d * Math.pow((double)10.0F, (double)(significant - 1)))) / Math.pow((double)10.0F, (double)(significant - 1));
        double dt = (double)((int)Math.round(d * Math.pow((double)10.0F, (double)(significant - 1)))) / Math.pow((double)10.0F, (double)(significant - 1 - exp));
        return Math.abs(exp) <= significant - 1 ? "" + dt : d + "E" + exp;
    }
}
