package com.sorrowmist.useless.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.client.ItemDecoratorHandler;
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
    private final IGuiHelper guiHelper;

    AdvancedAlloyFurnaceRecipeCategory(IGuiHelper guiHelper) {
        this.width = DISPLAY_WIDTH;
        this.height = DISPLAY_HEIGHT;
        this.guiHelper = guiHelper;

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
    public RecipeType<AdvancedAlloyFurnaceRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, AdvancedAlloyFurnaceRecipe recipe, IFocusGroup focuses) {
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
                    displayStack.setCount(Math.min(count, Integer.MAX_VALUE));
                    displayStacks[j] = displayStack;
                }

                Ingredient displayIngredient = Ingredient.of(displayStacks);

                builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                       .addIngredients(displayIngredient)
                       .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
            }
        }

        // 输出物品槽位 (最多9个) - 3行3列排列
        List<ItemStack> outputs = recipe.outputs();
        for (int i = 0; i < Math.min(outputs.size(), 9); i++) {
            int row = i / 3;
            int col = i % 3;
            int x = OUTPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
            int y = OUTPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .addItemStack(outputs.get(i))
                   .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
        }

        // 输入流体槽 - 使用自定义渲染器显示数量
        List<FluidStack> inputFluids = recipe.inputFluids();
        if (!inputFluids.isEmpty()) {
            int fluidCount = inputFluids.size();
            int fluidWidth = FLUID_INPUT_WIDTH / fluidCount;

            for (int i = 0; i < fluidCount; i++) {
                FluidStack fluid = inputFluids.get(i);
                int x = FLUID_INPUT_X + i * fluidWidth;
                builder.addSlot(RecipeIngredientRole.INPUT, x, FLUID_INPUT_Y)
                       .addFluidStack(fluid.getFluid(), fluid.getAmount())
                       .setCustomRenderer(NeoForgeTypes.FLUID_STACK,
                                          new FluidStackRenderer(fluid.getAmount(), true, fluidWidth,
                                                                 FLUID_INPUT_HEIGHT
                                          )
                       );
            }
        }

        // 输出流体槽 - 使用自定义渲染器显示数量
        List<FluidStack> outputFluids = recipe.outputFluids();
        if (!outputFluids.isEmpty()) {
            int fluidCount = outputFluids.size();
            int fluidWidth = FLUID_OUTPUT_WIDTH / fluidCount;

            for (int i = 0; i < fluidCount; i++) {
                FluidStack fluid = outputFluids.get(i);
                int x = FLUID_OUTPUT_X + i * fluidWidth;
                builder.addSlot(RecipeIngredientRole.OUTPUT, x, FLUID_OUTPUT_Y)
                       .addFluidStack(fluid.getFluid(), fluid.getAmount())
                       .setCustomRenderer(NeoForgeTypes.FLUID_STACK,
                                          new FluidStackRenderer(fluid.getAmount(), true, fluidWidth,
                                                                 FLUID_OUTPUT_HEIGHT
                                          )
                       );
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
            builder.addSlot(RecipeIngredientRole.CATALYST,
                            CATALYST_SLOT_X, CATALYST_SLOT_Y
                   )
                   .addIngredients(Ingredient.of(catalystStacks.toArray(new ItemStack[0])))
                   .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
        }

        // 模具槽位
        if (!recipe.mold().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST,
                            MOLD_SLOT_X, MOLD_SLOT_Y
                   )
                   .addIngredients(recipe.mold())
                   .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AdvancedAlloyFurnaceRecipe recipe,
                                   IFocusGroup focuses) {
        // 不需要额外的遮罩，因为贴图已经包含了所有视觉元素
    }

    @Override
    public void draw(AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
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
        if (!recipe.catalyst().isEmpty() && !this.isUselessIngotRecipe(recipe)) {
            guiGraphics.pose().pushPose();
            float scale = 0.7f;
            guiGraphics.pose().scale(scale, scale, 1.0f);

            String catalystText = "无用锭为可选催化剂";
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

            String moldText = "需要标志物（不会被消耗）";
            int textWidth = minecraft.font.width(moldText);
            int centeredX = (int) ((DISPLAY_WIDTH / 2.0f - textWidth * scale / 2) / scale);
            int y = (int) (MOLD_TEXT_Y / scale);

            guiGraphics.drawString(minecraft.font, moldText, centeredX, y, 0xFF0000, false);
            guiGraphics.pose().popPose();
        }
    }

    // 修改工具提示方法，添加并行数信息
    @Override
    public void getTooltip(ITooltipBuilder tooltip, AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView,
                           double mouseX, double mouseY) {
        // 能量和时间显示的悬停提示
        if (mouseX >= ENERGY_DISPLAY_X && mouseX <= ENERGY_DISPLAY_X + ENERGY_DISPLAY_WIDTH &&
                mouseY >= ENERGY_DISPLAY_Y && mouseY <= ENERGY_DISPLAY_Y + ENERGY_DISPLAY_HEIGHT) {
            tooltip.add(Component.literal("基础能量消耗: " + this.formatEnergy(recipe.energy())));
            tooltip.add(Component.literal("实际能量 = 基础能量 × 并行数"));
        }

        // 时间显示区域
        if (mouseX >= TIME_DISPLAY_X && mouseX <= TIME_DISPLAY_X + TIME_DISPLAY_WIDTH &&
                mouseY >= TIME_DISPLAY_Y && mouseY <= TIME_DISPLAY_Y + TIME_DISPLAY_HEIGHT) {
            tooltip.add(Component.literal("处理时间: " + recipe.processTime() + " ticks"));
            tooltip.add(Component.literal("处理时间不受并行数影响"));
        }

        // 并行数效果说明区域 - 只在显示红色提示文本时（非无用锭配方且允许催化剂）才渲染
        if (mouseX >= PARALLEL_TEXT_X && mouseX <= PARALLEL_TEXT_X + 120 &&
                mouseY >= PARALLEL_TEXT_Y && mouseY <= PARALLEL_TEXT_Y + 10 &&
                !recipe.catalyst().isEmpty() && !isUselessIngotRecipe(recipe)) {
            tooltip.add(Component.literal("并行数效果说明").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("• 输入物品消耗 × 并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 输出物品数量 × 并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 能量消耗 × 并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));
        }

        // 催化剂提示
        if (mouseX >= CATALYST_SLOT_X && mouseX <= CATALYST_SLOT_X + SLOT_SIZE &&
                mouseY >= CATALYST_SLOT_Y && mouseY <= CATALYST_SLOT_Y + SLOT_SIZE) {
            tooltip.add(Component.literal("催化剂").withStyle(ChatFormatting.GOLD));
            
            int targetTier = getTargetUselessIngotTier(recipe);
            if (targetTier > 0) {
                // 无用锭配方：显示跨阶合成信息
                tooltip.add(Component.literal("无用锭配方催化剂说明:").withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.literal("• 可使用高阶无用锭催化低阶合成").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• 例如：用5阶催化4阶，并行数=3").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• 有用锭可提供无限并行").withStyle(ChatFormatting.GREEN));
                tooltip.add(Component.literal("⚠ 催化剂会被消耗（有用锭除外）").withStyle(ChatFormatting.RED));
            } else {
                // 普通配方
                tooltip.add(Component.literal("• 可以使用无用锭提高并行数").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• 催化剂会被消耗").withStyle(ChatFormatting.RED));
                tooltip.add(Component.literal("• 不同等级的无用锭提供不同的并行数").withStyle(ChatFormatting.GRAY));
            }
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
    private String formatItemCount(int count) {
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

    // 格式化流体数量
    private String formatFluidAmount(int amount) {
        if (amount >= 1000000000) {
            return String.format("%.2fG", amount / 1000000000.0);
        } else if (amount >= 1000000) {
            return String.format("%.2fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.2fk", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }

    // 判断是否是无用锭配方（根据输出物品判断）
    private boolean isUselessIngotRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        return getTargetUselessIngotTier(recipe) > 0;
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

    private static class ItemStackRenderer implements IIngredientRenderer<ItemStack> {

        private static String formatItemCountStatic(int count) {
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

        @Override
        public void render(GuiGraphics guiGraphics, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return;

            // 启用深度测试
            RenderSystem.enableDepthTest();

            // 渲染物品图标
            guiGraphics.renderFakeItem(stack, 0, 0);
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            PoseStack pose = guiGraphics.pose();

            // 数量文本缩放
            if (stack.getCount() > 1) {
                String text = formatItemCountStatic(stack.getCount());
                pose.pushPose();
                float scale = 0.65f;
                pose.translate(0, 0, 200.0F);
                pose.scale(scale, scale, 1.0F);

                int textX = Math.round(16.0f / scale) - font.width(text);
                int textY = Math.round(10.0f / scale);

                guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, true);
                pose.popPose();
            }

            // 原版逻辑（耐久条 + 冷却 + 装饰）
            if (stack.isBarVisible()) {
                int l = stack.getBarWidth();
                int i = stack.getBarColor();
                int j = 2;
                int k = 13;
                guiGraphics.fill(RenderType.guiOverlay(), j, k, j + 13, k + 2, 0xFF000000);
                guiGraphics.fill(RenderType.guiOverlay(), j, k, j + l, k + 1, i | 0xFF000000);
            }

            LocalPlayer player = mc.player;
            float f = player == null ? 0.0F :
                    player.getCooldowns().getCooldownPercent(stack.getItem(), mc.getFrameTimeNs());
            if (f > 0.0F) {
                int i1 = Mth.floor(16.0F * (1.0F - f));
                int j1 = i1 + Mth.ceil(16.0F * f);
                guiGraphics.fill(RenderType.guiOverlay(), 0, i1, 16, j1, Integer.MAX_VALUE);
            }

            ItemDecoratorHandler.of(stack).render(guiGraphics, font, stack, 0, 0);
            RenderSystem.disableBlend();
        }

        // 保持已弃用方法的重写以避免编译错误
        @Override
        @Deprecated
        @SuppressWarnings("removal")
        public @NotNull List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
            List<Component> tooltip = stack.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(Minecraft.getInstance().level),
                    Minecraft.getInstance().player,
                    flag
            );

            // 为工具提示添加数量信息（使用缩写）
            if (stack.getCount() > 1) {
                // 找到显示名称的行（通常是第一行），在其后添加数量信息
                if (!tooltip.isEmpty()) {
                    Component displayName = tooltip.getFirst();
                    String countText = formatItemCountStatic(stack.getCount());
                    tooltip.set(0, Component.literal(displayName.getString() + " ×" + countText));
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

    private record FluidStackRenderer(int capacity, boolean showAmount, int width,
                                      int height) implements IIngredientRenderer<FluidStack> {

        private static String formatFluidAmountStatic(int amount) {
            if (amount >= 1000000000) {
                return String.format("%.2fG", amount / 1000000000.0);
            } else if (amount >= 1000000) {
                return String.format("%.2fM", amount / 1000000.0);
            } else if (amount >= 1000) {
                return String.format("%.2fk", amount / 1000.0);
            } else {
                return String.valueOf(amount);
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, FluidStack stack) {
            if (stack == null || stack.isEmpty()) return;

            // 启用深度测试
            RenderSystem.enableDepthTest();

            // 渲染流体填充
            int fluidHeight = (int) (this.height * ((float) stack.getAmount() / this.capacity));
            if (fluidHeight > 0) {
                net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions fluidAttributes =
                        net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid());
                ResourceLocation fluidStillTexture = fluidAttributes.getStillTexture(stack);
                int fluidColor = fluidAttributes.getTintColor(stack);

                if (fluidStillTexture != null) {
                    TextureAtlasSprite fluidSprite = Minecraft.getInstance().getTextureAtlas(
                            net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(fluidStillTexture);

                    float r = ((fluidColor >> 16) & 0xFF) / 255.0F;
                    float g = ((fluidColor >> 8) & 0xFF) / 255.0F;
                    float b = (fluidColor & 0xFF) / 255.0F;
                    float a = ((fluidColor >> 24) & 0xFF) / 255.0F;

                    RenderSystem.setShaderColor(r, g, b, a);
                    RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);

                    int fluidY = this.height - fluidHeight;

                    // 绘制流体纹理
                    for (int i = 0; i < Math.ceil((double) this.width / 16); i++) {
                        for (int j = 0; j < Math.ceil((double) fluidHeight / 16); j++) {
                            int texWidth = Math.min(16, this.width - i * 16);
                            int texHeight = Math.min(16, fluidHeight - j * 16);
                            if (texWidth > 0 && texHeight > 0) {
                                guiGraphics.blit(
                                        i * 16,
                                        fluidY + j * 16,
                                        0,
                                        texWidth, texHeight,
                                        fluidSprite
                                );
                            }
                        }
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }

            // 渲染流体数量文本
            if (this.showAmount && stack.getAmount() > 0) {
                this.renderFluidAmount(guiGraphics, stack);
            }

            // 从UI图抠取覆盖层
            RenderSystem.setShaderTexture(0, GUI_TEXTURE);
            guiGraphics.blit(
                    GUI_TEXTURE,
                    -1, -1, // 向左向上1像素
                    0, 54, // UI图起点
                    this.width + 2, this.height + 2, // 覆盖层大小
                    256, 256 // 纹理实际尺寸
            );

            RenderSystem.disableBlend();
        }

        // 保持已弃用方法的重写以避免编译错误
        @Override
        @Deprecated
        @SuppressWarnings("removal")
        public @NotNull List<Component> getTooltip(FluidStack stack, @NotNull TooltipFlag flag) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(stack.getHoverName());
            tooltip.add(Component.literal("数量: " + formatFluidAmountStatic(stack.getAmount())));
            return tooltip;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, FluidStack stack, TooltipFlag flag) {
            tooltip.add(stack.getHoverName());
            tooltip.add(Component.literal("数量: " + formatFluidAmountStatic(stack.getAmount())));
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        private void renderFluidAmount(GuiGraphics guiGraphics, FluidStack stack) {
            String text = formatFluidAmountStatic(stack.getAmount());
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            PoseStack pose = guiGraphics.pose();

            pose.pushPose();
            float scale = 0.65f;
            pose.translate(0, 0, 200.0F);
            pose.scale(scale, scale, 1.0F);

            int x = Math.round(this.width / scale) - font.width(text);
            int y = Math.round(this.height / scale) - font.lineHeight;

            guiGraphics.drawString(font, text, x, y, 0xFFFFFF, true);
            pose.popPose();
        }
    }
}
