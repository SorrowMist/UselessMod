package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.menu.slot.HighStackSlot;
import com.sorrowmist.useless.networking.ClearFluidPacket;
import com.sorrowmist.useless.networking.FluidInteractionPacket;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.CatalystManager;
import com.sorrowmist.useless.utils.NumberFormatUtil;
import com.sorrowmist.useless.utils.SlotRendererUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceScreen extends AbstractContainerScreen<AdvancedAlloyFurnaceMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/advanced_alloy_furnace_gui.png");

    private static final ResourceLocation COMPONENTS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/advanced_alloy_furnace_zu_jian.png");

    // 新增：提示图片纹理
    private static final ResourceLocation TIPS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/advanced_alloy_furnace_tips.png");

    // GUI显示尺寸（新贴图尺寸）
    private static final int DISPLAY_WIDTH = 177;
    private static final int DISPLAY_HEIGHT = 274;

    // 贴图实际尺寸（与显示尺寸相同）
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 512;

    // 进度条位置和尺寸 - 根据新贴图调整
    private static final int PROGRESS_PART1_X = 25;
    private static final int PROGRESS_PART1_Y = 31;
    private static final int PROGRESS_PART1_WIDTH = 17;
    private static final int PROGRESS_PART1_HEIGHT = 111;

    private static final int PROGRESS_PART2_X = 80;
    private static final int PROGRESS_PART2_Y = 116;
    private static final int PROGRESS_PART2_WIDTH = 15;
    private static final int PROGRESS_PART2_HEIGHT = 2;

    private static final int PROGRESS_PART3_X = 132;
    private static final int PROGRESS_PART3_Y = 31;
    private static final int PROGRESS_PART3_WIDTH = 22;
    private static final int PROGRESS_PART3_HEIGHT = 111;

    // 能量条位置和尺寸 - 根据新贴图调整
    private static final int ENERGY_BAR_X = 58;
    private static final int ENERGY_BAR_Y = 4;
    private static final int ENERGY_BAR_WIDTH = 66;
    private static final int ENERGY_BAR_HEIGHT = 6;

    // 能量条遮罩位置
    private static final int ENERGY_MASK_X = 57;
    private static final int ENERGY_MASK_Y = 4;
    private static final int ENERGY_MASK_WIDTH = 68;
    private static final int ENERGY_MASK_HEIGHT = 7;

    // 流体槽位置和尺寸 - 根据新贴图调整
    private static final int FLUID_INPUT_X = 10;
    private static final int FLUID_OUTPUT_X = 154;
    private static final int FLUID_Y = 143;
    private static final int FLUID_WIDTH = 15;
    private static final int FLUID_HEIGHT = 31;

    // 新增：催化剂和模具槽位位置
    private static final int CATALYST_SLOT_X = 60;
    private static final int CATALYST_SLOT_Y = 150;
    private static final int MOLD_SLOT_X = 100;
    private static final int MOLD_SLOT_Y = 150;

    // 流体遮罩位置
    private static final int FLUID_MASK_INPUT_X = 8;
    private static final int FLUID_MASK_INPUT_Y = 140;
    private static final int FLUID_MASK_OUTPUT_X = 152;
    private static final int FLUID_MASK_OUTPUT_Y = 140;
    private static final int FLUID_MASK_WIDTH = 19;
    private static final int FLUID_MASK_HEIGHT = 36;

    // 清空按钮位置 - 重新定位
    private static final int CLEAR_FLUID_BUTTON_X = 29;
    private static final int CLEAR_FLUID_BUTTON_Y = 150;
    private static final int CLEAR_FLUID_BUTTON_WIDTH = 17;
    private static final int CLEAR_FLUID_BUTTON_HEIGHT = 17;

    // 新增：催化剂和模具信息区域位置
    private static final int CATALYST_MOLD_INFO_X = 138;
    private static final int CATALYST_MOLD_INFO_Y = 154;
    private static final int CATALYST_MOLD_INFO_WIDTH = 7;
    private static final int CATALYST_MOLD_INFO_HEIGHT = 10;

    // 标题位置 - 根据新要求添加
    private static final int TITLE_LABEL_X = 66;
    private static final int TITLE_LABEL_Y = 52;
    private static final int INVENTORY_LABEL_X = 17;
    private static final int INVENTORY_LABEL_Y = 179;

    // 组件纹理坐标 - 根据新素材图调整
    private static final int ENERGY_MASK_U = 162;
    private static final int ENERGY_MASK_V = 0;
    private static final int ENERGY_BAR_U = 163;
    private static final int ENERGY_BAR_V = 12;

    private static final int PROGRESS_PART1_U = 0;
    private static final int PROGRESS_PART1_V = 0;
    private static final int PROGRESS_PART2_MASK_U = 130;
    private static final int PROGRESS_PART2_MASK_V = 39;
    private static final int PROGRESS_PART2_BAR_U = 132;
    private static final int PROGRESS_PART2_BAR_V = 46;
    private static final int PROGRESS_PART3_U = 18;
    private static final int PROGRESS_PART3_V = 0;

    private static final int FLUID_MASK_INPUT_U = 44;
    private static final int FLUID_MASK_INPUT_V = 39;
    private static final int FLUID_MASK_OUTPUT_U = 64;
    private static final int FLUID_MASK_OUTPUT_V = 39;

    // 新增：催化剂和模具槽位纹理
    private static final int CATALYST_SLOT_U = 63;
    private static final int CATALYST_SLOT_V = 0;
    private static final int MOLD_SLOT_U = 120;
    private static final int MOLD_SLOT_V = 0;

    // 新增：指示灯纹理坐标
    private static final int BLUE_INDICATOR_U = 44;
    private static final int BLUE_INDICATOR_V = 26;
    private static final int YELLOW_INDICATOR_U = 49;
    private static final int YELLOW_INDICATOR_V = 26;

    private static final int CLEAR_BUTTON_DEFAULT_U = 88;
    private static final int CLEAR_BUTTON_DEFAULT_V = 39;
    private static final int CLEAR_BUTTON_PRESSED_U = 88;
    private static final int CLEAR_BUTTON_PRESSED_V = 57;

    // 清空按钮状态
    private boolean clearButtonPressed = false;

    public AdvancedAlloyFurnaceScreen(AdvancedAlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = DISPLAY_WIDTH;
        this.imageHeight = DISPLAY_HEIGHT;

        // 设置标题位置
        this.titleLabelX = TITLE_LABEL_X;
        this.titleLabelY = TITLE_LABEL_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // 渲染GUI主贴图
        guiGraphics.blit(TEXTURE, x, y, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // 渲染能量条
        renderEnergyBar(guiGraphics, x, y);

        // 渲染进度条
        renderProgressBar(guiGraphics, x, y);

        // 渲染流体槽
        renderFluidTanks(guiGraphics, x, y);

        // 渲染催化剂和模具槽位
        renderCatalystAndMoldSlots(guiGraphics, x, y);

        // 渲染指示灯
        renderIndicators(guiGraphics, x, y);

        // 渲染清空按钮
        renderClearButton(guiGraphics, x, y);

        // 渲染提示图片
        renderTipsImage(guiGraphics, x, y);
    }

    // 新增：渲染提示图片
    private void renderTipsImage(GuiGraphics guiGraphics, int x, int y) {
        // 在指定位置渲染提示图片
        guiGraphics.blit(TIPS_TEXTURE,
                x + CATALYST_MOLD_INFO_X, y + CATALYST_MOLD_INFO_Y,
                0, 0,
                CATALYST_MOLD_INFO_WIDTH, CATALYST_MOLD_INFO_HEIGHT,
                CATALYST_MOLD_INFO_WIDTH, CATALYST_MOLD_INFO_HEIGHT);
    }

    // 渲染能量条
    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int energyStored = menu.getEnergy();
        int maxEnergy = menu.getMaxEnergy();

        if (maxEnergy > 0) {
            // 渲染能量填充条
            float energyRatio = (float) energyStored / maxEnergy;
            int energyWidth = (int) (ENERGY_BAR_WIDTH * energyRatio);

            if (energyWidth > 0) {
                // 从组件贴图渲染能量填充条
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                        ENERGY_BAR_U, ENERGY_BAR_V,
                        energyWidth, ENERGY_BAR_HEIGHT);
            }

            // 渲染能量条遮罩
            guiGraphics.blit(COMPONENTS_TEXTURE,
                    x + ENERGY_MASK_X, y + ENERGY_MASK_Y,
                    ENERGY_MASK_U, ENERGY_MASK_V,
                    ENERGY_MASK_WIDTH, ENERGY_MASK_HEIGHT);
        }
    }

    // 渲染进度条
    private void renderProgressBar(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();

        if (maxProgress > 0 && progress > 0) {
            float progressRatio = 1 - (float) progress / maxProgress;

            // 计算总进度宽度（三部分总宽度）
            int totalProgressWidth = PROGRESS_PART1_WIDTH + PROGRESS_PART2_WIDTH + PROGRESS_PART3_WIDTH;
            int currentProgressWidth = (int) Math.ceil(totalProgressWidth * progressRatio);

            // 渲染第一部分
            if (currentProgressWidth > 0) {
                int part1Width = Math.min(currentProgressWidth, PROGRESS_PART1_WIDTH);
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        x + PROGRESS_PART1_X, y + PROGRESS_PART1_Y,
                        PROGRESS_PART1_U, PROGRESS_PART1_V,
                        part1Width, PROGRESS_PART1_HEIGHT);
                currentProgressWidth -= part1Width;
            }

            // 渲染第二部分（包括遮罩和进度条本体）
            if (currentProgressWidth > 0) {
                // 先渲染进度条本体
                int part2Width = Math.min(currentProgressWidth, PROGRESS_PART2_WIDTH);
                if (part2Width > 0) {
                    guiGraphics.blit(COMPONENTS_TEXTURE,
                            x + PROGRESS_PART2_X, y + PROGRESS_PART2_Y,
                            PROGRESS_PART2_BAR_U, PROGRESS_PART2_BAR_V,
                            part2Width, PROGRESS_PART2_HEIGHT);
                }

                // 始终渲染第二部分遮罩
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        x + PROGRESS_PART2_X - 2, y + PROGRESS_PART2_Y - 2,
                        PROGRESS_PART2_MASK_U, PROGRESS_PART2_MASK_V,
                        19, 6);

                currentProgressWidth -= PROGRESS_PART2_WIDTH;
            }

            // 渲染第三部分
            if (currentProgressWidth > 0) {
                int part3Width = Math.min(currentProgressWidth, PROGRESS_PART3_WIDTH);
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        x + PROGRESS_PART3_X, y + PROGRESS_PART3_Y,
                        PROGRESS_PART3_U, PROGRESS_PART3_V,
                        part3Width, PROGRESS_PART3_HEIGHT);
            }
        } else {
            // 即使没有进度，也渲染第二部分遮罩
            guiGraphics.blit(COMPONENTS_TEXTURE,
                    x + PROGRESS_PART2_X - 2, y + PROGRESS_PART2_Y - 2,
                    PROGRESS_PART2_MASK_U, PROGRESS_PART2_MASK_V,
                    19, 6);
        }
    }

    // 渲染流体槽
    private void renderFluidTanks(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        FluidStack inputFluid = menu.getBlockEntity().getInputFluidTank().getFluid();
        FluidStack outputFluid = menu.getBlockEntity().getOutputFluidTank().getFluid();

        // 渲染输入流体
        renderFluidTank(guiGraphics, x + FLUID_INPUT_X, y + FLUID_Y, inputFluid,
                menu.getBlockEntity().getInputFluidTank().getCapacity());

        // 渲染输出流体
        renderFluidTank(guiGraphics, x + FLUID_OUTPUT_X, y + FLUID_Y, outputFluid,
                menu.getBlockEntity().getOutputFluidTank().getCapacity());

        // 渲染流体遮罩
        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + FLUID_MASK_INPUT_X, y + FLUID_MASK_INPUT_Y,
                FLUID_MASK_INPUT_U, FLUID_MASK_INPUT_V,
                FLUID_MASK_WIDTH, FLUID_MASK_HEIGHT);

        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + FLUID_MASK_OUTPUT_X, y + FLUID_MASK_OUTPUT_Y,
                FLUID_MASK_OUTPUT_U, FLUID_MASK_OUTPUT_V,
                FLUID_MASK_WIDTH, FLUID_MASK_HEIGHT);
    }

    // 新增：渲染催化剂和模具槽位
    private void renderCatalystAndMoldSlots(GuiGraphics guiGraphics, int x, int y) {
        // 渲染催化剂槽位
        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + CATALYST_SLOT_X, y + CATALYST_SLOT_Y,
                CATALYST_SLOT_U, CATALYST_SLOT_V,
                18, 18);

        // 渲染模具槽位
        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + MOLD_SLOT_X, y + MOLD_SLOT_Y,
                MOLD_SLOT_U, MOLD_SLOT_V,
                18, 18);
    }

    // 修改：渲染指示灯，现在显示并行数状态
    private void renderIndicators(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int currentParallel = menu.getCurrentParallel();
        int maxParallel = menu.getMaxParallel();

        // 渲染催化剂指示灯 - 根据并行数状态显示不同颜色
        int catalystIndicatorX = x + menu.getCatalystSlotX() + (18 - menu.getIndicatorSize()) / 2;
        int catalystIndicatorY = y + menu.getCatalystSlotY() + menu.getIndicatorYOffset();

        int catalystIndicatorU = (currentParallel > 1) ? YELLOW_INDICATOR_U : BLUE_INDICATOR_U;
        int catalystIndicatorV = (currentParallel > 1) ? YELLOW_INDICATOR_V : BLUE_INDICATOR_V;

        guiGraphics.blit(COMPONENTS_TEXTURE,
                catalystIndicatorX, catalystIndicatorY,
                catalystIndicatorU, catalystIndicatorV,
                menu.getIndicatorSize(), menu.getIndicatorSize());

        // 模具指示灯保持不变（如果需要的话）
        int moldIndicatorX = x + menu.getMoldSlotX() + (18 - menu.getIndicatorSize()) / 2;
        int moldIndicatorY = y + menu.getMoldSlotY() + menu.getIndicatorYOffset();

        // 模具不需要并行数指示，保持蓝色
        guiGraphics.blit(COMPONENTS_TEXTURE,
                moldIndicatorX, moldIndicatorY,
                BLUE_INDICATOR_U, BLUE_INDICATOR_V,
                menu.getIndicatorSize(), menu.getIndicatorSize());
    }

    // 修改流体渲染方法，使用完整单位
    private void renderFluidTank(GuiGraphics guiGraphics, int x, int y, FluidStack fluid, int capacity) {
        if (!fluid.isEmpty() && capacity > 0) {
            int fluidHeight = (int) (FLUID_HEIGHT * ((float) fluid.getAmount() / capacity));
            if (fluidHeight > 0) {
                IClientFluidTypeExtensions fluidAttributes = IClientFluidTypeExtensions.of(fluid.getFluid());
                ResourceLocation fluidStillTexture = fluidAttributes.getStillTexture(fluid);
                int fluidColor = fluidAttributes.getTintColor(fluid);

                if (fluidStillTexture != null) {
                    TextureAtlasSprite fluidSprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(fluidStillTexture);

                    float r = ((fluidColor >> 16) & 0xFF) / 255.0F;
                    float g = ((fluidColor >> 8) & 0xFF) / 255.0F;
                    float b = (fluidColor & 0xFF) / 255.0F;
                    float a = ((fluidColor >> 24) & 0xFF) / 255.0F;

                    RenderSystem.setShaderColor(r, g, b, a);
                    RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

                    int fluidY = y + FLUID_HEIGHT - fluidHeight;

                    // 修复：使用正确的流体渲染方法
                    for (int i = 0; i < Math.ceil((double) FLUID_WIDTH / 16); i++) {
                        for (int j = 0; j < Math.ceil((double) fluidHeight / 16); j++) {
                            int texWidth = Math.min(16, FLUID_WIDTH - i * 16);
                            int texHeight = Math.min(16, fluidHeight - j * 16);

                            if (texWidth > 0 && texHeight > 0) {
                                guiGraphics.blit(
                                        x + i * 16,
                                        fluidY + j * 16,
                                        0,
                                        texWidth,
                                        texHeight,
                                        fluidSprite
                                );
                            }
                        }
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }

            // 渲染流体数量文本 - 使用完整单位
            SlotRendererUtil.renderFluidAmount(guiGraphics, fluid.getAmount(), x, y, FLUID_WIDTH, FLUID_HEIGHT);
        }
    }

    // 渲染清空按钮
    private void renderClearButton(GuiGraphics guiGraphics, int x, int y) {
        int buttonX = x + CLEAR_FLUID_BUTTON_X;
        int buttonY = y + CLEAR_FLUID_BUTTON_Y;

        // 根据按钮状态选择纹理
        int u = clearButtonPressed ? CLEAR_BUTTON_PRESSED_U : CLEAR_BUTTON_DEFAULT_U;
        int v = clearButtonPressed ? CLEAR_BUTTON_PRESSED_V : CLEAR_BUTTON_DEFAULT_V;

        guiGraphics.blit(COMPONENTS_TEXTURE, buttonX, buttonY, u, v,
                CLEAR_FLUID_BUTTON_WIDTH, CLEAR_FLUID_BUTTON_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 机器名字渲染为彩虹渐变
        renderRainbowText(guiGraphics, this.font, this.title, this.titleLabelX, this.titleLabelY);

        // 物品栏文字渲染为金色并缩小为0.9倍
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.8f, 0.8f, 1.0f);
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                (int)(this.inventoryLabelX / 0.8f),
                (int)(this.inventoryLabelY / 0.8f+1),
                0xFFD700, false);
        guiGraphics.pose().popPose();
    }

    private void renderRainbowText(GuiGraphics guiGraphics, Font font, Component text, int x, int y) {
        String string = text.getString();
        int currentX = x;

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            int color = getRainbowColor(i, string.length());
            guiGraphics.drawString(font, String.valueOf(c), currentX, y, color, false);
            currentX += font.width(String.valueOf(c));
        }
    }

    private int getRainbowColor(int index, int total) {
        float hue = (float) index / total;
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 使用父类的渲染逻辑（这会正常渲染所有槽位，包括玩家物品栏）
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 为HighStackSlot添加自定义数量渲染
        renderHighStackSlotCounts(guiGraphics);

        // 渲染自定义工具提示
        renderCustomTooltips(guiGraphics, mouseX, mouseY);
    }

    /**
     * 为HighStackSlot渲染自定义数量文本
     */
    private void renderHighStackSlotCounts(GuiGraphics guiGraphics) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        for (Slot slot : this.menu.slots) {
            if (slot instanceof HighStackSlot && slot.hasItem() && slot.getItem().getCount() > 1) {
                renderHighStackCount(guiGraphics, slot, x, y);
            }
        }
    }

    /**
     * 渲染单个HighStackSlot的数量文本
     */
    private void renderHighStackCount(GuiGraphics guiGraphics, Slot slot, int x, int y) {
        ItemStack stack = slot.getItem();
        String text = NumberFormatUtil.formatItemCount(stack.getCount());
        Font font = minecraft.font;

        guiGraphics.pose().pushPose();
        float scale = 0.65f;
        guiGraphics.pose().translate(0, 0, 300.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);

        int textX = Math.round((x + slot.x + 16 - font.width(text) * scale) / scale);
        int textY = Math.round((y + slot.y + 16 - 8 * scale) / scale);

        guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    /**
     * 渲染自定义工具提示
     */
    // 修改工具提示渲染方法，实时显示并行数
    // 在 renderCustomTooltips 方法中修改催化剂和模具信息区域悬停提示
    private void renderCustomTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (menu == null) return;

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // 能量条悬停提示
        if (isMouseOverArea(mouseX, mouseY, x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("能量: " + menu.getEnergy() + " / " + menu.getMaxEnergy() + " FE"));
            tooltip.add(Component.literal("消耗: " + menu.getProcessTick() + " FE/t"));
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 进度条悬停提示
        if (isMouseOverProgressArea(mouseX, mouseY, x, y)) {
            List<Component> tooltip = new ArrayList<>();
            int progress = menu.getProgress();
            int maxProgress = menu.getMaxProgress();

            if (maxProgress > 0) {
                float progressPercent = (float) progress / maxProgress * 100;
                tooltip.add(Component.literal("进度: " + progress + "/" + maxProgress + " (" + String.format("%.1f", progressPercent) + "%)"));
                tooltip.add(Component.literal("状态: " + (menu.isActive() ? "工作中" : "空闲")));
                // 添加并行数信息
                tooltip.add(Component.literal("本次并行数: " + menu.getCurrentParallel()));
                tooltip.add(Component.literal("当前最大并行数: " + menu.getMaxParallel()));
            } else {
                tooltip.add(Component.literal("没有活动进程"));
            }
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 清空按钮悬停提示
        if (isMouseOverArea(mouseX, mouseY,
                x + CLEAR_FLUID_BUTTON_X, y + CLEAR_FLUID_BUTTON_Y,
                CLEAR_FLUID_BUTTON_WIDTH, CLEAR_FLUID_BUTTON_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("清空输入流体"));
            tooltip.add(Component.literal("点击清空输入流体槽"));
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 修复：催化剂和模具信息区域悬停提示，始终显示并行数信息
        if (isMouseOverArea(mouseX, mouseY,
                x + CATALYST_MOLD_INFO_X, y + CATALYST_MOLD_INFO_Y,
                CATALYST_MOLD_INFO_WIDTH, CATALYST_MOLD_INFO_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();

            int currentParallel = menu.getCurrentParallel();
            int maxParallel = menu.getMaxParallel();

            tooltip.add(Component.literal("本次并行数: " + currentParallel).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("当前最大并行数: " + maxParallel).withStyle(ChatFormatting.BLUE));

            // 检查配方是否允许催化剂
            if (menu.getBlockEntity() != null) {
                ItemStack catalyst = menu.getBlockEntity().getItemInSlot(12); // 催化剂槽位

                // 获取当前配方（需要添加获取方法）
                AdvancedAlloyFurnaceRecipe currentRecipe = menu.getBlockEntity().getCurrentRecipe();
                boolean catalystAllowed = currentRecipe == null || currentRecipe.isCatalystAllowed();

                if (menu.getBlockEntity().hasCatalyst() && catalystAllowed) {
                    String catalystName = CatalystManager.getCatalystName(catalyst);
                    int catalystParallel = CatalystManager.getCatalystParallel(catalyst);
                    tooltip.add(Component.literal("催化剂: " + catalystName + " (" + catalystParallel + "倍)").withStyle(ChatFormatting.GREEN));
                } else if (!catalystAllowed) {
                    tooltip.add(Component.literal("该配方不支持催化剂并行").withStyle(ChatFormatting.RED));
                }
            }

            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("并行数说明:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("• 消耗和产出乘以并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 能量消耗乘以并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 催化剂为可选项，可提高并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 催化剂槽位悬停提示
        if (isMouseOverArea(mouseX, mouseY,
                x + menu.getCatalystSlotX(), y + menu.getCatalystSlotY(), 18, 18)) {
            List<Component> tooltip = new ArrayList<>();

            tooltip.add(Component.literal("催化剂槽位").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("放入无用锭可提高并行数").withStyle(ChatFormatting.GRAY));

            // 使用 CatalystManager 获取格式化的催化剂列表
            tooltip.addAll(CatalystManager.getFormattedCatalystList());

            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("催化剂为可选项，不放入时并行数为1").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("注意: 合成无用锭时催化剂无效").withStyle(ChatFormatting.RED));
            tooltip.add(Component.literal("⚠ 催化剂会被消耗").withStyle(ChatFormatting.RED));

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 流体槽悬停提示
        renderFluidInteractions(guiGraphics, mouseX, mouseY, x, y);
    }

    private boolean isMouseOverProgressArea(int mouseX, int mouseY, int x, int y) {
        return isMouseOverArea(mouseX, mouseY, x + PROGRESS_PART1_X, y + PROGRESS_PART1_Y, PROGRESS_PART1_WIDTH, PROGRESS_PART1_HEIGHT) ||
                isMouseOverArea(mouseX, mouseY, x + PROGRESS_PART2_X, y + PROGRESS_PART2_Y, PROGRESS_PART2_WIDTH, PROGRESS_PART2_HEIGHT) ||
                isMouseOverArea(mouseX, mouseY, x + PROGRESS_PART3_X, y + PROGRESS_PART3_Y, PROGRESS_PART3_WIDTH, PROGRESS_PART3_HEIGHT);
    }

    private void renderFluidInteractions(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        // 输入流体槽交互区域
        if (isMouseOverArea(mouseX, mouseY, x + FLUID_INPUT_X, y + FLUID_Y, FLUID_WIDTH, FLUID_HEIGHT)) {
            FluidStack inputFluid = menu.getBlockEntity().getInputFluidTank().getFluid();
            List<Component> tooltip = new ArrayList<>();

            if (!inputFluid.isEmpty()) {
                tooltip.add(Component.literal("输入流体: " + inputFluid.getDisplayName().getString()));
                tooltip.add(Component.literal("数量: " + NumberFormatUtil.formatFluidAmount(inputFluid.getAmount()) + " / " +
                        NumberFormatUtil.formatFluidAmount(menu.getBlockEntity().getInputFluidTank().getCapacity())));
                tooltip.add(Component.literal("右键: 倒入流体"));
            } else {
                tooltip.add(Component.literal("输入流体槽"));
                tooltip.add(Component.literal("右键倒入流体"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 输出流体槽交互区域
        if (isMouseOverArea(mouseX, mouseY, x + FLUID_OUTPUT_X, y + FLUID_Y, FLUID_WIDTH, FLUID_HEIGHT)) {
            FluidStack outputFluid = menu.getBlockEntity().getOutputFluidTank().getFluid();
            List<Component> tooltip = new ArrayList<>();

            if (!outputFluid.isEmpty()) {
                tooltip.add(Component.literal("输出流体: " + outputFluid.getDisplayName().getString()));
                tooltip.add(Component.literal("数量: " + NumberFormatUtil.formatFluidAmount(outputFluid.getAmount()) + " / " +
                        NumberFormatUtil.formatFluidAmount(menu.getBlockEntity().getOutputFluidTank().getCapacity())));
                tooltip.add(Component.literal("左键: 抽取流体"));
            } else {
                tooltip.add(Component.literal("输出流体槽"));
                tooltip.add(Component.literal("左键抽取流体"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu != null && menu.getBlockEntity() != null) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;

            // 检查是否点击了清空按钮
            if (isMouseOverArea((int)mouseX, (int)mouseY,
                    x + CLEAR_FLUID_BUTTON_X, y + CLEAR_FLUID_BUTTON_Y,
                    CLEAR_FLUID_BUTTON_WIDTH, CLEAR_FLUID_BUTTON_HEIGHT)) {
                clearButtonPressed = true;
                handleClearFluidButton();
                return true;
            }

            // 检查是否点击了输入流体槽
            if (isMouseOverArea((int)mouseX, (int)mouseY, x + FLUID_INPUT_X, y + FLUID_Y, FLUID_WIDTH, FLUID_HEIGHT)) {
                handleFluidInteraction(true, button);
                return true;
            }

            // 检查是否点击了输出流体槽
            if (isMouseOverArea((int)mouseX, (int)mouseY, x + FLUID_OUTPUT_X, y + FLUID_Y, FLUID_WIDTH, FLUID_HEIGHT)) {
                handleFluidInteraction(false, button);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clearButtonPressed) {
            clearButtonPressed = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleClearFluidButton() {
        if (minecraft == null || minecraft.player == null) return;

        UselessMod.NETWORK.sendToServer(new ClearFluidPacket(
                menu.getBlockEntity().getBlockPos(),
                true
        ));
    }

    private void handleFluidInteraction(boolean isInputTank, int button) {
        if (minecraft == null || minecraft.player == null) return;

        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;

        boolean isFill = (button == 1);

        if (!isInputTank && isFill) {
            return;
        }

        UselessMod.NETWORK.sendToServer(new FluidInteractionPacket(
                menu.getBlockEntity().getBlockPos(),
                isInputTank,
                isFill,
                button
        ));
    }

    private boolean isMouseOverArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}