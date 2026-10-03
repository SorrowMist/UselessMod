package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
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
        // 判定「可复用输入」所需的关卡不在这里取：SmartDoublingPlanner 会从供应器方块实体
        // （本模组自己的机器）拿。⛔ 千万不要为了拿关卡往 CraftingService 上再挂 mixin ——
        // 该类上已有 OmniSequence 的 OmniCraftingServiceMixin，多加一个会静默顶掉对方的注入，
        // 导致 AppliedEnhancements 的 AELIS 精确规划器不参与、计划退化成 long 饱和值、任务卡在 0。
        return SmartDoublingPlans.rewriteForSubmission(plan, service::getProviders);
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
