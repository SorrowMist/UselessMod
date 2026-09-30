package com.sorrowmist.useless.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 采用 AE2 文本框外观的输入框。
 *
 * <p>AE2 的文本框由 {@code ae2:textures/guis/text_field.png} 一张 128×128 贴图绘制：横向分三段，
 * 左端 1 像素、可平铺中段、右端 1 像素；纵向每 12 像素一行，依次对应普通、聚焦与只读三种状态。
 * 该绘制方式仅依赖贴图与 {@code GuiGraphics}，不依赖 AE2 的 {@code ScreenStyle} 配置体系，
 * 因此可直接复用其外观而不引入 AE2 界面 JSON 依赖。</p>
 *
 * <p>原版 {@link EditBox} 的字体与提示文本字段均为私有且无可用的公开读取方法，
 * 因此本类自行保存这两项，绘制时不访问父类私有状态。</p>
 */
final class AE2StyleTextField extends EditBox {
    /** AE2 文本框贴图，尺寸 128×128。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/text_field.png");
    /** 贴图中可平铺中段的横向起点，位于左端盖之后。 */
    private static final int SOURCE_BODY_X = 1;
    /** 可平铺中段的像素宽度，用作平铺步长。 */
    private static final int SOURCE_BODY_WIDTH = 126;
    /** 贴图总宽度，作为 blit 的纹理宽度参数。 */
    private static final int TEXTURE_SIZE = 128;
    /** 贴图每行的高度，与三种状态的行距一致。 */
    private static final int ROW_HEIGHT = 12;
    /** 文本相对输入框左缘的水平内缩。 */
    private static final int PADDING = 4;
    /** 提示文本颜色。 */
    private static final int HINT_COLOR = 0x808080;
    /** 正文颜色。 */
    private static final int TEXT_COLOR = 0xE0E0E0;

    /** 绘制用字体，父类字段私有，此处另行保存。 */
    private final Font textFont;
    /** 空值且未聚焦时显示的提示文本。 */
    private String hintText = "";

    AE2StyleTextField(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
        this.textFont = font;
        setBordered(false);
    }

    /**
     * 设置空值提示文本。
     *
     * <p>父类的提示由原版在聚焦时绘制，本类改为自绘，故需同时保存一份文本。</p>
     */
    @Override
    public void setHint(Component hint) {
        super.setHint(hint);
        this.hintText = hint.getString();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        // 贴图纵向三行依次为普通、聚焦、只读：第 0 行为未聚焦底纹，第 1 行为聚焦高亮底纹。
        // 本类不用于只读展示，因此不涉及第 2 行。
        int sourceY = (isFocused() ? 1 : 0) * ROW_HEIGHT;
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        // 目标高度取控件实际高度：贴图源行高固定为 ROW_HEIGHT，纵向按需缩放以铺满控件，
        // 避免控件高度大于贴图行高时底部露出空隙。
        int height = getHeight();

        // 左端盖。
        graphics.blit(TEXTURE, x, y, 0, sourceY, 1, height, TEXTURE_SIZE, TEXTURE_SIZE);
        // 中段按中段宽度为步长平铺，末段按剩余宽度裁剪，避免贴图被拉伸。
        int bodyX = x + 1;
        while (bodyX < right - 1) {
            int piece = Math.min(SOURCE_BODY_WIDTH, right - 1 - bodyX);
            graphics.blit(TEXTURE, bodyX, y, SOURCE_BODY_X, sourceY, piece, height,
                    TEXTURE_SIZE, TEXTURE_SIZE);
            bodyX += piece;
        }
        // 右端盖。
        graphics.blit(TEXTURE, right - 1, y, 127, sourceY, 1, height, TEXTURE_SIZE, TEXTURE_SIZE);

        int textY = y + (getHeight() - 8) / 2;
        int maxTextWidth = getWidth() - PADDING * 2;
        String value = getValue();
        if (value.isEmpty()) {
            if (!hintText.isEmpty()) {
                graphics.drawString(textFont, textFont.plainSubstrByWidth(hintText, maxTextWidth),
                        x + PADDING, textY, HINT_COLOR, false);
            }
            return;
        }

        String visible = textFont.plainSubstrByWidth(value, maxTextWidth);
        graphics.drawString(textFont, visible, x + PADDING, textY, TEXT_COLOR, false);

        // 光标仅在聚焦时绘制，位置紧随已显示文本。
        if (isFocused() && (Util.getMillis() / 500L) % 2L == 0L) {
            int cursorX = x + PADDING + textFont.width(visible);
            graphics.fill(cursorX, textY - 1, cursorX + 1, textY + 9, 0xFFD0D0D0);
        }
    }
}
