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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Common terrain and surface-feature implementation for the three generators. */
public abstract class AbstractPlasticPlatformGenerator extends ChunkGenerator {
    private static final int MIN_BUILD_Y = -64;
    private volatile DimensionGenerationConfig configuration = DimensionGenerationConfig.defaults();

    AbstractPlasticPlatformGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    protected enum PlatformRole {
        BORDER,
        FILL,
        CENTER
    }

    /** Returns the old per-chunk style role at a local block position. */
    protected abstract PlatformRole getPlatformRole(int x, int z);

    /** Returns the number of road-start blocks reserved for the boundary. */
    protected int getRoadStartBoundaryWidth() {
        return 0;
    }

    /** Returns the number of blocks used by a road's center marking. */
    protected int getRoadCenterLineWidth() {
        return 2;
    }

    /** Whether an intersection ignores the road-start boundary inset. */
    protected boolean useUnshiftedRoadIntersectionLayout() {
        return false;
    }

    /** Allows platform styles to define how a center marker occupies its center. */
    protected boolean isCenterMarkerPosition(int areaX, int areaZ, int centerX, int centerZ) {
        return areaX == centerX && areaZ == centerZ;
    }

    protected final BlockState getPlatformBlockState(
            DimensionGenerationConfig configuration, int x, int z) {
        return switch (getPlatformRole(x, z)) {
            case BORDER -> getBorderBlockState(configuration);
            case CENTER -> getCenterBlockState(configuration);
            case FILL -> getFillBlockState(configuration);
        };
    }

    protected abstract String getDebugName();

    public final void setConfiguration(DimensionGenerationConfig configuration) {
        this.configuration = configuration.normalized();
    }

    protected final BlockState getBorderBlockState(DimensionGenerationConfig configuration) {
        return configuration.borderBlock().defaultBlockState();
    }

    protected final BlockState getFillBlockState(DimensionGenerationConfig configuration) {
        return configuration.fillBlock().defaultBlockState();
    }

    protected final BlockState getCenterBlockState(DimensionGenerationConfig configuration) {
        return configuration.centerBlock().defaultBlockState();
    }

    private int getTopY(DimensionGenerationConfig configuration) {
        return configuration.platformStartY() + configuration.platformLayers();
    }

    private int getBottomY(DimensionGenerationConfig configuration) {
        return configuration.platformStartY();
    }

