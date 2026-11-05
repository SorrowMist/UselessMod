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

    public static int MAX_UPGRADE = 16;

    static {
        File file = FMLPaths.CONFIGDIR.get().resolve("useless_mod-server.toml").toFile();

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

    /**
     * 已安装升级占该类型上限的比例，口径与 MekanismUtils.fractionUpgrades 一致。
     *
     * @param tile 升级宿主
     * @param type 升级类型
     * @return 0 到 1 之间的比例；宿主不支持该类型时返回 0
     */
    private static double fraction(IUpgradeTile tile, Upgrade type) {
        if (tile.supportsUpgrade(type)) {
            return (double) tile.getComponent().getUpgrades(type) / (double) type.getMax();
        }
        return 0;
    }

    public static double time(IUpgradeTile tile) {
        double multiplier = Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getTimeMultiplier()), -fraction(tile, Upgrade.SPEED));
        // 仅防御 NaN/Infinity 异常值，不再硬截断有效倍数，避免限制高倍率增强
        return Double.isFinite(multiplier) ? multiplier : 1.0;
    }

    public static double electricity(IUpgradeTile tile) {
        // 照搬 Mekanism 原版 MekanismUtils.getEnergyPerTick：速度升级按两倍权重抬高能耗，能量升级按一倍权重压低能耗。
        // 节电配置倍率只作用于能量项，默认 1.0 时与原版逐位一致。
        double base = MekanismConfig.general.maxUpgradeMultiplier.get();
        double multiplier = Math.pow(base, 2.0 * fraction(tile, Upgrade.SPEED))
                * Math.pow(base * ConfigManager.getElectricityMultiplier(), -fraction(tile, Upgrade.ENERGY));
        return Double.isFinite(multiplier) ? multiplier : 1.0;
    }

    public static double capacity(IUpgradeTile tile) {
        return Math.pow((double)(MekanismConfig.general.maxUpgradeMultiplier.get() * ConfigManager.getCapacityMultiplier()), fraction(tile, Upgrade.ENERGY));
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
