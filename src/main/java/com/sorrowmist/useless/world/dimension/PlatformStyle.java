package com.sorrowmist.useless.world.dimension;

/**
 * 每个维度各自的平台样式参数。
 *
 * <p>这些参数原先分散在 {@code UselessDimGen} / {@code UselessDimGen2} / {@code UselessDimGen3}
 * 的覆写方法里。集中到本枚举后有两个好处：
 * <ul>
 *   <li>布局算法（{@link PlatformLayout}）只需要一个 {@code PlatformStyle} 参数，
 *       生成器与客户端预览可以共用同一份实现，不会各自漂移；</li>
 *   <li>枚举是纯数据、双侧安全，客户端无需实例化 {@code ChunkGenerator} 即可取到样式。</li>
 * </ul>
 *
 * <p>默认实现对应原 {@code AbstractPlasticPlatformGenerator} 里的默认方法体（二维度风格）。
 */
public enum PlatformStyle {

    /** 一维度：中心 1 格 (8,8)，边框为 x==0 或 z==0，多联边框 1 格厚。 */
    STYLE_1 {
        @Override
        public PlatformLayout.Role platformRole(int localX, int localZ) {
            if (localX == 8 && localZ == 8) {
                return PlatformLayout.Role.CENTER;
            }
            return localX == 0 || localZ == 0
                    ? PlatformLayout.Role.BORDER : PlatformLayout.Role.FILL;
        }

        /** 一维度多联中心：偶数区块数占 2×2，奇数占 1 格，两轴各自判定。 */
        @Override
        public boolean isMultiCenterMarker(int groupX, int groupZ, int widthX, int widthZ) {
            return evenSizedMultiCenterMarker(groupX, groupZ, widthX, widthZ);
        }

        @Override
        public int roadStartBoundaryWidth() {
            return 1;
        }

        @Override
        public int roadCenterLineWidth() {
            return 1;
        }

        @Override
        public boolean unshiftedRoadIntersectionLayout() {
            return true;
        }

        @Override
        public String debugName() {
            return "Plastic Platform - Style 1 (2x2 Center)";
        }
    },

    /** 二维度：中心 2×2 (7..8,7..8)，边框为最外一圈，多联边框 2 格厚。 */
    STYLE_2 {
        @Override
        public PlatformLayout.Role platformRole(int localX, int localZ) {
            if (localX >= 7 && localX <= 8 && localZ >= 7 && localZ <= 8) {
                return PlatformLayout.Role.CENTER;
            }
            if (localX >= 1 && localX <= 14 && localZ >= 1 && localZ <= 14) {
                return PlatformLayout.Role.FILL;
            }
            return PlatformLayout.Role.BORDER;
        }

        /** 二维度多联边框固定为 2 格宽。 */
        @Override
        public int multiBorderThickness() {
            return 2;
        }

        /**
         * 二维度多联中心固定为 2×2，与其单区块中心（x∈7..8, z∈7..8）的风格一致。
         * 不再按合并尺寸奇偶收缩成 1 格，因此偶数与奇数区块数下都是 4 格。
         */
        @Override
        public boolean isMultiCenterMarker(int groupX, int groupZ, int widthX, int widthZ) {
            int centerX = widthX / 2;
            int centerZ = widthZ / 2;
            return (groupX == centerX - 1 || groupX == centerX)
                    && (groupZ == centerZ - 1 || groupZ == centerZ);
        }

        @Override
        public boolean isCenterMarkerPosition(int areaX, int areaZ, int centerX, int centerZ) {
            // An even boundary interval places the area center on a chunk seam.
            // Cover the complete 2x2 center in that case (for example, 2x3).
            if ((centerX & 15) == 0 || (centerZ & 15) == 0) {
                return areaX >= centerX - 1 && areaX <= centerX
                        && areaZ >= centerZ - 1 && areaZ <= centerZ;
            }

            // Keep the two-block marker centered on a fixed row.
            return (areaX == centerX - 1 || areaX == centerX) && areaZ == centerZ;
        }

        @Override
        public String debugName() {
            return "Plastic Platform - Style 2 (L-Shaped Border)";
        }
    },

    /** 三维度：中心 1 格 (8,8)，边框为 2 格厚外圈，多联边框 3 格厚。 */
    STYLE_3 {
        @Override
        public PlatformLayout.Role platformRole(int localX, int localZ) {
            if (localX == 8 && localZ == 8) {
                return PlatformLayout.Role.CENTER;
            }
            return localX <= 1 || localX == 15 || localZ <= 1 || localZ == 15
                    ? PlatformLayout.Role.BORDER : PlatformLayout.Role.FILL;
        }

        /** 三维度多联边框固定为 3 格宽。 */
        @Override
        public int multiBorderThickness() {
            return 3;
        }

        @Override
        public int roadStartBoundaryWidth() {
            return 1;
        }

        @Override
        public int roadCenterLineWidth() {
            return 1;
        }

        @Override
        public boolean unshiftedRoadIntersectionLayout() {
            return true;
        }

        @Override
        public String debugName() {
            return "Plastic Platform - Style 3 (Thick Border)";
        }
    };

    /** 区块内的平台角色，决定该列用边框 / 填充 / 中心方块。 */
    public abstract PlatformLayout.Role platformRole(int localX, int localZ);

    /** 道路起始侧保留给边界的宽度（格）。 */
    public int roadStartBoundaryWidth() {
        return 0;
    }

    /** 道路中心标线的宽度（格）。 */
    public int roadCenterLineWidth() {
        return 2;
    }

    /** 交叉口是否忽略道路起始侧的边界内缩。 */
    public boolean unshiftedRoadIntersectionLayout() {
        return false;
    }

    /** 多联模式下合并组起始侧的边框宽度（格）。 */
    public int multiBorderThickness() {
        return 1;
    }

    /** 多联合并组的中心标记判定。默认只占正中心 1 格，各轴独立计算。 */
    public boolean isMultiCenterMarker(int groupX, int groupZ, int widthX, int widthZ) {
        return groupX == widthX / 2 && groupZ == widthZ / 2;
    }

    /** 马路模式下单个平台区域内的中心标记判定。默认只占正中心 1 格。 */
    public boolean isCenterMarkerPosition(int areaX, int areaZ, int centerX, int centerZ) {
        return areaX == centerX && areaZ == centerZ;
    }

    /** 调试界面显示的名称。 */
    public abstract String debugName();

    /**
     * 偶数区块数的轴中心占 2 格、奇数占 1 格，两个轴各自独立判定，
     * 因此非正方形的合并尺寸也能在每条轴上分别取到正确的中心位置。
     */
    public static boolean evenSizedMultiCenterMarker(
            int groupX, int groupZ, int widthX, int widthZ) {
        int centerX = widthX / 2;
        int centerZ = widthZ / 2;
        int minX = widthX % 32 == 0 ? centerX - 1 : centerX;
        int minZ = widthZ % 32 == 0 ? centerZ - 1 : centerZ;
        return groupX >= minX && groupX <= centerX && groupZ >= minZ && groupZ <= centerZ;
    }
}
