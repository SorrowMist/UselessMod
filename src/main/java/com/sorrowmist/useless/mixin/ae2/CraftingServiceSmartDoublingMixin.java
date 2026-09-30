package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlanner;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, priority = 1200, remap = false)
public abstract class CraftingServiceSmartDoublingMixin {
    @ModifyVariable(method = "submitJob", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ICraftingPlan uselessMod$rewriteSubmittedPlan(ICraftingPlan plan) {
        CraftingService service = (CraftingService) (Object) this;
        // 本方法跑在服务端线程上，取关卡安全。改写后的样板会按关卡把「可复用输入」
        // （注魔水晶这类用完还回来、且还回来还能用的槽）的倍率钉在 1 份上 —— 拿不到关卡时
        // 退回保守判据，只少修一类，不会错修。
        IGrid grid = ((CraftingServiceGridAccessor) (Object) this).uselessMod$getGrid();
        return SmartDoublingPlans.rewriteForSubmission(
                plan, service::getProviders, SmartDoublingPatterns.levelOf(grid));
    }

    @Inject(method = "getProviders", at = @At("HEAD"), cancellable = true)
    private void uselessMod$getSmartDoublingProviders(
            IPatternDetails pattern, CallbackInfoReturnable<Iterable<ICraftingProvider>> callback) {
        if (!(pattern instanceof ScaledPattern)) {
            return;
        }

        IPatternDetails original = SmartDoublingPatterns.unwrap(pattern);
        Iterable<ICraftingProvider> providers = ((CraftingService) (Object) this).getProviders(original);
        callback.setReturnValue(SmartDoublingPlanner.eligibleProviders(providers));
    }
}
