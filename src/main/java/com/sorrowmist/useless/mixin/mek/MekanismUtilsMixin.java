package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.math.MathUtils;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.MekanismUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MekanismUtils.class, remap = false)
public class MekanismUtilsMixin {

    @Inject(method = "getTicksD", at = @At("RETURN"), cancellable = true)
    private static void modifyTicks(IUpgradeTile tile, int def, CallbackInfoReturnable<Double> cir) {
        if (tile.supportsUpgrades()) {
            cir.setReturnValue(cir.getReturnValue() * MekUtils.time(tile));
        }
    }

    @Inject(method = "getEnergyPerTick", at = @At("RETURN"), cancellable = true)
    private static void modifyEnergyPerTick(IUpgradeTile tile, long def, CallbackInfoReturnable<Long> cir) {
        if (tile.supportsUpgrades()) {
            cir.setReturnValue(MathUtils.ceilToLong(cir.getReturnValue() * MekUtils.electricity(tile)));
        }
    }

    @Inject(method = "getMaxEnergy*", at = @At("RETURN"), cancellable = true)
    private static void modifyMaxEnergy(IUpgradeTile tile, long def, CallbackInfoReturnable<Long> cir) {
        if (tile.supportsUpgrades()) {
            cir.setReturnValue(MathUtils.ceilToLong(cir.getReturnValue() * MekUtils.capacity(tile)));
        }
    }
}