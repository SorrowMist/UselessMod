package com.sorrowmist.useless.dimension;

import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 所有塑料平台维度生成器的抽象父类
 * 高度、层数、基岩等配置统一由 ConfigManager 控制
 * 子类只需实现平台外观样式和调试名称
 */
public abstract class AbstractPlasticPlatformGenerator extends ChunkGenerator {
    AbstractPlasticPlatformGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /**
     * 子类必须实现：返回该位置在平台层应该放置的方块状态
     */
    protected abstract BlockState getPlatformBlockState(int chunkX, int chunkZ);

    /**
     * 子类必须实现：返回调试屏幕显示的名称（用于区分不同样式）
     */
    protected abstract String getDebugName();

    /**
     * 平台最顶层 Y（包含）
     */
    private int getTopY() {
        return ConfigManager.getPlatformStartY() + ConfigManager.getPlatformLayers();
    }

    /**
     * 平台最底层 Y（基岩层或平台起始层）
     */
    private int getBottomY() {
        return ConfigManager.getPlatformStartY();
    }

    /**
     * 是否生成基岩底层
     */
    private boolean shouldGenerateBedrock() {
        return ConfigManager.shouldGenerateBedrock();
    }

    @Override public void applyCarvers(@NotNull WorldGenRegion region,
                                       long seed,
                                       @NotNull RandomState randomState,
                                       @NotNull BiomeManager biomeManager,
                                       @NotNull StructureManager structureManager,
                                       @NotNull ChunkAccess chunk,
                                       @NotNull GenerationStep.Carving step) {
        // 不生成洞穴、峡谷等雕刻
    }

    @Override
    public void buildSurface(@NotNull WorldGenRegion region, @NotNull StructureManager structureManager,
                             @NotNull RandomState randomState, @NotNull ChunkAccess chunk) {

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        int topY = this.getTopY();
        int bottomY = this.getBottomY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                // 可选：基岩底层
                if (this.shouldGenerateBedrock()) {
                    chunk.setBlockState(pos.set(x, bottomY, z), Blocks.BEDROCK.defaultBlockState(), false);
                }

                // 平台主体层（从 bottomY + 1 到 topY）
                for (int y = bottomY + 1; y <= topY; y++) {
                    BlockState state = this.getPlatformBlockState(x, z);
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                }

                // 上方全部清为空气
                for (int y = topY + 1; y < 320; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                }

                // 正确更新高度图（非常重要！）
                BlockState surfaceState = ConfigManager.getFillBlock().defaultBlockState();
                worldSurface.update(x, z, topY, surfaceState);
                oceanFloor.update(x, z, topY, surfaceState);
            }
        }
    }

    @Override public void spawnOriginalMobs(@NotNull WorldGenRegion region) {
        // 不生成原始生物群系怪物
    }

    @Override public int getGenDepth() {return 384;}

    @Override
    public @NotNull CompletableFuture<ChunkAccess> fillFromNoise(@NotNull Blender blender,
                                                                 @NotNull RandomState randomState,
                                                                 @NotNull StructureManager structureManager,
                                                                 @NotNull ChunkAccess chunk) {
        // 不使用噪声生成，直接返回空区块
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getSeaLevel() {return 0;}

    @Override public int getMinY() {return this.getBottomY();}

    @Override
    public int getBaseHeight(int x, int z, @NotNull Heightmap.Types heightmap,
                             @NotNull LevelHeightAccessor level, @NotNull RandomState randomState) {
        return this.getTopY();
    }

    @Override
    public @NotNull NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor level,
                                              @NotNull RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        int minBuild = level.getMinBuildHeight();
        int bottomY = this.getBottomY();
        int topY = this.getTopY();

        for (int y = minBuild; y < level.getMaxBuildHeight(); y++) {
            int idx = y - minBuild;
            if (y == bottomY && this.shouldGenerateBedrock()) {
                column[idx] = Blocks.BEDROCK.defaultBlockState();
            } else if (y > bottomY && y <= topY) {
                column[idx] = ConfigManager.getFillBlock().defaultBlockState();
            } else {
                column[idx] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(minBuild, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, @NotNull RandomState randomState, @NotNull BlockPos pos) {
        list.add("Useless Dimension - " + this.getDebugName());
        list.add("高度: Y=" + this.getBottomY() + " ~ " + this.getTopY());
        list.add("基岩: " + this.shouldGenerateBedrock());
        list.add("边框方块: " + ConfigManager.getBorderBlock());
        list.add("填充方块: " + ConfigManager.getFillBlock());
        list.add("中心方块: " + ConfigManager.getCenterBlock());
    }
}