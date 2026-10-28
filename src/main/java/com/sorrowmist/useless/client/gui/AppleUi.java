package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;

/**
 * Apple 风（浅色 macOS / Liquid Glass）GUI 工具包 v2。
 *
 * <p>标准组件使用设计包原始透明切片，由 {@link DesignAssets} 按内容框及阴影边距绘制。
 * 自定义几何仍可通过 {@link #fillRound} 绘制；槽位、Menu、网络包与业务数据不变。</p>
 */
public final class AppleUi {
    // ---- 语义颜色 ----
    public static final int BLUE = 0xFF007AFF;
    public static final int BLUE_TEXT = 0xFF0066CC;
    public static final int GREEN = 0xFF34C759;
    public static final int GREEN_TEXT = 0xFF248A3D;
    public static final int ORANGE = 0xFFFF9F0A;
    public static final int ORANGE_TEXT = 0xFFC93400;
    public static final int RED = 0xFFFF3B30;
    public static final int RED_TEXT = 0xFFD70015;
    public static final int PURPLE = 0xFFAF52DE;
    public static final int TEAL = 0xFF30B0C7;
    public static final int LABEL = 0xFF1D1D1F;
    public static final int SECONDARY_LABEL = 0xFF6E6E73;
    public static final int TERTIARY_LABEL = 0xFF8E8E93;
    public static final int QUATERNARY_LABEL = 0xFFB4B4BA;
    public static final int SEPARATOR = 0x333C3C43;
    public static final int FILL = 0x1F767680;
    public static final int FILL_STRONG = 0x33767680;
    public static final int GROUPED_BG = 0xFFF2F2F7;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLUE_TINT = 0x24007AFF;
    public static final int RED_TINT = 0x1FFF3B30;

    /** 不透明的组件底色（浅色背景下叠加次数不确定，统一用不透明色避免半透明叠深）。 */
    public static final int PANEL_BODY = 0xF7F6F6F9;
    public static final int PANEL_EDGE = 0xFFD2D2D8;
    public static final int CARD_BODY = 0xFFFDFDFE;
    public static final int CARD_EDGE = 0xFFE1E1E6;
    public static final int SLOT_BODY = 0xFFE9E9EE;
    public static final int SLOT_EDGE = 0xFFD9D9DF;
    public static final int SLOT_SELECTED = 0xFFD6E8FF;
    public static final int CONTROL = 0xFFE8E8ED;
    public static final int CONTROL_HOVER = 0xFFDEDEE4;
    public static final int CONTROL_PRESSED = 0xFFD1D1D8;
    public static final int FIELD_BODY = 0xFFFFFFFF;
    public static final int FIELD_EDGE = 0xFFC9C9CF;
    public static final int TRACK = 0xFFE3E3E8;

    public enum Icon { CHEVRON_LEFT, CHEVRON_RIGHT, PLUS, XMARK, TRASH, GEAR, SEARCH, ARROW_OUT, ARROW_IN, BOLT, CHECK, ARROW_UP, ARROW_DOWN, FILTER, INFO, GRIP,
        FLAME, CUBE, GRID, PATTERN, CONVERT, THERMO, GLOBE, CHAIN, BOX, ROTATE, LINK, COPY, IMPORT, EXPORT, EDIT, WARNING, LOCK, LEAF, WHEEL,
        MINUS, AE, LAYERS, EYE, PICK, SWORD, STAR }

    private AppleUi() {}

