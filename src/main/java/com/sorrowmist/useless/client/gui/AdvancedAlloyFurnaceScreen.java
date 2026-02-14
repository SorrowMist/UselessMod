package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.network.TankClearPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 高级合金炉GUI屏幕
 */
public class AdvancedAlloyFurnaceScreen extends AbstractContainerScreen<AdvancedAlloyFurnaceMenu> {

    // ==================== 纹理资源 ====================
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/advanced_alloy_furnace_gui.png");

    private static final ResourceLocation COMPONENTS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/advanced_alloy_furnace_zu_jian.png");

    // ==================== GUI尺寸 ====================
    private static final int DISPLAY_WIDTH = 176;
    private static final int DISPLAY_HEIGHT = 260;
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 260;

    // ==================== 进度条 ====================
    private static final int PROGRESS_LEFT_X = 32;
    private static final int PROGRESS_LEFT_Y = 72;
    private static final int PROGRESS_LEFT_WIDTH = 24;
    private static final int PROGRESS_LEFT_HEIGHT = 25;

    private static final int PROGRESS_RIGHT_X = 120;
    private static final int PROGRESS_RIGHT_Y = 90;
    private static final int PROGRESS_RIGHT_WIDTH = 27;
    private static final int PROGRESS_RIGHT_HEIGHT = 21;

    // ==================== 能量条 ====================
    private static final int ENERGY_BAR_X = 58;
    private static final int ENERGY_BAR_Y = 3;
    private static final int ENERGY_BAR_WIDTH = 60;
    private static final int ENERGY_BAR_HEIGHT = 6;

    private static final int ENERGY_MASK_X = 57;
    private static final int ENERGY_MASK_Y = 3;
    private static final int ENERGY_MASK_WIDTH = 62;
    private static final int ENERGY_MASK_HEIGHT = 7;

    // ==================== 流体区域 ====================
    private static final int FLUID_INPUT_AREA_X = 74;
    private static final int FLUID_INPUT_AREA_Y = 20;
    private static final int FLUID_INPUT_AREA_WIDTH = 86;
    private static final int FLUID_INPUT_AREA_HEIGHT = 50;

    private static final int FLUID_OUTPUT_AREA_X = 8;
    private static final int FLUID_OUTPUT_AREA_Y = 115;
    private static final int FLUID_OUTPUT_AREA_WIDTH = 86;
    private static final int FLUID_OUTPUT_AREA_HEIGHT = 50;

    // ==================== 流体槽 ====================
    private static final int FLUID_TANK_WIDTH = 55;
    private static final int FLUID_TANK_HEIGHT = 17;
    private static final int FLUID_TANK_SPACING = 2;

    // ==================== 滑块 ====================
    private static final int SLIDER_SLOT_X = 161;
    private static final int SLIDER_SLOT_Y = 24;
    private static final int SLIDER_SLOT_WIDTH = 3;
    private static final int SLIDER_SLOT_HEIGHT = 42;
    private static final int SLIDER_WIDTH = 7;
    private static final int SLIDER_HEIGHT = 15;

    private static final int OUTPUT_SLIDER_SLOT_X = 95;
    private static final int OUTPUT_SLIDER_SLOT_Y = 119;
    private static final int OUTPUT_SLIDER_SLOT_WIDTH = 3;
    private static final int OUTPUT_SLIDER_SLOT_HEIGHT = 42;

    // ==================== 槽位位置 ====================
    private static final int CATALYST_SLOT_X = 61;
    private static final int CATALYST_SLOT_Y = 87;
    private static final int MOLD_SLOT_X = 99;
    private static final int MOLD_SLOT_Y = 87;

    // ==================== 指示灯 ====================
    private static final int CATALYST_INDICATOR_X = 67;
    private static final int CATALYST_INDICATOR_Y = 80;
    private static final int MOLD_INDICATOR_X = 105;
    private static final int MOLD_INDICATOR_Y = 80;
    private static final int INDICATOR_WIDTH = 4;
    private static final int INDICATOR_HEIGHT = 5;

    // ==================== 问号区域（并行数信息） ====================
    private static final int TIPS_AREA_X = 80;
    private static final int TIPS_AREA_Y = 87;
    private static final int TIPS_AREA_WIDTH = 16;
    private static final int TIPS_AREA_HEIGHT = 16;

    // ==================== 标题位置 ====================
    private static final int TITLE_LABEL_X = 66;
    private static final int TITLE_LABEL_Y = 52;
    private static final int INVENTORY_LABEL_X = 10;
    private static final int INVENTORY_LABEL_Y = 168;

