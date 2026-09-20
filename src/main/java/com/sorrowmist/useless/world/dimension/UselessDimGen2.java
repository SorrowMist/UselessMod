// UselessDimGen2.java
package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

public class UselessDimGen2 extends AbstractPlasticPlatformGenerator {
    static final MapCodec<UselessDimGen2> CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                    instance.group(BiomeSource.CODEC.fieldOf("biome_source")
                                                    .forGetter(g -> g.biomeSource))
                            .apply(instance, UselessDimGen2::new));

    private UselessDimGen2(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override protected @NotNull MapCodec<? extends ChunkGenerator> codec() {return CODEC;}

    @Override
    protected PlatformRole getPlatformRole(int x, int z) {
        if (x >= 7 && x <= 8 && z >= 7 && z <= 8) { // 中心 2x2
            return PlatformRole.CENTER;
        } else if (x >= 1 && x <= 14 && z >= 1 && z <= 14) { // 内层填充
            return PlatformRole.FILL;
        } else {
            return PlatformRole.BORDER; // 最外边框
        }
    }

    /** 二维度多联边框固定为 2 格宽。 */
    @Override
    protected int getMultiBorderThickness() {
        return 2;
    }

    /**
     * 二维度多联中心固定为 2×2，与其单区块中心（x∈7..8, z∈7..8）的风格一致。
     * 不再按合并尺寸奇偶收缩成 1 格，因此偶数与奇数区块数下都是 4 格。
     */
    @Override
    protected boolean isMultiCenterMarker(int groupX, int groupZ, int widthX, int widthZ) {
        int centerX = widthX / 2;
        int centerZ = widthZ / 2;
        return (groupX == centerX - 1 || groupX == centerX)
                && (groupZ == centerZ - 1 || groupZ == centerZ);
    }

    @Override
    protected boolean isCenterMarkerPosition(int areaX, int areaZ, int centerX, int centerZ) {
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
    protected String getDebugName() {
        return "Plastic Platform - Style 2 (L-Shaped Border)";
    }
}
