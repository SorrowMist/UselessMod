// UselessDimGen2.java
package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.core.config.ConfigManager;
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
    protected BlockState getPlatformBlockState(int x, int z) {
        if (x >= 7 && x <= 8 && z >= 7 && z <= 8) { // 中心 2x2
            return ConfigManager.getCenterBlock().defaultBlockState();
        } else if (x >= 1 && x <= 14 && z >= 1 && z <= 14) { // 内层填充
            return ConfigManager.getFillBlock().defaultBlockState();
        } else {
            return ConfigManager.getBorderBlock().defaultBlockState(); // 最外边框
        }
    }

    @Override
    protected String getDebugName() {
        return "Plastic Platform - Style 2 (L-Shaped Border)";
    }
}