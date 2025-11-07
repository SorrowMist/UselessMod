package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.math.MathUtils;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.MekanismUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Mixin for MekanismUtils to modify upgrade calculations
 */
@Mixin(value = MekanismUtils.class, remap = false)
public class MekanismUtilsMixin {

    /**
     * @author C-H716
     * @reason Overwrite to use custom time calculation with configurable multipliers
     */
    @Overwrite
    public static int getTicks(IUpgradeTile tile, int def) {
        if (tile.supportsUpgrades()) {
            double d = (double) def * MekUtils.time(tile);
            return d >= 1.0 ? MathUtils.clampToInt(d) : MathUtils.clampToInt(1.0 / d) * -1;
        } else {
            return def;
        }
    }

    /**
     * @author C-H716
     * @reason Overwrite to use custom energy consumption calculation with configurable multipliers
     */
    @Overwrite
    public static long getEnergyPerTick(IUpgradeTile tile, long def) {
        return tile.supportsUpgrades() ? MathUtils.ceilToLong(def * MekUtils.electricity(tile)) : def;
    }

    /**
     * @author C-H716
     * @reason Overwrite to use custom energy capacity calculation with configurable multipliers
     */
    @Overwrite
    public static long getMaxEnergy(IUpgradeTile tile, long def) {
        return tile.supportsUpgrades() ? MathUtils.ceilToLong(def * MekUtils.electricity(tile)) : def;
    }
}