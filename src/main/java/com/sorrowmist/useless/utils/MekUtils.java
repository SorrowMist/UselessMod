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

    static {
        File file = FMLPaths.CONFIGDIR.get().resolve("useless_mod-common.toml").toFile();

        CommentedFileConfig config = CommentedFileConfig.builder(file).autosave().sync().build();
        config.load();

        // 读取 section
        Config mekanism = config.get("Mekanism升级设置");
        if (mekanism != null) {
            int max = mekanism.getInt("最大升级数量");
            if (max < 1 || max > 64) {
                max = 16;
            }
            MAX_UPGRADE = max;
        } else {
            // 没有 section，就写进去
            config.set("Mekanism升级设置.最大升级数量", MAX_UPGRADE);
            config.save();
        }

        config.close();
    }

    public static double time(IUpgradeTile tile) {
        return Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getTimeMultiplier()), (double)tile.getComponent().getUpgrades(Upgrade.SPEED) / (double)-8.0F);
    }

    public static double electricity(IUpgradeTile tile) {
        // 简化公式：每8个能量升级降低10倍能耗
        return Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getElectricityMultiplier()), (double)tile.getComponent().getUpgrades(Upgrade.ENERGY) / (double)-8.0F);
    }

    public static double capacity(IUpgradeTile tile) {
        return Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getCapacityMultiplier()), (double)tile.getComponent().getUpgrades(Upgrade.ENERGY) / (double)8.0F);
    }

    public static String exponential(double d) {
        int significant = 4;
        int exp = (int)Math.floor(Math.log10(d));
        d *= Math.pow((double)10.0F, (double)(-exp));
        d = (double)((int)Math.round(d * Math.pow((double)10.0F, (double)(significant - 1)))) / Math.pow((double)10.0F, (double)(significant - 1));
        double dt = (double)((int)Math.round(d * Math.pow((double)10.0F, (double)(significant - 1)))) / Math.pow((double)10.0F, (double)(significant - 1 - exp));
        return Math.abs(exp) <= significant - 1 ? "" + dt : d + "E" + exp;
    }
}