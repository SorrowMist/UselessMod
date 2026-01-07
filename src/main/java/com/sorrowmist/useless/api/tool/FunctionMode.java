package com.sorrowmist.useless.api.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumSet;
import java.util.List;

public enum FunctionMode {
    // 默认连锁（内部用，不显示在轮盘）
    CHAIN_MINING("chain_mining", ""),

    // 增强连锁（轮盘显示这个）
    ENHANCED_CHAIN_MINING("enhanced_chain_mining", "tooltip.useless_mod.enhanced_chain_mining_mode"),

    // 强制挖掘
    FORCE_MINING("force_mining", "tooltip.useless_mod.force_mining_mode"),

    // AE存储优先
    AE_STORAGE_PRIORITY("ae_storage_priority", "tooltip.useless_mod.ae_storage_priority_mode");

    // 轮盘右侧只显示这三个（顺序可调）
    private static final List<FunctionMode> TOOLTIP_DISPLAY_ORDER = List.of(
            ENHANCED_CHAIN_MINING,   // 第一：增强连锁（开关）
            FORCE_MINING,            // 第二：强制挖掘
            AE_STORAGE_PRIORITY      // 第三：AE存储优先
    );

    private final String name;
    private final String tooltipKey;

    FunctionMode(String name, String tooltipKey) {
        this.name = name;
        this.tooltipKey = tooltipKey;
    }

    public static List<FunctionMode> getTooltipDisplayGroups() {
        return TOOLTIP_DISPLAY_ORDER;
    }

    public static Component getStatusForGroup(FunctionMode groupLeader, EnumSet<FunctionMode> activeModes) {
        return groupLeader.getStatusComponent(activeModes);
    }

    private Component getStatusComponent(EnumSet<FunctionMode> activeModes) {
        return switch (this) {
            case FORCE_MINING -> Component.translatable(
                    activeModes.contains(FORCE_MINING)
                            ? "tooltip.useless_mod.enable"    // 开启
                            : "tooltip.useless_mod.disable"   // 关闭
            ).withStyle(activeModes.contains(FORCE_MINING)
                                ? ChatFormatting.GREEN
                                : ChatFormatting.GRAY);

            case CHAIN_MINING, ENHANCED_CHAIN_MINING -> {
                if (activeModes.contains(ENHANCED_CHAIN_MINING)) {
                    // 增强状态
                    yield Component.translatable("tooltip.useless_mod.chain_mining_mode.enhanced")
                                   .withStyle(ChatFormatting.AQUA);
                } else if (activeModes.contains(CHAIN_MINING)) {
                    // 普通连锁（默认连锁）
                    yield Component.translatable("tooltip.useless_mod.chain_mining_mode.default")
                                   .withStyle(ChatFormatting.GREEN);
                } else {
                    // 完全关闭
                    yield Component.translatable("tooltip.useless_mod.disable")
                                   .withStyle(ChatFormatting.GRAY);
                }
            }

            case AE_STORAGE_PRIORITY -> Component.translatable(
                    activeModes.contains(AE_STORAGE_PRIORITY)
                            ? "tooltip.useless_mod.enable"    // 开启
                            : "tooltip.useless_mod.disable"   // 关闭
            ).withStyle(activeModes.contains(AE_STORAGE_PRIORITY)
                                ? ChatFormatting.GREEN
                                : ChatFormatting.GRAY);
        };
    }

    public MutableComponent getTitleComponent() {
        ChatFormatting color = switch (this) {
            case ENHANCED_CHAIN_MINING -> ChatFormatting.AQUA;
            case FORCE_MINING -> ChatFormatting.RED;
            case AE_STORAGE_PRIORITY -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
        return this.tooltipKey.isEmpty()
                ? Component.literal(this.name)
                : Component.translatable(this.tooltipKey).withStyle(color);
    }

    public String getName() {
        return this.name;
    }

    public Component getTooltip() {return Component.translatable(this.tooltipKey);}
}