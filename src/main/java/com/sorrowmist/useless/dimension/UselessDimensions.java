package com.sorrowmist.useless.dimension;

import com.mojang.serialization.MapCodec;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class UselessDimensions {

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UselessMod.MODID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN =
            CHUNK_GENERATORS.register("uselessdim_gen", () -> UselessDimGen.CODEC);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN_2 =
            CHUNK_GENERATORS.register("uselessdim_gen_2", () -> UselessDimGen2.CODEC);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN_3 =
            CHUNK_GENERATORS.register("uselessdim_gen_3", () -> UselessDimGen3.CODEC);

    public static void init(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}