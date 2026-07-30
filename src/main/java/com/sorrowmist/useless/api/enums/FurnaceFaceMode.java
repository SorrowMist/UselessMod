package com.sorrowmist.useless.api.enums;

/**
 * 高级合金炉每个面的输入输出模式。
 * <p>
 * 每次点击GUI对应区域时在这些状态间循环，用于控制外部物流手段
 * （漏斗、管道等）是否允许从该面输入/输出物品和流体。
 */
public enum FurnaceFaceMode {
    /** 禁止输入输出，UI无变动 */
    DISABLED(-1),
    /** 原材料输入：允许外部向输入槽/输入流体槽输入 */
    MATERIAL_INPUT(0),
    /** 原材料输出：允许外部从输出槽/输出流体槽抽取 */
    MATERIAL_OUTPUT(12),
    /** 原材料输入和输出 */
    MATERIAL_INPUT_OUTPUT(24),
    /** 催化剂输入：允许外部向催化剂槽输入 */
    CATALYST_INPUT(36),
    /** 模具输入：允许外部向模具槽输入 */
    MOLD_INPUT(48);

    // UI覆盖图在纹理中的U坐标（V固定为265，尺寸11*12），-1表示无覆盖
    private final int overlayU;

    FurnaceFaceMode(int overlayU) {
        this.overlayU = overlayU;
    }

    /**
     * 获取该模式在纹理中的覆盖图U坐标（V=265）。
     *
     * @return U坐标，-1表示无覆盖（DISABLED）
     */
    public int getOverlayU() {
        return this.overlayU;
    }

    public boolean hasOverlay() {
        return this.overlayU >= 0;
    }

    /** 是否允许原材料输入（物品输入槽/输入流体槽） */
    public boolean allowsMaterialInput() {
        return this == MATERIAL_INPUT || this == MATERIAL_INPUT_OUTPUT;
    }

    /** 是否允许原材料输出（物品输出槽/输出流体槽） */
    public boolean allowsMaterialOutput() {
        return this == MATERIAL_OUTPUT || this == MATERIAL_INPUT_OUTPUT;
    }

    /** 是否允许催化剂输入 */
    public boolean allowsCatalystInput() {
        return this == CATALYST_INPUT;
    }

    /** 是否允许模具输入 */
    public boolean allowsMoldInput() {
        return this == MOLD_INPUT;
    }

    /** 是否允许任何交互（非禁止） */
    public boolean allowsAny() {
        return this != DISABLED;
    }

    /**
     * 循环到下一个模式。
     */
    public FurnaceFaceMode next() {
        FurnaceFaceMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public FurnaceFaceMode previous() {
        FurnaceFaceMode[] values = values();
        return values[(this.ordinal() - 1 + values.length) % values.length];
    }

    public static FurnaceFaceMode byIndex(int index) {
        FurnaceFaceMode[] values = values();
        if (index < 0 || index >= values.length) return DISABLED;
        return values[index];
    }
}
