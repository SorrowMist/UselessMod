package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekTemp;
import mekanism.common.tile.machine.TileEntityElectricPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityElectricPump.class, remap = false)
public abstract class ElectricPumpMixin {

    @Shadow
    public int ticksRequired;

    @Shadow
    protected abstract void onUpdateServer();
    @Inject(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/tile/machine/TileEntityElectricPump;suck()Z",
                    shift = At.Shift.AFTER
            )
    ) private void injected(CallbackInfo cir) {
        MekTemp.inject.accept(this.ticksRequired, this::onUpdateServer);
    }
}