    // ==================== 组件纹理坐标 ====================
    private static final int ENERGY_BAR_U = 0;
    private static final int ENERGY_BAR_V = 94;
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

    private static final int LIT_INDICATOR_U = 0;
    private static final int LIT_INDICATOR_V = 88;

    private static final int SLIDER_DEFAULT_U = 0;
    private static final int SLIDER_DEFAULT_V = 72;
    private static final int SLIDER_PRESSED_U = 18;
    private static final int SLIDER_PRESSED_V = 72;

    // ==================== 清空按钮纹理 ====================
    private static final int TANK_CLEAR_BUTTON_U = 0;
    private static final int TANK_CLEAR_BUTTON_V = 36;
    private static final int TANK_CLEAR_BUTTON_PRESSED_U = 18;
    private static final int TANK_CLEAR_BUTTON_PRESSED_V = 36;
    private static final int TANK_CLEAR_BUTTON_LIT_U = 18;
    private static final int TANK_CLEAR_BUTTON_LIT_V = 36;
    private static final int TANK_CLEAR_BUTTON_LIT_PRESSED_U = 0;
    private static final int TANK_CLEAR_BUTTON_LIT_PRESSED_V = 36;
    private static final int TANK_CLEAR_BUTTON_WIDTH = 17;
    private static final int TANK_CLEAR_BUTTON_HEIGHT = 17;

    // ==================== 状态变量 ====================
    private final boolean[] inputTankClearButtonsPressed = new boolean[AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT];
    private final boolean[] outputTankClearButtonsPressed = new boolean[AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT];

    private int inputFluidScrollOffset = 0;
    private boolean isDraggingInputSlider = false;
    private int draggedInputSliderY = 0;

    private int outputFluidScrollOffset = 0;
    private boolean isDraggingOutputSlider = false;
    private int draggedOutputSliderY = 0;

