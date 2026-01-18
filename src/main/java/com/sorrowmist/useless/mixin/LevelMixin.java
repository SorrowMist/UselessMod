package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Level.class)
public class LevelMixin {
    @Inject(
            method = "isDay",
            at = @At("HEAD"),
            cancellable = true
    )
    private void injectIsDay(CallbackInfoReturnable<Boolean> cir) {
        if (this.isUselessDimension()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "isRaining",
            at = @At("HEAD"),
            cancellable = true
    )
    private void uselessDimAlwaysClear_rain(CallbackInfoReturnable<Boolean> cir) {
        if (this.isUselessDimension()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "isThundering",
            at = @At("HEAD"),
            cancellable = true
    )
    private void uselessDimAlwaysClear_thunder(CallbackInfoReturnable<Boolean> cir) {
        if (this.isUselessDimension()) {
            cir.setReturnValue(false);
        }
    }

    private boolean isUselessDimension() {
        Level level = (Level) (Object) this;
        return UselessMod.MODID.equals(level.dimension().location().getNamespace());
    }
}
