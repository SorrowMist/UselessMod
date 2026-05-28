package com.sorrowmist.useless.mixin.botanypots;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.darkhax.botanypots.common.impl.block.BotanyPotRenderer;
import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BotanyPotRenderer.class)
public class BotanyPotRendererMixin {

    @Inject(
            method = "render*",
            at = @At("HEAD"),
            cancellable = true
    )
    private void checkRenderingEnabled(BotanyPotBlockEntity pot, float tickDelta, PoseStack pose, MultiBufferSource bufferSource, int light, int overlay, CallbackInfo ci) {
        if (!ConfigManager.shouldEnableBotanyPotRendering()) {
            ci.cancel();
        }
    }
}
