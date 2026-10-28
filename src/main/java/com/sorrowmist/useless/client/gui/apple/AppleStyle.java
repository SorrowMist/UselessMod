package com.sorrowmist.useless.client.gui.apple;

/**
 * Apple 控件库唯一的设计令牌表：颜色 / 圆角 / 阴影 / 尺寸全部只在这里定义，
 * 所有 ldl 控件与各界面 buildAppleUi 只引用这里的常量（{@link AppleTex} 的同名常量是别名）。
 *
 * <p>文字规则：一律使用原版正常字号（不缩放），层级只用灰阶（LABEL → SECONDARY → TERTIARY）与加粗表达；
 * 唯一放大的是大数字（整数 2x，任何 GUI 缩放下都清晰）。</p>
 */
public final class AppleStyle {
    private AppleStyle() {}

    // ---- 系统色（iOS 浅色）
    public static final int BLUE = 0xFF007AFF;
    public static final int GREEN = 0xFF34C759;
    public static final int GREEN_TEXT = 0xFF248A3D;
    public static final int ORANGE = 0xFFFF9F0A;
    public static final int ORANGE_TEXT = 0xFFA05A00;
    public static final int RED = 0xFFFF3B30;
    public static final int RED_TEXT = 0xFFD70015;
    public static final int PURPLE = 0xFFAF52DE;
    public static final int TEAL = 0xFF30B0C7;
    public static final int INDIGO = 0xFF5856D6;
    public static final int YELLOW = 0xFFFFCC00;

    // ---- 浅色 tint（图标块 / 徽标底）
    public static final int BLUE_TINT = 0xFFD9E9FF;
    public static final int GREEN_TINT = 0xFFD8F5DF;
    public static final int ORANGE_TINT = 0xFFFFEBD1;
    public static final int RED_TINT = 0xFFFFE0DE;
    public static final int PURPLE_TINT = 0xFFF1E1FA;
    public static final int TEAL_TINT = 0xFFD6F1F6;
    public static final int GRAY_TINT = 0xFFEDEDF0;

    // ---- 文字灰阶
    public static final int LABEL = 0xFF1C1C1E;
    public static final int SECONDARY = 0xFF6E6E73;
    public static final int TERTIARY = 0xFF8E8E93;
    public static final int QUATERNARY = 0xFFB4B4BA;
    public static final int ON_ACCENT = 0xFFFFFFFF;

    // ---- 表面
    public static final int WINDOW_TOP = 0xFFFBFBFD;
    public static final int WINDOW_BOTTOM = 0xFFF2F2F7;
    public static final int WINDOW_EDGE = 0x24000000;
    public static final int CARD = 0xB3FFFFFF;
    public static final int CARD_EDGE = 0xB3FFFFFF;
    public static final int FILL = 0xFFEDEDF0;
    public static final int FILL_HOVER = 0xFFE3E3E8;
    public static final int FILL_PRESSED = 0xFFD8D8DE;
    public static final int TRACK = 0xFFE5E5EA;
    public static final int SEPARATOR = 0x1F3C3C43;
    public static final int SELECTED_ROW = 0xFFDCEBFD;
    public static final int HOVER_ROW = 0x0B000000;
    public static final int SLOT = 0x1F767680;
    public static final int SLOT_HOVER = 0x33767680;
    public static final int SLOT_EDGE = 0x16000000;
    public static final int SLOT_LOCKED = 0xFFE4E4E8;
    public static final int SCRIM = 0x33000000;
    public static final int FOCUS_RING = 0x66007AFF;

    // ---- 按钮
    public static final int BTN_PRIMARY_HOVER = 0xFF0A84FF;
    public static final int BTN_TINTED = 0xFFDCEBFD;
    public static final int BTN_TINTED_HOVER = 0xFFCDE2FC;
    public static final int BTN_DANGER = 0xFFFFE3E1;
    public static final int BTN_DANGER_HOVER = 0xFFFFD6D3;
    public static final int BTN_PLAIN_HOVER = 0x14000000;
    public static final int BTN_PRIMARY_GLOW = 0x40007AFF;
    public static final int BTN_PRIMARY_GLOW_HOVER = 0x55007AFF;

    // ---- 窗口玻璃四角渐变（替代毛玻璃）
    public static final int GLASS_TL = 0xF5E6EFFF, GLASS_TR = 0xF5FBEAF8, GLASS_BL = 0xF5EAF8F3, GLASS_BR = 0xF5FFF1E6;
    public static final int GLASS_RIM = 0xFFFFFFFF;

    // ---- 圆角
    public static final float R_WINDOW = 12f;
    public static final float R_CARD = 8f;
    public static final float R_CONTROL = 5f;
    public static final float R_FIELD = 4.5f;
    public static final float R_ROW = 4.5f;
    public static final float R_SLOT = 4f;
    public static final float R_TILE = 3.5f;
    public static final float CAPSULE = -1f;

    // ---- 阴影 {spread, dy, argb}
    public static final float[] SH_WINDOW = {9f, 3f, 0x3A000000};
    public static final float[] SH_CARD = {3f, 1f, 0x14000000};
    public static final float[] SH_POPOVER = {7f, 2.5f, 0x33000000};
    public static final float[] SH_KNOB = {2.5f, 1f, 0x33000000};
    public static final float[] SH_BUTTON = {2f, 0.8f, 0x1F000000};

    // ---- 尺寸
    public static final int TITLE_BAR = 28;
    public static final int ROW_H = 17;
    public static final int CONTROL_H = 14;
    public static final int GAP = 8;
    public static final int PAD = 12;

    public static AppleTex.Shadow shadow(float[] s) { return new AppleTex.Shadow(s[0], s[1], (int) (long) s[2]); }

    /** 给颜色乘 alpha（0..1）。 */
    public static int alpha(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * a);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int tintOf(int accent) {
        if (accent == GREEN || accent == GREEN_TEXT) return GREEN_TINT;
        if (accent == ORANGE || accent == ORANGE_TEXT) return ORANGE_TINT;
        if (accent == RED || accent == RED_TEXT) return RED_TINT;
        if (accent == PURPLE) return PURPLE_TINT;
        if (accent == TEAL) return TEAL_TINT;
        if (accent == BLUE || accent == INDIGO) return BLUE_TINT;
        return GRAY_TINT;
    }
}
