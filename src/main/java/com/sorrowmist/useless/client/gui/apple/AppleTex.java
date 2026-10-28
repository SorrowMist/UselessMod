package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.GuiTextureGroup;
import com.sorrowmist.useless.client.gui.apple.core.IGuiTexture;
import com.sorrowmist.useless.client.gui.apple.core.TransformTexture;
import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 纹理工厂：所有 Apple 质感都以 {@link IGuiTexture} 组合表达（纯原版 GuiGraphics + v5 烘焙 PNG）。
 *
 * <ul>
 *   <li>{@link Round}：v5 高清 9-slice 圆角（4x 预渲染 + blur mcmeta），四角颜色可分别指定 = 线性渐变；</li>
 *   <li>{@link Shadow}：v5 预模糊阴影 9-slice（外扩 spread、下移 dy）；</li>
 *   <li>{@link GuiTextureGroup}：多层叠加（阴影 → 描边 → 主体 → 光泽）。</li>
 * </ul>
 * 圆角主体走 v5 高清 9-slice（AppleUi.nine），
 * 由 TransformTexture 子类组合。
 */
public final class AppleTex {
    private AppleTex() {}

    // 别名：唯一定义在 AppleStyle。
    public static final int BLUE = AppleStyle.BLUE;
    public static final int GREEN = AppleStyle.GREEN;
    public static final int RED = AppleStyle.RED;
    public static final int LABEL = AppleStyle.LABEL;
    public static final int SECONDARY = AppleStyle.TERTIARY;
    public static final int SEPARATOR = AppleStyle.SEPARATOR;
    public static final int FILL = AppleStyle.FILL;
    public static final int FILL_HOVER = AppleStyle.FILL_HOVER;
    public static final int CARD = AppleStyle.CARD;
    public static final int SELECTED_ROW = AppleStyle.SELECTED_ROW;

    /** 圆角矩形（可渐变）。 */
    public static class Round extends TransformTexture {
        public int tl, tr, bl, br;
        public float radius;
        public float inset;
        public Round(int color, float radius) { this(color, color, radius); }
        public Round(int top, int bottom, float radius) { this.tl = this.tr = top; this.bl = this.br = bottom; this.radius = radius; }
        public Round inset(float i) { this.inset = i; return this; }
        @Override
        protected void drawInternal(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h) {
            float r = radius < 0 ? (h - 2 * inset) / 2f : radius; // radius<0 = 胶囊
            AppleUi.nine(g, AppleUi.ROUND_TEX, 128, 32, x + inset, y + inset, w - 2 * inset, h - 2 * inset, r, tl, tr, bl, br);
        }
        @Override public Round setColor(int c) { tl = tr = bl = br = c; return this; }
    }

    /** 柔和投影。 */
    public static class Shadow extends TransformTexture {
        public float spread, dy; public int color;
        public Shadow(float spread, float dy, int color) { this.spread = spread; this.dy = dy; this.color = color; }
        @Override
        protected void drawInternal(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h) {
            AppleUi.softShadow(g, x, y, w, h, spread, dy, color);
        }
    }

    /** 1px 物理像素描边 + 主体。 */
    public static class Stroked extends TransformTexture {
        public int body, edge; public float radius;
        public Stroked(int body, int edge, float radius) { this.body = body; this.edge = edge; this.radius = radius; }
        @Override
        protected void drawInternal(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h) {
            AppleUi.strokedRound(g, x, y, w, h, radius < 0 ? h / 2f : radius, body, edge);
        }
    }

    public static IGuiTexture round(int c, float r) { return new Round(c, r); }
    public static IGuiTexture capsule(int c) { return new Round(c, -1); }
    public static IGuiTexture gradient(int top, int bottom, float r) { return new Round(top, bottom, r); }

    /** 白色卡片：极淡阴影 + 白色主体。 */
    public static IGuiTexture card() { return card(AppleStyle.R_CARD); }

    public static IGuiTexture card(float r) {
        return new DesignSlice("card_white_400x200");
    }

    /** Original artwork shared by the vanilla fallback and native LDLib host. */
    public static class DesignSlice extends TransformTexture {
        private final String key;
        public DesignSlice(String key) { this.key = key; }
        @Override protected void drawInternal(GuiGraphics g, int mx, int my, float x, float y, int w, int h) {
            com.sorrowmist.useless.client.gui.DesignAssets.draw(g, key, x, y, w, h);
        }
    }

    /** 窗口玻璃面板：大阴影 + 白边描线 + 浅蓝→浅粉→白四角渐变（替代毛玻璃）。 */
    public static IGuiTexture glassWindow(float r) {
        return new TransformTexture() {
            @Override protected void drawInternal(GuiGraphics g, int mx, int my, float x, float y, int w, int h) {
                AppleUi.glassPanel(g, Math.round(x), Math.round(y), w, h);
            }
        };
    }
}
