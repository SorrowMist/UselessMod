package com.sorrowmist.useless.mixin.botanypots;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.darkhax.botanypots.block.BotanyPotRenderer;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BotanyPotRenderer.class,remap=false)
public class BotanyPotRendererMixin {

    @Inject(
            method = "render(Lnet/darkhax/botanypots/block/BlockEntityBotanyPot;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void checkRenderingEnabled(BlockEntityBotanyPot pot, float tickDelta, PoseStack pose, MultiBufferSource bufferSource, int light, int overlay, CallbackInfo ci) {
        if (!ConfigManager.shouldEnableBotanyPotRendering()) {
            ci.cancel();
        }
    }
}
