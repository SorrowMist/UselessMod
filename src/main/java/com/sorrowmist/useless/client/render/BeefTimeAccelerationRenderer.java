package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sorrowmist.useless.content.entities.BeefTimeAccelerationEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class BeefTimeAccelerationRenderer extends EntityRenderer<BeefTimeAccelerationEntity> {
    private static final float TEXT_SCALE = 0.02F;
    private final Font font;

    public BeefTimeAccelerationRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.font = context.getFont();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(BeefTimeAccelerationEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight) {
        if (entity.level().getBlockState(entity.getTargetPos()).isAir()) {
            return;
        }

        String speedText = String.format(Locale.ROOT, "x%.1f", (float) (1 << entity.getTickSpeed()));
        String timeText = String.format(Locale.ROOT, "%.2fs", entity.getRemainingTime() / 20.0F);

        for (Direction face : Direction.values()) {
            renderText(poseStack, buffer, speedText, face, -0.08F, 0xFFFFFF, packedLight);
            renderText(poseStack, buffer, timeText, face, 0.12F, 0xD0D0D0, packedLight);
        }
    }

    private void renderText(PoseStack poseStack,
                            MultiBufferSource buffer,
                            String text,
                            Direction face,
                            float y,
                            int color,
                            int packedLight) {
        poseStack.pushPose();
        moveToFace(poseStack, face);
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        float x = -this.font.width(text) / 2.0F;
        this.font.drawInBatch(
                text,
                x,
                y / TEXT_SCALE,
                color,
                false,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );
        poseStack.popPose();
    }

    private static void moveToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case UP -> {
                poseStack.translate(0.0D, 0.506D, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
            case DOWN -> {
                poseStack.translate(0.0D, -0.506D, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case NORTH -> {
                poseStack.translate(0.0D, 0.0D, -0.506D);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case SOUTH -> poseStack.translate(0.0D, 0.0D, 0.506D);
            case WEST -> {
                poseStack.translate(-0.506D, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            }
            case EAST -> {
                poseStack.translate(0.506D, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BeefTimeAccelerationEntity entity) {
        return null;
    }
}
