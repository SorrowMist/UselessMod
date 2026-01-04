package com.sorrowmist.useless.worldgen.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class UselessDimGen2 extends ChunkGenerator {

    // 注册Codec用于数据序列化
    public static final Codec<UselessDimGen2> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
            ).apply(instance, UselessDimGen2::new)
    );

    public UselessDimGen2(BiomeSource biomeSource) {
        super(biomeSource);
        // 不再需要加载配置，ConfigManager 会处理
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 超平坦世界不生成原始怪物
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];
        int platformLayers = ConfigManager.getPlatformLayers();
        int startY = ConfigManager.getPlatformStartY();
        int maxY = startY + platformLayers;

        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            int index = y - level.getMinBuildHeight();

            if (y == startY && ConfigManager.shouldGenerateBedrock()) {
                states[index] = Blocks.BEDROCK.defaultBlockState();
            } else if (y > startY && y <= maxY) {
                // 使用 ConfigManager 获取填充方块
                states[index] = ConfigManager.getFillBlock().defaultBlockState();
            } else {
                states[index] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Heightmap oceanFloorMap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surfaceMap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int platformLayers = ConfigManager.getPlatformLayers();
        int startY = ConfigManager.getPlatformStartY();
        int maxY = startY + platformLayers;
        int airStartY = maxY + 1;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 设置基岩底层
                if (ConfigManager.shouldGenerateBedrock()) {
                    chunk.setBlockState(pos.set(x, startY, z), Blocks.BEDROCK.defaultBlockState(), false);
                }

                // 生成平台 (Y=startY+1 到 Y=maxY)
                for (int y = startY + 1; y <= maxY; y++) {
                    BlockState blockState;

                    // 判断是否在坐标(8,8)的中心方块
                    if (x == 8 && z == 8) {
                        blockState = ConfigManager.getCenterBlock().defaultBlockState();
                    }
                    // 判断是否在最北边一行(z=0)或最西边一列(x=0)
                    else if (x == 0 || z == 0) {
                        blockState = ConfigManager.getBorderBlock().defaultBlockState();
                    } else {
                        blockState = ConfigManager.getFillBlock().defaultBlockState();
                    }

                    chunk.setBlockState(pos.set(x, y, z), blockState, false);
                }

                // 空气层设置
                for (int y = airStartY; y < 320; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                }

                
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor level, RandomState randomState) {
        return ConfigManager.getPlatformLayers()+1;
    }

    @Override
    public int getMinY() {
        return ConfigManager.getPlatformStartY();
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public void applyCarvers(WorldGenRegion chunkRegion, long seed, RandomState randomState, BiomeManager biomeAccess, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving generationStep) {
        // 不需要洞穴雕刻
    }

    @Override
    public void addDebugScreenInfo(java.util.List<String> list, RandomState randomState, BlockPos pos) {
        list.add("Useless Dimension - Plastic Platform");
        list.add("边框方块: " + ConfigManager.getBorderBlock());
        list.add("填充方块: " + ConfigManager.getFillBlock());
        list.add("中心方块: " + ConfigManager.getCenterBlock());
    }

    // 移除 getConfigPath 方法，因为不再需要
}