    private int getBedrockY(DimensionGenerationConfig configuration, int minY, int maxY) {
        if (configuration.bedrockAtBottom()) return clampY(minY, minY, maxY);
        return clampY(getBottomY(configuration), minY, maxY);
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
        int bottomY = clampY(getBottomY(config), minY, maxY);
        int topY = clampY(getTopY(config), minY, maxY);
        int bedrockY = getBedrockY(config, minY, maxY);
        ChunkPos chunkPos = chunk.getPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                if (config.generateBedrock()) {
                    chunk.setBlockState(pos.set(localX, bedrockY, localZ),
                            Blocks.BEDROCK.defaultBlockState(), false);
                }

                for (int y = bottomY + 1; y <= topY; y++) {
                    chunk.setBlockState(pos.set(localX, y, localZ),
                            platformState(config, localX, localZ), false);
                }
                for (int y = topY + 1; y < maxY; y++) {
                    chunk.setBlockState(pos.set(localX, y, localZ), Blocks.AIR.defaultBlockState(), false);
                }

                List<Integer> surfaces = surfaceLevels(config, localX, localZ, minY, maxY);
                for (int surfaceY : surfaces) {
                    BlockState decoration = surfaceDecoration(config, chunkPos.x, chunkPos.z,
                            localX, localZ);
                    if (decoration != null) {
                        chunk.setBlockState(pos.set(localX, surfaceY, localZ), decoration, false);
                    }
                }

                int highest = highestSurface(config, localX, localZ, minY, maxY);
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
        int surface = highestSurface(config, x & 15, z & 15,
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
        int localX = x & 15;
        int localZ = z & 15;
        int bottomY = clampY(getBottomY(config), minBuild, maxBuild);
        int topY = clampY(getTopY(config), minBuild, maxBuild);
        int bedrockY = getBedrockY(config, minBuild, maxBuild);

        if (config.generateBedrock() && bedrockY >= minBuild && bedrockY < maxBuild) {
            column[bedrockY - minBuild] = Blocks.BEDROCK.defaultBlockState();
        }
        for (int y = bottomY + 1; y <= topY && y < maxBuild; y++) {
            if (y >= minBuild) column[y - minBuild] = platformState(config, localX, localZ);
        }

        List<Integer> surfaces = surfaceLevels(config, localX, localZ, minBuild, maxBuild);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        for (int surfaceY : surfaces) {
            BlockState decoration = surfaceDecoration(config, chunkX, chunkZ, localX, localZ);
            if (decoration != null && surfaceY >= minBuild && surfaceY < maxBuild) {
                column[surfaceY - minBuild] = decoration;
            }
        }
        return new NoiseColumn(minBuild, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, @NotNull RandomState randomState, @NotNull BlockPos pos) {
        DimensionGenerationConfig config = configuration;
        list.add("Useless Dimension - " + getDebugName());
        list.add("Height: Y=" + getBottomY(config) + " ~ " + getTopY(config));
        list.add("Layers: " + config.platformLayers());
        list.add("Boundary: " + config.boundaryIntervalX() + " x " + config.boundaryIntervalZ());
        list.add("Road: width=" + config.roadWidth() + ", preset=" + config.roadPreset());
        list.add("Center marker: " + config.centerMarkerEnabled());
    }

    private BlockState platformState(DimensionGenerationConfig config, int localX, int localZ) {
        return getPlatformBlockState(config, localX, localZ);
    }

    private List<Integer> surfaceLevels(DimensionGenerationConfig config, int localX, int localZ,
                                        int minY, int maxY) {
        List<Integer> surfaces = new ArrayList<>();
        int top = getTopY(config);
        if (top >= minY && top < maxY
                && !platformState(config, localX, localZ).isAir()) {
            surfaces.add(top);
        }
        return surfaces;
    }

    private int highestSurface(DimensionGenerationConfig config, int localX, int localZ,
                               int minY, int maxY) {
        List<Integer> surfaces = surfaceLevels(config, localX, localZ, minY, maxY);
        return surfaces.isEmpty() ? Integer.MIN_VALUE : surfaces.get(surfaces.size() - 1);
    }

    private BlockState surfaceDecoration(DimensionGenerationConfig config, int chunkX, int chunkZ,
                                         int localX, int localZ) {
        int intervalX = config.boundaryIntervalX();
        int intervalZ = config.boundaryIntervalZ();
        int roadWidth = config.roadWidth();
        if (intervalX <= 0 && intervalZ <= 0) return null;

        int periodX = intervalX > 0 ? intervalX + roadWidth : 0;
        int periodZ = intervalZ > 0 ? intervalZ + roadWidth : 0;
        int chunkModX = periodX > 0 ? mod(chunkX, periodX) : 0;
        int chunkModZ = periodZ > 0 ? mod(chunkZ, periodZ) : 0;
        boolean roadX = roadWidth > 0 && intervalX > 0 && chunkModX >= intervalX;
        boolean roadZ = roadWidth > 0 && intervalZ > 0 && chunkModZ >= intervalZ;
        int roadStartBoundaryWidth = getRoadStartBoundaryWidth();
        int roadCenterLineWidth = getRoadCenterLineWidth();
        int worldX = chunkX * 16 + localX;
        int worldZ = chunkZ * 16 + localZ;

        if (roadX || roadZ) {
            int roadWidthBlocks = roadWidth * 16 - roadStartBoundaryWidth;
            if (roadX && roadZ) {
                int rawOffsetX = (chunkModX - intervalX) * 16 + localX;
                int rawOffsetZ = (chunkModZ - intervalZ) * 16 + localZ;
                int offsetX = rawOffsetX - roadStartBoundaryWidth;
                int offsetZ = rawOffsetZ - roadStartBoundaryWidth;
                if (offsetX < 0 || offsetZ < 0) {
                    if (!useUnshiftedRoadIntersectionLayout()) {
                        return boundaryBlockState(config, worldX, worldZ);
                    }
                    // The shared negative corner is still the boundary tile;
                    // only the adjacent tiles belong to the road edge.
                    if (rawOffsetX < roadStartBoundaryWidth
                            && rawOffsetZ < roadStartBoundaryWidth) {
                        return boundaryBlockState(config, worldX, worldZ);
                    }
                }
                int intersectionOffsetX = useUnshiftedRoadIntersectionLayout() ? rawOffsetX : offsetX;
                int intersectionOffsetZ = useUnshiftedRoadIntersectionLayout() ? rawOffsetZ : offsetZ;
                int intersectionWidth = useUnshiftedRoadIntersectionLayout()
                        ? roadWidth * 16 : roadWidthBlocks;
                boolean onIntersectionEdgeX = useUnshiftedRoadIntersectionLayout()
                        ? rawOffsetX <= roadStartBoundaryWidth || rawOffsetX == intersectionWidth - 1
                        : intersectionOffsetX == 0 || intersectionOffsetX == intersectionWidth - 1;
                boolean onIntersectionEdgeZ = useUnshiftedRoadIntersectionLayout()
                        ? rawOffsetZ <= roadStartBoundaryWidth || rawOffsetZ == intersectionWidth - 1
                        : intersectionOffsetZ == 0 || intersectionOffsetZ == intersectionWidth - 1;
                if (config.roadPreset() == DimensionGenerationConfig.RoadPreset.ROAD
                        && onIntersectionEdgeX && onIntersectionEdgeZ) {
                    return config.roadBlockB().defaultBlockState();
                }
                return config.roadBlockA().defaultBlockState();
            }
            if (roadX) {
                int offset = (chunkModX - intervalX) * 16 + localX - roadStartBoundaryWidth;
                if (offset < 0) return boundaryBlockState(config, worldX, worldZ);
                return roadBlockState(config, offset, worldZ, roadWidthBlocks, roadCenterLineWidth);
            }
            int offset = (chunkModZ - intervalZ) * 16 + localZ - roadStartBoundaryWidth;
            if (offset < 0) return boundaryBlockState(config, worldX, worldZ);
            return roadBlockState(config, offset, worldX, roadWidthBlocks, roadCenterLineWidth);
        }

        // Boundary strips belong to the road layout. A zero-width road means
        // that neither the road nor its boundary should be generated.
        if (roadWidth > 0) {
            boolean boundaryX = isBoundaryChunk(chunkX, intervalX, roadWidth);
            boolean previousBoundaryX = roadStartBoundaryWidth == 0
                    && isPreviousBoundaryChunk(chunkX, intervalX, roadWidth);
            boolean boundaryZ = isBoundaryChunk(chunkZ, intervalZ, roadWidth);
            boolean previousBoundaryZ = roadStartBoundaryWidth == 0
                    && isPreviousBoundaryChunk(chunkZ, intervalZ, roadWidth);
            if ((boundaryX && localX == 0) || (previousBoundaryX && localX == 15)
                    || (boundaryZ && localZ == 0) || (previousBoundaryZ && localZ == 15)) {
                return boundaryBlockState(config, worldX, worldZ);
            }
        }

        if (config.centerMarkerEnabled() && intervalX > 0 && intervalZ > 0) {
            int periodBlocksX = periodX * 16;
            int periodBlocksZ = periodZ * 16;
            int areaWidthX = intervalX * 16;
            int areaWidthZ = intervalZ * 16;
            int centerX = intervalX * 8;
            int centerZ = intervalZ * 8;
            int areaX = mod(chunkX * 16 + localX, periodBlocksX);
            int areaZ = mod(chunkZ * 16 + localZ, periodBlocksZ);
            if (areaX < areaWidthX && areaZ < areaWidthZ
                    && isCenterMarkerPosition(areaX, areaZ, centerX, centerZ)) {
                return config.centerMarkerBlock().defaultBlockState();
            }
        }
        return null;
    }

    private BlockState roadBlockState(DimensionGenerationConfig config, int offsetInRoad,
                                      int alongRoad, int roadWidthBlocks, int centerLineWidth) {
        if (config.roadPreset() == DimensionGenerationConfig.RoadPreset.SOLID) {
            return config.roadBlockA().defaultBlockState();
        }
        if (offsetInRoad == 0 || offsetInRoad == roadWidthBlocks - 1) {
            return config.roadBlockB().defaultBlockState();
        }
        if (roadWidthBlocks >= 4) {
            int center = roadWidthBlocks / 2;
            boolean onCenterLine = centerLineWidth == 1
                    ? offsetInRoad == center
                    : offsetInRoad == center || offsetInRoad == center - 1;
            if (onCenterLine
                    && mod(alongRoad + 2, 8) < 4) {
                return config.roadBlockC().defaultBlockState();
            }
        }
        return config.roadBlockA().defaultBlockState();
    }

    private BlockState boundaryBlockState(DimensionGenerationConfig config, int worldX, int worldZ) {
        return (((worldX + worldZ) & 1) == 0
                ? config.boundaryBlockA() : config.boundaryBlockB()).defaultBlockState();
    }

    private boolean isBoundaryChunk(int chunk, int interval, int roadWidth) {
        if (interval <= 0) return false;
        return mod(chunk, interval + roadWidth) == 0;
    }

    private boolean isPreviousBoundaryChunk(int chunk, int interval, int roadWidth) {
        if (interval <= 0) return false;
        int period = interval + roadWidth;
        if (roadWidth > 0) return mod(chunk, period) == interval - 1;
        return mod(chunk + 1, period) == 0;
    }

    private static int mod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static int clampY(int y, int minY, int maxY) {
        return Math.max(minY, Math.min(maxY - 1, y));
    }
}