    public AdvancedAlloyFurnaceScreen(AdvancedAlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = DISPLAY_WIDTH;
        this.imageHeight = DISPLAY_HEIGHT;
        this.titleLabelX = TITLE_LABEL_X;
        this.titleLabelY = TITLE_LABEL_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        this.renderFluidTankTooltip(guiGraphics, mouseX, mouseY, x, y, true);
        this.renderFluidTankTooltip(guiGraphics, mouseX, mouseY, x, y, false);

        // 渲染能量条悬停提示
        this.renderEnergyTooltip(guiGraphics, mouseX, mouseY, x, y);

        // 渲染进度条悬停提示
        this.renderProgressTooltip(guiGraphics, mouseX, mouseY, x, y);

        // 渲染问号区域悬停提示（并行数信息）
        this.renderTipsTooltip(guiGraphics, mouseX, mouseY, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                               0x404040, false
        );
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        this.renderEnergyBar(guiGraphics, x, y);
        this.renderProgressBar(guiGraphics, x, y);
        this.renderFluidInputArea(guiGraphics, x, y);
        this.renderOutputFluidTanks(guiGraphics, x, y);
        this.renderIndicators(guiGraphics, x, y);
        this.renderSlider(guiGraphics, x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 检查是否点击了进度条区域（JEI配方查看）
        if (this.handleProgressClick(mouseX, mouseY, x, y)) {
            return true;
        }

        if (this.handleInputSliderClick(mouseX, mouseY, x, y)) {
            return true;
        }

        if (this.handleOutputSliderClick(mouseX, mouseY, x, y)) {
            return true;
        }

        if (this.checkTankClearButtonClick(mouseX, mouseY, x, y, true)) {
            return true;
        }

        if (this.checkTankClearButtonClick(mouseX, mouseY, x, y, false)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingInputSlider) {
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;
            int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
            int maxScroll = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            int relativeY = (int) mouseY - (y + SLIDER_SLOT_Y) - this.draggedInputSliderY;
            float scrollRatio = (float) relativeY / maxScroll;
            this.inputFluidScrollOffset = Math.max(0, Math.min(
                                                           AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks,
                                                           (int) (scrollRatio * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks))
                                                   )
            );
            return true;
        }

        if (this.isDraggingOutputSlider) {
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;
            int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
            int maxScroll = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            int relativeY = (int) mouseY - (y + OUTPUT_SLIDER_SLOT_Y) - this.draggedOutputSliderY;
            float scrollRatio = (float) relativeY / maxScroll;
            this.outputFluidScrollOffset = Math.max(0, Math.min(
                                                            AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks,
                                                            (int) (scrollRatio * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks))
                                                    )
            );
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingInputSlider = false;
        this.isDraggingOutputSlider = false;

        for (int i = 0; i < AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT; i++) {
            this.inputTankClearButtonsPressed[i] = false;
            this.outputTankClearButtonsPressed[i] = false;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 计算流体区域布局
     */
    private FluidAreaLayout calculateFluidAreaLayout(int areaX, int areaY, int areaWidth, int areaHeight, int x,
                                                     int y) {
        int visibleTanks = areaHeight / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int totalWidth = FLUID_TANK_WIDTH + 1 + TANK_CLEAR_BUTTON_WIDTH;

        if (visibleTanks <= 0) {
            return new FluidAreaLayout(0, 0, 0, totalWidth);
        }

        int totalTankHeight = visibleTanks * FLUID_TANK_HEIGHT;
        int totalSpacing = areaHeight - totalTankHeight;
        int spacing = visibleTanks > 1 ? totalSpacing / (visibleTanks + 1) : totalSpacing / 2;
        int startX = x + areaX + (areaWidth - totalWidth) / 2;

        return new FluidAreaLayout(visibleTanks, spacing, startX, totalWidth);
    }

    /**
     * 获取流体槽的Y坐标
     */
    private int getFluidTankY(int areaY, int spacing, int tankIndex, int scrollOffset, int y) {
        return y + areaY + spacing + (tankIndex - scrollOffset) * (FLUID_TANK_HEIGHT + spacing);
    }

    /**
     * 渲染流体槽悬停提示
     */
    private void renderFluidTankTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y,
                                        boolean isInput) {
        int areaX = isInput ? FLUID_INPUT_AREA_X : FLUID_OUTPUT_AREA_X;
        int areaY = isInput ? FLUID_INPUT_AREA_Y : FLUID_OUTPUT_AREA_Y;
        int areaWidth = isInput ? FLUID_INPUT_AREA_WIDTH : FLUID_OUTPUT_AREA_WIDTH;
        int areaHeight = isInput ? FLUID_INPUT_AREA_HEIGHT : FLUID_OUTPUT_AREA_HEIGHT;
        int scrollOffset = isInput ? this.inputFluidScrollOffset : this.outputFluidScrollOffset;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(areaX, areaY, areaWidth, areaHeight, x, y);

        if (layout.visibleTanks > 0) {
            for (int i = scrollOffset; i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT,
                                                    scrollOffset + layout.visibleTanks
            ); i++) {
                int tankY = this.getFluidTankY(areaY, layout.spacing, i, scrollOffset, y);
                int tankX = layout.startX;

                if (mouseX >= tankX && mouseX < tankX + FLUID_TANK_WIDTH &&
                        mouseY >= tankY && mouseY < tankY + FLUID_TANK_HEIGHT) {
                    FluidStack fluid = isInput ?
                            this.menu.getInputFluidTank(i).getFluid() :
                            this.menu.getOutputFluidTank(i).getFluid();
                    int capacity = isInput ?
                            this.menu.getInputFluidTank(i).getCapacity() :
                            this.menu.getOutputFluidTank(i).getCapacity();

                    if (!fluid.isEmpty()) {
                        Component fluidName = fluid.getHoverName();
                        Component amountText = Component.literal(
                                String.format("%,d / %,d mB", fluid.getAmount(), capacity));

                        guiGraphics.renderTooltip(this.font, List.of(fluidName, amountText),
                                                  Optional.empty(), mouseX, mouseY
                        );
                    }
                    break;
                }
            }
        }
    }

    /**
     * 处理输入区域滑块点击
     */
    private boolean handleInputSliderClick(double mouseX, double mouseY, int x, int y) {
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT <= visibleInputTanks) {
            return false;
        }

