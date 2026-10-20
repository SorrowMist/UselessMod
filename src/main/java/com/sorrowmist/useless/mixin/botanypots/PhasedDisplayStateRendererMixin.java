package com.sorrowmist.useless.mixin.botanypots;

import com.mojang.blaze3d.vertex.PoseStack;
import net.darkhax.botanypots.data.displaystate.TransitionalDisplayState;
import net.darkhax.botanypots.data.displaystate.render.TransitionalDisplayStateRenderer;
import net.darkhax.botanypots.data.displaystate.render.DisplayStateRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TransitionalDisplayStateRenderer.class,remap=false)
public class PhasedDisplayStateRendererMixin {
    @Inject(method="render(Lnet/darkhax/botanypots/data/displaystate/TransitionalDisplayState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/MultiBufferSource;IIF)V",at=@At("HEAD"),cancellable=true)
    private void useless$maturePhase(TransitionalDisplayState display,PoseStack stack,Level level,BlockPos pos,MultiBufferSource buffers,int light,int overlay,float progress,CallbackInfo ci){
        if(display.phases.isEmpty()) return;
        DisplayStateRenderer.renderState(display.phases.get(display.phases.size()-1),stack,level,pos,buffers,light,overlay,1F);
        ci.cancel();
    }
}
