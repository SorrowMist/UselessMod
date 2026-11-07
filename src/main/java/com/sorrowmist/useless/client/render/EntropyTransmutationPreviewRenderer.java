package com.sorrowmist.useless.client.render;

import appeng.api.implementations.blockentities.IMEChest;
import appeng.items.tools.powered.EntropyManipulatorItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.network.EntropyTransmutationPreviewRequestPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import org.joml.Matrix4f;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class EntropyTransmutationPreviewRenderer {
    private static final int REFRESH_INTERVAL = 5;
    private static final double MAX_RENDER_DISTANCE = 128.0;
    private static final long EFFECT_DURATION_TICKS = 40L;
    private static final float PARTICLE_BURST_DURATION = 0.5F;
    private static final RenderType ARRAY_TYPE = ArrayState.create();
    private static final class ArrayState extends RenderStateShard {
        private ArrayState() { super("useless_entropy_array", () -> {}, () -> {}); }
        static RenderType create() {
            return RenderType.create("useless_entropy_array", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 8192, false, true,
                    RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
    private static final float ARRAY_HEIGHT = 0.62F;
    private static final float ARRAY_OUTER_RADIUS = 1.0F;
    private static final float ARRAY_INNER_RADIUS = 0.62F;
    private static final float RING_THICKNESS = 0.05F;
    private static final float TRIGRAM_OUTER_RADIUS = 0.90F;
    private static final float TRIGRAM_ROW_SPACING = 0.075F;
    private static final float TRIGRAM_HALF_LENGTH = 0.12F;
    private static final float TRIGRAM_GAP = 0.05F;
    private static final float TRIGRAM_BAR_WIDTH = 0.028F;
    private static final float TRIGRAM_HALO_WIDTH_SCALE = 2.2F;
    private static final float TRIGRAM_HALO_ALPHA_SCALE = 0.30F;
    private static final float TRIGRAM_PULSE_DEPTH = 0.45F;
    private static final float TRIGRAM_PULSE_CYCLES = 2.0F;
    private static final float TRIGRAM_ROW_DIM = 0.14F;
    private static final float TRIGRAM_STAGGER = 0.012F;
    private static final float BAND_ALPHA_SCALE = 0.10F;
    private static final float TAIJI_RADIUS = 0.34F;
    private static final float TAIJI_THICKNESS = 0.035F;
    private static final int RING_SEGMENTS = 96;
    private static final int ARC_SEGMENTS = 48;
    private static final float PILLAR_HEIGHT = 1.7F;
    private static final float PILLAR_HALF_WIDTH = 0.12F;
    private static final float SHOCKWAVE_MIN_RADIUS = 0.45F;
    private static final float SHOCKWAVE_MAX_RADIUS = 1.9F;
    private static final float SHOCKWAVE_THICKNESS = 0.07F;
    private static final float SHOCKWAVE_DURATION = 0.45F;
    private static final float CORE_R = 0.80F;
    private static final float CORE_G = 0.96F;
    private static final float CORE_B = 1.00F;
    private static final float HALO_R = 0.18F;
    private static final float HALO_G = 0.60F;
    private static final float HALO_B = 1.00F;
    private static final float HALO_WIDTH_SCALE = 2.8F;
    private static final float HALO_ALPHA_SCALE = 0.20F;
    private static final int[] TRIGRAMS = {
            0b111, // 乾
            0b110, // 兑
            0b101, // 离
            0b100, // 震
            0b011, // 巽
            0b010, // 坎
            0b001, // 艮
            0b000, // 坤
    };
    private static final int FULL_LIGHT = 0xF000F0;
    private static final float PREVIEW_ALPHA = 0.85F;
    private static final float PREVIEW_SCALE = 1.05F;
    private static BlockPos previewPos;
    private static BlockState previewState;

    private static ClientLevel lastLevel;
    private static long lastResponseTick = Long.MIN_VALUE;
    private static int nextRequestId;
    private static int latestRequestId = -1;
    private static BlockPos requestedPos;
    private static long lastRequestTick = Long.MIN_VALUE;

    private static BlockPos effectPos;
    private static long effectStartTick = Long.MIN_VALUE;

    private EntropyTransmutationPreviewRenderer() {}

    public static void setPreview(int requestId, ResourceLocation dimension, BlockPos pos, BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != lastLevel || requestId != latestRequestId
                || !minecraft.level.dimension().location().equals(dimension)
                || requestedPos == null || !requestedPos.equals(pos) || !shouldRequest(minecraft)) return;
        previewPos = state == null || state.isAir() ? null : pos.immutable();
        previewState = state;
        lastResponseTick = minecraft.level.getGameTime();
    }

    public static void triggerEffect(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        clearRequest();
        effectPos = pos.immutable();
        effectStartTick = minecraft.level.getGameTime();

        Level level = minecraft.level;
        RandomSource random = level.random;
        Vec3 center = Vec3.atCenterOf(pos);
        level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0.0, 0.0, 0.0);
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double pitch = (random.nextDouble() - 0.5) * Math.PI;
            double speed = 0.15 + random.nextDouble() * 0.25;
            double vx = Math.cos(angle) * Math.cos(pitch) * speed;
            double vy = Math.sin(pitch) * speed + 0.1;
            double vz = Math.sin(angle) * Math.cos(pitch) * speed;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, vx, vy, vz);
        }
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * 0.5;
            level.addParticle(ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * radius,
                    center.y + (random.nextDouble() - 0.5),
                    center.z + Math.sin(angle) * radius,
                    Math.cos(angle) * 0.08, 0.12, Math.sin(angle) * 0.08);
        }
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 0.8F, 1.4F, false);
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0F, 1.2F, false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel) {
            clearRequest();
            effectPos = null;
            effectStartTick = Long.MIN_VALUE;
            lastLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null) {
            clearRequest();
            return;
        }
        if (minecraft.isPaused()) return;
        if (lastResponseTick != Long.MIN_VALUE && minecraft.level.getGameTime() - lastResponseTick > 15) {
            previewPos = null;
            previewState = null;
        }

        if (shouldRequest(minecraft)) {
            BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
            long tick = minecraft.level.getGameTime();
            if (requestedPos == null
                    || !requestedPos.equals(hit.getBlockPos())
                    || tick - lastRequestTick >= REFRESH_INTERVAL) {
                if (!hit.getBlockPos().equals(requestedPos)) {
                    previewPos = null;
                    previewState = null;
                }
                int requestId = nextRequestId++;
                latestRequestId = requestId;
                requestedPos = hit.getBlockPos().immutable();
                lastRequestTick = tick;
                PacketDistributor.sendToServer(
                        new EntropyTransmutationPreviewRequestPacket(requestId, minecraft.level.dimension().location(), hit.getBlockPos()));
            }
        } else {
            clearRequest();
        }
        emitEffectParticles(minecraft);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        long tick = minecraft.level.getGameTime();

        pose.pushPose();

        renderEffect(pose, buffer, cameraPos, tick + event.getPartialTick());

        pose.popPose();
    }

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (previewPos == null || previewState == null || !shouldRequest(Minecraft.getInstance())) {
            return;
        }
        if (!event.getTarget().getBlockPos().equals(previewPos)) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        if (Vec3.atCenterOf(previewPos).distanceToSqr(cameraPos)
                > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(previewPos.getX() - cameraPos.x,
                       previewPos.getY() - cameraPos.y,
                       previewPos.getZ() - cameraPos.z);
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
        pose.translate(-0.5, -0.5, -0.5);
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, PREVIEW_ALPHA);
            minecraft.getBlockRenderer().renderSingleBlock(previewState, pose, buffer, FULL_LIGHT,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
            buffer.endBatch(RenderType.translucent());
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
        VertexConsumer outlineLines = buffer.getBuffer(RenderType.lines());
        AABB outline = new AABB(previewPos).inflate(0.5 * (PREVIEW_SCALE - 1.0F) + 0.004)
                .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        LevelRenderer.renderLineBox(pose, outlineLines, outline, 0.35F, 0.9F, 1.0F, 0.9F);
        buffer.endBatch(RenderType.lines());
        event.setCanceled(true);
    }

    private static void emitEffectParticles(Minecraft minecraft) {
        if (effectPos == null || effectStartTick == Long.MIN_VALUE) {
            return;
        }
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        long elapsed = level.getGameTime() - effectStartTick;
        if (elapsed < 0 || elapsed > EFFECT_DURATION_TICKS) {
            effectPos = null;
            return;
        }
        float progress = (float) elapsed / EFFECT_DURATION_TICKS;
        float burst = 1.0F - Mth.clamp(progress / PARTICLE_BURST_DURATION, 0.0F, 1.0F);
        if (burst <= 0.0F) {
            return;
        }

        RandomSource random = level.random;
        Vec3 center = Vec3.atCenterOf(effectPos);
        int count = Mth.ceil(burst * 4.0F);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 0.15 + random.nextDouble() * 0.30;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = center.y + (random.nextDouble() - 0.3) * 0.6;
            double speed = 0.05 + random.nextDouble() * 0.08;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.02 + random.nextDouble() * 0.05;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, vx, vy, vz);
        }
        if (burst > 0.5F && elapsed % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = 0.3 + random.nextDouble() * 0.35;
                level.addParticle(ParticleTypes.ENCHANT,
                        center.x + Math.cos(angle) * radius,
                        center.y + 0.2 + random.nextDouble() * 0.4,
                        center.z + Math.sin(angle) * radius,
                        0.0, 0.08, 0.0);
            }
        }
    }

    private static void renderEffect(PoseStack pose, MultiBufferSource.BufferSource buffer,
                                     Vec3 cameraPos, double tick) {
        if (effectPos == null || effectStartTick == Long.MIN_VALUE) {
            return;
        }
        double elapsed = tick - effectStartTick;
        if (elapsed < 0 || elapsed > EFFECT_DURATION_TICKS) {
            effectPos = null;
            return;
        }
        Vec3 center = Vec3.atCenterOf(effectPos);
        if (center.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        float progress = (float) elapsed / EFFECT_DURATION_TICKS;
        float appear = Mth.clamp(progress / 0.15F, 0.0F, 1.0F);
        float fade = 1.0F - Mth.clamp((progress - 0.60F) / 0.40F, 0.0F, 1.0F);
        float alpha = appear * fade;
        if (alpha <= 0.0F) {
            return;
        }
        float spin = Mth.sin(progress * (float) Math.PI * 0.5F) * 140.0F;
        float rise = ARRAY_HEIGHT + Mth.sin(progress * (float) Math.PI) * 0.12F;

        VertexConsumer vc = buffer.getBuffer(ARRAY_TYPE);
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y - 0.48 - cameraPos.y, center.z - cameraPos.z);
        drawShockwave(vc, pose.last().pose(), progress);
        pose.popPose();
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
        drawPillar(vc, pose.last().pose(), appear, alpha);
        pose.popPose();
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y + rise - cameraPos.y, center.z - cameraPos.z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        drawArray(vc, pose.last().pose(), appear, alpha, progress);
        pose.popPose();

        buffer.endBatch(ARRAY_TYPE);
    }

    private static void drawArray(VertexConsumer vc, Matrix4f mat, float scale, float alpha,
                                  float progress) {
        ring(vc, mat, (ARRAY_OUTER_RADIUS + ARRAY_INNER_RADIUS) * 0.5F * scale,
                (ARRAY_OUTER_RADIUS - ARRAY_INNER_RADIUS) * scale,
                HALO_R, HALO_G, HALO_B, alpha * BAND_ALPHA_SCALE);
        float halo = alpha * HALO_ALPHA_SCALE;
        ring(vc, mat, ARRAY_OUTER_RADIUS * scale, RING_THICKNESS * HALO_WIDTH_SCALE,
                HALO_R, HALO_G, HALO_B, halo);
        ring(vc, mat, ARRAY_INNER_RADIUS * scale, RING_THICKNESS * HALO_WIDTH_SCALE,
                HALO_R, HALO_G, HALO_B, halo);
        ring(vc, mat, ARRAY_OUTER_RADIUS * scale, RING_THICKNESS, CORE_R, CORE_G, CORE_B, alpha);
        ring(vc, mat, ARRAY_INNER_RADIUS * scale, RING_THICKNESS,
                CORE_R, CORE_G, CORE_B, alpha * 0.85F);
        for (int i = 0; i < TRIGRAMS.length; i++) {
            float angle = (float) (i * Math.PI * 2.0 / TRIGRAMS.length);
            float phase = (float) i / TRIGRAMS.length;
            float lit = Mth.clamp((progress - phase * TRIGRAM_STAGGER) / 0.18F, 0.0F, 1.0F);
            float wave = Mth.sin((phase * TRIGRAM_PULSE_CYCLES - progress * TRIGRAM_PULSE_CYCLES)
                    * (float) (Math.PI * 2.0));
            float pulse = 1.0F - TRIGRAM_PULSE_DEPTH * (0.5F - 0.5F * wave);
            trigram(vc, mat, angle, TRIGRAMS[i], scale, alpha * lit * pulse);
        }

        taiji(vc, mat, scale, alpha);
    }

    private static void drawShockwave(VertexConsumer vc, Matrix4f mat, float progress) {
        float t = Mth.clamp(progress / SHOCKWAVE_DURATION, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }
        float radius = Mth.lerp(t, SHOCKWAVE_MIN_RADIUS, SHOCKWAVE_MAX_RADIUS);
        float waveAlpha = (1.0F - t) * (1.0F - t);
        ring(vc, mat, radius, SHOCKWAVE_THICKNESS * HALO_WIDTH_SCALE,
                HALO_R, HALO_G, HALO_B, waveAlpha * HALO_ALPHA_SCALE);
        ring(vc, mat, radius, SHOCKWAVE_THICKNESS, CORE_R, CORE_G, CORE_B, waveAlpha);
    }

    private static void drawPillar(VertexConsumer vc, Matrix4f mat, float scale, float alpha) {
        float height = PILLAR_HEIGHT * scale;
        if (height <= 0.0F) {
            return;
        }
        pillarQuad(vc, mat, PILLAR_HALF_WIDTH * HALO_WIDTH_SCALE, height,
                HALO_R, HALO_G, HALO_B, alpha * HALO_ALPHA_SCALE);
        pillarQuad(vc, mat, PILLAR_HALF_WIDTH, height, CORE_R, CORE_G, CORE_B, alpha * 0.9F);
    }

    private static void pillarQuad(VertexConsumer vc, Matrix4f mat, float half, float height,
                                   float r, float g, float b, float alpha) {
        vc.vertex(mat, -half, 0.0F, 0.0F).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, half, 0.0F, 0.0F).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, half, height, 0.0F).color(r, g, b, 0.0F).endVertex();
        vc.vertex(mat, -half, height, 0.0F).color(r, g, b, 0.0F).endVertex();
        vc.vertex(mat, 0.0F, 0.0F, -half).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, 0.0F, 0.0F, half).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, 0.0F, height, half).color(r, g, b, 0.0F).endVertex();
        vc.vertex(mat, 0.0F, height, -half).color(r, g, b, 0.0F).endVertex();
    }

    private static void ring(VertexConsumer vc, Matrix4f mat, float radius, float thickness,
                             float r, float g, float b, float alpha) {
        ring(vc, mat, 0.0F, 0.0F, radius, thickness, r, g, b, alpha);
    }

    private static void ring(VertexConsumer vc, Matrix4f mat, float cx, float cz,
                             float radius, float thickness,
                             float r, float g, float b, float alpha) {
        if (radius <= 0.0F || thickness <= 0.0F || alpha <= 0.0F) {
            return;
        }
        float half = thickness * 0.5F;
        float step = (float) (Math.PI * 2.0 / RING_SEGMENTS);
        float px = cx + radius;
        float pz = cz;
        for (int i = 1; i <= RING_SEGMENTS; i++) {
            float angle = step * i;
            float x = cx + Mth.cos(angle) * radius;
            float z = cz + Mth.sin(angle) * radius;
            ribbon(vc, mat, px, pz, x, z, half, r, g, b, alpha);
            px = x;
            pz = z;
        }
    }

    private static void arc(VertexConsumer vc, Matrix4f mat, float cx, float cz, float radius,
                            float startAngle, float endAngle, float thickness, float alpha) {
        if (radius <= 0.0F || thickness <= 0.0F || alpha <= 0.0F) {
            return;
        }
        float half = thickness * 0.5F;
        float step = (endAngle - startAngle) / ARC_SEGMENTS;
        float px = cx + Mth.cos(startAngle) * radius;
        float pz = cz + Mth.sin(startAngle) * radius;
        for (int i = 1; i <= ARC_SEGMENTS; i++) {
            float angle = startAngle + step * i;
            float x = cx + Mth.cos(angle) * radius;
            float z = cz + Mth.sin(angle) * radius;
            ribbon(vc, mat, px, pz, x, z, half, CORE_R, CORE_G, CORE_B, alpha);
            px = x;
            pz = z;
        }
    }

    private static void trigram(VertexConsumer vc, Matrix4f mat, float angle, int bits,
                                float scale, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float tx = -sin;
        float tz = cos;
        float half = TRIGRAM_HALF_LENGTH * scale;
        float coreHalf = TRIGRAM_BAR_WIDTH * 0.5F;
        float haloHalf = coreHalf * TRIGRAM_HALO_WIDTH_SCALE;
        for (int row = 0; row < 3; row++) {
            float radius = (TRIGRAM_OUTER_RADIUS - row * TRIGRAM_ROW_SPACING) * scale;
            if (radius <= 0.0F) {
                continue;
            }
            float rowAlpha = alpha * (1.0F - row * TRIGRAM_ROW_DIM);
            if (rowAlpha <= 0.0F) {
                continue;
            }
            float cx = cos * radius;
            float cz = sin * radius;
            if (((bits >> (2 - row)) & 1) == 1) {
                trigramBar(vc, mat, cx, cz, tx, tz, -half, half, coreHalf, haloHalf, rowAlpha);
            } else {
                float gap = TRIGRAM_GAP * scale;
                trigramBar(vc, mat, cx, cz, tx, tz, -half, -gap, coreHalf, haloHalf, rowAlpha);
                trigramBar(vc, mat, cx, cz, tx, tz, gap, half, coreHalf, haloHalf, rowAlpha);
            }
        }
    }

    private static void trigramBar(VertexConsumer vc, Matrix4f mat, float cx, float cz,
                                   float tx, float tz, float from, float to,
                                   float coreHalf, float haloHalf, float alpha) {
        ribbon(vc, mat,
                cx + tx * from, cz + tz * from,
                cx + tx * to, cz + tz * to,
                haloHalf, HALO_R, HALO_G, HALO_B, alpha * TRIGRAM_HALO_ALPHA_SCALE);
        ribbon(vc, mat,
                cx + tx * from, cz + tz * from,
                cx + tx * to, cz + tz * to,
                coreHalf, CORE_R, CORE_G, CORE_B, alpha);
    }

    private static void taiji(VertexConsumer vc, Matrix4f mat, float scale, float alpha) {
        float radius = TAIJI_RADIUS * scale;
        if (radius <= 0.0F || alpha <= 0.0F) {
            return;
        }
        ring(vc, mat, radius, TAIJI_THICKNESS, CORE_R, CORE_G, CORE_B, alpha);
        float half = radius * 0.5F;
        arc(vc, mat, 0.0F, -half, half,
                (float) (Math.PI * 0.5), (float) (Math.PI * 1.5), TAIJI_THICKNESS, alpha);
        arc(vc, mat, 0.0F, half, half,
                (float) (-Math.PI * 0.5), (float) (Math.PI * 0.5), TAIJI_THICKNESS, alpha);
        float dot = radius * 0.16F;
        ring(vc, mat, 0.0F, -half, dot, TAIJI_THICKNESS * 1.2F, CORE_R, CORE_G, CORE_B, alpha);
        ring(vc, mat, 0.0F, half, dot, TAIJI_THICKNESS * 1.2F, CORE_R, CORE_G, CORE_B, alpha);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat,
                               float x1, float z1, float x2, float z2,
                               float halfWidth, float r, float g, float b, float alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        float nx = -dz / len * halfWidth;
        float nz = dx / len * halfWidth;
        vc.vertex(mat, x1 + nx, 0.0F, z1 + nz).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, x1 - nx, 0.0F, z1 - nz).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, x2 - nx, 0.0F, z2 - nz).color(r, g, b, alpha).endVertex();
        vc.vertex(mat, x2 + nx, 0.0F, z2 + nz).color(r, g, b, alpha).endVertex();
    }

    private static boolean shouldRequest(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.isShiftKeyDown()) {
            return false;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof IMEChest)) {
            return false;
        }
        return holdsEntropyManipulator(minecraft);
    }

    private static boolean holdsEntropyManipulator(Minecraft minecraft) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof EntropyManipulatorItem) {
                return true;
            }
        }
        return false;
    }

    private static void clearRequest() {
        requestedPos = null;
        lastRequestTick = Long.MIN_VALUE;
        latestRequestId = -1;
        lastResponseTick = Long.MIN_VALUE;
        previewPos = null;
        previewState = null;
    }
}
