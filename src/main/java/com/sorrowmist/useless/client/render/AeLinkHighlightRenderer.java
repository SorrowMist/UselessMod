package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.network.AeLinkPreviewRequestPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.List;

/**
 * 手持造化杖且开了 AE 连接模式时，把「工具绑定的访问点」和「它已经连上的机器」画上边框。
 *
 * <p>渲染位置选 {@code AFTER_LEVEL}、并在 set up 时乘一个反向相机旋转，是照
 * {@link ConstructionWandPreviewRenderer} 的空中预览那条已经在跑的路径抄的：
 * NeoForge 在 {@code GameRenderer} 里分发 AFTER_LEVEL 时传的是 <b>null</b> pose stack
 * （事件内部退化成 identity），而 modelview 栈在 {@code LevelRenderer.renderLevel} 结束时
 * 已经弹掉相机旋转，所以这里必须自己补上反向旋转，并把方块坐标减掉相机位置。</p>
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class AeLinkHighlightRenderer {
    /** 提示数据变化很慢，没必要每 tick 问服务端。 */
    private static final int REFRESH_INTERVAL = 10;
    private static final double MAX_RENDER_DISTANCE = 128.0;

    private static ResourceLocation dimension;
    private static BlockPos accessPoint;
    private static List<BlockPos> machines = List.of();
    private static int tickCounter = REFRESH_INTERVAL;

    private AeLinkHighlightRenderer() {}

    public static void setLinks(ResourceLocation machineDimension, BlockPos accessPoint,
                                List<BlockPos> machines) {
        AeLinkHighlightRenderer.dimension = machineDimension;
        AeLinkHighlightRenderer.accessPoint = accessPoint.immutable();
        AeLinkHighlightRenderer.machines = List.copyOf(machines);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !shouldRequest(minecraft)) {
            clear();
            return;
        }
        if (++tickCounter < REFRESH_INTERVAL) {
            return;
        }
        tickCounter = 0;
        PacketDistributor.sendToServer(new AeLinkPreviewRequestPacket());
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || dimension == null
                || !dimension.equals(minecraft.level.dimension().location())
                || (accessPoint == null && machines.isEmpty())) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        pose.pushPose();
        pose.mulPose(new Quaternionf(camera.rotation()).invert());
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        if (accessPoint != null) {
            // 绑定的访问点用暖色，和「已连接的机器」区分开。
            drawBox(pose, lines, accessPoint, cameraPos, 1.0F, 0.82F, 0.30F, 0.95F);
        }
        for (BlockPos machine : machines) {
            if (Vec3.atCenterOf(machine).distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }
            drawBox(pose, lines, machine, cameraPos, 0.35F, 0.78F, 1.0F, 0.9F);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, BlockPos pos, Vec3 cameraPos,
                                float red, float green, float blue, float alpha) {
        AABB box = new AABB(pos).inflate(0.0025).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, alpha);
    }

    /** 只在手上真的拿着「开着 AE 连接模式、且已经绑定访问点」的造化杖时才要提示数据。 */
    private static boolean shouldRequest(Minecraft minecraft) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (!BeefToolVariants.isBeafTool(stack)) continue;
            if (!stack.getOrDefault(UComponents.AeNetworkConnectComponent.get(), false)) continue;
            if (stack.has(UComponents.WIRELESS_LINK_TARGET.get())) return true;
        }
        return false;
    }

    /** 收工：清掉缓存，并把计数器顶满，这样下次手持的当 tick 就会立刻要一次最新数据。 */
    private static void clear() {
        dimension = null;
        accessPoint = null;
        machines = List.of();
        tickCounter = REFRESH_INTERVAL;
    }
}
