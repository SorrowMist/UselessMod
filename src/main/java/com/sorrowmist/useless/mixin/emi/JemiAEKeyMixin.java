package com.sorrowmist.useless.mixin.emi;

import com.sorrowmist.useless.compat.jei.AEKeyJeiIngredients;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import mezz.jei.api.ingredients.IIngredientType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiUtil", remap = false)
public abstract class JemiAEKeyMixin {
    @Inject(method = "getStack(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;)Ldev/emi/emi/api/stack/EmiStack;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void useless$aeKey(IIngredientType<?> type, Object ingredient, CallbackInfoReturnable<EmiStack> cir) {
        if (type == AEKeyJeiIngredients.TYPE && ingredient instanceof AEKeyJeiIngredients.KeyIngredient key)
            cir.setReturnValue(new AEKeyEmiStack(key));
    }
}
