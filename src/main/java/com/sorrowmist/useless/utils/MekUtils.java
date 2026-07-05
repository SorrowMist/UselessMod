package com.sorrowmist.useless.utils;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.sorrowmist.useless.core.config.ConfigManager;
import mekanism.api.Upgrade;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.interfaces.IUpgradeTile;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;

public class MekUtils {

    public static int MAX_UPGRADE = 32;
    private static final double MIN_TIME_MULTIPLIER = 0.001;

    static {
        File file = FMLPaths.CONFIGDIR.get().resolve("useless_mod-common.toml").toFile();

        CommentedFileConfig config = CommentedFileConfig.builder(file).autosave().sync().build();
        config.load();

        Config mekanism = config.get("mekanism_upgrade");
        if (mekanism != null) {
            int max = mekanism.getInt("max_upgrade");
            if (max < 1 || max > 64) {
                max = 16;
            }
            MAX_UPGRADE = max;
        } else {
            config.set("mekanism_upgrade.max_upgrade", MAX_UPGRADE);
            config.save();
        }

        config.close();
    }

    public static double time(IUpgradeTile tile) {
        double multiplier = Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getTimeMultiplier()), (double)tile.getComponent().getUpgrades(Upgrade.SPEED) / (double)-8.0F);
        return Double.isFinite(multiplier) ? Math.max(multiplier, MIN_TIME_MULTIPLIER) : MIN_TIME_MULTIPLIER;
    }

    public static double electricity(IUpgradeTile tile) {
        // 简化公式：每8个能量升级降低10倍能耗
        return Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getElectricityMultiplier()), (double)tile.getComponent().getUpgrades(Upgrade.ENERGY) / (double)-8.0F);
    }

    public static double capacity(IUpgradeTile tile) {
        return Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getCapacityMultiplier()), (double)tile.getComponent().getUpgrades(Upgrade.ENERGY) / (double)8.0F);
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
