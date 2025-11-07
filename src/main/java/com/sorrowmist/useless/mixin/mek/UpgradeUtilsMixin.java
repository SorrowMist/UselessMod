package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.Upgrade;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin for UpgradeUtils to modify upgrade information display
 */
@Mixin(value = UpgradeUtils.class, remap = false)
public class UpgradeUtilsMixin {

    /**
     * @author sorrowmist
     * @reason Overwrite to display exponential scaled upgrade effects with custom calculations
     */
    @Overwrite
    public static List<Component> getExpScaledInfo(IUpgradeTile tile, Upgrade upgrade) {
        ArrayList<Component> ret = new ArrayList();
        if (tile.supportsUpgrades() && upgrade.getMax() > 1) {
            ret.add(MekanismLang.UPGRADES_EFFECT.translate(MekUtils.time(tile)));
        }
        return ret;
    }

    /**
     * @author sorrowmist
     * @reason Overwrite to display multiplier scaled upgrade effects with custom calculations
     */
    @Overwrite
    public static List<Component> getMultScaledInfo(IUpgradeTile tile, Upgrade upgrade) {
        ArrayList<Component> ret = new ArrayList();
        if (tile.supportsUpgrades() && upgrade.getMax() > 1) {
            double effect = upgrade == Upgrade.ENERGY ? MekUtils.capacity(tile) :
                    (upgrade == Upgrade.SPEED ? MekUtils.time(tile) :
                            Math.pow(MekanismConfig.general.maxUpgradeMultiplier.get(),
                                    (double)tile.getComponent().getUpgrades(upgrade) / (double)upgrade.getMax()));
            ret.add(MekanismLang.UPGRADES_EFFECT.translate(MekUtils.exponential(effect)));
        }
        return ret;
    }
}