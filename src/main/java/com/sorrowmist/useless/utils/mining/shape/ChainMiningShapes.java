package com.sorrowmist.useless.utils.mining.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置连锁形状集合。
 *
 * <p>枚举顺序即滚轮循环顺序，首项为默认值。
 *
 * <p>{@link #SHAPELESS} 不提供固定布局：它在服务端沿用既有的相邻扩散扫描
 * （普通连锁按 26 邻域逐格扩展，增强连锁按范围全量收集后按距离截断），
 * 因此该形状与历史行为完全一致。其余形状给出确定性布局，与增强连锁开关无关。
 *
 * <p>形状的种类划分与各形状的布局语义参考 FTB Ultimine（FTB 连锁）的挖掘形状设计，
 * 形状标识沿用其命名以便与上游语义对照；本文件为独立实现。
 */
public enum ChainMiningShapes implements ChainMiningShape {

    /**
     * 默认形状：布局由被破坏方块与相邻方块共同决定，使用既有扫描逻辑。
     */
    SHAPELESS("shapeless") {
        @Override
        public List<BlockPos> getBlocks(ChainMiningShapeContext context) {
            return List.of();
        }

        @Override
        public boolean usesBuiltinScan() {
            return true;
        }
    },

    /**
     * 沿视线方向推进的单格直线隧道。
     */
    SMALL_TUNNEL("small_tunnel") {
        @Override
        public List<BlockPos> getBlocks(ChainMiningShapeContext context) {
            List<BlockPos> list = new ArrayList<>(context.maxBlocks());
            for (int i = 0; i < context.maxBlocks(); i++) {
                BlockPos pos = context.originPos().relative(context.face(), -i);
                if (!context.check(pos)) {
                    break;
                }
                list.add(pos);
            }
            return list;
        }
    },

    /**
     * 单层 3×3。
     */
    SMALL_SQUARE("small_square") {
        @Override
        public List<BlockPos> getBlocks(ChainMiningShapeContext context) {
            return tunnelLayers(context, 1);
        }
    },

    /**
     * 3×3 逐层向前推进的隧道。
     */
    LARGE_TUNNEL("large_tunnel") {
        @Override
        public List<BlockPos> getBlocks(ChainMiningShapeContext context) {
            return tunnelLayers(context, Integer.MAX_VALUE);
        }
    },

    /**
     * 对角隧道，逐层向下，用于向下开拓。
     */
    MINING_TUNNEL("mining_tunnel") {
        @Override
        public List<BlockPos> getBlocks(ChainMiningShapeContext context) {
            return diagonalTunnel(context, -1);
        }
    },

    /**
     * 对角隧道，逐层向上，用于向上逃生。
     */
    ESCAPE_TUNNEL("escape_tunnel") {
        @Override
        public List<BlockPos> getBlocks(ChainMiningShapeContext context) {
            return diagonalTunnel(context, 1);
        }
    };

    private final ResourceLocation id;

    ChainMiningShapes(String path) {
        this.id = ResourceLocation.fromNamespaceAndPath("useless_mod", path);
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    /**
     * {@return true 表示该形状使用既有的相邻扩散扫描，{@link #getBlocks} 的返回值不被使用}
     */
    public boolean usesBuiltinScan() {
        return false;
    }

    /**
     * {@return 该形状的显示名翻译键}
     */
    public String getTranslationKey() {
        return "gui.useless_mod.shape." + this.id.getPath();
    }

    /**
     * {@return 该形状的方向说明翻译键}
     *
     * <p>形状名只表达布局类型，不表达推进方向；隧道与对角类的实际走向取决于点击面与玩家朝向，
     * 缺少该说明时玩家无法预判挖掘方向，因此显示名与方向说明成对出现。
     */
    public String getDescriptionKey() {
        return "gui.useless_mod.shape." + this.id.getPath() + ".desc";
    }

    /**
     * 按滚动方向循环取相邻形状。
     *
     * @param delta 滚动步数，正数为向后一个形状
     * @return 切换后的形状
     */
    public ChainMiningShapes cycle(int delta) {
        ChainMiningShapes[] values = values();
        int index = (ordinal() + delta) % values.length;
        if (index < 0) {
            index += values.length;
        }
        return values[index];
    }

    /**
     * 按持久化标识解析形状。
     *
     * <p>标识缺失或无法识别时回退到 {@link #SHAPELESS}，保证旧存档中的物品仍可正常连锁。
     *
     * @param id 形状标识，允许为 null
     * @return 对应的形状，未命中时为默认形状
     */
    public static ChainMiningShapes byId(String id) {
        if (id == null || id.isEmpty()) {
            return SHAPELESS;
        }
        for (ChainMiningShapes shape : values()) {
            if (shape.id.toString().equals(id) || shape.id.getPath().equals(id)) {
                return shape;
            }
        }
        return SHAPELESS;
    }

    /**
     * 生成 3×3 逐层推进的隧道。
     *
     * <p>首层仅取 3×3 中除中心以外的八格，中心即原点；后续每一层整体前移一格。
     * 某一层没有任何格子可用时终止推进，避免在已挖空的区域继续向深处扩展。
     *
     * @param context 形状上下文
     * @param maxDepth 推进层数上限
     * @return 候选方块序列
     */
    private static List<BlockPos> tunnelLayers(ChainMiningShapeContext context, int maxDepth) {
        List<BlockPos> list = new ArrayList<>();
        BlockPos basePos = context.originPos();
        list.add(basePos);
        int depth = 0;

        while (depth < maxDepth && list.size() < context.maxBlocks()) {
            int size = list.size();
            layer:
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (depth > 0 || a != 0 || b != 0) {
                        BlockPos pos = switch (context.face().getAxis()) {
                            case X -> basePos.offset(0, a, b);
                            case Y -> basePos.offset(a, 0, b);
                            case Z -> basePos.offset(a, b, 0);
                        };
                        if (context.check(pos)) {
                            list.add(pos);
                            if (list.size() >= context.maxBlocks()) {
                                break layer;
                            }
                        }
                    }
                }
            }
            if (list.size() == size) {
                break;
            }
            basePos = basePos.relative(context.face().getOpposite());
            depth++;
        }

        return list;
    }

    /**
     * 生成沿水平朝向逐层升降的对角隧道。
     *
     * <p>点击面为竖直面（顶面或底面）时没有可用的水平推进方向，此时改用玩家朝向的反方向作为
     * 推进基准，与既有连锁扫描中「准星朝上下时按视线方向展开」的处理保持一致。
     *
     * @param context    形状上下文
     * @param yDirection 每推进一格的 Y 轴位移，负值向下、正值向上
     * @return 候选方块序列
     */
    private static List<BlockPos> diagonalTunnel(ChainMiningShapeContext context, int yDirection) {
        Direction face = context.face().getAxis().isVertical()
                ? context.player().getDirection().getOpposite()
                : context.face();

        List<BlockPos> list = new ArrayList<>(context.maxBlocks());
        for (int i = 0; i < context.maxBlocks(); i++) {
            BlockPos pos = context.originPos().offset(-face.getStepX() * i, yDirection * i, -face.getStepZ() * i);
            if (!context.check(pos)) {
                break;
            }
            list.add(pos);
        }

        return list;
    }
}
