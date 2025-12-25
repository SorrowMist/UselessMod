package com.sorrowmist.useless.client.render;

import com.sorrowmist.useless.items.EndlessBeafItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = "useless_mod")
public class AdvancedAEAdvPatternProviderRenderer {
    
    // 纹理资源位置
    private static final ResourceLocation MASTER_TEXTURE = new ResourceLocation("useless_mod:textures/icon/master.png");
    private static final ResourceLocation SLAVE_TEXTURE = new ResourceLocation("useless_mod:textures/icon/slave.png");
    
    float thickness = 0.05f;
    
    // 注册渲染器
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 只有在AdvancedAE模组安装时才注册渲染器
        try {
            // 检查AdvancedAE类是否存在
            Class.forName("net.pedroksl.advanced_ae.common.definitions.AAEBlockEntities");
            
            // 使用反射获取BlockEntityType实例
            Class<?> aaeBlockEntitiesClass = Class.forName("net.pedroksl.advanced_ae.common.definitions.AAEBlockEntities");
            Method advPatternProviderMethod = aaeBlockEntitiesClass.getDeclaredMethod("ADV_PATTERN_PROVIDER");
            Method smallAdvPatternProviderMethod = aaeBlockEntitiesClass.getDeclaredMethod("SMALL_ADV_PATTERN_PROVIDER");
            
            Object advPatternProviderHolder = advPatternProviderMethod.invoke(null);
            Object smallAdvPatternProviderHolder = smallAdvPatternProviderMethod.invoke(null);
            
            // 获取实际的BlockEntityType
            Method getMethod = advPatternProviderHolder.getClass().getDeclaredMethod("get");
            Object advPatternProviderType = getMethod.invoke(advPatternProviderHolder);
            Object smallAdvPatternProviderType = getMethod.invoke(smallAdvPatternProviderHolder);
            
            // 注册渲染器 - 使用反射调用registerBlockEntityRenderer方法
            try {
                // 获取registerBlockEntityRenderer方法
                Method registerMethod = EntityRenderersEvent.RegisterRenderers.class.getDeclaredMethod("registerBlockEntityRenderer", net.minecraft.world.level.block.entity.BlockEntityType.class, BlockEntityRendererProvider.class);
                
                // 创建渲染器提供者
                BlockEntityRendererProvider<net.minecraft.world.level.block.entity.BlockEntity> rendererProvider = ctx -> new GenericAdvPatternProviderRenderer();
                
                // 注册渲染器
                registerMethod.invoke(event, advPatternProviderType, rendererProvider);
                registerMethod.invoke(event, smallAdvPatternProviderType, rendererProvider);
            } catch (Exception e) {
                // 注册失败，跳过
            }
        } catch (Exception e) {
            // AdvancedAE模组未安装或注册失败，跳过注册
        }
    }
    
    // 通用渲染器，不直接引用AdvancedAE类
    private static class GenericAdvPatternProviderRenderer implements BlockEntityRenderer<net.minecraft.world.level.block.entity.BlockEntity> {
        
        public GenericAdvPatternProviderRenderer() {}
        
        @Override
        public int getViewDistance() {
            return 512;
        }
        
        @Override
        public boolean shouldRenderOffScreen(net.minecraft.world.level.block.entity.BlockEntity be) {
            // 始终允许渲染，因为我们需要检查玩家是否手持工具
            return true;
        }
        
        @Override
        public void render(net.minecraft.world.level.block.entity.BlockEntity be, float partialTicks, PoseStack poseStack, 
                          MultiBufferSource buffers, int packedLight, int packedOverlay) {
            if (be == null) return;
            
            // 检查是否是AdvancedAE的高级样板供应器
            String className = be.getClass().getName();
            if (!(className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") || 
                  className.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity"))) {
                return;
            }
            
            // 使用反射获取level和blockPos
            Level level = null;
            BlockPos currentPos = null;
            try {
                Method getLevelMethod = be.getClass().getMethod("getLevel");
                Method getBlockPosMethod = be.getClass().getMethod("getBlockPos");
                level = (Level) getLevelMethod.invoke(be);
                currentPos = (BlockPos) getBlockPosMethod.invoke(be);
            } catch (Exception e) {
                return;
            }
            
            if (level == null || currentPos == null) return;
            
            // 检查玩家是否手持牛排工具（只检查主手和副手）
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            
            boolean holdingItem = false;
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            if ((mainHand.getItem() instanceof EndlessBeafItem) || (offHand.getItem() instanceof EndlessBeafItem)) {
                holdingItem = true;
            }
            
            // 如果玩家没有手持牛排工具，不渲染
            if (!holdingItem) return;
            
            // 检查当前方块是否是主供应器或从供应器
            boolean isMaster = EndlessBeafItem.isMasterPatternProvider(currentPos);
            boolean isSlave = EndlessBeafItem.isSlavePatternProvider(currentPos);
            
            // 只渲染主端或当前选定主端的从端
            boolean shouldRender = false;
            ResourceLocation texture = null;
            
            if (isMaster) {
                // 主端始终渲染
                shouldRender = true;
                texture = MASTER_TEXTURE;
            } else if (isSlave) {
                // 从端只有当它属于当前选定的主端时才渲染
                if (EndlessBeafItem.isSlaveOfCurrentMaster(currentPos, level)) {
                    shouldRender = true;
                    texture = SLAVE_TEXTURE;
                }
            }
            
            if (!shouldRender || texture == null) return;
            
            // 保存状态
            poseStack.pushPose();
            
            // 在当前方块的六个面上渲染图片
            for (Direction direction : Direction.values()) {
                renderStaticLinkPoint(poseStack, buffers, direction, packedLight, texture);
            }
            
            // 恢复状态
            poseStack.popPose();
        }
        
        /**
         * 在指定面上渲染静态图片
         */
        private void renderStaticLinkPoint(PoseStack poseStack, MultiBufferSource buffers, Direction face, int packedLight, ResourceLocation texture) {
            poseStack.pushPose();
            
            // 图片大小
            float size = 0.2f;
            // 增加偏移量，确保图片渲染在机器表面
            float offset = 0.01f;
            
            // 根据面调整位置和旋转
            switch (face) {
                case DOWN:
                    poseStack.translate(0.5f, -offset, 0.5f);
                    poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                    break;
                case UP:
                    poseStack.translate(0.5f, 1.0f + offset, 0.5f);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                    break;
                case NORTH:
                    poseStack.translate(0.5f, 0.5f, -offset);
                    // 北面不需要额外旋转，直接显示
                    break;
                case SOUTH:
                    poseStack.translate(0.5f, 0.5f, 1.0f + offset);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                    break;
                case WEST:
                    poseStack.translate(-offset, 0.5f, 0.5f);
                    poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
                    break;
                case EAST:
                    poseStack.translate(1.0f + offset, 0.5f, 0.5f);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
                    break;
            }
            
            // 使用适合纹理渲染的渲染类型
            RenderType renderType = RenderType.text(texture);
            VertexConsumer vertexConsumer = buffers.getBuffer(renderType);
            
            // 渲染图片
            renderTextureQuad(poseStack, vertexConsumer, size, packedLight);
            
            poseStack.popPose();
        }
        
        /**
         * 渲染完整的纹理图像，确保图像完整显示且方向正确
         */
        private void renderTextureQuad(PoseStack poseStack, VertexConsumer consumer, float size, int packedLight) {
            PoseStack.Pose pose = poseStack.last();
            
            // 颜色（白色，不改变纹理颜色）
            float r = 1.0f;
            float g = 1.0f;
            float b = 1.0f;
            float a = 1.0f;
            
            // 基础顶点坐标
            float x1 = -size;
            float y1 = -size;
            float x2 = size;
            float y2 = size;
            
            // 使用标准UV坐标
            float u1 = 0.0f;
            float v1 = 0.0f;
            float u2 = 1.0f;
            float v2 = 1.0f;
            
            // 渲染四边形，使用正确的顶点顺序
            // 顶点顺序：左上 -> 右上 -> 右下 -> 左下
            // 左上
            consumer.vertex(pose.pose(), x1, y2, 0.0f)
                    .color(r, g, b, a)
                    .uv(u1, v1)
                    .uv2(packedLight)
                    .endVertex();
            
            // 右上
            consumer.vertex(pose.pose(), x2, y2, 0.0f)
                    .color(r, g, b, a)
                    .uv(u2, v1)
                    .uv2(packedLight)
                    .endVertex();
            
            // 右下
            consumer.vertex(pose.pose(), x2, y1, 0.0f)
                    .color(r, g, b, a)
                    .uv(u2, v2)
                    .uv2(packedLight)
                    .endVertex();
            
            // 左下
            consumer.vertex(pose.pose(), x1, y1, 0.0f)
                    .color(r, g, b, a)
                    .uv(u1, v2)
                    .uv2(packedLight)
                    .endVertex();
        }
    }
}