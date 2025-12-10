package com.sorrowmist.useless.mixin.thermal;

/*
 * This file is part of Thermal Parallel.
 * 
 * Copyright (c) 2025 EtSH-C2H6S
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

import cofh.lib.common.inventory.ItemStorageCoFH;
import com.sorrowmist.useless.interfaces.IItemStorageCoFHAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStorageCoFH.class, remap = false)
public class ItemStorageCoFHMixin implements IItemStorageCoFHAccessor {
    @Unique private int useless_mod$parallel;

    public void useless_mod$setParallel(int count) {
        this.useless_mod$parallel = count;
    }

    public int useless_mod$getParallel() {
        return this.useless_mod$parallel;
    }

    @Inject(at = @At("RETURN"), method = "getSlotLimit", cancellable = true)
    public void modifyItemStorage(int slot, CallbackInfoReturnable<Integer> cir) {
        if (this.useless_mod$parallel > 0) {
            long newLimit = (long) cir.getReturnValueI() * (this.useless_mod$parallel + 1);
            // 防止整数溢出，同时限制最大堆叠数为 2^31-1
            if (newLimit > Integer.MAX_VALUE) {
                newLimit = Integer.MAX_VALUE;
            }
            cir.setReturnValue((int) newLimit);
        }
    }
}