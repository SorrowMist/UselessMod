package com.sorrowmist.useless.mixin.botanypots;

import net.darkhax.botanypots.common.impl.BotanyPotsContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BotanyPotsContent.class)
public abstract class BotanyPotsContentMixin {
    @Inject(method = "bindBlockEntityRenderer", at = @At("HEAD"), cancellable = true)
    private void bindBlockEntityRenderer(CallbackInfo ci) {
        ci.cancel();
    }
}