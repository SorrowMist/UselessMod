package com.sorrowmist.useless.api.component;

import com.mojang.serialization.Codec;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumSet;
import java.util.function.UnaryOperator;

public final class UComponents {
    // 创建一个 DeferredRegister，用于延迟注册 DataComponentType 到 Minecraft 的注册表中
    // 注册表键为 Registries.DATA_COMPONENT_TYPE，模组 ID 为 UselessMod.MODID
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
     * 功能模式集合组件（FunctionModes）
     * 用于在物品上存储一组激活的功能模式（EnumSet<FunctionMode>）
     * 允许物品同时拥有多个功能模式
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnumSet<FunctionMode>>> FunctionModesComponent =
            register("function_modes", builder -> builder
                    // 持久化存储：先将 EnumSet 转为字符串列表，再保存
                    .persistent(
                            Codec.STRING.listOf().xmap(
                                    list -> {
                                        // 从字符串列表恢复 EnumSet
                                        EnumSet<FunctionMode> set = EnumSet.noneOf(FunctionMode.class);
                                        for (String s : list) {
                                            set.add(FunctionMode.valueOf(s));
                                        }
                                        return set;
                                    },
                                    set -> set.stream()
                                              .map(Enum::name)
                                              .toList()                     // 将 EnumSet 转为字符串列表
                            )
                    )
                    // 网络同步：手动写入集合大小 + 每个枚举值
                    .networkSynchronized(StreamCodec.of(
                            (buf, set) -> {
                                buf.writeVarInt(set.size());                    // 先写集合大小（可变整数）
                                for (FunctionMode mode : set) {
                                    buf.writeEnum(mode);                        // 逐个写枚举
                                }
                            },
                            buf -> {
                                int size = buf.readVarInt();                     // 读取集合大小
                                EnumSet<FunctionMode> set = EnumSet.noneOf(FunctionMode.class);
                                for (int i = 0; i < size; i++) {
                                    set.add(buf.readEnum(FunctionMode.class));  // 逐个读取枚举并加入集合
                                }
                                return set;
                            }
                    ))
            );

    /**
     * 当前工具类型组件（CurrentToolType）
     * 用于在物品上存储当前选中的工具类型（枚举类型 ToolTypeMode）
     * 例如多功能工具在不同模式（镐、斧、铲等）之间切换时使用
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolTypeMode>> CurrentToolTypeComponent =
            register("current_tool_type", builder ->
                    builder
                            // 持久化存储：同样使用字符串保存枚举值
                            .persistent(Codec.STRING.xmap(ToolTypeMode::valueOf, Enum::name))
                            // 网络同步：直接使用枚举的读写方法
                            .networkSynchronized(StreamCodec.of(
                                    FriendlyByteBuf::writeEnum,
                                    buf -> buf.readEnum(ToolTypeMode.class)
                            ))
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