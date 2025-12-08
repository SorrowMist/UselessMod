package com.sorrowmist.useless.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.registry.CatalystManager;
import com.sorrowmist.useless.registry.ModIngots;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CatalystInfoCategory implements IRecipeCategory<CatalystInfoCategory.CatalystInfo> {
    public static final RecipeType<CatalystInfo> TYPE =
            RecipeType.create(UselessMod.MOD_ID, "catalyst_info", CatalystInfo.class);

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/jei_catalyst_info_gui.png");

    // 显示尺寸
    private static final int DISPLAY_WIDTH = 150;
    private static final int DISPLAY_HEIGHT = 130;

    // 从 CatalystManager 获取催化剂数据
    private final Map<String, Integer> catalystData;
    private final ItemStack[] catalystStacks;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    private final IGuiHelper guiHelper;

    public CatalystInfoCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        // 创建一个空白的背景，尺寸与显示尺寸相同
        this.background = guiHelper.createBlankDrawable(DISPLAY_WIDTH, DISPLAY_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModIngots.USEFUL_INGOT.get()));
        this.title = Component.translatable("jei.useless_mod.catalyst_info");

        // 从 CatalystManager 获取所有催化剂数据
        this.catalystData = CatalystManager.getAllCatalysts();

        // 创建催化剂物品堆栈数组
        this.catalystStacks = new ItemStack[]{
                new ItemStack(ModIngots.USELESS_INGOT_TIER_1.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_2.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_3.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_4.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_5.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_6.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_7.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_8.get()),
                new ItemStack(ModIngots.USELESS_INGOT_TIER_9.get()),
                new ItemStack(ModIngots.USEFUL_INGOT.get())
        };
    }

    @Override
    public RecipeType<CatalystInfo> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CatalystInfo recipe, IFocusGroup focuses) {
        // 2行5列网格布局
        int startX = 29; // 第一个格子左上角起点X坐标
        int startY = 24; // 第一个格子左上角起点Y坐标
        int spacing = 18; // 每个格子18*18像素

        // 总共10个催化剂，按2行5列排列
        for (int i = 0; i < catalystStacks.length; i++) {
            final int index = i;
            // 计算行和列
            int row = i / 5; // 0, 0, 0, 0, 0, 1, 1, 1, 1, 1
            int col = i % 5; // 0, 1, 2, 3, 4, 0, 1, 2, 3, 4

            int x = startX + col * spacing;
            int y = startY + row * spacing;

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .addItemStack(catalystStacks[index])
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new CatalystItemStackRenderer(index))
                    .addTooltipCallback(new CatalystTooltipCallback(index));
        }
    }

    @Override
    public void draw(CatalystInfo recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        // 手动绘制背景，确保不缩放
        // 假设纹理尺寸与显示尺寸相同（150x120）
        guiGraphics.blit(GUI_TEXTURE,
                0, 0, // 绘制位置 (0,0)
                0, 0, // 纹理起始位置 (0,0)
                DISPLAY_WIDTH, DISPLAY_HEIGHT, // 绘制尺寸
                DISPLAY_WIDTH, DISPLAY_HEIGHT); // 纹理尺寸（与绘制尺寸相同）

        // 绘制标题 - 居中显示
        String titleText = "催化剂并行数信息";
        int titleWidth = minecraft.font.width(titleText);
        int titleX = (DISPLAY_WIDTH - titleWidth) / 2;
        guiGraphics.drawString(minecraft.font, titleText, titleX, 10, 0x404040, false);

        // 调整说明文字的位置，并居中显示
        String[] explanationLines = {
                "• 催化剂为可选项，除有用锭外会被消耗",
                "• 放入催化剂可大幅提高并行数",
                "• 并行数影响消耗、产出和能量",
                "• 合成无用锭时催化剂无效"
        };

        int explanationY = 85;
        for (String line : explanationLines) {
            int lineWidth = minecraft.font.width(line);
            int lineX = (DISPLAY_WIDTH - lineWidth) / 2;
            guiGraphics.drawString(minecraft.font, line, lineX, explanationY, 0x404040, false);
            explanationY += 10;
        }
    }

    // 自定义工具提示回调
    private class CatalystTooltipCallback implements IRecipeSlotTooltipCallback {
        private final int catalystIndex;

        public CatalystTooltipCallback(int index) {
            this.catalystIndex = index;
        }

        @Override
        public void onTooltip(mezz.jei.api.gui.ingredient.IRecipeSlotView recipeSlotView, List<Component> tooltip) {
            if (catalystIndex < catalystStacks.length) {
                ItemStack stack = catalystStacks[catalystIndex];
                int parallel = CatalystManager.getCatalystParallel(stack);
                String catalystName = CatalystManager.getCatalystName(stack);

                // 清空原有工具提示，添加自定义工具提示
                tooltip.clear();
                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));
                
                // 检查是否为USEFUL_INGOT
                if (stack.getItem() == ModIngots.USEFUL_INGOT.get()) {
                    tooltip.add(Component.literal("并行数: 无上限").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.literal("• 催化剂不会被消耗").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 无并行数上限").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 能量消耗不会倍增").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.literal("并行数: " + parallel + "倍").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.literal("• 输入物品消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 输出物品数量 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 能量消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                    // 添加黑名单说明
                    tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));
                    tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    // 自定义物品渲染器，显示并行数信息
    private class CatalystItemStackRenderer implements IIngredientRenderer<ItemStack> {
        private final int catalystIndex;

        public CatalystItemStackRenderer(int index) {
            this.catalystIndex = index;
        }

        @Override
        public void render(GuiGraphics guiGraphics, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return;

            // 启用深度测试
            RenderSystem.enableDepthTest();

            // 渲染物品图标 - 在18*18的栏位内居中显示
            guiGraphics.renderFakeItem(stack, 1, 1);

            // 渲染并行数文本
            Minecraft mc = Minecraft.getInstance();
            int parallel = CatalystManager.getCatalystParallel(stack);
            String parallelText;
            
            // 检查是否为USEFUL_INGOT
            if (stack.getItem() == ModIngots.USEFUL_INGOT.get()) {
                parallelText = "∞";
            } else {
                parallelText = parallel + "x";
            }
            
            int textWidth = mc.font.width(parallelText);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200.0F);
            guiGraphics.pose().scale(0.5f, 0.5f, 1.0F);

            int x = (int)((16 - textWidth * 0.5f) / 0.5f);
            int y = (int)(12 / 0.5f);

            guiGraphics.drawString(mc.font, parallelText, x, y, 0x00FF00, true);
            guiGraphics.pose().popPose();

            RenderSystem.disableBlend();
        }

        // 使用新的getTooltip方法
        @Override
        public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, ItemStack ingredient, TooltipFlag tooltipFlag) {
            if (catalystIndex < catalystStacks.length) {
                int parallel = CatalystManager.getCatalystParallel(ingredient);
                String catalystName = CatalystManager.getCatalystName(ingredient);

                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));
                
                // 检查是否为USEFUL_INGOT
                if (ingredient.getItem() == ModIngots.USEFUL_INGOT.get()) {
                    tooltip.add(Component.literal("并行数: 无上限").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.literal("• 催化剂不会被消耗").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 无并行数上限").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 能量消耗不会倍增").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.literal("并行数: " + parallel + "倍").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.literal("• 输入物品消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 输出物品数量 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 能量消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                    // 添加黑名单说明
                    tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));
                    tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
                }
            }
        }

        // 保持已弃用方法的重写以避免编译错误
        @Override
        @Deprecated
        public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
            List<Component> tooltip = new ArrayList<>();
            if (catalystIndex < catalystStacks.length) {
                int parallel = CatalystManager.getCatalystParallel(ingredient);
                String catalystName = CatalystManager.getCatalystName(ingredient);

                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));
                
                // 检查是否为USEFUL_INGOT
                if (ingredient.getItem() == ModIngots.USEFUL_INGOT.get()) {
                    tooltip.add(Component.literal("并行数: 无上限").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.literal("• 催化剂不会被消耗").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 无并行数上限").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 能量消耗不会倍增").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.literal("并行数: " + parallel + "倍").withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.literal("• 输入物品消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 输出物品数量 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 能量消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                    // 添加黑名单说明
                    tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));
                    tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
                }
            }
            return tooltip;
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }
    }

    // 催化剂信息类
    public static class CatalystInfo {
        // 这个类可以是空的，因为我们不需要额外的数据
    }
}