package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekTemp;
import mekanism.api.recipes.cache.CachedRecipe;
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