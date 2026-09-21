package com.sorrowmist.useless.world.dimension;

/*
 * Portions of the boundary, road, and center-marker generation algorithm are
 * adapted from GT New Horizons/PersonalSpace:
 * https://github.com/GTNewHorizons/PersonalSpace
 * PersonalSpace is licensed under LGPL-3.0; see LICENSES/PersonalSpace-LGPL-3.0.txt.
 * The remaining generator code and platform-specific behavior are original.
 */

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Common terrain and surface-feature implementation for the three generators.
 *
 * <p>所有布局数学都在 {@link PlatformLayout} 里，本类只负责把它接到区块生成流程上。
 * 这样客户端预览可以复用同一份算法，不会与实际生成结果脱节。
 */
public abstract class AbstractPlasticPlatformGenerator extends ChunkGenerator {
    private static final int MIN_BUILD_Y = -64;
    private volatile DimensionGenerationConfig configuration = DimensionGenerationConfig.defaults();

    AbstractPlasticPlatformGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** 该维度的平台样式；布局数学统一由 {@link PlatformLayout} 提供。 */
    protected abstract PlatformStyle style();

    public final void setConfiguration(DimensionGenerationConfig configuration) {
        this.configuration = configuration.normalized();
    }

    @Override
    public void applyCarvers(@NotNull WorldGenRegion region, long seed,
                             @NotNull RandomState randomState, @NotNull BiomeManager biomeManager,
                             @NotNull StructureManager structureManager, @NotNull ChunkAccess chunk,
                             @NotNull GenerationStep.Carving step) {
        // This dimension deliberately has no caves or other terrain carving.
    }

    @Override
    public void buildSurface(@NotNull WorldGenRegion region, @NotNull StructureManager structureManager,
                             @NotNull RandomState randomState, @NotNull ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap motionBlocking = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
        Heightmap motionBlockingNoLeaves = chunk.getOrCreateHeightmapUnprimed(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

        int minY = region.getMinBuildHeight();
        int maxY = region.getMaxBuildHeight();
        DimensionGenerationConfig config = configuration;
        PlatformStyle style = style();
        int bottomY = PlatformLayout.clampY(PlatformLayout.bottomY(config), minY, maxY);
        int topY = PlatformLayout.clampY(PlatformLayout.topY(config), minY, maxY);
        int bedrockY = PlatformLayout.bedrockY(config, minY, maxY);
        ChunkPos chunkPos = chunk.getPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                if (config.generateBedrock()) {
                    chunk.setBlockState(pos.set(localX, bedrockY, localZ),
                            Blocks.BEDROCK.defaultBlockState(), false);
                }

                for (int y = bottomY + 1; y <= topY; y++) {
                    chunk.setBlockState(pos.set(localX, y, localZ),
                            PlatformLayout.platformState(style, config, chunkPos.x, chunkPos.z,
                                    localX, localZ), false);
                }
                for (int y = topY + 1; y < maxY; y++) {
                    chunk.setBlockState(pos.set(localX, y, localZ), Blocks.AIR.defaultBlockState(), false);
                }

                List<Integer> surfaces = PlatformLayout.surfaceLevels(style, config,
                        chunkPos.x, chunkPos.z, localX, localZ, minY, maxY);
                for (int surfaceY : surfaces) {
                    BlockState decoration = PlatformLayout.surfaceDecoration(style, config,
                            chunkPos.x, chunkPos.z, localX, localZ);
                    if (decoration != null) {
                        chunk.setBlockState(pos.set(localX, surfaceY, localZ), decoration, false);
                    }
                }

                int highest = PlatformLayout.highestSurface(style, config,
                        chunkPos.x, chunkPos.z, localX, localZ, minY, maxY);
                if (highest >= minY && highest < maxY) {
                    BlockState state = chunk.getBlockState(pos.set(localX, highest, localZ));
                    worldSurface.update(localX, highest, localZ, state);
                    oceanFloor.update(localX, highest, localZ, state);
                    motionBlocking.update(localX, highest, localZ, state);
                    motionBlockingNoLeaves.update(localX, highest, localZ, state);
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(@NotNull WorldGenRegion region) {
        // No mobs are spawned by this generator.
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public @NotNull CompletableFuture<ChunkAccess> fillFromNoise(@NotNull Blender blender,
                                                                 @NotNull RandomState randomState,
                                                                 @NotNull StructureManager structureManager,
                                                                 @NotNull ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return MIN_BUILD_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, @NotNull Heightmap.Types heightmap,
                             @NotNull LevelHeightAccessor level, @NotNull RandomState randomState) {
        DimensionGenerationConfig config = configuration;
        int surface = PlatformLayout.highestSurface(style(), config, x >> 4, z >> 4, x & 15, z & 15,
                level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (surface < level.getMinBuildHeight()) return level.getMinBuildHeight();
        return Math.min(level.getMaxBuildHeight(), surface + 1);
    }

    @Override
    public @NotNull NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor level,
                                              @NotNull RandomState randomState) {
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        DimensionGenerationConfig config = configuration;
        PlatformStyle style = style();
        int localX = x & 15;
        int localZ = z & 15;
        int bottomY = PlatformLayout.clampY(PlatformLayout.bottomY(config), minBuild, maxBuild);
        int topY = PlatformLayout.clampY(PlatformLayout.topY(config), minBuild, maxBuild);
        int bedrockY = PlatformLayout.bedrockY(config, minBuild, maxBuild);

        if (config.generateBedrock() && bedrockY >= minBuild && bedrockY < maxBuild) {
            column[bedrockY - minBuild] = Blocks.BEDROCK.defaultBlockState();
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        for (int y = bottomY + 1; y <= topY && y < maxBuild; y++) {
            if (y >= minBuild) column[y - minBuild] =
                    PlatformLayout.platformState(style, config, chunkX, chunkZ, localX, localZ);
        }

        List<Integer> surfaces = PlatformLayout.surfaceLevels(style, config, chunkX, chunkZ,
                localX, localZ, minBuild, maxBuild);
        for (int surfaceY : surfaces) {
            BlockState decoration = PlatformLayout.surfaceDecoration(style, config,
                    chunkX, chunkZ, localX, localZ);
            if (decoration != null && surfaceY >= minBuild && surfaceY < maxBuild) {
                column[surfaceY - minBuild] = decoration;
            }
        }
        return new NoiseColumn(minBuild, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, @NotNull RandomState randomState, @NotNull BlockPos pos) {
        DimensionGenerationConfig config = configuration;
        list.add("Useless Dimension - " + style().debugName());
        list.add("Height: Y=" + PlatformLayout.bottomY(config) + " ~ " + PlatformLayout.topY(config));
        list.add("Layers: " + config.platformLayers());
        list.add("Boundary: " + config.boundaryIntervalX() + " x " + config.boundaryIntervalZ());
        list.add("Road: width=" + config.roadWidth() + ", mode=" + config.mode());
        list.add("Center marker: " + config.centerMarkerEnabled());
    }
}
