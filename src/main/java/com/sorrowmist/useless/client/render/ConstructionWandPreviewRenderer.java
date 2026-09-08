package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
import com.sorrowmist.useless.compat.constructionwand.ConstructionWandLogic;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.network.ConstructionWandPreviewRequestPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.List;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class ConstructionWandPreviewRenderer {
    private static List<BlockPos> preview = List.of();
    private static BlockPos requestedPos;
    private static Direction requestedFace;
    private static Direction requestedStep;
    private static InteractionHand requestedHand;
    private static ConstructionWandCoreMode requestedCore;
    private static boolean requestedAir;
    private static ItemStack requestedTool = ItemStack.EMPTY;
    private static ItemStack requestedOffhand = ItemStack.EMPTY;
    private static long lastRequestTick = Long.MIN_VALUE;
    private static int nextRequestId;
    private static int latestRequestId = -1;

    private ConstructionWandPreviewRenderer() {}

    public static void setPreview(int requestId, List<BlockPos> positions) {
        if (requestId != latestRequestId) return;
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
        ConstructionWandCoreMode core = activeCore(minecraft, hand);
        ItemStack tool = minecraft.player.getItemInHand(hand);
        ItemStack offhand = minecraft.player.getOffhandItem();
        long tick = minecraft.level.getGameTime();
        if (requestedAir
                || requestedPos == null
                || !requestedPos.equals(hit.getBlockPos())
                || requestedFace != hit.getDirection()
                || requestedHand != hand
                || requestedCore != core
                || !ItemStack.matches(requestedTool, tool)
                || !ItemStack.matches(requestedOffhand, offhand)
                || tick - lastRequestTick >= 5) {
            int requestId = beginRequest(hit.getBlockPos(), hit.getDirection(), null,
                                         hand, core, false, tick, tool, offhand);
            preview = List.of();
            PacketDistributor.sendToServer(
                    ConstructionWandPreviewRequestPacket.block(requestId, hit, hand));
        }

        if (!renderPreview(event.getPoseStack(), event.getMultiBufferSource(), event.getCamera())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderAirPreview(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }

        InteractionHand hand = activeHand(minecraft);
        HitResult hit = minecraft.hitResult;
        if (hand == null || hit == null) {
            clear();
            return;
        }
        if (hit.getType() != HitResult.Type.MISS) {
            if (hit.getType() != HitResult.Type.BLOCK) clear();
            return;
        }

        ConstructionWandCoreMode core = activeCore(minecraft, hand);
        ItemStack offhand = minecraft.player.getOffhandItem();
        if (core != ConstructionWandCoreMode.ANGEL
                || !(offhand.getItem() instanceof BlockItem)) {
            clear();
            return;
        }

        BlockPos start = ConstructionWandLogic.angelAirStart(minecraft.player);
        Direction step = ConstructionWandLogic.angelAirStep(minecraft.player);
        ItemStack tool = minecraft.player.getItemInHand(hand);
        long tick = minecraft.level.getGameTime();
        if (!requestedAir
                || requestedPos == null
                || !requestedPos.equals(start)
                || requestedStep != step
                || requestedHand != hand
                || requestedCore != core
                || !ItemStack.matches(requestedTool, tool)
                || !ItemStack.matches(requestedOffhand, offhand)
                || tick - lastRequestTick >= 5) {
            int requestId = beginRequest(start, Direction.UP, step, hand, core, true,
                                         tick, tool, offhand);
            preview = List.of();
            PacketDistributor.sendToServer(ConstructionWandPreviewRequestPacket.air(requestId, hand));
        }

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(new Quaternionf(event.getCamera().rotation()).invert());
        if (renderPreview(pose, buffer, event.getCamera())) {
            buffer.endBatch(RenderType.lines());
        }
        pose.popPose();
    }

    private static boolean renderPreview(PoseStack pose, MultiBufferSource buffers, Camera camera) {
        if (preview.isEmpty()) return false;

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vec3 cameraPos = camera.getPosition();
        pose.pushPose();
        for (BlockPos pos : preview) {
            AABB box = new AABB(pos).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(pose, lines, box, 0.25F, 1.0F, 0.35F, 0.9F);
        }
        pose.popPose();
        return true;
    }

    private static int beginRequest(BlockPos pos, Direction face, Direction step,
                                    InteractionHand hand, ConstructionWandCoreMode core,
                                    boolean air, long tick, ItemStack tool, ItemStack offhand) {
        int requestId = nextRequestId++;
        latestRequestId = requestId;
        requestedPos = pos.immutable();
        requestedFace = face;
        requestedStep = step;
        requestedHand = hand;
        requestedCore = core;
        requestedAir = air;
        requestedTool = tool.copy();
        requestedOffhand = offhand.copy();
        lastRequestTick = tick;
        return requestId;
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

    private static ConstructionWandCoreMode activeCore(Minecraft minecraft, InteractionHand hand) {
        return minecraft.player.getItemInHand(hand).getOrDefault(
                UComponents.ConstructionWandCoreComponent.get(), ConstructionWandCoreMode.DEFAULT);
    }

    private static void clear() {
        preview = List.of();
        requestedPos = null;
        requestedFace = null;
        requestedStep = null;
        requestedHand = null;
        requestedCore = null;
        requestedAir = false;
        requestedTool = ItemStack.EMPTY;
        requestedOffhand = ItemStack.EMPTY;
        lastRequestTick = Long.MIN_VALUE;
        latestRequestId = -1;
    }
}
