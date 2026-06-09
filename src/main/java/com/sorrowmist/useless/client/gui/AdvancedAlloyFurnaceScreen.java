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

    // 能量槽位置：起点153,153，大小82*18
    private static final int ENERGY_BAR_X = 153;
    private static final int ENERGY_BAR_Y = 153;
    private static final int ENERGY_BAR_WIDTH = 82;
    private static final int ENERGY_BAR_HEIGHT = 18;

    // 能量槽纹理坐标：起点13,401
    private static final int ENERGY_BAR_U = 13;
    private static final int ENERGY_BAR_V = 401;

    // 帮助提示区域位置：起点103,154，大小16*16
    private static final int TIPS_AREA_X = 103;
    private static final int TIPS_AREA_Y = 154;
    private static final int TIPS_AREA_SIZE = 16;

    // 翻页按钮状态
    private boolean prevPageButtonPressed = false;
    private boolean nextPageButtonPressed = false;

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
        int energyStored = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        if (maxEnergy <= 0) return;

        float energyRatio = (float) energyStored / maxEnergy;
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

        for (int i = 0; i < AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT; i++) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (this.handlePatternPageClick(mouseX, mouseY, x, y)) return true;
        if (this.handleProgressClick(mouseX, mouseY, x, y)) return true;
        if (this.handleFluidTankClick(mouseX, mouseY, x, y)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.prevPageButtonPressed = false;
        this.nextPageButtonPressed = false;
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
        for (int i = 0; i < AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT; i++) {
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
        for (int i = 0; i < AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT; i++) {
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

    private static boolean isInArea(double mouseX, double mouseY, int areaX, int areaY, int width, int height) {
        return mouseX >= areaX && mouseX < areaX + width && mouseY >= areaY && mouseY < areaY + height;
    }

    private void renderFluidTankTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y,
                                        boolean isInput) {
        int startX = isInput ? FLUID_INPUT_X : FLUID_OUTPUT_X;
        int startY = isInput ? FLUID_INPUT_Y : FLUID_OUTPUT_Y;

        for (int i = 0; i < AdvancedAlloyFurnaceBlockEntity.FLUID_TANK_COUNT; i++) {
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
}
