package com.sorrowmist.useless.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sorrowmist.useless.modes.ModeManager;
import com.sorrowmist.useless.modes.ToolMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 模式轮盘屏幕，使用Minecraft官方Screen类实现
 */
public class ModeWheelScreen extends Screen {
    // 模式管理器
    private final ModeManager modeManager;
    // 主手物品栈
    private final ItemStack mainHandItem;
    
    // 圆盘菜单参数
    private static final float PRECISION = 5.0f;
    private static final int MAX_SLOTS = 18;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;
    
    private float totalTime;
    private float prevTick;
    private float extraTick;
    private boolean closing = false;
    
    // 模式数据分组
    private final List<ModeData> leftModes = new ArrayList<>();
    private final List<ModeData> middleModes = new ArrayList<>();
    private final List<ModeData> rightModes = new ArrayList<>();
    
    public record ModeData(ToolMode mode, Component name, boolean isActive) {}
    
    // 圆盘配置
    private static final float DISC_RADIUS = 60.0f;
    private static final float DISC_SPACING = 150.0f;
    
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
        this.minecraft = Minecraft.getInstance();
        loadModes();
    }
    
    private void loadModes() {
        leftModes.clear();
        middleModes.clear();
        rightModes.clear();
        
        for (ToolMode mode : ToolMode.values()) {
            if (mode != ToolMode.CHAIN_MINING) {
                boolean shouldAddMode = true;
                if (mode == ToolMode.OMNITOOL_MODE) {
                    net.minecraft.resources.ResourceLocation omnitoolId = new net.minecraft.resources.ResourceLocation("omnitools:omni_wrench");
                    shouldAddMode = net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(omnitoolId);
                }
                
                if (shouldAddMode) {
                    boolean isActive = modeManager.isModeActive(mode);
                    Component name = isActive ? Component.translatable("tooltip.useless_mod.mode_with_status", mode.getTooltip(), Component.translatable("text.useless_mod.active")) : mode.getTooltip();
                    ModeData modeData = new ModeData(mode, name, isActive);
                    
                    // 根据模式类型分配到不同分组
                    switch (mode) {
                        case SILK_TOUCH:
                        case FORTUNE:
                            leftModes.add(modeData);
                            break;
                        case WRENCH_MODE:
                        case MALLET_MODE:
                        case CROWBAR_MODE:
                        case HAMMER_MODE:
                        case SCREWDRIVER_MODE:
                        case OMNITOOL_MODE:
                            middleModes.add(modeData);
                            break;
                        case FORCE_MINING:
                        case AE_STORAGE_PRIORITY:
                        case ENHANCED_CHAIN_MINING:
                            rightModes.add(modeData);
                            break;
                        default:
                            // 其他模式暂时不处理
                            break;
                    }
                }
            }
        }
    }
    
    @Override
    public void tick() {
        if (totalTime < OPEN_ANIMATION_LENGTH) {
            extraTick++;
        }
        
        // 检查G键是否被松开，如果松开则关闭菜单
        if (!InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_G)) {
            onClose();
        }
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (leftModes.isEmpty() && middleModes.isEmpty() && rightModes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("message.useless_mod.no_modes"), width / 2, height / 2, 0xFFFFFF);
            return;
        }
        
        PoseStack ms = graphics.pose();
        float openAnimation = closing ? 1.0f - totalTime / OPEN_ANIMATION_LENGTH : totalTime / OPEN_ANIMATION_LENGTH;
        float currTick = minecraft.getFrameTime();
        totalTime += (currTick + extraTick - prevTick) / 20f;
        extraTick = 0;
        prevTick = currTick;
        
        float animProgress = Mth.clamp(openAnimation, 0, 1);
        animProgress = (float) (1 - Math.pow(1 - animProgress, 3));
        
        // 计算三个圆盘的中心位置
        int centerY = height / 2;
        int leftCenterX = (int) (width / 2 - DISC_SPACING);
        int middleCenterX = width / 2;
        int rightCenterX = (int) (width / 2 + DISC_SPACING);
        
        // 渲染准备
        ms.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        // 绘制三个圆盘
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        // 绘制左圆盘
        drawDisc(buffer, leftCenterX, centerY, DISC_RADIUS, leftModes, mouseX, mouseY, animProgress);
        
        // 绘制中间圆盘
        drawDisc(buffer, middleCenterX, centerY, DISC_RADIUS, middleModes, mouseX, mouseY, animProgress);
        
        // 绘制右圆盘
        drawDisc(buffer, rightCenterX, centerY, DISC_RADIUS, rightModes, mouseX, mouseY, animProgress);
        
        BufferUploader.drawWithShader(buffer.end());
        
        // 绘制分隔线
        buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        
        // 左圆盘分隔线
        drawDiscDividers(buffer, leftCenterX, centerY, DISC_RADIUS, leftModes.size(), animProgress);
        
        // 中间圆盘分隔线
        drawDiscDividers(buffer, middleCenterX, centerY, DISC_RADIUS, middleModes.size(), animProgress);
        
        // 右圆盘分隔线
        drawDiscDividers(buffer, rightCenterX, centerY, DISC_RADIUS, rightModes.size(), animProgress);
        
        BufferUploader.drawWithShader(buffer.end());
        
        // 恢复渲染状态
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        
        // 绘制模式名称
        drawModeNames(graphics, leftCenterX, centerY, DISC_RADIUS, leftModes, animProgress);
        drawModeNames(graphics, middleCenterX, centerY, DISC_RADIUS, middleModes, animProgress);
        drawModeNames(graphics, rightCenterX, centerY, DISC_RADIUS, rightModes, animProgress);
        
        // 绘制悬停提示
        drawHoverText(graphics, leftCenterX, centerY, DISC_RADIUS, leftModes, mouseX, mouseY);
        drawHoverText(graphics, middleCenterX, centerY, DISC_RADIUS, middleModes, mouseX, mouseY);
        drawHoverText(graphics, rightCenterX, centerY, DISC_RADIUS, rightModes, mouseX, mouseY);
        
        ms.popPose();
    }
    
    /**
     * 绘制单个圆盘
     */
    private void drawDisc(BufferBuilder buffer, int centerX, int centerY, float radius, 
                         List<ModeData> modes, int mouseX, int mouseY, float animProgress) {
        if (modes.isEmpty()) return;
        
        float radiusIn = Math.max(0.1f, radius * 0.4f * animProgress);
        float radiusOut = Math.max(0.1f, radius * animProgress);
        int numberOfSlices = modes.size();
        
        // 绘制灰色背景环
        drawSlice(buffer, centerX, centerY, 9, radiusIn, radiusOut, 0, 360, 80, 80, 80, 120);
        
        // 确定选中的项目
        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
        float slot0 = (((0 - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
        if (mouseAngle < slot0) {
            mouseAngle += 360;
        }
        
        int selectedItem = -1;
        for (int i = 0; i < numberOfSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            if (mouseAngle >= sliceBorderLeft && mouseAngle < sliceBorderRight && 
                mouseDistance >= radiusIn && mouseDistance < radiusOut) {
                selectedItem = i;
                break;
            }
        }
        
        // 绘制高亮切片
        for (int i = 0; i < numberOfSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            
            if (selectedItem == i) {
                // 悬停切片 - 蓝色高亮
                drawSlice(buffer, centerX, centerY, 10, radiusIn, radiusOut, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
            } else {
                // 调整索引以匹配实际显示的模式
                int adjusted = ((i + (numberOfSlices / 2 + 1)) % numberOfSlices) - 1;
                adjusted = adjusted == -1 ? numberOfSlices - 1 : adjusted;
                
                if (adjusted >= 0 && adjusted < modes.size() && modes.get(adjusted).isActive()) {
                    // 当前激活的模式 - 绿色高亮
                    drawSlice(buffer, centerX, centerY, 10, radiusIn, radiusOut, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
                }
            }
        }
    }
    
    /**
     * 绘制圆盘分隔线
     */
    private void drawDiscDividers(BufferBuilder buffer, int centerX, int centerY, float radius, 
                                 int sliceCount, float animProgress) {
        if (sliceCount <= 0) return;
        
        float radiusIn = radius * 0.4f * animProgress;
        float radiusOut = radius * animProgress;
        
        for (int i = 0; i < sliceCount; i++) {
            float angle = (float) Math.toRadians((((i - 0.5f) / (float) sliceCount) + 0.25f) * 360);
            float x1 = centerX + radiusIn * (float) Math.cos(angle);
            float y1 = centerY + radiusIn * (float) Math.sin(angle);
            float x2 = centerX + radiusOut * (float) Math.cos(angle);
            float y2 = centerY + radiusOut * (float) Math.sin(angle);
            buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
            buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
        }
    }
    
    /**
     * 绘制模式名称
     */
    private void drawModeNames(GuiGraphics graphics, int centerX, int centerY, float radius, 
                              List<ModeData> modes, float animProgress) {
        if (modes.isEmpty()) return;
        
        float textRadius = radius * 0.7f * animProgress;
        int numberOfSlices = modes.size();
        
        for (int i = 0; i < numberOfSlices; i++) {
            float angle = ((i / (float) numberOfSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfSlices % 2 != 0) {
                angle += Math.PI / numberOfSlices;
            }
            
            Component name = modes.get(i).mode.getTooltip();
            int nameWidth = font.width(name);
            float textX = centerX - nameWidth / 2 + textRadius * (float) Math.cos(angle);
            float textY = centerY - font.lineHeight / 2 + textRadius * (float) Math.sin(angle);
            
            graphics.drawString(font, name, (int) textX, (int) textY, 0xFFFFFF, false);
        }
    }
    
    /**
     * 绘制悬停提示文字
     */
    private void drawHoverText(GuiGraphics graphics, int centerX, int centerY, float radius, 
                              List<ModeData> modes, int mouseX, int mouseY) {
        if (modes.isEmpty()) return;
        
        float radiusIn = radius * 0.4f;
        float radiusOut = radius;
        int numberOfSlices = modes.size();
        
        // 计算鼠标角度和距离
        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
        float slot0 = (((0 - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
        if (mouseAngle < slot0) {
            mouseAngle += 360;
        }
        
        // 确定选中的项目
        int selectedItem = -1;
        for (int i = 0; i < numberOfSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            if (mouseAngle >= sliceBorderLeft && mouseAngle < sliceBorderRight && 
                mouseDistance >= radiusIn && mouseDistance < radiusOut) {
                selectedItem = i;
                break;
            }
        }
        
        // 绘制悬停提示文字
        if (selectedItem >= 0 && selectedItem < modes.size()) {
            // 调整索引以匹配实际显示的模式
            int adjusted = ((selectedItem + (numberOfSlices / 2 + 1)) % numberOfSlices) - 1;
            adjusted = adjusted == -1 ? numberOfSlices - 1 : adjusted;
            
            if (adjusted >= 0 && adjusted < modes.size()) {
                Component name = modes.get(adjusted).name;
                int nameWidth = font.width(name);
                graphics.drawString(font, name, centerX - nameWidth / 2, centerY - font.lineHeight / 2, 0xFFFFFF, false);
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerY = height / 2;
        int leftCenterX = (int) (width / 2 - DISC_SPACING);
        int middleCenterX = width / 2;
        int rightCenterX = (int) (width / 2 + DISC_SPACING);
        
        // 检查左圆盘点击
        if (checkDiscClick((int) mouseX, (int) mouseY, leftCenterX, centerY, DISC_RADIUS, leftModes)) {
            return true;
        }
        
        // 检查中间圆盘点击
        if (checkDiscClick((int) mouseX, (int) mouseY, middleCenterX, centerY, DISC_RADIUS, middleModes)) {
            return true;
        }
        
        // 检查右圆盘点击
        if (checkDiscClick((int) mouseX, (int) mouseY, rightCenterX, centerY, DISC_RADIUS, rightModes)) {
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * 检查是否点击了某个圆盘，并处理点击事件
     */
    private boolean checkDiscClick(int mouseX, int mouseY, int centerX, int centerY, 
                                  float radius, List<ModeData> modes) {
        if (modes.isEmpty()) return false;
        
        float radiusIn = radius * 0.4f;
        float radiusOut = radius;
        int numberOfSlices = modes.size();
        
        // 计算鼠标角度和距离
        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
        float slot0 = (((0 - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
        if (mouseAngle < slot0) {
            mouseAngle += 360;
        }
        
        // 检查是否在圆盘范围内
        if (mouseDistance < radiusIn || mouseDistance > radiusOut) {
            return false;
        }
        
        // 确定点击的切片
        for (int i = 0; i < numberOfSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            
            if (mouseAngle >= sliceBorderLeft && mouseAngle < sliceBorderRight) {
                // 调整索引以匹配实际显示的模式
                int adjusted = ((i + (numberOfSlices / 2 + 1)) % numberOfSlices) - 1;
                adjusted = adjusted == -1 ? numberOfSlices - 1 : adjusted;
                
                if (adjusted >= 0 && adjusted < modes.size()) {
                    handleModeClick(modes.get(adjusted).mode);
                }
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 只允许ESC键关闭屏幕，G键通过松开自动关闭
        if (keyCode == InputConstants.KEY_ESCAPE) {
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
     * 处理模式选择
     * 
     * @param mode 被选中的模式
     */
    private void handleModeClick(ToolMode mode) {
        // 发送数据包到服务端处理模式切换
        com.sorrowmist.useless.networking.ModMessages.sendToServer(
            new com.sorrowmist.useless.networking.ToolModeSwitchPacket(mode)
        );
        
        // 仅在客户端更新UI显示（不修改实际状态）
        modeManager.toggleMode(mode);
        
        // 关闭模式轮盘
        onClose();
    }
    
    /**
     * 绘制圆盘切片
     */
    private void drawSlice(BufferBuilder buffer, float x, float y, float z, float radiusIn, float radiusOut, 
                           float startAngle, float endAngle, int r, int g, int b, int a) {
        float angle = endAngle - startAngle;
        int sections = Math.max(1, Mth.ceil(angle / PRECISION));

        for (int i = 0; i < sections; i++) {
            float angle1 = (float) Math.toRadians(startAngle + (i / (float) sections) * angle);
            float angle2 = (float) Math.toRadians(startAngle + ((i + 1) / (float) sections) * angle);

            float x1In = x + radiusIn * (float) Math.cos(angle1);
            float y1In = y + radiusIn * (float) Math.sin(angle1);
            float x1Out = x + radiusOut * (float) Math.cos(angle1);
            float y1Out = y + radiusOut * (float) Math.sin(angle1);
            float x2In = x + radiusIn * (float) Math.cos(angle2);
            float y2In = y + radiusIn * (float) Math.sin(angle2);
            float x2Out = x + radiusOut * (float) Math.cos(angle2);
            float y2Out = y + radiusOut * (float) Math.sin(angle2);

            buffer.vertex(x1In, y1In, z).color(r, g, b, a).endVertex();
            buffer.vertex(x1Out, y1Out, z).color(r, g, b, a).endVertex();
            buffer.vertex(x2Out, y2Out, z).color(r, g, b, a).endVertex();
            buffer.vertex(x2In, y2In, z).color(r, g, b, a).endVertex();
        }
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