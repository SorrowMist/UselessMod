package com.sorrowmist.useless.dimension;

import com.mojang.serialization.MapCodec;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class UselessDimension {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UselessMod.MODID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> USELESSDIM_GEN_CODEC =
            CHUNK_GENERATORS.register("uselessdim_gen", () -> UselessDimGen.CODEC);
}
