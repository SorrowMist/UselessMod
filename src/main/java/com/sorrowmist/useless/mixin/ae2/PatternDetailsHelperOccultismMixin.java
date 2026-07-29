package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OccultismBoundBookPatternDetails;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatternDetailsHelper.class, priority = 1100, remap = false)
public abstract class PatternDetailsHelperOccultismMixin {
    @Inject(
            method = "decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("RETURN"), cancellable = true)
    private static void uselessMod$wrapBoundBookKey(
            AEItemKey definition, Level level, CallbackInfoReturnable<IPatternDetails> callback) {
        callback.setReturnValue(OccultismBoundBookPatternDetails.wrap(
                callback.getReturnValue(), level));
    }

    @Inject(
            method = "decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("RETURN"), cancellable = true)
    private static void uselessMod$wrapBoundBookStack(
            ItemStack stack, Level level, CallbackInfoReturnable<IPatternDetails> callback) {
        callback.setReturnValue(OccultismBoundBookPatternDetails.wrap(
                callback.getReturnValue(), level));
    }
}
