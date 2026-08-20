// UselessDimGen.java
package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
    protected BlockState getPlatformBlockState(DimensionGenerationConfig configuration, int x, int z) {
        if (x == 8 && z == 8) {
            return getCenterBlockState(configuration);
        } else if (x == 0 || z == 0) {
            return getBorderBlockState(configuration);
        } else {
            return getFillBlockState(configuration);
        }
    }

    @Override
    protected String getDebugName() {
        return "Plastic Platform - Style 1 (2x2 Center)";
    }
}
