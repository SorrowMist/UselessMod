package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.network.StaffLinkHighlightRequestPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;

import java.util.List;

/**
 * 手持启用无线物流的造化杖时，按流向绘制服务端下发的容器边框。
 * 每 {@link #REFRESH_INTERVAL} tick 请求最新数据，界面关闭时仍能显示。
 *
 * <p>与 {@link AeLinkHighlightRenderer} 共用 1.20.1 的世界视图阶段
 * AFTER_PARTICLES，只减一次相机位置，不能使用 AFTER_LEVEL 的投影栈。</p>
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class StaffLinkHighlightRenderer {
    /** 提示数据变化很慢，没必要每 tick 问服务端。 */
    private static final int REFRESH_INTERVAL = 10;
    /** 网络可以铺得很远，太远的框既看不见又白费顶点，裁掉。 */
    private static final double MAX_RENDER_DISTANCE = 128.0;

    /** 发送端：至少有一条启用的「释放」线路。 */
    private static final float RELEASE_RED = 1.00F;
    private static final float RELEASE_GREEN = 0.55F;
    private static final float RELEASE_BLUE = 0.15F;
    /** 接收端：只有启用的「吸收」线路。 */
    private static final float ABSORB_RED = 0.35F;
    private static final float ABSORB_GREEN = 0.72F;
    private static final float ABSORB_BLUE = 1.00F;
    /** 已绑定但所有线路都关着。 */
    private static final float DISABLED_RED = 0.45F;
    private static final float DISABLED_GREEN = 0.45F;
    private static final float DISABLED_BLUE = 0.48F;
    private static final float LINE_ALPHA = 0.95F;

    private static List<GlobalPos> releaseAnchors = List.of();
    private static List<GlobalPos> absorbAnchors = List.of();
    private static List<GlobalPos> disabledAnchors = List.of();
    private static int tickCounter = REFRESH_INTERVAL;

    private StaffLinkHighlightRenderer() {
    }

    /** 客户端收到服务端的分类结果。 */
    public static void setHighlights(List<GlobalPos> release, List<GlobalPos> absorb,
                                     List<GlobalPos> disabled) {
        releaseAnchors = List.copyOf(release);
        absorbAnchors = List.copyOf(absorb);
        disabledAnchors = List.copyOf(disabled);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !shouldRequest(minecraft)) {
            clear();
            return;
        }
        if (++tickCounter < REFRESH_INTERVAL) {
            return;
        }
        tickCounter = 0;
        PacketDistributor.sendToServer(new StaffLinkHighlightRequestPacket());
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !shouldRequest(minecraft)
                || (releaseAnchors.isEmpty() && absorbAnchors.isEmpty() && disabledAnchors.isEmpty())) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        pose.pushPose();

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        ResourceKey<Level> dimension = minecraft.level.dimension();
        drawAll(pose, lines, cameraPos, dimension, releaseAnchors,
                RELEASE_RED, RELEASE_GREEN, RELEASE_BLUE);
        drawAll(pose, lines, cameraPos, dimension, absorbAnchors,
                ABSORB_RED, ABSORB_GREEN, ABSORB_BLUE);
        drawAll(pose, lines, cameraPos, dimension, disabledAnchors,
                DISABLED_RED, DISABLED_GREEN, DISABLED_BLUE);

        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }

    private static void drawAll(PoseStack pose, VertexConsumer lines, Vec3 cameraPos,
                                ResourceKey<Level> dimension, List<GlobalPos> anchors,
                                float red, float green, float blue) {
        for (GlobalPos anchor : anchors) {
            // 一张网络的锚点可以跨维度，这里只画当前维度那批。
            if (!anchor.dimension().equals(dimension)) {
                continue;
            }
            if (Vec3.atCenterOf(anchor.pos()).distanceToSqr(cameraPos)
                    > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }
            AABB box = new AABB(anchor.pos()).inflate(0.0025)
                    .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, LINE_ALPHA);
        }
    }

    /**
     * 只在手上真的拿着「开着无线物流模式」的造化杖时才要数据。
     *
     * <p>刻意<b>不</b>在这里判断「有没有网络」：网络列表现在挂在归属者名下、存在服务端，
     * 客户端手里没有这份数据。一张都没有时服务端会回空包，自然什么都不画。</p>
     */
    private static boolean shouldRequest(Minecraft minecraft) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (!BeefToolVariants.isBeafTool(stack)) {
                continue;
            }
            if (EndlessBeafItem.isStaffLinkEnabled(stack)) {
                return true;
            }
        }
        return false;
    }

    /** 清空缓存并把刷新计数器置为上限，使下次手持当 tick 立即请求一次最新数据。 */
    private static void clear() {
        releaseAnchors = List.of();
        absorbAnchors = List.of();
        disabledAnchors = List.of();
        tickCounter = REFRESH_INTERVAL;
    }
}
