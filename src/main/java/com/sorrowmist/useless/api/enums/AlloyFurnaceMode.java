package com.sorrowmist.useless.api.enums;

import com.mojang.serialization.Codec;

public enum AlloyFurnaceMode {
    NORMAL("normal"),
    INSOLATOR("insolator"),
    PRESS("press");

    // 提供给 Codec 的便捷方法
    public static final Codec<AlloyFurnaceMode> CODEC = Codec.STRING.xmap(
            AlloyFurnaceMode::fromString,
            AlloyFurnaceMode::getSerializedName
    );
    private final String serializedName;

    AlloyFurnaceMode(String serializedName) {
        this.serializedName = serializedName;
    }

    // 字符串 → 枚举（用于 codec / 网络）
    public static AlloyFurnaceMode fromString(String name) {
        if (name == null) return NORMAL;
        return switch (name.toLowerCase()) {
            case "insolator" -> INSOLATOR;
            case "press" -> PRESS;
            default -> NORMAL;
        };
    }

    public String getSerializedName() {
        return this.serializedName;
    }
}