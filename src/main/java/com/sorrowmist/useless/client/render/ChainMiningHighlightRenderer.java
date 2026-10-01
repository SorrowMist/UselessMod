package com.sorrowmist.useless.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 按住连锁键期间，把本次连锁将波及的全部方块描上边框。
 *
 * <p>数据直接取自客户端缓存的 {@link PlayerMiningData#getCachedBlocks()}：该列表由服务端在准星
 * 命中新方块时用与破坏阶段完全相同的扫描流程算出并下发，因此预览范围与实际破坏范围天然一致，
 * 不需要额外的请求包或二次预测。
 *
 * <p>渲染位置与相机变换照 {@link StaffLinkHighlightRenderer} 与 {@link AeLinkHighlightRenderer}：
 * {@code AFTER_LEVEL} 阶段事件传入的 pose stack 为 identity（事件内部退化成 identity），
 * modelview 上的相机旋转在 {@code LevelRenderer.renderLevel} 结束时已被弹掉，故此处必须自行
 * 补上反向相机旋转，并把方块坐标减去相机位置。
 *
 * <p>相邻两个方块的边框在公共面上完全重合。若双方都提交该面，同一批顶点内该处的深度值相同，
 * 显示结果由浮点精度而非绘制顺序决定，原点边框会被相邻方块的冷色边框覆盖或闪烁。
 * 因此非原点方块一律跳过与其它待破坏方块共享的面，每个面至多被提交一次；
 * 原点保留完整边框，使玩家可明确辨认扫描起点。
 *
 * <p>连锁上限可达数十万格，因此候选方块在提交顶点前依次经过三重剔除：距离、视锥与面朝向。
 * 距离与视锥以方块为单位，面朝向以暴露面为单位。三者叠加后，顶点数只与玩家当前可见的轮廓
 * 规模相关，与连锁方块总数无关。
 *
 * <p>该渲染器每帧执行一次，热路径避免逐方块与逐顶点的临时对象分配：距离判定使用标量运算，
 * 顶点提交复用单个 {@link Vector3f}，坐标集合跨帧复用同一实例。
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class ChainMiningHighlightRenderer {
    /** 连锁原点用暖色，与其余待破坏方块区分，便于判断扫描起点。 */
    private static final float ORIGIN_RED = 1.0F;
    private static final float ORIGIN_GREEN = 0.78F;
    private static final float ORIGIN_BLUE = 0.25F;

    /** 其余待破坏方块用冷色。 */
    private static final float EDGE_RED = 0.35F;
    private static final float EDGE_GREEN = 0.85F;
    private static final float EDGE_BLUE = 1.0F;

    /** 边框相对方块表面的外扩量，用于避免线框与方块表面共面产生深度冲突。 */
    private static final double EDGE_INFLATE = 0.0025;

    /**
     * 超出该距离的方块不绘制。
     *
     * <p>视锥只约束视野角度，不约束距离：远处方块仍可能落在视锥内。缺少该判定时，
     * 连锁上限较高会让视锥内堆积大量不可辨认的密集线框，既无信息量又增加顶点开销。
     */
    private static final double MAX_RENDER_DISTANCE = 128.0;

    /** 视距平方，避免逐方块重复开方。 */
    private static final double MAX_RENDER_DISTANCE_SQR = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    /** 单帧绘制上限，用于兜底保护；默认配置上限为 1000，正常情形不会触及。 */
    private static final int MAX_RENDERED_BLOCKS = 4096;

    /**
     * 本帧参与绘制的方块坐标集合，跨帧复用。
     *
     * <p>渲染发生在客户端渲染线程，单帧内不存在并发访问；复用同一实例可避免每帧按方块
     * 数量重新分配哈希表。
     */
    private static final LongOpenHashSet RENDERED_POSITIONS = new LongOpenHashSet(1024);

    /** 顶点提交的坐标暂存，跨顶点复用，避免每顶点分配。 */
    private static final Vector3f VERTEX_SCRATCH = new Vector3f();

    private ChainMiningHighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            return;
        }

        // 仅按住连锁键时显示：缓存会在松开按键后保留，若不加该判定，高亮会常驻在屏幕上。
        if (!KeyBindings.isChainMiningKeyDown()) {
            return;
        }
        if (!holdsBeafTool(player)) {
            return;
        }

        PlayerMiningData data = MiningDispatcher.getPlayerData(player);
        if (data == null) {
            return;
        }
        List<BlockPos> blocks = data.getCachedBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        // 视锥取自本帧渲染流程，已按当前相机完成计算；为空时退化为不做视锥剔除。
        Frustum frustum = minecraft.levelRenderer.getFrustum();

        pose.pushPose();
        pose.mulPose(new Quaternionf(camera.rotation()).invert());
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        BlockPos origin = data.getCachedPos();

        // 服务端下发的缓存按到原点的距离升序排列，超距方块因此集中在列表尾部。以
        // 「相机到原点距离 + 渲染距离」为界即可安全终止遍历：列表有序时，更远的方块到相机
        // 的距离必然超出渲染距离。形状布局未经排序，故在遍历中同步检测有序性，
        // 一旦发现逆序立即放弃提前终止，退化为完整遍历。
        double breakDistanceSqr = 0.0;
        if (origin != null) {
            double reach = Math.sqrt(distanceToSqr(origin, cameraPos)) + MAX_RENDER_DISTANCE;
            breakDistanceSqr = reach * reach;
        }

        // 先筛出本帧实际绘制的方块。共享面判定必须基于同一集合：若某个邻块因距离、视锥
        // 或数量上限未被绘制，而本方块仍跳过朝向它的面，外轮廓上会留下缺口。
        List<BlockPos> rendered = new ArrayList<>(Math.min(blocks.size(), MAX_RENDERED_BLOCKS));
        boolean ordered = origin != null;
        double previousOriginDistanceSqr = -1.0;
        for (BlockPos pos : blocks) {
            if (rendered.size() >= MAX_RENDERED_BLOCKS) {
                break;
            }
            if (ordered) {
                double originDistanceSqr = distanceToSqr(pos, origin);
                if (originDistanceSqr < previousOriginDistanceSqr) {
                    ordered = false;
                } else if (originDistanceSqr > breakDistanceSqr) {
                    break;
                }
                previousOriginDistanceSqr = originDistanceSqr;
            }
            if (pos.equals(origin)) {
                continue;
            }
            if (distanceToSqr(pos, cameraPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(new AABB(pos))) {
                continue;
            }
            rendered.add(pos);
        }

        boolean originVisible = origin != null
                && distanceToSqr(origin, cameraPos) <= MAX_RENDER_DISTANCE_SQR;

        LongOpenHashSet positions = RENDERED_POSITIONS;
        positions.clear();
        for (BlockPos pos : rendered) {
            positions.add(pos.asLong());
        }
        if (originVisible) {
            positions.add(origin.asLong());
        }

        Matrix4f matrix = pose.last().pose();
        for (BlockPos pos : rendered) {
            drawBoxSkippingSharedFaces(matrix, lines, pos, cameraPos, positions);
        }

        // 原点绘制完整边框。它与相邻方块之间的公共面已由相邻方块跳过，此处不会再有重叠。
        if (originVisible) {
            AABB box = new AABB(origin).inflate(EDGE_INFLATE).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(pose, lines, box,
                    ORIGIN_RED, ORIGIN_GREEN, ORIGIN_BLUE, 0.95F);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }

    /**
     * 计算方块中心到相机的距离平方。
     *
     * <p>使用标量运算而非 {@link Vec3#atCenterOf}，避免逐方块的临时对象分配：
     * 该循环每帧遍历整个缓存列表，连锁上限较高时分配量会随方块数线性增长。
     */
    private static double distanceToSqr(BlockPos pos, Vec3 cameraPos) {
        double dx = pos.getX() + 0.5 - cameraPos.x;
        double dy = pos.getY() + 0.5 - cameraPos.y;
        double dz = pos.getZ() + 0.5 - cameraPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 计算两个方块中心之间的距离平方。
     *
     * <p>用于判断缓存列表是否仍按到原点的距离升序排列：该判定只需比较大小关系，
     * 因此省去开方。
     */
    private static double distanceToSqr(BlockPos pos, BlockPos other) {
        double dx = pos.getX() - other.getX();
        double dy = pos.getY() - other.getY();
        double dz = pos.getZ() - other.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 绘制方框，但跳过与相邻待破坏方块共享的面，以及背对相机的面。
     *
     * <p>共享面在相邻两个方框上完全重合，该面的四条棱若由双方各提交一次，深度值相同，
     * 最终显示结果由浮点精度而非绘制顺序决定，表现为棱线闪烁或颜色相互覆盖。跳过公共面后，
     * 每个面至多被提交一次，被完全包围的内部方块也不再产生任何顶点。
     *
     * <p>面朝向剔除针对暴露面：外法线背对相机的面位于方块背面，被方块本体遮挡，
     * 提交其棱线不会增加可见信息，只会增加顶点开销。
     *
     * @param positions 本帧参与绘制的全部方块坐标，用于判断某个面是否与邻块共享
     */
    private static void drawBoxSkippingSharedFaces(Matrix4f matrix, VertexConsumer lines, BlockPos pos,
                                                   Vec3 cameraPos, LongOpenHashSet positions) {
        for (Direction face : Direction.values()) {
            long neighbour = BlockPos.asLong(
                    pos.getX() + face.getStepX(),
                    pos.getY() + face.getStepY(),
                    pos.getZ() + face.getStepZ());
            if (positions.contains(neighbour)) {
                continue;
            }
            if (!isFaceVisible(pos, face, cameraPos)) {
                continue;
            }
            drawFaceOutline(matrix, lines, pos, cameraPos, face);
        }
    }

    /**
     * 判断某个面是否朝向相机。
     *
     * <p>取面中心指向相机的方向与该面外法线做点积，结果为正表示相机位于该面正面。
     * 面中心为方块中心沿法线偏移半格，因此该判定等价于相机是否位于该面所在平面之外。
     *
     * @param pos       方块坐标
     * @param face      待判定的面
     * @param cameraPos 相机位置
     * @return 该面朝向相机返回 true
     */
    private static boolean isFaceVisible(BlockPos pos, Direction face, Vec3 cameraPos) {
        double centerX = pos.getX() + 0.5 + face.getStepX() * 0.5;
        double centerY = pos.getY() + 0.5 + face.getStepY() * 0.5;
        double centerZ = pos.getZ() + 0.5 + face.getStepZ() * 0.5;
        double dx = cameraPos.x - centerX;
        double dy = cameraPos.y - centerY;
        double dz = cameraPos.z - centerZ;
        return dx * face.getStepX() + dy * face.getStepY() + dz * face.getStepZ() > 0.0;
    }

    /**
     * 提交单个面的四条棱。
     *
     * <p>坐标按 {@link #EDGE_INFLATE} 外扩，与原点框使用同一几何，使两类边框在公共棱上完全对齐。
     * 顶点逐个内联提交，不构造中间数组：每帧面数可达数万，临时数组会显著增加垃圾回收压力。
     */
    private static void drawFaceOutline(Matrix4f matrix, VertexConsumer lines, BlockPos pos, Vec3 cameraPos,
                                        Direction face) {
        double x1 = pos.getX() - EDGE_INFLATE;
        double y1 = pos.getY() - EDGE_INFLATE;
        double z1 = pos.getZ() - EDGE_INFLATE;
        double x2 = pos.getX() + 1.0 + EDGE_INFLATE;
        double y2 = pos.getY() + 1.0 + EDGE_INFLATE;
        double z2 = pos.getZ() + 1.0 + EDGE_INFLATE;

        switch (face) {
            case DOWN -> {
                edge(matrix, lines, cameraPos, face, x1, y1, z1, x2, y1, z1);
                edge(matrix, lines, cameraPos, face, x2, y1, z1, x2, y1, z2);
                edge(matrix, lines, cameraPos, face, x2, y1, z2, x1, y1, z2);
                edge(matrix, lines, cameraPos, face, x1, y1, z2, x1, y1, z1);
            }
            case UP -> {
                edge(matrix, lines, cameraPos, face, x1, y2, z1, x1, y2, z2);
                edge(matrix, lines, cameraPos, face, x1, y2, z2, x2, y2, z2);
                edge(matrix, lines, cameraPos, face, x2, y2, z2, x2, y2, z1);
                edge(matrix, lines, cameraPos, face, x2, y2, z1, x1, y2, z1);
            }
            case NORTH -> {
                edge(matrix, lines, cameraPos, face, x1, y1, z1, x1, y2, z1);
                edge(matrix, lines, cameraPos, face, x1, y2, z1, x2, y2, z1);
                edge(matrix, lines, cameraPos, face, x2, y2, z1, x2, y1, z1);
                edge(matrix, lines, cameraPos, face, x2, y1, z1, x1, y1, z1);
            }
            case SOUTH -> {
                edge(matrix, lines, cameraPos, face, x1, y1, z2, x2, y1, z2);
                edge(matrix, lines, cameraPos, face, x2, y1, z2, x2, y2, z2);
                edge(matrix, lines, cameraPos, face, x2, y2, z2, x1, y2, z2);
                edge(matrix, lines, cameraPos, face, x1, y2, z2, x1, y1, z2);
            }
            case WEST -> {
                edge(matrix, lines, cameraPos, face, x1, y1, z1, x1, y1, z2);
                edge(matrix, lines, cameraPos, face, x1, y1, z2, x1, y2, z2);
                edge(matrix, lines, cameraPos, face, x1, y2, z2, x1, y2, z1);
                edge(matrix, lines, cameraPos, face, x1, y2, z1, x1, y1, z1);
            }
            case EAST -> {
                edge(matrix, lines, cameraPos, face, x2, y1, z1, x2, y2, z1);
                edge(matrix, lines, cameraPos, face, x2, y2, z1, x2, y2, z2);
                edge(matrix, lines, cameraPos, face, x2, y2, z2, x2, y1, z2);
                edge(matrix, lines, cameraPos, face, x2, y1, z2, x2, y1, z1);
            }
        }
    }

    /** 提交一条棱的两个端点，法线取所属面的朝向。 */
    private static void edge(Matrix4f matrix, VertexConsumer lines, Vec3 cameraPos, Direction face,
                             double ax, double ay, double az, double bx, double by, double bz) {
        addVertex(matrix, lines, cameraPos, face, ax, ay, az);
        addVertex(matrix, lines, cameraPos, face, bx, by, bz);
    }

    /**
     * 提交单个顶点。
     *
     * <p>顶点坐标复用 {@link #VERTEX_SCRATCH}，避免每个顶点分配一个 {@link Vector3f}：
     * 最坏情形下单帧顶点数可达数万，逐顶点分配会造成明显的垃圾回收开销。
     */
    private static void addVertex(Matrix4f matrix, VertexConsumer lines, Vec3 cameraPos, Direction face,
                                  double worldX, double worldY, double worldZ) {
        Vector3f vertex = VERTEX_SCRATCH;
        vertex.set((float) (worldX - cameraPos.x),
                   (float) (worldY - cameraPos.y),
                   (float) (worldZ - cameraPos.z));
        matrix.transformPosition(vertex);
        lines.addVertex(vertex.x, vertex.y, vertex.z)
                .setColor(EDGE_RED, EDGE_GREEN, EDGE_BLUE, 0.85F)
                .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    /** 主手或副手持有造化杖时才绘制：其余物品不触发连锁，其缓存预览没有意义。 */
    private static boolean holdsBeafTool(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (BeefToolVariants.isBeafTool(stack)) {
                return true;
            }
        }
        return false;
    }
}
