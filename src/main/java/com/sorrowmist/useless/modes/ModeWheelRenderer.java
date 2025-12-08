package com.sorrowmist.useless.modes;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 模式圆盘渲染器，负责绘制模式选择圆盘和处理交互
 */
public class ModeWheelRenderer {
    // 圆盘的半径
    private static final int WHEEL_RADIUS = 120;
    // 圆盘的内半径（用于绘制中心区域）
    private static final int INNER_RADIUS = 30;
    // 每个模式扇形区域的角度（弧度）
    private static final float SEGMENT_ANGLE = (float) (2 * Math.PI / ToolMode.getTotalModes());
    // 高亮颜色（绿色）
    private static final int HIGHLIGHT_COLOR = 0x4000FF00;
    // 非高亮颜色（灰色）
    private static final int NORMAL_COLOR = 0x80808080;
    // 激活模式颜色（亮绿色）
    private static final int ACTIVE_COLOR = 0x8000FF00;
    // 鼠标悬停的模式索引
    private int hoveredModeIndex = -1;
    // 模式管理器
    private final ModeManager modeManager;
    
    /**
     * 构造一个新的模式圆盘渲染器
     * 
     * @param modeManager 模式管理器
     */
    public ModeWheelRenderer(ModeManager modeManager) {
        this.modeManager = modeManager;
    }
    
    /**
     * 渲染模式选择圆盘
     * 
     * @param guiGraphics GUI 图形上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        // 计算圆盘中心坐标
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        // 更新鼠标悬停的模式
        updateHoveredMode(mouseX, mouseY, centerX, centerY);
        
        // 绘制一个非常简单的模式轮盘，避免任何可能导致黑屏的复杂绘制
        Font font = Minecraft.getInstance().font;
        
        // 绘制中心文字
        Component centerText = Component.literal("G");
        int textWidth = font.width(centerText);
        int textHeight = font.lineHeight;
        guiGraphics.drawString(font, centerText, centerX - textWidth / 2, centerY - textHeight / 2, 0x000000, false);
        
        // 绘制简单的模式列表，以文字形式显示
        int yOffset = 0;
        for (ToolMode mode : ToolMode.values()) {
            Component modeText = mode.getTooltip();
            int modeTextWidth = font.width(modeText);
            
            // 根据模式是否激活和是否悬停设置颜色
            int color = 0xFFFFFF;
            if (modeManager.isModeActive(mode)) {
                color = 0x00FF00; // 激活模式为绿色
            }
            if (hoveredModeIndex == mode.getIndex()) {
                color = 0xFFFF00; // 悬停模式为黄色
            }
            
            // 绘制模式文字
            guiGraphics.drawString(font, modeText, centerX - modeTextWidth / 2, centerY + 20 + yOffset, color, false);
            yOffset += 12;
        }
    }
    
    /**
     * 绘制模式扇形区域
     */
    private void renderModeSegments(GuiGraphics guiGraphics, int centerX, int centerY) {
        PoseStack poseStack = guiGraphics.pose();
        
        for (int i = 0; i < ToolMode.getTotalModes(); i++) {
            ToolMode mode = ToolMode.byIndex(i);
            float startAngle = i * SEGMENT_ANGLE - SEGMENT_ANGLE / 2;
            float endAngle = (i + 1) * SEGMENT_ANGLE - SEGMENT_ANGLE / 2;
            
            // 确定扇形区域的颜色
            int color;
            if (modeManager.isModeActive(mode)) {
                color = ACTIVE_COLOR;
            } else if (i == hoveredModeIndex) {
                color = HIGHLIGHT_COLOR;
            } else {
                color = NORMAL_COLOR;
            }
            
            // 绘制扇形区域
            drawSector(guiGraphics, centerX, centerY, startAngle, endAngle, WHEEL_RADIUS, INNER_RADIUS, color);
        }
    }
    
    /**
     * 绘制中心区域
     */
    private void renderCenter(GuiGraphics guiGraphics, int centerX, int centerY) {
        // 绘制中心白色圆形
        drawCircle(guiGraphics, centerX, centerY, INNER_RADIUS, 0xFFFFFFFF);
        
        // 绘制中心文字
        Font font = Minecraft.getInstance().font;
        Component text = Component.literal("G");
        int textWidth = font.width(text);
        int textHeight = font.lineHeight;
        guiGraphics.drawString(font, text, centerX - textWidth / 2, centerY - textHeight / 2, 0x000000, false);
    }
    
    /**
     * 绘制圆形
     */
    private void drawCircle(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int color) {
        // 使用正方形填充近似圆形
        int diameter = radius * 2;
        guiGraphics.fill(centerX - radius, centerY - radius, centerX + radius, centerY + radius, color);
    }
    
    /**
     * 绘制模式名称
     */
    private void renderModeNames(GuiGraphics guiGraphics, int centerX, int centerY) {
        Font font = Minecraft.getInstance().font;
        
        for (int i = 0; i < ToolMode.getTotalModes(); i++) {
            ToolMode mode = ToolMode.byIndex(i);
            Component name = mode.getTooltip();
            float angle = (i + 0.5F) * SEGMENT_ANGLE - SEGMENT_ANGLE / 2;
            
            // 计算文字位置
            int textRadius = WHEEL_RADIUS + 10;
            int textX = (int) (centerX + Math.cos(angle) * textRadius - font.width(name) / 2);
            int textY = (int) (centerY + Math.sin(angle) * textRadius - font.lineHeight / 2);
            
            // 绘制文字
            guiGraphics.drawString(font, name, textX, textY, 0xFFFFFF, false);
        }
    }
    
