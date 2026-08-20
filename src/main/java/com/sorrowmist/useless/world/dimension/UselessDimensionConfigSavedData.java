package com.sorrowmist.useless.world.dimension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class UselessDimensionConfigSavedData extends SavedData {
    private static final String FILE_ID = "useless_mod_dimension_generation";
    private static final String DIMENSIONS_TAG = "dimensions";
    private static final Factory<UselessDimensionConfigSavedData> FACTORY =
            new Factory<>(UselessDimensionConfigSavedData::new,
                    UselessDimensionConfigSavedData::load);

    private final Map<ResourceLocation, DimensionGenerationConfig> configurations = new HashMap<>();

    UselessDimensionConfigSavedData() {
    }

    public static UselessDimensionConfigSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<DimensionGenerationConfig> get(ResourceKey<Level> dimension) {
        return Optional.ofNullable(configurations.get(dimension.location()));
    }

    public boolean isConfigured(ResourceKey<Level> dimension) {
        return configurations.containsKey(dimension.location());
    }

    public DimensionGenerationConfig putIfAbsent(
            ResourceKey<Level> dimension, DimensionGenerationConfig config) {
        DimensionGenerationConfig existing = configurations.get(dimension.location());
        if (existing != null) return existing;
        DimensionGenerationConfig normalized = config.normalized();
        configurations.put(dimension.location(), normalized);
        setDirty();
        return normalized;
    }

    public DimensionGenerationConfig put(
            ResourceKey<Level> dimension, DimensionGenerationConfig config) {
        DimensionGenerationConfig normalized = config.normalized();
        configurations.put(dimension.location(), normalized);
        setDirty();
        return normalized;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag dimensions = new CompoundTag();
        configurations.forEach((id, config) -> dimensions.put(id.toString(), config.save(new CompoundTag())));
        tag.put(DIMENSIONS_TAG, dimensions);
        return tag;
    }

    static UselessDimensionConfigSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        UselessDimensionConfigSavedData data = new UselessDimensionConfigSavedData();
        if (!tag.contains(DIMENSIONS_TAG)) return data;

        CompoundTag dimensions = tag.getCompound(DIMENSIONS_TAG);
        for (String key : dimensions.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                data.configurations.put(id, DimensionGenerationConfig.load(dimensions.getCompound(key)));
            }
        }
        return data;
    }
}
