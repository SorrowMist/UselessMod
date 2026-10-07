package com.sorrowmist.useless.utils;

import net.minecraft.util.Mth;

/**
 * 温度 → 颜色 的映射，移植自气动工艺「压缩铁块」的着色公式
 * （{@code me.desht.pneumaticcraft.common.heat.HeatUtil#getColourForTemperature}）。
 *
 * <p>输入为开尔文，输出 {@code 0xAARRGGBB}。分段语义：</p>
 * <ul>
 *     <li>{@code < 273K}：青 → 蓝（越冷越蓝）</li>
 *     <li>{@code 273~323K}：白色（室温，无着色）</li>
 *     <li>{@code 323~873K}：红 → 橙</li>
 *     <li>{@code 873~2273K}：橙 → 黄</li>
 *     <li>{@code > 2273K}：黄 → 白热（色相停在黄，转而降低饱和度提亮）</li>
 * </ul>
 *
 * <p>高温端之所以不停在纯黄：金属受热发光的真实序列是「暗红 → 橙 → 黄 → 白热」，
 * 只到黄色会缺少「炽热」的观感。故 2273 K 之后色相钳在 60（黄）不再推进，改为
 * 让饱和度从 1 衰减到 {@link #WHITE_HOT_SATURATION}，到 10 000 K 呈明亮的暖白（白热）。</p>
 *
 * <p>纯 Java 实现，不引用任何第三方模组类型，也不依赖 {@code java.awt}。</p>
 */
public final class TemperatureColors {

    /** 「橙 → 黄」段的终点温度（K），也是白热段的起点。取气动压缩铁块的温度上限。 */
    public static final int WHITE_HOT_START_KELVIN = 2273;

    /** 白热段的终点温度（K），再高也只到这个饱和度。 */
    public static final int WHITE_HOT_END_KELVIN = 10_000;

    /**
     * 白热段终点的饱和度。不取 0（纯白）：完全褪色会变成贴图的灰白，反而失去「热」的暖调。
     * 0.30 ⇒ 终态约为 {@code (255, 255, 179)} 的暖白。
     */
    public static final float WHITE_HOT_SATURATION = 0.30f;

    private TemperatureColors() {
    }

    /** 按温度（开尔文）取内板颜色，返回 {@code 0xAARRGGBB}。 */
    public static int forTemperature(long kelvin) {
        int t = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, kelvin));

        int hue;
        float saturation;
        if (t < 273) {
            hue = 180 + (300 - t) / 5;                 // 180 -> 240：青 -> 蓝
            saturation = (273 - t) / 273f;
        } else if (t < 323) {
            hue = 360;
            saturation = 0f;                            // 室温：白
        } else if (t < 873) {
            hue = (int) ((t - 323) / 550f * 30f);       // 0 -> 30：红 -> 橙
            saturation = (t - 323) / 550f;
        } else {
            // 873K 以上分两段：先「橙 → 黄」（色相 30 → 60），到 WHITE_HOT_START_KELVIN 后
            // 色相停在黄不再推进，改为让饱和度衰减到 WHITE_HOT_SATURATION，呈白热观感。
            //
            // ⛔ 色相必须钳制：气动原版这个式子不钳制，是因为它方块的温度被硬夹在 0~2273K，
            // hue 最多走到 45（黄）。我们的调节器允许任意高温，不钳制的话 11000K 会算出
            // hue=138 → 变成绿色（越热越绿，语义完全反了）。
            float ramp = Math.min(1f, (t - 873f) / (WHITE_HOT_START_KELVIN - 873f));
            hue = 30 + (int) (ramp * 30f);              // 30 -> 60：橙 -> 黄

            if (t <= WHITE_HOT_START_KELVIN) {
                saturation = 1f;
            } else {
                float hot = Math.min(1f, (t - (float) WHITE_HOT_START_KELVIN)
                        / (WHITE_HOT_END_KELVIN - WHITE_HOT_START_KELVIN));
                saturation = 1f - hot * (1f - WHITE_HOT_SATURATION);
            }
        }

        // 气动原版对 hue 做了 floor 归一化；Mth.hsvToRgb 不做，这里把 360 度兜到 1 以下。
        float normalizedHue = Math.min(0.9999f, hue / 360f);
        return 0xFF000000 | Mth.hsvToRgb(normalizedHue, saturation, 1f);
    }
}
