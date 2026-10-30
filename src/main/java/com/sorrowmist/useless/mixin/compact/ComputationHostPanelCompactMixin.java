package com.sorrowmist.useless.mixin.compact;

import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactC9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.ui.CompactHostPanelState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity", remap = false)
public abstract class ComputationHostPanelCompactMixin {
    @Inject(method = "getComputationControllerPanelState", at = @At("RETURN"), require = 0, remap = false,
            cancellable = true)
    private void uselessMod$compactHardware(CallbackInfoReturnable<Object> cir) {
        if ((Object) this instanceof CompactC9BlockEntity) {
            cir.setReturnValue(CompactHostPanelState.levelNine(cir.getReturnValue(), true));
        }
    }
}
