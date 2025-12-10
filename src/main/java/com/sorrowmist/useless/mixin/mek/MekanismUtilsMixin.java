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

import mekanism.api.math.FloatingLong;
import mekanism.api.math.MathUtils;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.MekanismUtils;
import com.sorrowmist.useless.utils.MekUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Mixin for MekanismUtils to modify upgrade calculations
 */
@Mixin(value = MekanismUtils.class, remap = false)
public class MekanismUtilsMixin {

    /**
     * @author sorrowmist
     * @reason Overwrite to use custom time calculation with configurable multipliers
     */
    @Overwrite
    public static int getTicks(IUpgradeTile tile, int def) {
        if (tile.supportsUpgrades()) {
            double d = (double)def * MekUtils.time(tile);
            return d >= 1.0 ? MathUtils.clampToInt(d) : MathUtils.clampToInt(1.0 / d) * -1;
        } else {
            return def;
        }
    }

    /**
     * @author sorrowmist
     * @reason Overwrite to use custom energy consumption calculation with configurable multipliers
     */
    @Overwrite
    public static FloatingLong getEnergyPerTick(IUpgradeTile tile, FloatingLong def) {
        return tile.supportsUpgrades() ? def.multiply(MekUtils.electricity(tile)) : def;
    }

    /**
     * @author sorrowmist
     * @reason Overwrite to use custom energy capacity calculation with configurable multipliers
     */
    @Overwrite
    public static FloatingLong getMaxEnergy(IUpgradeTile tile, FloatingLong def) {
        return tile.supportsUpgrades() ? def.multiply(MekUtils.capacity(tile)) : def;
    }
}