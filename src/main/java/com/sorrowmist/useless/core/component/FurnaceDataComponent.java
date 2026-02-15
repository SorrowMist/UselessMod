package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.UnaryOperator;

/**
 * 熔炉数据组件 - 用于存储高级合金炉的完整状态到物品中
 * 包含阶级、物品栏、流体、能量等所有数据
 */
public record FurnaceDataComponent(int tier, CompoundTag data) {
    
    // Codec 用于持久化存储
    public static final Codec<FurnaceDataComponent> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("tier").forGetter(FurnaceDataComponent::tier),
            CompoundTag.CODEC.fieldOf("data").forGetter(FurnaceDataComponent::data)
        ).apply(instance, FurnaceDataComponent::new)
    );
    
    // StreamCodec 用于网络同步
    public static final StreamCodec<RegistryFriendlyByteBuf, FurnaceDataComponent> STREAM_CODEC = 
        StreamCodec.composite(
                ByteBufCodecs.INT,
                FurnaceDataComponent::tier,
                ByteBufCodecs.COMPOUND_TAG,
                FurnaceDataComponent::data,
                FurnaceDataComponent::new
        );

    public static UnaryOperator<DataComponentType.Builder<FurnaceDataComponent>> builder() {
        return builder -> builder
            .persistent(CODEC)
            .networkSynchronized(STREAM_CODEC);
    }

    public static FurnaceDataComponent empty() {
        return new FurnaceDataComponent(0, new CompoundTag());
    }
}
