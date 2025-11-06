package com.sorrowmist.useless.dimension;

import com.mojang.serialization.MapCodec;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 一个简单的自定义维度地形生成器：
 * 在 Y=-64 放置基岩，
 * 在 Y=-63~5 生成塑料平台（可通过 ConfigManager 自定义材质），
 * 以上为空气。
 */
public class UselessDimGen extends ChunkGenerator {

    public static final MapCodec<UselessDimGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(generator -> generator.biomeSource)
            ).apply(instance, UselessDimGen::new)
    );

    public UselessDimGen(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        // 不进行地形雕刻
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Heightmap oceanFloorMap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surfaceMap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Y=-64 基岩
                chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);

                // 生成地表平台
                for (int y = -63; y <= 5; y++) {
                    BlockState blockState;

                    // 中心 2x2
                    if (x >= 7 && x <= 8 && z >= 7 && z <= 8) {
                        blockState = ConfigManager.getCenterBlock().defaultBlockState();
                    }
                    // 内层 14x14
                    else if (x >= 1 && x <= 14 && z >= 1 && z <= 14) {
                        blockState = ConfigManager.getFillBlock().defaultBlockState();
                    } else {
                        blockState = ConfigManager.getBorderBlock().defaultBlockState();
                    }

                    chunk.setBlockState(pos.set(x, y, z), blockState, false);
                }

                // 空气层
                for (int y = 6; y < 320; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                }

                surfaceMap.update(x, z, 6, Blocks.AIR.defaultBlockState());
                oceanFloorMap.update(x, z, 6, Blocks.AIR.defaultBlockState());
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 不生成自然生物
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,
                                                        RandomState randomState,
                                                        StructureManager structureManager,
                                                        ChunkAccess chunkAccess) {
        // 不生成噪声地形，直接返回现有区块
        return CompletableFuture.completedFuture(chunkAccess);
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap,
                             LevelHeightAccessor level, RandomState randomState) {
        return 70;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];

        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            int index = y - level.getMinBuildHeight();
            if (y == -64) {
                states[index] = Blocks.BEDROCK.defaultBlockState();
            } else if (y >= -63 && y <= 5) {
                states[index] = ConfigManager.getFillBlock().defaultBlockState();
            } else {
                states[index] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        list.add("Useless Dimension - Plastic Platform");
        list.add("边框方块: " + ConfigManager.getBorderBlock());
        list.add("填充方块: " + ConfigManager.getFillBlock());
        list.add("中心方块: " + ConfigManager.getCenterBlock());
    }
}
