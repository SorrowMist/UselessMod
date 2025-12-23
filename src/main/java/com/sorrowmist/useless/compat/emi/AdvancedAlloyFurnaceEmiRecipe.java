package com.sorrowmist.useless.compat.emi;

import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.CatalystManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.utils.NumberFormatUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.widget.AnimatedTextureWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceEmiRecipe implements EmiRecipe {
    private final AdvancedAlloyFurnaceRecipe recipe;
    private final List<EmiIngredient> inputs = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    // 槽位位置常量
    private static final int INPUT_SLOTS_START_X = 1;
    private static final int INPUT_SLOTS_START_Y = 1;
    private static final int INPUT_SLOT_SPACING_X = 18;
    private static final int INPUT_SLOT_SPACING_Y = 18;
    private static final int OUTPUT_SLOTS_START_X = 138;
    private static final int OUTPUT_SLOTS_START_Y = 1;
    private static final int FLUID_INPUT_X = 37;
    private static final int FLUID_INPUT_Y = 1;
    private static final int FLUID_OUTPUT_X = 174;
    private static final int FLUID_OUTPUT_Y = 1;
    private static final int CATALYST_SLOT_X = 37;
    private static final int CATALYST_SLOT_Y = 19;
    private static final int MOLD_SLOT_X = 37;
    private static final int MOLD_SLOT_Y = 37;
    private static final int PROGRESS_BAR_X = 79;
    private static final int PROGRESS_BAR_Y = 23;
    private static final int PROGRESS_BAR_WIDTH = 33;
    private static final int PROGRESS_BAR_HEIGHT = 8;
    private static final int ENERGY_DISPLAY_X = 68;
    private static final int ENERGY_DISPLAY_Y = 0;
    private static final int ENERGY_DISPLAY_WIDTH = 55;
    private static final int ENERGY_DISPLAY_HEIGHT = 5;
    private static final int TIME_DISPLAY_X = 79;
    private static final int TIME_DISPLAY_Y = 45;
    private static final int TIME_DISPLAY_WIDTH = 33;
    private static final int TIME_DISPLAY_HEIGHT = 6;
    private static final int CATALYST_TEXT_Y = 13;
    private static final int MOLD_TEXT_Y = 32;
    private static final int PARALLEL_TEXT_X = 57;
    private static final int PARALLEL_TEXT_Y = 5;
    private static final int SLOT_SIZE = 16;

    // 纹理资源
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/jei_advanced_alloy_furnace_gui.png");
    private static final ResourceLocation PROGRESS_TEXTURE = ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/jei_arrow_advanced_alloy_furnace_gui.png");

    public AdvancedAlloyFurnaceEmiRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        this.recipe = recipe;

        // 处理输入物品
        for (int i = 0; i < Math.min(recipe.getInputItems().size(), 6); i++) {
            Ingredient ingredient = recipe.getInputItems().get(i);

            ItemStack[] matchingStacks = ingredient.getItems();
            if (matchingStacks.length > 0) {
                ItemStack[] displayStacks = new ItemStack[matchingStacks.length];
                for (int j = 0; j < matchingStacks.length; j++) {
                    ItemStack displayStack = matchingStacks[j].copy();
                    // 让EMI读取到的数量为1，然后自己绘制格式化的数量
                    displayStack.setCount(1);
                    displayStacks[j] = displayStack;
                }

                inputs.add(EmiIngredient.of(Ingredient.of(displayStacks)));
            }
        }

        // 处理输出物品
        for (ItemStack stack : recipe.getOutputItems()) {
            ItemStack displayStack = stack.copy();
            // 让EMI读取到的数量为1，然后自己绘制格式化的数量
            displayStack.setCount(1);
            outputs.add(EmiStack.of(displayStack));
        }

        // 处理流体输入（如果有）
        if (!recipe.getInputFluid().isEmpty()) {
            // EMI处理流体的方式可能不同，这里先添加一个占位符
        }

        // 处理流体输出（如果有）
        if (!recipe.getOutputFluid().isEmpty()) {
            // EMI处理流体的方式可能不同，这里先添加一个占位符
        }

        // 处理催化剂
        if (recipe.requiresCatalyst()) {
            inputs.add(EmiIngredient.of(recipe.getCatalyst()));
        }

        // 处理模具
        if (recipe.requiresMold()) {
            inputs.add(EmiIngredient.of(recipe.getMold()));
        }
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return AdvancedAlloyFurnaceRecipeCategory.CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return 191;
    }

    @Override
    public int getDisplayHeight() {
        return 54;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // 绘制背景
        widgets.addTexture(GUI_TEXTURE, 0, 0, 191, 54, 0, 0, 191, 54, 256, 256);

        // 输入物品槽位 (6个) - 3行2列排列
        int inputIndex = 0;
        for (int i = 0; i < Math.min(recipe.getInputItems().size(), 6); i++) {
            int row = i / 2;
            int col = i % 2;
            // 物品渲染需要向上向左各移动1像素
            int x = INPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X - 1;
            int y = INPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y - 1;

            EmiIngredient ingredient = inputs.get(inputIndex++);
            // 添加槽位，不绘制背景（因为背景已经在GUI纹理中了）
            widgets.addSlot(ingredient, x, y).drawBack(false);
            
            // 添加自定义绘制器来显示格式化的物品数量
            final int index = i;
            widgets.addDrawable(x, y, 18, 18, (draw, mouseX, mouseY, delta) -> {
                // 启用深度测试
                RenderSystem.enableDepthTest();
                
                Minecraft mc = Minecraft.getInstance();
                Font font = mc.font;
                
                // 使用配方中的实际数量，而不是从物品堆栈中获取
                long count = recipe.getInputItemCounts().get(index);
                if (count > 1) {
                    String text = NumberFormatUtil.formatItemCount(count);
                    draw.pose().pushPose();
                    float scale = 0.65f;
                    // 自定义绘制器的坐标是相对于自己的绘制区域的，所以从(1,1)开始，而不是(x+1,y+1)
                    draw.pose().translate(1, 1, 200.0F);
                    draw.pose().scale(scale, scale, 1.0F);
                    
                    // 严格参考JEI的显示位置
                    int textX = Math.round(16.0f / scale) - font.width(text);
                    int textY = Math.round(10.0f / scale);
                    
                    draw.drawString(font, text, textX, textY, 0xFFFFFF, true);
                    draw.pose().popPose();
                }
                
                RenderSystem.disableBlend();
            });
        }

        // 输出物品槽位 (6个) - 3行2列排列
        for (int i = 0; i < Math.min(recipe.getOutputItems().size(), 6); i++) {
            int row = i / 2;
            int col = i % 2;
            // 物品渲染需要向上向左各移动1像素
            int x = OUTPUT_SLOTS_START_X + col * INPUT_SLOT_SPACING_X - 1;
            int y = OUTPUT_SLOTS_START_Y + row * INPUT_SLOT_SPACING_Y - 1;

            EmiStack stack = outputs.get(i);
            // 添加槽位，不绘制背景，不使用large属性，保持默认大小
            widgets.addSlot(stack, x, y).drawBack(false);
            
            // 添加自定义绘制器来显示格式化的物品数量
            final int index = i;
            widgets.addDrawable(x, y, 18, 18, (draw, mouseX, mouseY, delta) -> {
                // 启用深度测试
                RenderSystem.enableDepthTest();
                
                Minecraft mc = Minecraft.getInstance();
                Font font = mc.font;
                
                // 使用配方中的实际输出数量
                int count = recipe.getOutputItems().get(index).getCount();
                if (count > 1) {
                    String text = NumberFormatUtil.formatItemCount(count);
                    draw.pose().pushPose();
                    float scale = 0.65f;
                    // 自定义绘制器的坐标是相对于自己的绘制区域的，所以从(1,1)开始，而不是(x+1,y+1)
                    draw.pose().translate(1, 1, 200.0F);
                    draw.pose().scale(scale, scale, 1.0F);
                    
                    // 严格参考JEI的显示位置
                    int textX = Math.round(16.0f / scale) - font.width(text);
                    int textY = Math.round(10.0f / scale);
                    
                    draw.drawString(font, text, textX, textY, 0xFFFFFF, true);
                    draw.pose().popPose();
                }
                
                RenderSystem.disableBlend();
            });
        }

        // 输入流体槽 - 使用SlotWidget处理流体的渲染和tooltips
        if (!recipe.getInputFluid().isEmpty()) {
            final var fluidStack = recipe.getInputFluid();
            // 流体渲染位置向左上各移动一个像素
            final int fluidX = FLUID_INPUT_X - 1;
            final int fluidY = FLUID_INPUT_Y - 1;
            
            // 创建流体的EmiStack，用于显示tooltips和渲染
            EmiStack emiFluidStack = EmiStack.of(fluidStack.getFluid(), fluidStack.getAmount());
            
            // 添加槽位，用于处理渲染和tooltips
            widgets.addSlot(emiFluidStack, fluidX, fluidY).drawBack(false);
            
            // 只渲染流体数量文本，不重复渲染流体
            widgets.addDrawable(fluidX, fluidY, 16, 16, (draw, mouseX, mouseY, delta) -> {
                RenderSystem.enableDepthTest();
                
                if (!fluidStack.isEmpty()) {
                    // 只渲染流体数量文本，流体本身由SlotWidget渲染
                    Minecraft mc = Minecraft.getInstance();
                    Font font = mc.font;
                    String text = NumberFormatUtil.formatFluidAmount(fluidStack.getAmount());
                    draw.pose().pushPose();
                    float scale = 0.65f;
                    // 自定义绘制器的坐标是相对于自己的绘制区域的，所以从(0,0)开始
                    draw.pose().translate(0, 0, 200.0F);
                    draw.pose().scale(scale, scale, 1.0F);
                    
                    int textX = Math.round(16.0f / scale) - font.width(text);
                    int textY = Math.round(10.0f / scale);
                    
                    draw.drawString(font, text, textX, textY, 0xFFFFFF, true);
                    draw.pose().popPose();
                }
                
                RenderSystem.disableBlend();
            });
        }

        // 输出流体槽 - 使用SlotWidget处理流体的渲染和tooltips
        if (!recipe.getOutputFluid().isEmpty()) {
            final var fluidStack = recipe.getOutputFluid();
            // 流体渲染位置向左上各移动一个像素
            final int fluidX = FLUID_OUTPUT_X - 1;
            final int fluidY = FLUID_OUTPUT_Y - 1;
            
            // 创建流体的EmiStack，用于显示tooltips和渲染
            EmiStack emiFluidStack = EmiStack.of(fluidStack.getFluid(), fluidStack.getAmount());
            
            // 添加槽位，用于处理渲染和tooltips
            widgets.addSlot(emiFluidStack, fluidX, fluidY).drawBack(false);
            
            // 只渲染流体数量文本，不重复渲染流体
            widgets.addDrawable(fluidX, fluidY, 16, 16, (draw, mouseX, mouseY, delta) -> {
                RenderSystem.enableDepthTest();
                
                if (!fluidStack.isEmpty()) {
                    // 只渲染流体数量文本，流体本身由SlotWidget渲染
                    Minecraft mc = Minecraft.getInstance();
                    Font font = mc.font;
                    String text = NumberFormatUtil.formatFluidAmount(fluidStack.getAmount());
                    draw.pose().pushPose();
                    float scale = 0.65f;
                    // 自定义绘制器的坐标是相对于自己的绘制区域的，所以从(0,0)开始
                    draw.pose().translate(0, 0, 200.0F);
                    draw.pose().scale(scale, scale, 1.0F);
                    
                    int textX = Math.round(16.0f / scale) - font.width(text);
                    int textY = Math.round(10.0f / scale);
                    
                    draw.drawString(font, text, textX, textY, 0xFFFFFF, true);
                    draw.pose().popPose();
                }
                
                RenderSystem.disableBlend();
            });
        }

        // 催化剂槽位：只在配方需要催化剂时显示
        if (recipe.requiresCatalyst()) {
            EmiIngredient catalyst = inputs.get(inputIndex++);
            // 物品渲染需要向上向左各移动1像素
            widgets.addSlot(catalyst, CATALYST_SLOT_X - 1, CATALYST_SLOT_Y - 1).drawBack(false);
        }

        // 模具槽位
        if (recipe.requiresMold()) {
            EmiIngredient mold = inputs.get(inputIndex);
            // 物品渲染需要向上向左各移动1像素
            widgets.addSlot(mold, MOLD_SLOT_X - 1, MOLD_SLOT_Y - 1).drawBack(false);
        }

        // 绘制进度条 - 2秒完成一次合成进度
        widgets.addAnimatedTexture(PROGRESS_TEXTURE, PROGRESS_BAR_X, PROGRESS_BAR_Y, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, 0, 0, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, 128, 128, 2000, true, true, false);

        // 移除能量条背景和填充的自定义纹理，因为JEI中没有这些
        
        // 绘制能量和时间信息
        String energyText = NumberFormatUtil.formatEnergy(recipe.getEnergy());
        String timeText = recipe.getProcessTime() + " ticks";

        // 在能量显示区域居中显示能量（绿色）
        Minecraft minecraft = Minecraft.getInstance();
        int energyTextWidth = minecraft.font.width(energyText);
        int energyX = ENERGY_DISPLAY_X + (ENERGY_DISPLAY_WIDTH - energyTextWidth) / 2;
        widgets.addText(Component.literal(energyText), energyX, ENERGY_DISPLAY_Y, 0x00FF00, false);

        // 在时间显示区域居中显示时间（绿色）
        int timeTextWidth = minecraft.font.width(timeText);
        int timeX = TIME_DISPLAY_X + (TIME_DISPLAY_WIDTH - timeTextWidth) / 2;
        widgets.addText(Component.literal(timeText), timeX, TIME_DISPLAY_Y, 0x00FF00, false);

        // 修改：只有当配方允许催化剂时才显示提示
        if (recipe.requiresCatalyst() && recipe.isCatalystAllowed()) {
            String catalystText = "无用锭为可选催化剂";
            // 使用自定义绘制器来实现文字缩放
            widgets.addDrawable(0, 0, 191, 54, (draw, mouseX, mouseY, delta) -> {
                draw.pose().pushPose();
                float scale = 0.7f;
                draw.pose().scale(scale, scale, 1.0f);
                
                int textWidth = minecraft.font.width(catalystText);
                // 在整张 JEI 背景宽度内居中
                int centeredX = (int) ((191 / 2.0f - textWidth * scale / 2) / scale);
                int y = (int) (CATALYST_TEXT_Y / scale);
                
                draw.drawString(minecraft.font, catalystText, centeredX, y, 0xFF0000, false);
                draw.pose().popPose();
            });
        }

        // 模具提示
        if (recipe.requiresMold()) {
            String moldText = "需要标志物（不会被消耗）";
            // 使用自定义绘制器来实现文字缩放
            widgets.addDrawable(0, 0, 191, 54, (draw, mouseX, mouseY, delta) -> {
                draw.pose().pushPose();
                float scale = 0.7f;
                draw.pose().scale(scale, scale, 1.0f);
                
                int textWidth = minecraft.font.width(moldText);
                int centeredX = (int) ((191 / 2.0f - textWidth * scale / 2) / scale);
                int y = (int) (MOLD_TEXT_Y / scale);
                
                draw.drawString(minecraft.font, moldText, centeredX, y, 0xFF0000, false);
                draw.pose().popPose();
            });
        }
    }
}