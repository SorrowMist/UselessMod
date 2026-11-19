package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.content.blockentities.CreatorDollBlockEntity;
import com.sorrowmist.useless.content.blocks.CreatorDollBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** 使用当前方块模型绘制弹跳姿态，挤压变换参考 ExtendedAE Plus 提交 3832a827e859807a2fff3857c8719a097181804c。 */
public final class CreatorDollBounceRenderer implements BlockEntityRenderer<CreatorDollBlockEntity> {
    private final BlockRenderDispatcher blocks;

    public CreatorDollBounceRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(CreatorDollBlockEntity doll, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (doll.getLevel() == null) return;

        BlockState state = doll.getBlockState();
        float squash = state.getValue(CreatorDollBlock.BOUNCING) ? doll.getSquash(partialTick) : 0;
        pose.pushPose();
        pose.translate(0.5, 0.08F * squash, 0.5);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(state, pose, buffers, light, overlay);
        pose.popPose();
    }
}
