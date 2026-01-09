// UselessDimGen3.java
package com.sorrowmist.useless.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

public class UselessDimGen3 extends AbstractPlasticPlatformGenerator {
    static final MapCodec<UselessDimGen3> CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                    instance.group(BiomeSource.CODEC.fieldOf("biome_source")
                                                    .forGetter(g -> g.biomeSource))
                            .apply(instance, UselessDimGen3::new));

    private UselessDimGen3(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override protected @NotNull MapCodec<? extends ChunkGenerator> codec() {return CODEC;}

    @Override
    protected BlockState getPlatformBlockState(int x, int z) {
        if (x == 8 && z == 8) {
            return ConfigManager.getCenterBlock().defaultBlockState();
        } else if (x <= 1 || x == 15 || z <= 1 || z == 15) {
            return ConfigManager.getBorderBlock().defaultBlockState();
        } else {
            return ConfigManager.getFillBlock().defaultBlockState();
        }
    }

    @Override
    protected String getDebugName() {
        return "Plastic Platform - Style 3 (Thick Border)";
    }
}