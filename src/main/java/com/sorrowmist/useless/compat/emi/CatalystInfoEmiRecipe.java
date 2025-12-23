package com.sorrowmist.useless.compat.emi;

import com.sorrowmist.useless.registry.CatalystManager;
import com.sorrowmist.useless.registry.ModIngots;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CatalystInfoEmiRecipe implements EmiRecipe {
    private final List<EmiIngredient> inputs = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    // 显示尺寸
    private static final int DISPLAY_WIDTH = 150;
    private static final int DISPLAY_HEIGHT = 130;

    // 从 CatalystManager 获取催化剂数据
    private final ItemStack[] catalystStacks;

    // 纹理资源
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/jei_catalyst_info_gui.png");

    public CatalystInfoEmiRecipe() {
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

        // 添加催化剂到输入列表
        for (ItemStack stack : catalystStacks) {
            inputs.add(EmiStack.of(stack));
        }
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return CatalystInfoCategory.CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath("useless_mod", "catalyst_info");
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
        return DISPLAY_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return DISPLAY_HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // 绘制背景，修复缩放问题
        widgets.addTexture(GUI_TEXTURE, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, DISPLAY_WIDTH, DISPLAY_HEIGHT);

        // 绘制标题 - 居中显示
        String titleText = "催化剂并行数信息";
        Minecraft minecraft = Minecraft.getInstance();
        int titleWidth = minecraft.font.width(titleText);
        int titleX = (DISPLAY_WIDTH - titleWidth) / 2;
        widgets.addText(Component.literal(titleText), titleX, 10, 0x404040, false);

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
            widgets.addText(Component.literal(line), lineX, explanationY, 0x404040, false);
            explanationY += 10;
        }

        // 2行5列网格布局
        int startX = 29; // 第一个格子左上角起点X坐标
        int startY = 24; // 第一个格子左上角起点Y坐标
        int spacing = 18; // 每个格子18*18像素

        // 总共10个催化剂，按2行5列排列
        for (int i = 0; i < catalystStacks.length; i++) {
            // 计算行和列
            int row = i / 5; // 0, 0, 0, 0, 0, 1, 1, 1, 1, 1
            int col = i % 5; // 0, 1, 2, 3, 4, 0, 1, 2, 3, 4

            int x = startX + col * spacing;
            int y = startY + row * spacing;

            ItemStack stack = catalystStacks[i];
            EmiStack emiStack = EmiStack.of(stack);
            SlotWidget slot = widgets.addSlot(emiStack, x, y).drawBack(false);

            // 添加自定义工具提示
            int parallel = CatalystManager.getCatalystParallel(stack);
            String catalystName = CatalystManager.getCatalystName(stack);

            slot.appendTooltip(Component.literal(catalystName).withStyle(ChatFormatting.GOLD));
            
            // 检查是否为USEFUL_INGOT
            if (stack.getItem() == ModIngots.USEFUL_INGOT.get()) {
                slot.appendTooltip(Component.literal("并行数: 无上限").withStyle(ChatFormatting.GREEN));
                slot.appendTooltip(Component.literal(""));
                slot.appendTooltip(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                slot.appendTooltip(Component.literal("• 催化剂不会被消耗").withStyle(ChatFormatting.GREEN));
                slot.appendTooltip(Component.literal("• 无并行数上限").withStyle(ChatFormatting.GREEN));
                slot.appendTooltip(Component.literal("• 能量消耗不会倍增").withStyle(ChatFormatting.GREEN));
                slot.appendTooltip(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                slot.appendTooltip(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
            } else {
                slot.appendTooltip(Component.literal("并行数: " + parallel + "倍").withStyle(ChatFormatting.GREEN));
                slot.appendTooltip(Component.literal(""));
                slot.appendTooltip(Component.literal("效果说明:").withStyle(ChatFormatting.YELLOW));
                slot.appendTooltip(Component.literal("• 输入物品消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                slot.appendTooltip(Component.literal("• 输出物品数量 × " + parallel).withStyle(ChatFormatting.GRAY));
                slot.appendTooltip(Component.literal("• 能量消耗 × " + parallel).withStyle(ChatFormatting.GRAY));
                slot.appendTooltip(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
                // 添加黑名单说明
                slot.appendTooltip(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));
                slot.appendTooltip(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
            }

            // 在物品上显示并行数
            final String parallelText;
            if (stack.getItem() == ModIngots.USEFUL_INGOT.get()) {
                parallelText = "∞";
            } else {
                parallelText = parallel + "x";
            }
            // 使用自定义绘制器绘制并行数文本，与JEI一致
            widgets.addDrawable(x, y, 18, 18, (draw, mouseX, mouseY, delta) -> {
                draw.pose().pushPose();
                // 自定义绘制器的坐标是相对于自己的绘制区域的，所以从(0,0)开始，而不是(x, y)
                draw.pose().translate(0, 0, 200.0F);
                draw.pose().scale(0.5f, 0.5f, 1.0F);
                
                int textWidth = minecraft.font.width(parallelText);
                int scaledX = (int)((16 - textWidth * 0.5f) / 0.5f);
                int scaledY = (int)(12 / 0.5f);
                
                draw.drawString(minecraft.font, parallelText, scaledX, scaledY, 0x00FF00, true);
                draw.pose().popPose();
            });
        }
    }


}