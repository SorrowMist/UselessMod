package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.constructionwand.ConstructionWandLogic;
import com.sorrowmist.useless.network.ConstructionWandPreviewRequestPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class ConstructionWandPreviewRenderer {
    private static List<BlockPos> preview = List.of();
    private static BlockPos requestedPos;
    private static net.minecraft.core.Direction requestedFace;
    private static InteractionHand requestedHand;
    private static long lastRequestTick = Long.MIN_VALUE;

    private ConstructionWandPreviewRenderer() {}

    public static void setPreview(List<BlockPos> positions) {
        preview = List.copyOf(positions);
    }

    @SubscribeEvent
    public static void render(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        InteractionHand hand = activeHand(minecraft);
        if (hand == null) {
            clear();
            return;
        }

        BlockHitResult hit = event.getTarget();
        long tick = minecraft.level.getGameTime();
        if (requestedPos == null
                || !requestedPos.equals(hit.getBlockPos())
                || requestedFace != hit.getDirection()
                || requestedHand != hand
                || tick - lastRequestTick >= 5) {
            requestedPos = hit.getBlockPos().immutable();
            requestedFace = hit.getDirection();
            requestedHand = hand;
            lastRequestTick = tick;
            setPreview(List.of());
            PacketDistributor.sendToServer(new ConstructionWandPreviewRequestPacket(hit, hand));
        }

        if (preview.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        pose.pushPose();
        for (BlockPos pos : preview) {
            AABB box = new AABB(pos).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(pose, lines, box, 0.25F, 1.0F, 0.35F, 0.9F);
        }
        pose.popPose();
        event.setCanceled(true);
    }

    private static InteractionHand activeHand(Minecraft minecraft) {
        if (ConstructionWandLogic.isEnabled(minecraft.player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (ConstructionWandLogic.isEnabled(minecraft.player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static void clear() {
        preview = List.of();
        requestedPos = null;
        requestedFace = null;
        requestedHand = null;
        lastRequestTick = Long.MIN_VALUE;
    }
}