    /**
     * 绘制扇形区域
     */
    private void drawSector(GuiGraphics guiGraphics, int centerX, int centerY, float startAngle, float endAngle, int outerRadius, int innerRadius, int color) {
        // 使用简单的线条绘制扇形轮廓，避免大面积填充导致黑屏
        int segments = 8; // 减少分段数，提高性能
        
        // 绘制外圆弧
        for (int i = 0; i < segments; i++) {
            float currentAngle = startAngle + (endAngle - startAngle) * i / segments;
            float nextAngle = startAngle + (endAngle - startAngle) * (i + 1) / segments;
            
            int x1 = (int) (centerX + Math.cos(currentAngle) * outerRadius);
            int y1 = (int) (centerY + Math.sin(currentAngle) * outerRadius);
            int x2 = (int) (centerX + Math.cos(nextAngle) * outerRadius);
            int y2 = (int) (centerY + Math.sin(nextAngle) * outerRadius);
            
            // 绘制外圆弧线段
            drawLine(guiGraphics, x1, y1, x2, y2, 0xFFFFFFFF);
        }
        
        // 绘制内圆弧
        for (int i = 0; i < segments; i++) {
            float currentAngle = startAngle + (endAngle - startAngle) * i / segments;
            float nextAngle = startAngle + (endAngle - startAngle) * (i + 1) / segments;
            
            int x1 = (int) (centerX + Math.cos(currentAngle) * innerRadius);
            int y1 = (int) (centerY + Math.sin(currentAngle) * innerRadius);
            int x2 = (int) (centerX + Math.cos(nextAngle) * innerRadius);
            int y2 = (int) (centerY + Math.sin(nextAngle) * innerRadius);
            
            // 绘制内圆弧线段
            drawLine(guiGraphics, x1, y1, x2, y2, 0xFFFFFFFF);
        }
        
        // 绘制两条半径线
        int outerX1 = (int) (centerX + Math.cos(startAngle) * outerRadius);
        int outerY1 = (int) (centerY + Math.sin(startAngle) * outerRadius);
        int innerX1 = (int) (centerX + Math.cos(startAngle) * innerRadius);
        int innerY1 = (int) (centerY + Math.sin(startAngle) * innerRadius);
        drawLine(guiGraphics, outerX1, outerY1, innerX1, innerY1, 0xFFFFFFFF);
        
        int outerX2 = (int) (centerX + Math.cos(endAngle) * outerRadius);
        int outerY2 = (int) (centerY + Math.sin(endAngle) * outerRadius);
        int innerX2 = (int) (centerX + Math.cos(endAngle) * innerRadius);
        int innerY2 = (int) (centerY + Math.sin(endAngle) * innerRadius);
        drawLine(guiGraphics, outerX2, outerY2, innerX2, innerY2, 0xFFFFFFFF);
    }
    
    /**
     * 绘制线段的辅助方法
     */
    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        // 使用填充矩形模拟线段
        // 这种方式更简单，兼容性更好
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        
        if (dx > dy) {
            // 水平线
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int y = y1;
            guiGraphics.fill(minX, y, maxX + 1, y + 1, color);
        } else {
            // 垂直线
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            int x = x1;
            guiGraphics.fill(x, minY, x + 1, maxY + 1, color);
        }
    }
    
    /**
     * 更新鼠标悬停的模式
     */
    private void updateHoveredMode(int mouseX, int mouseY, int centerX, int centerY) {
        // 计算鼠标到中心的距离
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // 如果鼠标在圆盘范围内
        if (distance > INNER_RADIUS && distance < WHEEL_RADIUS) {
            // 计算鼠标相对于中心的角度
            double angle = Math.atan2(dy, dx);
            if (angle < 0) {
                angle += 2 * Math.PI;
            }
            
            // 计算当前角度对应的模式索引
            int modeIndex = (int) ((angle + SEGMENT_ANGLE / 2) / SEGMENT_ANGLE) % ToolMode.getTotalModes();
            hoveredModeIndex = modeIndex;
        } else {
            hoveredModeIndex = -1;
        }
    }
    
    /**
     * 处理鼠标点击事件
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param centerX 圆盘中心 X 坐标
     * @param centerY 圆盘中心 Y 坐标
     * @param itemStack 工具物品栈
     * @return 是否处理了点击事件
     */
    public boolean handleMouseClick(int mouseX, int mouseY, int centerX, int centerY, ItemStack itemStack) {
        // 如果鼠标悬停在某个模式上
        if (hoveredModeIndex != -1) {
            // 获取对应的模式
            ToolMode mode = ToolMode.byIndex(hoveredModeIndex);
            
            // 切换模式激活状态
            modeManager.toggleMode(mode);
            
            // 保存模式状态到物品栈
            modeManager.saveToStack(itemStack);
            
            return true;
        }
        return false;
    }
    
    /**
     * 获取当前鼠标悬停的模式索引
     */
    public int getHoveredModeIndex() {
        return hoveredModeIndex;
    }
}