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

import mekanism.api.recipes.cache.CachedRecipe;
import com.sorrowmist.useless.utils.MekTemp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.IntSupplier;

@Mixin(value = CachedRecipe.class, remap = false)
public abstract class CachedRecipeMixin {

    @Shadow
    private IntSupplier requiredTicks;

    @Shadow
    public abstract void process();

    @Inject(
            method = "process",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/api/recipes/cache/CachedRecipe;finishProcessing(I)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            remap = false
    )
    private void injected(CallbackInfo ci, int operations) {
        MekTemp.inject.accept(this.requiredTicks.getAsInt(), this::process);
    }
}