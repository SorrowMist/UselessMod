package com.sorrowmist.useless.mixin.jade;

import com.sorrowmist.useless.compat.jade.JadePatternStorageSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.addon.universal.ItemStorageProvider;
import snownee.jade.api.Accessor;

@Mixin(ItemStorageProvider.class)
public abstract class JadeItemStorageProviderMixin {
    @Inject(method = "putData", at = @At("HEAD"), cancellable = true)
    private static void useless$writeCachedPatternData(Accessor<?> accessor, CallbackInfo ci) {
        if (JadePatternStorageSnapshot.writeData(accessor)) {
            ci.cancel();
        }
    }
}
