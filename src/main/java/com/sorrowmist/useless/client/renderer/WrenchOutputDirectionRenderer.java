package com.sorrowmist.useless.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.awt.*;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class WrenchOutputDirectionRenderer {

    // 扳手设置方向的颜色（绿色）
    private static final Color WRENCH_DIRECTION_COLOR = new Color(0, 255, 100, 128);
    // 线条颜色（更亮的绿色）
    private static final Color WRENCH_DIRECTION_LINE_COLOR = new Color(0, 255, 100, 255);

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // 检查玩家是否手持扳手
        if (!AdvancedAlloyFurnaceBlockEntity.isWrench(player.getMainHandItem())
                && !AdvancedAlloyFurnaceBlockEntity.isWrench(player.getOffhandItem())) {
            return;
        }

        // 获取玩家准星指向的方块
        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (!(blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace)) {
            return;
        }

        // 获取扳手设置的方向
        Direction outputDirection = furnace.getCachedOutputDirection();
        if (outputDirection == null) {
            return;
        }

        // 渲染高亮面
        renderHighlightedFace(event, pos, outputDirection);
    }

    private static void renderHighlightedFace(RenderLevelStageEvent event, BlockPos pos, Direction direction) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();

        // 获取相机位置
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        // 获取面的形状（稍微向外扩展一点，避免z-fighting）
        VoxelShape faceShape = getFaceShape(direction, 0.002);

        // 使用 LevelRenderer 渲染形状
        float red = WRENCH_DIRECTION_COLOR.getRed() / 255f;
        float green = WRENCH_DIRECTION_COLOR.getGreen() / 255f;
        float blue = WRENCH_DIRECTION_COLOR.getBlue() / 255f;
        float alpha = WRENCH_DIRECTION_COLOR.getAlpha() / 255f;

        // 渲染填充面 - 使用 lines 类型的 RenderType 避免 UV 要求
        VertexConsumer fillConsumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        renderShapeFilled(poseStack, fillConsumer, faceShape, red, green, blue, alpha);

        // 渲染边框
        float lineRed = WRENCH_DIRECTION_LINE_COLOR.getRed() / 255f;
        float lineGreen = WRENCH_DIRECTION_LINE_COLOR.getGreen() / 255f;
        float lineBlue = WRENCH_DIRECTION_LINE_COLOR.getBlue() / 255f;
        float lineAlpha = WRENCH_DIRECTION_LINE_COLOR.getAlpha() / 255f;

        VertexConsumer lineConsumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        renderShapeEdges(poseStack, lineConsumer, faceShape, lineRed, lineGreen, lineBlue, lineAlpha);

        poseStack.popPose();
    }

    private static VoxelShape getFaceShape(Direction direction, double expand) {
        // 根据方向返回对应的面的形状（从0到1的完整面，稍微向外扩展）
        return switch (direction) {
            case DOWN -> Shapes.box(0, -expand, 0, 1, 0, 1);
            case UP -> Shapes.box(0, 1, 0, 1, 1 + expand, 1);
            case NORTH -> Shapes.box(0, 0, -expand, 1, 1, 0);
            case SOUTH -> Shapes.box(0, 0, 1, 1, 1, 1 + expand);
            case WEST -> Shapes.box(-expand, 0, 0, 0, 1, 1);
            case EAST -> Shapes.box(1, 0, 0, 1 + expand, 1, 1);
        };
    }

    private static void renderShapeFilled(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                                           float red, float green, float blue, float alpha) {
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            // 渲染盒子的边框线来表示填充效果
            float x1 = (float) minX;
            float y1 = (float) minY;
            float z1 = (float) minZ;
            float x2 = (float) maxX;
            float y2 = (float) maxY;
            float z2 = (float) maxZ;

            // 底面四条边
            addLine(consumer, poseStack, x1, y1, z1, x2, y1, z1, red, green, blue, alpha);
            addLine(consumer, poseStack, x2, y1, z1, x2, y1, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x2, y1, z2, x1, y1, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x1, y1, z2, x1, y1, z1, red, green, blue, alpha);

            // 顶面四条边
            addLine(consumer, poseStack, x1, y2, z1, x2, y2, z1, red, green, blue, alpha);
            addLine(consumer, poseStack, x2, y2, z1, x2, y2, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x2, y2, z2, x1, y2, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x1, y2, z2, x1, y2, z1, red, green, blue, alpha);

            // 垂直四条边
            addLine(consumer, poseStack, x1, y1, z1, x1, y2, z1, red, green, blue, alpha);
            addLine(consumer, poseStack, x2, y1, z1, x2, y2, z1, red, green, blue, alpha);
            addLine(consumer, poseStack, x2, y1, z2, x2, y2, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x1, y1, z2, x1, y2, z2, red, green, blue, alpha);

            // 内部十字交叉线（表示填充）
            float cx = (x1 + x2) / 2;
            float cy = (y1 + y2) / 2;
            float cz = (z1 + z2) / 2;

            // 水平十字
            addLine(consumer, poseStack, cx, y1, z1, cx, y1, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x1, y1, cz, x2, y1, cz, red, green, blue, alpha);
            addLine(consumer, poseStack, cx, y2, z1, cx, y2, z2, red, green, blue, alpha);
            addLine(consumer, poseStack, x1, y2, cz, x2, y2, cz, red, green, blue, alpha);
        });
    }

    private static void addLine(VertexConsumer consumer, PoseStack poseStack,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float red, float green, float blue, float alpha) {
        consumer.addVertex(poseStack.last().pose(), x1, y1, z1)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), x2 - x1, y2 - y1, z2 - z1);
        consumer.addVertex(poseStack.last().pose(), x2, y2, z2)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), x2 - x1, y2 - y1, z2 - z1);
    }

    private static void renderShapeEdges(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                                          float red, float green, float blue, float alpha) {
        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            float x1 = (float) minX;
            float y1 = (float) minY;
            float z1 = (float) minZ;
            float x2 = (float) maxX;
            float y2 = (float) maxY;
            float z2 = (float) maxZ;

            consumer.addVertex(poseStack.last().pose(), x1, y1, z1)
                    .setColor(red, green, blue, alpha)
                    .setNormal(poseStack.last(), x2 - x1, y2 - y1, z2 - z1);
            consumer.addVertex(poseStack.last().pose(), x2, y2, z2)
                    .setColor(red, green, blue, alpha)
                    .setNormal(poseStack.last(), x2 - x1, y2 - y1, z2 - z1);
        });
    }
}
