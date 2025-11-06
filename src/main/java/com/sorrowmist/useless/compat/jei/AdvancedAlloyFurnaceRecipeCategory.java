package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AdvancedAlloyFurnaceRecipeCategory implements IRecipeCategory<AdvancedAlloyFurnaceRecipe> {
    public static final RecipeType<AdvancedAlloyFurnaceRecipe> TYPE =
            RecipeType.create(UselessMod.MOD_ID, "advanced_alloy_furnace", AdvancedAlloyFurnaceRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    private final int width;
    private final int height;

    public AdvancedAlloyFurnaceRecipeCategory(IGuiHelper guiHelper) {
        // 只使用机器界面的部分，不包括玩家物品栏
        this.width = 176;  // 机器GUI的宽度
        this.height = 83;  // 机器GUI的高度（不包括玩家物品栏）

        // 从GUI纹理中只截取机器部分
        this.background = guiHelper.createDrawable(
                ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/advanced_alloy_furnace.png"),
                0, 0, width, height  // 只截取机器界面的部分
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

    @Nullable
    @Override
    @Deprecated
    public IDrawable getBackground() {
        return null;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AdvancedAlloyFurnaceRecipe recipe, IFocusGroup focuses) {
        // 修复：为每个输入物品创建带有正确数量的 ItemStack
        for (int i = 0; i < recipe.getInputItems().size(); i++) {
            Ingredient ingredient = recipe.getInputItems().get(i);
            int count = recipe.getInputItemCounts().get(i);

            // 获取 Ingredient 中的所有匹配物品
            ItemStack[] matchingStacks = ingredient.getItems();
            if (matchingStacks.length > 0) {
                // 为每个匹配的物品创建带有正确数量的堆栈
                for (ItemStack stack : matchingStacks) {
                    ItemStack displayStack = stack.copy();
                    displayStack.setCount(count);

                    builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 26 + i * 18, 17)
                            .addItemStack(displayStack);
                }
            } else {
                // 如果没有匹配的物品，添加一个空的占位符
                UselessMod.LOGGER.warn("No matching items for ingredient in recipe: {}", recipe.getId());
            }
        }

        // 添加输入流体（如果存在）
        if (!recipe.getInputFluid().isEmpty()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 8, 17)
                    .addFluidStack(recipe.getInputFluid().getFluid(), recipe.getInputFluid().getAmount())
                    .setFluidRenderer(16000, false, 16, 40);
        }

        // 添加输出物品槽位
        for (int i = 0; i < recipe.getOutputItems().size(); i++) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 26 + i * 18, 53)
                    .addItemStack(recipe.getOutputItems().get(i));
        }

        // 添加输出流体（如果存在）
        if (!recipe.getOutputFluid().isEmpty()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 152, 17)
                    .addFluidStack(recipe.getOutputFluid().getFluid(), recipe.getOutputFluid().getAmount())
                    .setFluidRenderer(16000, false, 16, 40);
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AdvancedAlloyFurnaceRecipe recipe, IFocusGroup focuses) {
        // 可以在这里添加能量消耗等额外信息
    }

    @Override
    public void draw(AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, net.minecraft.client.gui.GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 背景已经在构造函数中创建，这里不需要额外绘制

        // 可以在这里绘制进度箭头等动态元素
        int progressWidth = (int) (24 * (System.currentTimeMillis() % 2000 / 2000.0));
        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/advanced_alloy_furnace.png"),
                76, 35, 176, 0, progressWidth, 16);

        // 绘制能量条（示例）
        int energyHeight = (int) (50 * 0.75); // 75% 能量
        guiGraphics.fill(152, 15 + (50 - energyHeight), 152 + 16, 15 + 50, 0xFF00FF00);
        guiGraphics.renderOutline(152, 15, 16, 50, 0xFF000000);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        // 添加能量信息
        if (mouseX >= 152 && mouseX <= 168 && mouseY >= 15 && mouseY <= 65) {
            tooltip.add(Component.literal("Energy: " + recipe.getEnergy() + " FE"));
            tooltip.add(Component.literal("Process Time: " + recipe.getProcessTime() + " ticks"));
        }

        // 为输入物品添加数量提示
        for (int i = 0; i < recipe.getInputItems().size(); i++) {
            int slotX = 26 + i * 18;
            int slotY = 17;
            if (mouseX >= slotX && mouseX <= slotX + 16 && mouseY >= slotY && mouseY <= slotY + 16) {
                int count = recipe.getInputItemCounts().get(i);
                if (count > 1) {
                    tooltip.add(Component.literal("Count: " + count));
                }
            }
        }
    }

    @Override
    public void onDisplayedIngredientsUpdate(AdvancedAlloyFurnaceRecipe recipe, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
        // 当配方显示的原料更新时调用
    }

    @Override
    public boolean isHandled(AdvancedAlloyFurnaceRecipe recipe) {
        return true;
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName(AdvancedAlloyFurnaceRecipe recipe) {
        return recipe.getId();
    }
}