        float inputScrollRatio = (float) this.inputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks);
        int inputSliderY = (int) (inputScrollRatio * (SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));
        int sliderX = x + SLIDER_SLOT_X - (SLIDER_WIDTH - SLIDER_SLOT_WIDTH) / 2;
        int sliderY = y + SLIDER_SLOT_Y + inputSliderY;

        if (mouseX >= sliderX && mouseX < sliderX + SLIDER_WIDTH &&
                mouseY >= sliderY && mouseY < sliderY + SLIDER_HEIGHT) {
            this.isDraggingInputSlider = true;
            this.draggedInputSliderY = (int) mouseY - sliderY;
            return true;
        }

        if (mouseX >= x + SLIDER_SLOT_X && mouseX < x + SLIDER_SLOT_X + SLIDER_SLOT_WIDTH &&
                mouseY >= y + SLIDER_SLOT_Y && mouseY < y + SLIDER_SLOT_Y + SLIDER_SLOT_HEIGHT) {
            int clickY = (int) mouseY - (y + SLIDER_SLOT_Y) - SLIDER_HEIGHT / 2;
            int maxScroll = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            this.inputFluidScrollOffset = Math.max(0, Math.min(
                                                           AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks,
                                                           (int) ((float) clickY / maxScroll * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks))
                                                   )
            );
            return true;
        }

        return false;
    }

    /**
     * 处理输出区域滑块点击
     */
    private boolean handleOutputSliderClick(double mouseX, double mouseY, int x, int y) {
        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT <= visibleOutputTanks) {
            return false;
        }

        float outputScrollRatio = (float) this.outputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks);
        int outputSliderY = (int) (outputScrollRatio * (OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));
        int sliderX = x + OUTPUT_SLIDER_SLOT_X - (SLIDER_WIDTH - OUTPUT_SLIDER_SLOT_WIDTH) / 2;
        int sliderY = y + OUTPUT_SLIDER_SLOT_Y + outputSliderY;

        if (mouseX >= sliderX && mouseX < sliderX + SLIDER_WIDTH &&
                mouseY >= sliderY && mouseY < sliderY + SLIDER_HEIGHT) {
            this.isDraggingOutputSlider = true;
            this.draggedOutputSliderY = (int) mouseY - sliderY;
            return true;
        }

        if (mouseX >= x + OUTPUT_SLIDER_SLOT_X && mouseX < x + OUTPUT_SLIDER_SLOT_X + OUTPUT_SLIDER_SLOT_WIDTH &&
                mouseY >= y + OUTPUT_SLIDER_SLOT_Y && mouseY < y + OUTPUT_SLIDER_SLOT_Y + OUTPUT_SLIDER_SLOT_HEIGHT) {
            int clickY = (int) mouseY - (y + OUTPUT_SLIDER_SLOT_Y) - SLIDER_HEIGHT / 2;
            int maxScroll = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            this.outputFluidScrollOffset = Math.max(0, Math.min(
                                                            AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks,
                                                            (int) ((float) clickY / maxScroll * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks))
                                                    )
            );
            return true;
        }

        return false;
    }

    /**
     * 检查并处理清空按钮点击
     */
    private boolean checkTankClearButtonClick(double mouseX, double mouseY, int x, int y, boolean isInput) {
        int areaX = isInput ? FLUID_INPUT_AREA_X : FLUID_OUTPUT_AREA_X;
        int areaY = isInput ? FLUID_INPUT_AREA_Y : FLUID_OUTPUT_AREA_Y;
        int areaWidth = isInput ? FLUID_INPUT_AREA_WIDTH : FLUID_OUTPUT_AREA_WIDTH;
        int areaHeight = isInput ? FLUID_INPUT_AREA_HEIGHT : FLUID_OUTPUT_AREA_HEIGHT;
        int scrollOffset = isInput ? this.inputFluidScrollOffset : this.outputFluidScrollOffset;
        boolean[] buttonsPressed = isInput ? this.inputTankClearButtonsPressed : this.outputTankClearButtonsPressed;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(areaX, areaY, areaWidth, areaHeight, x, y);

        if (layout.visibleTanks > 0) {
            for (int i = scrollOffset; i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT,
                                                    scrollOffset + layout.visibleTanks
            ); i++) {
                int tankY = this.getFluidTankY(areaY, layout.spacing, i, scrollOffset, y);
                int buttonX = layout.startX + FLUID_TANK_WIDTH + 1;

                if (mouseX >= buttonX && mouseX < buttonX + TANK_CLEAR_BUTTON_WIDTH &&
                        mouseY >= tankY && mouseY < tankY + TANK_CLEAR_BUTTON_HEIGHT) {
                    boolean hasFluid = isInput ?
                            !this.menu.getInputFluidTank(i).getFluid().isEmpty() :
                            !this.menu.getOutputFluidTank(i).getFluid().isEmpty();

                    if (hasFluid) {
                        buttonsPressed[i] = true;

                        if (this.menu.getBlockEntity() != null) {
                            this.menu.getBlockEntity().clearFluidTank(i, isInput);

                            PacketDistributor.sendToServer(new TankClearPacket(
                                    this.menu.getBlockEntity().getBlockPos(),
                                    i,
                                    isInput
                            ));
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (this.handleInputAreaScroll(mouseX, mouseY, x, y, scrollY)) {
            return true;
        }

        if (this.handleOutputAreaScroll(mouseX, mouseY, x, y, scrollY)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 处理输入区域滚动
     */
    private boolean handleInputAreaScroll(double mouseX, double mouseY, int x, int y, double scrollY) {
        if (mouseX < x + FLUID_INPUT_AREA_X || mouseX >= x + FLUID_INPUT_AREA_X + FLUID_INPUT_AREA_WIDTH ||
                mouseY < y + FLUID_INPUT_AREA_Y || mouseY >= y + FLUID_INPUT_AREA_Y + FLUID_INPUT_AREA_HEIGHT) {
            return false;
        }

        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT <= visibleInputTanks) {
            return false;
        }

        if (scrollY > 0) {
            this.inputFluidScrollOffset = Math.max(0, this.inputFluidScrollOffset - 1);
        } else if (scrollY < 0) {
            this.inputFluidScrollOffset = Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks,
                                                   this.inputFluidScrollOffset + 1
            );
        }
        return true;
    }

    /**
     * 处理输出区域滚动
     */
    private boolean handleOutputAreaScroll(double mouseX, double mouseY, int x, int y, double scrollY) {
        if (mouseX < x + FLUID_OUTPUT_AREA_X || mouseX >= x + FLUID_OUTPUT_AREA_X + FLUID_OUTPUT_AREA_WIDTH ||
                mouseY < y + FLUID_OUTPUT_AREA_Y || mouseY >= y + FLUID_OUTPUT_AREA_Y + FLUID_OUTPUT_AREA_HEIGHT) {
            return false;
        }

        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT <= visibleOutputTanks) {
            return false;
        }

        if (scrollY > 0) {
            this.outputFluidScrollOffset = Math.max(0, this.outputFluidScrollOffset - 1);
        } else if (scrollY < 0) {
            this.outputFluidScrollOffset = Math.min(
                    AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks,
                    this.outputFluidScrollOffset + 1
            );
        }
        return true;
    }

    /**
     * 渲染能量条
     */
    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu == null) return;

        int energyStored = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        if (maxEnergy > 0) {
            float energyRatio = (float) energyStored / maxEnergy;
            int energyWidth = (int) (ENERGY_BAR_WIDTH * energyRatio);

            if (energyWidth > 0) {
                guiGraphics.blit(COMPONENTS_TEXTURE,
                                 x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                                 ENERGY_BAR_U, ENERGY_BAR_V,
                                 energyWidth, ENERGY_BAR_HEIGHT
                );
            }

            guiGraphics.blit(COMPONENTS_TEXTURE,
                             x + ENERGY_MASK_X, y + ENERGY_MASK_Y,
                             ENERGY_MASK_U, ENERGY_MASK_V,
                             ENERGY_MASK_WIDTH, ENERGY_MASK_HEIGHT
            );
        }
    }

    /**
     * 渲染进度条
     */
    private void renderProgressBar(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu == null) return;

        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        if (maxProgress > 0 && progress > 0) {
            float progressRatio = (float) progress / maxProgress;

            int leftProgressHeight = (int) Math.ceil(PROGRESS_LEFT_HEIGHT * Math.min(1.0f, progressRatio * 2));
            if (leftProgressHeight > 0) {
                guiGraphics.blit(COMPONENTS_TEXTURE,
                                 x + PROGRESS_LEFT_X, y + PROGRESS_LEFT_Y,
                                 PROGRESS_LEFT_MASK_U, PROGRESS_LEFT_MASK_V,
                                 PROGRESS_LEFT_WIDTH, leftProgressHeight
                );
            }

            if (progressRatio > 0.5f) {
                float secondSegmentRatio = (progressRatio - 0.5f) * 2;
                int rightProgressHeight = (int) Math.ceil(PROGRESS_RIGHT_HEIGHT * secondSegmentRatio);
                if (rightProgressHeight > 0) {
                    guiGraphics.blit(COMPONENTS_TEXTURE,
                                     x + PROGRESS_RIGHT_X, y + PROGRESS_RIGHT_Y,
                                     PROGRESS_RIGHT_MASK_U, PROGRESS_RIGHT_MASK_V,
                                     PROGRESS_RIGHT_WIDTH, rightProgressHeight
                    );
                }
            }
        }
    }

    /**
     * 渲染流体输入区域
     */
    private void renderFluidInputArea(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu == null || this.menu.getBlockEntity() == null) return;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(FLUID_INPUT_AREA_X, FLUID_INPUT_AREA_Y,
                                                               FLUID_INPUT_AREA_WIDTH, FLUID_INPUT_AREA_HEIGHT, x, y
        );

        int maxOffset = Math.max(0, AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - layout.visibleTanks);
        this.inputFluidScrollOffset = Math.max(0, Math.min(this.inputFluidScrollOffset, maxOffset));

        if (layout.visibleTanks > 0) {
            for (int i = this.inputFluidScrollOffset;
                 i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT,
                              this.inputFluidScrollOffset + layout.visibleTanks
                 );
                 i++) {
                int tankY = this.getFluidTankY(FLUID_INPUT_AREA_Y, layout.spacing, i, this.inputFluidScrollOffset, y);
                int tankX = layout.startX;
                int buttonX = layout.startX + FLUID_TANK_WIDTH + 1;

                guiGraphics.blit(COMPONENTS_TEXTURE,
                                 tankX, tankY,
                                 FLUID_TANK_U, FLUID_TANK_V,
                                 FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT
                );

                FluidStack fluid = this.menu.getInputFluidTank(i).getFluid();
                int capacity = this.menu.getInputFluidTank(i).getCapacity();
                this.renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);

                guiGraphics.blit(COMPONENTS_TEXTURE,
                                 tankX, tankY,
                                 FLUID_TANK_MASK_U, FLUID_TANK_MASK_V,
                                 FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT
                );

                this.renderTankClearButton(guiGraphics, buttonX, tankY, i, true);
            }
        }
    }

    /**
     * 渲染清空按钮
     */
    private void renderTankClearButton(GuiGraphics guiGraphics, int x, int y, int tankIndex, boolean isInput) {
        boolean isPressed = isInput ?
                this.inputTankClearButtonsPressed[tankIndex] :
                this.outputTankClearButtonsPressed[tankIndex];

        boolean hasFluid = false;
        if (this.menu != null) {
            if (isInput) {
                hasFluid = !this.menu.getInputFluidTank(tankIndex).getFluid().isEmpty();
            } else {
                hasFluid = !this.menu.getOutputFluidTank(tankIndex).getFluid().isEmpty();
            }
        }

        int u, v;
        if (hasFluid) {
            u = isPressed ? TANK_CLEAR_BUTTON_LIT_PRESSED_U : TANK_CLEAR_BUTTON_LIT_U;
            v = isPressed ? TANK_CLEAR_BUTTON_LIT_PRESSED_V : TANK_CLEAR_BUTTON_LIT_V;
        } else {
            u = isPressed ? TANK_CLEAR_BUTTON_PRESSED_U : TANK_CLEAR_BUTTON_U;
            v = isPressed ? TANK_CLEAR_BUTTON_PRESSED_V : TANK_CLEAR_BUTTON_V;
        }

        guiGraphics.blit(COMPONENTS_TEXTURE, x, y, u, v,
                         TANK_CLEAR_BUTTON_WIDTH, TANK_CLEAR_BUTTON_HEIGHT
        );
    }

    /**
     * 渲染输出流体槽
     */
    private void renderOutputFluidTanks(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu == null || this.menu.getBlockEntity() == null) return;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(FLUID_OUTPUT_AREA_X, FLUID_OUTPUT_AREA_Y,
                                                               FLUID_OUTPUT_AREA_WIDTH, FLUID_OUTPUT_AREA_HEIGHT, x, y
        );

        if (layout.visibleTanks > 0) {
            for (int i = this.outputFluidScrollOffset;
                 i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT,
                              this.outputFluidScrollOffset + layout.visibleTanks
                 );
                 i++) {
                int tankY = this.getFluidTankY(FLUID_OUTPUT_AREA_Y, layout.spacing, i, this.outputFluidScrollOffset, y);
                int tankX = layout.startX;
                int buttonX = layout.startX + FLUID_TANK_WIDTH + 1;

                guiGraphics.blit(COMPONENTS_TEXTURE,
                                 tankX, tankY,
                                 FLUID_TANK_U, FLUID_TANK_V,
                                 FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT
                );

                FluidStack fluid = this.menu.getOutputFluidTank(i).getFluid();
                int capacity = this.menu.getOutputFluidTank(i).getCapacity();
                this.renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);

                guiGraphics.blit(COMPONENTS_TEXTURE,
                                 tankX, tankY,
                                 FLUID_TANK_MASK_U, FLUID_TANK_MASK_V,
                                 FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT
                );

                this.renderTankClearButton(guiGraphics, buttonX, tankY, i, false);
            }
        }
    }

    /**
     * 渲染流体槽中的流体（从左往右填充）
     */
    private void renderFluidTank(GuiGraphics guiGraphics, int x, int y, FluidStack fluidStack, int capacity) {
        if (fluidStack.isEmpty() || capacity <= 0) return;

        Fluid fluid = fluidStack.getFluid();
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;

        IClientFluidTypeExtensions fluidTypeExtensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = fluidTypeExtensions.getStillTexture(fluidStack);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        if (sprite == null) return;

        int fluidWidth = (int) ((float) fluidStack.getAmount() / capacity * FLUID_TANK_WIDTH);
        if (fluidWidth <= 0) return;

        int fluidColor = fluidTypeExtensions.getTintColor(fluidStack);

        guiGraphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float r = ((fluidColor >> 16) & 0xFF) / 255.0F;
        float g = ((fluidColor >> 8) & 0xFF) / 255.0F;
        float b = (fluidColor & 0xFF) / 255.0F;
        float a = ((fluidColor >> 24) & 0xFF) / 255.0F;
        if (a == 0) a = 1.0F;

        RenderSystem.setShaderColor(r, g, b, a);

        int remainingWidth = fluidWidth;
        int currentX = x;
        int tileSize = 16;

        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        while (remainingWidth > 0) {
            int drawWidth = Math.min(tileSize, remainingWidth);

            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU((float) drawWidth / tileSize);
            float v1 = sprite.getV1();

            float x1 = currentX;
            float x2 = currentX + drawWidth;
            float y1 = y;
            float y2 = y + FLUID_TANK_HEIGHT;

            bufferBuilder.addVertex(x1, y2, 0).setUv(u0, v1);
            bufferBuilder.addVertex(x2, y2, 0).setUv(u1, v1);
            bufferBuilder.addVertex(x2, y1, 0).setUv(u1, v0);
            bufferBuilder.addVertex(x1, y1, 0).setUv(u0, v0);

            remainingWidth -= drawWidth;
            currentX += drawWidth;
        }

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

    /**
     * 渲染指示灯
     */
    private void renderIndicators(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu == null) return;

        int currentParallel = this.menu.getCurrentParallel();

        if (currentParallel > 1) {
            guiGraphics.blit(COMPONENTS_TEXTURE,
                             x + CATALYST_INDICATOR_X, y + CATALYST_INDICATOR_Y,
                             LIT_INDICATOR_U, LIT_INDICATOR_V,
                             INDICATOR_WIDTH, INDICATOR_HEIGHT
            );
        }

        if (this.menu.hasMold()) {
            guiGraphics.blit(COMPONENTS_TEXTURE,
                             x + MOLD_INDICATOR_X, y + MOLD_INDICATOR_Y,
                             LIT_INDICATOR_U, LIT_INDICATOR_V,
                             INDICATOR_WIDTH, INDICATOR_HEIGHT
            );
        }
    }

    /**
     * 渲染滑块
     */
    private void renderSlider(GuiGraphics guiGraphics, int x, int y) {
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT > visibleInputTanks) {
            float inputScrollRatio = (float) this.inputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks);
            int inputSliderY = (int) (inputScrollRatio * (SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));

            guiGraphics.blit(COMPONENTS_TEXTURE,
                             x + SLIDER_SLOT_X - (SLIDER_WIDTH - SLIDER_SLOT_WIDTH) / 2,
                             y + SLIDER_SLOT_Y + inputSliderY,
                             this.isDraggingInputSlider ? SLIDER_PRESSED_U : SLIDER_DEFAULT_U,
                             this.isDraggingInputSlider ? SLIDER_PRESSED_V : SLIDER_DEFAULT_V,
                             SLIDER_WIDTH, SLIDER_HEIGHT
            );
        }

        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT > visibleOutputTanks) {
            float outputScrollRatio = (float) this.outputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks);
            int outputSliderY = (int) (outputScrollRatio * (OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));

            guiGraphics.blit(COMPONENTS_TEXTURE,
                             x + OUTPUT_SLIDER_SLOT_X - (SLIDER_WIDTH - OUTPUT_SLIDER_SLOT_WIDTH) / 2,
                             y + OUTPUT_SLIDER_SLOT_Y + outputSliderY,
                             this.isDraggingOutputSlider ? SLIDER_PRESSED_U : SLIDER_DEFAULT_U,
                             this.isDraggingOutputSlider ? SLIDER_PRESSED_V : SLIDER_DEFAULT_V,
                             SLIDER_WIDTH, SLIDER_HEIGHT
            );
        }
    }

    /**
     * 渲染能量条悬停提示
     */
    private void renderEnergyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (this.menu == null) return;

        // 检查是否在能量条区域内
        if (mouseX >= x + ENERGY_BAR_X && mouseX < x + ENERGY_BAR_X + ENERGY_BAR_WIDTH &&
                mouseY >= y + ENERGY_BAR_Y && mouseY < y + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("能量: " + this.menu.getEnergy() + " / " + this.menu.getMaxEnergy() + " FE"));
            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    /**
     * 渲染进度条悬停提示
     */
    private void renderProgressTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (this.menu == null) return;

        // 检查是否在进度条区域内（左右两段进度条）
        boolean overLeftProgress = mouseX >= x + PROGRESS_LEFT_X && mouseX < x + PROGRESS_LEFT_X + PROGRESS_LEFT_WIDTH &&
                mouseY >= y + PROGRESS_LEFT_Y && mouseY < y + PROGRESS_LEFT_Y + PROGRESS_LEFT_HEIGHT;
        boolean overRightProgress = mouseX >= x + PROGRESS_RIGHT_X && mouseX < x + PROGRESS_RIGHT_X + PROGRESS_RIGHT_WIDTH &&
                mouseY >= y + PROGRESS_RIGHT_Y && mouseY < y + PROGRESS_RIGHT_Y + PROGRESS_RIGHT_HEIGHT;

        if (overLeftProgress || overRightProgress) {
            List<Component> tooltip = new ArrayList<>();
            int progress = this.menu.getProgress();
            int maxProgress = this.menu.getMaxProgress();

            if (maxProgress > 0) {
                float progressPercent = (float) progress / maxProgress * 100;
                tooltip.add(Component.literal("进度: " + progress + "/" + maxProgress + " (" + String.format("%.1f",
                                                                                                             progressPercent
                ) + "%)"));

                // 获取活跃状态（progress > 0 表示工作中）
                boolean isActive = progress > 0 && progress < maxProgress;
                tooltip.add(Component.literal("状态: " + (isActive ? "工作中" : "空闲"))
                                     .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.GRAY));

                // 添加并行数信息
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("本次并行数: " + this.menu.getCurrentParallel())
                                     .withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.literal("当前最大并行数: " + this.menu.getMaxParallel())
                                     .withStyle(ChatFormatting.BLUE));

                // 添加JEI提示
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("点击查看配方").withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.literal("没有活动进程"));
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("点击查看配方").withStyle(ChatFormatting.AQUA));
            }

            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    /**
     * 渲染问号区域悬停提示（并行数信息）
     */
    private void renderTipsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (this.menu == null) return;

        // 检查是否在问号区域内
        if (mouseX >= x + TIPS_AREA_X && mouseX < x + TIPS_AREA_X + TIPS_AREA_WIDTH &&
                mouseY >= y + TIPS_AREA_Y && mouseY < y + TIPS_AREA_Y + TIPS_AREA_HEIGHT) {

            List<Component> tooltip = new ArrayList<>();

            int currentParallel = this.menu.getCurrentParallel();
            int catalystMaxParallel = this.menu.getCatalystMaxParallel();

            tooltip.add(Component.literal("并行数信息").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("本次并行数: " + currentParallel).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("当前最大并行数: " + catalystMaxParallel).withStyle(ChatFormatting.BLUE));

            // 显示催化剂信息
            if (this.menu.getBlockEntity() != null) {
                AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
                ItemStack catalyst = entity.getItemHandler().getStackInSlot(AdvancedAlloyFurnaceBlockEntity.CATALYST_SLOT);

                if (!catalyst.isEmpty()) {
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("催化剂: " + catalyst.getDisplayName().getString() + " (" + catalystMaxParallel + "倍)").withStyle(ChatFormatting.GREEN));
                }
            }

            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("并行数说明:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("• 消耗和产出乘以并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 处理时间保持不变").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 能量消耗乘以并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• 催化剂为可选项，可提高并行数").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("⚠ 普通催化剂会被消耗").withStyle(ChatFormatting.RED));

            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    /**
     * 处理进度条点击（打开JEI配方）
     */
    private boolean handleProgressClick(double mouseX, double mouseY, int x, int y) {
        // 检查是否在进度条区域内（左右两段进度条）
        boolean overLeftProgress = mouseX >= x + PROGRESS_LEFT_X && mouseX < x + PROGRESS_LEFT_X + PROGRESS_LEFT_WIDTH &&
                mouseY >= y + PROGRESS_LEFT_Y && mouseY < y + PROGRESS_LEFT_Y + PROGRESS_LEFT_HEIGHT;
        boolean overRightProgress = mouseX >= x + PROGRESS_RIGHT_X && mouseX < x + PROGRESS_RIGHT_X + PROGRESS_RIGHT_WIDTH &&
                mouseY >= y + PROGRESS_RIGHT_Y && mouseY < y + PROGRESS_RIGHT_Y + PROGRESS_RIGHT_HEIGHT;

        if (overLeftProgress || overRightProgress) {
            // 检查JEI是否加载，然后通过compat模块打开配方界面
            if (ModList.get().isLoaded("jei")) {
                com.sorrowmist.useless.compat.jei.JEIPlugin.showAdvancedAlloyFurnaceRecipes();
            }
            return true;
        }

        return false;
    }

    /**
     * 流体区域布局信息
     */
    private record FluidAreaLayout(int visibleTanks, int spacing, int startX, int totalWidth) {}
}
