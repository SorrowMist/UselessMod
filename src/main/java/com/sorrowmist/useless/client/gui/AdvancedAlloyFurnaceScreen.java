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
import com.sorrowmist.useless.network.PatternPageChangePacket;
import com.sorrowmist.useless.network.TankClearPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdvancedAlloyFurnaceScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<AdvancedAlloyFurnaceMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/advanced_alloy_furnace_gui.png");
    private static final ResourceLocation COMPONENTS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/advanced_alloy_furnace_zu_jian.png");
    private static final ResourceLocation PATTERN_SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/advanced_alloy_furnace_pattern_solt.png");

    private static final int DISPLAY_WIDTH = 176;
    private static final int DISPLAY_HEIGHT = 260;
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 260;

    private static final int PROGRESS_LEFT_X = 32;
    private static final int PROGRESS_LEFT_Y = 72;
    private static final int PROGRESS_LEFT_WIDTH = 24;
    private static final int PROGRESS_LEFT_HEIGHT = 25;

    private static final int PROGRESS_RIGHT_X = 120;
    private static final int PROGRESS_RIGHT_Y = 90;
    private static final int PROGRESS_RIGHT_WIDTH = 27;
    private static final int PROGRESS_RIGHT_HEIGHT = 21;

    private static final int ENERGY_BAR_X = 58;
    private static final int ENERGY_BAR_Y = 3;
    private static final int ENERGY_BAR_WIDTH = 60;
    private static final int ENERGY_BAR_HEIGHT = 6;

    private static final int ENERGY_MASK_X = 57;
    private static final int ENERGY_MASK_Y = 3;
    private static final int ENERGY_MASK_WIDTH = 62;
    private static final int ENERGY_MASK_HEIGHT = 7;

    private static final int FLUID_INPUT_AREA_X = 74;
    private static final int FLUID_INPUT_AREA_Y = 20;
    private static final int FLUID_INPUT_AREA_WIDTH = 86;
    private static final int FLUID_INPUT_AREA_HEIGHT = 50;

    private static final int FLUID_OUTPUT_AREA_X = 8;
    private static final int FLUID_OUTPUT_AREA_Y = 115;
    private static final int FLUID_OUTPUT_AREA_WIDTH = 86;
    private static final int FLUID_OUTPUT_AREA_HEIGHT = 50;

    private static final int FLUID_TANK_WIDTH = 55;
    private static final int FLUID_TANK_HEIGHT = 17;
    private static final int FLUID_TANK_SPACING = 2;

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

    private static final int CATALYST_INDICATOR_X = 67;
    private static final int CATALYST_INDICATOR_Y = 80;
    private static final int MOLD_INDICATOR_X = 105;
    private static final int MOLD_INDICATOR_Y = 80;
    private static final int INDICATOR_WIDTH = 4;
    private static final int INDICATOR_HEIGHT = 5;

    private static final int TIPS_AREA_X = 80;
    private static final int TIPS_AREA_Y = 87;
    private static final int TIPS_AREA_WIDTH = 16;
    private static final int TIPS_AREA_HEIGHT = 16;

    private static final int TITLE_LABEL_X = 66;
    private static final int TITLE_LABEL_Y = 52;
    private static final int INVENTORY_LABEL_X = 10;
    private static final int INVENTORY_LABEL_Y = 168;

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

    private final boolean[] inputTankClearButtonsPressed = new boolean[AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT];
    private final boolean[] outputTankClearButtonsPressed = new boolean[AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT];

    private int inputFluidScrollOffset = 0;
    private boolean isDraggingInputSlider = false;
    private int draggedInputSliderY = 0;

    private int outputFluidScrollOffset = 0;
    private boolean isDraggingOutputSlider = false;
    private int draggedOutputSliderY = 0;

    private static final int PATTERN_PAGE_BUTTON_WIDTH = 16;
    private static final int PATTERN_PAGE_BUTTON_HEIGHT = 16;

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
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        this.renderPatternPageButtons(guiGraphics);
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        this.renderFluidTankTooltip(guiGraphics, mouseX, mouseY, x, y, true);
        this.renderFluidTankTooltip(guiGraphics, mouseX, mouseY, x, y, false);
        this.renderEnergyTooltip(guiGraphics, mouseX, mouseY, x, y);
        this.renderProgressTooltip(guiGraphics, mouseX, mouseY, x, y);
        this.renderTipsTooltip(guiGraphics, mouseX, mouseY, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                               0x404040, false);
    }

    /**
     * 重写槽位渲染，支持自定义槽位的背景图标
     */
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, @NotNull Slot slot) {
        int x = slot.x;
        int y = slot.y;
        ItemStack stack = slot.getItem();

        // 如果是自定义的PatternSlotItemHandler槽位，检查是否激活
        if (slot instanceof com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler patternSlot) {
            if (!patternSlot.isActive()) {
                return;
            }
            var icon = patternSlot.getIcon();
            if ((patternSlot.renderIconWithItem() || stack.isEmpty()) && patternSlot.isSlotEnabled() && icon != null) {
                icon.getBlitter()
                        .dest(x, y)
                        .opacity(patternSlot.getOpacityOfIcon())
                        .blit(guiGraphics);
            }
        }

        guiGraphics.renderItem(stack, x, y, slot.x + slot.y * this.imageWidth);

        if (slot.index < AdvancedAlloyFurnaceBlockEntity.TOTAL_SLOTS && !stack.isEmpty() && stack.getCount() > 1) {
            this.renderCustomItemCount(guiGraphics, stack, x, y);
        } else {
            guiGraphics.renderItemDecorations(this.font, stack, x, y, null);
        }
    }

    private void renderCustomItemCount(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        int count = stack.getCount();
        if (count <= 1) return;

        String text = formatAeCount(count);

        final float scaleFactor = 0.666f;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        guiGraphics.pose().scale(scaleFactor, scaleFactor, scaleFactor);

        renderSizeLabel(guiGraphics.pose().last().pose(), this.font, x, y, text);

        guiGraphics.pose().popPose();
    }

    private void renderSizeLabel(org.joml.Matrix4f matrix, Font font, float xPos, float yPos, String text) {
        final float scaleFactor = 0.666f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = -1;

        RenderSystem.disableBlend();
        final int X = (int) ((xPos + offset + 16.0f + 2.0f - font.width(text) * scaleFactor) * inverseScaleFactor);
        final int Y = (int) ((yPos + offset + 16.0f - 5.0f * scaleFactor) * inverseScaleFactor);
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        font.drawInBatch(text, X + 1, Y + 1, 0x413f54, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        font.drawInBatch(text, X, Y, 0xffffff, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        buffer.endBatch();
        RenderSystem.enableBlend();
    }

    private String formatAeCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            int k = count / 1000;
            return k + "K";
        } else if (count < 1000000000) {
            int m = count / 1000000;
            return m + "M";
        } else {
            int b = count / 1000000000;
            return b + "B";
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(PATTERN_SLOT_TEXTURE, x - 67, y, 0, 0, 67, 186, 67, 186);

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

        if (this.handlePatternPageClick(mouseX, mouseY, x, y)) return true;
        if (this.handleProgressClick(mouseX, mouseY, x, y)) return true;
        if (this.handleInputSliderClick(mouseX, mouseY, x, y)) return true;
        if (this.handleOutputSliderClick(mouseX, mouseY, x, y)) return true;
        if (this.checkTankClearButtonClick(mouseX, mouseY, x, y, true)) return true;
        if (this.checkTankClearButtonClick(mouseX, mouseY, x, y, false)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingInputSlider) {
            this.updateInputScrollOffset(mouseX, mouseY);
            return true;
        }

        if (this.isDraggingOutputSlider) {
            this.updateOutputScrollOffset(mouseX, mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateInputScrollOffset(double mouseX, double mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int maxScroll = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
        int relativeY = (int) mouseY - (y + SLIDER_SLOT_Y) - this.draggedInputSliderY;
        float scrollRatio = (float) relativeY / maxScroll;
        this.inputFluidScrollOffset = Math.max(0, Math.min(
                AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks,
                (int) (scrollRatio * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks))));
    }

    private void updateOutputScrollOffset(double mouseX, double mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);
        int maxScroll = OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
        int relativeY = (int) mouseY - (y + OUTPUT_SLIDER_SLOT_Y) - this.draggedOutputSliderY;
        float scrollRatio = (float) relativeY / maxScroll;
        this.outputFluidScrollOffset = Math.max(0, Math.min(
                AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks,
                (int) (scrollRatio * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks))));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (this.handleInputAreaScroll(mouseX, mouseY, x, y, scrollY)) return true;
        if (this.handleOutputAreaScroll(mouseX, mouseY, x, y, scrollY)) return true;

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleInputAreaScroll(double mouseX, double mouseY, int x, int y, double scrollY) {
        if (!isInArea(mouseX, mouseY, x + FLUID_INPUT_AREA_X, y + FLUID_INPUT_AREA_Y,
                FLUID_INPUT_AREA_WIDTH, FLUID_INPUT_AREA_HEIGHT)) {
            return false;
        }

        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (scrollY > 0) {
            this.inputFluidScrollOffset = Math.max(0, this.inputFluidScrollOffset - 1);
        } else if (scrollY < 0) {
            this.inputFluidScrollOffset = Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks,
                    this.inputFluidScrollOffset + 1);
        }
        return true;
    }

    private boolean handleOutputAreaScroll(double mouseX, double mouseY, int x, int y, double scrollY) {
        if (!isInArea(mouseX, mouseY, x + FLUID_OUTPUT_AREA_X, y + FLUID_OUTPUT_AREA_Y,
                FLUID_OUTPUT_AREA_WIDTH, FLUID_OUTPUT_AREA_HEIGHT)) {
            return false;
        }

        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        if (scrollY > 0) {
            this.outputFluidScrollOffset = Math.max(0, this.outputFluidScrollOffset - 1);
        } else if (scrollY < 0) {
            this.outputFluidScrollOffset = Math.min(
                    AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks,
                    this.outputFluidScrollOffset + 1);
        }
        return true;
    }

    private static boolean isInArea(double mouseX, double mouseY, int areaX, int areaY, int width, int height) {
        return mouseX >= areaX && mouseX < areaX + width && mouseY >= areaY && mouseY < areaY + height;
    }

    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        int energyStored = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        if (maxEnergy <= 0) return;

        float energyRatio = (float) energyStored / maxEnergy;
        int energyWidth = (int) (ENERGY_BAR_WIDTH * energyRatio);

        if (energyWidth > 0) {
            guiGraphics.blit(COMPONENTS_TEXTURE, x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                    ENERGY_BAR_U, ENERGY_BAR_V, energyWidth, ENERGY_BAR_HEIGHT);
        }

        guiGraphics.blit(COMPONENTS_TEXTURE, x + ENERGY_MASK_X, y + ENERGY_MASK_Y,
                ENERGY_MASK_U, ENERGY_MASK_V, ENERGY_MASK_WIDTH, ENERGY_MASK_HEIGHT);
    }

    private void renderProgressBar(GuiGraphics guiGraphics, int x, int y) {
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        if (maxProgress <= 0 || progress <= 0) return;

        float progressRatio = (float) progress / maxProgress;

        int leftProgressHeight = (int) Math.ceil(PROGRESS_LEFT_HEIGHT * Math.min(1.0f, progressRatio * 2));
        if (leftProgressHeight > 0) {
            guiGraphics.blit(COMPONENTS_TEXTURE, x + PROGRESS_LEFT_X, y + PROGRESS_LEFT_Y,
                    PROGRESS_LEFT_MASK_U, PROGRESS_LEFT_MASK_V, PROGRESS_LEFT_WIDTH, leftProgressHeight);
        }

        if (progressRatio > 0.5f) {
            float secondSegmentRatio = (progressRatio - 0.5f) * 2;
            int rightProgressHeight = (int) Math.ceil(PROGRESS_RIGHT_HEIGHT * secondSegmentRatio);
            if (rightProgressHeight > 0) {
                guiGraphics.blit(COMPONENTS_TEXTURE, x + PROGRESS_RIGHT_X, y + PROGRESS_RIGHT_Y,
                        PROGRESS_RIGHT_MASK_U, PROGRESS_RIGHT_MASK_V, PROGRESS_RIGHT_WIDTH, rightProgressHeight);
            }
        }
    }

    private void renderFluidInputArea(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(FLUID_INPUT_AREA_X, FLUID_INPUT_AREA_Y,
                FLUID_INPUT_AREA_WIDTH, FLUID_INPUT_AREA_HEIGHT, x, y);

        int maxOffset = Math.max(0, AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - layout.visibleTanks);
        this.inputFluidScrollOffset = Math.max(0, Math.min(this.inputFluidScrollOffset, maxOffset));

        if (layout.visibleTanks <= 0) return;

        for (int i = this.inputFluidScrollOffset;
             i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT, this.inputFluidScrollOffset + layout.visibleTanks);
             i++) {
            int tankY = this.getFluidTankY(FLUID_INPUT_AREA_Y, layout.spacing, i, this.inputFluidScrollOffset, y);
            int tankX = layout.startX;
            int buttonX = layout.startX + FLUID_TANK_WIDTH + 1;

            guiGraphics.blit(COMPONENTS_TEXTURE, tankX, tankY,
                    FLUID_TANK_U, FLUID_TANK_V, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);

            FluidStack fluid = this.menu.getInputFluidTank(i).getFluid();
            int capacity = this.menu.getInputFluidTank(i).getCapacity();
            this.renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);

            guiGraphics.blit(COMPONENTS_TEXTURE, tankX, tankY,
                    FLUID_TANK_MASK_U, FLUID_TANK_MASK_V, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);

            this.renderTankClearButton(guiGraphics, buttonX, tankY, i, true);
        }
    }

    private void renderTankClearButton(GuiGraphics guiGraphics, int x, int y, int tankIndex, boolean isInput) {
        boolean isPressed = isInput ?
                this.inputTankClearButtonsPressed[tankIndex] :
                this.outputTankClearButtonsPressed[tankIndex];

        boolean hasFluid = isInput ?
                !this.menu.getInputFluidTank(tankIndex).getFluid().isEmpty() :
                !this.menu.getOutputFluidTank(tankIndex).getFluid().isEmpty();

        int u, v;
        if (hasFluid) {
            u = isPressed ? TANK_CLEAR_BUTTON_LIT_PRESSED_U : TANK_CLEAR_BUTTON_LIT_U;
            v = isPressed ? TANK_CLEAR_BUTTON_LIT_PRESSED_V : TANK_CLEAR_BUTTON_LIT_V;
        } else {
            u = isPressed ? TANK_CLEAR_BUTTON_PRESSED_U : TANK_CLEAR_BUTTON_U;
            v = isPressed ? TANK_CLEAR_BUTTON_PRESSED_V : TANK_CLEAR_BUTTON_V;
        }

        guiGraphics.blit(COMPONENTS_TEXTURE, x, y, u, v, TANK_CLEAR_BUTTON_WIDTH, TANK_CLEAR_BUTTON_HEIGHT);
    }

    private void renderOutputFluidTanks(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(FLUID_OUTPUT_AREA_X, FLUID_OUTPUT_AREA_Y,
                FLUID_OUTPUT_AREA_WIDTH, FLUID_OUTPUT_AREA_HEIGHT, x, y);

        if (layout.visibleTanks <= 0) return;

        for (int i = this.outputFluidScrollOffset;
             i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT, this.outputFluidScrollOffset + layout.visibleTanks);
             i++) {
            int tankY = this.getFluidTankY(FLUID_OUTPUT_AREA_Y, layout.spacing, i, this.outputFluidScrollOffset, y);
            int tankX = layout.startX;
            int buttonX = layout.startX + FLUID_TANK_WIDTH + 1;

            guiGraphics.blit(COMPONENTS_TEXTURE, tankX, tankY,
                    FLUID_TANK_U, FLUID_TANK_V, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);

            FluidStack fluid = this.menu.getOutputFluidTank(i).getFluid();
            int capacity = this.menu.getOutputFluidTank(i).getCapacity();
            this.renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);

            guiGraphics.blit(COMPONENTS_TEXTURE, tankX, tankY,
                    FLUID_TANK_MASK_U, FLUID_TANK_MASK_V, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT);

            this.renderTankClearButton(guiGraphics, buttonX, tankY, i, false);
        }
    }

    private void renderFluidTank(GuiGraphics guiGraphics, int x, int y, FluidStack fluidStack, int capacity) {
        if (fluidStack.isEmpty() || capacity <= 0) return;

        Fluid fluid = fluidStack.getFluid();
        if (fluid == Fluids.EMPTY) return;

        IClientFluidTypeExtensions fluidTypeExtensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = fluidTypeExtensions.getStillTexture(fluidStack);

        TextureAtlasSprite sprite = null;
        if (this.minecraft != null) {
            sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        }
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
            float y2 = y + FLUID_TANK_HEIGHT;

            bufferBuilder.addVertex(x1, y2, 0).setUv(u0, v1);
            bufferBuilder.addVertex(x2, y2, 0).setUv(u1, v1);
            bufferBuilder.addVertex(x2, (float) y, 0).setUv(u1, v0);
            bufferBuilder.addVertex(x1, (float) y, 0).setUv(u0, v0);

            remainingWidth -= drawWidth;
            currentX += drawWidth;
        }

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

    private void renderIndicators(GuiGraphics guiGraphics, int x, int y) {
        int currentParallel = this.menu.getCurrentParallel();

        if (currentParallel > 1) {
            guiGraphics.blit(COMPONENTS_TEXTURE, x + CATALYST_INDICATOR_X, y + CATALYST_INDICATOR_Y,
                    LIT_INDICATOR_U, LIT_INDICATOR_V, INDICATOR_WIDTH, INDICATOR_HEIGHT);
        }

        if (this.menu.hasMold()) {
            guiGraphics.blit(COMPONENTS_TEXTURE, x + MOLD_INDICATOR_X, y + MOLD_INDICATOR_Y,
                    LIT_INDICATOR_U, LIT_INDICATOR_V, INDICATOR_WIDTH, INDICATOR_HEIGHT);
        }
    }

    private void renderSlider(GuiGraphics guiGraphics, int x, int y) {
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        float inputScrollRatio = (float) this.inputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks);
        int inputSliderY = (int) (inputScrollRatio * (SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));

        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + SLIDER_SLOT_X - (SLIDER_WIDTH - SLIDER_SLOT_WIDTH) / 2,
                y + SLIDER_SLOT_Y + inputSliderY,
                this.isDraggingInputSlider ? SLIDER_PRESSED_U : SLIDER_DEFAULT_U,
                this.isDraggingInputSlider ? SLIDER_PRESSED_V : SLIDER_DEFAULT_V,
                SLIDER_WIDTH, SLIDER_HEIGHT);

        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        float outputScrollRatio = (float) this.outputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks);
        int outputSliderY = (int) (outputScrollRatio * (OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));

        guiGraphics.blit(COMPONENTS_TEXTURE,
                x + OUTPUT_SLIDER_SLOT_X - (SLIDER_WIDTH - OUTPUT_SLIDER_SLOT_WIDTH) / 2,
                y + OUTPUT_SLIDER_SLOT_Y + outputSliderY,
                this.isDraggingOutputSlider ? SLIDER_PRESSED_U : SLIDER_DEFAULT_U,
                this.isDraggingOutputSlider ? SLIDER_PRESSED_V : SLIDER_DEFAULT_V,
                SLIDER_WIDTH, SLIDER_HEIGHT);
    }

    private void renderEnergyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY, x + ENERGY_BAR_X, y + ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.energy",
                this.menu.getEnergy(), this.menu.getMaxEnergy()));
        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderProgressTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        boolean overLeftProgress = isInArea(mouseX, mouseY, x + PROGRESS_LEFT_X, y + PROGRESS_LEFT_Y,
                PROGRESS_LEFT_WIDTH, PROGRESS_LEFT_HEIGHT);
        boolean overRightProgress = isInArea(mouseX, mouseY, x + PROGRESS_RIGHT_X, y + PROGRESS_RIGHT_Y,
                PROGRESS_RIGHT_WIDTH, PROGRESS_RIGHT_HEIGHT);

        if (!overLeftProgress && !overRightProgress) return;

        List<Component> tooltip = new ArrayList<>();
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        if (maxProgress > 0) {
            float progressPercent = (float) progress / maxProgress * 100;
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.progress",
                    progress, maxProgress, String.format("%.1f", progressPercent)));

            boolean isActive = progress > 0 && progress < maxProgress;
            String statusKey = isActive
                    ? "gui.useless_mod.advanced_alloy_furnace.status.active"
                    : "gui.useless_mod.advanced_alloy_furnace.status.idle";
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.status",
                            Component.translatable(statusKey))
                    .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.GRAY));

            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.current_parallel",
                            this.menu.getCurrentParallel())
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.max_parallel",
                            this.menu.getCatalystMaxParallel())
                    .withStyle(ChatFormatting.BLUE));
        } else {
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.no_process"));
        }

        int aeActiveTasks = this.menu.getActiveAETaskCount();
        int aeTotalProgress = this.menu.getTotalAEProgress();
        int aeTotalMaxProgress = this.menu.getTotalAEMaxProgress();

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_tasks",
                        aeActiveTasks)
                .withStyle(ChatFormatting.DARK_PURPLE));

        var taskProgressList = this.menu.getAETaskProgressList();
        if (!taskProgressList.isEmpty()) {
            for (var taskProgress : taskProgressList) {
                String productName = taskProgress.getProductName();
                int taskProgressVal = taskProgress.getProgress();
                int taskMaxProgress = taskProgress.getMaxProgress();
                int totalOutputCount = taskProgress.getTotalOutputCount();

                float taskProgressPercent = taskMaxProgress > 0 ? (float) taskProgressVal / taskMaxProgress * 100 : 0;

                Component nameComponent = Component.translatable(productName);

                tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_progress",
                                nameComponent, totalOutputCount, taskProgressVal, taskMaxProgress, String.format("%.1f", taskProgressPercent))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        } else if (aeTotalMaxProgress > 0) {
            float aeProgressPercent = (float) aeTotalProgress / aeTotalMaxProgress * 100;
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_progress",
                            aeTotalProgress, aeTotalMaxProgress, String.format("%.1f", aeProgressPercent))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.view_recipes")
                .withStyle(ChatFormatting.AQUA));

        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderTipsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY, x + TIPS_AREA_X, y + TIPS_AREA_Y, TIPS_AREA_WIDTH, TIPS_AREA_HEIGHT)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();

        int currentParallel = this.menu.getCurrentParallel();
        int catalystMaxParallel = this.menu.getCatalystMaxParallel();

        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_info")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.current_parallel", currentParallel)
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.max_parallel", catalystMaxParallel)
                .withStyle(ChatFormatting.BLUE));

        if (this.menu.getBlockEntity() != null) {
            AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
            ItemStack catalyst = entity.getItemHandler().getStackInSlot(AdvancedAlloyFurnaceBlockEntity.CATALYST_SLOT);

            if (!catalyst.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.catalyst",
                                catalyst.getDisplayName().getString(), catalystMaxParallel)
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.title")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.3")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.4")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.catalyst_warning")
                .withStyle(ChatFormatting.RED));

        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderFluidTankTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y,
                                        boolean isInput) {
        int areaX = isInput ? FLUID_INPUT_AREA_X : FLUID_OUTPUT_AREA_X;
        int areaY = isInput ? FLUID_INPUT_AREA_Y : FLUID_OUTPUT_AREA_Y;
        int areaWidth = isInput ? FLUID_INPUT_AREA_WIDTH : FLUID_OUTPUT_AREA_WIDTH;
        int areaHeight = isInput ? FLUID_INPUT_AREA_HEIGHT : FLUID_OUTPUT_AREA_HEIGHT;
        int scrollOffset = isInput ? this.inputFluidScrollOffset : this.outputFluidScrollOffset;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(areaX, areaY, areaWidth, areaHeight, x, y);

        if (layout.visibleTanks <= 0) return;

        for (int i = scrollOffset; i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT,
                scrollOffset + layout.visibleTanks); i++) {
            int tankY = this.getFluidTankY(areaY, layout.spacing, i, scrollOffset, y);
            int tankX = layout.startX;

            if (!isInArea(mouseX, mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) continue;

            FluidStack fluid = isInput ?
                    this.menu.getInputFluidTank(i).getFluid() :
                    this.menu.getOutputFluidTank(i).getFluid();
            int capacity = isInput ?
                    this.menu.getInputFluidTank(i).getCapacity() :
                    this.menu.getOutputFluidTank(i).getCapacity();

            if (!fluid.isEmpty()) {
                Component fluidName = fluid.getHoverName();
                Component amountText = Component.translatable("gui.useless_mod.advanced_alloy_furnace.fluid_amount",
                        String.format("%,d", fluid.getAmount()),
                        String.format("%,d", capacity));

                guiGraphics.renderTooltip(this.font, List.of(fluidName, amountText),
                        Optional.empty(), mouseX, mouseY);
            }
            break;
        }
    }

    private boolean handleProgressClick(double mouseX, double mouseY, int x, int y) {
        boolean overLeftProgress = isInArea(mouseX, mouseY, x + PROGRESS_LEFT_X, y + PROGRESS_LEFT_Y,
                PROGRESS_LEFT_WIDTH, PROGRESS_LEFT_HEIGHT);
        boolean overRightProgress = isInArea(mouseX, mouseY, x + PROGRESS_RIGHT_X, y + PROGRESS_RIGHT_Y,
                PROGRESS_RIGHT_WIDTH, PROGRESS_RIGHT_HEIGHT);

        if (!overLeftProgress && !overRightProgress) return false;

        if (ModList.get().isLoaded("jei")) {
            com.sorrowmist.useless.compat.jei.JEIPlugin.showAdvancedAlloyFurnaceRecipes();
        }
        return true;
    }

    private boolean handleInputSliderClick(double mouseX, double mouseY, int x, int y) {
        int visibleInputTanks = FLUID_INPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        float inputScrollRatio = (float) this.inputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks);
        int inputSliderY = (int) (inputScrollRatio * (SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));
        int sliderX = x + SLIDER_SLOT_X - (SLIDER_WIDTH - SLIDER_SLOT_WIDTH) / 2;
        int sliderY = y + SLIDER_SLOT_Y + inputSliderY;

        if (isInArea(mouseX, mouseY, sliderX, sliderY, SLIDER_WIDTH, SLIDER_HEIGHT)) {
            this.isDraggingInputSlider = true;
            this.draggedInputSliderY = (int) mouseY - sliderY;
            return true;
        }

        if (isInArea(mouseX, mouseY, x + SLIDER_SLOT_X, y + SLIDER_SLOT_Y, SLIDER_SLOT_WIDTH, SLIDER_SLOT_HEIGHT)) {
            int clickY = (int) mouseY - (y + SLIDER_SLOT_Y) - SLIDER_HEIGHT / 2;
            int maxScroll = SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT;
            this.inputFluidScrollOffset = Math.max(0, Math.min(
                    AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks,
                    (int) ((float) clickY / maxScroll * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleInputTanks))));
            return true;
        }

        return false;
    }

    private boolean handleOutputSliderClick(double mouseX, double mouseY, int x, int y) {
        int visibleOutputTanks = FLUID_OUTPUT_AREA_HEIGHT / (FLUID_TANK_HEIGHT + FLUID_TANK_SPACING);

        float outputScrollRatio = (float) this.outputFluidScrollOffset / (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks);
        int outputSliderY = (int) (outputScrollRatio * (OUTPUT_SLIDER_SLOT_HEIGHT - SLIDER_HEIGHT));
        int sliderX = x + OUTPUT_SLIDER_SLOT_X - (SLIDER_WIDTH - OUTPUT_SLIDER_SLOT_WIDTH) / 2;
        int sliderY = y + OUTPUT_SLIDER_SLOT_Y + outputSliderY;

        if (isInArea(mouseX, mouseY, sliderX, sliderY, SLIDER_WIDTH, SLIDER_HEIGHT)) {
            this.isDraggingOutputSlider = true;
            this.draggedOutputSliderY = (int) mouseY - sliderY;
            return true;
        }

        if (isInArea(mouseX, mouseY, x + OUTPUT_SLIDER_SLOT_X, y + OUTPUT_SLIDER_SLOT_Y,
                OUTPUT_SLIDER_SLOT_WIDTH, OUTPUT_SLIDER_SLOT_HEIGHT)) {
            int clickY = (int) mouseY - (y + OUTPUT_SLIDER_SLOT_Y) - SLIDER_HEIGHT / 2;
            int maxScroll = OUTPUT_SLIDER_SLOT_WIDTH - SLIDER_HEIGHT;
            this.outputFluidScrollOffset = Math.max(0, Math.min(
                    AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks,
                    (int) ((float) clickY / maxScroll * (AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT - visibleOutputTanks))));
            return true;
        }

        return false;
    }

    private boolean checkTankClearButtonClick(double mouseX, double mouseY, int x, int y, boolean isInput) {
        int areaX = isInput ? FLUID_INPUT_AREA_X : FLUID_OUTPUT_AREA_X;
        int areaY = isInput ? FLUID_INPUT_AREA_Y : FLUID_OUTPUT_AREA_Y;
        int areaWidth = isInput ? FLUID_INPUT_AREA_WIDTH : FLUID_OUTPUT_AREA_WIDTH;
        int areaHeight = isInput ? FLUID_INPUT_AREA_HEIGHT : FLUID_OUTPUT_AREA_HEIGHT;
        int scrollOffset = isInput ? this.inputFluidScrollOffset : this.outputFluidScrollOffset;
        boolean[] buttonsPressed = isInput ? this.inputTankClearButtonsPressed : this.outputTankClearButtonsPressed;

        FluidAreaLayout layout = this.calculateFluidAreaLayout(areaX, areaY, areaWidth, areaHeight, x, y);

        if (layout.visibleTanks <= 0) return false;

        for (int i = scrollOffset; i < Math.min(AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT,
                scrollOffset + layout.visibleTanks); i++) {
            int tankY = this.getFluidTankY(areaY, layout.spacing, i, scrollOffset, y);
            int buttonX = layout.startX + FLUID_TANK_WIDTH + 1;

            if (isInArea(mouseX, mouseY, buttonX, tankY, TANK_CLEAR_BUTTON_WIDTH, TANK_CLEAR_BUTTON_HEIGHT)) {
                buttonsPressed[i] = true;

                FluidStack fluid = isInput ?
                        this.menu.getInputFluidTank(i).getFluid() :
                        this.menu.getOutputFluidTank(i).getFluid();

                if (!fluid.isEmpty()) {
                    PacketDistributor.sendToServer(new TankClearPacket(
                            this.menu.getBlockEntity().getBlockPos(), i, isInput));
                }
                return true;
            }
        }
        return false;
    }

    private FluidAreaLayout calculateFluidAreaLayout(int areaX, int areaY, int areaWidth, int areaHeight, int x, int y) {
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

    private int getFluidTankY(int areaY, int spacing, int tankIndex, int scrollOffset, int y) {
        return y + areaY + spacing + (tankIndex - scrollOffset) * (FLUID_TANK_HEIGHT + spacing);
    }

    private record FluidAreaLayout(int visibleTanks, int spacing, int startX, int totalWidth) {}

    private boolean handlePatternPageClick(double mouseX, double mouseY, int x, int y) {
        int currentPage = this.menu.getPatternPage();
        int maxPage = this.menu.getMaxPatternPage();

        int prevButtonX = x - 67 + 2;
        int prevButtonY = y + 168;
        int nextButtonX = x - 67 + 46;
        int nextButtonY = y + 168;

        if (currentPage > 0 && isInArea(mouseX, mouseY, prevButtonX, prevButtonY, PATTERN_PAGE_BUTTON_WIDTH, PATTERN_PAGE_BUTTON_HEIGHT)) {
            int newPage = currentPage - 1;
            this.menu.setPatternPage(newPage);
            this.updatePatternSlotVisibility();
            PacketDistributor.sendToServer(new PatternPageChangePacket(newPage));
            return true;
        }

        if (currentPage < maxPage && isInArea(mouseX, mouseY, nextButtonX, nextButtonY, PATTERN_PAGE_BUTTON_WIDTH, PATTERN_PAGE_BUTTON_HEIGHT)) {
            int newPage = currentPage + 1;
            this.menu.setPatternPage(newPage);
            this.updatePatternSlotVisibility();
            PacketDistributor.sendToServer(new PatternPageChangePacket(newPage));
            return true;
        }

        return false;
    }

    private void updatePatternSlotVisibility() {
        int currentPage = this.menu.getPatternPage();
        int slotsPerPage = this.menu.getPatternSlotsPerPage();
        int base = currentPage * slotsPerPage;
        int end = Math.min(base + slotsPerPage, AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_COUNT);

        for (Slot slot : this.menu.slots) {
            if (slot instanceof com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler patternSlot) {
                int slotIndex = slot.getSlotIndex();
                int relativeIndex = slotIndex - AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_START;
                boolean isActive = relativeIndex >= base && relativeIndex < end;
                patternSlot.setActive(isActive);
            }
        }
    }

    private void renderPatternPageButtons(GuiGraphics guiGraphics) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        int currentPage = this.menu.getPatternPage();
        int maxPage = this.menu.getMaxPatternPage();

        if (maxPage <= 0) return;

        int prevButtonX = x - 67 + 2;
        int prevButtonY = y + 168;
        int nextButtonX = x - 67 + 46;
        int nextButtonY = y + 168;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (currentPage > 0) {
            appeng.client.gui.Icon.ARROW_LEFT.getBlitter()
                    .dest(prevButtonX, prevButtonY, PATTERN_PAGE_BUTTON_WIDTH, PATTERN_PAGE_BUTTON_HEIGHT)
                    .blit(guiGraphics);
        }

        if (currentPage < maxPage) {
            appeng.client.gui.Icon.ARROW_RIGHT.getBlitter()
                    .dest(nextButtonX, nextButtonY, PATTERN_PAGE_BUTTON_WIDTH, PATTERN_PAGE_BUTTON_HEIGHT)
                    .blit(guiGraphics);
        }

        String pageText = (currentPage + 1) + "/" + (maxPage + 1);
        int textX = x - 67 + 22;
        int textY = y + 170;
        guiGraphics.drawString(this.font, pageText, textX, textY, 0xFFFFFF, false);
    }
}