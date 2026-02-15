package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.init.ModTags;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CatalystInfoCategory implements IRecipeCategory<CatalystInfoCategory.CatalystInfo> {
    public static final RecipeType<CatalystInfo> TYPE =
            RecipeType.create(UselessMod.MODID, "catalyst_info", CatalystInfo.class);

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/jei_catalyst_info_gui.png");

    // 显示尺寸
    private static final int DISPLAY_WIDTH = 150;
    private static final int DISPLAY_HEIGHT = 130;

    // 催化剂物品堆栈列表
    private final List<ItemStack> catalystStacks;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    CatalystInfoCategory(IGuiHelper guiHelper) {
        // 创建一个空白的背景，尺寸与显示尺寸相同
        this.background = guiHelper.createBlankDrawable(DISPLAY_WIDTH, DISPLAY_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.USEFUL_INGOT.get()));
        this.title = Component.translatable("jei.useless_mod.catalyst_info");

        // 从 ModTags.CATALYSTS 获取所有催化剂物品
        this.catalystStacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.is(ModTags.CATALYSTS)) {
                this.catalystStacks.add(stack);
            }
        }
    }

    @Override
    public @NotNull RecipeType<CatalystInfo> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return this.title;
    }

    @Override
    @Deprecated
    @SuppressWarnings("removal")
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public int getWidth() {
        return DISPLAY_WIDTH;
    }

    @Override
    public int getHeight() {
        return DISPLAY_HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull CatalystInfo recipe, @NotNull IFocusGroup focuses) {
        // 2行5列网格布局
        int startX = 29; // 第一个格子左上角起点X坐标
        int startY = 24; // 第一个格子左上角起点Y坐标
        int spacing = 18; // 每个格子18*18像素

        // 动态计算列数（最多5列）
        int columns = Math.min(this.catalystStacks.size(), 5);
        int rows = (this.catalystStacks.size() + columns - 1) / columns;

        for (int i = 0; i < this.catalystStacks.size(); i++) {
            // 计算行和列
            int row = i / columns;
            int col = i % columns;

            int x = startX + col * spacing;
            int y = startY + row * spacing;

            ItemStack catalystStack = this.catalystStacks.get(i);

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                   .addItemStack(catalystStack)
                   .addRichTooltipCallback(new CatalystTooltipCallback(i));
        }
    }

    @Override
    public void draw(@NotNull CatalystInfo recipe, @NotNull IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        // 手动绘制背景，确保不缩放
        guiGraphics.blit(GUI_TEXTURE,
                0, 0, // 绘制位置 (0,0)
                0, 0, // 纹理起始位置 (0,0)
                DISPLAY_WIDTH, DISPLAY_HEIGHT, // 绘制尺寸
                DISPLAY_WIDTH, DISPLAY_HEIGHT); // 纹理尺寸（与绘制尺寸相同）

        // 绘制标题 - 居中显示
        String titleText = Component.translatable("jei.useless_mod.catalyst_info.title").getString();
        int titleWidth = minecraft.font.width(titleText);
        int titleX = (DISPLAY_WIDTH - titleWidth) / 2;
        guiGraphics.drawString(minecraft.font, titleText, titleX, 10, 0x404040, false);

        // 调整说明文字的位置，并居中显示
        String[] explanationLines = {
                Component.translatable("jei.useless_mod.catalyst_info.desc1").getString(),
                Component.translatable("jei.useless_mod.catalyst_info.desc2").getString(),
                Component.translatable("jei.useless_mod.catalyst_info.desc3").getString(),
                Component.translatable("jei.useless_mod.catalyst_info.desc4").getString()
        };

        int explanationY = 85;
        for (String line : explanationLines) {
            int lineWidth = minecraft.font.width(line);
            int lineX = (DISPLAY_WIDTH - lineWidth) / 2;
            guiGraphics.drawString(minecraft.font, line, lineX, explanationY, 0x404040, false);
            explanationY += 10;
        }
    }

    // 催化剂信息类
    public static class CatalystInfo {
        // 这个类可以是空的，因为我们不需要额外的数据
    }

    // 自定义工具提示回调
    private class CatalystTooltipCallback implements IRecipeSlotRichTooltipCallback {
        private final int catalystIndex;

        CatalystTooltipCallback(int index) {
            this.catalystIndex = index;
        }

        @Override
        public void onRichTooltip(@NotNull IRecipeSlotView recipeSlotView, @NotNull ITooltipBuilder tooltip) {
            if (this.catalystIndex < CatalystInfoCategory.this.catalystStacks.size()) {
                ItemStack stack = CatalystInfoCategory.this.catalystStacks.get(this.catalystIndex);
                int tier = CatalystParallelManager.getCatalystTier(stack);
                String catalystName = CatalystParallelManager.getCatalystDisplayName(stack);

                // 添加自定义工具提示
                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));

                // 检查是否为USEFUL_INGOT
                if (CatalystParallelManager.isUsefulIngot(stack)) {
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.parallel.unlimited").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.effect.title").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.effect.not_consumed").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.effect.unlimited").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.effect.no_energy_mult").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.effect.time_unchanged").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.effect.useless_warning").withStyle(ChatFormatting.RED));
                } else {
                    // 普通无用锭催化剂
                    int normalParallel = CatalystParallelManager.calculateParallelForNormalRecipe(stack);
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.normal.parallel", normalParallel)
                                         .withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.cross_tier.title").withStyle(ChatFormatting.YELLOW));

                    // 显示跨阶合成信息
                    if (tier >= 2 && tier <= 9) {
                        for (int targetTier = 1; targetTier < tier; targetTier++) {
                            int crossTierParallel = CatalystParallelManager.calculateParallelForUselessIngotRecipe(
                                    stack, targetTier);
                            tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.cross_tier.format", targetTier, crossTierParallel)
                                                 .withStyle(ChatFormatting.GRAY));
                        }
                    }

                    tooltip.add(Component.empty());
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.title").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc1").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc2").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc3").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc4").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.warning").withStyle(ChatFormatting.RED));
                }
            }
        }
    }
}
