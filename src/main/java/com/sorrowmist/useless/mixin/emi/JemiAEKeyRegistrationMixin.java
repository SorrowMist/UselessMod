package com.sorrowmist.useless.mixin.emi;

import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStack;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyEmiStackConverter;
import dev.emi.emi.api.EmiRegistry;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiPlugin", remap = false)
public abstract class JemiAEKeyRegistrationMixin {
    @Inject(method = "register", at = @At("RETURN"), require = 1)
    private void useless$aeKeySerializer(EmiRegistry registry, CallbackInfo ci) {
        AEKeyEmiStack.register(registry);
        AEKeyEmiStackConverter.register();
    }
}
