package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public final class UComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, UselessMod.MODID);

    /**
     * 附魔模式组件（EnchantMode）
     * 用于在物品上存储当前的附魔模式（枚举类型 EnchantMode）
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnchantMode>> EnchantModeComponent =
            register("enchant_mode", builder ->
                    builder
                            // 持久化存储：使用字符串 Codec，将枚举转换为字符串保存（保存到 NBT/文件时）
                            // valueOf 用于从字符串转为枚举，Enum::name 用于从枚举转为字符串
                            .persistent(Codec.STRING.xmap(EnchantMode::valueOf, Enum::name))
                            // 网络同步：客户端与服务端同步时使用枚举的原生读写方式
                            .networkSynchronized(StreamCodec.of(
                                    FriendlyByteBuf::writeEnum,                     // 写入枚举
                                    buf -> buf.readEnum(EnchantMode.class)          // 读取枚举
                            ))
            );

    /**
     * 当前工具类型组件（CurrentToolType）
     * 用于在物品上存储当前选中的工具类型（枚举类型 ToolTypeMode）
     * 例如多功能工具在不同模式（镐、斧、铲等）之间切换时使用
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolTypeMode>> CurrentToolTypeComponent =
            register("tool_type", builder ->
                    builder.persistent(Codec.STRING.xmap(ToolTypeMode::valueOf, Enum::name))
                            .networkSynchronized(StreamCodec.of(
                                    FriendlyByteBuf::writeEnum,
                                    buf -> buf.readEnum(ToolTypeMode.class)
                            ))
            );

    /**
     * 增强连锁挖矿模式组件（EnhancedChainMiningMode）
     * 用于在物品上存储是否启用增强连锁挖掘（布尔类型）
     * true = 启用增强连锁挖掘，false = 使用普通连锁挖掘
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> EnhancedChainMiningComponent =
            register("chain_mining", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 强制挖掘组件（ForceMining）
     * 用于在物品上存储是否启用强制挖掘功能（布尔类型）
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ForceMiningComponent =
            register("force_mining", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * AE存储优先组件（AEStoragePriority）
     * 用于在物品上存储是否启用AE存储优先功能（布尔类型）
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AEStoragePriorityComponent =
            register("ae_storage_priority", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> WIRELESS_LINK_TARGET = register(
            "wireless_link_target",
            builder ->
                    builder.persistent(GlobalPos.CODEC)
                           .networkSynchronized(GlobalPos.STREAM_CODEC)
    );

    // 私有构造器，防止外部实例化（该类仅用于注册静态组件）
    private UComponents() {}

    /**
     * 通用注册方法
     *
     * @param name            组件的注册名称（资源位置路径部分）
     * @param builderOperator 对 DataComponentType.Builder 的操作函数
     * @param <T>             组件存储的数据类型
     * @return DeferredHolder，用于延迟获取已注册的 DataComponentType
     */
    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        // 通过 DeferredRegister 注册，实际构建在模组加载时进行
        return DATA_COMPONENTS.register(name, () -> builderOperator.apply(DataComponentType.builder()).build());
    }

    /**
     * 初始化方法：在模组事件总线上注册 DeferredRegister
     * 通常在模组主类的构造函数中调用：UComponents.init(modEventBus);
     * @param modEventBus 模组的事件总线（通常是 MOD 事件总线）
     */
    public static void init(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}