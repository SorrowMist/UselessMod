package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledProcessingPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlanner;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, priority = 1200, remap = false)
public abstract class CraftingServiceSmartDoublingMixin {
    @Inject(method = "getProviders", at = @At("HEAD"), cancellable = true)
    private void uselessMod$getSmartDoublingProviders(
            IPatternDetails pattern, CallbackInfoReturnable<Iterable<ICraftingProvider>> callback) {
        if (!(pattern instanceof ScaledProcessingPattern)) {
            return;
        }

        IPatternDetails original = SmartDoublingPatterns.unwrap(pattern);
        Iterable<ICraftingProvider> providers = ((CraftingService) (Object) this).getProviders(original);
        callback.setReturnValue(SmartDoublingPlanner.eligibleProviders(providers));
    }
}
