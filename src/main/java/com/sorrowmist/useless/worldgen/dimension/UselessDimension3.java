package com.sorrowmist.useless.worldgen.dimension;

import com.mojang.serialization.Codec;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class UselessDimension3 {

    // 只需要注册区块生成器
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CHUNK_GENERATOR, UselessMod.MOD_ID);

    public static final RegistryObject<Codec<UselessDimGen3>> USELESSDIM_GEN_CODEC = 
            CHUNK_GENERATORS.register("uselessdim3_gen", () -> UselessDimGen3.CODEC);

}