package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.MapCodec;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.world.teleport.AbstractDimensionTeleporter;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter2;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter3;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class UselessDimensions {

    public static final ResourceKey<Level> USELESSDIM_KEY = key("uselessdim");
    public static final ResourceKey<Level> USELESSDIM_2_KEY = key("uselessdim2");
    public static final ResourceKey<Level> USELESSDIM_3_KEY = key("uselessdim3");

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UselessMod.MODID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN =
            CHUNK_GENERATORS.register("uselessdim_gen", () -> UselessDimGen.CODEC);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN_2 =
            CHUNK_GENERATORS.register("uselessdim_gen_2", () -> UselessDimGen2.CODEC);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN_3 =
            CHUNK_GENERATORS.register("uselessdim_gen_3", () -> UselessDimGen3.CODEC);

    public static boolean isUselessDimension(ResourceKey<Level> dimension) {
        return USELESSDIM_KEY.equals(dimension)
                || USELESSDIM_2_KEY.equals(dimension)
                || USELESSDIM_3_KEY.equals(dimension);
    }

    public static AbstractDimensionTeleporter teleporterFor(ResourceKey<Level> dimension) {
        if (USELESSDIM_KEY.equals(dimension)) return new UselessDimTeleporter();
        if (USELESSDIM_2_KEY.equals(dimension)) return new UselessDimTeleporter2();
        if (USELESSDIM_3_KEY.equals(dimension)) return new UselessDimTeleporter3();
        return null;
    }

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, path));
    }

    public static void init(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}
