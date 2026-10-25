package com.sorrowmist.useless.mixin.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.compat.jei.AEKeyJeiIngredients;
import com.sorrowmist.useless.compat.jei.GenericStackJeiIngredientProviders;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.Optional;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiUtil", remap = false)
public abstract class JemiAEKeyMixin {
    @Inject(method = "getStack(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;)Ldev/emi/emi/api/stack/EmiStack;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void useless$aeKey(IIngredientType<?> type, Object ingredient, CallbackInfoReturnable<EmiStack> cir) {
        if (type == AEKeyJeiIngredients.TYPE && ingredient instanceof AEKeyJeiIngredients.KeyIngredient key) {
            cir.setReturnValue(new AEKeyEmiStack(key));
            return;
        }
        var stack = GenericStackJeiIngredientProviders.fromJei(type, ingredient);
        if (stack != null && !(stack.what() instanceof AEItemKey) && !(stack.what() instanceof AEFluidKey)) {
            // Carry the converter's quantity rather than a custom JEMI stack's default amount.
            // AE2 reads EmiIngredient.getAmount() separately when it constructs processing inputs.
            cir.setReturnValue(new AEKeyEmiStack(new AEKeyJeiIngredients.KeyIngredient(stack.what(), stack.amount())));
        }
    }

    @Inject(method = "getTyped(Ldev/emi/emi/api/stack/EmiStack;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void useless$nativeTyped(EmiStack stack, CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {
        if (stack instanceof AEKeyEmiStack keyStack) {
            keyStack.nativeTypedIngredient().ifPresent(typed -> cir.setReturnValue(Optional.of(typed)));
        }
    }
}
