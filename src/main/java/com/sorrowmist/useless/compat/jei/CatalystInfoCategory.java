package com.sorrowmist.useless.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.init.ModTags;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
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
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CatalystInfoCategory implements IRecipeCategory<CatalystInfoCategory.CatalystInfo> {
    public static final RecipeType<CatalystInfo> TYPE =
            RecipeType.create(UselessMod.MODID, "catalyst_info", CatalystInfo.class);

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/jei_catalyst_info_gui.png");

    // 显示尺寸
    private static final int DISPLAY_WIDTH = 150;
    private static final int DISPLAY_HEIGHT = 130;

    // 催化剂并行数映射
    private static final Map<String, Integer> CATALYST_PARALLEL_MAP = new LinkedHashMap<>();

    static {
        // 初始化催化剂并行数映射
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_1", 3);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_2", 9);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_3", 27);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_4", 81);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_5", 243);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_6", 729);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_7", 2187);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_8", 6561);
        CATALYST_PARALLEL_MAP.put("useless_mod:useless_ingot_tier_9", 19683);
        // 有用的锭，无并行数上限
        CATALYST_PARALLEL_MAP.put("useless_mod:useful_ingot", Integer.MAX_VALUE);
    }

    // 催化剂物品堆栈列表
    private final List<ItemStack> catalystStacks;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    private final IGuiHelper guiHelper;

    CatalystInfoCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
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

    // 获取催化剂并行数
    private static int getCatalystParallel(ItemStack stack) {
        if (stack.isEmpty()) return 1;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return CATALYST_PARALLEL_MAP.getOrDefault(itemId, 1);
    }

    // 获取催化剂名称（用于显示）
    private static String getCatalystName(ItemStack stack) {
        if (stack.isEmpty()) return "";

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return switch (itemId) {
            case "useless_mod:useless_ingot_tier_1" -> "一阶无用锭";
            case "useless_mod:useless_ingot_tier_2" -> "二阶无用锭";
            case "useless_mod:useless_ingot_tier_3" -> "三阶无用锭";
            case "useless_mod:useless_ingot_tier_4" -> "四阶无用锭";
            case "useless_mod:useless_ingot_tier_5" -> "五阶无用锭";
            case "useless_mod:useless_ingot_tier_6" -> "六阶无用锭";
            case "useless_mod:useless_ingot_tier_7" -> "七阶无用锭";
            case "useless_mod:useless_ingot_tier_8" -> "八阶无用锭";
            case "useless_mod:useless_ingot_tier_9" -> "九阶无用锭";
            case "useless_mod:possible_useful_ingot" -> "可能有用锭";
            case "useless_mod:useful_ingot" -> "有用锭";
            default -> "";
        };
    }

    @Override
    public RecipeType<CatalystInfo> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, CatalystInfo recipe, IFocusGroup focuses) {
        // 2行5列网格布局
        int startX = 29; // 第一个格子左上角起点X坐标
        int startY = 24; // 第一个格子左上角起点Y坐标
        int spacing = 18; // 每个格子18*18像素

        // 动态计算列数（最多5列）
        int columns = Math.min(this.catalystStacks.size(), 5);
        int rows = (this.catalystStacks.size() + columns - 1) / columns;

        for (int i = 0; i < this.catalystStacks.size(); i++) {
            final int index = i;
            // 计算行和列
            int row = i / columns;
            int col = i % columns;

            int x = startX + col * spacing;
            int y = startY + row * spacing;

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .addItemStack(this.catalystStacks.get(index))
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new CatalystItemStackRenderer(index))
                    .addTooltipCallback(new CatalystTooltipCallback(index));
        }
    }

    @Override
    public void draw(CatalystInfo recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        // 手动绘制背景，确保不缩放
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

    // 催化剂信息类
    public static class CatalystInfo {
        // 这个类可以是空的，因为我们不需要额外的数据
    }

    // 自定义工具提示回调
    private class CatalystTooltipCallback implements IRecipeSlotTooltipCallback {
        private final int catalystIndex;

        CatalystTooltipCallback(int index) {
            this.catalystIndex = index;
        }

        @Override
        public void onTooltip(mezz.jei.api.gui.ingredient.IRecipeSlotView recipeSlotView, List<Component> tooltip) {
            if (this.catalystIndex < CatalystInfoCategory.this.catalystStacks.size()) {
                ItemStack stack = CatalystInfoCategory.this.catalystStacks.get(this.catalystIndex);
                int parallel = getCatalystParallel(stack);
                String catalystName = getCatalystName(stack);

                // 清空原有工具提示，添加自定义工具提示
                tooltip.clear();
                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));

                // 检查是否为USEFUL_INGOT
                if (stack.getItem() == ModItems.USEFUL_INGOT.get()) {
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

        CatalystItemStackRenderer(int index) {
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
            int parallel = getCatalystParallel(stack);
            String parallelText;

            // 检查是否为USEFUL_INGOT
            if (stack.getItem() == ModItems.USEFUL_INGOT.get()) {
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

        // 保持已弃用方法的重写以避免编译错误
        @Override
        @Deprecated
        public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
            List<Component> tooltip = new ArrayList<>();
            if (this.catalystIndex < CatalystInfoCategory.this.catalystStacks.size()) {
                int parallel = getCatalystParallel(ingredient);
                String catalystName = getCatalystName(ingredient);

                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));

                // 检查是否为USEFUL_INGOT
                if (ingredient.getItem() == ModItems.USEFUL_INGOT.get()) {
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

        // 使用新的getTooltip方法
        @Override
        public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, ItemStack ingredient, TooltipFlag tooltipFlag) {
            if (this.catalystIndex < CatalystInfoCategory.this.catalystStacks.size()) {
                int parallel = getCatalystParallel(ingredient);
                String catalystName = getCatalystName(ingredient);

                tooltip.add(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));

                // 检查是否为USEFUL_INGOT
                if (ingredient.getItem() == ModItems.USEFUL_INGOT.get()) {
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

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }
    }
}
