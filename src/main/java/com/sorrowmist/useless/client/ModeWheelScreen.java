package com.sorrowmist.useless.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.FunctionModeTogglePacket;
import com.sorrowmist.useless.network.ToolTypeModeSwitchPacket;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ModeWheelScreen extends Screen {
    private static final float DISC_RADIUS = 60.0f;
    private static final float DISC_SPACING = 150.0f;
    private static final float PRECISION = 5.0f;

    private static final int COL_ACTIVE_R = 80, COL_ACTIVE_G = 180, COL_ACTIVE_B = 80, COL_ACTIVE_A = 140;
    private static final int COL_HOVER_R = 63, COL_HOVER_G = 161, COL_HOVER_B = 191, COL_HOVER_A = 160;

    private static final boolean hasGtceuMod = ModList.get().isLoaded("gtceu");
    private static final boolean hasOmnitoolMod = ModList.get().isLoaded("omnitools");
    private final ItemStack mainHandItem;
    private final List<ModeData> leftModes = new ArrayList<>();
    private final List<ModeData> middleModes = new ArrayList<>();
    private final List<ModeData> rightModes = new ArrayList<>();
    private boolean showMiddleDisc;
    private float totalTime, prevTick, extraTick;

    public ModeWheelScreen(ItemStack mainHandItem) {
        super(Component.literal("Mode Wheel"));
        this.mainHandItem = mainHandItem;
        this.minecraft = Minecraft.getInstance();
        this.showMiddleDisc = false;
        this.loadModesFromEnums();
    }


    private void loadModesFromEnums() {
        this.leftModes.clear();
        this.middleModes.clear();
        this.rightModes.clear();

        EnchantMode currentEnchant = this.mainHandItem.get(UComponents.EnchantModeComponent);
        ToolTypeMode currentTool = this.mainHandItem.get(UComponents.CurrentToolTypeComponent);
        var currentFuncs = UComponentUtils.getFunctionModes(this.mainHandItem);

        // 左：附魔模式
        for (EnchantMode m : EnchantMode.values())
            this.leftModes.add(new ModeData(m, m.getTooltip(), m == currentEnchant));

        for (ToolTypeMode m : ToolTypeMode.values()) {
            boolean shouldAdd = switch (m) {
                // NONE_MODE
                case NONE_MODE -> true;
                // gtceu
                case WRENCH_MODE, SCREWDRIVER_MODE, MALLET_MODE, CROWBAR_MODE, HAMMER_MODE -> hasGtceuMod;
                // omnitool
                case OMNITOOL_MODE -> hasOmnitoolMod;
            };
            // 根据模式类型和模组依赖决定是否添加
            if (shouldAdd) {
                this.middleModes.add(new ModeData(m, m.getTooltip(), m == currentTool));
            }
        }

        // 如果有可用的工具模式，则显示中间轮盘
        this.showMiddleDisc = !this.middleModes.isEmpty();

        // 右：功能模式（分组后的 3 个）
        for (FunctionMode m : FunctionMode.getTooltipDisplayGroups()) {
            this.rightModes.add(new ModeData(
                    m,
                    m.getTooltip(),
                    currentFuncs.contains(m)   // ✔ 集合包含 = 高亮
            ));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        PoseStack ms = g.pose();

        float curr = this.minecraft != null ? this.minecraft.getFrameTimeNs() : 0;
        this.totalTime += (curr + this.extraTick - this.prevTick) / 20f;
        this.extraTick = 0;
        this.prevTick = curr;

        float anim = Mth.clamp(this.totalTime / 0.25f, 0, 1);
        anim = (float) (1 - Math.pow(1 - anim, 3));

        int cy = this.height / 2;
        int centerX = this.width / 2;

        int lx, mx, rx;

        if (this.showMiddleDisc) {
            lx = (int) (centerX - DISC_SPACING);
            mx = centerX;
            rx = (int) (centerX + DISC_SPACING);
        } else {
            lx = (int) (centerX - DISC_SPACING / 2);
            mx = centerX; // 不使用
            rx = (int) (centerX + DISC_SPACING / 2);
        }

        ms.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        this.drawDisc(buf, lx, cy, this.leftModes, mouseX, mouseY, anim);
        if (this.showMiddleDisc)
            this.drawDisc(buf, mx, cy, this.middleModes, mouseX, mouseY, anim);
        this.drawDisc(buf, rx, cy, this.rightModes, mouseX, mouseY, anim);

        BufferUploader.drawWithShader(buf.buildOrThrow());

        buf = t.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        this.drawDividers(buf, lx, cy, this.leftModes.size(), anim);
        if (this.showMiddleDisc)
            this.drawDividers(buf, mx, cy, this.middleModes.size(), anim);
        this.drawDividers(buf, rx, cy, this.rightModes.size(), anim);

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        this.drawModeNames(g, lx, cy, this.leftModes, anim);
        if (this.showMiddleDisc)
            this.drawModeNames(g, mx, cy, this.middleModes, anim);
        this.drawModeNames(g, rx, cy, this.rightModes, anim);

        this.drawHover(g, lx, cy, this.leftModes, mouseX, mouseY);
        if (this.showMiddleDisc)
            this.drawHover(g, mx, cy, this.middleModes, mouseX, mouseY);
        this.drawHover(g, rx, cy, this.rightModes, mouseX, mouseY);

        ms.popPose();
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldCloseOnEsc() {return true;}

    @Override
    public void tick() {
        if (this.totalTime < 0.25f)
            this.extraTick++;

        // G 松开 → 自动关闭
        if (!InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(),
                InputConstants.KEY_G
        )) {
            this.onClose();
        }
    }

    @Override public boolean isPauseScreen() {return false;}

    private void drawDisc(BufferBuilder buf, int cx, int cy,
                          List<ModeData> modes,
                          int mx, int my,
                          float anim) {

        if (modes.isEmpty()) return;

        float rIn = 0.4f * DISC_RADIUS * anim;
        float rOut = 1.0f * DISC_RADIUS * anim;

        // 背景灰环
        this.drawSlice(buf, cx, cy, 9, rIn, rOut, 0, 360, 80, 80, 80, 120);

        int n = modes.size();

        // —— 统一计算选中扇区 —— //
        int sel = this.pickSliceIndex(mx, my, cx, cy, n, rIn, rOut);

        for (int i = 0; i < n; i++) {

            float aL = (((i - .5f) / n) + .25f) * 360;
            float aR = (((i + .5f) / n) + .25f) * 360;

            int adj = ((i + (n / 2 + 1)) % n) - 1;
            if (adj == -1) adj = n - 1;

            boolean active = modes.get(adj).active();
            boolean hover = (sel == i);

            // —— 绿色底色：已激活 —— //
            if (active) {
                this.drawSlice(buf, cx, cy, 10, rIn, rOut, aL, aR,
                               COL_ACTIVE_R, COL_ACTIVE_G, COL_ACTIVE_B, COL_ACTIVE_A
                );
            }

            // —— 蓝色覆盖层：悬停 —— //
            if (hover) {
                this.drawSlice(buf, cx, cy, 11, rIn, rOut, aL, aR,
                               COL_HOVER_R, COL_HOVER_G, COL_HOVER_B, COL_HOVER_A
                );
            }
        }
    }

    /**
     * 统一计算扇区选择逻辑（渲染 / 点击复用）
     */
    private int pickSliceIndex(int mx, int my, int cx, int cy,
                               int n, float rIn, float rOut) {

        double ang = Math.toDegrees(Math.atan2(my - cy, mx - cx));
        double dist = Math.hypot(mx - cx, my - cy);

        if (dist < rIn || dist > rOut) return -1;

        float slot0 = (((0 - .5f) / n) + .25f) * 360;
        if (ang < slot0) ang += 360;

        for (int i = 0; i < n; i++) {
            float aL = (((i - .5f) / n) + .25f) * 360;
            float aR = (((i + .5f) / n) + .25f) * 360;

            if (ang >= aL && ang < aR)
                return i;
        }

        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int cy = this.height / 2;
        int centerX = this.width / 2;

        int lx = this.showMiddleDisc
                ? (int) (centerX - DISC_SPACING)
                : (int) (centerX - DISC_SPACING / 2);

        int rx = this.showMiddleDisc
                ? (int) (centerX + DISC_SPACING)
                : (int) (centerX + DISC_SPACING / 2);

        return this.checkClick((int) mx, (int) my, lx, cy, this.leftModes)
                || (this.showMiddleDisc && this.checkClick((int) mx, (int) my, centerX, cy, this.middleModes))
                || this.checkClick((int) mx, (int) my, rx, cy, this.rightModes)
                || super.mouseClicked(mx, my, btn);
    }

    private boolean checkClick(int mx, int my, int cx, int cy, List<ModeData> modes) {
        if (modes.isEmpty()) return false;

        int n = modes.size();
        int sel = this.pickSliceIndex(mx, my, cx, cy, n, DISC_RADIUS * 0.4f, DISC_RADIUS);
        if (sel < 0) return false;

        int adj = ((sel + (n / 2 + 1)) % n) - 1;
        if (adj == -1) adj = n - 1;

        Enum<?> mode = modes.get(adj).mode();

        this.onModeSelected(mode);

        this.onClose();
        return true;
    }

    private void onModeSelected(Enum<?> mode) {
        if (mode instanceof EnchantMode em) {
            PacketDistributor.sendToServer(new EnchantmentSwitchPacket(em));
        } else if (mode instanceof ToolTypeMode tm) {
            PacketDistributor.sendToServer(new ToolTypeModeSwitchPacket(tm));
        } else if (mode instanceof FunctionMode fm) {
            PacketDistributor.sendToServer(new FunctionModeTogglePacket(fm));
        }
    }

    private void drawDividers(BufferBuilder buf, int cx, int cy, int n, float anim) {
        if (n <= 0) return;

        float rIn = DISC_RADIUS * 0.4f * anim;
        float rOut = DISC_RADIUS * 1.0f * anim;

        for (int i = 0; i < n; i++) {
            float a = (float) Math.toRadians((((i - .5f) / n) + .25f) * 360);

            float x1 = cx + rIn * (float) Math.cos(a);
            float y1 = cy + rIn * (float) Math.sin(a);
            float x2 = cx + rOut * (float) Math.cos(a);
            float y2 = cy + rOut * (float) Math.sin(a);


            buf.addVertex(x1, y1, 11).setColor(200, 200, 200, 100);
            buf.addVertex(x2, y2, 11).setColor(200, 200, 200, 100);
        }
    }

    private void drawModeNames(GuiGraphics g, int cx, int cy,
                               List<ModeData> modes,
                               float anim) {
        if (modes.isEmpty()) return;

        int n = modes.size();
        float tr = DISC_RADIUS * 0.7f * anim;

        for (int i = 0; i < n; i++) {

            float ang = ((i / (float) n) - .25f) * 2 * (float) Math.PI;
            if (n % 2 != 0) ang += (float) (Math.PI / n);

            Component name = modes.get(i).name();
            int w = this.font.width(name);

            float x = cx - w / 2f + tr * (float) Math.cos(ang);
            float y = cy - this.font.lineHeight / 2f + tr * (float) Math.sin(ang);

            g.drawString(this.font, name, (int) x, (int) y, 0xFFFFFF, false);
        }
    }

    private void drawHover(GuiGraphics g, int cx, int cy,
                           List<ModeData> modes,
                           int mx, int my) {
        if (modes.isEmpty()) return;

        int n = modes.size();
        double ang = Math.toDegrees(Math.atan2(my - cy, mx - cx));
        double dist = Math.hypot(mx - cx, my - cy);

        float slot0 = (((0 - .5f) / n) + .25f) * 360;
        if (ang < slot0) ang += 360;

        int sel = -1;

        for (int i = 0; i < n; i++) {
            float aL = (((i - .5f) / n) + .25f) * 360;
            float aR = (((i + .5f) / n) + .25f) * 360;

            if (ang >= aL && ang < aR && dist >= DISC_RADIUS * 0.4f && dist < DISC_RADIUS)
                sel = i;
        }

        if (sel >= 0) {

            int adj = ((sel + (n / 2 + 1)) % n) - 1;
            if (adj == -1) adj = n - 1;

            Component name = modes.get(adj).name();
            int w = this.font.width(name);

            g.drawString(this.font, name,
                         cx - w / 2,
                         cy - this.font.lineHeight / 2,
                         0xFFFFFF,
                         false
            );
        }
    }

    private void drawSlice(BufferBuilder buf,
                           float x, float y, float z,
                           float rIn, float rOut,
                           float start, float end,
                           int r, int g, int b, int a) {
        float angle = end - start;
        int sec = Math.max(1, Mth.ceil(angle / PRECISION));

        for (int i = 0; i < sec; i++) {

            float a1 = (float) Math.toRadians(start + (i / (float) sec) * angle);
            float a2 = (float) Math.toRadians(start + ((i + 1f) / sec) * angle);

            float x1i = x + rIn * (float) Math.cos(a1);
            float y1i = y + rIn * (float) Math.sin(a1);
            float x2i = x + rIn * (float) Math.cos(a2);
            float y2i = y + rIn * (float) Math.sin(a2);

            float x1o = x + rOut * (float) Math.cos(a1);
            float y1o = y + rOut * (float) Math.sin(a1);
            float x2o = x + rOut * (float) Math.cos(a2);
            float y2o = y + rOut * (float) Math.sin(a2);

            buf.addVertex(x1i, y1i, z).setColor(r, g, b, a);
            buf.addVertex(x1o, y1o, z).setColor(r, g, b, a);
            buf.addVertex(x2o, y2o, z).setColor(r, g, b, a);
            buf.addVertex(x2i, y2i, z).setColor(r, g, b, a);
        }
    }

    private record ModeData(Enum<?> mode, Component name, boolean active) {}
}