package com.sorrowmist.useless.mixin.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingCalculationContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationSmartDoublingMixin implements SmartDoublingCalculationContext {
    @Unique
    private IGrid uselessMod$craftingGrid;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void uselessMod$retainCraftingGrid(
            Level level, IGrid grid, ICraftingSimulationRequester requester,
            GenericStack output, CalculationStrategy strategy, CallbackInfo callback) {
        uselessMod$craftingGrid = grid;
    }

    @Override
    public IGrid uselessMod$getCraftingGrid() {
        return uselessMod$craftingGrid;
    }
}
