package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekTemp;
import mekanism.common.tile.machine.TileEntityFluidicPlenisher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityFluidicPlenisher.class, remap = false)
public abstract class FluidicPlenisherMixin {

    @Shadow
    public int ticksRequired;

    @Shadow
    protected abstract boolean onUpdateServer();

    @Inject(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/tile/machine/TileEntityFluidicPlenisher;doPlenish()V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    public void injected(CallbackInfoReturnable<Boolean> cir) {
        MekTemp.inject.accept(this.ticksRequired, this::onUpdateServer);
    }
}