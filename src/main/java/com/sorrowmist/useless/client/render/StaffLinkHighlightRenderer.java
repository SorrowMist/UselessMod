package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * 无线物流界面里双击某个容器后，把那个方块在世界里描个框；再双击一次取消。
 *
 * <p>纯客户端状态：只在客户端存一个 {@link GlobalPos}，不占网络包。渲染路径照
 * {@link AeLinkHighlightRenderer}（它又是照 {@code ConstructionWandPreviewRenderer} 抄的）：
 * {@code AFTER_LEVEL} 阶段拿到的 pose stack 是 identity，modelview 上的相机旋转已经被弹掉，
 * 所以这里要自己补一个反向相机旋转，并把方块坐标减掉相机位置。</p>
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class StaffLinkHighlightRenderer {
    private static final float RED = 0.30F;
    private static final float GREEN = 0.95F;
    private static final float BLUE = 0.55F;

    @Nullable
    private static GlobalPos highlighted;
    @Nullable
    private static Level observedLevel;

    private StaffLinkHighlightRenderer() {
    }

    /** 双击同一个锚点 = 取消；双击另一个 = 换过去。 */
    public static void toggle(GlobalPos anchor) {
        highlighted = anchor.equals(highlighted) ? null : anchor;
    }

    public static boolean isHighlighted(GlobalPos anchor) {
        return anchor.equals(highlighted);
    }

    @Nullable
    public static GlobalPos highlighted() {
        return highlighted;
    }

    /** 离开世界时清掉，免得下次进来还留着上一局的框。 */
    public static void clear() {
        highlighted = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Level level = Minecraft.getInstance().level;
        if (level != observedLevel) {
            observedLevel = level;
            highlighted = null;
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GlobalPos target = highlighted;
        if (minecraft.level == null || target == null
                || !target.dimension().equals(minecraft.level.dimension())) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        pose.pushPose();
        pose.mulPose(new Quaternionf(camera.rotation()).invert());
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        AABB box = new AABB(target.pos()).inflate(0.0025)
                .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        LevelRenderer.renderLineBox(pose, lines, box, RED, GREEN, BLUE, 0.95F);
        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }
}
