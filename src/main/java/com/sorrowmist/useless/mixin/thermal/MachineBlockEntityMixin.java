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

import cofh.thermal.lib.common.block.entity.MachineBlockEntity;
import cofh.thermal.lib.common.block.entity.Reconfigurable4WayBlockEntity;
import cofh.thermal.lib.util.recipes.MachineProperties;
import com.sorrowmist.useless.interfaces.IAugmentableBlockEntityAccessor;
import com.sorrowmist.useless.utils.ThermalConfigUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MachineBlockEntity.class, remap = false)
public abstract class MachineBlockEntityMixin extends Reconfigurable4WayBlockEntity {
    @Shadow protected int processMax;
    @Shadow protected int process;

    public MachineBlockEntityMixin(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
        super(tileEntityTypeIn, pos, state);
    }

    @Shadow public abstract MachineProperties getMachineProperties();
    @Shadow protected abstract void resolveOutputs();
    @Shadow protected abstract void resolveInputs();
    @Shadow protected abstract boolean validateInputs();
    @Shadow protected abstract boolean validateOutputs();

    @Inject(method = "tickServer", at = @At(value = "INVOKE", target = "Lcofh/thermal/lib/common/block/entity/MachineBlockEntity;transferOutput()V", ordinal = 0))
    public void addParallelLogic(CallbackInfo ci) {
        int parallel = ((IAugmentableBlockEntityAccessor) this).useless_mod$getParallel();
        if (parallel > 0) {
            for (int i = 0; i < parallel; ++i) {
                if (!this.validateInputs() || !this.validateOutputs()) {
                    return;
                }

                if (ThermalConfigUtils.PARALLEL_INCREASE_ENERGY_CONSUMPTION) {
                    // 检查能量是否足够，防止负数溢出
                    int energyRequired = this.processMax;
                    if (energyRequired < 0) {
                        // 如果 processMax 已经溢出为负数，使用安全值
                        energyRequired = Integer.MAX_VALUE / (parallel + 1);
                    }

                    if (this.energyStorage.getEnergyStored() < energyRequired) {
                        return;
                    }

                    // 安全地减少能量
                    int currentEnergy = this.energyStorage.getEnergyStored();
                    int newEnergy = currentEnergy - energyRequired;
                    if (newEnergy < 0) {
                        newEnergy = 0;
                    }
                    this.energyStorage.setEnergyStored(newEnergy);
                }

                this.resolveOutputs();
                this.resolveInputs();
                this.markDirtyFast();
            }
        }
    }
}