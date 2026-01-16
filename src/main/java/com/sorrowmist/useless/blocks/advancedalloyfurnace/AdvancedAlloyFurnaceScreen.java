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
import net.minecraft.world.item.TooltipFlag;
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
    private static final int DISPLAY_WIDTH = 176;
    private static final int DISPLAY_HEIGHT = 256;

    // 贴图实际尺寸（与显示尺寸相同）
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 512;

    // 进度条位置和尺寸 - 改为两段
    private static final int PROGRESS_LEFT_X = 32;
    private static final int PROGRESS_LEFT_Y = 72;
    private static final int PROGRESS_LEFT_WIDTH = 24;
    private static final int PROGRESS_LEFT_HEIGHT = 25;

    private static final int PROGRESS_RIGHT_X = 120;
    private static final int PROGRESS_RIGHT_Y = 90;
    private static final int PROGRESS_RIGHT_WIDTH = 27;
    private static final int PROGRESS_RIGHT_HEIGHT = 21;

    // 能量条位置和尺寸 - 根据新贴图调整
    private static final int ENERGY_BAR_X = 58;
    private static final int ENERGY_BAR_Y = 3;
    private static final int ENERGY_BAR_WIDTH = 60;
    private static final int ENERGY_BAR_HEIGHT = 6;

    // 能量条遮罩位置
    private static final int ENERGY_MASK_X = 57;
    private static final int ENERGY_MASK_Y = 3;
    private static final int ENERGY_MASK_WIDTH = 62;
    private static final int ENERGY_MASK_HEIGHT = 7;

    // 流体槽位置和尺寸 - 根据新贴图调整
    private static final int FLUID_INPUT_X = 10;
    private static final int FLUID_OUTPUT_X = 154;
    private static final int FLUID_Y = 142;
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 32;

    // 流体输入区域
    private static final int FLUID_INPUT_AREA_X = 74;
    private static final int FLUID_INPUT_AREA_Y = 20;
    private static final int FLUID_INPUT_AREA_WIDTH = 86;
    private static final int FLUID_INPUT_AREA_HEIGHT = 50;
    
    // 流体输出区域
    private static final int FLUID_OUTPUT_AREA_X = 8;
    private static final int FLUID_OUTPUT_AREA_Y = 115;
    private static final int FLUID_OUTPUT_AREA_WIDTH = 86;
    private static final int FLUID_OUTPUT_AREA_HEIGHT = 50;
    
    // 流体槽通用属性
    private static final int FLUID_TANK_WIDTH = 55;
    private static final int FLUID_TANK_HEIGHT = 17;
    private static final int FLUID_TANK_SPACING = 2;

    // 滑块相关
    private static final int SLIDER_SLOT_X = 161;
    private static final int SLIDER_SLOT_Y = 24;
    private static final int SLIDER_SLOT_WIDTH = 3;
    private static final int SLIDER_SLOT_HEIGHT = 42; // 修改为42
    private static final int SLIDER_WIDTH = 7;
    private static final int SLIDER_HEIGHT = 15;
    
    // 输出区域滑块槽
    private static final int OUTPUT_SLIDER_SLOT_X = 95;
    private static final int OUTPUT_SLIDER_SLOT_Y = 119;
    private static final int OUTPUT_SLIDER_SLOT_WIDTH = 3;
    private static final int OUTPUT_SLIDER_SLOT_HEIGHT = 42;

    // 催化剂和模具槽位位置
    private static final int CATALYST_SLOT_X = 61;
    private static final int CATALYST_SLOT_Y = 87;
    private static final int MOLD_SLOT_X = 99;
    private static final int MOLD_SLOT_Y = 87;

    // 指示灯位置
    private static final int CATALYST_INDICATOR_X = 67;
    private static final int CATALYST_INDICATOR_Y = 80;
    private static final int MOLD_INDICATOR_X = 105;
    private static final int MOLD_INDICATOR_Y = 80;
    private static final int INDICATOR_WIDTH = 4;
    private static final int INDICATOR_HEIGHT = 5;

    // 提示?区域 - 主图内置
    private static final int TIPS_AREA_X = 80;
    private static final int TIPS_AREA_Y = 87;
    private static final int TIPS_AREA_WIDTH = 16;
    private static final int TIPS_AREA_HEIGHT = 16;

    // 标题位置 - 根据新要求添加
    private static final int TITLE_LABEL_X = 66;
    private static final int TITLE_LABEL_Y = 52;
    private static final int INVENTORY_LABEL_X = 10;
    private static final int INVENTORY_LABEL_Y = 181;

    // 组件纹理坐标 - 根据新素材图调整
    private static final int ENERGY_BAR_U = 0;
    private static final int ENERGY_BAR_V = 94; // 修改为正确的能量条本体位置
    private static final int ENERGY_MASK_U = 0;
    private static final int ENERGY_MASK_V = 101;

    private static final int PROGRESS_LEFT_MASK_U = 0;
    private static final int PROGRESS_LEFT_MASK_V = 109;
    private static final int PROGRESS_RIGHT_MASK_U = 0;
    private static final int PROGRESS_RIGHT_MASK_V = 135;

    private static final int FLUID_TANK_U = 0;
    private static final int FLUID_TANK_V = 0;
    private static final int FLUID_TANK_MASK_U = 0;
    private static final int FLUID_TANK_MASK_V = 18;

    // 指示灯纹理坐标
    private static final int LIT_INDICATOR_U = 0;
    private static final int LIT_INDICATOR_V = 88;

    // 催化剂和模具槽位纹理坐标
    private static final int CATALYST_SLOT_U = 0;
    private static final int CATALYST_SLOT_V = 0;
    private static final int MOLD_SLOT_U = 0;
    private static final int MOLD_SLOT_V = 0;

    // 滑块纹理坐标
    private static final int SLIDER_DEFAULT_U = 0;
    private static final int SLIDER_DEFAULT_V = 72;
    private static final int SLIDER_PRESSED_U = 18;
    private static final int SLIDER_PRESSED_V = 72;

    // 流体遮罩纹理坐标
    private static final int FLUID_MASK_OUTPUT_X = 152;
    private static final int FLUID_MASK_OUTPUT_Y = 140;
    private static final int FLUID_MASK_OUTPUT_U = 0;
    private static final int FLUID_MASK_OUTPUT_V = 0;
    private static final int FLUID_MASK_WIDTH = 19;
    private static final int FLUID_MASK_HEIGHT = 36;

    // 提示区域变量
    private static final int CATALYST_MOLD_INFO_X = 80;
    private static final int CATALYST_MOLD_INFO_Y = 87;
    private static final int CATALYST_MOLD_INFO_WIDTH = 16;
    private static final int CATALYST_MOLD_INFO_HEIGHT = 16;

    // 清空按钮位置 - 重新定位
    private static final int CLEAR_FLUID_BUTTON_X = 29;
    private static final int CLEAR_FLUID_BUTTON_Y = 150;
    private static final int CLEAR_FLUID_BUTTON_WIDTH = 17;
    private static final int CLEAR_FLUID_BUTTON_HEIGHT = 17;

    // 清空按钮纹理（单槽清空按钮）
    private static final int TANK_CLEAR_BUTTON_U = 0;
    private static final int TANK_CLEAR_BUTTON_V = 36;
    private static final int TANK_CLEAR_BUTTON_PRESSED_U = 18;
    private static final int TANK_CLEAR_BUTTON_PRESSED_V = 36;
    private static final int TANK_CLEAR_BUTTON_WIDTH = 17;
    private static final int TANK_CLEAR_BUTTON_HEIGHT = 17;

    // 输入区域清空按钮状态 - 存储每个按钮的按下状态
    private final boolean[] inputTankClearButtonsPressed = new boolean[6];
    
    // 输出区域清空按钮状态 - 存储每个按钮的按下状态
    private final boolean[] outputTankClearButtonsPressed = new boolean[6];

    // 流体输入区域滚动功能
    private int inputFluidScrollOffset = 0;
    private boolean isDraggingInputSlider = false;
    private int draggedInputSliderY = 0;
    
    // 流体输出区域滚动功能
    private int outputFluidScrollOffset = 0;
    private boolean isDraggingOutputSlider = false;
    private int draggedOutputSliderY = 0;

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

        // 渲染流体输入区域
        renderFluidInputArea(guiGraphics, x, y);

        // 渲染输出流体槽
        renderOutputFluidTanks(guiGraphics, x, y);

        // 渲染催化剂和模具槽位
        renderCatalystAndMoldSlots(guiGraphics, x, y);

        // 渲染指示灯
        renderIndicators(guiGraphics, x, y);

        // 移除旧UI的清空按钮，只使用新UI的每槽清空按钮

        // 渲染滑块
        renderSlider(guiGraphics, x, y);
    }

    // 渲染提示图片（已内置在主图中，不再需要额外渲染）
    private void renderTipsImage(GuiGraphics guiGraphics, int x, int y) {
        // 提示区域已内置在主图中，无需额外渲染
    }

    // 渲染能量条
    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int energyStored = menu.getEnergy();
        int maxEnergy = menu.getMaxEnergy();

        if (maxEnergy > 0) {
            // 渲染能量条本体，从组件图抠取，起点0,24，大小60*6，覆盖主图58,3,60*6区域
            float energyRatio = (float) energyStored / maxEnergy;
            int energyWidth = (int) (ENERGY_BAR_WIDTH * energyRatio);

            if (energyWidth > 0) {
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                        ENERGY_BAR_U, ENERGY_BAR_V,
                        energyWidth, ENERGY_BAR_HEIGHT);
            }

            // 渲染能量条遮罩，从组件图抠取，起点0,101，大小62*7，覆盖主图57,3,62*7位置
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

            // 渲染左进度条
            int leftProgressHeight = (int) Math.ceil(PROGRESS_LEFT_HEIGHT * progressRatio);
            guiGraphics.blit(COMPONENTS_TEXTURE,
                    x + PROGRESS_LEFT_X, y + PROGRESS_LEFT_Y,
                    PROGRESS_LEFT_MASK_U, PROGRESS_LEFT_MASK_V,
                    PROGRESS_LEFT_WIDTH, leftProgressHeight);

            // 渲染右进度条
            int rightProgressHeight = (int) Math.ceil(PROGRESS_RIGHT_HEIGHT * progressRatio);
            guiGraphics.blit(COMPONENTS_TEXTURE,
                    x + PROGRESS_RIGHT_X, y + PROGRESS_RIGHT_Y,
                    PROGRESS_RIGHT_MASK_U, PROGRESS_RIGHT_MASK_V,
                    PROGRESS_RIGHT_WIDTH, rightProgressHeight);
        }
    }

    // 渲染流体槽（已被renderFluidInputArea和renderOutputFluidTanks替代）
    private void renderFluidTanks(GuiGraphics guiGraphics, int x, int y) {
        // 流体渲染逻辑已迁移到renderFluidInputArea和renderOutputFluidTanks方法
        // 保留此方法以保持兼容性
    }

    // 渲染流体输入区域，支持滚动
    private void renderFluidInputArea(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        // 计算可见的流体槽数量
        int visibleTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalTanks = 6; // 固定6个输入流体槽
        
        // 计算流体槽和按钮的总宽度（流体槽宽度 + 1像素间隔 + 按钮宽度）
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;
        
        // 限制滚动偏移
        int maxOffset = Math.max(0, totalTanks - visibleTanks);
        inputFluidScrollOffset = Math.max(0, Math.min(inputFluidScrollOffset, maxOffset));

        // 渲染可见的流体槽
        if (visibleTanks > 0) {
            // 计算每个槽位的垂直间距，使上下边距和槽位间隔相等
            int totalTankHeight = visibleTanks * FLUID_TANK_HEIGHT;
            int totalSpacing = FLUID_INPUT_AREA_HEIGHT - totalTankHeight;
            int spacing = visibleTanks > 1 ? totalSpacing / (visibleTanks + 1) : totalSpacing / 2;
            
            for (int i = inputFluidScrollOffset; i < Math.min(totalTanks, inputFluidScrollOffset + visibleTanks); i++) {
                int tankIndex = i;
                // 计算流体槽Y坐标，使上下边距和槽位间隔相等
                int tankY = y + FLUID_INPUT_AREA_Y + spacing + (i - inputFluidScrollOffset) * (FLUID_TANK_HEIGHT + spacing);
                // 整体横向居中：(区域宽度 - 总宽度) / 2
                int startX = x + FLUID_INPUT_AREA_X + (FLUID_INPUT_AREA_WIDTH - totalWidth) / 2;
                int tankX = startX;
                int buttonX = startX + FLUID_TANK_WIDTH + 1; // 流体槽右边空1像素

                // 渲染流体槽背景
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        tankX, tankY,
                        FLUID_TANK_U, FLUID_TANK_V,
                        FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);

                // 渲染流体
                FluidStack fluid = menu.getBlockEntity().getInputFluidTank(tankIndex).getFluid();
                int capacity = menu.getBlockEntity().getInputFluidTank(tankIndex).getCapacity();
                renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);

                // 渲染流体槽遮罩
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        tankX, tankY,
                        FLUID_TANK_MASK_U, FLUID_TANK_MASK_V,
                        FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);
                
                // 渲染清空流体按钮
                renderTankClearButton(guiGraphics, buttonX, tankY, tankIndex, true);
            }
        }
    }
    
    // 渲染单个流体槽的清空按钮
    private void renderTankClearButton(GuiGraphics guiGraphics, int x, int y, int tankIndex, boolean isInput) {
        // 根据按钮状态选择纹理，区分输入输出区域
        boolean isPressed = isInput ? inputTankClearButtonsPressed[tankIndex] : outputTankClearButtonsPressed[tankIndex];
        int u = isPressed ? TANK_CLEAR_BUTTON_PRESSED_U : TANK_CLEAR_BUTTON_U;
        int v = isPressed ? TANK_CLEAR_BUTTON_PRESSED_V : TANK_CLEAR_BUTTON_V;
        
        // 渲染按钮
        guiGraphics.blit(COMPONENTS_TEXTURE, x, y,
                u, v,
                TANK_CLEAR_BUTTON_WIDTH, TANK_CLEAR_BUTTON_HEIGHT);
    }

    // 渲染输出流体槽
    private void renderOutputFluidTanks(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        // 计算可见的流体槽数量
        int visibleTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalTanks = 6; // 固定6个输出流体槽
        
        // 计算流体槽和按钮的总宽度（流体槽宽度 + 1像素间隔 + 按钮宽度）
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;
        
        // 渲染可见的流体槽
        if (visibleTanks > 0) {
            // 计算每个槽位的垂直间距，使上下边距和槽位间隔相等
            int totalTankHeight = visibleTanks * FLUID_TANK_HEIGHT;
            int totalSpacing = FLUID_OUTPUT_AREA_HEIGHT - totalTankHeight;
            int spacing = visibleTanks > 1 ? totalSpacing / (visibleTanks + 1) : totalSpacing / 2;
            
            for (int i = outputFluidScrollOffset; i < Math.min(totalTanks, outputFluidScrollOffset + visibleTanks); i++) {
                int tankIndex = i;
                // 计算流体槽Y坐标，使上下边距和槽位间隔相等
                int tankY = y + FLUID_OUTPUT_AREA_Y + spacing + (i - outputFluidScrollOffset) * (FLUID_TANK_HEIGHT + spacing);
                // 整体横向居中：(区域宽度 - 总宽度) / 2
                int startX = x + FLUID_OUTPUT_AREA_X + (FLUID_OUTPUT_AREA_WIDTH - totalWidth) / 2;
                int tankX = startX;
                int buttonX = startX + FLUID_TANK_WIDTH + 1; // 流体槽右边空1像素

                // 渲染流体槽背景
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        tankX, tankY,
                        FLUID_TANK_U, FLUID_TANK_V,
                        FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);

                // 渲染流体
                FluidStack fluid = menu.getBlockEntity().getOutputFluidTank(tankIndex).getFluid();
                int capacity = menu.getBlockEntity().getOutputFluidTank(tankIndex).getCapacity();
                renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);

                // 渲染流体槽遮罩
                guiGraphics.blit(COMPONENTS_TEXTURE,
                        tankX, tankY,
                        FLUID_TANK_MASK_U, FLUID_TANK_MASK_V,
                        FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);
                
                // 渲染清空流体按钮
                renderTankClearButton(guiGraphics, buttonX, tankY, tankIndex, false);
            }
        }
    }

    // 新增：渲染催化剂和模具槽位 - 不渲染流体槽相关贴图
    private void renderCatalystAndMoldSlots(GuiGraphics guiGraphics, int x, int y) {
        // 槽位背景已在主GUI贴图中，不需要额外渲染流体槽贴图
    }

    // 修改：渲染指示灯，现在显示并行数状态
    private void renderIndicators(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int currentParallel = menu.getCurrentParallel();
        int maxParallel = menu.getMaxParallel();

        // 渲染催化剂指示灯 - 根据并行数状态显示不同颜色
        // 熄灭状态主图自带，不需要额外渲染，只渲染亮起状态
        if (currentParallel > 1) {
            guiGraphics.blit(COMPONENTS_TEXTURE,
                    x + CATALYST_INDICATOR_X, y + CATALYST_INDICATOR_Y,
                    LIT_INDICATOR_U, LIT_INDICATOR_V,
                    INDICATOR_WIDTH, INDICATOR_HEIGHT);
        }

        // 模具指示灯 - 使用menu中的方法获取需要模具的状态
        // 熄灭状态主图自带，不需要额外渲染，只渲染亮起状态
        boolean requiresMold = menu.requiresMold();
        if (requiresMold) {
            guiGraphics.blit(COMPONENTS_TEXTURE,
                    x + MOLD_INDICATOR_X, y + MOLD_INDICATOR_Y,
                    LIT_INDICATOR_U, LIT_INDICATOR_V,
                    INDICATOR_WIDTH, INDICATOR_HEIGHT);
        }
    }

    // 修改流体渲染方法，从左向右填充，使用完整单位，流体大小为53*15，离边缘1像素
    private void renderFluidTank(GuiGraphics guiGraphics, int x, int y, FluidStack fluid, int capacity) {
        if (!fluid.isEmpty() && capacity > 0) {
            // 流体渲染尺寸：离边缘1像素，大小53*15
            final int FLUID_RENDER_WIDTH = 53;
            final int FLUID_RENDER_HEIGHT = 15;
            final int FLUID_RENDER_X_OFFSET = 1;
            final int FLUID_RENDER_Y_OFFSET = 1;
            
            float fluidRatio = (float) fluid.getAmount() / capacity;
            int fluidWidth = (int) (FLUID_RENDER_WIDTH * fluidRatio); // 从左向右填充，所以计算宽度
            if (fluidWidth > 0) {
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

                    int renderX = x + FLUID_RENDER_X_OFFSET;
                    int renderY = y + FLUID_RENDER_Y_OFFSET;
                    int renderHeight = FLUID_RENDER_HEIGHT;
                    
                    // 渲染流体 - 从左向右填充
                    for (int i = 0; i < Math.ceil((double) fluidWidth / 16); i++) {
                        for (int j = 0; j < Math.ceil((double) renderHeight / 16); j++) {
                            int texWidth = Math.min(16, fluidWidth - i * 16);
                            int texHeight = Math.min(16, renderHeight - j * 16);

                            if (texWidth > 0 && texHeight > 0) {
                                guiGraphics.blit(
                                        renderX + i * 16,
                                        renderY + j * 16,
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
            SlotRendererUtil.renderFluidAmount(guiGraphics, fluid.getAmount(), x, y, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);
        }
    }

    // 渲染清空按钮

    // 渲染滑块
    private void renderSlider(GuiGraphics guiGraphics, int x, int y) {
        // 滑块槽不需要纹理坐标，主图自带，直接渲染滑块即可

        // 计算输入区域滑块位置
        int totalTanks = 6;
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int maxInputOffset = Math.max(0, totalTanks - visibleInputTanks);
        
        // 计算输入滑块可移动范围
        int inputSliderRange = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
        int inputSliderY = y + SLIDER_SLOT_Y;
        if (maxInputOffset > 0) {
            inputSliderY += (int) ((float) inputFluidScrollOffset / maxInputOffset * inputSliderRange);
        }

        // 渲染输入区域滑块
        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + SLIDER_SLOT_X - 2, inputSliderY,
                isDraggingInputSlider ? SLIDER_PRESSED_U : SLIDER_DEFAULT_U,
                isDraggingInputSlider ? SLIDER_PRESSED_V : SLIDER_DEFAULT_V,
                SLIDER_WIDTH, SLIDER_HEIGHT);
        
        // 计算输出区域滑块位置
        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int maxOutputOffset = Math.max(0, totalTanks - visibleOutputTanks);
        
        // 计算输出滑块可移动范围
        int outputSliderRange = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
        int outputSliderY = y + OUTPUT_SLIDER_SLOT_Y;
        if (maxOutputOffset > 0) {
            outputSliderY += (int) ((float) outputFluidScrollOffset / maxOutputOffset * outputSliderRange);
        }
        
        // 渲染输出区域滑块，滑块纹理起点0,72，大小7*15
        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + OUTPUT_SLIDER_SLOT_X - 2, outputSliderY,
                isDraggingOutputSlider ? SLIDER_PRESSED_U : SLIDER_DEFAULT_U,
                isDraggingOutputSlider ? SLIDER_PRESSED_V : SLIDER_DEFAULT_V,
                SLIDER_WIDTH, SLIDER_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 检查鼠标是否在流体输入区域内
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // 检查输入区域滚动
        if (isMouseOverArea((int) mouseX, (int) mouseY, x + FLUID_INPUT_AREA_X, y + FLUID_INPUT_AREA_Y,
                FLUID_INPUT_AREA_WIDTH, FLUID_INPUT_AREA_HEIGHT)) {
            // 滚动流体输入区域
            int scrollAmount = delta > 0 ? -1 : 1;
            int visibleTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
            inputFluidScrollOffset = Math.max(0, Math.min(6 - visibleTanks,
                    inputFluidScrollOffset + scrollAmount));
            return true;
        }
        
        // 检查输出区域滚动
        if (isMouseOverArea((int) mouseX, (int) mouseY, x + FLUID_OUTPUT_AREA_X, y + FLUID_OUTPUT_AREA_Y,
                FLUID_OUTPUT_AREA_WIDTH, FLUID_OUTPUT_AREA_HEIGHT)) {
            // 滚动流体输出区域
            int scrollAmount = delta > 0 ? -1 : 1;
            int visibleTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
            outputFluidScrollOffset = Math.max(0, Math.min(6 - visibleTanks,
                    outputFluidScrollOffset + scrollAmount));
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingInputSlider) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            
            // 计算新的滑块位置
            int sliderY = (int) mouseY - draggedInputSliderY;
            int minSliderY = y + SLIDER_SLOT_Y;
            int maxSliderY = minSliderY + SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            
            // 限制滑块位置
            sliderY = Math.max(minSliderY, Math.min(maxSliderY, sliderY));
            
            // 计算对应的滚动偏移
            int sliderRange = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            int totalTanks = 6;
            int visibleTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
            int maxOffset = Math.max(0, totalTanks - visibleTanks);
            
            if (maxOffset > 0) {
                inputFluidScrollOffset = (int) (((double) (sliderY - minSliderY) / sliderRange) * maxOffset);
                inputFluidScrollOffset = Math.max(0, Math.min(maxOffset, inputFluidScrollOffset));
            }
            
            return true;
        }
        
        // 处理输出滑块拖拽
        if (isDraggingOutputSlider) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            
            // 计算新的滑块位置
            int sliderY = (int) mouseY - draggedOutputSliderY;
            int minSliderY = y + OUTPUT_SLIDER_SLOT_Y;
            int maxSliderY = minSliderY + OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            
            // 限制滑块位置
            sliderY = Math.max(minSliderY, Math.min(maxSliderY, sliderY));
            
            // 计算对应的滚动偏移
            int sliderRange = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            int totalTanks = 6;
            int visibleTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
            int maxOffset = Math.max(0, totalTanks - visibleTanks);
            
            if (maxOffset > 0) {
                outputFluidScrollOffset = (int) (((double) (sliderY - minSliderY) / sliderRange) * maxOffset);
                outputFluidScrollOffset = Math.max(0, Math.min(maxOffset, outputFluidScrollOffset));
            }
            
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 取消渲染标题和物品栏文字，根据用户要求
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

        // 首先渲染物品的工具提示
        for (Slot slot : menu.slots) {
            if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    // 渲染物品的工具提示
                    guiGraphics.renderComponentTooltip(this.font, stack.getTooltipLines(minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL), mouseX, mouseY);
                }
            }
        }

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

        // 移除旧UI清空按钮的悬停提示

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
            tooltip.add(Component.literal("• 能量消耗乘以并行数（有用锭除外）").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 催化剂为可选项，可提高并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 有用锭作为催化剂时不会被消耗").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("⚠ 普通催化剂会被消耗").withStyle(ChatFormatting.RED));

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 催化剂槽位悬停提示 - 当槽内有物品时不再显示
        if (isMouseOverArea(mouseX, mouseY,
                x + menu.getCatalystSlotX(), y + menu.getCatalystSlotY(), 18, 18)) {
            // 检查催化剂槽是否有物品
            boolean hasCatalyst = false;
            if (menu.getBlockEntity() != null) {
                ItemStack catalyst = menu.getBlockEntity().getItemInSlot(12); // 催化剂槽位
                hasCatalyst = !catalyst.isEmpty();
            }
            
            // 只有当槽内没有物品时才显示提示
            if (!hasCatalyst) {
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
        }

        // 流体槽悬停提示
        renderFluidTooltips(guiGraphics, mouseX, mouseY, x, y);
    }

    private boolean isMouseOverProgressArea(int mouseX, int mouseY, int x, int y) {
        return isMouseOverArea(mouseX, mouseY, x + PROGRESS_LEFT_X, y + PROGRESS_LEFT_Y, PROGRESS_LEFT_WIDTH, PROGRESS_LEFT_HEIGHT) ||
                isMouseOverArea(mouseX, mouseY, x + PROGRESS_RIGHT_X, y + PROGRESS_RIGHT_Y, PROGRESS_RIGHT_WIDTH, PROGRESS_RIGHT_HEIGHT);
    }

    // 渲染流体槽悬浮提示
    private void renderFluidTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;
        
        // 计算可见的流体槽数量
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalTanks = 6;
        
        // 计算流体槽和按钮的总宽度
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;
        
        // 检查输入区域的流体槽
        if (visibleInputTanks > 0) {
            // 计算每个槽位的垂直间距，与渲染时保持一致
            int inputTotalTankHeight = visibleInputTanks * FLUID_TANK_HEIGHT;
            int inputTotalSpacing = FLUID_INPUT_AREA_HEIGHT - inputTotalTankHeight;
            int inputSpacing = visibleInputTanks > 1 ? inputTotalSpacing / (visibleInputTanks + 1) : inputTotalSpacing / 2;
            
            for (int i = inputFluidScrollOffset; i < Math.min(totalTanks, inputFluidScrollOffset + visibleInputTanks); i++) {
                int tankY = y + FLUID_INPUT_AREA_Y + inputSpacing + (i - inputFluidScrollOffset) * (FLUID_TANK_HEIGHT + inputSpacing);
                int startX = x + FLUID_INPUT_AREA_X + (FLUID_INPUT_AREA_WIDTH - totalWidth) / 2;
                int tankX = startX;
                
                if (isMouseOverArea(mouseX, mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) {
                    FluidStack fluid = menu.getBlockEntity().getInputFluidTank(i).getFluid();
                    if (!fluid.isEmpty()) {
                        renderFluidTooltip(guiGraphics, mouseX, mouseY, fluid, menu.getBlockEntity().getInputFluidTank(i).getCapacity());
                        return;
                    }
                }
            }
        }
        
        // 检查输出区域的流体槽
        if (visibleOutputTanks > 0) {
            // 计算每个槽位的垂直间距，与渲染时保持一致
            int outputTotalTankHeight = visibleOutputTanks * FLUID_TANK_HEIGHT;
            int outputTotalSpacing = FLUID_OUTPUT_AREA_HEIGHT - outputTotalTankHeight;
            int outputSpacing = visibleOutputTanks > 1 ? outputTotalSpacing / (visibleOutputTanks + 1) : outputTotalSpacing / 2;
            
            for (int i = outputFluidScrollOffset; i < Math.min(totalTanks, outputFluidScrollOffset + visibleOutputTanks); i++) {
                int tankY = y + FLUID_OUTPUT_AREA_Y + outputSpacing + (i - outputFluidScrollOffset) * (FLUID_TANK_HEIGHT + outputSpacing);
                int startX = x + FLUID_OUTPUT_AREA_X + (FLUID_OUTPUT_AREA_WIDTH - totalWidth) / 2;
                int tankX = startX;
                
                if (isMouseOverArea(mouseX, mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) {
                    FluidStack fluid = menu.getBlockEntity().getOutputFluidTank(i).getFluid();
                    if (!fluid.isEmpty()) {
                        renderFluidTooltip(guiGraphics, mouseX, mouseY, fluid, menu.getBlockEntity().getOutputFluidTank(i).getCapacity());
                        return;
                    }
                }
            }
        }
    }
    
    // 渲染单个流体槽的悬浮提示
    private void renderFluidTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, FluidStack fluid, int capacity) {
        List<Component> tooltip = new ArrayList<>();
        
        // 添加流体名称
        tooltip.add(fluid.getDisplayName().copy().withStyle(ChatFormatting.AQUA));
        
        // 添加流体数量
        tooltip.add(Component.literal(fluid.getAmount() + " / " + capacity + " mB").withStyle(ChatFormatting.WHITE));
        
        guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }
    
    private void renderFluidInteractions(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        // 移除旧的流体槽交互提示，因为现在使用的是新的流体输入输出区域
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu != null && menu.getBlockEntity() != null) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;

            // 移除旧UI的全局清空按钮检测

            // 检查是否点击了单个流体槽的清空按钮
            Object[] clickedInfo = getClickedTankClearButtonIndex(mouseX, mouseY);
            if (clickedInfo != null) {
                int clickedTankIndex = (int) clickedInfo[0];
                boolean isInput = (boolean) clickedInfo[1];
                // 更新对应区域的按钮状态
                if (isInput) {
                    inputTankClearButtonsPressed[clickedTankIndex] = true;
                } else {
                    outputTankClearButtonsPressed[clickedTankIndex] = true;
                }
                handleTankClearButton(clickedTankIndex, isInput);
                return true;
            }

            // 检查是否点击了输入流体槽区域
            if (isMouseOverFluidInputArea((int)mouseX, (int)mouseY)) {
                handleFluidInputAreaInteraction(mouseX, mouseY, button);
                return true;
            }

            // 检查是否点击了输出流体槽区域
            if (isMouseOverArea((int)mouseX, (int)mouseY, x + FLUID_OUTPUT_AREA_X, y + FLUID_OUTPUT_AREA_Y,
                    FLUID_OUTPUT_AREA_WIDTH, FLUID_OUTPUT_AREA_HEIGHT)) {
                handleFluidOutputAreaInteraction(mouseX, mouseY, button);
                return true;
            }
            
            // 检查是否点击了输入滑块
            if (isMouseOverInputSlider((int)mouseX, (int)mouseY)) {
                isDraggingInputSlider = true;
                int totalTanks = 6;
                int visibleTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
                int maxOffset = Math.max(0, totalTanks - visibleTanks);
                int sliderRange = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
                int sliderY = y + SLIDER_SLOT_Y;
                if (maxOffset > 0) {
                    sliderY += (int) ((float) inputFluidScrollOffset / maxOffset * sliderRange);
                }
                draggedInputSliderY = (int) mouseY - sliderY;
                return true;
            }
            
            // 检查是否点击了输出滑块
            if (isMouseOverOutputSlider((int)mouseX, (int)mouseY)) {
                isDraggingOutputSlider = true;
                int totalTanks = 6;
                int visibleTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
                int maxOffset = Math.max(0, totalTanks - visibleTanks);
                int sliderRange = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
                int sliderY = y + OUTPUT_SLIDER_SLOT_Y;
                if (maxOffset > 0) {
                    sliderY += (int) ((float) outputFluidScrollOffset / maxOffset * sliderRange);
                }
                draggedOutputSliderY = (int) mouseY - sliderY;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 移除旧UI的清空按钮释放处理，只处理新UI的每槽清空按钮
        
        // 检查是否释放了输入区域单个流体槽的清空按钮
        for (int i = 0; i < inputTankClearButtonsPressed.length; i++) {
            if (inputTankClearButtonsPressed[i]) {
                inputTankClearButtonsPressed[i] = false;
                return true;
            }
        }
        
        // 检查是否释放了输出区域单个流体槽的清空按钮
        for (int i = 0; i < outputTankClearButtonsPressed.length; i++) {
            if (outputTankClearButtonsPressed[i]) {
                outputTankClearButtonsPressed[i] = false;
                return true;
            }
        }
        
        // 处理输入滑块释放
        if (isDraggingInputSlider) {
            isDraggingInputSlider = false;
            return true;
        }
        
        // 处理输出滑块释放
        if (isDraggingOutputSlider) {
            isDraggingOutputSlider = false;
            return true;
        }
        
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    // 获取被点击的流体槽清空按钮索引，返回数组 [tankIndex, isInput]
    private Object[] getClickedTankClearButtonIndex(double mouseX, double mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // 计算可见的流体槽数量
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalTanks = 6;
        
        // 计算流体槽和按钮的总宽度
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;
        
        // 检查输入区域的清空按钮
        if (visibleInputTanks > 0) {
            // 计算每个槽位的垂直间距，与渲染时保持一致
            int inputTotalTankHeight = visibleInputTanks * FLUID_TANK_HEIGHT;
            int inputTotalSpacing = FLUID_INPUT_AREA_HEIGHT - inputTotalTankHeight;
            int inputSpacing = visibleInputTanks > 1 ? inputTotalSpacing / (visibleInputTanks + 1) : inputTotalSpacing / 2;
            
            for (int i = inputFluidScrollOffset; i < Math.min(totalTanks, inputFluidScrollOffset + visibleInputTanks); i++) {
                int tankY = y + FLUID_INPUT_AREA_Y + inputSpacing + (i - inputFluidScrollOffset) * (FLUID_TANK_HEIGHT + inputSpacing);
                int startX = x + FLUID_INPUT_AREA_X + (FLUID_INPUT_AREA_WIDTH - totalWidth) / 2;
                int buttonX = startX + FLUID_TANK_WIDTH + 1;
                
                if (isMouseOverArea((int)mouseX, (int)mouseY, buttonX, tankY, TANK_CLEAR_BUTTON_WIDTH, TANK_CLEAR_BUTTON_HEIGHT)) {
                    return new Object[]{i, true}; // 返回槽位索引和输入区域标记
                }
            }
        }
        
        // 检查输出区域的清空按钮
        if (visibleOutputTanks > 0) {
            // 计算每个槽位的垂直间距，与渲染时保持一致
            int outputTotalTankHeight = visibleOutputTanks * FLUID_TANK_HEIGHT;
            int outputTotalSpacing = FLUID_OUTPUT_AREA_HEIGHT - outputTotalTankHeight;
            int outputSpacing = visibleOutputTanks > 1 ? outputTotalSpacing / (visibleOutputTanks + 1) : outputTotalSpacing / 2;
            
            for (int i = outputFluidScrollOffset; i < Math.min(totalTanks, outputFluidScrollOffset + visibleOutputTanks); i++) {
                int tankY = y + FLUID_OUTPUT_AREA_Y + outputSpacing + (i - outputFluidScrollOffset) * (FLUID_TANK_HEIGHT + outputSpacing);
                int startX = x + FLUID_OUTPUT_AREA_X + (FLUID_OUTPUT_AREA_WIDTH - totalWidth) / 2;
                int buttonX = startX + FLUID_TANK_WIDTH + 1;
                
                if (isMouseOverArea((int)mouseX, (int)mouseY, buttonX, tankY, TANK_CLEAR_BUTTON_WIDTH, TANK_CLEAR_BUTTON_HEIGHT)) {
                    return new Object[]{i, false}; // 返回槽位索引和输出区域标记
                }
            }
        }
        return null; // 未点击任何按钮
    }
    
    // 检查鼠标是否在流体输入区域内
    private boolean isMouseOverFluidInputArea(int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        return isMouseOverArea(mouseX, mouseY, x + FLUID_INPUT_AREA_X, y + FLUID_INPUT_AREA_Y,
                FLUID_INPUT_AREA_WIDTH, FLUID_INPUT_AREA_HEIGHT);
    }
    
    // 检查鼠标是否在输入滑块上
    private boolean isMouseOverInputSlider(int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // 计算输入滑块位置
        int totalTanks = 6;
        int visibleTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int maxOffset = Math.max(0, totalTanks - visibleTanks);
        
        // 计算滑块可移动范围
        int sliderRange = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
        int sliderY = y + SLIDER_SLOT_Y;
        if (maxOffset > 0) {
            sliderY += (int) ((float) inputFluidScrollOffset / maxOffset * sliderRange);
        }
        
        // 检查鼠标是否在滑块上
        return isMouseOverArea(mouseX, mouseY, x + SLIDER_SLOT_X - 2, sliderY, SLIDER_WIDTH, SLIDER_HEIGHT);
    }
    
    // 检查鼠标是否在输出滑块上
    private boolean isMouseOverOutputSlider(int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // 计算输出滑块位置
        int totalTanks = 6;
        int visibleTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int maxOffset = Math.max(0, totalTanks - visibleTanks);
        
        // 计算滑块可移动范围
        int sliderRange = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
        int sliderY = y + OUTPUT_SLIDER_SLOT_Y;
        if (maxOffset > 0) {
            sliderY += (int) ((float) outputFluidScrollOffset / maxOffset * sliderRange);
        }
        
        // 检查鼠标是否在滑块上
        return isMouseOverArea(mouseX, mouseY, x + OUTPUT_SLIDER_SLOT_X - 2, sliderY, SLIDER_WIDTH, SLIDER_HEIGHT);
    }
    
    // 处理流体输入区域的交互
    private void handleFluidInputAreaInteraction(double mouseX, double mouseY, int button) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // 计算可见的流体槽数量
        int visibleTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalTanks = 6;
        
        // 计算流体槽和按钮的总宽度
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;
        
        if (visibleTanks > 0) {
            // 计算每个槽位的垂直间距，与渲染时保持一致
            int totalTankHeight = visibleTanks * FLUID_TANK_HEIGHT;
            int totalSpacing = FLUID_INPUT_AREA_HEIGHT - totalTankHeight;
            int spacing = visibleTanks > 1 ? totalSpacing / (visibleTanks + 1) : totalSpacing / 2;
            
            for (int i = inputFluidScrollOffset; i < Math.min(totalTanks, inputFluidScrollOffset + visibleTanks); i++) {
                int tankIndex = i;
                int tankY = y + FLUID_INPUT_AREA_Y + spacing + (i - inputFluidScrollOffset) * (FLUID_TANK_HEIGHT + spacing);
                int startX = x + FLUID_INPUT_AREA_X + (FLUID_INPUT_AREA_WIDTH - totalWidth) / 2;
                int tankX = startX;
                
                if (isMouseOverArea((int)mouseX, (int)mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) {
                    handleFluidInteraction(true, button, tankIndex);
                    return;
                }
            }
        }
    }
    
    // 处理流体输出区域的交互
    private void handleFluidOutputAreaInteraction(double mouseX, double mouseY, int button) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // 计算可见的流体槽数量
        int visibleTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalTanks = 6;
        
        // 计算流体槽和按钮的总宽度
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;
        
        if (visibleTanks > 0) {
            // 计算每个槽位的垂直间距，与渲染时保持一致
            int totalTankHeight = visibleTanks * FLUID_TANK_HEIGHT;
            int totalSpacing = FLUID_OUTPUT_AREA_HEIGHT - totalTankHeight;
            int spacing = visibleTanks > 1 ? totalSpacing / (visibleTanks + 1) : totalSpacing / 2;
            
            for (int i = outputFluidScrollOffset; i < Math.min(totalTanks, outputFluidScrollOffset + visibleTanks); i++) {
                int tankIndex = i;
                int tankY = y + FLUID_OUTPUT_AREA_Y + spacing + (i - outputFluidScrollOffset) * (FLUID_TANK_HEIGHT + spacing);
                int startX = x + FLUID_OUTPUT_AREA_X + (FLUID_OUTPUT_AREA_WIDTH - totalWidth) / 2;
                int tankX = startX;
                
                if (isMouseOverArea((int)mouseX, (int)mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) {
                    handleFluidInteraction(false, button, tankIndex);
                    return;
                }
            }
        }
    }
    
    // 处理单个流体槽的清空按钮点击
    private void handleTankClearButton(int tankIndex, boolean isInput) {
        if (minecraft == null || minecraft.player == null) return;
        
        // 发送数据包清空指定槽位的流体，区分输入输出区域
        UselessMod.NETWORK.sendToServer(new ClearFluidPacket(
                menu.getBlockEntity().getBlockPos(),
                isInput,
                tankIndex
        ));
    }


    private void handleFluidInteraction(boolean isInputTank, int button) {
        handleFluidInteraction(isInputTank, button, 0);
    }
    
    private void handleFluidInteraction(boolean isInputTank, int button, int tankIndex) {
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
                button,
                tankIndex
        ));
    }

    private boolean isMouseOverArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}