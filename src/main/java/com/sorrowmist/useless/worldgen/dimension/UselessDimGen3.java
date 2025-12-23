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

public class UselessDimGen3 extends ChunkGenerator {

    // 注册Codec用于数据序列化
    public static final Codec<UselessDimGen3> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
            ).apply(instance, UselessDimGen3::new)
    );

    public UselessDimGen3(BiomeSource biomeSource) {
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

        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            int index = y - level.getMinBuildHeight();

            if (y == -64) {
                states[index] = Blocks.BEDROCK.defaultBlockState();
            } else if (y >= -63 && y <= 5) {
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

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 设置基岩底层 (Y=-64)
                chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);

                // 生成69层平台 (Y=-63 到 Y=5)
                for (int y = -63; y <= 5; y++) {
                    BlockState blockState;

                    // 判断是否在坐标(8,8)的中心方块
                    if (x == 8 && z == 8) {
                        blockState = ConfigManager.getCenterBlock().defaultBlockState();
                    }
                    // 判断是否在边框3格范围内 (x < 2 或 x > 14 或 z < 4 或 z > 14)
                    else if (x < 2 || x > 14 || z < 2 || z > 14) {
                        blockState = ConfigManager.getBorderBlock().defaultBlockState();
                    } else {
                        blockState = ConfigManager.getFillBlock().defaultBlockState();
                    }

                    chunk.setBlockState(pos.set(x, y, z), blockState, false);
                }

                // Y=6及以上设置为空气
                for (int y = 6; y < 320; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                }

                surfaceMap.update(x, z, 6, Blocks.AIR.defaultBlockState());
                oceanFloorMap.update(x, z, 6, Blocks.AIR.defaultBlockState());
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor level, RandomState randomState) {
        return 70;
    }

    @Override
    public int getMinY() {
        return -64;
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