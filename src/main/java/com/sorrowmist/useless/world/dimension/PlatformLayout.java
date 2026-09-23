package com.sorrowmist.useless.world.dimension;

/*
 * Portions of the boundary, road, and center-marker generation algorithm are
 * adapted from GT New Horizons/PersonalSpace:
 * https://github.com/GTNewHorizons/PersonalSpace
 * PersonalSpace is licensed under LGPL-3.0; see LICENSES/PersonalSpace-LGPL-3.0.txt.
 * The remaining generator code and platform-specific behavior are original.
 */

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台布局的纯函数实现：给定样式与配置，算出某个坐标该放什么方块。
 *
 * <p>这是布局的唯一真相源——世界生成器（{@code AbstractPlasticPlatformGenerator}）与
 * 客户端预览（{@code PlatformPreview}）都调用这里，因此预览不会与实际生成结果脱节。
 *
 * <p><b>双侧安全</b>：本类只依赖 {@link DimensionGenerationConfig}、{@link BlockState}
 * 与 {@link PlatformStyle}，不引用 {@code WorldGenRegion} / {@code ChunkAccess} /
 * {@code ChunkGenerator} 等服务端专属类型，客户端可直接调用。
 */
public final class PlatformLayout {

    /** 区块内的平台角色，决定该列用边框 / 填充 / 中心方块。 */
    public enum Role {
        BORDER,
        FILL,
        CENTER
    }

    private PlatformLayout() {
    }

    /* ================= 高度 ================= */

    public static int topY(DimensionGenerationConfig config) {
        return config.platformStartY() + config.platformLayers();
    }

    public static int bottomY(DimensionGenerationConfig config) {
        return config.platformStartY();
    }

    public static int bedrockY(DimensionGenerationConfig config, int minY, int maxY) {
        if (config.bedrockAtBottom()) return clampY(minY, minY, maxY);
        return clampY(bottomY(config), minY, maxY);
    }

    /* ================= 平台方块 ================= */

    public static BlockState platformState(PlatformStyle style, DimensionGenerationConfig config,
                                           int chunkX, int chunkZ, int localX, int localZ) {
        if (config.mode() == DimensionGenerationConfig.Mode.MULTI) {
            return multiModePlatformState(style, config, chunkX, chunkZ, localX, localZ);
        }
        return platformBlockState(style, config, localX, localZ);
    }

    public static BlockState platformBlockState(PlatformStyle style, DimensionGenerationConfig config,
                                                int localX, int localZ) {
        return switch (style.platformRole(localX, localZ)) {
            case BORDER -> borderState(config);
            case CENTER -> centerState(config);
            case FILL -> fillState(config);
        };
    }

    public static BlockState borderState(DimensionGenerationConfig config) {
        return config.borderBlock().defaultBlockState();
    }

    public static BlockState fillState(DimensionGenerationConfig config) {
        return config.fillBlock().defaultBlockState();
    }

    public static BlockState centerState(DimensionGenerationConfig config) {
        return config.centerBlock().defaultBlockState();
    }

    /**
     * 多联模式：把边界间隔当作合并尺寸，把 sizeX×sizeZ 个区块当成一个整体平台。
     * 边框只铺在每个合并组的起始列与起始行上，相邻合并组共用同一条边框带，
     * 因此交界处不会叠加出两条边框；边框厚度沿用具体生成器自己的边框语义，
     * 例如三维度是 3 格厚、二维度是 2 格厚、一维度是 1 格厚。组内部（包括区块
     * 之间的接缝）全部是填充方块，中心方块以填充区居中，占 1 格或 2×2 格。
     */
    private static BlockState multiModePlatformState(PlatformStyle style,
                                                     DimensionGenerationConfig config,
                                                     int chunkX, int chunkZ,
                                                     int localX, int localZ) {
        int sizeX = Math.max(1, config.boundaryIntervalX());
        int sizeZ = Math.max(1, config.boundaryIntervalZ());
        // 合并区域内的相对坐标，范围分别是 [0, sizeX * 16) 与 [0, sizeZ * 16)。
        int groupX = mod(chunkX, sizeX) * 16 + localX;
        int groupZ = mod(chunkZ, sizeZ) * 16 + localZ;
        // 只铺起始列与起始行：相邻合并组共用同一条边框带，交界处不会出现两条边框。
        // 厚度由具体生成器声明的多联边框宽度决定，并且不会超过半个合并组。
        int thickness = Math.min(style.multiBorderThickness(),
                (Math.min(sizeX, sizeZ) * 16 - 1) / 2);

        if (style.isMultiCenterMarker(groupX, groupZ, sizeX * 16, sizeZ * 16, thickness)) {
            return centerState(config);
        }
        boolean onBorder = groupX < thickness || groupZ < thickness;
        return onBorder ? borderState(config) : fillState(config);
    }

