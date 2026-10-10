package com.sorrowmist.useless.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 物品槽位渲染器：以缩写形式绘制堆叠数量。
 *
 * <p>JEI 默认经 {@code GuiGraphics#renderItemDecorations} 绘制完整数量文本，
 * 四位以上数字的宽度超出 16 像素槽位并溢出到相邻槽位。本渲染器仅绘制物品图标，
 * 数量改为缩写后右对齐绘制，使文本宽度受控。
 *
 * <p>提示文本与 JEI 内置物品渲染器保持一致，槽位自身的富提示回调不受影响。
 */
public class ItemStackCountRenderer implements IIngredientRenderer<ItemStack> {
    public static final ItemStackCountRenderer INSTANCE = new ItemStackCountRenderer();

    /** 数量文本右边缘相对槽位左边缘的内缩量，与槽位渲染尺寸一致。 */
    private static final int COUNT_TEXT_RIGHT_INSET = 16;

    /** 数量文本基线相对槽位上边缘的偏移量。 */
    private static final int COUNT_TEXT_BASELINE_Y = 8;

    @Override
    public void render(GuiGraphics guiGraphics, @Nullable ItemStack ingredient) {
        render(guiGraphics, ingredient, 0, 0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, @Nullable ItemStack ingredient, int posX, int posY) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }

        RenderSystem.enableDepthTest();
        guiGraphics.renderFakeItem(ingredient, posX, posY);

        int count = ingredient.getCount();
        if (count > 1) {
            String text = formatCount(count);
            Font font = getFontRenderer(Minecraft.getInstance(), ingredient);
            int textX = posX + COUNT_TEXT_RIGHT_INSET - font.width(text);
            guiGraphics.drawString(font, text, textX, posY + COUNT_TEXT_BASELINE_Y, 0xFFFFFF, true);
        }

        RenderSystem.disableBlend();
    }

    @Override
    @Deprecated(since = "19.49.0", forRemoval = true)
    @SuppressWarnings("removal")
    public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        return getTooltip(ingredient, tooltipContext, minecraft.player, tooltipFlag);
    }

    @Override
    public List<Component> getTooltip(ItemStack ingredient, Item.TooltipContext tooltipContext,
                                      @Nullable Player player, TooltipFlag tooltipFlag) {
        return ingredient.getTooltipLines(tooltipContext, player, tooltipFlag);
    }

    /**
     * 将数值缩写为受控宽度的文本，千位以上使用 k / M / G 后缀。
     *
     * @param count 待格式化的数值
     * @return 缩写后的文本
     */
    private static String formatCount(long count) {
        if (count >= 1000000000L) {
            return String.format("%.2fG", count / 1000000000.0);
        } else if (count >= 1000000L) {
            return String.format("%.2fM", count / 1000000.0);
        } else if (count >= 1000L) {
            return String.format("%.2fk", count / 1000.0);
        } else {
            return String.valueOf(count);
        }
    }
}
