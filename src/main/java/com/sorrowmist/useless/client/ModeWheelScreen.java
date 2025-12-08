package com.sorrowmist.useless.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorrowmist.useless.modes.ModeManager;
import com.sorrowmist.useless.modes.ToolMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 模式轮盘屏幕，使用Minecraft官方Screen类实现
 */
public class ModeWheelScreen extends Screen {
    // 模式管理器
    private final ModeManager modeManager;
    // 主手物品栈
    private final ItemStack mainHandItem;
    // 按钮列表
    private final Button[] modeButtons;
    
    /**
     * 构造模式轮盘屏幕
     * 
     * @param modeManager 模式管理器
     * @param mainHandItem 主手物品栈
     */
    public ModeWheelScreen(ModeManager modeManager, ItemStack mainHandItem) {
        super(Component.literal("Mode Wheel"));
        this.modeManager = modeManager;
        this.mainHandItem = mainHandItem;
        // 计算不包含普通连锁模式的按钮数量
        int buttonCount = 0;
        for (ToolMode mode : ToolMode.values()) {
            if (mode != ToolMode.CHAIN_MINING) {
                // 检查是否是MEK_CONFIGURATOR模式，如果是，添加兼容性检查
                if (mode == ToolMode.MEK_CONFIGURATOR) {
                    net.minecraft.resources.ResourceLocation configuratorId = new net.minecraft.resources.ResourceLocation("mekanism:configurator");
                    if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(configuratorId)) {
                        buttonCount++;
                    }
                } else {
                    buttonCount++;
                }
            }
        }
        this.modeButtons = new Button[buttonCount];
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int spacing = 5;
        int totalHeight = (buttonHeight + spacing) * modeButtons.length;
        
        // 创建模式按钮，跳过普通连锁模式
        int buttonIndex = 0;
        for (ToolMode mode : ToolMode.values()) {
            if (mode != ToolMode.CHAIN_MINING) {
                // 检查是否是MEK_CONFIGURATOR模式，如果是，添加兼容性检查
                boolean shouldAddButton = true;
                if (mode == ToolMode.MEK_CONFIGURATOR) {
                    net.minecraft.resources.ResourceLocation configuratorId = new net.minecraft.resources.ResourceLocation("mekanism:configurator");
                    shouldAddButton = net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(configuratorId);
                }
                
                if (shouldAddButton) {
                    int y = centerY - totalHeight / 2 + buttonIndex * (buttonHeight + spacing);
                    
                    Button button = Button.builder(
                            getModeButtonText(mode),
                            (btn) -> handleModeClick(mode)
                        )
                        .bounds(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight)
                        .build();
                    
                    addRenderableWidget(button);
                    modeButtons[buttonIndex] = button;
                    buttonIndex++;
                }
            }
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 绘制半透明背景
        renderBackground(guiGraphics);
        
        // 绘制标题
        Component title = Component.literal("Tool Modes");
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, width / 2 - titleWidth / 2, height / 2 - 100, 0xFFFFFF, false);
        
        // 绘制说明文字
        Component info = Component.literal("Press G to close");
        int infoWidth = font.width(info);
        guiGraphics.drawString(font, info, width / 2 - infoWidth / 2, height / 2 + 100, 0x808080, false);
        
        // 绘制按钮
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 按下G键关闭屏幕
        if (keyCode == InputConstants.KEY_G) {
            onClose();
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        // 不是暂停屏幕，游戏继续运行
        return false;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        // 允许按ESC关闭
        return true;
    }
    
    /**
     * 处理模式按钮点击
     * 
     * @param mode 被点击的模式
     */
    private void handleModeClick(ToolMode mode) {
        // 发送数据包到服务端处理模式切换
        // 不再在客户端直接修改物品，而是由服务端统一处理
        com.sorrowmist.useless.networking.ModMessages.sendToServer(
            new com.sorrowmist.useless.networking.ToolModeSwitchPacket(mode)
        );
        
        // 仅在客户端更新UI显示（不修改实际状态）
        modeManager.toggleMode(mode);
        
        // 更新按钮文本
        updateButtonTexts();
        
        // 关闭模式轮盘
        onClose();
    }
    
    /**
     * 更新所有按钮文本
     */
    private void updateButtonTexts() {
        int buttonIndex = 0;
        for (ToolMode mode : ToolMode.values()) {
            if (mode != ToolMode.CHAIN_MINING) {
                // 检查是否是MEK_CONFIGURATOR模式，如果是，添加兼容性检查
                boolean shouldUpdateButton = true;
                if (mode == ToolMode.MEK_CONFIGURATOR) {
                    net.minecraft.resources.ResourceLocation configuratorId = new net.minecraft.resources.ResourceLocation("mekanism:configurator");
                    shouldUpdateButton = net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(configuratorId);
                }
                
                if (shouldUpdateButton) {
                    modeButtons[buttonIndex].setMessage(getModeButtonText(mode));
                    buttonIndex++;
                }
            }
        }
    }
    
    /**
     * 获取模式按钮的文本
     * 
     * @param mode 模式
     * @return 按钮文本
     */
    private Component getModeButtonText(ToolMode mode) {
        boolean isActive = modeManager.isModeActive(mode);
        Component status = isActive ? Component.translatable("text.useless_mod.active") : Component.literal("");
        return isActive ? Component.translatable("tooltip.useless_mod.mode_with_status", mode.getTooltip(), status) : mode.getTooltip();
    }
    
    /**
     * 显示模式轮盘屏幕
     * 
     * @param modeManager 模式管理器
     * @param mainHandItem 主手物品栈
     */
    public static void show(ModeManager modeManager, ItemStack mainHandItem) {
        Minecraft.getInstance().setScreen(new ModeWheelScreen(modeManager, mainHandItem));
    }
}