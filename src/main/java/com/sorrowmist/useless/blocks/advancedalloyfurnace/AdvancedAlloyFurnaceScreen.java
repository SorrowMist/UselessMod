// AdvancedAlloyFurnaceScreen.java
package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceScreen extends AbstractContainerScreen<AdvancedAlloyFurnaceMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "textures/gui/advanced_alloy_furnace.png");

    // 添加字段来跟踪上次渲染状态
    private int lastEnergy = -1;
    private int lastProgress = -1;
    private boolean lastActive = false;
    private FluidStack lastInputFluid = FluidStack.EMPTY;
    private FluidStack lastOutputFluid = FluidStack.EMPTY;

    // 修改：调整进度条的位置和尺寸，使其纵向居中
    private static final int PROGRESS_ARROW_X = 26;
    private static final int PROGRESS_ARROW_Y = 40; // 调整Y坐标使其在输入和输出槽中间
    private static final int PROGRESS_ARROW_WIDTH = 108; // 与能量条同宽
    private static final int PROGRESS_ARROW_HEIGHT = 5;  // 更细的高度

    public AdvancedAlloyFurnaceScreen(AdvancedAlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // 渲染能量条和进度箭头
        renderEnergyBar(guiGraphics, x, y);
        renderProgressArrow(guiGraphics, x, y);

        // 修改：使用自定义渲染方法渲染所有槽位
        renderAllSlotsCustom(guiGraphics, x, y);

        // 渲染流体槽（使用真实流体纹理）
        renderFluidTanks(guiGraphics, x, y);
    }

    // 修改：将能量条改为底部横向渲染
    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int energyStored = menu.getEnergy();
        int maxEnergy = menu.getMaxEnergy();

        if (maxEnergy > 0) {
            // 新的能量条位置：在输出槽下方，横向渲染
            int energyBarX = x + 26;  // 与输出槽左对齐
            int energyBarY = y + 75;  // 在输出槽下方
            int energyBarWidth = 108; // 与6个输出槽总宽度相同
            int energyBarHeight = 8;  // 适当的高度

            // 计算能量填充宽度
            int energyWidth = (int) (energyBarWidth * ((float) energyStored / maxEnergy));

            // 绘制能量填充（根据能量比例改变颜色）
            int color = getEnergyBarColor((float) energyStored / maxEnergy);
            guiGraphics.fill(energyBarX, energyBarY, energyBarX + energyWidth, energyBarY + energyBarHeight, color);

            // 绘制能量条边框
            guiGraphics.renderOutline(energyBarX, energyBarY, energyBarWidth, energyBarHeight, 0xFF000000);
        }
    }

    private int getEnergyBarColor(float energyRatio) {
        if (energyRatio > 0.7f) return 0xFF00FF00; // 绿色
        if (energyRatio > 0.3f) return 0xFFFFFF00; // 黄色
        return 0xFFFF0000; // 红色
    }

    // 修改：渲染进度条，从右到左减少（兼容进度获取逻辑）
    private void renderProgressArrow(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null) return;

        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();

        if (maxProgress > 0) {
            // 修改：假设进度获取是反的（初始为满，处理完为0），所以进度比例是 progress/maxProgress
            float progressRatio = (float) progress / maxProgress;

            // 根据进度比例计算颜色
            int arrowColor = getProgressArrowColor(progressRatio);

            // 绘制进度条背景（灰色）
            guiGraphics.fill(x + PROGRESS_ARROW_X, y + PROGRESS_ARROW_Y,
                    x + PROGRESS_ARROW_X + PROGRESS_ARROW_WIDTH,
                    y + PROGRESS_ARROW_Y + PROGRESS_ARROW_HEIGHT,
                    0xFF888888);

            // 修改：根据进度从右到左绘制填充部分（初始为满，处理完为0）
            int progressWidth = (int) (PROGRESS_ARROW_WIDTH * progressRatio);
            if (progressWidth > 0) {
                // 使用颜色填充来显示进度，从右到左减少
                guiGraphics.fill(x + PROGRESS_ARROW_X + (PROGRESS_ARROW_WIDTH - progressWidth),
                        y + PROGRESS_ARROW_Y,
                        x + PROGRESS_ARROW_X + PROGRESS_ARROW_WIDTH,
                        y + PROGRESS_ARROW_Y + PROGRESS_ARROW_HEIGHT,
                        arrowColor);
            }

            // 绘制进度条边框
            guiGraphics.renderOutline(x + PROGRESS_ARROW_X, y + PROGRESS_ARROW_Y,
                    PROGRESS_ARROW_WIDTH, PROGRESS_ARROW_HEIGHT,
                    0xFF000000);

            // 更新缓存
            lastProgress = progress;
        }
    }

    // 修改：根据进度比例获取颜色（现在进度是反的，所以颜色逻辑也要反过来）
    private int getProgressArrowColor(float progressRatio) {
        if (progressRatio > 0.75f) return 0xFF0000FF; // 蓝色（刚开始）
        if (progressRatio > 0.5f) return 0xFF00FF00;  // 绿色（进行中）
        if (progressRatio > 0.25f) return 0xFFFFFF00; // 黄色（接近完成）
        return 0xFFFF0000; // 红色（即将完成）
    }

    // 新增：自定义渲染所有槽位，支持大数量堆叠
    private void renderAllSlotsCustom(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        // 渲染输入槽 (0-5)
        for (int i = 0; i < 6; i++) {
            ItemStack currentStack = menu.getBlockEntity().getItemInSlot(i);
            int slotX = x + 26 + i * 18;
            int slotY = y + 17;

            // 绘制槽位背景
            guiGraphics.blit(TEXTURE, slotX, slotY, 26 + i * 18, 17, 16, 16);

            // 绘制物品
            renderItemStackCustom(guiGraphics, currentStack, slotX + 1, slotY + 1);
        }

        // 渲染输出槽 (6-11)
        for (int i = 6; i < 12; i++) {
            ItemStack currentStack = menu.getBlockEntity().getItemInSlot(i);
            int slotX = x + 26 + (i - 6) * 18;
            int slotY = y + 53;

            // 绘制槽位背景
            guiGraphics.blit(TEXTURE, slotX, slotY, 26 + (i - 6) * 18, 53, 16, 16);

            // 绘制物品
            renderItemStackCustom(guiGraphics, currentStack, slotX + 1, slotY + 1);
        }
    }

    // 新增：自定义物品堆叠渲染，支持大数量
    private void renderItemStackCustom(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            // 渲染物品图标
            guiGraphics.renderFakeItem(stack, x, y);

            // 渲染数量文本（使用自定义格式）
            if (stack.getCount() > 1) {
                String countText = getCountText(stack.getCount());

                // 保存当前颜色状态
                guiGraphics.pose().pushPose();

                // 根据数量大小调整文本缩放
                float scale = getTextScale(stack.getCount());
                guiGraphics.pose().scale(scale, scale, 1.0F);

                // 计算缩放后的位置
                int scaledX = (int) (x / scale);
                int scaledY = (int) (y / scale);

                // 渲染数量文本
                guiGraphics.drawString(this.font, countText,
                        scaledX + 19 - (int)(this.font.width(countText) * scale),
                        scaledY + 9, 0xFFFFFF, true);

                // 恢复颜色状态
                guiGraphics.pose().popPose();
            }

            // 渲染耐久条等其他装饰（如果需要）
            guiGraphics.renderItemDecorations(this.font, stack, x, y, "");
        }
    }

    // 新增：根据数量获取合适的文本缩放比例
    private float getTextScale(int count) {
        if (count < 1000) return 0.8f;
        if (count < 10000) return 0.7f;
        if (count < 100000) return 0.6f;
        return 0.5f;
    }

    // 新增：处理大数量的文本显示
    private String getCountText(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            // 使用k表示千
            int thousands = count / 1000;
            int remainder = (count % 1000) / 100;
            if (remainder > 0) {
                return String.format("%d.%dk", thousands, remainder);
            } else {
                return String.format("%dk", thousands);
            }
        } else {
            // 使用M表示百万
            int millions = count / 1000000;
            int remainder = (count % 1000000) / 100000;
            if (remainder > 0) {
                return String.format("%d.%dM", millions, remainder);
            } else {
                return String.format("%dM", millions);
            }
        }
    }

    // 修改：使用真实流体纹理渲染流体槽
    private void renderFluidTanks(GuiGraphics guiGraphics, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        // 获取流体数据
        FluidStack inputFluid = menu.getBlockEntity().getInputFluidTank().getFluid();
        FluidStack outputFluid = menu.getBlockEntity().getOutputFluidTank().getFluid();

        // 输入流体槽位置：x=8, y=17, 宽度=16, 高度=40
        renderFluidTankWithTexture(guiGraphics, x + 8, y + 17, 16, 40, inputFluid, menu.getBlockEntity().getInputFluidTank().getCapacity());

        // 输出流体槽位置：x=152, y=17, 宽度=16, 高度=40
        renderFluidTankWithTexture(guiGraphics, x + 152, y + 17, 16, 40, outputFluid, menu.getBlockEntity().getOutputFluidTank().getCapacity());

        // 更新缓存
        lastInputFluid = inputFluid.copy();
        lastOutputFluid = outputFluid.copy();
    }

    // 新增：使用真实流体纹理渲染流体槽
    private void renderFluidTankWithTexture(GuiGraphics guiGraphics, int x, int y, int width, int height, FluidStack fluid, int capacity) {
        // 绘制流体槽背景
        guiGraphics.blit(TEXTURE, x, y, 8, 17, width, height);

        // 如果有流体，绘制流体纹理
        if (!fluid.isEmpty() && capacity > 0) {
            int fluidHeight = (int) (height * ((float) fluid.getAmount() / capacity));
            if (fluidHeight > 0) {
                // 获取流体纹理和颜色
                IClientFluidTypeExtensions fluidAttributes = IClientFluidTypeExtensions.of(fluid.getFluid());
                ResourceLocation fluidStillTexture = fluidAttributes.getStillTexture(fluid);
                int fluidColor = fluidAttributes.getTintColor(fluid);

                if (fluidStillTexture != null) {
                    // 绑定流体纹理
                    TextureAtlasSprite fluidSprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(fluidStillTexture);

                    // 设置渲染颜色（使用流体的颜色）
                    float r = ((fluidColor >> 16) & 0xFF) / 255.0F;
                    float g = ((fluidColor >> 8) & 0xFF) / 255.0F;
                    float b = (fluidColor & 0xFF) / 255.0F;
                    float a = ((fluidColor >> 24) & 0xFF) / 255.0F;

                    RenderSystem.setShaderColor(r, g, b, a);
                    RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

                    // 计算流体在槽内的位置（从底部开始）
                    int fluidY = y + height - fluidHeight;

                    // 绘制流体纹理（平铺）
                    int textureSize = 16; // 流体纹理通常是16x16
                    int repeatX = (int) Math.ceil((double) width / textureSize);
                    int repeatY = (int) Math.ceil((double) fluidHeight / textureSize);

                    for (int i = 0; i < repeatX; i++) {
                        for (int j = 0; j < repeatY; j++) {
                            int texX = x + i * textureSize;
                            int texY = fluidY + j * textureSize;
                            int texWidth = Math.min(textureSize, width - i * textureSize);
                            int texHeight = Math.min(textureSize, fluidHeight - j * textureSize);

                            guiGraphics.blit(texX, texY, 0, texWidth, texHeight, fluidSprite);
                        }
                    }

                    // 重置颜色
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        }

        // 绘制流体槽边框
        guiGraphics.renderOutline(x, y, width, height, 0xFF000000);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 只保留标题和玩家物品栏标题，移除所有状态文本
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (menu != null) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;

            // 新增：进度条悬停提示（汉化）
            if (isMouseOverArea(mouseX, mouseY, x + PROGRESS_ARROW_X, y + PROGRESS_ARROW_Y,
                    PROGRESS_ARROW_WIDTH, PROGRESS_ARROW_HEIGHT)) {
                List<Component> tooltip = new ArrayList<>();
                int progress = menu.getProgress();
                int maxProgress = menu.getMaxProgress();

                if (maxProgress > 0) {
                    // 修改：计算剩余进度（因为进度获取是反的）
                    int remainingProgress = progress;
                    int completedProgress = maxProgress - progress;
                    float progressPercent = (float) completedProgress / maxProgress * 100;

                    tooltip.add(Component.literal("进度: " + completedProgress + "/" + maxProgress + " (" + String.format("%.1f", progressPercent) + "%)"));
                    tooltip.add(Component.literal("状态: " + (menu.isActive() ? "工作中" : "空闲")));

                    // 根据进度显示不同的状态信息
                    if (completedProgress > 0 && completedProgress < maxProgress) {
                        tooltip.add(Component.literal("正在处理中..."));
                    } else if (completedProgress >= maxProgress) {
                        tooltip.add(Component.literal("准备输出"));
                    }
                } else {
                    tooltip.add(Component.literal("没有活动进程"));
                }

                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }

            // 新的能量条悬停区域（底部横向）- 汉化
            if (isMouseOverArea(mouseX, mouseY, x + 26, y + 75, 108, 8)) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("能量: " + menu.getEnergy() + " / " + menu.getMaxEnergy() + " FE"));
                tooltip.add(Component.literal("消耗: " +
                        (menu.getBlockEntity() != null ? menu.getBlockEntity().getProcessTick() : 0) + " FE/t"));
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }

            // 输入流体槽悬停提示 - 汉化
            if (isMouseOverArea(mouseX, mouseY, x + 8, y + 17, 16, 40)) {
                if (menu.getBlockEntity() != null) {
                    FluidStack inputFluid = menu.getBlockEntity().getInputFluidTank().getFluid();
                    if (!inputFluid.isEmpty()) {
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.literal("输入流体: " + inputFluid.getDisplayName().getString()));
                        tooltip.add(Component.literal("数量: " + inputFluid.getAmount() + " / " +
                                menu.getBlockEntity().getInputFluidTank().getCapacity() + " mB"));
                        tooltip.add(Component.literal("温度: " + inputFluid.getFluid().getFluidType().getTemperature() + " K"));
                        guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                    }
                }
            }

            // 输出流体槽悬停提示 - 汉化
            if (isMouseOverArea(mouseX, mouseY, x + 152, y + 17, 16, 40)) {
                if (menu.getBlockEntity() != null) {
                    FluidStack outputFluid = menu.getBlockEntity().getOutputFluidTank().getFluid();
                    if (!outputFluid.isEmpty()) {
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.literal("输出流体: " + outputFluid.getDisplayName().getString()));
                        tooltip.add(Component.literal("数量: " + outputFluid.getAmount() + " / " +
                                menu.getBlockEntity().getOutputFluidTank().getCapacity() + " mB"));
                        tooltip.add(Component.literal("温度: " + outputFluid.getFluid().getFluidType().getTemperature() + " K"));
                        guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                    }
                }
            }

            // 新增：物品槽悬停提示，显示实际数量
            renderItemStackTooltips(guiGraphics, mouseX, mouseY, x, y);
        }
    }

    // 新增：物品槽悬停提示
    private void renderItemStackTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        if (menu == null || menu.getBlockEntity() == null) return;

        // 检查输入槽
        for (int i = 0; i < 6; i++) {
            ItemStack stack = menu.getBlockEntity().getItemInSlot(i);
            if (!stack.isEmpty() && isMouseOverArea(mouseX, mouseY, x + 26 + i * 18, y + 17, 16, 16)) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(stack.getHoverName());
                tooltip.add(Component.literal("数量: " + stack.getCount()));
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                return;
            }
        }

        // 检查输出槽
        for (int i = 6; i < 12; i++) {
            ItemStack stack = menu.getBlockEntity().getItemInSlot(i);
            if (!stack.isEmpty() && isMouseOverArea(mouseX, mouseY, x + 26 + (i - 6) * 18, y + 53, 16, 16)) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(stack.getHoverName());
                tooltip.add(Component.literal("数量: " + stack.getCount()));
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    private boolean isMouseOverArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}