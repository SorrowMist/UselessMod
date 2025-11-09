package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class AdvancedAlloyFurnaceRecipeCategory implements IRecipeCategory<AdvancedAlloyFurnaceRecipe> {
    public static final RecipeType<AdvancedAlloyFurnaceRecipe> TYPE =
            RecipeType.create(UselessMod.MOD_ID, "advanced_alloy_furnace", AdvancedAlloyFurnaceRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    private final int width;
    private final int height;
    private final IGuiHelper guiHelper;

    // 使用新的JEI渲染贴图
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/jei_advanced_alloy_furnace_gui.png");

    // 进度条贴图
    private static final ResourceLocation PROGRESS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/jei_arrow_advanced_alloy_furnace_gui.png");

    // JEI中显示的背景区域 (191x54)
    private static final int DISPLAY_WIDTH = 191;
    private static final int DISPLAY_HEIGHT = 54;

    // 槽位位置 - 根据新贴图调整
    // 输入物品槽位 (6个) - 3行2列排列
    private static final int INPUT_SLOTS_START_X = 1;
    private static final int INPUT_SLOTS_START_Y = 1;
    private static final int INPUT_SLOT_SPACING_X = 18;
    private static final int INPUT_SLOT_SPACING_Y = 18;

    // 输出物品槽位 (6个) - 3行2列排列
    private static final int OUTPUT_SLOTS_START_X = 138;
    private static final int OUTPUT_SLOTS_START_Y = 1;

    // 流体槽位
    private static final int FLUID_INPUT_X = 37;
    private static final int FLUID_INPUT_Y = 1;

    private static final int FLUID_OUTPUT_X = 174;
    private static final int FLUID_OUTPUT_Y = 1;

    // 催化剂和模具槽位
    private static final int CATALYST_SLOT_X = 37;
    private static final int CATALYST_SLOT_Y = 19;

    private static final int MOLD_SLOT_X = 37;
    private static final int MOLD_SLOT_Y = 37;

    // 进度条位置和尺寸
    private static final int PROGRESS_BAR_X = 79;
    private static final int PROGRESS_BAR_Y = 23;
    private static final int PROGRESS_BAR_WIDTH = 33;
    private static final int PROGRESS_BAR_HEIGHT = 8;

    // 能量显示区域
    private static final int ENERGY_DISPLAY_X = 68;
    private static final int ENERGY_DISPLAY_Y = 0;
    private static final int ENERGY_DISPLAY_WIDTH = 55;
    private static final int ENERGY_DISPLAY_HEIGHT = 5;

    // 时间显示区域
    private static final int TIME_DISPLAY_X = 79;
    private static final int TIME_DISPLAY_Y = 45;
    private static final int TIME_DISPLAY_WIDTH = 33;
    private static final int TIME_DISPLAY_HEIGHT = 6;

    // 文字提示位置 - 向左移动5像素
    private static final int CATALYST_TEXT_X = 57; // 62 - 5
    private static final int CATALYST_TEXT_Y = 13;

    private static final int MOLD_TEXT_X = 57; // 62 - 5
    private static final int MOLD_TEXT_Y = 32;

    // 槽位实际渲染尺寸（MC默认）
    private static final int SLOT_SIZE = 16;

    public AdvancedAlloyFurnaceRecipeCategory(IGuiHelper guiHelper) {
        this.width = DISPLAY_WIDTH;
        this.height = DISPLAY_HEIGHT;
        this.guiHelper = guiHelper;

        // 创建背景，使用新的JEI贴图
        this.background = guiHelper.createDrawable(
                GUI_TEXTURE,
                0, 0, // 纹理起始位置
                DISPLAY_WIDTH, DISPLAY_HEIGHT // 显示尺寸
        );

        this.icon = guiHelper.createDrawableItemStack(new ItemStack(AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK.get()));
        this.title = Component.translatable("block.useless_mod.advanced_alloy_furnace_block");
    }

    @Override
    public RecipeType<AdvancedAlloyFurnaceRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AdvancedAlloyFurnaceRecipe recipe, IFocusGroup focuses) {
        // 输入物品槽位 (6个) - 3行2列排列
        for (int i = 0; i < Math.min(recipe.getInputItems().size(), 6); i++) {
            Ingredient ingredient = recipe.getInputItems().get(i);
            int count = recipe.getInputItemCounts().get(i);

            ItemStack[] matchingStacks = ingredient.getItems();
            if (matchingStacks.length > 0) {
                ItemStack[] displayStacks = new ItemStack[matchingStacks.length];
                for (int j = 0; j < matchingStacks.length; j++) {
                    ItemStack displayStack = matchingStacks[j].copy();
                    displayStack.setCount(count);
                    displayStacks[j] = displayStack;
                }

                Ingredient displayIngredient = Ingredient.of(displayStacks);

                // 计算槽位位置：3行2列
                int row = i / 2;
                int col = i % 2;
                int x = INPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
                int y = INPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

                builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, x, y)
                        .addIngredients(displayIngredient);
            }
        }

        // 输入流体槽 - 满格显示
        if (!recipe.getInputFluid().isEmpty()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                            FLUID_INPUT_X, FLUID_INPUT_Y)
                    .addFluidStack(recipe.getInputFluid().getFluid(), recipe.getInputFluid().getAmount())
                    .setFluidRenderer(recipe.getInputFluid().getAmount(), true, SLOT_SIZE, SLOT_SIZE);
        }

        // 输出物品槽位 (6个) - 3行2列排列
        for (int i = 0; i < Math.min(recipe.getOutputItems().size(), 6); i++) {
            // 计算槽位位置：3行2列
            int row = i / 2;
            int col = i % 2;
            int x = OUTPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
            int y = OUTPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, x, y)
                    .addItemStack(recipe.getOutputItems().get(i));
        }

        // 输出流体槽 - 满格显示
        if (!recipe.getOutputFluid().isEmpty()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                            FLUID_OUTPUT_X, FLUID_OUTPUT_Y)
                    .addFluidStack(recipe.getOutputFluid().getFluid(), recipe.getOutputFluid().getAmount())
                    .setFluidRenderer(recipe.getOutputFluid().getAmount(), true, SLOT_SIZE, SLOT_SIZE);
        }

        // 催化剂槽位
        if (recipe.requiresCatalyst()) {
            ItemStack[] catalystStacks = recipe.getCatalyst().getItems();
            if (catalystStacks.length > 0) {
                ItemStack[] displayCatalystStacks = new ItemStack[catalystStacks.length];
                for (int j = 0; j < catalystStacks.length; j++) {
                    ItemStack displayStack = catalystStacks[j].copy();
                    displayStack.setCount(recipe.getCatalystCount());
                    displayCatalystStacks[j] = displayStack;
                }

                builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.CATALYST,
                                CATALYST_SLOT_X, CATALYST_SLOT_Y)
                        .addIngredients(Ingredient.of(displayCatalystStacks));
            }
        }

        // 模具槽位
        if (recipe.requiresMold()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.CATALYST,
                            MOLD_SLOT_X, MOLD_SLOT_Y)
                    .addIngredients(recipe.getMold());
        }
    }

    @Override
    public void draw(AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 绘制背景
        background.draw(guiGraphics);

        // 绘制进度条 - 2秒完成一次合成进度
        long currentTime = System.currentTimeMillis();
        float progressRatio = (currentTime % 2000) / 2000.0f; // 2秒周期

        int progressWidth = (int) (PROGRESS_BAR_WIDTH * progressRatio);
        if (progressWidth > 0) {
            guiGraphics.blit(PROGRESS_TEXTURE,
                    PROGRESS_BAR_X, PROGRESS_BAR_Y,
                    0, 0,
                    progressWidth, PROGRESS_BAR_HEIGHT,
                    PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);
        }

        // 绘制能量和时间信息
        Minecraft minecraft = Minecraft.getInstance();

        // 格式化能量显示
        String energyText = formatNumber(recipe.getEnergy()) + " FE";
        String timeText = recipe.getProcessTime() + " ticks";

        // 在能量显示区域居中显示能量（绿色）
        int energyTextWidth = minecraft.font.width(energyText);
        int energyX = ENERGY_DISPLAY_X + (ENERGY_DISPLAY_WIDTH - energyTextWidth) / 2;
        guiGraphics.drawString(minecraft.font, energyText, energyX, ENERGY_DISPLAY_Y, 0x00FF00, false);

        // 在时间显示区域居中显示时间（绿色）
        int timeTextWidth = minecraft.font.width(timeText);
        int timeX = TIME_DISPLAY_X + (TIME_DISPLAY_WIDTH - timeTextWidth) / 2;
        guiGraphics.drawString(minecraft.font, timeText, timeX, TIME_DISPLAY_Y, 0x00FF00, false);

        // 绘制需求状态文字提示（红色）- 缩小字体
        if (recipe.requiresCatalyst()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);
            guiGraphics.drawString(minecraft.font, "需要催化剂（会被消耗）",
                    (int)(CATALYST_TEXT_X / 0.7f), (int)(CATALYST_TEXT_Y / 0.7f), 0xFF0000, false);
            guiGraphics.pose().popPose();
        }

        if (recipe.requiresMold()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);
            guiGraphics.drawString(minecraft.font, "需要模具（不会被消耗）",
                    (int)(MOLD_TEXT_X / 0.7f), (int)(MOLD_TEXT_Y / 0.7f), 0xFF0000, false);
            guiGraphics.pose().popPose();
        }

        // 注意：已取消流体数量显示
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AdvancedAlloyFurnaceRecipe recipe, IFocusGroup focuses) {
        // 不需要额外的遮罩，因为新贴图已经包含了所有视觉元素
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        // 取消催化槽、流体槽和模具槽的悬停提示

        // 只保留能量和时间显示的悬停提示
        // 检查鼠标是否在能量显示区域
        if (mouseX >= ENERGY_DISPLAY_X && mouseX <= ENERGY_DISPLAY_X + ENERGY_DISPLAY_WIDTH &&
                mouseY >= ENERGY_DISPLAY_Y && mouseY <= ENERGY_DISPLAY_Y + ENERGY_DISPLAY_HEIGHT) {
            tooltip.add(Component.literal("能量消耗: " + formatNumber(recipe.getEnergy()) + " FE"));
        }

        // 检查鼠标是否在时间显示区域
        if (mouseX >= TIME_DISPLAY_X && mouseX <= TIME_DISPLAY_X + TIME_DISPLAY_WIDTH &&
                mouseY >= TIME_DISPLAY_Y && mouseY <= TIME_DISPLAY_Y + TIME_DISPLAY_HEIGHT) {
            tooltip.add(Component.literal("处理时间: " + recipe.getProcessTime() + " ticks"));
        }
    }

    // 格式化数字显示（能量）
    private String formatNumber(int number) {
        if (number >= 1000000000) {
            return (number / 1000000000) + "G";
        } else if (number >= 1000000) {
            return (number / 1000000) + "M";
        } else if (number >= 1000) {
            return (number / 1000) + "K";
        } else {
            return String.valueOf(number);
        }
    }
}