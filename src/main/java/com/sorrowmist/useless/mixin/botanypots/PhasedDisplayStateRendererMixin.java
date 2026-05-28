package com.sorrowmist.useless.mixin.botanypots;

import com.mojang.blaze3d.vertex.PoseStack;
import net.darkhax.botanypots.common.api.data.display.render.DisplayRenderer;
import net.darkhax.botanypots.common.api.data.display.types.Display;
import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity;
import net.darkhax.botanypots.common.impl.data.display.renderer.PhasedDisplayStateRenderer;
import net.darkhax.botanypots.common.impl.data.display.types.PhasedDisplayState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(PhasedDisplayStateRenderer.class)
public class PhasedDisplayStateRendererMixin {

    /**
     * @author C-H716
     * @reason 强制显示作物成熟状态
     */
    @Overwrite
    public float render(BlockEntityRendererProvider.Context renderContext, PhasedDisplayState displayState, PoseStack stack, Level level, BlockPos pos, float tickDelta, MultiBufferSource bufferSource, int light, int overlay, BotanyPotBlockEntity pot, float progress, float growthScale, float heightOffset) {
        // 直接获取成熟阶段（最后一个阶段）
        int maturePhaseIndex = displayState.getDisplayPhases().size() - 1;
        Display matureState = displayState.getDisplayPhases().get(maturePhaseIndex);
        return DisplayRenderer.renderState(renderContext, matureState, stack, level, pos, tickDelta, bufferSource, light, overlay, pot, progress, growthScale, heightOffset);
    }
}
