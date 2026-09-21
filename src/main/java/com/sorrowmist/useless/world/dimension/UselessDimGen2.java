// UselessDimGen2.java
package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

/** 二维度生成器。布局细节见 {@link PlatformStyle#STYLE_2} 与 {@link PlatformLayout}。 */
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
    protected PlatformStyle style() {
        return PlatformStyle.STYLE_2;
    }
}
