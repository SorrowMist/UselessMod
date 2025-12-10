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

import com.sorrowmist.useless.utils.MekTemp;
import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.Upgrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to modify Mekanism upgrade limits
 */
@Mixin({Upgrade.class})
public abstract class UpgradeMixin {
    @ModifyVariable(
            method = {"<init>"},
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static String getName(String s) {
        MekTemp.name = s;
        return s;
    }

    @ModifyVariable(
            method = {"<init>"},
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static int toFullStack(int i) {
        return !MekTemp.name.equals("speed") && !MekTemp.name.equals("energy") ? i : MekUtils.MAX_UPGRADE;
    }
}