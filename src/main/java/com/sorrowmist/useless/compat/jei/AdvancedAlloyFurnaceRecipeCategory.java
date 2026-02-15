package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceRecipeCategory implements IRecipeCategory<AdvancedAlloyFurnaceRecipe> {
    public static final RecipeType<AdvancedAlloyFurnaceRecipe> TYPE =
            RecipeType.create(UselessMod.MODID, "advanced_alloy_furnace", AdvancedAlloyFurnaceRecipe.class);

    // 使用JEI渲染贴图
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/jei_advanced_alloy_furnace_gui.png");

    // JEI中显示的背景区域 (212x72)
    private static final int DISPLAY_WIDTH = 212;
    private static final int DISPLAY_HEIGHT = 72;

    // 槽位位置 - 根据贴图调整
    // 输入物品槽位 (9个) - 3行3列排列
    private static final int INPUT_SLOTS_START_X = 1;
    private static final int INPUT_SLOTS_START_Y = 1;
    private static final int INPUT_SLOT_SPACING_X = 18;
    private static final int INPUT_SLOT_SPACING_Y = 18;

    // 输出物品槽位 (9个) - 3行3列排列
    private static final int OUTPUT_SLOTS_START_X = 159;
    private static final int OUTPUT_SLOTS_START_Y = 1;

    // 流体槽位
    private static final int FLUID_INPUT_X = 1;
    private static final int FLUID_INPUT_Y = 55;
    private static final int FLUID_INPUT_WIDTH = 52;
    private static final int FLUID_INPUT_HEIGHT = 16;
    private static final int FLUID_OUTPUT_X = 159;
    private static final int FLUID_OUTPUT_Y = 55;
    private static final int FLUID_OUTPUT_WIDTH = 52;
    private static final int FLUID_OUTPUT_HEIGHT = 16;

    // 催化剂和模具槽位
    private static final int CATALYST_SLOT_X = 80;
    private static final int CATALYST_SLOT_Y = 55;
    private static final int MOLD_SLOT_X = 116;
    private static final int MOLD_SLOT_Y = 55;

    // 进度条位置和尺寸
    private static final int PROGRESS_BAR_X = 90;
    private static final int PROGRESS_BAR_Y = 23;
    private static final int PROGRESS_BAR_WIDTH = 32;
    private static final int PROGRESS_BAR_HEIGHT = 8;
    private static final int PROGRESS_BAR_SOURCE_X = 90;
    private static final int PROGRESS_BAR_SOURCE_Y = 73;

    // 能量显示区域
    private static final int ENERGY_DISPLAY_X = 75;
    private static final int ENERGY_DISPLAY_Y = 0;
    private static final int ENERGY_DISPLAY_WIDTH = 62;
    private static final int ENERGY_DISPLAY_HEIGHT = 7;

    // 时间显示区域
    private static final int TIME_DISPLAY_X = 78;
    private static final int TIME_DISPLAY_Y = 41;
    private static final int TIME_DISPLAY_WIDTH = 56;
    private static final int TIME_DISPLAY_HEIGHT = 10;

    // 文字提示位置
    private static final int CATALYST_TEXT_Y = 13;
    private static final int MOLD_TEXT_Y = 32;

    // 并行数显示位置
    private static final int PARALLEL_TEXT_X = 57;
    private static final int PARALLEL_TEXT_Y = 5;

    // 槽位实际渲染尺寸（MC默认）
    private static final int SLOT_SIZE = 16;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;
    private final int width;
    private final int height;

    AdvancedAlloyFurnaceRecipeCategory(IGuiHelper guiHelper) {
        this.width = DISPLAY_WIDTH;
        this.height = DISPLAY_HEIGHT;

        // 创建背景，使用JEI贴图
        this.background = guiHelper.createDrawable(
                GUI_TEXTURE,
                0, 0, // 纹理起始位置
                DISPLAY_WIDTH, DISPLAY_HEIGHT // 显示尺寸
        );

        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()));
        this.title = Component.translatable("block.useless_mod.advanced_alloy_furnace_block");
    }

    @Override
    public @NotNull RecipeType<AdvancedAlloyFurnaceRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return this.title;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull AdvancedAlloyFurnaceRecipe recipe, @NotNull IFocusGroup focuses) {
        // 计算配方相关的并行数信息
        int targetTier = getTargetUselessIngotTier(recipe);
        boolean isUselessRecipe = targetTier > 0;

        // 输入物品槽位 (最多9个) - 3行3列排列
        List<CountedIngredient> inputs = recipe.inputs();
        for (int i = 0; i < Math.min(inputs.size(), 9); i++) {
            int row = i / 3;
            int col = i % 3;
            int x = INPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
            int y = INPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

            CountedIngredient countedIngredient = inputs.get(i);
            Ingredient ingredient = countedIngredient.ingredient();
            int count = (int) countedIngredient.count();

            ItemStack[] matchingStacks = ingredient.getItems();
            if (matchingStacks.length > 0) {
                ItemStack[] displayStacks = new ItemStack[matchingStacks.length];
                for (int j = 0; j < matchingStacks.length; j++) {
                    ItemStack displayStack = matchingStacks[j].copy();
                    displayStack.setCount(count);
                    displayStacks[j] = displayStack;
                }

                Ingredient displayIngredient = Ingredient.of(displayStacks);

                builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                       .addIngredients(displayIngredient)
                       .addRichTooltipCallback((slot, tooltip) -> {
                           if (count > 1) {
                               tooltip.add(Component.translatable("jei.useless_mod.tooltip.amount", formatCount(count)).withStyle(ChatFormatting.GRAY));
                           }
                       });
            }
        }

        // 输出物品槽位 (最多9个) - 3行3列排列
        List<ItemStack> outputs = recipe.outputs();
        for (int i = 0; i < Math.min(outputs.size(), 9); i++) {
            int row = i / 3;
            int col = i % 3;
            int x = OUTPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
            int y = OUTPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

            ItemStack outputStack = outputs.get(i);
            int count = outputStack.getCount();

            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .addItemStack(outputStack)
                    .addRichTooltipCallback((slot, tooltip) -> {
                        if (count > 1) {
                            tooltip.add(Component.translatable("jei.useless_mod.tooltip.amount", formatCount(count)).withStyle(ChatFormatting.GRAY));
                        }
                    });
        }

        // 输入流体槽 - 使用JEI内置流体渲染器
        List<FluidStack> inputFluids = recipe.inputFluids();
        if (!inputFluids.isEmpty()) {
            int fluidCount = inputFluids.size();
            int fluidWidth = FLUID_INPUT_WIDTH / fluidCount;

            for (int i = 0; i < fluidCount; i++) {
                FluidStack fluid = inputFluids.get(i);
                int x = FLUID_INPUT_X + i * fluidWidth;
                int amount = fluid.getAmount();

                builder.addSlot(RecipeIngredientRole.INPUT, x, FLUID_INPUT_Y)
                       .setFluidRenderer(amount, false, fluidWidth, FLUID_INPUT_HEIGHT)
                       .addFluidStack(fluid.getFluid(), amount)
                       .addRichTooltipCallback((slot, tooltip) -> {
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.amount.mb", formatCount(amount)).withStyle(ChatFormatting.GRAY));
                       });
            }
        }

        // 输出流体槽 - 使用JEI内置流体渲染器
        List<FluidStack> outputFluids = recipe.outputFluids();
        if (!outputFluids.isEmpty()) {
            int fluidCount = outputFluids.size();
            int fluidWidth = FLUID_OUTPUT_WIDTH / fluidCount;

            for (int i = 0; i < fluidCount; i++) {
                FluidStack fluid = outputFluids.get(i);
                int x = FLUID_OUTPUT_X + i * fluidWidth;
                int amount = fluid.getAmount();

                builder.addSlot(RecipeIngredientRole.OUTPUT, x, FLUID_OUTPUT_Y)
                       .setFluidRenderer(amount, false, fluidWidth, FLUID_OUTPUT_HEIGHT)
                       .addFluidStack(fluid.getFluid(), amount)
                       .addRichTooltipCallback((slot, tooltip) -> {
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.amount.mb", formatCount(amount)).withStyle(ChatFormatting.GRAY));
                       });
            }
        }

        // 催化剂槽位
        List<ItemStack> catalystStacks = new ArrayList<>();

        if (isUselessRecipe && !recipe.catalyst().isEmpty()) {
            // 无用锭配方且有特定催化剂要求：显示配方中定义的特定催化剂
            ItemStack[] recipeCatalysts = recipe.catalyst().getItems();
            for (ItemStack stack : recipeCatalysts) {
                ItemStack displayStack = stack.copy();
                displayStack.setCount(recipe.catalystUses() > 0 ? recipe.catalystUses() : 1);
                catalystStacks.add(displayStack);
            }
        } else if (!isUselessRecipe) {
            // 普通配方：使用ModTags.CATALYSTS轮询显示所有催化剂（可选）
            BuiltInRegistries.ITEM.getTag(ModTags.CATALYSTS).ifPresent(tag -> {
                for (var holder : tag) {
                    ItemStack displayStack = new ItemStack(holder.value());
                    displayStack.setCount(1);
                    catalystStacks.add(displayStack);
                }
            });
        }

        if (!catalystStacks.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, CATALYST_SLOT_X, CATALYST_SLOT_Y)
                   .addIngredients(Ingredient.of(catalystStacks.toArray(new ItemStack[0])))
                   .addRichTooltipCallback((slot, tooltip) -> {
                       tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst").withStyle(ChatFormatting.GOLD));
                       int tier = getTargetUselessIngotTier(recipe);
                       if (tier > 0) {
                           // 无用锭配方：显示跨阶合成信息
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.useless_ingot.title").withStyle(ChatFormatting.YELLOW));
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.useless_ingot.desc1").withStyle(ChatFormatting.GRAY));
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.useless_ingot.desc2").withStyle(ChatFormatting.GREEN));
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.useless_ingot.warning").withStyle(ChatFormatting.RED));
                       } else if (!isUselessRecipe) {
                           // 普通配方
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.normal.desc1").withStyle(ChatFormatting.GRAY));
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.normal.warning").withStyle(ChatFormatting.RED));
                           tooltip.add(Component.translatable("jei.useless_mod.tooltip.catalyst.normal.desc2").withStyle(ChatFormatting.GRAY));
                       }
                   });
        }

        // 模具槽位
        if (!recipe.mold().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, MOLD_SLOT_X, MOLD_SLOT_Y)
                   .addIngredients(recipe.mold());
        }
    }

    @Override
    public void draw(@NotNull AdvancedAlloyFurnaceRecipe recipe, @NotNull IRecipeSlotsView recipeSlotsView, @NotNull GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 绘制背景
        this.background.draw(guiGraphics);

        // 绘制进度条 - 2秒完成一次合成进度
        long currentTime = System.currentTimeMillis();
        float progressRatio = (currentTime % 2000) / 2000.0f; // 2秒周期

        int progressWidth = (int) (PROGRESS_BAR_WIDTH * progressRatio);
        if (progressWidth > 0) {
            guiGraphics.blit(GUI_TEXTURE,
                             PROGRESS_BAR_X, PROGRESS_BAR_Y,
                             PROGRESS_BAR_SOURCE_X, PROGRESS_BAR_SOURCE_Y,
                             progressWidth, PROGRESS_BAR_HEIGHT,
                             256, 256
            );
        }

        // 绘制能量和时间信息
        Minecraft minecraft = Minecraft.getInstance();

        // 格式化能量显示
        String energyText = this.formatEnergy(recipe.energy());
        String timeText = recipe.processTime() + " ticks";

        // 在能量显示区域居中显示能量（绿色）- 使用缩小字体
        guiGraphics.pose().pushPose();
        float energyScale = 0.8f;
        guiGraphics.pose().scale(energyScale, energyScale, 1.0f);
        int energyTextWidth = minecraft.font.width(energyText);
        int energyX = (int) ((ENERGY_DISPLAY_X + (ENERGY_DISPLAY_WIDTH - energyTextWidth * energyScale) / 2) / energyScale);
        int energyY = (int) (ENERGY_DISPLAY_Y / energyScale);
        guiGraphics.drawString(minecraft.font, energyText, energyX, energyY, 0x00FF00, false);
        guiGraphics.pose().popPose();

        // 在时间显示区域居中显示时间（绿色）- 使用缩小字体，位置往下移动
        guiGraphics.pose().pushPose();
        float timeScale = 0.8f;
        guiGraphics.pose().scale(timeScale, timeScale, 1.0f);
        int timeTextWidth = minecraft.font.width(timeText);
        int timeX = (int) ((TIME_DISPLAY_X + (TIME_DISPLAY_WIDTH - timeTextWidth * timeScale) / 2) / timeScale);
        int timeY = (int) ((TIME_DISPLAY_Y + 1) / timeScale);
        guiGraphics.drawString(minecraft.font, timeText, timeX, timeY, 0x00FF00, false);
        guiGraphics.pose().popPose();

        // 只有当配方允许通用催化剂（非特定催化剂）时才显示提示
        // 无用锭配方有特定的催化剂要求，不显示此提示
        if (!recipe.catalyst().isEmpty() && this.isUselessIngotRecipe(recipe)) {
            guiGraphics.pose().pushPose();
            float scale = 0.7f;
            guiGraphics.pose().scale(scale, scale, 1.0f);

            String catalystText = Component.translatable("jei.useless_mod.gui.catalyst_optional").getString();
            int textWidth = minecraft.font.width(catalystText);
            int centeredX = (int) ((DISPLAY_WIDTH / 2.0f - textWidth * scale / 2) / scale);
            int y = (int) (CATALYST_TEXT_Y / scale);

            guiGraphics.drawString(minecraft.font, catalystText, centeredX, y, 0xFF0000, false);
            guiGraphics.pose().popPose();
        }

        // 模具提示
        if (!recipe.mold().isEmpty()) {
            guiGraphics.pose().pushPose();
            float scale = 0.7f;
            guiGraphics.pose().scale(scale, scale, 1.0f);

            String moldText = Component.translatable("jei.useless_mod.gui.mold_required").getString();
            int textWidth = minecraft.font.width(moldText);
            int centeredX = (int) ((DISPLAY_WIDTH / 2.0f - textWidth * scale / 2) / scale);
            int y = (int) (MOLD_TEXT_Y / scale);

            guiGraphics.drawString(minecraft.font, moldText, centeredX, y, 0xFF0000, false);
            guiGraphics.pose().popPose();
        }
    }

    // 修改工具提示方法，添加并行数信息
    @Override
    public void getTooltip(@NotNull ITooltipBuilder tooltip,
                           @NotNull AdvancedAlloyFurnaceRecipe recipe,
                           @NotNull IRecipeSlotsView recipeSlotsView,
                           double mouseX, double mouseY) {
        // 能量和时间显示的悬停提示
        if (mouseX >= ENERGY_DISPLAY_X && mouseX <= ENERGY_DISPLAY_X + ENERGY_DISPLAY_WIDTH &&
                mouseY >= ENERGY_DISPLAY_Y && mouseY <= ENERGY_DISPLAY_Y + ENERGY_DISPLAY_HEIGHT) {
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.energy.base", this.formatEnergy(recipe.energy())));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.energy.actual"));
        }

        // 时间显示区域
        if (mouseX >= TIME_DISPLAY_X && mouseX <= TIME_DISPLAY_X + TIME_DISPLAY_WIDTH &&
                mouseY >= TIME_DISPLAY_Y && mouseY <= TIME_DISPLAY_Y + TIME_DISPLAY_HEIGHT) {
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.process_time", recipe.processTime()));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.process_time.note"));
        }

        // 并行数效果说明区域 - 只在显示红色提示文本时（非无用锭配方且允许催化剂）才渲染
        if (mouseX >= PARALLEL_TEXT_X && mouseX <= PARALLEL_TEXT_X + 120 &&
                mouseY >= PARALLEL_TEXT_Y && mouseY <= PARALLEL_TEXT_Y + 10 &&
                !recipe.catalyst().isEmpty() && isUselessIngotRecipe(recipe)) {
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.title").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc3").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.desc4").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei.useless_mod.tooltip.parallel.warning").withStyle(ChatFormatting.RED));
        }
    }

    // 格式化能量显示
    private String formatEnergy(int energy) {
        if (energy >= 1000000000) {
            return String.format("%.2fG FE", energy / 1000000000.0);
        } else if (energy >= 1000000) {
            return String.format("%.2fM FE", energy / 1000000.0);
        } else if (energy >= 1000) {
            return String.format("%.2fk FE", energy / 1000.0);
        } else {
            return energy + " FE";
        }
    }

    // 格式化物品数量
    private String formatCount(int count) {
        if (count >= 1000000000) {
            return String.format("%.2fG", count / 1000000000.0);
        } else if (count >= 1000000) {
            return String.format("%.2fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format("%.2fk", count / 1000.0);
        } else {
            return String.valueOf(count);
        }
    }

    // 判断是否是无用锭配方（根据输出物品判断）
    private boolean isUselessIngotRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        return getTargetUselessIngotTier(recipe) <= 0;
    }

    // 获取目标无用锭等级
    private int getTargetUselessIngotTier(AdvancedAlloyFurnaceRecipe recipe) {
        List<ItemStack> outputs = recipe.outputs();
        if (outputs.isEmpty()) return 0;

        for (ItemStack output : outputs) {
            int tier = CatalystParallelManager.getTargetUselessIngotTier(output);
            if (tier > 0) {
                return tier;
            }
        }
        return 0;
    }
}
