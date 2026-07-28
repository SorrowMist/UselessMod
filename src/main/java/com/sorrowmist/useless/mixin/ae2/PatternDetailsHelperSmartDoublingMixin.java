package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatternDetailsHelper.class, priority = 1200, remap = false)
public abstract class PatternDetailsHelperSmartDoublingMixin {
    @Inject(
            method = "decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("HEAD"), cancellable = true)
    private static void uselessMod$decodeScaledKey(
            AEItemKey definition, Level level, CallbackInfoReturnable<IPatternDetails> callback) {
        if (SmartDoublingPatterns.definitionOperations(definition) != null) {
            callback.setReturnValue(SmartDoublingPatterns.restore(definition, level));
        }
    }

    @Inject(
            method = "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("HEAD"), cancellable = true)
    private static void uselessMod$decodeScaledStack(
            ItemStack stack, Level level, CallbackInfoReturnable<IPatternDetails> callback) {
        AEItemKey definition = AEItemKey.of(stack);
        if (definition != null && SmartDoublingPatterns.definitionOperations(definition) != null) {
            callback.setReturnValue(SmartDoublingPatterns.restore(definition, level));
        }
    }
}
