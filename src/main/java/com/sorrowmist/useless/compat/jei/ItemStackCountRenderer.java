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
 * 数量改为缩写并缩小字号后右对齐绘制，使文本宽度受控。
 *
 * <p>缩放与 Z 层偏移的处理方式参考 Applied Energistics 2 的
 * {@code appeng.client.gui.me.common.StackSizeRenderer}。
 *
 * <p>提示文本与 JEI 内置物品渲染器保持一致，槽位自身的富提示回调不受影响。
 */
public class ItemStackCountRenderer implements IIngredientRenderer<ItemStack> {
    public static final ItemStackCountRenderer INSTANCE = new ItemStackCountRenderer();

    /** 槽位渲染尺寸，与 Minecraft 物品槽位一致。 */
    private static final int SLOT_SIZE = 16;

    /** 数量文本缩放系数 */
    private static final float COUNT_TEXT_SCALE = 0.666f;

    /**
     * 数量文本的 Z 层偏移。
     *
     * <p>物品模型以三维形式绘制在较高的 Z 层，文本缺少该偏移时会被模型遮挡。
     */
    private static final int COUNT_TEXT_Z_OFFSET = 200;

    /** 数量文本基线相对槽位下边缘的距离，单位为缩放前的像素。 */
    private static final float COUNT_TEXT_BASELINE_FROM_BOTTOM = 5.0f;

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
            Font font = getFontRenderer(Minecraft.getInstance(), ingredient);
            String text = formatCount(count);

            // 坐标在缩放后的坐标系中计算，故按缩放系数的倒数折算回缩放前的像素空间。
            float inverseScale = 1.0f / COUNT_TEXT_SCALE;
            float textWidth = font.width(text) * COUNT_TEXT_SCALE;
            int textX = (int) ((posX + SLOT_SIZE - textWidth) * inverseScale);
            int textY = (int) ((posY + SLOT_SIZE - COUNT_TEXT_BASELINE_FROM_BOTTOM * COUNT_TEXT_SCALE)
                    * inverseScale);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, COUNT_TEXT_Z_OFFSET);
            guiGraphics.pose().scale(COUNT_TEXT_SCALE, COUNT_TEXT_SCALE, COUNT_TEXT_SCALE);
            guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, true);
            guiGraphics.pose().popPose();
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
