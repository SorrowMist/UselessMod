// UselessDimGen.java
package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

/** 一维度生成器。布局细节见 {@link PlatformStyle#STYLE_1} 与 {@link PlatformLayout}。 */
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
    protected PlatformStyle style() {
        return PlatformStyle.STYLE_1;
    }
}
