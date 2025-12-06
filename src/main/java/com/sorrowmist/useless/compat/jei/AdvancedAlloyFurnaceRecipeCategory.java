package com.sorrowmist.useless.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.CatalystManager;
import com.sorrowmist.useless.registry.ModIngots;
import com.sorrowmist.useless.utils.NumberFormatUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.ItemDecoratorHandler;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceRecipeCategory implements IRecipeCategory<AdvancedAlloyFurnaceRecipe> {
    public static final RecipeType<AdvancedAlloyFurnaceRecipe> TYPE =
            RecipeType.create(UselessMod.MOD_ID, "advanced_alloy_furnace", AdvancedAlloyFurnaceRecipe.class);
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

                int row = i / 2;
                int col = i % 2;
                int x = INPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
                int y = INPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

                builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                        .addIngredients(displayIngredient)
                        .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
            }
        }

        // 输出物品槽位 (6个) - 3行2列排列
        for (int i = 0; i < Math.min(recipe.getOutputItems().size(), 6); i++) {
            // 计算槽位位置：3行2列
            int row = i / 2;
            int col = i % 2;
            int x = OUTPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X;
            int y = OUTPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y;

            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, x, y)
                    .addItemStack(recipe.getOutputItems().get(i))
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
        }

        // 输入流体槽 - 使用自定义渲染器显示数量
        if (!recipe.getInputFluid().isEmpty()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                            FLUID_INPUT_X, FLUID_INPUT_Y)
                    .addFluidStack(recipe.getInputFluid().getFluid(), recipe.getInputFluid().getAmount())
                    .setCustomRenderer(mezz.jei.api.forge.ForgeTypes.FLUID_STACK,
                            new FluidStackRenderer(recipe.getInputFluid().getAmount(), true, SLOT_SIZE, SLOT_SIZE));
        }

        // 输出流体槽 - 使用自定义渲染器显示数量
        if (!recipe.getOutputFluid().isEmpty()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                            FLUID_OUTPUT_X, FLUID_OUTPUT_Y)
                    .addFluidStack(recipe.getOutputFluid().getFluid(), recipe.getOutputFluid().getAmount())
                    .setCustomRenderer(mezz.jei.api.forge.ForgeTypes.FLUID_STACK,
                            new FluidStackRenderer(recipe.getOutputFluid().getAmount(), true, SLOT_SIZE, SLOT_SIZE));
        }

        // 催化剂槽位：只在配方需要催化剂时显示
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
                        .addIngredients(Ingredient.of(displayCatalystStacks))
                        .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
            }
        }
        // 模具槽位
        if (recipe.requiresMold()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.CATALYST,
                            MOLD_SLOT_X, MOLD_SLOT_Y)
                    .addIngredients(recipe.getMold())
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, new ItemStackRenderer());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AdvancedAlloyFurnaceRecipe recipe, IFocusGroup focuses) {
        // 不需要额外的遮罩，因为新贴图已经包含了所有视觉元素
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

        // 格式化能量显示 - 使用新的格式化方法
        String energyText = NumberFormatUtil.formatEnergy(recipe.getEnergy());
        String timeText = recipe.getProcessTime() + " ticks";

        // 在能量显示区域居中显示能量（绿色）
        int energyTextWidth = minecraft.font.width(energyText);
        int energyX = ENERGY_DISPLAY_X + (ENERGY_DISPLAY_WIDTH - energyTextWidth) / 2;
        guiGraphics.drawString(minecraft.font, energyText, energyX, ENERGY_DISPLAY_Y, 0x00FF00, false);

        // 在时间显示区域居中显示时间（绿色）
        int timeTextWidth = minecraft.font.width(timeText);
        int timeX = TIME_DISPLAY_X + (TIME_DISPLAY_WIDTH - timeTextWidth) / 2;
        guiGraphics.drawString(minecraft.font, timeText, timeX, TIME_DISPLAY_Y, 0x00FF00, false);

        // 修改：只有当配方允许催化剂时才显示提示
        if (recipe.requiresCatalyst() && recipe.isCatalystAllowed()) {
            guiGraphics.pose().pushPose();
            float scale = 0.7f;
            guiGraphics.pose().scale(scale, scale, 1.0f);

            String catalystText = "无用锭为可选催化剂";
            int textWidth = minecraft.font.width(catalystText);
            // 在整张 JEI 背景宽度内居中
            int centeredX = (int) ((DISPLAY_WIDTH / 2.0f - textWidth * scale / 2) / scale);
            int y = (int) (CATALYST_TEXT_Y / scale);

            guiGraphics.drawString(minecraft.font, catalystText, centeredX, y, 0xFF0000, false);
            guiGraphics.pose().popPose();
        }

// 模具提示
        if (recipe.requiresMold()) {
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

        // 注意：已取消流体数量显示
    }

    // 移除旧的 CATALYST_PARALLEL_MAP，使用统一的 CatalystManager
    private int getCatalystParallel(AdvancedAlloyFurnaceRecipe recipe) {
        // 即使配方不需要催化剂，也可以使用催化剂提高并行数
        // 获取催化剂的第一个物品作为代表
        ItemStack[] catalystStacks = recipe.getCatalyst().getItems();
        if (catalystStacks.length > 0) {
            return CatalystManager.getCatalystParallel(catalystStacks[0]);
        }

        // 如果配方没有定义催化剂，返回默认值1
        return 1;
    }

    // 修改工具提示方法，添加并行数信息
    @Override
    public void getTooltip(ITooltipBuilder tooltip, AdvancedAlloyFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        // 能量和时间显示的悬停提示
        if (mouseX >= ENERGY_DISPLAY_X && mouseX <= ENERGY_DISPLAY_X + ENERGY_DISPLAY_WIDTH &&
                mouseY >= ENERGY_DISPLAY_Y && mouseY <= ENERGY_DISPLAY_Y + ENERGY_DISPLAY_HEIGHT) {
            tooltip.add(Component.literal("基础能量消耗: " + NumberFormatUtil.formatEnergy(recipe.getEnergy())));
            tooltip.add(Component.literal("实际能量 = 基础能量 × 并行数"));
        }

        // 时间显示区域
        if (mouseX >= TIME_DISPLAY_X && mouseX <= TIME_DISPLAY_X + TIME_DISPLAY_WIDTH &&
                mouseY >= TIME_DISPLAY_Y && mouseY <= TIME_DISPLAY_Y + TIME_DISPLAY_HEIGHT) {
            tooltip.add(Component.literal("处理时间: " + recipe.getProcessTime() + " ticks"));
            tooltip.add(Component.literal("处理时间不受并行数影响"));
        }

        // 并行数效果说明区域
        if (mouseX >= PARALLEL_TEXT_X && mouseX <= PARALLEL_TEXT_X + 120 &&
                mouseY >= PARALLEL_TEXT_Y && mouseY <= PARALLEL_TEXT_Y + 10) {
            tooltip.add(Component.literal("并行数效果说明").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("• 输入物品消耗 × 并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 输出物品数量 × 并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 能量消耗 × 并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));
        }
        
        // 催化剂提示：所有配方都可以使用无用锭作为催化剂
        if (mouseX >= CATALYST_SLOT_X && mouseX <= CATALYST_SLOT_X + SLOT_SIZE &&
                mouseY >= CATALYST_SLOT_Y && mouseY <= CATALYST_SLOT_Y + SLOT_SIZE) {
            tooltip.add(Component.literal("催化剂").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("• 可以使用无用锭提高并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 催化剂会被消耗").withStyle(ChatFormatting.RED));
            tooltip.add(Component.literal("• 不同等级的无用锭提供不同的并行数").withStyle(ChatFormatting.GRAY));
        }
    }

    private static class ItemStackRenderer implements IIngredientRenderer<ItemStack> {

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

            // 数量文本缩放 - 使用新的格式化方法
            if (stack.getCount() > 1) {
                String text = NumberFormatUtil.formatItemCount(stack.getCount());
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
                    player.getCooldowns().getCooldownPercent(stack.getItem(), mc.getFrameTime());
            if (f > 0.0F) {
                int i1 = Mth.floor(16.0F * (1.0F - f));
                int j1 = i1 + Mth.ceil(16.0F * f);
                guiGraphics.fill(RenderType.guiOverlay(), 0, i1, 16, j1, Integer.MAX_VALUE);
            }

            ItemDecoratorHandler.of(stack).render(guiGraphics, font, stack, 0, 0);
            RenderSystem.disableBlend();
        }

        @Override
        public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
            List<Component> tooltip = stack.getTooltipLines(Minecraft.getInstance().player, flag);

            // 为工具提示添加数量信息（使用缩写）
            if (stack.getCount() > 1) {
                // 找到显示名称的行（通常是第一行），在其后添加数量信息
                if (!tooltip.isEmpty()) {
                    Component displayName = tooltip.get(0);
                    String countText = NumberFormatUtil.formatItemCount(stack.getCount());
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

    private static class FluidStackRenderer implements IIngredientRenderer<FluidStack> {
        private final int capacity;
        private final boolean showAmount;
        private final int width;
        private final int height;

        public FluidStackRenderer(int capacity, boolean showAmount, int width, int height) {
            this.capacity = capacity;
            this.showAmount = showAmount;
            this.width = width;
            this.height = height;
        }

        @Override
        public void render(GuiGraphics guiGraphics, FluidStack stack) {
            if (stack == null || stack.isEmpty()) return;

            // 启用深度测试
            RenderSystem.enableDepthTest();

            // 渲染流体填充
            int fluidHeight = (int) (height * ((float) stack.getAmount() / capacity));
            if (fluidHeight > 0) {
                net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions fluidAttributes =
                        net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid());
                ResourceLocation fluidStillTexture = fluidAttributes.getStillTexture(stack);
                int fluidColor = fluidAttributes.getTintColor(stack);

                if (fluidStillTexture != null) {
                    TextureAtlasSprite fluidSprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(fluidStillTexture);

                    float r = ((fluidColor >> 16) & 0xFF) / 255.0F;
                    float g = ((fluidColor >> 8) & 0xFF) / 255.0F;
                    float b = (fluidColor & 0xFF) / 255.0F;
                    float a = ((fluidColor >> 24) & 0xFF) / 255.0F;

                    RenderSystem.setShaderColor(r, g, b, a);
                    RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);

                    int fluidY = height - fluidHeight;

                    // 绘制流体纹理
                    for (int i = 0; i < Math.ceil((double) width / 16); i++) {
                        for (int j = 0; j < Math.ceil((double) fluidHeight / 16); j++) {
                            int texWidth = Math.min(16, width - i * 16);
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
            if (showAmount && stack.getAmount() > 0) {
                renderFluidAmount(guiGraphics, stack);
            }

            RenderSystem.disableBlend();
        }

        /**
         * 渲染流体数量文本
         */
        private void renderFluidAmount(GuiGraphics guiGraphics, FluidStack stack) {
            String text = NumberFormatUtil.formatFluidAmount(stack.getAmount());
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            PoseStack pose = guiGraphics.pose();

            pose.pushPose();
            float scale = 0.65f;
            pose.translate(0, 0, 200.0F);
            pose.scale(scale, scale, 1.0F);

            int x = Math.round(width / scale) - font.width(text);
            int y = Math.round(height / scale) - font.lineHeight;

            guiGraphics.drawString(font, text, x, y, 0xFFFFFF, true);
            pose.popPose();
        }

        @Override
        public List<Component> getTooltip(FluidStack stack, TooltipFlag flag) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(stack.getDisplayName());
            // 使用新的格式化方法显示流体数量
            tooltip.add(Component.literal("数量: " + NumberFormatUtil.formatFluidAmount(stack.getAmount())));
            return tooltip;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }
    }
}