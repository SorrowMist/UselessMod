// UselessDimGen.java
package com.sorrowmist.useless.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

public class UselessDimGen extends AbstractPlasticPlatformGenerator {
    static final MapCodec<UselessDimGen> CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                    instance.group(BiomeSource.CODEC.fieldOf("biome_source")
                                                    .forGetter(g -> g.biomeSource))
                            .apply(instance, UselessDimGen::new));

    private UselessDimGen(BiomeSource biomeSource) {
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
        return "Plastic Platform - Style 1 (2x2 Center)";
    }
}