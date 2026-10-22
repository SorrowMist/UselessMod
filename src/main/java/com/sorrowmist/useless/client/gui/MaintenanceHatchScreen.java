package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import com.sorrowmist.useless.content.multiblock.MaintenanceCalibrationRules;
import com.sorrowmist.useless.network.MaintenanceCalibrationActionPacket;
import com.sorrowmist.useless.network.MaintenanceCalibrationSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 维护仓的校准小游戏界面。
 *
 * <p>指针位置由「本轮已流逝的 tick」推导，客户端与服务端共用
 * {@link MaintenanceCalibrationRules} 的公式。客户端只负责绘制与上报点击时刻，
 * 是否命中完全由服务端判定，因此这里的本地推进仅用于表现。</p>
 *
 * <p>计时基准来自服务端快照：收到快照时记下当时的本地 tick 与服务器流逝值，
 * 之后每帧用本地 tick 的增量继续推进。这样即使客户端与服务器的世界时间存在偏差，
 * 指针也能与服务端保持一致，偏差只来自网络延迟。</p>
 *
 * <h2>版面</h2>
 *
 * <p>自上而下依次是：标题分隔线、状态徽章、三轮卡片、校准轨道、刻度、读数条、
 * 操作提示、底部操作按钮。每一层只承担一件事，避免信息挤在同一行。</p>
 *
 * <h2>绘制方式</h2>
 *
 * <p>界面不依赖贴图，全部由 {@code GuiGraphics#fill} 堆叠而成。竖直渐变通过逐行取色实现，
 * 光晕通过多层半透明矩形向外扩散实现。动画只由本地 {@code gameTime} 驱动，
 * 不额外持有计时器，因此界面关闭后不会残留动画状态。</p>
 */
public final class MaintenanceHatchScreen extends AbstractContainerScreen<MaintenanceHatchMenu> {

    private static final int PANEL_WIDTH = 208;
    private static final int PANEL_HEIGHT = 172;

    /** 内容区左右留白，卡片与分隔线都以它对齐。 */
    private static final int CONTENT_X = 12;

    private static final int BADGE_Y = 23;
    private static final int BADGE_HEIGHT = 16;

    private static final int CARD_Y = 43;
    private static final int CARD_WIDTH = 60;
    private static final int CARD_HEIGHT = 24;
    private static final int CARD_GAP = 2;

    private static final int TRACK_X = 20;
    private static final int TRACK_Y = 77;
    private static final int TRACK_WIDTH = PANEL_WIDTH - TRACK_X * 2;
    private static final int TRACK_HEIGHT = 20;
    /** 轨道上下各留出的点击余量，让指针端头也算在热区内。 */
    private static final int TRACK_HIT_PADDING = 10;

    private static final int SCALE_Y = 101;
    private static final int READOUT_Y = 114;
    private static final int READOUT_HEIGHT = 12;
    private static final int HINT_Y = 130;

    private static final int BUTTON_X = 40;
    private static final int BUTTON_Y = 144;
    private static final int BUTTON_WIDTH = 128;
    private static final int BUTTON_HEIGHT = 18;

    /** 轨道外描边：比内底深一档，让赛道从面板里凹下去。 */
    private static final int TRACK_SHADOW_COLOR = 0xFF5A5F73;
    private static final int TRACK_BORDER_COLOR = 0xFF6D7287;
    private static final int TRACK_BORDER_HOVER_COLOR = 0xFF41435A;
    /** 轨道内底的竖直渐变两端：上端略深、中部最亮，制造凹槽的纵深。 */
    private static final int TRACK_BED_DARK = 0xFFC6C9D6;
    private static final int TRACK_BED_LIGHT = 0xFFE2E4EC;
    /** 每 10% 一道的刻度，半透明叠加，只做参照不做干扰。 */
    private static final int TRACK_TICK_COLOR = 0x2A41435A;
    /** 每 50% 一道的主刻度。 */
    private static final int TRACK_TICK_MAJOR_COLOR = 0x5041435A;
    /** 中点基准线：目标区之上仍能看见，帮助估读位置。 */
    private static final int TRACK_MIDLINE_COLOR = 0x3341435A;

    /**
     * 目标区由外到内的色阶。
     *
     * <p>用一条对称色阶而非单色块，是为了让命中区在静止时也有聚焦的方向感；
     * 中央最亮处即视觉中心，与判定使用的中点一致。</p>
     */
    private static final int[] TARGET_RAMP = {
            0xFF2C6B36, 0xFF3F8F4A, 0xFF57AB62, 0xFF7ACB84, 0xFF57AB62, 0xFF3F8F4A, 0xFF2C6B36
    };
    /** 目标区左右边缘的高光，窄区时也保证有一条可见边界。 */
    private static final int TARGET_EDGE_COLOR = 0xFFB7E8BE;
    /** 目标区向外扩散的光晕底色。 */
    private static final int TARGET_GLOW_COLOR = 0xFF57AB62;

    private static final int POINTER_COLOR = 0xFFCE2401;
    private static final int POINTER_INNER_COLOR = 0xFFFFF1EC;

    private static final int BADGE_BORDER_COLOR = 0xFF8B8FA3;
    private static final int BADGE_FILL_COLOR = 0xFFF2F2F2;
    private static final int BADGE_IDLE_COLOR = 0xFF6D7287;
    private static final int BADGE_RUNNING_COLOR = 0xFF2E6DB4;
    private static final int BADGE_COMPLETED_COLOR = 0xFF2E7D32;

    private static final int CARD_IDLE_FILL = 0xFFE4E5EA;
    private static final int CARD_ACTIVE_FILL = 0xFFFBF3DC;
    private static final int CARD_DONE_FILL = 0xFFE3F2E5;
    /** 卡片顶部一行浅色高光，给扁平方块一点厚度。 */
    private static final int CARD_HIGHLIGHT_COLOR = 0x66FFFFFF;

    private static final int PIP_DONE_BORDER = 0xFF2E7D32;
    private static final int PIP_ACTIVE_BORDER = 0xFFC79A2E;
    private static final int PIP_IDLE_BORDER = 0xFFB4B7C6;
    private static final int BLOCK_DONE_FILL = 0xFF57AB62;
    private static final int BLOCK_ACTIVE_FILL = 0xFFF6E3B4;
    private static final int BLOCK_IDLE_FILL = 0xFFD5D7E0;
    private static final int CHECK_COLOR = 0xFFF2F2F2;

    private static final int BUTTON_BORDER_COLOR = 0xFF6D7287;
    private static final int BUTTON_FILL_COLOR = 0xFFE4E5EA;
    private static final int BUTTON_BORDER_HOVER = 0xFF41435A;
    private static final int BUTTON_FILL_HOVER = 0xFFF2F2F2;
    private static final int BUTTON_TEXT_COLOR = 0xFF413F54;

    private byte phase = MaintenanceCalibrationRules.PHASE_IDLE;
    private int roundsCleared;
    private float targetCenter = 0.5F;

    /** 快照下发时刻的服务器流逝值。 */
    private long baseElapsed;
    /** 收到快照时的本地 tick，用于把本地时间换算成流逝值。 */
    private long baseLocalTick;

    /** 命中提示的到期本地 tick；到期前轨道泛绿、提示行改为命中反馈。 */
    private long hitFlashUntil = Long.MIN_VALUE;
    /** 失误提示的到期本地 tick；失误会清空进度，因此用红色区分。 */
    private long missFlashUntil = Long.MIN_VALUE;

    public MaintenanceHatchScreen(MaintenanceHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = -1000;
        titleLabelX = CONTENT_X - 4;
        titleLabelY = 7;
    }

    /** 服务端快照到达：重置本地计时基准与目标区。 */
    public void receiveSync(MaintenanceCalibrationSyncPacket packet) {
        if (!packet.pos().equals(menu.getBlockPos())) {
            return;
        }
        boolean wasRunning = phase == MaintenanceCalibrationRules.PHASE_RUNNING;
        // 轮数增加且尚未全部完成，说明刚刚命中一轮，给一次短暂的视觉反馈。
        if (packet.roundsCleared() > roundsCleared
                && packet.roundsCleared() < MaintenanceCalibrationRules.ROUNDS_TOTAL) {
            hitFlashUntil = localTick() + 25L;
        }
        // 进行中轮数回退，说明刚才那一下没有命中，服务端已把进度清零。
        // 重新开始时客户端处于空闲或完成态，不会命中该分支。
        if (wasRunning && packet.phase() == MaintenanceCalibrationRules.PHASE_RUNNING
                && packet.roundsCleared() < roundsCleared) {
            missFlashUntil = localTick() + 20L;
        }
        phase = packet.phase();
        roundsCleared = packet.roundsCleared();
        targetCenter = packet.targetCenter();
        baseElapsed = packet.elapsedTicks();
        baseLocalTick = localTick();
    }

    private long localTick() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    /** 当前指针应在的位置；未开始或已完成时返回 -1。 */
    private float pointerPosition() {
        if (phase != MaintenanceCalibrationRules.PHASE_RUNNING) {
            return -1.0F;
        }
        return MaintenanceCalibrationRules.pointerAt(elapsedNow());
    }

    /** 按本地 tick 外推得到的流逝值。 */
    private long elapsedNow() {
        return baseElapsed + Math.max(0L, localTick() - baseLocalTick);
    }

    /** 上报给服务端的流逝值。 */
    private long reportedElapsed() {
        return elapsedNow();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        drawHeaderRule(graphics);
        drawStatusBadge(graphics);
        drawRoundCards(graphics);
        drawTrack(graphics, mouseX, mouseY);
        drawScale(graphics);
        drawReadout(graphics);
        drawHint(graphics);
        drawActionButton(graphics, mouseX, mouseY);
    }

    /** 0..10 的往返三角波，用本地 tick 驱动，避免引入额外的动画计时器。 */
    private int pulse() {
        int t = (int) Math.floorMod(localTick(), 20L);
        return t < 10 ? t : 20 - t;
    }

    /** 标题下的分隔线：整体一条暗线，中段提亮，让分界从面板背景里浮出来。 */
    private void drawHeaderRule(GuiGraphics graphics) {
        int left = leftPos + CONTENT_X;
        int right = leftPos + PANEL_WIDTH - CONTENT_X;
        int y = topPos + 18;
        graphics.fill(left, y, right, y + 1, MachineScreenStyle.FIELD_BORDER_COLOR);
        int glowLeft = leftPos + PANEL_WIDTH / 2 - 42;
        graphics.fill(glowLeft, y, glowLeft + 84, y + 1, MachineScreenStyle.HIGHLIGHT_COLOR);
    }

    /**
     * 居中的状态徽章：呼吸圆点 + 状态色文字，两侧以渐隐横线接到内容区边缘。
     *
     * <p>渐隐线的作用是把孤悬在中央的徽章纳入版面结构，同时不引入额外边框。</p>
     */
    private void drawStatusBadge(GuiGraphics graphics) {
        Component status = statusText();
        int accent = statusColor();
        int textWidth = font.width(status);
        int badgeWidth = textWidth + 24;
        int badgeLeft = leftPos + (PANEL_WIDTH - badgeWidth) / 2;
        int badgeTop = topPos + BADGE_Y;
        int badgeRight = badgeLeft + badgeWidth;
        int badgeBottom = badgeTop + BADGE_HEIGHT;

        drawFadingRule(graphics, leftPos + CONTENT_X, badgeLeft - 6,
                badgeTop + BADGE_HEIGHT / 2, accent, 1);
        drawFadingRule(graphics, badgeRight + 6, leftPos + PANEL_WIDTH - CONTENT_X,
                badgeTop + BADGE_HEIGHT / 2, accent, -1);

        // 四角各缺 1 像素，用两层错位矩形拼出圆角外框。
        graphics.fill(badgeLeft + 1, badgeTop, badgeRight - 1, badgeBottom, BADGE_BORDER_COLOR);
        graphics.fill(badgeLeft, badgeTop + 1, badgeRight, badgeBottom - 1, BADGE_BORDER_COLOR);
        graphics.fill(badgeLeft + 1, badgeTop + 1, badgeRight - 1, badgeBottom - 1, BADGE_FILL_COLOR);
        graphics.fill(badgeLeft + 1, badgeTop + 1, badgeRight - 1, badgeTop + 2, CARD_HIGHLIGHT_COLOR);

        int dotX = badgeLeft + 9;
        int dotY = badgeTop + BADGE_HEIGHT / 2;
        int halo = phase == MaintenanceCalibrationRules.PHASE_RUNNING ? 3 + pulse() / 3 : 3;
        graphics.fill(dotX - halo, dotY - halo, dotX + halo, dotY + halo, withAlpha(accent, 0x2E));
        graphics.fill(dotX - 2, dotY - 2, dotX + 2, dotY + 2, accent);
        graphics.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, withAlpha(0xFFFFFFFF, 0x90));

        graphics.drawString(font, status, badgeLeft + 16, badgeTop + 4, accent, false);
    }

    /**
     * 从 {@code from} 向 {@code to} 画一条透明度递减的横线。
     *
     * <p>{@code direction} 为 1 时由左向右画，为 -1 时由右向左画；两端的淡出方向
     * 因此始终背向徽章，不会出现一段突然截断的实线。</p>
     */
    private void drawFadingRule(GuiGraphics graphics, int from, int to, int y,
                                int color, int direction) {
        int length = direction > 0 ? to - from : from - to;
        int step = length / 10;
        if (step <= 0) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            int alpha = 0x66 - i * 0x09;
            if (alpha <= 0) {
                return;
            }
            int start = direction > 0 ? from + i * step : from - (i + 1) * step;
            graphics.fill(start, y, start + step, y + 1, withAlpha(color, alpha));
        }
    }

    /** 三个轮次卡片：完成显示对勾，进行中的一轮描边发光，未到的淡化。 */
    private void drawRoundCards(GuiGraphics graphics) {
        int rounds = MaintenanceCalibrationRules.ROUNDS_TOTAL;
        int total = rounds * CARD_WIDTH + (rounds - 1) * CARD_GAP;
        int startX = leftPos + (PANEL_WIDTH - total) / 2;

        for (int i = 0; i < rounds; i++) {
            boolean done = i < roundsCleared;
            boolean active = !done && i == roundsCleared
                    && phase == MaintenanceCalibrationRules.PHASE_RUNNING;
            drawRoundCard(graphics, startX + i * (CARD_WIDTH + CARD_GAP), topPos + CARD_Y,
                    i, done, active);
        }
    }

    private void drawRoundCard(GuiGraphics graphics, int x, int y,
                               int index, boolean done, boolean active) {
        int border = done ? PIP_DONE_BORDER
                : active ? PIP_ACTIVE_BORDER : PIP_IDLE_BORDER;
        int fill = done ? CARD_DONE_FILL
                : active ? CARD_ACTIVE_FILL : CARD_IDLE_FILL;
        int right = x + CARD_WIDTH;
        int bottom = y + CARD_HEIGHT;

        // 进行中的卡片外圈随呼吸波扩张，提示本轮正在计时。
        if (active) {
            int alpha = 0x30 + pulse() * 0x08;
            graphics.fill(x - 1, y - 1, right + 1, bottom + 1, withAlpha(PIP_ACTIVE_BORDER, alpha));
        }

        graphics.fill(x, y, right, bottom, border);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
        graphics.fill(x + 1, y + 1, right - 1, y + 2, CARD_HIGHLIGHT_COLOR);

        // 底部状态条：把「已通过 / 进行中 / 未开始」压缩成一条颜色，扫一眼即可分辨。
        int barColor = done ? PIP_DONE_BORDER
                : active ? PIP_ACTIVE_BORDER : withAlpha(PIP_IDLE_BORDER, 0x80);
        graphics.fill(x + 3, bottom - 3, right - 3, bottom - 1, barColor);

        // 左侧序号方块：完成时换成对勾，未完成时显示轮次数字。
        int blockX = x + 6;
        int blockY = y + 6;
        int blockBorder = done ? PIP_DONE_BORDER
                : active ? PIP_ACTIVE_BORDER : PIP_IDLE_BORDER;
        int blockFill = done ? BLOCK_DONE_FILL
                : active ? BLOCK_ACTIVE_FILL : BLOCK_IDLE_FILL;
        graphics.fill(blockX, blockY, blockX + 12, blockY + 12, blockBorder);
        graphics.fill(blockX + 1, blockY + 1, blockX + 11, blockY + 11, blockFill);

        if (done) {
            drawCheckMark(graphics, blockX + 2, blockY + 2, CHECK_COLOR);
        } else {
            Component number = Component.literal(Integer.toString(index + 1));
            graphics.drawString(font, number,
                    blockX + (12 - font.width(number)) / 2, blockY + 2,
                    active ? 0xFF6B5216 : MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }

        // 右侧给出本轮的命中半宽，让玩家事先知道目标在收窄。
        int halfPercent = Math.round(MaintenanceCalibrationRules.halfWidthFor(index) * 100.0F);
        Component target = Component.translatable(
                "gui.useless_mod.maintenance_hatch.target", halfPercent);
        int textLeft = x + 20;
        int textArea = CARD_WIDTH - 24;
        graphics.drawString(font, target,
                textLeft + (textArea - font.width(target)) / 2, y + 8,
                done ? 0xFF2E7D32 : active ? MachineScreenStyle.TEXT_COLOR
                        : MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    /** 在 12x12 的序号方块内画一个由两段斜笔组成的对勾。 */
    private void drawCheckMark(GuiGraphics graphics, int x, int y, int color) {
        // 下降笔
        graphics.fill(x + 1, y + 3, x + 3, y + 5, color);
        graphics.fill(x + 2, y + 4, x + 4, y + 6, color);
        graphics.fill(x + 3, y + 5, x + 5, y + 7, color);
        // 上升笔
        graphics.fill(x + 4, y + 4, x + 6, y + 6, color);
        graphics.fill(x + 5, y + 2, x + 7, y + 5, color);
        graphics.fill(x + 6, y + 0, x + 8, y + 3, color);
    }

    /** 轨道外框、内底渐变、刻度、目标区与指针。 */
    private void drawTrack(GuiGraphics graphics, int mouseX, int mouseY) {
        int trackLeft = leftPos + TRACK_X;
        int trackTop = topPos + TRACK_Y;
        int trackRight = trackLeft + TRACK_WIDTH;
        int trackBottom = trackTop + TRACK_HEIGHT;
        boolean hovered = inside(mouseX, mouseY, TRACK_X, TRACK_Y - TRACK_HIT_PADDING,
                TRACK_WIDTH, TRACK_HEIGHT + TRACK_HIT_PADDING * 2);
        boolean running = phase == MaintenanceCalibrationRules.PHASE_RUNNING;
        boolean hitFlashing = localTick() < hitFlashUntil;
        boolean missFlashing = localTick() < missFlashUntil;

        // 悬停或进行中时在轨道外围铺一层光晕，明确这里是可操作区域。
        if (hovered || running) {
            int accent = hovered ? TRACK_BORDER_HOVER_COLOR : BADGE_RUNNING_COLOR;
            graphics.fill(trackLeft - 4, trackTop - 4, trackRight + 4, trackBottom + 4,
                    withAlpha(accent, hovered ? 0x1E : 0x12));
            graphics.fill(trackLeft - 3, trackTop - 3, trackRight + 3, trackBottom + 3,
                    withAlpha(accent, hovered ? 0x30 : 0x1C));
        }

        graphics.fill(trackLeft - 2, trackTop - 2, trackRight + 2, trackBottom + 2,
                TRACK_SHADOW_COLOR);
        graphics.fill(trackLeft - 1, trackTop - 1, trackRight + 1, trackBottom + 1,
                hovered ? TRACK_BORDER_HOVER_COLOR : TRACK_BORDER_COLOR);
        drawTrackBed(graphics, trackLeft, trackTop, trackRight, trackBottom);

        // 刻度从下沿向上生长，之后被目标区覆盖的部分自然被遮住，不会浮在绿区上。
        for (int i = 1; i < 10; i++) {
            int x = trackLeft + Math.round(TRACK_WIDTH * i / 10.0F);
            boolean major = i % 5 == 0;
            int tickHeight = major ? 8 : 4;
            graphics.fill(x, trackBottom - tickHeight - 1, x + 1, trackBottom - 1,
                    major ? TRACK_TICK_MAJOR_COLOR : TRACK_TICK_COLOR);
        }

        if (running) {
            float halfWidth = MaintenanceCalibrationRules.halfWidthFor(roundsCleared);
            int targetLeft = trackLeft + Math.round((targetCenter - halfWidth) * TRACK_WIDTH);
            int targetRight = trackLeft + Math.round((targetCenter + halfWidth) * TRACK_WIDTH);
            drawTargetZone(graphics,
                    Math.max(trackLeft, targetLeft),
                    Math.min(trackRight, targetRight),
                    trackTop, trackBottom, hitFlashing);
        }

        // 中点基准线画在目标区之后：它不表示判定区，只用来估读大致位置。
        int midX = trackLeft + TRACK_WIDTH / 2;
        graphics.fill(midX, trackTop + 1, midX + 1, trackBottom - 1, TRACK_MIDLINE_COLOR);

        float pointer = pointerPosition();
        if (pointer >= 0.0F) {
            drawPointer(graphics, trackLeft + Math.round(pointer * TRACK_WIDTH),
                    trackLeft, trackRight, trackTop, trackBottom, hitFlashing);
        }

        // 命中与失误都在轨道上整体叠一层色，让反馈不局限于文字行。
        if (hitFlashing || missFlashing) {
            long until = hitFlashing ? hitFlashUntil : missFlashUntil;
            int remain = (int) Math.max(0L, until - localTick());
            int alpha = Math.min(0x50, remain * 4);
            graphics.fill(trackLeft, trackTop, trackRight, trackBottom,
                    withAlpha(hitFlashing ? BADGE_COMPLETED_COLOR : POINTER_COLOR, alpha));
        }
    }

    /**
     * 逐行插值绘制轨道内底。
     *
     * <p>最亮处位于从上往下约 40% 的高度，符合光从上方照入凹槽的观感；
     * 上下两端同时向深色过渡，避免出现一条平涂的色带。</p>
     */
    private void drawTrackBed(GuiGraphics graphics, int left, int top, int right, int bottom) {
        int height = bottom - top;
        for (int i = 0; i < height; i++) {
            float t = (float) i / Math.max(1, height - 1);
            float brightness = 1.0F - Math.abs(t - 0.4F) * 1.4F;
            graphics.fill(left, top + i, right, top + i + 1,
                    lerpColor(TRACK_BED_DARK, TRACK_BED_LIGHT, brightness));
        }
    }

    /**
     * 以对称色阶填充目标区，并向两侧扩散一层光晕。
     *
     * <p>上下边缘分别加高光与压暗，使目标区看起来是从轨道上浮起的一条带子，
     * 而不是一块直接涂上去的色块。</p>
     */
    private void drawTargetZone(GuiGraphics graphics, int left, int right,
                                int top, int bottom, boolean flashing) {
        int width = right - left;
        if (width <= 0) {
            return;
        }
        int baseGlow = flashing ? 0x34 : 0x1C;
        for (int i = 1; i <= 3; i++) {
            int alpha = baseGlow / i;
            graphics.fill(left - i, top - 1, left, bottom + 1,
                    withAlpha(TARGET_GLOW_COLOR, alpha));
            graphics.fill(right, top - 1, right + i, bottom + 1,
                    withAlpha(TARGET_GLOW_COLOR, alpha));
        }

        int segments = Math.min(TARGET_RAMP.length, Math.max(1, width));
        for (int i = 0; i < segments; i++) {
            int segmentLeft = left + width * i / segments;
            int segmentRight = left + width * (i + 1) / segments;
            graphics.fill(segmentLeft, top, segmentRight, bottom,
                    TARGET_RAMP[i * TARGET_RAMP.length / segments]);
        }

        graphics.fill(left, top, right, top + 1, 0x88FFFFFF);
        graphics.fill(left, bottom - 1, right, bottom, 0x44000000);
        graphics.fill(left, top, left + 1, bottom, TARGET_EDGE_COLOR);
        graphics.fill(right - 1, top, right, bottom, TARGET_EDGE_COLOR);
        int centerX = (left + right) / 2;
        graphics.fill(centerX, top + 1, centerX + 1, bottom - 1, 0x99FFFFFF);
    }

    /**
     * 指针：方向尾迹 + 光晕 + 赤红竖条 + 上下箭头。
     *
     * <p>光晕让指针在绿色目标区上方依然可辨；上下两个箭头越过轨道边缘，
     * 给出一个不受目标区颜色影响的视觉锚点，也便于在高速往返时定位。</p>
     */
    private void drawPointer(GuiGraphics graphics, int pointerX,
                             int trackLeft, int trackRight,
                             int trackTop, int trackBottom, boolean flashing) {
        int top = trackTop - 4;
        int bottom = trackBottom + 4;

        // 尾迹沿运动反方向拖出，长度随速度固定，透明度逐段衰减。
        int direction = pointerDirection();
        if (direction != 0) {
            for (int i = 1; i <= 4; i++) {
                int trailX = pointerX - direction * i * 2;
                if (trailX <= trackLeft || trailX >= trackRight) {
                    break;
                }
                graphics.fill(trailX, trackTop + 4, trailX + 1, trackBottom - 4,
                        withAlpha(POINTER_COLOR, 0x48 / i));
            }
        }

        int glowAlpha = flashing ? 0x7A : 0x42;
        graphics.fill(pointerX - 6, top + 3, pointerX + 7, bottom - 3,
                withAlpha(POINTER_COLOR, glowAlpha / 3));
        graphics.fill(pointerX - 4, top + 1, pointerX + 5, bottom - 1,
                withAlpha(POINTER_COLOR, glowAlpha / 2));
        graphics.fill(pointerX - 2, top, pointerX + 3, bottom, withAlpha(POINTER_COLOR, glowAlpha));

        graphics.fill(pointerX - 1, top, pointerX + 1, bottom, POINTER_COLOR);

        // 顶部箭头：自下而上逐行收窄。
        graphics.fill(pointerX - 3, top, pointerX + 4, top + 1, POINTER_COLOR);
        graphics.fill(pointerX - 2, top - 1, pointerX + 3, top, POINTER_COLOR);
        graphics.fill(pointerX - 1, top - 2, pointerX + 2, top - 1, POINTER_COLOR);
        // 底部倒三角：与顶部对称，指针越过下沿时仍指向轨道。
        graphics.fill(pointerX - 3, bottom - 1, pointerX + 4, bottom, POINTER_COLOR);
        graphics.fill(pointerX - 2, bottom, pointerX + 3, bottom + 1, POINTER_COLOR);
        graphics.fill(pointerX - 1, bottom + 1, pointerX + 2, bottom + 2, POINTER_COLOR);

        // 中段一条亮线，让指针在光晕里不糊成一团色块。
        graphics.fill(pointerX, top + 4, pointerX + 1, bottom - 4, POINTER_INNER_COLOR);
    }

    /** 指针的即时运动方向：1 向右、-1 向左、0 静止或不在运行中。 */
    private int pointerDirection() {
        if (phase != MaintenanceCalibrationRules.PHASE_RUNNING) {
            return 0;
        }
        long elapsed = elapsedNow();
        float delta = MaintenanceCalibrationRules.pointerAt(elapsed)
                - MaintenanceCalibrationRules.pointerAt(elapsed - 1L);
        if (Math.abs(delta) < 1.0E-4F) {
            return 0;
        }
        return delta > 0.0F ? 1 : -1;
    }

    /** 轨道刻度标签。 */
    private void drawScale(GuiGraphics graphics) {
        int trackLeft = leftPos + TRACK_X;
        int trackRight = trackLeft + TRACK_WIDTH;
        int y = topPos + SCALE_Y;
        int color = MachineScreenStyle.SUBTLE_TEXT_COLOR;

        graphics.drawString(font, "0%", trackLeft, y, color, false);
        Component middle = Component.literal("50%");
        graphics.drawString(font, middle, trackLeft + (TRACK_WIDTH - font.width(middle)) / 2,
                y, color, false);
        Component end = Component.literal("100%");
        graphics.drawString(font, end, trackRight - font.width(end), y, color, false);
    }

    /** 读数条：以浅色胶囊呈现当前轮次与指针位置，未开始时给出总量提示。 */
    private void drawReadout(GuiGraphics graphics) {
        Component text;
        int color;
        if (phase == MaintenanceCalibrationRules.PHASE_RUNNING) {
            int position = Math.round(pointerPosition() * 100.0F);
            text = Component.translatable("gui.useless_mod.maintenance_hatch.position",
                    roundsCleared + 1, MaintenanceCalibrationRules.ROUNDS_TOTAL, position);
            color = MachineScreenStyle.TEXT_COLOR;
        } else if (phase == MaintenanceCalibrationRules.PHASE_COMPLETED) {
            text = Component.translatable("gui.useless_mod.maintenance_hatch.readout_done",
                    MaintenanceCalibrationRules.ROUNDS_TOTAL);
            color = BADGE_COMPLETED_COLOR;
        } else {
            text = Component.translatable("gui.useless_mod.maintenance_hatch.readout_idle",
                    MaintenanceCalibrationRules.ROUNDS_TOTAL);
            color = MachineScreenStyle.MUTED_TEXT_COLOR;
        }

        int width = font.width(text) + 16;
        int left = leftPos + (PANEL_WIDTH - width) / 2;
        int top = topPos + READOUT_Y;
        int right = left + width;
        int bottom = top + READOUT_HEIGHT;

        // 与状态徽章相同的圆角外框，保持两处「胶囊」元素风格一致。
        graphics.fill(left + 1, top, right - 1, bottom, MachineScreenStyle.FIELD_BORDER_COLOR);
        graphics.fill(left, top + 1, right, bottom - 1, MachineScreenStyle.FIELD_BORDER_COLOR);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, MachineScreenStyle.FIELD_FILL_COLOR);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, CARD_HIGHLIGHT_COLOR);

        graphics.drawString(font, text, left + 8, top + 2, color, false);
    }

    /** 操作提示；命中或失误后短暂切换为对应的反馈文案。 */
    private void drawHint(GuiGraphics graphics) {
        boolean hitFlashing = localTick() < hitFlashUntil;
        boolean missFlashing = localTick() < missFlashUntil;
        Component hint;
        int color;
        if (hitFlashing) {
            hint = Component.translatable("gui.useless_mod.maintenance_hatch.hit");
            color = BADGE_COMPLETED_COLOR;
        } else if (missFlashing) {
            hint = Component.translatable("gui.useless_mod.maintenance_hatch.miss");
            color = POINTER_COLOR;
        } else if (phase == MaintenanceCalibrationRules.PHASE_RUNNING) {
            hint = Component.translatable("gui.useless_mod.maintenance_hatch.hint");
            color = MachineScreenStyle.SUBTLE_TEXT_COLOR;
        } else if (phase == MaintenanceCalibrationRules.PHASE_COMPLETED) {
            hint = Component.translatable("gui.useless_mod.maintenance_hatch.restart_hint");
            color = MachineScreenStyle.SUBTLE_TEXT_COLOR;
        } else {
            hint = Component.translatable("gui.useless_mod.maintenance_hatch.start_hint");
            color = MachineScreenStyle.SUBTLE_TEXT_COLOR;
        }

        // 左侧小方块与文字一起居中，避免提示行单独偏右。
        int markerWidth = 5;
        int gap = 4;
        int total = markerWidth + gap + font.width(hint);
        int x = leftPos + (PANEL_WIDTH - total) / 2;
        int y = topPos + HINT_Y;
        int markerAlpha = hitFlashing || missFlashing ? 0x80 + pulse() * 0x07 : 0xFF;
        graphics.fill(x, y + 3, x + markerWidth, y + 8, withAlpha(color, markerAlpha));
        graphics.fill(x + 1, y + 4, x + markerWidth - 1, y + 7,
                withAlpha(0xFFFFFFFF, hitFlashing || missFlashing ? 0xB0 : 0x50));
        graphics.drawString(font, hint, x + markerWidth + gap, y, color, false);
    }

    /** 底部操作按钮：悬停外发光，左侧状态色条，右侧方向箭头。 */
    private void drawActionButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, BUTTON_X, BUTTON_Y,
                BUTTON_WIDTH, BUTTON_HEIGHT);
        int x = leftPos + BUTTON_X;
        int y = topPos + BUTTON_Y;
        int right = x + BUTTON_WIDTH;
        int bottom = y + BUTTON_HEIGHT;
        int accent = statusColor();

        if (hovered) {
            graphics.fill(x - 2, y - 2, right + 2, bottom + 2, withAlpha(accent, 0x1E));
            graphics.fill(x - 1, y - 1, right + 1, bottom + 1, withAlpha(accent, 0x38));
        }

        graphics.fill(x, y, right, bottom, hovered ? BUTTON_BORDER_HOVER : BUTTON_BORDER_COLOR);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1,
                hovered ? BUTTON_FILL_HOVER : BUTTON_FILL_COLOR);
        graphics.fill(x + 1, y + 1, right - 1, y + 2, CARD_HIGHLIGHT_COLOR);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, 0x22000000);

        graphics.fill(x + 1, y + 1, x + 5, bottom - 1, accent);
        graphics.fill(x + 2, y + 2, x + 4, bottom - 2, withAlpha(0xFFFFFFFF, 0x30));

        int arrowX = right - 12;
        int arrowY = y + BUTTON_HEIGHT / 2;
        int arrowColor = hovered ? accent : MachineScreenStyle.SUBTLE_TEXT_COLOR;
        graphics.fill(arrowX, arrowY - 3, arrowX + 1, arrowY + 3, arrowColor);
        graphics.fill(arrowX + 1, arrowY - 2, arrowX + 2, arrowY + 2, arrowColor);
        graphics.fill(arrowX + 2, arrowY - 1, arrowX + 3, arrowY + 1, arrowColor);

        Component label = buttonLabel();
        int textAreaLeft = x + 5;
        int textAreaRight = right - 14;
        graphics.drawString(font, label,
                textAreaLeft + (textAreaRight - textAreaLeft - font.width(label)) / 2,
                y + (BUTTON_HEIGHT - 8) / 2, hovered ? 0xFF2E2C3D : BUTTON_TEXT_COLOR, false);
    }

    private Component buttonLabel() {
        String key = switch (phase) {
            case MaintenanceCalibrationRules.PHASE_RUNNING ->
                    "gui.useless_mod.maintenance_hatch.button.lock";
            case MaintenanceCalibrationRules.PHASE_COMPLETED ->
                    "gui.useless_mod.maintenance_hatch.button.restart";
            default -> "gui.useless_mod.maintenance_hatch.button.start";
        };
        return Component.translatable(key);
    }

    private int statusColor() {
        return switch (phase) {
            case MaintenanceCalibrationRules.PHASE_RUNNING -> BADGE_RUNNING_COLOR;
            case MaintenanceCalibrationRules.PHASE_COMPLETED -> BADGE_COMPLETED_COLOR;
            default -> BADGE_IDLE_COLOR;
        };
    }

    private Component statusText() {
        return switch (phase) {
            case MaintenanceCalibrationRules.PHASE_RUNNING ->
                    Component.translatable("gui.useless_mod.maintenance_hatch.running");
            case MaintenanceCalibrationRules.PHASE_COMPLETED ->
                    Component.translatable("gui.useless_mod.maintenance_hatch.completed");
            default -> Component.translatable("gui.useless_mod.maintenance_hatch.idle");
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideHitArea(mouseX, mouseY)) {
            act();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            act();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 底部按钮与轨道（含指针端头）都算热区，点哪儿都能操作。 */
    private boolean isInsideHitArea(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                || inside(mouseX, mouseY, TRACK_X, TRACK_Y - TRACK_HIT_PADDING,
                        TRACK_WIDTH, TRACK_HEIGHT + TRACK_HIT_PADDING * 2);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX <= leftPos + x + width
                && mouseY >= topPos + y && mouseY <= topPos + y + height;
    }

    /**
     * 未开始或已完成时发「开始 / 重开」，进行中时上报一次点击。
     *
     * <p>点击上报后不立即改动本地状态：服务端会回发权威快照，界面以它为准，
     * 避免客户端自行推进造成与服务端判定不一致。</p>
     */
    private void act() {
        // 未收到快照时 phase 仍是 IDLE，同样会走「开始」分支，服务端先建状态再回发。
        boolean running = phase == MaintenanceCalibrationRules.PHASE_RUNNING;
        PacketDistributor.sendToServer(new MaintenanceCalibrationActionPacket(
                menu.containerId, menu.getBlockPos(), !running, running ? reportedElapsed() : 0L));
    }

    /** 保留 RGB，替换透明度分量。 */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /**
     * 在两种颜色之间按 {@code t} 线性插值，{@code t} 取 [0, 1]。
     *
     * <p>用于逐行生成竖直渐变。逐行取色后调用 {@code fill} 即可拼出平滑过渡，
     * 无需为界面引入贴图资源。</p>
     */
    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(((from >>> 24) & 0xFF) * (1.0F - clamped) + ((to >>> 24) & 0xFF) * clamped);
        int r = Math.round(((from >>> 16) & 0xFF) * (1.0F - clamped) + ((to >>> 16) & 0xFF) * clamped);
        int g = Math.round(((from >>> 8) & 0xFF) * (1.0F - clamped) + ((to >>> 8) & 0xFF) * clamped);
        int b = Math.round((from & 0xFF) * (1.0F - clamped) + (to & 0xFF) * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
