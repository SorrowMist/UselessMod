package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.network.AutoIOChangePacket;
import com.sorrowmist.useless.network.AECancelPacket;
import com.sorrowmist.useless.network.AEReturnOutputTogglePacket;
import com.sorrowmist.useless.network.FaceModeChangePacket;
import com.sorrowmist.useless.network.PatternPageChangePacket;
import com.sorrowmist.useless.network.RedstoneControlPacket;
import com.sorrowmist.useless.network.TankClearPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdvancedAlloyFurnaceScreen extends AbstractContainerScreen<AdvancedAlloyFurnaceMenu> {

    // UI背景纹理（从locate_picture.png抠取）
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "textures/gui/locate_picture.png");

    // UI尺寸：243*260
    private static final int DISPLAY_WIDTH = 243;
    private static final int DISPLAY_HEIGHT = 260;
    // 纹理实际尺寸：256*480
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 480;

    // 槽位尺寸
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_SPACING = 2;

    // 物品输入槽位置：起点75,16，横向9个
    private static final int INPUT_SLOTS_X = 75;
    private static final int INPUT_SLOTS_Y = 16;

    // 物品输出槽位置：起点75,93，横向9个
    private static final int OUTPUT_SLOTS_X = 75;
    private static final int OUTPUT_SLOTS_Y = 93;

    // 流体输入槽位置：起点75,36，大小16*31，横向9个
    private static final int FLUID_INPUT_X = 75;
    private static final int FLUID_INPUT_Y = 36;
    private static final int FLUID_TANK_WIDTH = 16;
    private static final int FLUID_TANK_HEIGHT = 31;

    // 流体输出槽位置：起点75,113，大小16*31，横向9个
    private static final int FLUID_OUTPUT_X = 75;
    private static final int FLUID_OUTPUT_Y = 113;

    // 样板槽位置：起点8,5，横向3个纵向9个
    private static final int PATTERN_SLOTS_X = 8;
    private static final int PATTERN_SLOTS_Y = 5;
    private static final int PATTERN_SLOTS_COLS = 3;
    private static final int PATTERN_SLOTS_ROWS = 9;

    // 翻页按钮位置
    private static final int PREV_PAGE_BUTTON_X = 11;
    private static final int PREV_PAGE_BUTTON_Y = 172;
    private static final int PREV_PAGE_BUTTON_WIDTH = 4;
    private static final int PREV_PAGE_BUTTON_HEIGHT = 7;

    private static final int NEXT_PAGE_BUTTON_X = 53;
    private static final int NEXT_PAGE_BUTTON_Y = 172;
    private static final int NEXT_PAGE_BUTTON_WIDTH = 4;
    private static final int NEXT_PAGE_BUTTON_HEIGHT = 7;

    // 翻页按钮纹理坐标（按下状态）
    private static final int PREV_PAGE_PRESSED_U = 103;
    private static final int PREV_PAGE_PRESSED_V = 402;
    private static final int NEXT_PAGE_PRESSED_U = 145;
    private static final int NEXT_PAGE_PRESSED_V = 402;

    // 催化剂槽位置：起点78,154
    private static final int CATALYST_SLOT_X = 78;
    private static final int CATALYST_SLOT_Y = 154;

    // 模具槽位置：起点128,154
    private static final int MOLD_SLOT_X = 128;
    private static final int MOLD_SLOT_Y = 154;

    // 催化剂指示灯位置：起点97,160，大小4*4
    private static final int CATALYST_INDICATOR_X = 97;
    private static final int CATALYST_INDICATOR_Y = 160;
    private static final int INDICATOR_SIZE = 4;

    // 模具指示灯位置：起点121,160，大小4*4
    private static final int MOLD_INDICATOR_X = 121;
    private static final int MOLD_INDICATOR_Y = 160;

    // 指示灯亮起纹理坐标：起点2,401
    private static final int INDICATOR_LIT_U = 2;
    private static final int INDICATOR_LIT_V = 401;

    // 进度条位置：起点78,71，大小154*18
    private static final int PROGRESS_BAR_X = 78;
    private static final int PROGRESS_BAR_Y = 71;
    private static final int PROGRESS_BAR_WIDTH = 154;
    private static final int PROGRESS_BAR_HEIGHT = 18;

    // 进度条纹理坐标：起点1,373
    private static final int PROGRESS_BAR_U = 1;
    private static final int PROGRESS_BAR_V = 373;

    // 能量槽位置：起点153,169，大小84*4
    private static final int ENERGY_BAR_X = 153;
    private static final int ENERGY_BAR_Y = 169;
    private static final int ENERGY_BAR_WIDTH = 84;
    private static final int ENERGY_BAR_HEIGHT = 4;

    // 能量槽纹理坐标：起点12,402
    private static final int ENERGY_BAR_U = 12;
    private static final int ENERGY_BAR_V = 402;

    // 帮助提示区域位置：起点103,154，大小16*16
    private static final int TIPS_AREA_X = 103;
    private static final int TIPS_AREA_Y = 154;
    private static final int TIPS_AREA_SIZE = 16;

    // ==================== 面模式控制区域 ====================
    // 覆盖图尺寸
    private static final int OVERLAY_WIDTH = 11;
    private static final int OVERLAY_HEIGHT = 12;
    private static final int OVERLAY_V = 265;

    // 六个面的控制区域坐标（x, y, 对应的FurnaceFace）
    // 顶部：26,197
    private static final int FACE_TOP_X = 26;
    private static final int FACE_TOP_Y = 197;

    // 左侧：14,210
    private static final int FACE_LEFT_X = 14;
    private static final int FACE_LEFT_Y = 210;

    // 前面：26,210
    private static final int FACE_FRONT_X = 26;
    private static final int FACE_FRONT_Y = 210;

    // 右侧：38,210
    private static final int FACE_RIGHT_X = 38;
    private static final int FACE_RIGHT_Y = 210;

    // 下部：26,223
    private static final int FACE_BOTTOM_X = 26;
    private static final int FACE_BOTTOM_Y = 223;

    // 后部：38,223
    private static final int FACE_BACK_X = 38;
    private static final int FACE_BACK_Y = 223;

    // 面模式区域定义（按FurnaceFace顺序存储x, y）
    private static final int[] FACE_XS = {
            FACE_TOP_X,   // TOP
            FACE_LEFT_X,  // LEFT
            FACE_FRONT_X, // FRONT
            FACE_RIGHT_X, // RIGHT
            FACE_BOTTOM_X,// BOTTOM
            FACE_BACK_X,  // BACK
    };
    private static final int[] FACE_YS = {
            FACE_TOP_Y,
            FACE_LEFT_Y,
            FACE_FRONT_Y,
            FACE_RIGHT_Y,
            FACE_BOTTOM_Y,
            FACE_BACK_Y,
    };

    // ==================== 自动输入输出配置区域 ====================
    // 自动输出：10,237，15*12
    private static final int AUTO_OUTPUT_X = 10;
    private static final int AUTO_OUTPUT_Y = 237;
    private static final int AUTO_OUTPUT_WIDTH = 15;
    private static final int AUTO_OUTPUT_HEIGHT = 12;

    // 自动输入：43,237，15*12
    private static final int AUTO_INPUT_X = 43;
    private static final int AUTO_INPUT_Y = 237;
    private static final int AUTO_INPUT_WIDTH = 15;
    private static final int AUTO_INPUT_HEIGHT = 12;

    // 自动输入输出覆盖图在纹理中的坐标（V=265）
    private static final int AUTO_OUTPUT_OVERLAY_U = 107;
    private static final int AUTO_INPUT_OVERLAY_U = 123;
    private static final int AUTO_IO_OVERLAY_V = 265;

    // ==================== 红石控制区域 ====================
    // 红石控制：152,151，14*15
    private static final int REDSTONE_CONTROL_X = 152;
    private static final int REDSTONE_CONTROL_Y = 151;
    private static final int REDSTONE_CONTROL_WIDTH = AlloyFurnaceControlIcons.WIDTH;
    private static final int REDSTONE_CONTROL_HEIGHT = AlloyFurnaceControlIcons.HEIGHT;

    // ==================== AE 任务控制区域 ====================
    // 取消 AE 合成任务按钮：169,151，14*15，常态[140,283]，按下[156,283]
    private static final int CANCEL_AE_X = 169;
    private static final int CANCEL_AE_Y = 151;
    private static final int CANCEL_AE_WIDTH = AlloyFurnaceControlIcons.WIDTH;
    private static final int CANCEL_AE_HEIGHT = AlloyFurnaceControlIcons.HEIGHT;

    // 产物回AE切换按钮：186,151，14*15，关闭[140,301]，开启[156,301]
    private static final int AE_RETURN_X = 186;
    private static final int AE_RETURN_Y = 151;
    private static final int AE_RETURN_WIDTH = 14;
    private static final int AE_RETURN_HEIGHT = 15;
    private static final int AE_RETURN_OFF_U = 140;
    private static final int AE_RETURN_ON_U = 156;
    private static final int AE_RETURN_V = 301;

    // 翻页按钮状态
    private boolean prevPageButtonPressed = false;
    private boolean nextPageButtonPressed = false;
    // 取消AE任务按钮状态
    private boolean cancelAePressed = false;

    public AdvancedAlloyFurnaceScreen(AdvancedAlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = DISPLAY_WIDTH;
        this.imageHeight = DISPLAY_HEIGHT;
        // 不显示标题和玩家背包标签
        this.titleLabelX = 0;
        this.titleLabelY = 0;
        this.inventoryLabelX = 0;
        this.inventoryLabelY = 0;
    }

    /**
     * 渲染自动输入输出配置的覆盖图。
     */
    private void renderAutoIOOverlays(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;

        // 自动输出
        if (this.menu.getBlockEntity().isAutoOutputEnabled()) {
            guiGraphics.blit(TEXTURE,
                    x + AUTO_OUTPUT_X, y + AUTO_OUTPUT_Y,
                    AUTO_OUTPUT_OVERLAY_U, AUTO_IO_OVERLAY_V,
                    AUTO_OUTPUT_WIDTH, AUTO_OUTPUT_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        // 自动输入
        if (this.menu.getBlockEntity().isAutoInputEnabled()) {
            guiGraphics.blit(TEXTURE,
                    x + AUTO_INPUT_X, y + AUTO_INPUT_Y,
                    AUTO_INPUT_OVERLAY_U, AUTO_IO_OVERLAY_V,
                    AUTO_INPUT_WIDTH, AUTO_INPUT_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    /**
     * 渲染红石控制模式的覆盖图。
     */
    private void renderRedstoneControlOverlay(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;
        RedstoneControlMode mode = this.menu.getBlockEntity().getRedstoneControlMode();
        if (!mode.hasOverlay()) return;
        AlloyFurnaceControlIcons.drawRedstone(
                guiGraphics, x + REDSTONE_CONTROL_X, y + REDSTONE_CONTROL_Y, mode);
    }

    /**
     * 渲染取消AE任务按钮。
     */
    private void renderCancelAeButton(GuiGraphics guiGraphics, int x, int y) {
        AlloyFurnaceControlIcons.drawCancel(
                guiGraphics, x + CANCEL_AE_X, y + CANCEL_AE_Y, cancelAePressed);
    }

    /**
     * 渲染产物回AE切换按钮。
     */
    private void renderAeReturnButton(GuiGraphics guiGraphics, int x, int y) {
        boolean isOn = this.menu.isReturnOutputToAe();
        int u = isOn ? AE_RETURN_ON_U : AE_RETURN_OFF_U;
        guiGraphics.blit(TEXTURE,
                x + AE_RETURN_X, y + AE_RETURN_Y,
                u, AE_RETURN_V,
                AE_RETURN_WIDTH, AE_RETURN_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
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
        this.renderFaceModeTooltip(guiGraphics, mouseX, mouseY, x, y);
        this.renderAutoIOTooltip(guiGraphics, mouseX, mouseY, x, y);
        this.renderRedstoneControlTooltip(guiGraphics, mouseX, mouseY, x, y);
        this.renderCancelAeTooltip(guiGraphics, mouseX, mouseY, x, y);
        this.renderAeReturnTooltip(guiGraphics, mouseX, mouseY, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 不渲染任何标签
    }

    /**
     * 重写槽位渲染，支持自定义槽位的背景图标
     */
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, @NotNull Slot slot) {
        int x = slot.x;
        int y = slot.y;
        ItemStack stack = slot.getItem();

        boolean isPatternSlot = slot instanceof PatternSlotItemHandler;
        // 如果是自定义的PatternSlotItemHandler槽位，检查是否激活
        if (isPatternSlot) {
            PatternSlotItemHandler patternSlot = (PatternSlotItemHandler) slot;
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

        GenericStack patternOutput = isPatternSlot ? getPatternOutput(stack) : null;
        if (patternOutput != null && PatternSlotRenderer.renderPattern(
                guiGraphics, this.font, stack, x, y, slot.x + slot.y * this.imageWidth,
                Minecraft.getInstance().level)) {
            return;
        }

        guiGraphics.renderItem(stack, x, y, slot.x + slot.y * this.imageWidth);

        if (slot.index < AdvancedAlloyFurnaceLayout.TOTAL_SLOTS && !stack.isEmpty() && stack.getCount() > 1) {
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
            int g = count / 1000000000;
            return g + "G";
        }
    }

    /**
     * 解析编码样板，返回主产物（用于在样板槽上直接显示产物图标）
     */
    @Nullable
    private GenericStack getPatternOutput(ItemStack stack) {
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return null;
        }
        var details = PatternDetailsHelper.decodePattern(stack, Minecraft.getInstance().level);
        if (details == null) {
            return null;
        }
        var outputs = details.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 渲染UI背景（从locate_picture.png抠取0,0到242,259区域）
        guiGraphics.blit(TEXTURE, x, y, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // 渲染进度条
        this.renderProgressBar(guiGraphics, x, y);

        // 渲染能量槽
        this.renderEnergyBar(guiGraphics, x, y);

        // 渲染流体槽
        this.renderFluidTanks(guiGraphics, x, y, true);
        this.renderFluidTanks(guiGraphics, x, y, false);

        // 渲染指示灯
        this.renderIndicators(guiGraphics, x, y);

        // 渲染翻页按钮
        this.renderPageButtons(guiGraphics, x, y);

        // 渲染面模式覆盖图
        this.renderFaceModeOverlays(guiGraphics, x, y);

        // 渲染自动输入输出覆盖图
        this.renderAutoIOOverlays(guiGraphics, x, y);

        // 渲染红石控制覆盖图
        this.renderRedstoneControlOverlay(guiGraphics, x, y);

        // 渲染取消AE任务按钮
        this.renderCancelAeButton(guiGraphics, x, y);

        // 渲染产物回AE切换按钮
        this.renderAeReturnButton(guiGraphics, x, y);
    }

    /**
     * 渲染进度条
     * 根据合成进度从上到下渲染
     */
    private void renderProgressBar(GuiGraphics guiGraphics, int x, int y) {
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        if (maxProgress <= 0 || progress <= 0) return;

        float progressRatio = (float) progress / maxProgress;
        int progressHeight = (int) (PROGRESS_BAR_HEIGHT * progressRatio);

        if (progressHeight > 0) {
            // 从上到下渲染进度条
            guiGraphics.blit(TEXTURE,
                    x + PROGRESS_BAR_X,
                    y + PROGRESS_BAR_Y,
                    PROGRESS_BAR_U,
                    PROGRESS_BAR_V,
                    PROGRESS_BAR_WIDTH,
                    progressHeight,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }
    }

    /**
     * 渲染能量槽
     * 根据电量百分比从左到右渲染
     */
    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        long energyStored = this.menu.getEnergy();
        long maxEnergy = this.menu.getMaxEnergy();

        if (maxEnergy <= 0) return;

        double energyRatio = (double) energyStored / maxEnergy;
        int energyWidth = (int) (ENERGY_BAR_WIDTH * energyRatio);

        if (energyWidth > 0) {
            // 从左到右渲染能量槽
            guiGraphics.blit(TEXTURE,
                    x + ENERGY_BAR_X,
                    y + ENERGY_BAR_Y,
                    ENERGY_BAR_U,
                    ENERGY_BAR_V,
                    energyWidth,
                    ENERGY_BAR_HEIGHT,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }
    }

    /**
     * 渲染流体槽
     */
    private void renderFluidTanks(GuiGraphics guiGraphics, int x, int y, boolean isInput) {
        if (this.menu.getBlockEntity() == null) return;

        int startX = isInput ? FLUID_INPUT_X : FLUID_OUTPUT_X;
        int startY = isInput ? FLUID_INPUT_Y : FLUID_OUTPUT_Y;

        for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
            int tankX = x + startX + i * (FLUID_TANK_WIDTH + SLOT_SPACING);
            int tankY = y + startY;

            FluidStack fluid = isInput ?
                    this.menu.getInputFluidTank(i).getFluid() :
                    this.menu.getOutputFluidTank(i).getFluid();
            int capacity = isInput ?
                    this.menu.getInputFluidTank(i).getCapacity() :
                    this.menu.getOutputFluidTank(i).getCapacity();

            this.renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);
        }
    }

    /**
     * 渲染单个流体槽
     */
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

        int fluidHeight = (int) ((float) fluidStack.getAmount() / capacity * FLUID_TANK_HEIGHT);
        if (fluidHeight <= 0) return;

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

        int remainingHeight = fluidHeight;
        int currentY = y + FLUID_TANK_HEIGHT - fluidHeight; // 从底部开始填充
        int tileSize = 16;

        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        while (remainingHeight > 0) {
            int drawHeight = Math.min(tileSize, remainingHeight);

            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV((float) drawHeight / tileSize);

            float x1 = x;
            float x2 = x + FLUID_TANK_WIDTH;
            float y1 = currentY;
            float y2 = currentY + drawHeight;

            bufferBuilder.addVertex(x1, y2, 0).setUv(u0, v1);
            bufferBuilder.addVertex(x2, y2, 0).setUv(u1, v1);
            bufferBuilder.addVertex(x2, y1, 0).setUv(u1, v0);
            bufferBuilder.addVertex(x1, y1, 0).setUv(u0, v0);

            remainingHeight -= drawHeight;
            currentY += drawHeight;
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
        int currentParallel = this.menu.getCurrentParallel();

        // 催化剂指示灯：当并行数大于1时亮起
        if (currentParallel > 1) {
            guiGraphics.blit(TEXTURE,
                    x + CATALYST_INDICATOR_X,
                    y + CATALYST_INDICATOR_Y,
                    INDICATOR_LIT_U,
                    INDICATOR_LIT_V,
                    INDICATOR_SIZE,
                    INDICATOR_SIZE,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }

        // 模具指示灯：当有模具时亮起
        if (this.menu.hasMold()) {
            guiGraphics.blit(TEXTURE,
                    x + MOLD_INDICATOR_X,
                    y + MOLD_INDICATOR_Y,
                    INDICATOR_LIT_U,
                    INDICATOR_LIT_V,
                    INDICATOR_SIZE,
                    INDICATOR_SIZE,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }
    }

    /**
     * 渲染翻页按钮和页码提示
     */
    private void renderPageButtons(GuiGraphics guiGraphics, int x, int y) {
        int currentPage = this.menu.getPatternPage();
        int maxPage = this.menu.getMaxPatternPage();

        // 渲染左翻页按钮（按下状态时覆盖纹理）
        if (this.prevPageButtonPressed && currentPage > 0) {
            guiGraphics.blit(TEXTURE,
                    x + PREV_PAGE_BUTTON_X,
                    y + PREV_PAGE_BUTTON_Y,
                    PREV_PAGE_PRESSED_U,
                    PREV_PAGE_PRESSED_V,
                    PREV_PAGE_BUTTON_WIDTH,
                    PREV_PAGE_BUTTON_HEIGHT,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }

        // 渲染右翻页按钮（按下状态时覆盖纹理）
        if (this.nextPageButtonPressed && currentPage < maxPage) {
            guiGraphics.blit(TEXTURE,
                    x + NEXT_PAGE_BUTTON_X,
                    y + NEXT_PAGE_BUTTON_Y,
                    NEXT_PAGE_PRESSED_U,
                    NEXT_PAGE_PRESSED_V,
                    NEXT_PAGE_BUTTON_WIDTH,
                    NEXT_PAGE_BUTTON_HEIGHT,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }

        // 渲染页码提示文字（在左右翻页键之间居中显示）
        String pageText = (currentPage + 1) + "/" + (maxPage + 1);
        int textWidth = this.font.width(pageText);
        int centerX = x + PREV_PAGE_BUTTON_X + PREV_PAGE_BUTTON_WIDTH +
                (NEXT_PAGE_BUTTON_X - PREV_PAGE_BUTTON_X - PREV_PAGE_BUTTON_WIDTH) / 2;
        int textX = centerX - textWidth / 2;
        int textY = y + PREV_PAGE_BUTTON_Y + 1;
        guiGraphics.drawString(this.font, pageText, textX, textY, 0xFFFFFF, false);
    }

    /**
     * 渲染六个面的模式覆盖图。
     * 仅当模式不是DISABLED时才渲染对应的覆盖纹理。
     * <p>
     * 纹理坐标：U从FurnaceFaceMode.getOverlayU()取，V=265，尺寸11*12。
     */
    private void renderFaceModeOverlays(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;
        FurnaceFaceMode[] modes = this.menu.getBlockEntity().getFaceModes();
        for (int faceIdx = 0; faceIdx < FurnaceFace.COUNT; faceIdx++) {
            FurnaceFaceMode mode = modes[faceIdx];
            if (!mode.hasOverlay()) continue;
            int u = mode.getOverlayU();
            int faceX = x + FACE_XS[faceIdx];
            int faceY = y + FACE_YS[faceIdx];
            guiGraphics.blit(TEXTURE,
                    faceX, faceY,
                    u, OVERLAY_V,
                    OVERLAY_WIDTH, OVERLAY_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (this.handlePatternPageClick(mouseX, mouseY, x, y)) return true;
        if (this.handleProgressClick(mouseX, mouseY, x, y)) return true;
        if (this.handleFluidTankClick(mouseX, mouseY, x, y)) return true;
        if (this.handleFaceModeClick(mouseX, mouseY, x, y)) return true;
        if (this.handleAutoIOClick(mouseX, mouseY, x, y)) return true;
        if (this.handleRedstoneControlClick(mouseX, mouseY, x, y)) return true;
        if (this.handleCancelAeClick(mouseX, mouseY, x, y)) return true;
        if (this.handleAeReturnClick(mouseX, mouseY, x, y)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.prevPageButtonPressed = false;
        this.nextPageButtonPressed = false;
        this.cancelAePressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 处理翻页按钮点击
     */
    private boolean handlePatternPageClick(double mouseX, double mouseY, int x, int y) {
        int currentPage = this.menu.getPatternPage();
        int maxPage = this.menu.getMaxPatternPage();

        // 左翻页按钮
        if (currentPage > 0 && isInArea(mouseX, mouseY,
                x + PREV_PAGE_BUTTON_X, y + PREV_PAGE_BUTTON_Y,
                PREV_PAGE_BUTTON_WIDTH, PREV_PAGE_BUTTON_HEIGHT)) {
            this.prevPageButtonPressed = true;
            int newPage = currentPage - 1;
            this.menu.setPatternPage(newPage);
            this.updatePatternSlotVisibility();
            PacketDistributor.sendToServer(new PatternPageChangePacket(newPage));
            return true;
        }

        // 右翻页按钮
        if (currentPage < maxPage && isInArea(mouseX, mouseY,
                x + NEXT_PAGE_BUTTON_X, y + NEXT_PAGE_BUTTON_Y,
                NEXT_PAGE_BUTTON_WIDTH, NEXT_PAGE_BUTTON_HEIGHT)) {
            this.nextPageButtonPressed = true;
            int newPage = currentPage + 1;
            this.menu.setPatternPage(newPage);
            this.updatePatternSlotVisibility();
            PacketDistributor.sendToServer(new PatternPageChangePacket(newPage));
            return true;
        }

        return false;
    }

    private boolean handleCancelAeClick(double mouseX, double mouseY, int x, int y) {
        if (isInArea(mouseX, mouseY, x + CANCEL_AE_X, y + CANCEL_AE_Y,
                CANCEL_AE_WIDTH, CANCEL_AE_HEIGHT)) {
            this.cancelAePressed = true;
            PacketDistributor.sendToServer(new AECancelPacket(
                    this.menu.getBlockEntity().getBlockPos()));
            return true;
        }
        return false;
    }

    private boolean handleAeReturnClick(double mouseX, double mouseY, int x, int y) {
        if (isInArea(mouseX, mouseY, x + AE_RETURN_X, y + AE_RETURN_Y,
                AE_RETURN_WIDTH, AE_RETURN_HEIGHT)) {
            if (this.menu.getBlockEntity() != null) {
                PacketDistributor.sendToServer(new AEReturnOutputTogglePacket(
                        this.menu.getBlockEntity().getBlockPos()));
            }
            return true;
        }
        return false;
    }

    /**
     * 处理进度条点击（查看配方）
     */
    private boolean handleProgressClick(double mouseX, double mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY,
                x + PROGRESS_BAR_X, y + PROGRESS_BAR_Y,
                PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT)) {
            return false;
        }

        if (ModList.get().isLoaded("jei")) {
            com.sorrowmist.useless.compat.jei.JEIPlugin.showAdvancedAlloyFurnaceRecipes();
        }
        return true;
    }

    /**
     * 处理流体槽点击（清空）
     */
    private boolean handleFluidTankClick(double mouseX, double mouseY, int x, int y) {
        // 检查输入流体槽
        for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
            int tankX = x + FLUID_INPUT_X + i * (FLUID_TANK_WIDTH + SLOT_SPACING);
            int tankY = y + FLUID_INPUT_Y;

            if (isInArea(mouseX, mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) {
                FluidStack fluid = this.menu.getInputFluidTank(i).getFluid();
                if (!fluid.isEmpty()) {
                    PacketDistributor.sendToServer(new TankClearPacket(
                            this.menu.getBlockEntity().getBlockPos(), i, true));
                }
                return true;
            }
        }

        // 检查输出流体槽
        for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
            int tankX = x + FLUID_OUTPUT_X + i * (FLUID_TANK_WIDTH + SLOT_SPACING);
            int tankY = y + FLUID_OUTPUT_Y;

            if (isInArea(mouseX, mouseY, tankX, tankY, FLUID_TANK_WIDTH, FLUID_TANK_HEIGHT)) {
                FluidStack fluid = this.menu.getOutputFluidTank(i).getFluid();
                if (!fluid.isEmpty()) {
                    PacketDistributor.sendToServer(new TankClearPacket(
                            this.menu.getBlockEntity().getBlockPos(), i, false));
                }
                return true;
            }
        }

        return false;
    }

    /**
     * 处理面模式控制区域点击。
     * <p>
     * 点击时循环该面的输入输出模式：
     * 客户端本地立即更新显示，同时发送网络包到服务器。
     */
    private boolean handleFaceModeClick(double mouseX, double mouseY, int x, int y) {
        for (int faceIdx = 0; faceIdx < FurnaceFace.COUNT; faceIdx++) {
            int faceX = x + FACE_XS[faceIdx];
            int faceY = y + FACE_YS[faceIdx];
            if (isInArea(mouseX, mouseY, faceX, faceY, OVERLAY_WIDTH, OVERLAY_HEIGHT)) {
                FurnaceFace face = FurnaceFace.values()[faceIdx];
                // 客户端本地立即更新
                FurnaceFaceMode newMode = this.menu.getBlockEntity().getFaceMode(face).next();
                this.menu.getBlockEntity().setFaceMode(face, newMode);
                // 发送网络包到服务器
                PacketDistributor.sendToServer(new FaceModeChangePacket(
                        this.menu.getBlockEntity().getBlockPos(), faceIdx));
                return true;
            }
        }
        return false;
    }

    /**
     * 处理自动输入输出配置区域点击。
     */
    private boolean handleAutoIOClick(double mouseX, double mouseY, int x, int y) {
        if (this.menu.getBlockEntity() == null) return false;

        // 自动输出区域
        if (isInArea(mouseX, mouseY, x + AUTO_OUTPUT_X, y + AUTO_OUTPUT_Y,
                AUTO_OUTPUT_WIDTH, AUTO_OUTPUT_HEIGHT)) {
            boolean newState = !this.menu.getBlockEntity().isAutoOutputEnabled();
            this.menu.getBlockEntity().setAutoOutputEnabled(newState);
            PacketDistributor.sendToServer(new AutoIOChangePacket(
                    this.menu.getBlockEntity().getBlockPos(), true));
            return true;
        }

        // 自动输入区域
        if (isInArea(mouseX, mouseY, x + AUTO_INPUT_X, y + AUTO_INPUT_Y,
                AUTO_INPUT_WIDTH, AUTO_INPUT_HEIGHT)) {
            boolean newState = !this.menu.getBlockEntity().isAutoInputEnabled();
            this.menu.getBlockEntity().setAutoInputEnabled(newState);
            PacketDistributor.sendToServer(new AutoIOChangePacket(
                    this.menu.getBlockEntity().getBlockPos(), false));
            return true;
        }

        return false;
    }

    /**
     * 处理红石控制区域点击。
     */
    private boolean handleRedstoneControlClick(double mouseX, double mouseY, int x, int y) {
        if (this.menu.getBlockEntity() == null) return false;

        if (isInArea(mouseX, mouseY, x + REDSTONE_CONTROL_X, y + REDSTONE_CONTROL_Y,
                REDSTONE_CONTROL_WIDTH, REDSTONE_CONTROL_HEIGHT)) {
            RedstoneControlMode newMode = this.menu.getBlockEntity().getRedstoneControlMode().next();
            this.menu.getBlockEntity().setRedstoneControlMode(newMode);
            PacketDistributor.sendToServer(new RedstoneControlPacket(
                    this.menu.getBlockEntity().getBlockPos()));
            return true;
        }

        return false;
    }

    private void updatePatternSlotVisibility() {
        int currentPage = this.menu.getPatternPage();
        int slotsPerPage = this.menu.getPatternSlotsPerPage();
        int base = currentPage * slotsPerPage;
        int end = Math.min(base + slotsPerPage, AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_COUNT);

        for (Slot slot : this.menu.slots) {
            if (slot instanceof com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler patternSlot) {
                int slotIndex = slot.getSlotIndex();
                int relativeIndex = slotIndex - AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_START;
                boolean isActive = relativeIndex >= base && relativeIndex < end;
                patternSlot.setActive(isActive);
            }
        }
    }

    private static boolean isInArea(double mouseX, double mouseY, int areaX, int areaY, int width, int height) {
        return mouseX >= areaX && mouseX < areaX + width && mouseY >= areaY && mouseY < areaY + height;
    }

    private void renderFluidTankTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y,
                                        boolean isInput) {
        int startX = isInput ? FLUID_INPUT_X : FLUID_OUTPUT_X;
        int startY = isInput ? FLUID_INPUT_Y : FLUID_OUTPUT_Y;

        for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
            int tankX = x + startX + i * (FLUID_TANK_WIDTH + SLOT_SPACING);
            int tankY = y + startY;

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

    private void renderEnergyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY,
                x + ENERGY_BAR_X, y + ENERGY_BAR_Y,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.energy",
                this.menu.getEnergy(), this.menu.getMaxEnergy()));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.useless_mod.furnace_tier", this.menu.getFurnaceTier())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_mod.furnace_upgrade_hint"));
        tooltip.add(Component.translatable("tooltip.useless_mod.furnace_upgrade_effect"));
        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderProgressTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY,
                x + PROGRESS_BAR_X, y + PROGRESS_BAR_Y,
                PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT)) {
            return;
        }

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

        int aeTotalProgress = this.menu.getTotalAEProgress();
        int aeTotalMaxProgress = this.menu.getTotalAEMaxProgress();
        var taskProgressList = this.menu.getAETaskProgressList();

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_tasks",
                        taskProgressList.size())
                .withStyle(ChatFormatting.DARK_PURPLE));

        if (!taskProgressList.isEmpty()) {
            for (var taskProgress : taskProgressList) {
                String productName = taskProgress.getProductName();
                int taskProgressVal = taskProgress.getProgress();
                int taskMaxProgress = taskProgress.getMaxProgress();
                long totalOutputCount = taskProgress.getTotalOutputCount();
                String statusKey = taskProgress.getStatusKey();
                String statusDetail = taskProgress.getStatusDetail();

                Component nameComponent = Component.literal(productName);
                Component statusComponent = Component.translatable(statusKey);

                if (taskMaxProgress > 1) {
                    tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_progress_ticks",
                                    nameComponent, totalOutputCount, taskProgressVal, taskMaxProgress, statusComponent)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else if (statusDetail != null && !statusDetail.isBlank()) {
                    tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_waiting_detail",
                                    nameComponent, totalOutputCount, statusComponent, createTaskDetailComponent(statusDetail))
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_waiting",
                                    nameComponent, totalOutputCount, statusComponent)
                            .withStyle(ChatFormatting.YELLOW));
                }
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

    private Component createTaskDetailComponent(String statusDetail) {
        if (statusDetail.startsWith("gui.useless_mod.")) {
            return Component.translatable(statusDetail);
        }
        return Component.literal(statusDetail);
    }

    private void renderTipsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY,
                x + TIPS_AREA_X, y + TIPS_AREA_Y,
                TIPS_AREA_SIZE, TIPS_AREA_SIZE)) {
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
            ItemStack catalyst = entity.getItemHandler().getStackInSlot(AdvancedAlloyFurnaceLayout.CATALYST_SLOT);

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

    private void renderFaceModeTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;

        for (int faceIdx = 0; faceIdx < FurnaceFace.COUNT; faceIdx++) {
            int faceX = x + FACE_XS[faceIdx];
            int faceY = y + FACE_YS[faceIdx];
            if (!isInArea(mouseX, mouseY, faceX, faceY, OVERLAY_WIDTH, OVERLAY_HEIGHT)) continue;

            FurnaceFace face = FurnaceFace.values()[faceIdx];
            FurnaceFaceMode mode = this.menu.getBlockEntity().getFaceMode(face);

            Component faceName = Component.translatable("gui.useless_mod.advanced_alloy_furnace.face." + face.name().toLowerCase());
            Component modeName = Component.translatable("gui.useless_mod.advanced_alloy_furnace.face_mode." + mode.name().toLowerCase());

            guiGraphics.renderTooltip(this.font, List.of(faceName, modeName), Optional.empty(), mouseX, mouseY);
            break;
        }
    }

    private void renderAutoIOTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;

        // 自动输出
        if (isInArea(mouseX, mouseY, x + AUTO_OUTPUT_X, y + AUTO_OUTPUT_Y,
                AUTO_OUTPUT_WIDTH, AUTO_OUTPUT_HEIGHT)) {
            Component title = Component.translatable("gui.useless_mod.advanced_alloy_furnace.auto_output");
            Component status = this.menu.getBlockEntity().isAutoOutputEnabled()
                    ? Component.translatable("gui.useless_mod.enabled")
                    : Component.translatable("gui.useless_mod.disabled");
            guiGraphics.renderTooltip(this.font, List.of(title, status), Optional.empty(), mouseX, mouseY);
            return;
        }

        // 自动输入
        if (isInArea(mouseX, mouseY, x + AUTO_INPUT_X, y + AUTO_INPUT_Y,
                AUTO_INPUT_WIDTH, AUTO_INPUT_HEIGHT)) {
            Component title = Component.translatable("gui.useless_mod.advanced_alloy_furnace.auto_input");
            Component status = this.menu.getBlockEntity().isAutoInputEnabled()
                    ? Component.translatable("gui.useless_mod.enabled")
                    : Component.translatable("gui.useless_mod.disabled");
            guiGraphics.renderTooltip(this.font, List.of(title, status), Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderRedstoneControlTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (this.menu.getBlockEntity() == null) return;

        if (isInArea(mouseX, mouseY, x + REDSTONE_CONTROL_X, y + REDSTONE_CONTROL_Y,
                REDSTONE_CONTROL_WIDTH, REDSTONE_CONTROL_HEIGHT)) {
            RedstoneControlMode mode = this.menu.getBlockEntity().getRedstoneControlMode();
            Component title = Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control");
            Component status = Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control." + mode.name().toLowerCase());
            guiGraphics.renderTooltip(this.font, List.of(title, status), Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderCancelAeTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (isInArea(mouseX, mouseY, x + CANCEL_AE_X, y + CANCEL_AE_Y,
                CANCEL_AE_WIDTH, CANCEL_AE_HEIGHT)) {
            Component title = Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks");
            Component desc = Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks.desc");
            guiGraphics.renderTooltip(this.font, List.of(title, desc), Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderAeReturnTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (!isInArea(mouseX, mouseY, x + AE_RETURN_X, y + AE_RETURN_Y,
                AE_RETURN_WIDTH, AE_RETURN_HEIGHT)) {
            return;
        }
        if (this.menu.getBlockEntity() == null) return;
        Component title = Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_return_output");
        Component status = this.menu.getBlockEntity().isReturnOutputToAe()
                ? Component.translatable("gui.useless_mod.enabled")
                : Component.translatable("gui.useless_mod.disabled");
        guiGraphics.renderTooltip(this.font, List.of(title, status), Optional.empty(), mouseX, mouseY);
    }
}
