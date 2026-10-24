package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekanismAccelerationState;
import mekanism.common.tile.machine.TileEntityFormulaicAssemblicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityFormulaicAssemblicator.class, remap = false, priority = 2000)
public abstract class FormulaicAssemblicatorMixin {

    @Shadow
    private int ticksRequired;

    @Shadow
    protected abstract void onUpdateServer();

    @Inject(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/tile/machine/TileEntityFormulaicAssemblicator;doSingleCraft()Z",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    public void injected(CallbackInfo cir) {
        MekanismAccelerationState.inject.accept(this.ticksRequired, this::onUpdateServer);
    }
}
