package com.sorrowmist.useless.mixin.ae2;

import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.me.service.CraftingService;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingCalculationContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingSimulationState.class, priority = 1200, remap = false)
public abstract class CraftingSimulationStateSmartDoublingMixin {
    @Inject(method = "buildCraftingPlan", at = @At("HEAD"))
    private static void uselessMod$applySmartDoubling(
            CraftingSimulationState state, CraftingCalculation calculation,
            long calculatedAmount, CallbackInfoReturnable<CraftingPlan> callback) {
        var grid = ((SmartDoublingCalculationContext) (Object) calculation).uselessMod$getCraftingGrid();
        if (grid == null || !(grid.getCraftingService() instanceof CraftingService craftingService)) {
            return;
        }

        var crafts = ((CraftingSimulationStateAccessor) state).uselessMod$getCrafts();
        var rewritten = SmartDoublingPlanner.rewrite(crafts, craftingService::getProviders);
        crafts.clear();
        crafts.putAll(rewritten);
    }
}
