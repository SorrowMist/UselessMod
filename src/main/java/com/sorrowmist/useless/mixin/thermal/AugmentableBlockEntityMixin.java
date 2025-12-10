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

import cofh.core.util.helpers.AugmentableHelper;
import cofh.lib.common.energy.EnergyStorageCoFH;
import cofh.lib.common.fluid.FluidStorageCoFH;
import cofh.lib.common.fluid.ManagedTankInv;
import cofh.lib.common.inventory.ManagedItemInv;
import cofh.thermal.lib.common.block.entity.AugmentableBlockEntity;
import com.sorrowmist.useless.interfaces.IAugmentableBlockEntityAccessor;
import com.sorrowmist.useless.interfaces.IItemStorageCoFHAccessor;
import com.sorrowmist.useless.utils.ThermalConfigUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;

@Mixin(value = AugmentableBlockEntity.class, remap = false)
public abstract class AugmentableBlockEntityMixin implements IAugmentableBlockEntityAccessor {
    @Shadow protected CompoundTag augmentNBT;
    @Shadow protected ManagedItemInv inventory;
    @Shadow protected ManagedTankInv tankInv;
    @Shadow protected EnergyStorageCoFH energyStorage;

    @Unique private int useless_mod$parallel;

    public int useless_mod$getParallel() {
        return this.useless_mod$parallel;
    }

    public void useless_mod$setParallel(int parallel) {
        this.useless_mod$parallel = parallel;
    }

    @Inject(method = "setAttributesFromAugment", at = @At(value = "INVOKE", target = "Lcofh/core/util/helpers/AugmentableHelper;setAttributeFromAugmentMax(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;)V", ordinal = 0, shift = At.Shift.AFTER))
    public void setParallelAttribute(CompoundTag augmentData, CallbackInfo ci) {
        AugmentableHelper.setAttributeFromAugmentAdd(this.augmentNBT, augmentData, "MachineParallel");
    }

    @Inject(method = "finalizeAttributes", at = @At("TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
    public void finalizeAttributes(Map<Enchantment, Integer> enchantmentMap, CallbackInfo ci, float holdingMod, float baseMod, float energyStorageMod, float fluidStorageMod, float itemStorageMod, float xpStorageMod, float energyXferMod, int storedXp, CompoundTag filterNBT) {
        int parallel = (int) AugmentableHelper.getAttributeMod(this.augmentNBT, "MachineParallel");

        if (ThermalConfigUtils.BASE_MOD_AFFECT_PARALLEL) {
            parallel = (int) ((float) (parallel + 1) * baseMod - 1.0F);
        }

        this.useless_mod$parallel = Math.max(1, parallel);

        // 创建 final 变量用于 lambda 表达式
        final int finalParallel = parallel;

        if (parallel > 0) {
            if (ThermalConfigUtils.PARALLEL_INCREASE_ITEM_CAPACITY) {
                // 使用 finalParallel 而不是 parallel
                this.inventory.getInputSlots().forEach(itemStorageCoFH ->
                        ((IItemStorageCoFHAccessor) itemStorageCoFH).useless_mod$setParallel(finalParallel + 1));
                this.inventory.getOutputSlots().forEach(itemStorageCoFH ->
                        ((IItemStorageCoFHAccessor) itemStorageCoFH).useless_mod$setParallel(finalParallel + 1));
            }

            if (ThermalConfigUtils.PARALLEL_INCREASE_FLUID_CAPACITY) {
                for (int i = 0; i < this.tankInv.getTanks(); ++i) {
                    FluidStorageCoFH tank = this.tankInv.getTank(i);
                    long newCapacity = (long) tank.getCapacity() * (finalParallel + 1);
                    // 防止整数溢出
                    if (newCapacity > Integer.MAX_VALUE) {
                        newCapacity = Integer.MAX_VALUE;
                    }
                    this.tankInv.getTank(i).setCapacity((int) newCapacity);
                }
            }

            if (ThermalConfigUtils.PARALLEL_INCREASE_ENERGY_CAPACITY) {
                long newCapacity = (long) this.energyStorage.getCapacity() * (finalParallel + 1);
                // 防止整数溢出
                if (newCapacity > Integer.MAX_VALUE) {
                    newCapacity = Integer.MAX_VALUE;
                }
                this.energyStorage.setCapacity((int) newCapacity);
            }

            if (ThermalConfigUtils.PARALLEL_INCREASE_ENERGY_TRANSFER) {
                long newMaxReceive = (long) this.energyStorage.getMaxReceive() * (finalParallel + 1);
                long newMaxExtract = (long) this.energyStorage.getMaxExtract() * (finalParallel + 1);

                // 防止整数溢出
                if (newMaxReceive > Integer.MAX_VALUE) {
                    newMaxReceive = Integer.MAX_VALUE;
                }
                if (newMaxExtract > Integer.MAX_VALUE) {
                    newMaxExtract = Integer.MAX_VALUE;
                }

                this.energyStorage.setMaxReceive((int) newMaxReceive);
                this.energyStorage.setMaxExtract((int) newMaxExtract);
            }
        }
    }
}