package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekanismAccelerationState;
import mekanism.api.recipes.cache.CachedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

@Mixin(value = CachedRecipe.class, remap = false, priority = 2000)
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
            remap = false
    )
    private void injected(CallbackInfo ci) {
        MekanismAccelerationState.inject.accept(this.requiredTicks.getAsInt(), this::process);
    }
}
