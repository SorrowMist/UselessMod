package com.sorrowmist.useless.mixin.mek;

/*
 * This file is based on Mekanism Upgrades: Reborn.
 * 
 * Copyright (c) Y_Xiao233
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import mekanism.api.Upgrade;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.UpgradeUtils;
import com.sorrowmist.useless.utils.MekUtils;
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