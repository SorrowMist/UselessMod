package com.sorrowmist.useless.api.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumSet;
import java.util.List;

public enum FunctionMode {
    // 强制挖掘
    FORCE_MINING("force_mining", "tooltip.useless_mod.force_mining_mode"),
    // 连锁
    CHAIN_MINING("chain_mining", "tooltip.useless_mod.chain_mining_mode"),
    // 增强连锁
    ENHANCED_CHAIN_MINING("enhanced_chain_mining", "tooltip.useless_mod.enhanced_chain_mining_mode"),

    AE_STORAGE_PRIORITY("ae_storage_priority", "tooltip.useless_mod.ae_storage_priority_mode");

    private static final List<FunctionMode> TOOLTIP_DISPLAY_ORDER = List.of(
            FORCE_MINING,
            CHAIN_MINING
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

    public String getName() {return this.name;}

    /**
     * 获取带颜色的标题组件
     */
    public MutableComponent getTitleComponent() {
        if (this.tooltipKey.isEmpty()) {
            return Component.empty();
        }
        ChatFormatting titleColor = switch (this) {
            case FORCE_MINING -> ChatFormatting.RED;      // 强制标题红色
            case CHAIN_MINING, ENHANCED_CHAIN_MINING -> ChatFormatting.BLUE; // 连锁标题蓝色
            default -> ChatFormatting.GRAY;
        };
        return Component.translatable(this.tooltipKey).withStyle(titleColor);
    }

    /**
     * 获取带颜色的状态组件
     */
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

            case AE_STORAGE_PRIORITY -> Component.empty();
        };
    }
}