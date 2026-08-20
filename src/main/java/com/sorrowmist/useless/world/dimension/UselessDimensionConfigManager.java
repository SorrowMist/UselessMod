package com.sorrowmist.useless.world.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class UselessDimensionConfigManager {
    private UselessDimensionConfigManager() {
    }

    public static boolean isConfigured(MinecraftServer server, ResourceKey<Level> dimension) {
        return UselessDimensionConfigSavedData.get(server).isConfigured(dimension);
    }

    public static DimensionGenerationConfig get(
            MinecraftServer server, ResourceKey<Level> dimension) {
        return UselessDimensionConfigSavedData.get(server).get(dimension)
                .orElseGet(DimensionGenerationConfig::defaults);
    }

    public static DimensionGenerationConfig save(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            DimensionGenerationConfig config,
            boolean firstSetup) {
        UselessDimensionConfigSavedData data = UselessDimensionConfigSavedData.get(server);
        DimensionGenerationConfig applied = firstSetup
                ? data.putIfAbsent(dimension, config)
                : data.put(dimension, config);
        ServerLevel level = server.getLevel(dimension);
        if (level != null) apply(level);
        return applied;
    }

    public static void apply(ServerLevel level) {
        if (level == null) return;
        if (!UselessDimensions.isUselessDimension(level.dimension())) return;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof AbstractPlasticPlatformGenerator platformGenerator) {
            platformGenerator.setConfiguration(get(level.getServer(), level.dimension()));
        }
    }

    public static void applyAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            apply(level);
        }
    }
}
