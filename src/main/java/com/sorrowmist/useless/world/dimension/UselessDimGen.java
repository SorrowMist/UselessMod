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
    protected PlatformRole getPlatformRole(int x, int z) {
        if (x == 8 && z == 8) {
            return PlatformRole.CENTER;
        } else if (x == 0 || z == 0) {
            return PlatformRole.BORDER;
        } else {
            return PlatformRole.FILL;
        }
    }

    @Override
    protected int getRoadStartBoundaryWidth() {
        return 1;
    }

    @Override
    protected int getRoadCenterLineWidth() {
        return 1;
    }

    @Override
    protected boolean useUnshiftedRoadIntersectionLayout() {
        return true;
    }

    @Override
    protected String getDebugName() {
        return "Plastic Platform - Style 1 (2x2 Center)";
    }
}
