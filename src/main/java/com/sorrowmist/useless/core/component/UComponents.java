package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WrenchTagEnabledComponent =
            register("wrench_tag_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ConstructionWandEnabledComponent =
            register("construction_wand_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ConstructionWandCoreMode>> ConstructionWandCoreComponent =
            register("construction_wand_core", builder ->
                    builder.persistent(Codec.STRING.xmap(ConstructionWandCoreMode::valueOf, Enum::name))
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeEnum,
                                   buf -> buf.readEnum(ConstructionWandCoreMode.class)
                           ))
            );

    /**
     * 土壤右键模式组件（BeefFarmlandMode）
     * 造化杖同时具备铲子与锄头能力，右键泥土/草方块时谁先生效由此决定：
     * false = 铲子优先（变为草径），true = 锄头优先（变为耕地）。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefFarmlandModeComponent =
            register("beef_farmland_mode", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 「顺手收菜」组件（BeefCropHarvest）
     * true = 右键成熟作物时由造化杖自身执行收获，并将种子保留于耕地（作物重置为 0 龄），
     * 以避免整合包的右键收菜功能将作物连根拔起。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefCropHarvestComponent =
            register("beef_crop_harvest", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 剪刀功能组件（BeefShears）
     * true = 造化杖具备剪刀能力：可对羊、哞菇等 IShearable 实体剪毛/剪掉落，
     * 也向依赖 ItemAbility 的模组暴露 DEFAULT_SHEARS_ACTIONS。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefShearsComponent =
            register("beef_shears", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 打火石功能组件（BeefFlintAndSteel）
     * true = 造化杖具备打火石能力：右键可点燃营火/蜡烛，或在可放置火的位置点火。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefFlintAndSteelComponent =
            register("beef_flint_and_steel", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 自动熔炼组件（AutoSmelt）
     * true = 挖掘产生的掉落物先按原版烹饪配方（熔炉/高炉/烟熏炉）炼一遍再入包。
     * 只影响挖掘掉落，不改变方块本身的行为。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AutoSmeltComponent =
            register("auto_smelt", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
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

    /**
     * AE 连接模式组件（AeNetworkConnect）
     * 开启后右键一台「拥有 AE 网格节点」的机器，把它的节点接入工具绑定无线访问点所在的那张网。
     * 与「顺手收菜 / 时间加速 / 建筑魔杖」互斥（同一时刻仅允许一项占用右键）。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AeNetworkConnectComponent =
            register("ae_network_connect", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 匠心仪式挎包模式（RitualSatchel）
     * 开启后右键已用魔典预览过的五芒星，可直接从绑定的 AE 网络取用方块摆放整座仪式。
     * 仅在安装了 occultism 时才会出现在模式轮盘里。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefRitualSatchelComponent =
            register("beef_ritual_satchel", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ForceKillEnabledComponent =
            register("force_kill_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefTimeAccelerationEnabledComponent =
            register("beef_time_acceleration_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefInvulnerabilityEnabledComponent =
            register("beef_invulnerability_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefAdvancedStealthEnabledComponent =
            register("beef_advanced_stealth_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefCaptureEnabledComponent =
            register("beef_capture_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefMalumSpiritEnabledComponent =
            register("beef_malum_spirit_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefMysticalAgricultureEnabledComponent =
            register("beef_mystical_agriculture_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefBeheadingEnabledComponent =
            register("beef_beheading_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefTeleportEnabledComponent =
            register("beef_teleport_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 范围伤害组件（BeefAoeDamage）
     * 用于在物品上存储是否启用范围伤害（布尔类型）
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefAoeDamageEnabledComponent =
            register("beef_aoe_damage_enabled", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 范围磁力吸附组件（BeefMagnet）
     * 用于在物品上存储是否启用击杀后的范围磁力吸附（布尔类型）
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefMagnetEnabledComponent =
            register("beef_magnet_enabled", builder ->
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

    /**
     * 熔炉数据组件（FurnaceData）
     * 用于存储高级合金炉的完整状态到物品中
     * 包含阶级、物品栏、流体、能量等所有数据
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FurnaceDataComponent>> FURNACE_DATA =
            register("furnace_data", builder -> builder
                    .persistent(FurnaceDataComponent.CODEC)
                    .networkSynchronized(FurnaceDataComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OmniversalPatternData>> OMNIVERSAL_PATTERN_DATA =
            register("omniversal_pattern_data", builder -> builder
                    .persistent(OmniversalPatternData.CODEC)
                    .networkSynchronized(OmniversalPatternData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PatternConverterData>> PATTERN_CONVERTER_DATA =
            register("pattern_converter_data", builder -> builder
                    .persistent(PatternConverterData.CODEC)
                    .networkSynchronized(PatternConverterData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> PATTERN_CONVERTER_LINK_TARGET = register(
            // Keep the serialized ID so existing converter links remain valid.
            "mold_hub_link_target",
            builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MultiblockRecoveryData>> MULTIBLOCK_RECOVERY_DATA =
            register("multiblock_recovery_data", builder -> builder
                    .persistent(MultiblockRecoveryData.CODEC)
                    .networkSynchronized(MultiblockRecoveryData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MultiblockPartData>> MULTIBLOCK_PART_DATA =
            register("multiblock_part_data", builder -> builder
                    .persistent(MultiblockPartData.CODEC)
                    .networkSynchronized(MultiblockPartData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ExternalInventoryReference>> EXTERNAL_INVENTORY_REFERENCE =
            register("external_inventory_reference", builder -> builder
                    .persistent(ExternalInventoryReference.CODEC)
                    .networkSynchronized(ExternalInventoryReference.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PassiveHatchSettings>> PASSIVE_HATCH_SETTINGS =
            register("passive_hatch_settings", builder -> builder
                    .persistent(PassiveHatchSettings.CODEC)
                    .networkSynchronized(PassiveHatchSettings.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SMART_DOUBLING_OPERATIONS =
            register("smart_doubling_operations", builder -> builder
                    .persistent(Codec.LONG)
                    .networkSynchronized(StreamCodec.of(
                            FriendlyByteBuf::writeLong,
                            FriendlyByteBuf::readLong)));
    /** The Occultism pentacles recorded on an imprinted ritual blueprint. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RitualBlueprintPentacles>> RITUAL_BLUEPRINT_PENTACLE =
            register("ritual_blueprint_pentacle", builder -> builder
                    .persistent(RitualBlueprintPentacles.CODEC)
                    .networkSynchronized(RitualBlueprintPentacles.STREAM_CODEC));

    /**
     * 催熟模式组件（BeefRipen）
     * true = 右键时一键催熟目标：可骨粉方块循环施加骨粉直到长满，
     * 幼年动物直接催至成年；潜行右键把这次交互让给其它模组。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefRipenComponent =
            register("beef_ripen", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
                           ))
            );

    /**
     * 连点模式组件（BeefAutoClick）
     * true = 客户端手持造化杖时以最快速度重复触发右键，再次按下绑定按键关闭。
     * 只存状态，真正的连点循环在客户端 {@code BeefAutoClicker} 中执行。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BeefAutoClickComponent =
            register("beef_auto_click", builder ->
                    builder.persistent(Codec.BOOL)
                           .networkSynchronized(StreamCodec.of(
                                   FriendlyByteBuf::writeBoolean,
                                   FriendlyByteBuf::readBoolean
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