    /* ================= 表面装饰 ================= */

    public static List<Integer> surfaceLevels(PlatformStyle style, DimensionGenerationConfig config,
                                              int chunkX, int chunkZ,
                                              int localX, int localZ, int minY, int maxY) {
        List<Integer> surfaces = new ArrayList<>();
        int top = topY(config);
        if (top >= minY && top < maxY
                && !platformState(style, config, chunkX, chunkZ, localX, localZ).isAir()) {
            surfaces.add(top);
        }
        return surfaces;
    }

    public static int highestSurface(PlatformStyle style, DimensionGenerationConfig config,
                                     int chunkX, int chunkZ,
                                     int localX, int localZ, int minY, int maxY) {
        List<Integer> surfaces = surfaceLevels(style, config, chunkX, chunkZ,
                localX, localZ, minY, maxY);
        return surfaces.isEmpty() ? Integer.MIN_VALUE : surfaces.get(surfaces.size() - 1);
    }

    /** 顶层装饰方块；无装饰时返回 {@code null}。 */
    public static BlockState surfaceDecoration(PlatformStyle style, DimensionGenerationConfig config,
                                               int chunkX, int chunkZ, int localX, int localZ) {
        // 多联模式不生成马路与边界装饰，平台由填充方块无缝连成一片。
        if (config.mode() == DimensionGenerationConfig.Mode.MULTI) return null;
        int intervalX = config.boundaryIntervalX();
        int intervalZ = config.boundaryIntervalZ();
        int roadWidth = config.roadWidth();
        if (intervalX <= 0 && intervalZ <= 0) return null;

        int periodX = intervalX > 0 ? intervalX + roadWidth : 0;
        int periodZ = intervalZ > 0 ? intervalZ + roadWidth : 0;
        int chunkModX = periodX > 0 ? mod(chunkX, periodX) : 0;
        int chunkModZ = periodZ > 0 ? mod(chunkZ, periodZ) : 0;
        boolean roadX = roadWidth > 0 && intervalX > 0 && chunkModX >= intervalX;
        boolean roadZ = roadWidth > 0 && intervalZ > 0 && chunkModZ >= intervalZ;
        int roadStartBoundaryWidth = style.roadStartBoundaryWidth();
        int roadCenterLineWidth = style.roadCenterLineWidth();
        int worldX = chunkX * 16 + localX;
        int worldZ = chunkZ * 16 + localZ;

        if (roadX || roadZ) {
            int roadWidthBlocks = roadWidth * 16 - roadStartBoundaryWidth;
            if (roadX && roadZ) {
                int rawOffsetX = (chunkModX - intervalX) * 16 + localX;
                int rawOffsetZ = (chunkModZ - intervalZ) * 16 + localZ;
                int offsetX = rawOffsetX - roadStartBoundaryWidth;
                int offsetZ = rawOffsetZ - roadStartBoundaryWidth;
                if (offsetX < 0 || offsetZ < 0) {
                    if (!style.unshiftedRoadIntersectionLayout()) {
                        return boundaryState(config, worldX, worldZ);
                    }
                    // The shared negative corner is still the boundary tile;
                    // only the adjacent tiles belong to the road edge.
                    if (rawOffsetX < roadStartBoundaryWidth
                            && rawOffsetZ < roadStartBoundaryWidth) {
                        return boundaryState(config, worldX, worldZ);
                    }
                }
                boolean unshifted = style.unshiftedRoadIntersectionLayout();
                int intersectionOffsetX = unshifted ? rawOffsetX : offsetX;
                int intersectionOffsetZ = unshifted ? rawOffsetZ : offsetZ;
                int intersectionWidth = unshifted ? roadWidth * 16 : roadWidthBlocks;
                boolean onIntersectionEdgeX = unshifted
                        ? rawOffsetX <= roadStartBoundaryWidth || rawOffsetX == intersectionWidth - 1
                        : intersectionOffsetX == 0 || intersectionOffsetX == intersectionWidth - 1;
                boolean onIntersectionEdgeZ = unshifted
                        ? rawOffsetZ <= roadStartBoundaryWidth || rawOffsetZ == intersectionWidth - 1
                        : intersectionOffsetZ == 0 || intersectionOffsetZ == intersectionWidth - 1;
                if (config.mode() == DimensionGenerationConfig.Mode.ROAD
                        && onIntersectionEdgeX && onIntersectionEdgeZ) {
                    return config.roadBlockB().defaultBlockState();
                }
                return config.roadBlockA().defaultBlockState();
            }
            if (roadX) {
                int offset = (chunkModX - intervalX) * 16 + localX - roadStartBoundaryWidth;
                if (offset < 0) return boundaryState(config, worldX, worldZ);
                return roadBlockState(config, offset, worldZ, roadWidthBlocks, roadCenterLineWidth);
            }
            int offset = (chunkModZ - intervalZ) * 16 + localZ - roadStartBoundaryWidth;
            if (offset < 0) return boundaryState(config, worldX, worldZ);
            return roadBlockState(config, offset, worldX, roadWidthBlocks, roadCenterLineWidth);
        }

        // Boundary strips belong to the road layout. A zero-width road means
        // that neither the road nor its boundary should be generated.
        if (roadWidth > 0) {
            boolean boundaryX = isBoundaryChunk(chunkX, intervalX, roadWidth);
            boolean previousBoundaryX = roadStartBoundaryWidth == 0
                    && isPreviousBoundaryChunk(chunkX, intervalX, roadWidth);
            boolean boundaryZ = isBoundaryChunk(chunkZ, intervalZ, roadWidth);
            boolean previousBoundaryZ = roadStartBoundaryWidth == 0
                    && isPreviousBoundaryChunk(chunkZ, intervalZ, roadWidth);
            if ((boundaryX && localX == 0) || (previousBoundaryX && localX == 15)
                    || (boundaryZ && localZ == 0) || (previousBoundaryZ && localZ == 15)) {
                return boundaryState(config, worldX, worldZ);
            }
        }

        if (config.centerMarkerEnabled() && intervalX > 0 && intervalZ > 0) {
            int periodBlocksX = periodX * 16;
            int periodBlocksZ = periodZ * 16;
            int areaWidthX = intervalX * 16;
            int areaWidthZ = intervalZ * 16;
            int centerX = intervalX * 8;
            int centerZ = intervalZ * 8;
            int areaX = mod(chunkX * 16 + localX, periodBlocksX);
            int areaZ = mod(chunkZ * 16 + localZ, periodBlocksZ);
            if (areaX < areaWidthX && areaZ < areaWidthZ
                    && style.isCenterMarkerPosition(areaX, areaZ, centerX, centerZ)) {
                return config.centerMarkerBlock().defaultBlockState();
            }
        }
        return null;
    }