    private static double guiScale() {
        return Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static int alphaMul(int argb, float k) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- 纹理 9-slice 基元（v5 视觉质感）
    /** 4x 超采样预渲染的白色圆角遮罩（128²，角 32 texel），blur mcmeta 线性过滤，代码 9-slice 拉伸 + 顶点着色。 */
    public static final ResourceLocation ROUND_TEX = new ResourceLocation(UselessMod.MODID, "textures/gui/apple/v5_round.png");

    private static int lerpColor(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return r;
    }

    private static void vtx(BufferBuilder b, Matrix4f m, float x, float y, float u, float v, int c) {
        b.vertex(m, x, y, 0).uv(u, v).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
    }

    /**
     * 通用 9-slice：tex 为方形纹理（texSize），四角各 cornerTex texel；目标矩形 (x,y,w,h)，角目标尺寸 cd。
     * 颜色按四角 (tl,tr,bl,br) 双线性插值，可直接画渐变。
     */
    public static void nine(GuiGraphics g, ResourceLocation tex, float texSize, float cornerTex,
                            float x, float y, float w, float h, float cd, int tl, int tr, int bl, int br) {
        if (w <= 0 || h <= 0) return;
        if (((tl | tr | bl | br) >>> 24) == 0) return;
        cd = Math.max(0f, Math.min(cd, Math.min(w, h) / 2f));
        float[] xs = {x, x + cd, x + w - cd, x + w};
        float[] ys = {y, y + cd, y + h - cd, y + h};
        float c = cornerTex / texSize;
        float[] us = {0, c, 1 - c, 1};
        // Flush GuiGraphics' queued text/items before drawing immediate textured quads.
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                float ax = xs[i], bx = xs[i + 1], ay = ys[j], by = ys[j + 1];
                if (bx - ax <= 0 || by - ay <= 0) continue;
                int[] cs = new int[4];
                float[][] pts = {{ax, ay}, {ax, by}, {bx, by}, {bx, ay}};
                for (int k = 0; k < 4; k++) {
                    float tx = (pts[k][0] - x) / w, ty = (pts[k][1] - y) / h;
                    cs[k] = lerpColor(lerpColor(tl, tr, tx), lerpColor(bl, br, tx), ty);
                }
                vtx(b, m, ax, ay, us[i], us[j], cs[0]);
                vtx(b, m, ax, by, us[i], us[j + 1], cs[1]);
                vtx(b, m, bx, by, us[i + 1], us[j + 1], cs[2]);
                vtx(b, m, bx, ay, us[i + 1], us[j], cs[3]);
            }
        }
        BufferUploader.drawWithShader(b.end());
    }

    /** 填充圆角矩形（GUI 单位，可为小数）：4x 预渲染圆角纹理 9-slice，边缘平滑。 */
    public static void fillRound(GuiGraphics g, float x, float y, float w, float h, float r, int argb) {
        nine(g, ROUND_TEX, 128, 32, x, y, w, h, r, argb, argb, argb, argb);
    }

    /** 竖向渐变圆角矩形。 */
    public static void gradientRound(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom) {
        nine(g, ROUND_TEX, 128, 32, x, y, w, h, r, top, top, bottom, bottom);
    }

    /** 柔和投影：spread 为模糊半径（GUI 单位），dy 为下移。 */
    public static void softShadow(GuiGraphics g, float x, float y, float w, float h, float spread, float dy, int argb) {
        softShadow(g, x, y, w, h, spread, dy, Math.min(8, Math.min(w, h) / 2f), argb);
    }

    /** Follow the actual silhouette. Stretching a square shadow leaves a visible square behind circles/pills. */
    public static void softShadow(GuiGraphics g, float x, float y, float w, float h, float spread, float dy, float radius, int argb) {
        if (w <= 0 || h <= 0 || spread <= 0) return;
        int steps = Math.max(6, Math.min(20, (int) Math.ceil(spread * 2)));
        int opacity = argb >>> 24;
        float previous = 0;
        for (int i = steps; i >= 0; i--) {
            float extent = spread * i / steps;
            float coverage = (float) Math.exp(-3.5f * (extent / spread) * (extent / spread));
            int alpha = Math.round(opacity * (coverage - previous));
            previous = coverage;
            if (alpha == 0) continue;
            fillRound(g, x - extent, y - extent + dy, w + 2 * extent, h + 2 * extent,
                    radius + extent, (alpha << 24) | (argb & 0xFFFFFF));
        }
    }

    /** 顶部光泽：上半部白色→透明。 */
    public static void gloss(GuiGraphics g, float x, float y, float w, float h, float r, float alpha) {
        int top = alphaMul(0xFFFFFFFF, alpha);
        nine(g, ROUND_TEX, 128, 32, x, y, w, h * 0.55f, Math.min(r, h * 0.27f), top, top, 0x00FFFFFF, 0x00FFFFFF);
    }

    /** 带 1 物理像素描边的圆角矩形：先画描边色整体，再内缩 1 物理像素画主体。 */
    public static void strokedRound(GuiGraphics g, float x, float y, float w, float h, float r, int body, int edge) {
        float px = (float) (1 / guiScale());
        fillRound(g, x, y, w, h, r, edge);
        fillRound(g, x + px, y + px, w - 2 * px, h - 2 * px, Math.max(0, r - px), body);
    }

    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
        fillRound(g, x, y, w, h, radius, argb);
    }

    public static void capsule(GuiGraphics g, int x, int y, int w, int h, int argb) {
        fillRound(g, x, y, w, h, h / 2f, argb);
    }

    /** 兼容 v1 的 9-slice 签名（转为纹理圆角）。 */
    public static void nineSlice(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                                 int u, int v, int rw, int rh, int cornerTex) {
        fillRound(g, x, y, w, h, Math.min(cornerTex / 4f, Math.min(w, h) / 2f), CARD_BODY);
    }

    // ---------------------------------------------------------------- 组件
    // 窗口背景四角色晕（对应效果图 radial-gradient：左上蓝、右上粉、左下薄荷、右下橙）
    public static final int GLOW_TL = 0xFFC4D8FF, GLOW_TR = 0xFFF7CDE5, GLOW_BL = 0xFFC6EEDD, GLOW_BR = 0xFFFBDCBC;

    /** 玻璃窗口：多层柔阴影 + 白色细边 + 四角彩色渐变底 + 半透明白玻璃层 + 顶部高光。 */
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h) {
        // The reference has a softly lit stage behind translucent glass. Without
        // backdrop blur, a translucent-only layer exposes sharp terrain and the
        // player through every card. Use a quiet opaque light bed, then retain
        // the supplied glass artwork, border and baked shadow unchanged.
        nine(g, ROUND_TEX, 128, 32, x, y, w, h, 12,
                0xFFDCE8FC, 0xFFF2E2F0, 0xFFDDEFE8, 0xFFF6EBDC);
        DesignAssets.draw(g, "window_panel_640x480", x, y, w, h);
    }

    public static void separator(GuiGraphics g, int x, int y, int w) {
        float px = (float) (1 / guiScale());
        nine(g, ROUND_TEX, 128, 32, x, y, w, px, 0, 0x003C3C43, 0x003C3C43, 0x003C3C43, 0x003C3C43);
        fillRound(g, x, y, w, px, 0, 0x1F3C3C43);
    }

    /** 卡片：柔阴影 + 半透明白（透出窗口色晕）+ 白色细边。 */
    public static void card(GuiGraphics g, int left, int top, int right, int bottom) {
        DesignAssets.draw(g, "card_white_400x200", left, top, right - left, bottom - top);
    }

    private static boolean mouseIn(float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 槽位：浅色圆角底（比 18 格略小，留出间距），有物品时轻微染色，悬停/选中蓝色描边光晕。 */
    public static void slot(GuiGraphics g, int x, int y, boolean selected) {
        slot(g, x, y, selected, false);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean selected, boolean filled) {
        String state = selected ? "selected" : mouseIn(x - 1, y - 1, 18, 18) ? "hover" : "empty";
        DesignAssets.draw(g, "slot_" + state + "_18", x - 1, y - 1, 18, 18);
    }

    public static void slot(GuiGraphics g, int leftPos, int topPos, Slot slot) {
        slot(g, leftPos + slot.x, topPos + slot.y, false, slot.hasItem());
    }

    public static void disabledSlot(GuiGraphics g, int leftPos, int topPos, Slot slot) {
        DesignAssets.drawTinted(g, "slot_empty_18", leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, 0x66FFFFFF);
    }

    /** 未解锁槽位：浅色蒙层 + 小锁（自绘几何，非系统符号）。坐标为槽位 16×16 内容区左上角。 */
    public static void lockedSlot(GuiGraphics g, int x, int y) {
        DesignAssets.draw(g, "slot_locked_18", x - 1, y - 1, 18, 18);
    }

    public static void progress(GuiGraphics g, int x, int y, int w, int h, float ratio, int color) {
        DesignAssets.draw(g, "ninepatch_progress_track", x, y, w, h);
        float fw = w * Math.max(0f, Math.min(1f, ratio));
        if (fw > 0) DesignAssets.draw(g, "ninepatch_progress_fill_" + DesignAssets.colorName(color).replace("gray", "blue").replace("indigo", "purple"), x, y, fw, h);
    }

    public static void toggle(GuiGraphics g, int x, int y, int w, int h, boolean on) {
        DesignAssets.draw(g, on ? "switch_on" : "switch_off", x, y, w, h);
    }

    public static void segmented(GuiGraphics g, Font font, int x, int y, int w, int h, String[] labels, int selected) {
        DesignAssets.draw(g, "segmented_track_240x32", x, y, w, h);
        int n = labels.length;
        for (int i = 0; i < n; i++) {
            int sx = x + 1 + (w - 2) * i / n, ex = x + 1 + (w - 2) * (i + 1) / n;
            if (i == selected) {
                DesignAssets.draw(g, "segmented_thumb_116x28", sx, y + 1, ex - sx, h - 2);
            }
            centered(g, font, labels[i], (sx + ex) / 2, y + (h - 8) / 2 + 1, i == selected ? LABEL : SECONDARY_LABEL);
        }
    }

    public enum ButtonStyle { GRAY, PRIMARY, TINTED, DESTRUCTIVE }

    /** 按钮表面：normal / hover / pressed 三态；PRIMARY 为蓝色竖向渐变 + 光泽 + 蓝色投影。 */
    public static void buttonSurface(GuiGraphics g, int x, int y, int w, int h, ButtonStyle style,
                                     boolean hovered, boolean pressed, boolean active) {
        String kind = switch (style) {
            case PRIMARY -> "primary"; case TINTED -> "tint";
            case DESTRUCTIVE -> "danger"; default -> "secondary";
        };
        DesignAssets.button(g, x, y, w, h, kind, hovered, pressed, active);
    }

    /** 窗口标题：粗体、主标签色。 */
    public static void title(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text.copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, LABEL, false);
    }

    public static void title(GuiGraphics g, Font font, String text, int x, int y) {
        title(g, font, Component.literal(text), x, y);
    }

    public static int buttonTextColor(ButtonStyle style, boolean active) {
        if (!active) return QUATERNARY_LABEL;
        return switch (style) {
            case PRIMARY -> WHITE;
            case TINTED -> BLUE_TEXT;
            case DESTRUCTIVE -> RED_TEXT;
            default -> LABEL;
        };
    }

    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        DesignAssets.draw(g, focused ? "field_focus_200" : "field_default_200", x, y, w, h);
    }

    public static String iconKey(Icon icon) {
        String name = switch (icon) {
            case CHEVRON_LEFT -> "left"; case CHEVRON_RIGHT -> "right";
            case XMARK -> "x"; case ARROW_OUT -> "out"; case ARROW_IN -> "in";
            case ARROW_UP -> "up"; case ARROW_DOWN -> "down";
            case GRIP -> "grip"; case THERMO -> "thermo"; case WARNING -> "warn";
            default -> icon.name().toLowerCase(java.util.Locale.ROOT);
        };
        return "icon_" + name + "_24_white";
    }

    public static void icon(GuiGraphics g, Icon icon, int x, int y, int size, int argb) {
        DesignAssets.drawTinted(g, iconKey(icon), x, y, size, size, argb);
    }

    public static void badge(GuiGraphics g, Font font, int x, int y, String text, int tint, int textColor) {
        int w = font.width(text) + 8;
        DesignAssets.draw(g, "badge_" + DesignAssets.colorName(textColor) + "_64", x, y, w, 11);
        g.drawString(font, text, x + 4, y + 2, textColor, false);
    }

    public static void centered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    public static void centered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font, seq, cx - font.width(seq) / 2, y, color, false);
    }

    /**
     * 浅色背景下把原版亮色文字映射为可读的深色系统色（白/黄/亮绿/亮红/青/蓝）。
     * 用于按钮、标签等原本在深色底上显示的彩色 Component。
     */
    public static int readable(int rgb) {
        rgb &= 0xFFFFFF;
        return switch (rgb) {
            case 0xFFFF55, 0xFFAA00 -> 0xC93400;
            case 0x55FF55, 0x00AA00 -> 0x248A3D;
            case 0xFF5555, 0xAA0000 -> 0xD70015;
            case 0x55FFFF, 0x00AAAA -> 0x0E7C8C;
            case 0x5555FF, 0x0000AA -> 0x0066CC;
            case 0xFFFFFF, 0xF2F2F2, 0xE0E0E0, 0xD0D0D0 -> 0x1D1D1F;
            case 0xAAAAAA, 0xA0A0A0, 0x888894, 0x808080 -> 0x8E8E93;
            default -> {
                int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
                int lum = (r * 299 + gg * 587 + b * 114) / 1000;
                yield lum > 185 ? 0x3A3A3C : rgb; // 其它过亮颜色压暗，深色保持
            }
        };
    }

    /** Empty-pattern hint uses the same supplied glyph as the other controls. */
    public static void patternGlyph(GuiGraphics g, int x, int y) {
        icon(g, Icon.PATTERN, x + 3, y + 3, 10, 0x40007AFF);
    }
}
