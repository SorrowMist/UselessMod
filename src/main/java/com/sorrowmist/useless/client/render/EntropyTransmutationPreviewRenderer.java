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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 熵变机械臂潜行右键 ME 箱子的「幽灵方块预览」与「转化瞬间反馈」。
 *
 * <p><b>预览</b>：手持熵变机械臂 + 潜行 + 准星命中 ME 箱子时，每 {@link #REFRESH_INTERVAL} tick
 * 向服务端发一次只读查询；服务端回答「这个箱子现在会被转化成什么方块」（不可转化则为空），
 * 客户端据此在该位置半透明渲染出目标方块。</p>
 *
 * <p>幽灵方块刻意比本体放大 {@link #PREVIEW_SCALE} 倍并套一圈发光轮廓，否则它会和 ME 箱子
 * 本体表面重叠、被盖得几乎看不出；放大后幽灵方块包住本体，边缘始终可见。</p>
 *
 * <p><b>反馈</b>：服务端转化成功时广播 {@code EntropyTransmutationEffectPacket}，
 * 客户端据此在方块上方展开一座旋转的太极八卦法阵（双圈光带 + 八组卦象 + 太极图），
 * 下方升起一道柔和的光柱，伴随一圈青蓝火花，随后收束淡出。</p>
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class EntropyTransmutationPreviewRenderer {
    /** 预览数据变化很慢，没必要每 tick 问服务端。 */
    private static final int REFRESH_INTERVAL = 5;
    private static final double MAX_RENDER_DISTANCE = 128.0;
    private static final long EFFECT_DURATION_TICKS = 40L;
    /** 粒子喷发只占动画的前这一比例：后段让法阵独自成为主角，免得火花飘上阵面盖住卦象。 */
    private static final float PARTICLE_BURST_DURATION = 0.5F;
    /**
     * 法阵光带与光柱的提交类型：{@code POSITION_COLOR} 四边形，半透明混合、不背面剔除、只写颜色。
     *
     * <p>刻意不用 {@link RenderType#lines()}：线宽在核心模式下基本被驱动忽略，无论 {@code lineWidth}
     * 设多大实际都只有 1px，远景里几乎看不见，这正是旧特效"不好看"的主因。换成有厚度的面片后，
     * 粗细由几何决定，与驱动无关，也才叠得出柔光边缘。</p>
     */
    private static final RenderType ARRAY_TYPE = RenderType.create(
            "useless_entropy_array",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            8192,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    /** 法阵平面悬浮在方块中心之上的高度。 */
    private static final float ARRAY_HEIGHT = 0.62F;
    /** 阵体外圈与内圈半径；两者之间是八组卦象所在的环带。 */
    private static final float ARRAY_OUTER_RADIUS = 1.0F;
    private static final float ARRAY_INNER_RADIUS = 0.62F;
    /** 圆环光带的径向厚度。 */
    private static final float RING_THICKNESS = 0.05F;
    /**
     * 卦象三爻由外向内排布：最外一条的半径与相邻两条的间距。
     *
     * <p>三爻都落在内外圈光带之间的环带上：最外一爻贴近外圈内侧，另两爻依次向内收，
     * 整组卦象居中偏外——既不压住外圈光带，也不缩到中心太极附近。</p>
     */
    private static final float TRIGRAM_OUTER_RADIUS = 0.90F;
    private static final float TRIGRAM_ROW_SPACING = 0.075F;
    /** 阳爻半长；阴爻从中断开，只画到距中心 {@link #TRIGRAM_GAP} 处。 */
    private static final float TRIGRAM_HALF_LENGTH = 0.12F;
    private static final float TRIGRAM_GAP = 0.05F;
    /** 爻线光条的宽度。 */
    private static final float TRIGRAM_BAR_WIDTH = 0.028F;
    /** 爻线也走「辉光底 + 亮芯」双层，和圆环同一套质感，不再像贴上去的实心胶带。 */
    private static final float TRIGRAM_HALO_WIDTH_SCALE = 2.2F;
    private static final float TRIGRAM_HALO_ALPHA_SCALE = 0.30F;
    /** 八组卦象的呼吸光浪：亮度在 1 与 1-{@link #TRIGRAM_PULSE_DEPTH} 之间起伏。 */
    private static final float TRIGRAM_PULSE_DEPTH = 0.45F;
    /** 整段动画期间光浪绕阵流动的圈数。 */
    private static final float TRIGRAM_PULSE_CYCLES = 2.0F;
    /** 同一卦内三爻的亮度落差：外爻最亮，每往内一爻暗这么多，让三爻分主次、有纵深。 */
    private static final float TRIGRAM_ROW_DIM = 0.14F;
    /** 相邻两卦的点亮延迟（占整段动画的比例），让八卦依次亮起而非同时浮现。 */
    private static final float TRIGRAM_STAGGER = 0.012F;
    /** 内外圈之间铺一层极淡的深蓝底衬，让卦象像刻在盘面上而非浮在空中。 */
    private static final float BAND_ALPHA_SCALE = 0.10F;
    /** 中心太极图的半径与圆环厚度。 */
    private static final float TAIJI_RADIUS = 0.34F;
    private static final float TAIJI_THICKNESS = 0.035F;
    /** 圆环 / 圆弧细分段数。 */
    private static final int RING_SEGMENTS = 96;
    private static final int ARC_SEGMENTS = 48;
    /** 竖直光柱的高度与半宽。 */
    private static final float PILLAR_HEIGHT = 1.7F;
    private static final float PILLAR_HALF_WIDTH = 0.12F;
    /**
     * 地面冲击波：转化那一刻从方块脚下向外扩出去的一圈光波。
     *
     * <p>占整段动画的前 {@link #SHOCKWAVE_DURATION}，扩到 {@link #SHOCKWAVE_MAX_RADIUS} 附近时
     * 正好淡尽；它给"转化发生了"这件事一个明确的落点与方向感。</p>
     */
    private static final float SHOCKWAVE_MIN_RADIUS = 0.45F;
    private static final float SHOCKWAVE_MAX_RADIUS = 1.9F;
    private static final float SHOCKWAVE_THICKNESS = 0.07F;
    private static final float SHOCKWAVE_DURATION = 0.45F;
    /** 辉光：核心近白青，光晕偏深蓝；光晕更宽更暗，叠出柔光边缘。 */
    private static final float CORE_R = 0.80F;
    private static final float CORE_G = 0.96F;
    private static final float CORE_B = 1.00F;
    private static final float HALO_R = 0.18F;
    private static final float HALO_G = 0.60F;
    private static final float HALO_B = 1.00F;
    private static final float HALO_WIDTH_SCALE = 2.8F;
    private static final float HALO_ALPHA_SCALE = 0.20F;
    /** 八卦：高位到低位依次对应最外到最内一爻，1 为阳爻、0 为阴爻。 */
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
    /** 全亮光照，便于在任何环境光下辨认幽灵方块。 */
    private static final int FULL_LIGHT = 0xF000F0;
    /** 幽灵方块整体 alpha；越高越实、越能压住本体方块的表面。 */
    private static final float PREVIEW_ALPHA = 0.85F;
    /** 相对原方块整体放大，让幽灵方块包住本体，避免被其表面盖住、深度打架。 */
    private static final float PREVIEW_SCALE = 1.05F;
    /** 当前应渲染的幽灵预览；{@code state} 为 null 表示不可转化。 */
    private static BlockPos previewPos;
    private static BlockState previewState;

    /** requestId 机制：只接受最近一次请求的应答，避免乱序应答覆盖新结果。 */
    private static int nextRequestId;
    private static int latestRequestId = -1;
    private static BlockPos requestedPos;
    private static long lastRequestTick = Long.MIN_VALUE;

    /** 转化瞬间反馈。 */
    private static BlockPos effectPos;
    private static long effectStartTick = Long.MIN_VALUE;

    private EntropyTransmutationPreviewRenderer() {}

    /** 服务端应答入口：{@code pos} / {@code state} 为空表示当前不可转化，清除预览。 */
    public static void setPreview(int requestId, BlockPos pos, BlockState state) {
        if (requestId != latestRequestId) return;
        previewPos = pos == null ? null : pos.immutable();
        previewState = state;
    }

    /** 转化成功瞬间反馈入口：记下起点，并立刻放一次爆发粒子与声响。 */
    public static void triggerEffect(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        effectPos = pos.immutable();
        effectStartTick = minecraft.level.getGameTime();

        Level level = minecraft.level;
        RandomSource random = level.random;
        Vec3 center = Vec3.atCenterOf(pos);

        // 中心一道闪光。
        level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0.0, 0.0, 0.0);

        // 一圈向四面八方炸开的电火花。
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double pitch = (random.nextDouble() - 0.5) * Math.PI;
            double speed = 0.15 + random.nextDouble() * 0.25;
            double vx = Math.cos(angle) * Math.cos(pitch) * speed;
            double vy = Math.sin(pitch) * speed + 0.1;
            double vz = Math.sin(angle) * Math.cos(pitch) * speed;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, vx, vy, vz);
        }

        // 一轮向上飘散的末影光点；只留少量，避免开局的白点糊住随后展开的法阵。
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * 0.5;
            level.addParticle(ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * radius,
                    center.y + (random.nextDouble() - 0.5),
                    center.z + Math.sin(angle) * radius,
                    Math.cos(angle) * 0.08, 0.12, Math.sin(angle) * 0.08);
        }

        // 声响：低沉的冲击 + 清脆的共鸣，双重叠加更有"仪式感"。
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 0.8F, 1.4F, false);
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0F, 1.2F, false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearRequest();
            return;
        }

        if (shouldRequest(minecraft)) {
            BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
            long tick = minecraft.level.getGameTime();
            if (requestedPos == null
                    || !requestedPos.equals(hit.getBlockPos())
                    || tick - lastRequestTick >= REFRESH_INTERVAL) {
                int requestId = nextRequestId++;
                latestRequestId = requestId;
                requestedPos = hit.getBlockPos().immutable();
                lastRequestTick = tick;
                PacketDistributor.sendToServer(
                        new EntropyTransmutationPreviewRequestPacket(requestId, hit.getBlockPos()));
            }
        } else {
            clearRequest();
        }

        // 转化反馈的粒子独立于预览：不持械/不潜行时也照常播放。
        emitEffectParticles(minecraft);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
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
        pose.mulPose(new Quaternionf(camera.rotation()).invert());

        renderEffect(pose, buffer, cameraPos, tick);

        pose.popPose();
    }

    /**
     * 准星命中的方块若正是预览目标，就地把幽灵方块画在它原本的位置，并取消原版高亮框。
     *
     * <p>刻意挂在 {@link RenderHighlightEvent.Block} 而不是 {@code AFTER_LEVEL}：这个阶段仍处在
     * 世界渲染流程内，深度、光照、投影与主帧缓冲都还是方块渲染用的那一套，方块模型才画得出来；
     * {@code AFTER_LEVEL} 只适合画线框，在那里渲染方块模型不会显示。本项目既有的
     * {@code ConstructionWandPreviewRenderer} 的实体方块预览也走同一事件。</p>
     */
    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (previewPos == null || previewState == null) {
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
        // 以方块中心为基准放大，让幽灵方块略微包住本体，避免被本体表面盖住。
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
        pose.translate(-0.5, -0.5, -0.5);

        // 半透明整体 alpha 靠 shader color 压低；提交要在恢复之前，否则乘数不生效。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, PREVIEW_ALPHA);
        minecraft.getBlockRenderer().renderSingleBlock(
                previewState, pose, buffer, FULL_LIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.translucent());
        buffer.endBatch(RenderType.translucent());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        pose.popPose();

        // 幽灵方块外侧再套一圈发光青蓝轮廓，保证在深色本体上依旧醒目。
        VertexConsumer outlineLines = buffer.getBuffer(RenderType.lines());
        AABB outline = new AABB(previewPos).inflate(0.5 * (PREVIEW_SCALE - 1.0F) + 0.004)
                .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        LevelRenderer.renderLineBox(pose, outlineLines, outline, 0.35F, 0.9F, 1.0F, 0.9F);
        buffer.endBatch(RenderType.lines());

        // 幽灵方块已盖在原位，原版高亮线框就不必再画。
        event.setCanceled(true);
    }

    /** 转化生效期间每 tick 持续喷涌粒子，形成一段能量喷发而非一闪而过。 */
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

        // 粒子只在动画前段喷发；过了这段就彻底停下，把视野让给法阵。
        float progress = (float) elapsed / EFFECT_DURATION_TICKS;
        float burst = 1.0F - Mth.clamp(progress / PARTICLE_BURST_DURATION, 0.0F, 1.0F);
        if (burst <= 0.0F) {
            return;
        }

        RandomSource random = level.random;
        Vec3 center = Vec3.atCenterOf(effectPos);

        // 火花数量随喷发强度递减，整体比旧版少一半左右。
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
            // 竖直速度压得很低，粒子只在方块附近散开，不会往上飘到法阵那一层。
            double vy = 0.02 + random.nextDouble() * 0.05;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, vx, vy, vz);
        }

        // 附魔符文更稀，且只出现在喷发的前半段。
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

    /**
     * 在方块上方展开一座旋转的太极八卦法阵：双圈光带、环带内八组卦象、中心一枚缓缓转动的太极图，
     * 方块中心同时升起一道柔和光柱。法阵先快速铺开，中段整体旋转并轻微起伏，末段收束淡出。
     */
    private static void renderEffect(PoseStack pose, MultiBufferSource.BufferSource buffer,
                                     Vec3 cameraPos, long tick) {
        if (effectPos == null || effectStartTick == Long.MIN_VALUE) {
            return;
        }
        long elapsed = tick - effectStartTick;
        if (elapsed < 0 || elapsed > EFFECT_DURATION_TICKS) {
            effectPos = null;
            return;
        }
        Vec3 center = Vec3.atCenterOf(effectPos);
        if (center.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        float progress = (float) elapsed / EFFECT_DURATION_TICKS;
        // 前 15% 快速铺开，后 40% 收束淡出，中段维持满亮。
        float appear = Mth.clamp(progress / 0.15F, 0.0F, 1.0F);
        float fade = 1.0F - Mth.clamp((progress - 0.60F) / 0.40F, 0.0F, 1.0F);
        float alpha = appear * fade;
        if (alpha <= 0.0F) {
            return;
        }
        // 缓入的旋转速度 + 轻微上下起伏，避免匀速旋转显得呆板。
        float spin = Mth.sin(progress * (float) Math.PI * 0.5F) * 140.0F;
        float rise = ARRAY_HEIGHT + Mth.sin(progress * (float) Math.PI) * 0.12F;

        VertexConsumer vc = buffer.getBuffer(ARRAY_TYPE);

        // 地面冲击波：贴着方块脚下向外扩开，最先出现也最先消失。
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y - 0.48 - cameraPos.y, center.z - cameraPos.z);
        drawShockwave(vc, pose.last().pose(), progress);
        pose.popPose();

        // 光柱：从方块中心拔地而起，越往上越淡。
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
        drawPillar(vc, pose.last().pose(), appear, alpha);
        pose.popPose();

        // 法阵：悬浮于方块之上，随进度旋转、起伏。
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y + rise - cameraPos.y, center.z - cameraPos.z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        drawArray(vc, pose.last().pose(), appear, alpha, progress);
        pose.popPose();

        buffer.endBatch(ARRAY_TYPE);
    }

    /**
     * 法阵本体：环带底衬 + 辉光底层 + 双圈核心光带 + 八组卦象 + 中心太极。
     *
     * <p>{@code progress} 是整段动画的进度（0~1），用于让八卦绕阵依次点亮、并有光浪流动。</p>
     */
    private static void drawArray(VertexConsumer vc, Matrix4f mat, float scale, float alpha,
                                  float progress) {
        // 环带底衬：内外圈之间铺一层极淡深蓝，让卦象像刻在盘面上而非浮在空中。
        ring(vc, mat, (ARRAY_OUTER_RADIUS + ARRAY_INNER_RADIUS) * 0.5F * scale,
                (ARRAY_OUTER_RADIUS - ARRAY_INNER_RADIUS) * scale,
                HALO_R, HALO_G, HALO_B, alpha * BAND_ALPHA_SCALE);

        // 辉光底层：内外圈各铺一层更宽更暗的深蓝圆环，叠出柔光边缘。
        float halo = alpha * HALO_ALPHA_SCALE;
        ring(vc, mat, ARRAY_OUTER_RADIUS * scale, RING_THICKNESS * HALO_WIDTH_SCALE,
                HALO_R, HALO_G, HALO_B, halo);
        ring(vc, mat, ARRAY_INNER_RADIUS * scale, RING_THICKNESS * HALO_WIDTH_SCALE,
                HALO_R, HALO_G, HALO_B, halo);

        // 核心：两道近白青的细圆环。
        ring(vc, mat, ARRAY_OUTER_RADIUS * scale, RING_THICKNESS, CORE_R, CORE_G, CORE_B, alpha);
        ring(vc, mat, ARRAY_INNER_RADIUS * scale, RING_THICKNESS,
                CORE_R, CORE_G, CORE_B, alpha * 0.85F);

        // 八组卦象：每组三爻沿半径由外向内排布，阳爻连贯、阴爻中分。
        // 逐卦错开点亮（TRIGRAM_STAGGER），并让一道光浪绕阵流动，使八卦有先后明灭的呼吸感。
        for (int i = 0; i < TRIGRAMS.length; i++) {
            float angle = (float) (i * Math.PI * 2.0 / TRIGRAMS.length);
            float phase = (float) i / TRIGRAMS.length;
            // 点亮进度：整段动画内按位次依次展开，最末一卦在动画过半前后亮起。
            float lit = Mth.clamp((progress - phase * TRIGRAM_STAGGER) / 0.18F, 0.0F, 1.0F);
            // 呼吸光浪：亮度绕阵流动，一圈 TRIGRAM_PULSE_CYCLES 次。
            float wave = Mth.sin((phase * TRIGRAM_PULSE_CYCLES - progress * TRIGRAM_PULSE_CYCLES)
                    * (float) (Math.PI * 2.0));
            float pulse = 1.0F - TRIGRAM_PULSE_DEPTH * (0.5F - 0.5F * wave);
            trigram(vc, mat, angle, TRIGRAMS[i], scale, alpha * lit * pulse);
        }

        taiji(vc, mat, scale, alpha);
    }

    /**
     * 地面冲击波：贴地一圈从方块脚下向外扩开的光环，占动画最前段，扩到最大半径前正好淡尽。
     *
     * <p>它给「转化就在此处发生」一个明确的落点与方向感，也让法阵的出现不再显得凭空冒出。</p>
     */
    private static void drawShockwave(VertexConsumer vc, Matrix4f mat, float progress) {
        float t = Mth.clamp(progress / SHOCKWAVE_DURATION, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }
        float radius = Mth.lerp(t, SHOCKWAVE_MIN_RADIUS, SHOCKWAVE_MAX_RADIUS);
        // 二次曲线淡出：起步亮、收尾快，符合冲击波迅速耗散的直觉。
        float waveAlpha = (1.0F - t) * (1.0F - t);
        ring(vc, mat, radius, SHOCKWAVE_THICKNESS * HALO_WIDTH_SCALE,
                HALO_R, HALO_G, HALO_B, waveAlpha * HALO_ALPHA_SCALE);
        ring(vc, mat, radius, SHOCKWAVE_THICKNESS, CORE_R, CORE_G, CORE_B, waveAlpha);
    }

    /** 在方块中心画一道竖直光柱：核心细柱 + 更宽更淡的光晕，两面交叉保证各角度可见。 */
    private static void drawPillar(VertexConsumer vc, Matrix4f mat, float scale, float alpha) {
        float height = PILLAR_HEIGHT * scale;
        if (height <= 0.0F) {
            return;
        }
        // 光晕在外，核心在内，由暗到亮叠出体积感。
        pillarQuad(vc, mat, PILLAR_HALF_WIDTH * HALO_WIDTH_SCALE, height,
                HALO_R, HALO_G, HALO_B, alpha * HALO_ALPHA_SCALE);
        pillarQuad(vc, mat, PILLAR_HALF_WIDTH, height, CORE_R, CORE_G, CORE_B, alpha * 0.9F);
    }

    /** 两片互相垂直的竖直四边形；顶边 alpha 归零，让光柱自然消散在空中。 */
    private static void pillarQuad(VertexConsumer vc, Matrix4f mat, float half, float height,
                                   float r, float g, float b, float alpha) {
        // XY 平面。
        vc.addVertex(mat, -half, 0.0F, 0.0F).setColor(r, g, b, alpha);
        vc.addVertex(mat, half, 0.0F, 0.0F).setColor(r, g, b, alpha);
        vc.addVertex(mat, half, height, 0.0F).setColor(r, g, b, 0.0F);
        vc.addVertex(mat, -half, height, 0.0F).setColor(r, g, b, 0.0F);
        // ZY 平面。
        vc.addVertex(mat, 0.0F, 0.0F, -half).setColor(r, g, b, alpha);
        vc.addVertex(mat, 0.0F, 0.0F, half).setColor(r, g, b, alpha);
        vc.addVertex(mat, 0.0F, height, half).setColor(r, g, b, 0.0F);
        vc.addVertex(mat, 0.0F, height, -half).setColor(r, g, b, 0.0F);
    }

    /** 以法阵中心为圆心的圆环，等价于 {@code cx = cz = 0}。 */
    private static void ring(VertexConsumer vc, Matrix4f mat, float radius, float thickness,
                             float r, float g, float b, float alpha) {
        ring(vc, mat, 0.0F, 0.0F, radius, thickness, r, g, b, alpha);
    }

    /** 在 XZ 平面上、以 ({@code cx}, {@code cz}) 为圆心画一整圈半径 {@code radius} 的圆环光带。 */
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

    /** 画一段圆弧光带，用于拼出太极图中的 S 形分界。 */
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

    /**
     * 画一组三爻卦象。{@code angle} 决定它落在环带上的方位，三爻沿径向由外向内排布，
     * 爻线本身沿切向铺开：阳爻为一整条，阴爻中分留出缺口。
     *
     * <p>三爻并非一样亮：最外一爻最亮，每往内一爻按 {@link #TRIGRAM_ROW_DIM} 暗一档，
     * 让卦象有主次与纵深，而不是三条等亮的横杠；爻线本身也走「深蓝底光 + 近白青亮芯」
     * 的双层，与圆环同一套质感。</p>
     */
    private static void trigram(VertexConsumer vc, Matrix4f mat, float angle, int bits,
                                float scale, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        // 切向单位向量，爻线沿它铺开。
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

    /**
     * 一条爻线（阳爻整条，或阴爻中分的半条）：沿切向从 {@code from} 铺到 {@code to}。
     * 先铺更宽更暗的深蓝底光，再叠近白青亮芯，边缘自然散开。
     */
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

    /** 中心太极图：外圆环 + S 形分界 + 阴阳两点。 */
    private static void taiji(VertexConsumer vc, Matrix4f mat, float scale, float alpha) {
        float radius = TAIJI_RADIUS * scale;
        if (radius <= 0.0F || alpha <= 0.0F) {
            return;
        }
        ring(vc, mat, radius, TAIJI_THICKNESS, CORE_R, CORE_G, CORE_B, alpha);

        // S 形分界由两条半径一半的半圆首尾相接而成。
        float half = radius * 0.5F;
        arc(vc, mat, 0.0F, -half, half,
                (float) (Math.PI * 0.5), (float) (Math.PI * 1.5), TAIJI_THICKNESS, alpha);
        arc(vc, mat, 0.0F, half, half,
                (float) (-Math.PI * 0.5), (float) (Math.PI * 0.5), TAIJI_THICKNESS, alpha);

        // 阴阳两点，分别落在 S 曲线上下两个半圆的圆心处。
        float dot = radius * 0.16F;
        ring(vc, mat, 0.0F, -half, dot, TAIJI_THICKNESS * 1.2F, CORE_R, CORE_G, CORE_B, alpha);
        ring(vc, mat, 0.0F, half, dot, TAIJI_THICKNESS * 1.2F, CORE_R, CORE_G, CORE_B, alpha);
    }

    /**
     * 在 XZ 平面上、以线段两端点为中心线，向两侧各扩 {@code halfWidth} 铺出一条光带。
     *
     * <p>用四边形代替线段，粗细才由几何决定；这是整套法阵能在远景里看清的基础。</p>
     */
    private static void ribbon(VertexConsumer vc, Matrix4f mat,
                               float x1, float z1, float x2, float z2,
                               float halfWidth, float r, float g, float b, float alpha) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        // 垂直方向的半边偏移。
        float nx = -dz / len * halfWidth;
        float nz = dx / len * halfWidth;
        vc.addVertex(mat, x1 + nx, 0.0F, z1 + nz).setColor(r, g, b, alpha);
        vc.addVertex(mat, x1 - nx, 0.0F, z1 - nz).setColor(r, g, b, alpha);
        vc.addVertex(mat, x2 - nx, 0.0F, z2 - nz).setColor(r, g, b, alpha);
        vc.addVertex(mat, x2 + nx, 0.0F, z2 + nz).setColor(r, g, b, alpha);
    }

    /** 只在「潜行 + 手持熵变机械臂 + 准星命中 ME 箱子」时才需要预览数据。 */
    private static boolean shouldRequest(Minecraft minecraft) {
        if (!minecraft.player.isShiftKeyDown()) {
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

    /** 清空请求去重状态，使下一次命中时当 tick 立即请求一次最新数据。 */
    private static void clearRequest() {
        requestedPos = null;
        lastRequestTick = Long.MIN_VALUE;
        latestRequestId = -1;
        previewPos = null;
        previewState = null;
    }
}
