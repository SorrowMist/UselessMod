package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 万象合金炉未成型时，把「当前该修的那一个错误方块」画成红色线框，常亮不闪。
 *
 * <p>数据来自服务端：核心每次结构重验（结构变化时立即、否则每 40 tick）都会挑出
 * 从下往上遇到的第一个错误方块发过来，客户端只维护「核心 → 该高亮哪个方块」。
 * 因此框永远只有一个，修好一处就自动跳到下一处；成型或核心消失时剔除。</p>
 *
 * <p>渲染路径照 {@link StaffLinkHighlightRenderer}：{@code AFTER_LEVEL} 阶段拿到的 pose stack
 * 是 identity，modelview 上的相机旋转已经被弹掉，所以这里要自己补一个反向相机旋转，
 * 并把方块坐标减掉相机位置。</p>
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class AlloyFurnaceErrorHighlightRenderer {
    /** 未成型提示只在玩家附近才有意义，太远裁掉。 */
    private static final double MAX_RENDER_DISTANCE = 128.0;

    /** 错误提示用醒目红色。 */
    private static final float ERROR_RED = 1.00F;
    private static final float ERROR_GREEN = 0.15F;
    private static final float ERROR_BLUE = 0.15F;
    private static final float LINE_ALPHA = 0.95F;

    /** 核心坐标 → 当前该高亮的错误方块。 */
    private static final Map<BlockPos, BlockPos> errorsByCore = new HashMap<>();
    @Nullable
    private static ResourceKey<Level> dimension;

    private AlloyFurnaceErrorHighlightRenderer() {
    }

    /** 客户端收到服务端的「最低错误」更新；{@code errorPos} 为 null 表示该核心已无可高亮错误。 */
    public static void setError(BlockPos corePos, @Nullable BlockPos errorPos) {
        BlockPos core = corePos.immutable();
        if (errorPos == null) {
            errorsByCore.remove(core);
        } else {
            errorsByCore.put(core, errorPos.immutable());
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || errorsByCore.isEmpty()) {
            return;
        }

        // 缓存以核心坐标为键，只在同一维度内有效；换维度就整体作废。
        ResourceKey<Level> current = minecraft.level.dimension();
        if (dimension == null) {
            dimension = current;
        } else if (!dimension.equals(current)) {
            errorsByCore.clear();
            dimension = current;
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        pose.pushPose();
        pose.mulPose(new Quaternionf(camera.rotation()).invert());
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        Iterator<Map.Entry<BlockPos, BlockPos>> iterator = errorsByCore.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockPos> entry = iterator.next();
            BlockPos core = entry.getKey();
            // 核心方块实体没了（被拆）就剔除；服务端不会专门发清除包。
            if (!(minecraft.level.getBlockEntity(core) instanceof MultiblockAlloyFurnaceCoreBlockEntity)) {
                iterator.remove();
                continue;
            }
            // 已成型：靠 FORMED 方块状态同步剔除，不必等服务端再发一次。
            BlockState state = minecraft.level.getBlockState(core);
            if (!state.hasProperty(MultiblockAlloyFurnaceCoreBlock.FORMED)
                    || state.getValue(MultiblockAlloyFurnaceCoreBlock.FORMED)) {
                iterator.remove();
                continue;
            }
            BlockPos error = entry.getValue();
            if (Vec3.atCenterOf(error).distanceToSqr(cameraPos)
                    > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }
            AABB box = new AABB(error).inflate(0.0025)
                    .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(pose, lines, box, ERROR_RED, ERROR_GREEN, ERROR_BLUE, LINE_ALPHA);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }
}
