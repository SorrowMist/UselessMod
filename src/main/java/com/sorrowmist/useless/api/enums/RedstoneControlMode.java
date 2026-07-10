package com.sorrowmist.useless.api.enums;

/**
 * 红石控制模式。
 * <p>
 * 控制合金炉是否响应红石信号来启用/禁用运行。
 */
public enum RedstoneControlMode {
    /** 无红石控制：始终运行 */
    DISABLED(172),
    /** 高电平激活：有红石信号时运行 */
    HIGH_ACTIVE(156),
    /** 低电平激活：无红石信号时运行 */
    LOW_ACTIVE(140);

    private final int overlayU;

    RedstoneControlMode(int overlayU) {
        this.overlayU = overlayU;
    }

    /** 获取UI覆盖图纹理U坐标（V=265），-1表示无覆盖 */
    public int getOverlayU() {
        return this.overlayU;
    }

    public boolean hasOverlay() {
        return this.overlayU >= 0;
    }

    public RedstoneControlMode next() {
        RedstoneControlMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static RedstoneControlMode byIndex(int index) {
        RedstoneControlMode[] values = values();
        if (index < 0 || index >= values.length) return DISABLED;
        return values[index];
    }

    /**
     * 判断给定红石信号强度下是否应该启用。
     *
     * @param hasRedstoneSignal 是否有红石信号
     * @return 是否允许运行
     */
    public boolean shouldRun(boolean hasRedstoneSignal) {
        return switch (this) {
            case DISABLED -> true;
            case HIGH_ACTIVE -> hasRedstoneSignal;
            case LOW_ACTIVE -> !hasRedstoneSignal;
        };
    }
}
