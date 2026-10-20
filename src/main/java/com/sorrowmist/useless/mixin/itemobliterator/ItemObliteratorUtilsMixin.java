package com.sorrowmist.useless.mixin.itemobliterator;

import com.sorrowmist.useless.compat.itemobliterator.ItemObliteratorProtection;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "elocindev.item_obliterator.neoforge.utils.Utils",
        remap = false,
        priority = 11000)
public abstract class ItemObliteratorUtilsMixin {
    @Inject(
            method = "isDisabled(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void uselessMod$protectItemId(
            String itemId, CallbackInfoReturnable<Boolean> cir) {
        protectItemId(itemId, cir);
    }

    @Inject(
            method = "isDisabled(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void uselessMod$protectItemStack(
            ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ItemObliteratorProtection.isProtected(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "shouldRecipeBeDisabled(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void uselessMod$protectRecipeOutput(
            String itemId, CallbackInfoReturnable<Boolean> cir) {
        protectItemId(itemId, cir);
    }

    @Inject(
            method = "isDisabledInteract(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void uselessMod$protectInteraction(
            String itemId, CallbackInfoReturnable<Boolean> cir) {
        protectItemId(itemId, cir);
    }

    @Inject(
            method = "isDisabledAttack(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void uselessMod$protectAttack(
            String itemId, CallbackInfoReturnable<Boolean> cir) {
        protectItemId(itemId, cir);
    }

    private static void protectItemId(
            String itemId, CallbackInfoReturnable<Boolean> cir) {
        if (ItemObliteratorProtection.isProtectedItemId(itemId)) {
            cir.setReturnValue(false);
        }
    }
}
