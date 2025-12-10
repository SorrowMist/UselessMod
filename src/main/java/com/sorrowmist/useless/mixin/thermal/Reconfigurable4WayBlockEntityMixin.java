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

import cofh.thermal.lib.common.block.entity.AugmentableBlockEntity;
import cofh.thermal.lib.common.block.entity.Reconfigurable4WayBlockEntity;
import com.sorrowmist.useless.interfaces.IAugmentableBlockEntityAccessor;
import com.sorrowmist.useless.utils.ThermalConfigUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Reconfigurable4WayBlockEntity.class, remap = false)
public abstract class Reconfigurable4WayBlockEntityMixin extends AugmentableBlockEntity {
    public Reconfigurable4WayBlockEntityMixin(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
        super(tileEntityTypeIn, pos, state);
    }

    @Inject(at = @At("RETURN"), method = "getInputItemAmount", cancellable = true)
    public void addParallelInput(CallbackInfoReturnable<Integer> cir) {
        if (ThermalConfigUtils.PARALLEL_INCREASE_ITEM_TRANSFER) {
            int parallel = ((IAugmentableBlockEntityAccessor) this).useless_mod$getParallel();
            if (parallel > 0) {
                cir.setReturnValue(cir.getReturnValueI() * (1 + parallel));
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "getOutputItemAmount", cancellable = true)
    public void addParallelOutput(CallbackInfoReturnable<Integer> cir) {
        if (ThermalConfigUtils.PARALLEL_INCREASE_ITEM_TRANSFER) {
            int parallel = ((IAugmentableBlockEntityAccessor) this).useless_mod$getParallel();
            if (parallel > 0) {
                cir.setReturnValue(cir.getReturnValueI() * (1 + parallel));
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "getInputFluidAmount", cancellable = true)
    public void addParallelInputFluid(CallbackInfoReturnable<Integer> cir) {
        if (ThermalConfigUtils.PARALLEL_INCREASE_FLUID_TRANSFER) {
            int parallel = ((IAugmentableBlockEntityAccessor) this).useless_mod$getParallel();
            if (parallel > 0) {
                cir.setReturnValue(cir.getReturnValueI() * (1 + parallel));
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "getOutputFluidAmount", cancellable = true)
    public void addParallelOutputFluid(CallbackInfoReturnable<Integer> cir) {
        if (ThermalConfigUtils.PARALLEL_INCREASE_FLUID_TRANSFER) {
            int parallel = ((IAugmentableBlockEntityAccessor) this).useless_mod$getParallel();
            if (parallel > 0) {
                cir.setReturnValue(cir.getReturnValueI() * (1 + parallel));
            }
        }
    }
}