    private static BlockState roadBlockState(DimensionGenerationConfig config, int offsetInRoad,
                                             int alongRoad, int roadWidthBlocks, int centerLineWidth) {
        if (offsetInRoad == 0 || offsetInRoad == roadWidthBlocks - 1) {
            return config.roadBlockB().defaultBlockState();
        }
        if (roadWidthBlocks >= 4) {
            int center = roadWidthBlocks / 2;
            boolean onCenterLine = centerLineWidth == 1
                    ? offsetInRoad == center
                    : offsetInRoad == center || offsetInRoad == center - 1;
            if (onCenterLine
                    && mod(alongRoad + 2, 8) < 4) {
                return config.roadBlockC().defaultBlockState();
            }
        }
        return config.roadBlockA().defaultBlockState();
    }

    private static BlockState boundaryState(DimensionGenerationConfig config, int worldX, int worldZ) {
        return (((worldX + worldZ) & 1) == 0
                ? config.boundaryBlockA() : config.boundaryBlockB()).defaultBlockState();
    }

    /* ================= 周期判定 ================= */

    public static boolean isBoundaryChunk(int chunk, int interval, int roadWidth) {
        if (interval <= 0) return false;
        return mod(chunk, interval + roadWidth) == 0;
    }

    private static boolean isPreviousBoundaryChunk(int chunk, int interval, int roadWidth) {
        if (interval <= 0) return false;
        int period = interval + roadWidth;
        if (roadWidth > 0) return mod(chunk, period) == interval - 1;
        return mod(chunk + 1, period) == 0;
    }

    /* ================= 工具 ================= */

    public static int mod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    public static int clampY(int y, int minY, int maxY) {
        return Math.max(minY, Math.min(maxY - 1, y));
    }
}
